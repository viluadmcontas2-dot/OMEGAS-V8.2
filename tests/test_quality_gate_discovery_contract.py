#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
gate = (ROOT / "tools/run_checks.py").read_text(encoding="utf-8")

assert 'glob("test_*.py")' in gate
assert 'glob("*.test.cjs")' in gate
assert "QUALITY_GATE_DISCOVERED" in gate
assert "python_tests = sorted" in gate
assert "ui_tests = sorted" in gate
assert "test_red_hotfix_contract.py" not in gate
assert "verde-dashboard-now.test.cjs" not in gate

print("QUALITY_GATE_DISCOVERY_CONTRACT=PASS")
