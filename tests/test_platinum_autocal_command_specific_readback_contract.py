from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
FIXTURE = json.loads((ROOT / "tests/fixtures/platinum-autocal-action-parity-v1.json").read_text(encoding="utf-8"))
OLD_DOC = (ROOT / "docs/incidents/2026-09-19-autocal-final-byte-matrix.md").read_text(encoding="utf-8")
OLD_WU = json.loads((ROOT / "docs/evidence/OMEGAS-WU-006.json").read_text(encoding="utf-8"))


def test_each_mutation_has_action_scoped_readback_witnesses():
    required = (
        "Action.RESET_PETROL -> petrolAcquisitionReadbackFields()",
        "Action.RESET_GAS -> gasAcquisitionReadbackFields()",
        "Action.MANUAL_AUTOMATCH -> listOf(AutoCalProtocol.MUL_ACT)",
        "Action.FINISH_AUTOCAL, Action.FINISH_AUTOMATCH -> listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)",
        "Action.RESET_K_FACTOR -> listOf(AutoCalProtocol.MUL_ACT)",
        "Action.DELETE_POINT ->",
        "missing.joinToString(\", \") { it.key }",
    )
    for token in required:
        assert token in MANAGER
    assert "Action.RESET_ALL -> petrolAcquisitionReadbackFields() +" in MANAGER
    assert "gasAcquisitionReadbackFields() +" in MANAGER
    assert '.put("readbackWitnesses", JSONArray(actionReadbackWitnesses(prepared).map { it.key }))' in MANAGER


def test_parity_fixture_does_not_use_generic_snapshot_as_mutation_witness():
    actions = {item["name"]: item for item in FIXTURE["actions"]}
    assert "NUM_BUF_UPD_PETR" in actions["RESET_PETROL"]["readback"]
    assert "NUM_BUF_UPD_GAS" in actions["RESET_GAS"]["readback"]
    assert "MUL_ACT" in actions["RESET_ALL"]["readback"]
    assert actions["MANUAL_AUTOMATCH"]["readback"].startswith("MUL_ACT")
    assert "AUTO_CAL_ENABLE" not in actions["DELETE_POINT_BATCH"]["readback"]


def test_historical_inverted_action_docs_are_superseded_and_corrected():
    assert "SUPERSEDED" in OLD_DOC
    assert "Manual AutoMatch=`0x08`" in OLD_DOC
    assert "Reset petrol=`0x01`" in OLD_DOC
    assert "Reset gas=`0x02`" in OLD_DOC
    assert "Reset all=`0x04`" in OLD_DOC

    assert OLD_WU["document_status"] == "SUPERSEDED"
    action_map = OLD_WU["progbase"]["action_map"]
    assert action_map["manual_automatch"]["mode"] == "0x08"
    assert action_map["reset_petrol"]["mode"] == "0x01"
    assert action_map["reset_gas"]["mode"] == "0x02"
    assert action_map["reset_all"]["mode"] == "0x04"
    assert action_map["modify_map_refs"]["mode"] is None
    assert "captured_reset_gas_effect" not in OLD_WU["p0_reset_safety"]
    assert "captured_reset_all_effect" in OLD_WU["p0_reset_safety"]


if __name__ == "__main__":
    test_each_mutation_has_action_scoped_readback_witnesses()
    test_parity_fixture_does_not_use_generic_snapshot_as_mutation_witness()
    test_historical_inverted_action_docs_are_superseded_and_corrected()
    print("PLATINUM_AUTOCAL_COMMAND_SPECIFIC_READBACK=PASS")
