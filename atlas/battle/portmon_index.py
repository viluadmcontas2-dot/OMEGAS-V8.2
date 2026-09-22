#!/usr/bin/env python3
from __future__ import annotations
import argparse, collections, json, re, zipfile
from dataclasses import dataclass
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
CANON=ROOT/"atlas/manifests/canonical-inputs.json"
SEEDS=ROOT/"atlas/manifests/autocal-seeds.json"
EVENT_RE=re.compile(r"^(?P<index>\d+)\s+(?P<field2>\d+\.\d+)\s+ProgBase\.exe\s+(?P<op>IRP_MJ_WRITE|IRP_MJ_READ|IOCTL_SERIAL_PURGE)\s+Silabser\d+\s*(?P<detail>.*)$")
SUCCESS_RE=re.compile(r"^(?P<index>\d+)\s+(?P<duration>\d+\.\d+)\s+SUCCESS\s*(?P<detail>.*)$")
HEX_RE=re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")

def payload(detail:str)->str:
    m=HEX_RE.search(detail)
    return "" if not m else " ".join(m.group(1).upper().split())

@dataclass
class Event:
    index:int; clock_before:float; duration:float; operation:str; payload_hex:str

def events_from_zip(path:Path,entry:str):
    clock=0.0; pending=None
    with zipfile.ZipFile(path) as zf, zf.open(entry) as raw:
        for b in raw:
            line=b.decode("utf-8","replace").rstrip("\r\n")
            m=EVENT_RE.match(line)
            if m:
                pending=(int(m.group("index")),float(m.group("field2")),m.group("op"),payload(m.group("detail")))
                continue
            s=SUCCESS_RE.match(line)
            if s and pending is not None:
                idx,field2,op,p0=pending
                if int(s.group("index"))==idx:
                    dur=float(s.group("duration")); p=payload(s.group("detail")) or p0
                    yield Event(idx,clock,dur,op,p)
                    clock+=dur
                pending=None

def summarize(path:Path,entry:str):
    writes=collections.Counter(); examples={}; total_events=0; total_writes=0; purges=0
    active=None; response=[]; tx=[]
    for ev in events_from_zip(path,entry):
        total_events+=1
        if ev.operation=="IOCTL_SERIAL_PURGE":
            purges+=1
        elif ev.operation=="IRP_MJ_WRITE":
            if active is not None:
                tx.append({"request":active.payload_hex,"request_index":active.index,"clock":active.clock_before,"response":" ".join(response)})
            active=ev; response=[]; total_writes+=1
            if ev.payload_hex:
                writes[ev.payload_hex]+=1
                examples.setdefault(ev.payload_hex,{"first_index":ev.index,"first_clock":ev.clock_before})
        elif ev.operation=="IRP_MJ_READ" and active is not None and ev.payload_hex:
            response.append(ev.payload_hex)
    if active is not None:
        tx.append({"request":active.payload_hex,"request_index":active.index,"clock":active.clock_before,"response":" ".join(response)})
    return {"events":total_events,"writes":total_writes,"purges":purges,"distinct_requests":len(writes),"request_counts":dict(writes),"examples":examples,"transactions":len(tx)}

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--out",type=Path,required=True); args=ap.parse_args()
    canon=json.loads(CANON.read_text(encoding="utf-8-sig")); seeds=json.loads(SEEDS.read_text(encoding="utf-8"))
    rows={x["id"]:x for x in canon["inputs"] if x["kind"]=="zip+raw-log"}
    arow=rows["portmon-autocal-1"]; brow=rows["portmon-lognovo-1"]
    a=summarize(ROOT/arow["repo_path"],arow["raw_entry"])
    b=summarize(ROOT/brow["repo_path"],brow["raw_entry"])
    frames=set(a["request_counts"])|set(b["request_counts"])
    candidates=[]
    request_catalog=[]
    for frame in frames:
        ca=a["request_counts"].get(frame,0); cb=b["request_counts"].get(frame,0)
        ra=ca/max(1,a["writes"]); rb=cb/max(1,b["writes"])
        ratio=(ra+1e-12)/(rb+1e-12)
        request_catalog.append({
            "frame":frame,
            "autocal_count":ca,
            "baseline_count":cb,
            "autocal_example":a["examples"].get(frame),
            "baseline_example":b["examples"].get(frame),
        })
        enriched=(ca>=2 and (cb==0 or ra>=rb*2.0))
        if enriched:
            candidates.append({"frame":frame,"autocal_count":ca,"baseline_count":cb,"autocal_rate":ra,"baseline_rate":rb,"rate_ratio":ratio,"autocal_example":a["examples"].get(frame),"baseline_example":b["examples"].get(frame)})
    candidates.sort(key=lambda x:(x["baseline_count"]==0,x["rate_ratio"],x["autocal_count"]),reverse=True)
    request_catalog.sort(key=lambda x:(x["autocal_count"]+x["baseline_count"],x["frame"]),reverse=True)
    leads=[]
    for lead in seeds.get("protocol_leads",[]):
        f=lead["frame"]
        leads.append({**lead,"autocal_count":a["request_counts"].get(f,0),"baseline_count":b["request_counts"].get(f,0),"observed_autocal":a["request_counts"].get(f,0)>0,"observed_baseline":b["request_counts"].get(f,0)>0})
    out={
        "schema":"omegas.atlas.portmon-index.v2",
        "clock_semantics":"cumulative completed-operation duration; no Portmon field is treated as absolute timestamp",
        "autocal":{k:v for k,v in a.items() if k not in ("request_counts","examples")},
        "baseline":{k:v for k,v in b.items() if k not in ("request_counts","examples")},
        "request_catalog":request_catalog,
        "candidate_count":len(candidates),
        "candidates":candidates[:256],
        "protocol_leads":leads,
    }
    args.out.mkdir(parents=True,exist_ok=True)
    (args.out/"portmon-index.json").write_text(json.dumps(out,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps({"autocal":out["autocal"],"baseline":out["baseline"],"requests":len(request_catalog),"candidates":len(candidates),"lead_observations":leads},indent=2))

if __name__=="__main__": main()
