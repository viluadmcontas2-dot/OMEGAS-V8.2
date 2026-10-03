#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = json.loads((ROOT / "tests/fixtures/progbase-autocal-action-map-v1.json").read_text(encoding="utf-8"))
ACTION = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
STATUS = (ROOT / "docs/archive/STATUS-ate-2026-10-03.md").read_text(encoding="utf-8")

assert ORACLE["classification"] == "ORIGINAL_DERIVED"
assert ORACLE["schema"] == "omegas.progbase.autocal-action-map.v2"

rows = {row["action"]: row for row in ORACLE["actions"]}
expected = {
    "MANUAL_AUTOMATCH": ("ActionAutoMatchExecute", "0x005189B4", "0x08", "02 24 04 08 32"),
    "RESET_PETROL": ("ActionResetPetrolExecute", "0x005189C0", "0x01", "02 24 04 01 2B"),
    "RESET_GAS": ("ActionResetGasExecute", "0x005189CC", "0x02", "02 24 04 02 2C"),
    "RESET_ALL": ("ActionResetAllExecute", "0x005189D8", "0x04", "02 24 04 04 2E"),
}

for name, values in expected.items():
    row = rows[name]
    assert (row["handler"], row["handlerVa"], row["mode"], row["frame"]) == values, (name, row)
    handler, _va, mode, frame = values
    assert f"{handler}`" in STATUS, f"STATUS arquivado lost {handler}"
    assert f"modo `{mode}`, frame `{frame}`" in STATUS, f"STATUS arquivado contradicts {name}"

rk = ORACLE["separateActions"]["resetKFactor"]
assert rk["handler"] == "ActionResetKFactorExecute"
assert rk["target"] == "MUL_ACT"
assert "1.0" in rk["operation"]
assert "ActionResetKFactorExecute` usa rota separada" in STATUS

modify_refs = ORACLE["separateActions"]["modifyMapRefs"]
assert modify_refs["handler"] == "ActionAutoCalRifExecute"
assert modify_refs["mode"] is None and modify_refs["frame"] is None
assert "ActionAutoCalRifExecute` = Modify map refs usa rota separada" in STATUS

assert "AUTOCAL_ACTION_COMMAND = 0x24" in PROTOCOL
assert "AUTOCAL_ACTION_SUBOP_CONTROL = 0x04" in PROTOCOL
for action, mode in (
    ("MANUAL_AUTOMATCH", "0x08"),
    ("RESET_PETROL", "0x01"),
    ("RESET_GAS", "0x02"),
    ("RESET_ALL", "0x04"),
):
    assert f"{action}({mode})" in PROTOCOL
    assert f"ManualActionMode.{action}" in ACTION

assert "MANUAL_AUTOMATCH" in ACTION
assert "requiresCriticalConfirmation" in ACTION
assert '.put("automatic", false)' in ACTION

print("PROGBASE_AUTOCAL_ACTION_ORACLE=PASS")
