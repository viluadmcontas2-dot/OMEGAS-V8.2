#!/usr/bin/env python3
import argparse, collections, json
from pathlib import Path

ALLOWED={"PROVEN","REFUTED","ESCALATE","BROKEN"}

def read_receipts(root):
    rows=[]
    for p in sorted(Path(root).rglob("*.json")):
        try:r=json.loads(p.read_text(encoding="utf-8"))
        except Exception:continue
        if isinstance(r,dict) and r.get("schema")=="omegas.atlas.receipt.v1":
            rows.append(r)
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--receipts",required=True)
    ap.add_argument("--round",type=int,required=True)
    ap.add_argument("--output",required=True)
    ap.add_argument("--frontier",required=True)
    args=ap.parse_args()
    rows=read_receipts(args.receipts)
    if not rows: raise SystemExit("no receipts")
    forbidden=[r for r in rows if r.get("status") not in ALLOWED]
    if forbidden: raise SystemExit("forbidden receipt status")
    broken=[r for r in rows if r["status"]=="BROKEN"]
    fingerprints=[r.get("attempt_fingerprint") for r in rows]
    dup=[k for k,v in collections.Counter(fingerprints).items() if k and v>1]
    if dup: raise SystemExit(f"duplicate investigation attempt fingerprint(s): {dup}")

    by_target=collections.defaultdict(list)
    for r in rows:by_target[r["target_id"]].append(r)
    lanes=[]; resolutions={}; contradictions=[]

    for target, group in sorted(by_target.items()):
        if target=="fixture:escalation":
            if any(r["driver"]=="fixture-resolve" and r["status"]=="PROVEN" for r in group):
                resolutions[target]="PROVEN"
            elif any(r["status"]=="ESCALATE" for r in group):
                lanes.append({"id":f"r{args.round+1}-fixture-resolve","driver":"fixture-resolve","target":target})
                resolutions[target]="ESCALATE"
            continue
        if target=="fixture:contradiction":
            values={c.get("value") for r in group for c in r.get("claims",[]) if c.get("kind")=="harness-choice"}
            if any(r["driver"]=="fixture-challenger" and r["status"]=="PROVEN" for r in group):
                resolutions[target]="PROVEN"
            elif len(values)>1:
                contradictions.append({"target":target,"values":sorted(values)})
                lanes.append({"id":f"r{args.round+1}-fixture-challenger","driver":"fixture-challenger","target":target})
                resolutions[target]="ESCALATE"
            continue

        presence=[r for r in group if r["status"]=="PROVEN" and any(c.get("kind")=="presence" and c.get("value") is True for c in r.get("claims",[]))]
        engines={r["driver"] for r in presence}
        if len(engines)>=2:
            resolutions[target]="PROVEN"
        else:
            tried={r["driver"] for r in group}
            for driver in ["bytestring","gnu-strings","pe-map","undelphi"]:
                if driver not in tried:
                    lanes.append({"id":f"r{args.round+1}-{target.lower().replace(':','-')}-{driver}","driver":driver,"target":target})
                    resolutions[target]="ESCALATE"
                    break
            else:
                resolutions[target]="ESCALATE"

    # De-duplicate planned attempts by (target, driver).
    unique=[];seen=set()
    for lane in lanes:
        key=(lane["target"],lane["driver"])
        if key not in seen:
            seen.add(key);unique.append(lane)
    lanes=unique
    out={
        "schema":"omegas.atlas.reconcile.v1","round":args.round,
        "receipts":len(rows),"broken":len(broken),"contradictions":contradictions,
        "resolutions":resolutions,"frontier_count":len(lanes),
        "has_next":bool(lanes),"matrix":{"include":lanes},
    }
    Path(args.output).write_text(json.dumps(out,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    Path(args.frontier).write_text(json.dumps({"lanes":lanes},indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(out,indent=2,ensure_ascii=False))
    if broken: raise SystemExit("BROKEN receipt present")

if __name__=="__main__":
    main()
