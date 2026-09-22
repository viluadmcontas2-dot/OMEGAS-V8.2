#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SEEDS=ROOT/"atlas/manifests/autocal-seeds.json"

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
    if len(funcs)<3: raise SystemExit(f"insufficient native graph: {len(funcs)} functions")
    seed=json.loads(SEEDS.read_text(encoding="utf-8"))
    port=json.loads((args.indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    symbols=seed["symbols"][:24]
    frames=port.get("candidates",[])[:8]
    capacity=256-(2*len(symbols)+2*len(frames))
    fn_count=max(0,capacity//2)
    funcs=funcs[:fn_count]
    lanes=[]; seq=0
    def add(kind,target,driver,label=""):
        nonlocal seq
        seq+=1; lanes.append({"id":f"{seq:03d}-{sid(driver)}-{sid(label or target)}","kind":kind,"target":target,"driver":driver})
    for f in funcs:
        add("function",f["entry"],"ghidra-fn",f["entry"]); add("function",f["entry"],"objdump-fn",f["entry"])
    for s in symbols:
        add("symbol",s["term"],"raw-symbol",s["term"]); add("symbol",s["term"],"undelphi-symbol",s["term"])
    for fr in frames:
        add("frame",fr["frame"],"portmon-frame","frame"); add("frame",fr["frame"],"binary-frame","frame")
    if len(lanes)>256: raise SystemExit("matrix exceeds 256 lanes")
    out={"include":lanes}
    args.output.parent.mkdir(parents=True,exist_ok=True); args.output.write_text(json.dumps(out,separators=(",",":"))+"\n",encoding="utf-8")
    print(json.dumps({"functions_in_graph":len(parse_functions(args.indices/"ghidra/functions.tsv")),"function_targets":len(funcs),"symbols":len(symbols),"frames":len(frames),"lanes":len(lanes)},indent=2))

if __name__=="__main__": main()
