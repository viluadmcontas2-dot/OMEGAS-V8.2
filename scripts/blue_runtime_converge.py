#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/main/assets/ui",
    ROOT / "app/src/test/java",
    ROOT / "tests",
]
PATTERN = re.compile(r"V7|v7|legacy|legado|compatib|Advisor|Predictor|AutoMatch")
rows = []
for base in SCAN_ROOTS:
    if not base.exists():
        continue
    for path in sorted(p for p in base.rglob("*") if p.is_file() and p.suffix in {".kt", ".java", ".js", ".cjs", ".py"}):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        matches = []
        for line_number, line in enumerate(lines, start=1):
            if PATTERN.search(line):
                matches.append(f"{line_number}:{line.strip()}")
        if matches:
            rows.append(str(path.relative_to(ROOT)))
            rows.extend(f"  {match}" for match in matches[:80])

out = ROOT / "blue-migration-inventory.txt"
out.write_text("\n".join(rows) + "\n", encoding="utf-8")
print(f"inventory entries={len(rows)}")
# tracked-report trigger
