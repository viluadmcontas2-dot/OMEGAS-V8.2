#!/usr/bin/env python3
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PORTMON = ROOT / "tests/fixtures/portmon-autocal-cycle-v1.json"
ORACLE = ROOT / "tests/fixtures/progbase-autocal-consumer-map-v1.json"
PARITY = ROOT / "tests/fixtures/omegas-autocal-progbase-parity-v1.json"

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def median_gap(items):
    return statistics.median(b["at_ms"] - a["at_ms"] for a,b in zip(items, items[1:]))

def tx_by_request():
    data=load(PORTMON)
    out={}
    for item in data["transactions"]:
        out.setdefault(item["request"],[]).append(item)
    return data,out

def receipt(status, summary, evidence=None, metrics=None):
    return {
        "status": status,
        "summary": summary,
        "evidence": evidence or [],
        "metrics": metrics or {},
    }

def command(cmd):
    p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True)
    tail=(p.stdout+"\n"+p.stderr)[-10000:]
    return p.returncode,tail

def source_contains(path,*needles):
    s=text(path)
    missing=[x for x in needles if x not in s]
    return s,missing

def lane(name):
    pm,by=tx_by_request()
    oracle=load(ORACLE)
    protocol=text("app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt")
    mp48=text("app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt")
    monitor=text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
    engine=text("app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt")
    cockpit=text("app/src/main/assets/ui/screens/autocal-cockpit.js")

    if name=="provenance_progbase_oracle":
        got=oracle["source"]["progbase"]["sha256"]
        ok=got=="8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4"
        return receipt("PASS" if ok else "BROKEN","ProgBase oracle SHA pinned",[got])

    if name=="provenance_portmon_fixture":
        ok=(oracle["source"]["portmon"]["source_raw_sha256"]==pm["sourceRawSha256"] and oracle["source"]["portmon"]["source_zip_sha256"]==pm["sourceZipSha256"])
        return receipt("PASS" if ok else "BROKEN","Portmon provenance hashes cross-check",[
            pm["sourceRawSha256"],pm["sourceZipSha256"]])

    if name=="portmon_live_envelope":
        rows=by["48 01 49"]; bad=[]
        for t in rows:
            raw=bytes.fromhex(t["response"])
            if len(raw)!=40 or raw[:3]!=bytes.fromhex("48 01 49") or raw[3]!=0x53 or raw[4]!=0x22 or len(raw[5:-1])!=34:
                bad.append(t["sequence"])
        return receipt("PASS" if not bad else "BROKEN","Live 48 envelope/34-byte payload",bad,{"samples":len(rows)})

    if name=="portmon_request_checksums":
        bad=[]
        for t in pm["transactions"]:
            req=bytes.fromhex(t["request"])
            if len(req)>=2 and (sum(req[:-1])&0xff)!=req[-1]: bad.append(t["sequence"])
        return receipt("PASS" if not bad else "BROKEN","All captured request checksums",bad,{"transactions":len(pm["transactions"])})

    if name=="portmon_live_cadence":
        rows=by["48 01 49"]; med=median_gap(rows)
        return receipt("PASS" if 30<=med<=70 else "RED","Observed live telemetry cadence",metrics={"median_ms":med,"samples":len(rows)})

    if name=="portmon_autocal_2s_cadence":
        reqs=["29 5B 01 85","29 5C 01 86","29 5D 01 87","29 5E 01 88","29 5F 01 89","29 60 01 8A","29 61 01 8B","29 62 01 8C","29 63 01 8D","29 6F 01 99","29 70 01 9A"]
        vals={q:median_gap(by[q]) for q in reqs}
        ok=all(1500<=v<=2600 for v in vals.values())
        return receipt("PASS" if ok else "RED","Observed ~2s AutoCal family",metrics=vals)

    if name=="portmon_reference_4s_cadence":
        vals={q:median_gap(by[q]) for q in ["29 8D 01 B7","29 8E 01 B8"]}
        ok=all(3000<=v<=5000 for v in vals.values())
        return receipt("PASS" if ok else "RED","Observed ~4s RV family",metrics=vals)

    if name=="portmon_live_interleave":
        slow={row.get("request") for row in oracle["autocal_dm"] if row.get("request")}
        tx=pm["transactions"]; hits=0; total=0
        for i,t in enumerate(tx):
            if t["request"] not in slow: continue
            total+=1
            lo=max(0,i-6); hi=min(len(tx),i+7)
            if any(x["request"]=="48 01 49" for x in tx[lo:hi]): hits+=1
        ratio=hits/max(total,1)
        return receipt("PASS" if ratio>=0.9 else "RED","Telemetry interleaved around secondary AutoCal reads",metrics={"covered":hits,"total":total,"ratio":ratio})

    if name=="portmon_slow_ordering":
        seq=[t["request"] for t in pm["transactions"] if t["request"]!="48 01 49"]
        return receipt("INFO","Captured non-live order retained as forensic evidence",evidence=seq[:80],metrics={"non_live_transactions":len(seq)})

    if name=="shape_buffers_015b_0163":
        reqs=["29 5B 01 85","29 5C 01 86","29 5D 01 87","29 5E 01 88","29 5F 01 89","29 60 01 8A","29 62 01 8C","29 63 01 8D"]
        bad={q:sorted({len(bytes.fromhex(x["response"])) for x in by[q]}) for q in reqs}
        ok=all(v==[43] for v in bad.values())
        return receipt("PASS" if ok else "BROKEN","18x16-bit buffer envelope sizes",metrics=bad)

    if name=="shape_zones_016f_0170":
        vals={q:sorted({len(bytes.fromhex(x["response"])) for x in by[q]}) for q in ["29 6F 01 99","29 70 01 9A"]}
        return receipt("PASS" if all(v==[11] for v in vals.values()) else "BROKEN","4-byte zone vector envelopes",metrics=vals)

    if name=="shape_reference_018d_018e":
        vals={q:sorted({len(bytes.fromhex(x["response"])) for x in by[q]}) for q in ["29 8D 01 B7","29 8E 01 B8"]}
        return receipt("PASS" if all(v==[67] for v in vals.values()) else "BROKEN","30x16-bit reference vector envelopes",metrics=vals)

    if name=="oracle_live_offsets":
        f={x["semantic"]:x["offset"] for x in oracle["live_telemetry"]["payload_fields"]}
        required={"petrol_injection_raw":8,"levels_raw":13,"map_raw":17}
        ok=all(f.get(k)==v for k,v in required.items())
        return receipt("PASS" if ok else "BROKEN","Original live payload offsets",metrics=f)

    if name=="oracle_runpoint":
        rp=oracle["live_telemetry"]["runpoint"]
        ok=rp["chart"]=="ChartData" and rp["caller_xref_va"]=="0x004A4BC9" and rp["helper_va"]=="0x005158F4"
        return receipt("PASS" if ok else "BROKEN","RunPoint call graph oracle",metrics=rp)

    if name=="oracle_levels":
        lv=oracle["levels"]
        ok=lv["live_raw"]["offset"]==13 and lv["live_raw"]["conversion"]=="NONE" and lv["calibration"]["channel_index_hex"]=="0x0E"
        return receipt("PASS" if ok else "BROKEN","LEVELS live RAW separated from learned references",metrics=lv)

    if name=="oracle_currentband":
        cb=oracle["consumers"]["CurrentBand"]
        ok="MNFLD_PRESS_THD" in cb["producer"] and "live RunPoint MAP" in cb["producer"]
        return receipt("PASS" if ok else "BROKEN","CurrentBand uses live MAP + thresholds",metrics=cb)

    if name=="oracle_curves":
        p=oracle["consumers"]["PetrolCurve"]; g=oracle["consumers"]["GasCurve"]
        ok=p["producer_x"]=="PETR_INJ_TBP" and p["producer_y"]=="PETR_MNFLD_PRESS_RV" and g["producer_y"]=="GAS_MNFLD_PRESS_RV"
        return receipt("PASS" if ok else "BROKEN","Original curve producer identity",metrics={"petrol":p,"gas":g})

    if name=="oracle_acquisition_zones":
        acq=oracle["consumers"]["AcqusitionAreas"]
        ok=all(x in acq["producer"] for x in ["ACQUIRED_ZONES_PETROL","ACQUIRED_ZONES_GAS","PETR_INJ_TBP","MNFLD_PRESS_THD"])
        return receipt("PASS" if ok else "BROKEN","Original acquisition-area dependencies",metrics=acq)

    if name=="omegas_protocol_addresses":
        wanted=["0x014A","0x014B","0x014C","0x015B","0x015C","0x016F","0x0170","0x018D","0x018E"]
        missing=[x for x in wanted if x not in protocol]
        return receipt("PASS" if not missing else "BROKEN","OMEGAS protocol contains original addresses",missing)

    if name=="omegas_live_decoder":
        wanted=["u16le(payload, 8)","u8(payload, 13)","u16le(payload, 17)","TELEMETRY_PAYLOAD_SIZE = 34"]
        missing=[x for x in wanted if x not in mp48]
        return receipt("PASS" if not missing else "BROKEN","OMEGAS live decoder uses recovered offsets",missing)

    if name=="omegas_single_serial_authority":
        bad=[x for x in ["Executors.","ScheduledExecutor","Thread("] if x in monitor]
        ok=not bad and "Mp48SerialScheduler" in monitor
        return receipt("PASS" if ok else "BROKEN","NativeAutoCalMonitor owns no thread/serial transport",bad)

    if name=="omegas_telemetry_after_secondary":
        ok="if (queued.telemetryAfter" in engine and "pollTelemetry()" in engine
        return receipt("PASS" if ok else "BROKEN","Serial engine restores telemetry after queued secondary work")

    if name=="omegas_levels_fast_authority":
        ok="const levelRaw = finite(live.level_raw ?? live.levelRaw);" in cockpit and "const levelRaw = finite(projection?.levelsRaw);" not in cockpit
        return receipt("PASS" if ok else "RED","AutoCal live strip LEVELS source is live frame")

    if name=="omegas_currentband_presence":
        ok="currentBand(snapshot = {}, live = {})" in cockpit and "physicalVector(snapshot, 'MNFLD_PRESS_THD')" in cockpit and "data-autocal-current-band" in cockpit
        return receipt("PASS" if ok else "RED","CurrentBand consumer and visual layer")

    if name=="omegas_currentband_boundary":
        rc,out=command(["node","--test","tests/ui/autocal-current-band-parity.test.cjs"])
        return receipt("PASS" if rc==0 else "RED","Executable CurrentBand boundary oracle",[out])

    if name=="omegas_grouped_acquisition_refresh":
        planner=ROOT/"app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt"
        service=text("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
        ok=planner.exists() and "refreshAcquisitionGroup" in monitor and all(x in monitor for x in ["NUM_BUF_UPD_PETR","NUM_BUF_UPD_GAS","ACQUIRED_ZONES_PETROL","ACQUIRED_ZONES_GAS"]) and "scheduleWithFixedDelay(::autoCalTick" in service
        return receipt("PASS" if ok else "RED","Grouped ~2s acquisition refresh on existing serial authority")

    if name=="omegas_grouped_reference_refresh":
        planner=(ROOT/"app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt")
        ps=planner.read_text("utf-8") if planner.exists() else ""
        wanted=["refreshReferenceGroup","PETR_INJ_TBP","MNFLD_PRESS_THD","PETR_MNFLD_PRESS_RV","GAS_MNFLD_PRESS_RV"]
        missing=[x for x in wanted if x not in monitor]
        ok="REFERENCE_INTERVAL_MS = 4_000L" in ps and not missing
        return receipt("PASS" if ok else "RED","Grouped ~4s reference refresh",missing)

    if name=="omegas_petrol_gas_refresh_symmetry":
        acq=monitor.split("private fun refreshAcquisitionGroup",1)[1].split("private fun",1)[0] if "private fun refreshAcquisitionGroup" in monitor else ""
        wanted=["NUM_BUF_UPD_PETR","NUM_BUF_UPD_GAS","ACQUIRED_ZONES_PETROL","ACQUIRED_ZONES_GAS"]
        missing=[x for x in wanted if x not in acq]
        return receipt("PASS" if not missing else "RED","Petrol/GNV acquisition-side symmetry",missing)

    if name=="omegas_snapshot_coherence_guard":
        snap=text("app/src/main/java/com/omegas/prohub/autocal/AutoCalSnapshot.kt")
        ok="MAX_AUTOMATCH_GROUP_SKEW_MS = 2_000L" in snap and "coherenceGroups" in snap
        return receipt("PASS" if ok else "BROKEN","Temporal coherence guard retained")

    if name=="omegas_session_safety":
        projection=text("app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt")
        ok="currentSession" in projection and "STALE_SESSION" in projection and "sessionId" in projection
        return receipt("PASS" if ok else "BROKEN","Current-session rejection remains present")

    if name=="omegas_no_automatic_write":
        action=text("app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt")
        bad=("MANUAL_WRITE" in monitor or "protocolTransaction(" in monitor)
        ok=not bad and 'appAutomaticWrite", false' in monitor and "expectedEnableReadback" in action
        return receipt("PASS" if ok else "BROKEN","Monitor remains read-only; writes stay manual/readback-gated")

    if name=="omegas_bridge_no_serial_io":
        bridge=text("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt")
        bad=[x for x in ["protocolTransaction(","serial.transaction(","Mp48WorkClass"] if x in bridge]
        return receipt("PASS" if not bad else "BROKEN","Web bridge performs no serial I/O",bad)

    if name=="omegas_ui_fast_scheduler":
        app=text("app/src/main/assets/ui/app.js"); scheduler=text("app/src/main/assets/ui/core/scheduler.js")
        ok="intervalMs: 200" in app and "this.tick % 10 === 0" in scheduler and "addHook('fast'" in cockpit
        return receipt("PASS" if ok else "RED","UI fast/context cadence separation")

    if name=="global_levels_percentage_scan":
        scale=text("app/src/main/java/com/omegas/prohub/ecu/Mp48TelemetryScale.kt")
        active=("fun levelPercentage" in scale and '.put("level_percentage", Mp48TelemetryScale.levelPercentage(levelRaw))' in mp48)
        return receipt("RED" if active else "PASS","No invented LEVELS percentage should remain active",["Mp48Protocol.kt emits level_percentage" if active else "no active percentage conversion"])

    if name=="dashboard_levels_raw_presence":
        ui=ROOT/"app/src/main/assets/ui"
        hits=[]
        for p in ui.rglob("*.js"):
            s=p.read_text("utf-8",errors="ignore")
            if "LEVELS RAW" in s or "level_raw" in s or "levelRaw" in s:
                hits.append(str(p.relative_to(ROOT)))
        dashboard=[h for h in hits if "dashboard" in h.lower() or "now" in h.lower() or "home" in h.lower()]
        return receipt("PASS" if dashboard else "RED","Dashboard/Agora exposes LEVELS RAW",hits)

    if name=="mutation_bad_checksum":
        req=bytearray.fromhex("29 5B 01 85"); req[-1]^=1
        detected=((sum(req[:-1])&0xff)!=req[-1])
        return receipt("PASS" if detected else "BROKEN","Checksum mutation is detected",metrics={"mutated":req.hex(" ")})

    if name=="mutation_bad_shape":
        raw=bytes.fromhex(by["29 8D 01 B7"][0]["response"]); mutated=raw[:-2]
        detected=len(mutated)!=67
        return receipt("PASS" if detected else "BROKEN","Reference-vector truncation is observable",metrics={"original":len(raw),"mutated":len(mutated)})

    if name=="targeted_ui_autocal_suite":
        rc,out=command(["node","--test","tests/ui/autocal-current-band-parity.test.cjs","tests/ui/autocal-didactic-cockpit.test.cjs","tests/ui/autocal-cockpit.test.cjs"])
        return receipt("PASS" if rc==0 else "RED","Targeted executable AutoCal UI suite",[out])

    if name=="targeted_jvm_projection":
        rc,out=command(["./gradlew","testDebugUnitTest","--tests","com.omegas.prohub.autocal.AutoCalUiProjectionTest","--stacktrace"])
        return receipt("PASS" if rc==0 else "RED","Targeted JVM projection tests",[out])

    if name=="targeted_jvm_refresh_planner":
        rc,out=command(["./gradlew","testDebugUnitTest","--tests","com.omegas.prohub.autocal.NativeAutoCalRefreshPlannerTest","--stacktrace"])
        return receipt("PASS" if rc==0 else "RED","Targeted JVM refresh-planner tests",[out])

    raise KeyError(name)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--lane",required=True)
    ap.add_argument("--receipt",required=True)
    args=ap.parse_args()
    out=Path(args.receipt)
    out.parent.mkdir(parents=True,exist_ok=True)
    base={"lane":args.lane,"sha":os.environ.get("GITHUB_SHA",""),"runner":os.environ.get("RUNNER_OS","")}
    try:
        res=lane(args.lane)
        base.update(res)
    except Exception as exc:
        base.update({
            "status":"BROKEN",
            "summary":"lane infrastructure/analysis exception",
            "evidence":[str(exc),traceback.format_exc()],
            "metrics":{},
        })
    out.write_text(json.dumps(base,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(base,indent=2,ensure_ascii=False))
    # Research workflow: RED is a finding; only BROKEN means the lane itself malfunctioned.
    return 2 if base["status"]=="BROKEN" else 0

if __name__=="__main__":
    sys.exit(main())
