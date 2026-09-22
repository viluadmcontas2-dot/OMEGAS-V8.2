#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import semantic_targets

SEEDS=ROOT/"atlas/manifests/autocal-seeds.json"

DRIVERS={
    "method":["delphi-method","capstone-method"],
    "field":["delphi-field","raw-field-name"],
    "field-use":["ghidra-field-use","capstone-field-use"],
    "event":["delphi-event"],
    "action":["delphi-action"],
    "serial":["delphi-serial","pe-serial-resource"],
    "visual":["delphi-visual","pe-visual-resource"],
}

def parse_functions(path:Path):
    rows=[]
    lines=path.read_text(encoding="utf-8",errors="replace").splitlines()
    for line in lines[1:]:
        p=line.split("\t")
        if len(p)<8: continue
        rows.append({"entry":p[0],"distance":int(p[1]),"min":p[2],"max":p[3],"name":p[4],"boundary":p[5]=="true","callers":p[6],"callees":p[7]})
    return rows

def sid(s):
    return re.sub(r"[^A-Za-z0-9_-]+","-",s).strip("-")[:48] or "target"

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--indices",type=Path,required=True); ap.add_argument("--output",type=Path,required=True); args=ap.parse_args()
    funcs=parse_functions(args.indices/"ghidra/functions.tsv")
    if len(funcs)<3: raise SystemExit(f"insufficient native graph: {len(funcs)}")
    sem=semantic_targets.build(semantic_targets.load(args.indices/"ghidra/autocal-semantics.json"))
    seed=json.loads(SEEDS.read_text(encoding="utf-8"))
    port=json.loads((args.indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    lanes=[];seq=0
    def add(kind,target,driver,label=""):
        nonlocal seq
        if len(lanes)>=256:return False
        seq+=1;lanes.append({"id":f"{seq:03d}-{sid(driver)}-{sid(label or target)}","kind":kind,"target":target,"driver":driver});return True

    semantic_targets_admitted=0
    for target in sem:
        ds=DRIVERS.get(target["kind"],[])
        if len(lanes)+len(ds)>256:break
        for driver in ds:add(target["kind"],target["target"],driver,target["target"])
        semantic_targets_admitted+=1

    # Spare capacity goes to independent protocol/symbol challenges, never generic graph churn.
    if len(lanes)<256:
        for s in seed["symbols"][:24]:
            if len(lanes)+2>256:break
            add("symbol",s["term"],"raw-symbol",s["term"]);add("symbol",s["term"],"undelphi-symbol",s["term"])
    if len(lanes)<256:
        for fr in port.get("candidates",[])[:16]:
            if len(lanes)+2>256:break
            add("frame",fr["frame"],"portmon-frame","frame");add("frame",fr["frame"],"binary-frame","frame")

    if not lanes or len(lanes)>256: raise SystemExit(f"invalid initial matrix size: {len(lanes)}")
    out={"include":lanes}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(out,separators=(",",":"))+"\n",encoding="utf-8")
    print(json.dumps({"semantic_targets_total":len(sem),"semantic_targets_admitted":semantic_targets_admitted,"functions_catalogued":len(funcs),"lanes":len(lanes)},indent=2))

if __name__=="__main__": main()
