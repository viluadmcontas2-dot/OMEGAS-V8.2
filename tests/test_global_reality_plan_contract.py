import json, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
out=subprocess.check_output(["python3","tools/ci/global_reality_plan.py"],cwd=ROOT,text=True)
matrix=json.loads(out)["include"]
assert 1 <= len(matrix) <= 256
ids=[x["id"] for x in matrix]
assert len(ids)==len(set(ids))
for surface in ("dashboard","learning","map","curve","obd","session","telemetry"):
    assert any(surface in (x["id"]+" "+x["target"]).lower() for x in matrix), surface
print(f"GLOBAL_REALITY_PLAN=PASS lanes={len(matrix)}")
