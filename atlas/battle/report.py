#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--states",type=Path,required=True);ap.add_argument("--report",type=Path,required=True);ap.add_argument("--closure",type=Path,required=True);args=ap.parse_args()
    states=[]
    for p in args.states.rglob("state.json"):
        try:states.append(json.loads(p.read_text(encoding="utf-8")))
        except Exception:pass
    if not states:raise SystemExit("no battle state artifacts")
    s=max(states,key=lambda x:int(x.get("wave",0)))
    targets=list(s.get("targets",{}).values())
    counts={}
    kinds={}
    for t in targets:
        counts[t["status"]]=counts.get(t["status"],0)+1
        kinds[t["kind"]]=kinds.get(t["kind"],0)+1
    cov=s.get("coverage",{})
    open_count=sum(1 for t in targets if t["status"]!="PROVEN")
    complete=(open_count==0 and cov.get("graph_functions_unseen",1)==0 and cov.get("broken",1)==0 and cov.get("stalled_targets",1)==0)
    lines=[
        "# OMEGAS Atlas — Battle Royale status","",
        f"Last reconciled wave: **{s.get('wave')}**",
        f"Targets tracked: **{len(targets)}**",
        f"By kind: **{json.dumps(kinds,sort_keys=True)}**",
        f"By state: **{json.dumps(counts,sort_keys=True)}**",
        f"Native graph functions: **{cov.get('graph_functions_total','?')}**",
        f"Native graph functions not yet admitted to battle: **{cov.get('graph_functions_unseen','?')}**",
        f"Stalled ESCALATE targets: **{cov.get('stalled_targets','?')}**",
        f"BROKEN: **{cov.get('broken','?')}**","",
        f"Closure gate: **{'PASS' if complete else 'OPEN'}**",
        "",
        "No UNKNOWN state is used. Any unresolved item remains ESCALATE and the Atlas must not be declared complete."
    ]
    args.report.write_text("\n".join(lines)+"\n",encoding="utf-8")
    args.closure.write_text(json.dumps({"complete":complete,"open_targets":open_count,"wave":s.get("wave"),"coverage":cov},indent=2)+"\n",encoding="utf-8")
    print("\n".join(lines))

if __name__=="__main__":main()
