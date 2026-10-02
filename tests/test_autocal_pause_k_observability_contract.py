from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")


def test_pause_gates_acquisition_refresh_but_not_reference_k_refresh():
    assert "acquisitionEnabled && refreshDue.acquisition" in MONITOR
    reference_line = next(line for line in MONITOR.splitlines() if "val referenceRefresh = if" in line)
    assert "acquisitionEnabled" not in reference_line
    assert "refreshDue.reference" in reference_line


def test_mul_act_remains_in_reference_refresh_group():
    start = MONITOR.index("private val REFERENCE_REFRESH_FIELDS")
    block = MONITOR[start:start + 600]
    assert "AutoCalProtocol.MUL_ACT" in block
    assert "AutoCalProtocol.PETR_INJ_TBP" in block
    assert "AutoCalProtocol.GAS_MNFLD_PRESS_RV" in block


def test_pause_is_not_encoded_as_k_reset():
    paused = MONITOR.split('state = baseState(if (enabled == 0)', 1)[1][:1000]
    assert "RESET_K_FACTOR" not in paused
    assert "write" not in paused.lower()


if __name__ == "__main__":
    test_pause_gates_acquisition_refresh_but_not_reference_k_refresh()
    test_mul_act_remains_in_reference_refresh_group()
    test_pause_is_not_encoded_as_k_reset()
    print("AUTOCAL_PAUSE_K_OBSERVABILITY_CONTRACT=PASS")
