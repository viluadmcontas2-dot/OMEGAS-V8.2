#!/usr/bin/env python3
import argparse, collections, json
from pathlib import Path

def rows(root):
    out=[]
    for p in Path(root).rglob("*.json"):
        try:r=json.loads(p.read_text(encoding="utf-8"))
        except Exception:continue
        if isinstance(r,dict) and r.get("schema")=="omegas.atlas.receipt.v1":out.append(r)
    return out

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--receipts",required=True)
    ap.add_argument("--report",required=True)
    args=ap.parse_args()
    rs=rows(args.receipts)
    broken=[r for r in rs if r.get("status")=="BROKEN"]
    if broken:raise SystemExit(f"BROKEN receipts: {[r.get('lane') for r in broken]}")
    ui=[r for r in rs if r.get("target_id")=="TAutoCalUI" and r.get("status")=="PROVEN"]
    engines={r.get("driver") for r in ui}
    if len(engines)<3:
        raise SystemExit(f"TAutoCalUI independent engines <3: {sorted(engines)}")
    esc_ok=any(r.get("target_id")=="fixture:escalation" and r.get("driver")=="fixture-resolve" and r.get("status")=="PROVEN" for r in rs)
    chal_ok=any(r.get("target_id")=="fixture:contradiction" and r.get("driver")=="fixture-challenger" and r.get("status")=="PROVEN" for r in rs)
    if not esc_ok:raise SystemExit("ESCALATE fixture did not auto-resolve in round 2")
    if not chal_ok:raise SystemExit("contradiction fixture did not spawn challenger")
    fps=[r.get("attempt_fingerprint") for r in rs]
    dups=[x for x,n in collections.Counter(fps).items() if x and n>1]
    if dups:raise SystemExit("duplicate attempts survived")
    lines=[
        "# OMEGAS Atlas — vertical slice PASS","",
        f"Receipts: **{len(rs)}**",
        f"TAutoCalUI independent PROVEN engines: **{len(engines)}** — {', '.join(sorted(engines))}",
        "ESCALATE -> second-round alternate method: **PASS**",
        "Contradiction -> challenger: **PASS**",
        "Duplicate attempt suppression: **PASS**",
        "BROKEN receipts: **0**","",
        "The large battle-royale wave is now permitted by the architecture gate.",
    ]
    Path(args.report).write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("\n".join(lines))

if __name__=="__main__":
    main()
