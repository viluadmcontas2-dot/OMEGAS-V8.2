#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/omegas"
PRODUCT_ROOT = SOURCE_ROOT / "prohub"

# Production has one didactic namespace. Parallel architecture namespaces are not allowed.
source_namespaces = sorted(
    path.name for path in SOURCE_ROOT.iterdir()
    if path.is_dir()
)
assert source_namespaces == ["prohub"], (
    "production source must have one namespace rooted at com.omegas.prohub; "
    f"found {source_namespaces}"
)

# Architecture/version numbers do not belong in production type names or references.
# Hardware/protocol numbers remain valid (for example MP48); this gate targets V<number> tags only.
version_tag = re.compile(r"V\d+")
violations = []
for path in sorted(PRODUCT_ROOT.rglob("*.kt")) + sorted(PRODUCT_ROOT.rglob("*.java")):
    relative = path.relative_to(ROOT)
    if version_tag.search(path.stem):
        violations.append(f"versioned filename: {relative}")
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if version_tag.search(line):
            violations.append(f"versioned source reference: {relative}:{line_number}")
            if len(violations) >= 40:
                break
    if len(violations) >= 40:
        break

assert not violations, "\n".join(violations)

print("BLUE_SOURCE_ARCHITECTURE_CONTRACT=PASS")
