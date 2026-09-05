#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

python_tests = sorted((ROOT / "tests").glob("test_*.py"))
node_tests = sorted((ROOT / "tests/ui").glob("*.test.cjs"))

commands = [
    [sys.executable, "-B", str(test.relative_to(ROOT))]
    for test in python_tests
]
commands += [
    ["node", "--test", str(test.relative_to(ROOT))]
    for test in node_tests
]

if not commands:
    raise SystemExit("Nenhum teste rápido atual foi encontrado")

for command in commands:
    print("+", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)

print(f"QUALITY_GATE_FAST=PASS python={len(python_tests)} node={len(node_tests)}", flush=True)
