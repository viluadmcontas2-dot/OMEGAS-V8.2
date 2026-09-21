import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
result = subprocess.run(
    ["python3", "tools/ci/autocal_forensic_plan.py"],
    cwd=ROOT,
    text=True,
    capture_output=True,
    check=True,
)
matrix = json.loads(result.stdout)
lanes = matrix["include"]
ids = [lane["id"] for lane in lanes]
tx = [lane for lane in lanes if lane["category"] == "transaction"]
meta = [lane for lane in lanes if lane["category"] != "transaction"]

assert len(lanes) == 252
assert len(tx) == 243
assert len(meta) == 9
assert len(ids) == len(set(ids))
assert all(lane["sequence"] > 0 for lane in tx)
assert len(lanes) <= 256
print("AUTOCAL_FORENSIC_PLAN_V2=PASS lanes=252 transactions=243 meta=9")
