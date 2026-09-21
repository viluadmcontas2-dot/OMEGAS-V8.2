#!/usr/bin/env python3
import json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
KEYWORDS=re.compile(r"(dashboard|learning|map|curve|obd|session|reconnect|telemetry|runtime|bridge|autocal|usb|evidence|predictor|suggestion)", re.I)
lanes=[]

for p in sorted((ROOT/"tests").glob("test_*.py")):
    if KEYWORDS.search(p.name):
        lanes.append({"id":"py_"+p.stem,"kind":"python","target":str(p.relative_to(ROOT)),"os":"ubuntu-latest"})

for p in sorted((ROOT/"tests/ui").glob("*.test.cjs")):
    if not KEYWORDS.search(p.name):
        continue
    source=p.read_text("utf-8",errors="ignore")
    kind="jvm_node" if "@requires-gradle-test-results" in source else "node"
    lanes.append({"id":"node_"+p.stem.replace(".test",""),"kind":kind,"target":str(p.relative_to(ROOT)),"os":"ubuntu-latest"})

for p in sorted((ROOT/"app/src/test/java").rglob("*Test.kt")):
    rel=str(p.relative_to(ROOT))
    if not KEYWORDS.search(rel):
        continue
    src=p.read_text("utf-8",errors="ignore")
    pkg=re.search(r"^package\s+([\w.]+)",src,re.M)
    cls=re.search(r"\bclass\s+(\w+Test)\b",src)
    if pkg and cls:
        fqcn=f"{pkg.group(1)}.{cls.group(1)}"
        lanes.append({"id":"jvm_"+cls.group(1),"kind":"jvm","target":fqcn,"os":"ubuntu-latest"})

# Stable uniqueness and hard cap guard.
seen=set(); unique=[]
for lane in lanes:
    if lane["id"] in seen:
        continue
    seen.add(lane["id"]); unique.append(lane)

assert unique, "no global reality lanes discovered"
assert len(unique) <= 256, f"matrix exceeds GitHub cap: {len(unique)}"
print(json.dumps({"include":unique},separators=(",",":")))
