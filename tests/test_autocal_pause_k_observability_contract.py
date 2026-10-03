from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")


PLANNER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt").read_text(encoding="utf-8")


def test_pause_gates_acquisition_refresh_but_not_reference_k_refresh():
    # Lote D: o gate mora no planner; a pausa fecha só a família de aquisição, a referência segue.
    assert "val acquisition = acquisitionEnabled && due.acquisition" in PLANNER
    assert "val reference = due.reference" in PLANNER
    assert "acquisitionEnabled = synchronized(lock) { autoCalEnabled } == 1" in MONITOR


def test_mul_act_remains_in_reference_refresh_group():
    start = PLANNER.index("G5_MUL_ACT(")
    block = PLANNER[start:start + 700]
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
