#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = json.loads((ROOT / "tests/fixtures/progbase-autocal-dump-contract-v2.json").read_text("utf-8"))
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text("utf-8")
POINT = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalPointDeleteProtocol.kt").read_text("utf-8")
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text("utf-8")

assert ORACLE["classification"] == "ORIGINAL_DERIVED"
assert ORACLE["authority"]["progbase_sha256"] == "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"

reset = ORACLE["protocol"]["resetKFactor"]
assert ORACLE["schema"] == "omegas.progbase.autocal-dump.v2"
assert ORACLE["authority"]["handlerVa"] == "0x0051A070"
assert ORACLE["authority"]["tautocaldmFieldOffset"] == "0x00A0"
assert ORACLE["authority"]["tautocaldmFieldName"] == "MUL_ACT"
assert 'Field("VECT_AUTOCAL_EE", 0x0164, Encoding.U16_LE, Shape.VECTOR, 4)' in PROTOCOL
assert 'Field("MUL_ACT", 0x0161, Encoding.Q14_U16_LE, Shape.VECTOR, 30' in PROTOCOL
assert "fun resetKFactorMulActFrames" in PROTOCOL
assert "writeIndexedU16(MUL_ACT.address, index, 0x4000)" in PROTOCOL
assert "RESET_K_FACTOR(" in MANAGER
assert "AutoCalProtocol.resetKFactorMulActFrames()" in MANAGER
assert "actual.all { it == 0x4000 }" in MANAGER
assert reset["target"] == "MUL_ACT"
assert reset["elements"] == 30
assert reset["rawValue"] == 16384
assert reset["firstFrame"] == "14 61 01 00 00 40 B6"
assert reset["lastFrame"] == "14 61 01 1D 00 40 D3"

delete = ORACLE["protocol"]["pointDelete"]
assert "writeMaskVector" in POINT
assert "AutoCalProtocol.writeVectorU8" in POINT
assert "writeMaskElement" not in POINT
assert "singlePointPlan" in POINT
assert "writeMaskVector(Fuel.GAS" in POINT
assert "writeMaskVector(Fuel.PETROL" in POINT
assert "commit()" in POINT
assert delete["commit"] == "01 24 05 2A"

for key, address in {
    "DIFF_ENG_SPD_THD": "0x0183",
    "DELTA_ENG_SPD_THD": "0x0184",
    "DIFF_MNFLD_PRESS_THD": "0x0185",
    "DELTA_MNFLD_PRESS_THD": "0x0186",
    "DIFF_PETR_TINJ_T_THD": "0x0187",
    "DELTA_PETR_INJ_T_THD": "0x0188",
    "DISABLE_ACQ_BAND": "0x018B",
}.items():
    assert f'Field("{key}", {address}' in PROTOCOL, key

# Fail closed: unresolved callback must not become a native wire action by accident.
assert "GO_TO_MIN_CALIBRATION" not in MANAGER

print("PROGBASE_AUTOCAL_DUMP_CONTRACT=PASS")
