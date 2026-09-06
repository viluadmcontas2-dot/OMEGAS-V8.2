from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLUE = (ROOT / ".github/workflows/blue-ci.yml").read_text(encoding="utf-8")
LEGACY = (ROOT / ".github/workflows/red-fast-learning-one-shot.yml").read_text(encoding="utf-8")
BRANCH = "work/omegas-blue-causal-engine"

assert "name: OMEGAS Blue CI" in BLUE, "Blue workflow identity changed unexpectedly"
assert BRANCH in BLUE, "Blue CI must remain the canonical workflow for the Blue branch"
assert BRANCH not in LEGACY, "legacy RED-BLUE workflow still duplicates the Blue branch CI"
assert "hotfix/v8.0-red-performance" in LEGACY, "legacy RED workflow must still protect the RED hotfix branch"

print("BLUE_CI_SINGLE_AUTHORITY_CONTRACT=PASS")
