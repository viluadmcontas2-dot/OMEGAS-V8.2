#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/omegas"
PRODUCT_ROOT = SOURCE_ROOT / "prohub"

# Production has one didactic namespace. Parallel architecture namespaces are not allowed.
source_namespaces = sorted(
    item.name for item in SOURCE_ROOT.iterdir()
    if item.is_dir()
)
assert source_namespaces == ["prohub"], (
    "production source must have one namespace rooted at com.omegas.prohub; "
    f"found {source_namespaces}"
)

# Release/protocol versions may exist as data. Architecture versions may not exist
# as packages, filenames, imports or declared type names.
versioned_file = re.compile(r"(?:^|[^A-Za-z0-9])V\d+(?:[^A-Za-z0-9]|$)", re.I)
versioned_import = re.compile(r"^\s*import\s+com\.omegas\.v\d+(?:\.|$)", re.I)
versioned_declaration = re.compile(
    r"^\s*(?:data\s+class|enum\s+class|sealed\s+class|class|object|interface)\s+[A-Za-z0-9_]*V\d+[A-Za-z0-9_]*\b",
    re.I,
)
violations = []
for source in sorted(PRODUCT_ROOT.rglob("*.kt")) + sorted(PRODUCT_ROOT.rglob("*.java")):
    relative = source.relative_to(ROOT)
    if versioned_file.search(source.stem):
        violations.append(f"versioned filename: {relative}")
    for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1):
        if versioned_import.search(line) or versioned_declaration.search(line):
            violations.append(f"versioned architecture reference: {relative}:{line_number}")
    if len(violations) >= 40:
        break

assert not violations, "\n".join(violations)
print("BLUE_SOURCE_ARCHITECTURE_CONTRACT=PASS")
