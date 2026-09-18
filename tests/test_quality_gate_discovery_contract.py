#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
gate = (ROOT / "tools/run_checks.py").read_text(encoding="utf-8")

assert 'glob("test_*.py")' in gate
assert 'glob("*.test.cjs")' in gate
assert "QUALITY_GATE_DISCOVERED" in gate
assert "QUALITY_GATE_FAST=PASS" in gate
assert "QUALITY_GATE_FULL=PASS" in gate
assert 'GRADLE_MARKER = "@requires-gradle-test-results"' in gate
assert '"--full"' in gate
assert "test_red_hotfix_contract.py" not in gate
assert "verde-dashboard-now.test.cjs" not in gate

marker = "@requires-gradle-test-results"
for path in sorted((ROOT / "tests" / "ui").glob("*.test.cjs")):
    source = path.read_text(encoding="utf-8")
    if "app/build/" in source:
        assert marker in source, f"{path.name} reads app/build without declaring {marker}"

print("QUALITY_GATE_DISCOVERY_CONTRACT=PASS")
