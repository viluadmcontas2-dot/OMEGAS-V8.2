from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KFACTOR = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text(encoding="utf-8")
AUTOCAL = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")


def test_reset_k_truth_is_progbase_mul_act_one_not_ee_surface():
    assert "ActionResetKFactorExecute" in KFACTOR
    assert "MUL_ACT" in KFACTOR
    assert "1.0" in KFACTOR
    assert "NÃO é a réplica" not in KFACTOR
    assert "VECT_AUTOCAL_EE" not in KFACTOR
    assert 'writeIndexedU16(MUL_ACT.address, index, 0x4000)' in PROTOCOL
    assert "actual.all { it == 0x4000 }" in AUTOCAL


def test_curve_k_batch_uses_one_direct_serial_unit_and_one_final_readback():
    start = KFACTOR.index("private fun executeBatch")
    end = KFACTOR.index("private fun readRawPoints", start)
    body = KFACTOR[start:end]

    assert 'reason = "escrita direta Curva K"' in body
    assert "KFactorProtocol.writeFactor(index, targetRaw)" in body
    assert body.count("serial.unit(") == 1
    assert body.count("KFactorProtocol.readFactors()") >= 2
    assert "appendHistory(event)" not in body
    assert "appendHistoryBatch(confirmed)" in body


def test_curve_k_history_is_persisted_once_per_batch():
    assert "private fun appendHistoryBatch(items: JSONArray)" in KFACTOR
    assert "private fun appendHistory(event: JSONObject)" not in KFACTOR


if __name__ == "__main__":
    test_reset_k_truth_is_progbase_mul_act_one_not_ee_surface()
    test_curve_k_batch_uses_one_direct_serial_unit_and_one_final_readback()
    test_curve_k_history_is_persisted_once_per_batch()
    print("CURVE_K_DIRECT_PARITY_CONTRACT=PASS")
