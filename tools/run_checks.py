#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

python_tests = sorted((ROOT / "tests").glob("test_*.py"))
ui_tests = sorted((ROOT / "tests" / "ui").glob("*.test.cjs"))

if not python_tests:
    raise SystemExit("QUALITY_GATE_DISCOVERY_ERROR: no Python contracts found")
if not ui_tests:
    raise SystemExit("QUALITY_GATE_DISCOVERY_ERROR: no UI tests found")

commands = [
    [sys.executable, "-B", str(path.relative_to(ROOT))]
    for path in python_tests
]
commands += [
    ["node", "--test", str(path.relative_to(ROOT))]
    for path in ui_tests
]

print(
    f"QUALITY_GATE_DISCOVERED python={len(python_tests)} ui={len(ui_tests)} total={len(commands)}",
    flush=True,
)

for command in commands:
    print("+", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)

print("QUALITY_GATE_FAST=PASS", flush=True)
