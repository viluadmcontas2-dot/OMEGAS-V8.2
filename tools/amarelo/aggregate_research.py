#!/usr/bin/env python3
import collections, json
from pathlib import Path

root=Path("research-receipts")
rows=[]
for p in sorted(root.rglob("*.json")):
    try:
        r=json.loads(p.read_text(encoding="utf-8"))
        if isinstance(r,dict) and "status" in r:
            rows.append(r)
    except Exception:
        pass

counts=collections.Counter(r.get("status","MISSING") for r in rows)
drivers=collections.Counter(r.get("driver","verde-forensic") for r in rows)
red=[r for r in rows if r.get("status") in {"RED","BROKEN"}]

summary={
    "schema":"omegas.amarelo.research-summary.v1",
    "receipts":len(rows),
    "statuses":dict(counts),
    "drivers":dict(drivers),
    "findings":rows
}
Path("research-summary.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

lines=[
"# OMEGAS Amarelo — Research Farm wave 001",
"",
f"Receipts: **{len(rows)} / 160**",
f"PASS: **{counts['PASS']}** · INFO: **{counts['INFO']}** · RED: **{counts['RED']}** · BROKEN: **{counts['BROKEN']}**",
"",
"## Drivers",
]
for k,v in sorted(drivers.items()):
    lines.append(f"- {k}: {v}")
lines += ["","## RED / BROKEN"]
if red:
    for r in red[:80]:
        lines.append(f"- \`{r.get('lane')}\` — **{r.get('status')}** — {r.get('summary','')}")
else:
    lines.append("- none")
lines += ["","## Interpretation","","RED is a research finding, not infrastructure failure. BROKEN means the lane itself was not reproducible.",""]
Path("research-summary.md").write_text("\n".join(lines),encoding="utf-8")
if counts["BROKEN"]:
    raise SystemExit("one or more research lanes are BROKEN")
