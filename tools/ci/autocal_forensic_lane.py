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
PLAN = ROOT / "tests/fixtures/autocal-forensic-plan-v2.json"

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def hx(value):
    return bytes.fromhex(value)

def checksum_request(frame):
    return bool(frame) and (sum(frame[:-1]) & 0xFF) == frame[-1]

def checksum_response(response, request_len):
    return len(response) >= request_len + 3 and (sum(response[request_len:-1]) & 0xFF) == response[-1]

def response_parts(tx):
    req = hx(tx["request"])
    res = hx(tx["response"])
    if len(res) < len(req) + 3:
        raise ValueError("response shorter than echo+status+length+checksum")
    status = res[len(req)]
    declared = res[len(req)+1]
    payload = res[len(req)+2:-1]
    return req, res, status, declared, payload

def u8(payload, offset):
    return payload[offset]

def u16le(payload, offset):
    return payload[offset] | (payload[offset+1] << 8)

def s16le(payload, offset):
    value = u16le(payload, offset)
    return value - 65536 if value >= 32768 else value

def command(cmd):
    p = subprocess.run(cmd, cwd=ROOT, text=True, capture_output=True)
    return p.returncode, (p.stdout + "\n" + p.stderr)[-16000:]

def receipt(status, summary, evidence=None, metrics=None):
    return {"status": status, "summary": summary, "evidence": evidence or [], "metrics": metrics or {}}

def oracle_request_map(oracle):
    return {row.get("request"): row for row in oracle.get("autocal_dm", []) if row.get("request")}

def tx_lane(sequence):
    pm = load(PORTMON)
    oracle = load(ORACLE)
    tx = next((t for t in pm["transactions"] if int(t["sequence"]) == sequence), None)
    if tx is None:
        return receipt("BROKEN", "transaction lane points to missing sequence", [sequence])

    req, res, status, declared, payload = response_parts(tx)
    violations = []
    if not checksum_request(req):
        violations.append("request checksum")
    if res[:len(req)] != req:
        violations.append("request echo")
    if status not in (0x53, 0xCA):
        violations.append(f"unexpected status 0x{status:02X}")
    if declared != len(payload):
        violations.append(f"declared payload={declared} actual={len(payload)}")
    if not checksum_response(res, len(req)):
        violations.append("response checksum from status")
    row = oracle_request_map(oracle).get(tx["request"])
    if row and row.get("response_total_bytes") is not None and len(res) != int(row["response_total_bytes"]):
        violations.append(f"oracle total bytes={row['response_total_bytes']} actual={len(res)}")

    metrics = {
        "sequence": sequence,
        "at_ms": tx["at_ms"],
        "request": tx["request"],
        "response_bytes": len(res),
        "status": f"0x{status:02X}",
        "payload_bytes": len(payload),
    }

    if tx["request"] == "48 01 49" and status == 0x53:
        if len(payload) != 34:
            violations.append(f"live payload bytes={len(payload)}")
        else:
            metrics["live"] = {
                "rpm_raw": u16le(payload, 0),
                "gas_injection_raw": u16le(payload, 6),
                "petrol_injection_raw": u16le(payload, 8),
                "fuel_state": u8(payload, 11),
                "levels_raw": u8(payload, 13),
                "gas_pressure_raw": u16le(payload, 14),
                "gas_temperature_raw": u8(payload, 16),
                "map_raw_signed": s16le(payload, 17),
                "gas_bank2_raw": u16le(payload, 24),
                "petrol_bank2_raw": u16le(payload, 28),
            }
    elif tx["request"] == "48 0B 53" and status == 0x53:
        if len(payload) != 14:
            violations.append(f"native status payload bytes={len(payload)}")
        else:
            metrics["native_status"] = {"flag13": payload[12], "automatch_count": payload[13]}
    elif row:
        if row.get("data_length") == 2 and len(payload) % 2:
            violations.append("16-bit vector has odd payload length")
        if row.get("data_mask") == 255 and row.get("class") == "TAebVector" and row.get("name","").startswith("ACQUIRED_ZONES") and len(payload) != 4:
            violations.append("zone vector is not 4 bytes")

    return receipt("RED" if violations else "PASS",
                   "Captured Portmon transaction satisfies protocol/oracle envelope" if not violations else "Captured Portmon transaction has an evidence/protocol discrepancy",
                   violations,
                   metrics)

def javascript_interface_bodies(source):
    lines = source.splitlines()
    bodies = []
    i = 0
    while i < len(lines):
        if lines[i].strip() != "@JavascriptInterface":
            i += 1
            continue
        start = i
        i += 1
        brace = 0
        saw_fun = False
        collected = []
        while i < len(lines):
            line = lines[i]
            if i > start + 1 and line.strip() == "@JavascriptInterface" and brace <= 0:
                break
            collected.append(line)
            if "fun " in line:
                saw_fun = True
            brace += line.count("{") - line.count("}")
            if saw_fun and brace <= 0 and ("=" in "".join(collected) or "{" in "".join(collected)):
                i += 1
                break
            i += 1
        bodies.append("\n".join(collected))
    return bodies

def meta_architecture_bundle():
    monitor = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
    service = text("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
    engine = text("app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt")
    bridge = text("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt")
    failures = []
    for forbidden in ("Executors.", "ScheduledExecutor", "Thread("):
        if forbidden in monitor:
            failures.append(f"monitor owns concurrency primitive: {forbidden}")
    if "Mp48SerialScheduler" not in monitor:
        failures.append("monitor missing shared serial scheduler")
    if "scheduleWithFixedDelay(::autoCalTick" not in service:
        failures.append("service does not own AutoCal cadence")
    if "if (queued.telemetryAfter" not in engine or "pollTelemetry()" not in engine:
        failures.append("telemetry-after-secondary invariant missing")
    exposed = javascript_interface_bodies(bridge)
    direct = [body.splitlines()[0:4] for body in exposed if "serial.transaction(" in body or "Mp48WorkClass" in body]
    if direct:
        failures.append("JavascriptInterface directly owns serial work")
    return receipt("RED" if failures else "PASS",
                   "Architecture keeps serial ownership below exposed JS methods",
                   failures,
                   {"javascript_interfaces": len(exposed)})

def meta_oracle_source_semantics():
    oracle = load(ORACLE)
    mp48 = text("app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt")
    protocol = text("app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt")
    mismatches = []
    live_source = {
        "rpm_raw": ("u16le", 0),
        "gas_injection_raw": ("u16le", 6),
        "petrol_injection_raw": ("u16le", 8),
        "fuel_state": ("u8", 11),
        "levels_raw": ("u8", 13),
        "gas_pressure_raw": ("u16le", 14),
        "gas_temperature_raw": ("u8", 16),
        "map_raw": ("s16le", 17),
        "gas_injection_bank2_raw": ("u16le", 24),
        "petrol_injection_bank2_raw": ("u16le", 28),
    }
    for item in oracle["live_telemetry"]["payload_fields"]:
        semantic = item["semantic"]
        got = live_source.get(semantic)
        expected = (item["encoding"], int(item["offset"]))
        if got != expected:
            mismatches.append(f"live {semantic}: oracle={expected} OMEGAS={got}")
        if got and f"{got[0]}(payload, {got[1]})" not in mp48:
            mismatches.append(f"live {semantic}: source expression absent")
    for row in oracle["autocal_dm"]:
        name = row["name"]
        if name == "VECT_AUTOCAL_U8_0_1_2":
            # The original oracle proves one 0x0165 family but not exact
            # subindex semantics. OMEGAS intentionally exposes only the
            # currently named index 1 plus the still-debt-tracked index 2.
            if "VECT_AUTOCAL_U8_1" not in protocol or "MAX_AUTOMATCH" not in protocol:
                mismatches.append("AutoCal 0x0165 indexed family missing")
            continue
        if name not in protocol:
            mismatches.append(f"AutoCal field name missing: {name}")
            continue
        if row.get("signed") is True and name not in {"VECT_AUTOCAL_U8_0_1_2"}:
            pattern = rf'val\s+{re.escape(name)}\s*=\s*Field\([^\n]+Encoding\.S16_LE'
            if re.search(pattern, protocol) is None:
                mismatches.append(f"AutoCal signed encoding mismatch: {name}")
    return receipt("RED" if mismatches else "PASS",
                   "OMEGAS source semantics match distilled ProgBase oracle",
                   mismatches)

def meta_levels_global():
    mp48 = text("app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt")
    scale = text("app/src/main/java/com/omegas/prohub/ecu/Mp48TelemetryScale.kt")
    dash = text("app/src/main/assets/ui/screens/dashboard.js")
    findings = []
    if "fun levelPercentage" in scale and '.put("level_percentage", Mp48TelemetryScale.levelPercentage(levelRaw))' in mp48:
        findings.append("backend still publishes uncalibrated level_percentage")
    if not any(token in dash for token in ("LEVELS RAW", "level_raw", "levelRaw")):
        findings.append("Dashboard/Agora still omits LEVELS RAW")
    return receipt("RED" if findings else "PASS",
                   "Global LEVELS semantics stay RAW-only and visible",
                   findings)

def meta_session_write_safety():
    monitor = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
    projection = text("app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt")
    action = text("app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt")
    failures = []
    if "MANUAL_WRITE" in monitor or "protocolTransaction(" in monitor:
        failures.append("monitor contains write path")
    if 'appAutomaticWrite", false' not in monitor:
        failures.append("automatic-write false identity missing")
    for token in ("currentSession", "STALE_SESSION", "sessionId"):
        if token not in projection:
            failures.append(f"projection session guard missing: {token}")
    for token in ("expectedEnableReadback", "requiresCriticalConfirmation"):
        if token not in action:
            failures.append(f"manual action safety missing: {token}")
    return receipt("RED" if failures else "PASS", "Session and manual-write safety invariants", failures)

def meta_mutation_negative_bundle():
    pm = load(PORTMON)
    sample_live = next(t for t in pm["transactions"] if t["request"] == "48 01 49")
    sample_vec = next(t for t in pm["transactions"] if t["request"] == "29 5B 01 85")
    detected = []
    req, res, status, declared, payload = response_parts(sample_live)
    bad = bytearray(res); bad[-1] ^= 1
    detected.append(not checksum_response(bytes(bad), len(req)))
    detected.append(len(payload[:-1]) != 34)
    req2, res2, status2, declared2, payload2 = response_parts(sample_vec)
    detected.append(len(payload2[:-1]) % 2 == 1)
    bad_echo = bytearray(res2); bad_echo[0] ^= 1
    detected.append(bytes(bad_echo[:len(req2)]) != req2)
    return receipt("PASS" if all(detected) else "BROKEN",
                   "Harness detects checksum, truncation, shape and echo mutations",
                   metrics={"detected": detected})


def meta_map_signedness_boundary():
    oracle = load(ORACLE)
    mp48 = text("app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt")
    live_field = next(
        item for item in oracle["live_telemetry"]["payload_fields"]
        if item["semantic"] == "map_raw"
    )
    pm = load(PORTMON)
    observed = []
    for tx in pm["transactions"]:
        if tx["request"] != "48 01 49":
            continue
        _, _, status, _, payload = response_parts(tx)
        if status != 0x53 or len(payload) != 34:
            continue
        observed.append(u16le(payload, 17))
    high_bit = [value for value in observed if value >= 0x8000]
    source_signed = "s16le(payload, 17)" in mp48
    source_unsigned = "u16le(payload, 17)" in mp48
    oracle_signed = live_field.get("encoding") == "s16le"
    synthetic_u16 = 0xFFFF
    synthetic_s16 = synthetic_u16 - 0x10000
    findings = []
    if oracle_signed and not source_signed:
        findings.append("ProgBase oracle requires S16LE MAP but OMEGAS source decodes U16LE")
    return receipt(
        "RED" if findings else "PASS",
        "Live MAP signedness agrees with original oracle at the high-bit boundary",
        findings,
        {
            "oracle_encoding": live_field.get("encoding"),
            "omegas_u16_source": source_unsigned,
            "omegas_s16_source": source_signed,
            "observed_samples": len(observed),
            "observed_min": min(observed) if observed else None,
            "observed_max": max(observed) if observed else None,
            "observed_high_bit_count": len(high_bit),
            "synthetic_ffff_u16": synthetic_u16,
            "synthetic_ffff_s16": synthetic_s16,
            "replay_can_distinguish_signedness": bool(high_bit),
        },
    )

def meta_acquisition_family_completeness():
    monitor = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
    acquisition = text("app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt")
    operational = [
        "PETR_INJ_TBUF",
        "MNFLD_PRESS_BUF",
        "NUM_BUF_UPD_PETR",
        "PETR_INJ_TBUF_GAS_PREV",
        "MNFLD_PRESS_BUF_GAS_PREV",
        "PETR_INJ_TBUF_GAS",
        "MNFLD_PRESS_BUF_GAS",
        "NUM_BUF_UPD_GAS",
        "ACQUIRED_ZONES_PETROL",
        "ACQUIRED_ZONES_GAS",
    ]
    section = monitor.split("private fun refreshAcquisitionGroup", 1)[1].split("private fun refreshReferenceGroup", 1)[0]
    missing = [name for name in operational if f"AutoCalProtocol.{name}" not in section]
    source_missing = [
        name for name in (
            "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR",
            "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV",
            "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS",
        )
        if f'"{name}"' not in acquisition
    ]
    if "AutoCalProtocol.MUL_ACT" in section:
        missing.append("MUL_ACT_MUST_REMAIN_REFERENCE_ONLY")
    failures = missing + [f"AutoCalAcquisition missing consumer {name}" for name in source_missing]
    return receipt(
        "RED" if failures else "PASS",
        "Two-second operational refresh updates the complete AutoCalAcquisition consumer unit without stealing MUL_ACT from reference",
        failures,
        {"operational_fields": operational, "failure_count": len(failures)},
    )

def meta_reference_revision_contract():
    monitor = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
    planner = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt")
    required = [
        'REFERENCE_INTERVAL_MS = 4_000L',
        'refreshReferenceGroup',
        'referenceRefreshAtElapsedMs',
        'reviseSnapshotHashOnChange = true',
        'incrementalReferenceRevision',
        'previous.optString("rawPayloadHex") != replacement.optString("rawPayloadHex")',
        'refreshPlanner.markReference',
    ]
    joined = planner + "\n" + monitor
    missing = [token for token in required if token not in joined]
    return receipt(
        "RED" if missing else "PASS",
        "Reference refresh publishes explicit timestamp and byte-sensitive revision",
        missing,
    )

def meta_levels_consumer_reachability():
    hits = []
    for root_name in ("app/src/main", "app/src/test", "tests"):
        root = ROOT / root_name
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in {".kt", ".java", ".js", ".cjs", ".py", ".html"}:
                continue
            source = path.read_text(encoding="utf-8", errors="ignore")
            for token in ("level_percentage", "levelPercentage"):
                if token in source:
                    hits.append({"path": str(path.relative_to(ROOT)), "token": token})
    production_consumers = [
        h for h in hits
        if h["path"].startswith("app/src/main")
        and h["path"] not in {
            "app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt",
            "app/src/main/java/com/omegas/prohub/ecu/Mp48TelemetryScale.kt",
        }
    ]
    return receipt(
        "RED" if production_consumers else "PASS",
        "Uncalibrated LEVELS percentage has no production consumer beyond its producer/scale",
        [f"{h['path']}:{h['token']}" for h in production_consumers],
        {"all_hits": hits, "production_consumer_count": len(production_consumers)},
    )

def meta_forensic_selftest():
    proc = subprocess.run(
        ["python3", "tools/ci/autocal_forensic_plan.py"],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if proc.returncode != 0:
        return receipt("BROKEN", "Forensic planner executes", [proc.stdout, proc.stderr])
    matrix = json.loads(proc.stdout)
    lanes = matrix.get("include", [])
    ids = [x["id"] for x in lanes]
    tx = [x for x in lanes if x.get("category") == "transaction"]
    ok = len(lanes) == 256 and len(tx) == 243 and len(ids) == len(set(ids)) and len(lanes) <= 256
    return receipt("PASS" if ok else "BROKEN",
                   "Forensic matrix is unique, complete and within GitHub cap",
                   metrics={"lanes":len(lanes),"transaction_lanes":len(tx),"unique":len(set(ids))})

def meta_same_ecu_jvm():
    rc, out = command(["./gradlew","testDebugUnitTest","--tests","com.omegas.prohub.ecu.PortmonSameEcuParityTest","--stacktrace"])
    return receipt("PASS" if rc == 0 else "RED", "Production Kotlin decoders consume the same committed ECU replay", [out])

def meta_autocal_jvm():
    cmd = ["./gradlew","testDebugUnitTest",
           "--tests","com.omegas.prohub.autocal.AutoCalUiProjectionTest",
           "--tests","com.omegas.prohub.autocal.NativeAutoCalRefreshPlannerTest",
           "--tests","com.omegas.prohub.autocal.AutoCalAcquisitionTest",
           "--stacktrace"]
    rc, out = command(cmd)
    return receipt("PASS" if rc == 0 else "RED", "Targeted AutoCal JVM state/projection tests", [out])

def meta_ui_autocal():
    rc, out = command(["node","--test",
                      "tests/ui/autocal-current-band-parity.test.cjs",
                      "tests/ui/autocal-didactic-cockpit.test.cjs",
                      "tests/ui/autocal-cockpit.test.cjs"])
    return receipt("PASS" if rc == 0 else "RED", "Targeted AutoCal UI contracts", [out])

META = {
    "meta_same_ecu_jvm": meta_same_ecu_jvm,
    "meta_autocal_jvm": meta_autocal_jvm,
    "meta_ui_autocal": meta_ui_autocal,
    "meta_architecture_bundle": meta_architecture_bundle,
    "meta_oracle_source_semantics": meta_oracle_source_semantics,
    "meta_levels_global": meta_levels_global,
    "meta_session_write_safety": meta_session_write_safety,
    "meta_mutation_negative_bundle": meta_mutation_negative_bundle,
    "meta_map_signedness_boundary": meta_map_signedness_boundary,
    "meta_acquisition_family_completeness": meta_acquisition_family_completeness,
    "meta_reference_revision_contract": meta_reference_revision_contract,
    "meta_levels_consumer_reachability": meta_levels_consumer_reachability,
    "meta_forensic_selftest": meta_forensic_selftest,
}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lane", required=True)
    ap.add_argument("--sequence", type=int, default=0)
    ap.add_argument("--receipt", required=True)
    args = ap.parse_args()
    out = Path(args.receipt)
    out.parent.mkdir(parents=True, exist_ok=True)
    base = {"lane": args.lane, "sha": os.environ.get("GITHUB_SHA",""), "runner": os.environ.get("RUNNER_OS","")}
    try:
        if args.sequence > 0:
            res = tx_lane(args.sequence)
        else:
            fn = META.get(args.lane)
            if fn is None:
                raise KeyError(args.lane)
            res = fn()
        base.update(res)
    except Exception as exc:
        base.update({
            "status":"BROKEN",
            "summary":"lane worker exception",
            "evidence":[str(exc),traceback.format_exc()],
            "metrics":{},
        })
    out.write_text(json.dumps(base, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(base, indent=2, ensure_ascii=False))
    return 2 if base["status"] == "BROKEN" else 0

if __name__ == "__main__":
    sys.exit(main())
