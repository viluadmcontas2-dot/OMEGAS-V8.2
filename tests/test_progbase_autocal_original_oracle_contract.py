#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = ROOT / "tests/fixtures/progbase-autocal-action-map-v1.json"
ACTION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
INCIDENT = ROOT / "docs/incidents/2026-08-12-autocal-manual-automatch-fidelity.md"
BYTE_MATRIX = ROOT / "docs/incidents/2026-09-19-autocal-final-byte-matrix.md"
CONTROL_MATRIX = ROOT / "docs/incidents/2026-09-19-autocal-final-canonical-matrix.md"
CONSUMER_MAP = ROOT / "tests/fixtures/progbase-autocal-consumer-map-v1.json"
PARITY = ROOT / "tests/fixtures/omegas-autocal-progbase-parity-v1.json"

assert ORACLE.is_file(), "Original-derived ProgBase action oracle is missing"
oracle = json.loads(ORACLE.read_text(encoding="utf-8"))
assert oracle["classification"] == "ORIGINAL_DERIVED"
assert oracle["sources"]["progbase"]["sha256"].lower() == "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"
assert oracle["sources"]["lognovo"]["rawSha256"] == "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
assert oracle["sources"]["autocal"]["rawSha256"] == "4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b"

actions = {item["action"]: item for item in oracle["actions"]}
expected = {
    "MANUAL_AUTOMATCH": ("0x01", "02 24 04 01 2B"),
    "RESET_PETROL": ("0x02", "02 24 04 02 2C"),
    "RESET_GAS": ("0x04", "02 24 04 04 2E"),
}
for name, (mode, frame) in expected.items():
    assert actions[name]["mode"] == mode
    assert actions[name]["frame"] == frame
assert actions["RESET_ALL"]["mode"] is None
assert actions["RESET_ALL"]["frame"] is None

assert actions["MANUAL_AUTOMATCH"]["handler"] == "ActionAutoMatchExecute"
assert actions["RESET_PETROL"]["handler"] == "ActionResetPetrolExecute"
assert actions["RESET_GAS"]["handler"] == "ActionResetGasExecute"
assert actions["RESET_ALL"]["handler"] == "ActionResetAllExecute"
assert actions["RESET_ALL"]["portmonObserved"] is False
assert actions["RESET_PETROL"]["portmonObserved"] is False
assert actions["RESET_GAS"]["portmonObserved"] is True
assert actions["MANUAL_AUTOMATCH"]["portmonObserved"] is False
assert oracle["separateActions"]["modifyMapRefs"]["handler"] == "ActionAutoCalRifExecute"
assert oracle["separateActions"]["modifyMapRefs"]["mode"] == "0x08"
assert oracle["separateActions"]["modifyMapRefs"]["frame"] == "02 24 04 08 32"

fields = oracle["dfmFields"]
assert fields["VECT_AUTOCAL_U8_1"]["fileKeyName"] == "!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD"
assert fields["VECT_AUTOCAL_U8_2"]["fileKeyName"] == "MaxAutomatch"

action_source = ACTION.read_text(encoding="utf-8")
assert 'RESET_PETROL(\n            Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x02))' in action_source
assert 'RESET_GAS(\n            Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x04))' in action_source
assert "RESET_ALL(" not in action_source
bridge = BRIDGE.read_text(encoding="utf-8")
assert 'manualAutoMatchExposed", false' in bridge
assert "DESTRUCTIVE_RESET_INTERLOCK_MESSAGE" in bridge

incident = INCIDENT.read_text(encoding="utf-8").replace(chr(96), "")
assert "0x01 é Manual AutoMatch" not in incident
assert "0x08 é Modify map refs" not in incident
byte_matrix = BYTE_MATRIX.read_text(encoding="utf-8").replace(chr(96), "")
assert "Reset gasolina: corpo 02 24 04 02" not in byte_matrix
assert "Reset GNV: corpo 02 24 04 04" not in byte_matrix
control_matrix = CONTROL_MATRIX.read_text(encoding="utf-8").replace(chr(96), "")
assert "corpo nativo 02 24 04 02" not in control_matrix
assert "corpo nativo 02 24 04 04" not in control_matrix

consumer = json.loads(CONSUMER_MAP.read_text(encoding="utf-8"))
u165 = next(row for row in consumer["autocal_dm"] if row["address_hex"] == "0x0165")
assert u165["status"] == "SUBINDEX_SEMANTICS_PROVEN_FROM_PROGBASE_DFM"
assert u165["subindexes"]["1"]["fileKeyName"] == "!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD"
assert u165["subindexes"]["2"]["fileKeyName"] == "MaxAutomatch"
assert consumer["enable_finish"]["finish"]["status"] != "EXACT_0x0165_SUBINDEX_SEMANTICS_INCONCLUSIVE"
assert not any("Exact subindex semantics" in item for item in consumer["unresolved"])

parity = json.loads(PARITY.read_text(encoding="utf-8"))
row = next(item for item in parity["classifications"] if item["behavior"] == "0x0165 subindex semantics")
assert row["classification"] == "MATCH"
assert row["dimensions"]["original"] == "PROVEN_FROM_PROGBASE_DFM"

print("PROGBASE_AUTOCAL_ORIGINAL_ORACLE=PASS")
