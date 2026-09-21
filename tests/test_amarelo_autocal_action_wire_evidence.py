import json
from pathlib import Path

FIXTURE = Path("tests/fixtures/amarelo-autocal-action-wire-v1.json")


def test_native_action_wire_evidence_contract():
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["schema"] == "omegas.amarelo.autocal-action-wire.v1"
    assert data["binary"]["bridge_va"] == "0x512280"
    assert data["binary"]["dispatcher_va"] == "0x517568"
    assert {row["action"] for row in data["binary"]["dispatcher_calls"]} == {1, 2, 4, 8}

    actions = {row["code"]: row for row in data["actions"]}
    assert set(actions) == {1, 2, 4, 8}
    for code, row in actions.items():
        raw = bytes.fromhex(row["request"])
        assert raw[:3] == bytes([0x02, 0x24, 0x04])
        assert raw[3] == code
        assert raw[-1] == (sum(raw[:-1]) & 0xFF)
        assert row["classification"] == "PROVEN"

    assert actions[4]["raw_observed"] is True
    assert len(actions[4]["observations"]) == 2
    assert all(not actions[c]["raw_observed"] for c in (1, 2, 8))

    scan = data["checksum_scan"]
    assert scan["portmon_autocal"]["writes"] == scan["portmon_autocal"]["additive_ok"]
    assert scan["portmon_lognovo"]["writes"] - scan["portmon_lognovo"]["additive_ok"] == 2
    assert {e["bytes"] for e in scan["portmon_lognovo"]["exceptions"]} == {"00"}
