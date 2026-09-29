from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "tests/fixtures/platinum-autocal-action-parity-v1.json"
FIXTURE_TEXT = FIXTURE_PATH.read_text(encoding="utf-8")
FIXTURE = json.loads(FIXTURE_TEXT)
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
POINTS = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalPointDeleteProtocol.kt").read_text(encoding="utf-8")
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
ANALYSIS = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoMatchSnapshotAnalysis.kt").read_text(encoding="utf-8")
COCKPIT = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")


def test_matrix_has_no_unclassified_host_mutation():
    actions = {item["name"]: item for item in FIXTURE["actions"]}
    expected = {
        "ENABLE_AUTO_CAL", "DISABLE_AUTO_CAL", "RESET_PETROL", "RESET_GAS",
        "RESET_ALL", "MANUAL_AUTOMATCH", "FINISH_AUTOCAL", "FINISH_AUTOMATCH",
        "RESET_K_FACTOR", "DELETE_POINT_BATCH",
    }
    assert set(actions) == expected
    assert all(item["automatic"] is False for item in actions.values())


def test_exact_native_action_frames_remain_bound():
    for frame in (
        "12 4A 01 01 5E",
        "12 4A 01 00 5D",
        "02 24 04 01 2B",
        "02 24 04 02 2C",
        "02 24 04 04 2E",
        "02 24 04 08 32",
    ):
        assert frame in FIXTURE_TEXT, frame

    assert "setEnabled(true)" in MANAGER
    assert "setEnabled(false)" in MANAGER
    assert "ManualActionMode.RESET_PETROL" in MANAGER
    assert "ManualActionMode.RESET_GAS" in MANAGER
    assert "ManualActionMode.RESET_ALL" in MANAGER
    assert "ManualActionMode.MANUAL_AUTOMATCH" in MANAGER


def test_finish_is_not_misrepresented_as_acquisition_stop():
    assert "Finish não desabilita AUTO_CAL_ENABLE" in MANAGER
    assert "Finalizar AutoCal confirma o contador nativo de AutoMatch, mas não pausa a aquisição" in COCKPIT
    assert "use Pausar aquisição e aguarde PAUSADA confirmada pela ECU" in COCKPIT
    assert "setEnabled(false)" in MANAGER


def test_reset_k_is_mul_act_30_q14_with_readback():
    assert 'Field("MUL_ACT", 0x0161' in PROTOCOL
    assert "resetKFactorMulActFrames" in PROTOCOL
    assert "writeIndexedU16(MUL_ACT.address, index, 0x4000)" in PROTOCOL
    assert "resetKFactorMulActFrames()" in MANAGER
    assert "actual.all { it == 0x4000 }" in MANAGER


def test_point_reacquisition_preserves_both_full_masks_and_single_commit():
    assert "POINT_COUNT = 18" in POINTS
    assert "PETROL_DELETE_ADDRESS = 0x016D" in POINTS
    assert "GAS_DELETE_ADDRESS = 0x016E" in POINTS
    assert "KEEP = 1" in POINTS
    assert "DELETE = 0" in POINTS
    assert "writeMaskVector(Fuel.GAS" in POINTS
    assert "writeMaskVector(Fuel.PETROL" in POINTS
    assert "fun commit()" in POINTS
    assert "byteArrayOf(0x01, 0x24, 0x05)" in POINTS


def test_native_automatch_and_inferred_math_are_not_writers():
    for source in (MONITOR, ANALYSIS):
        assert "KFactorManager" not in source
        assert "KWriteManager" not in source
        assert "Mp48WorkClass.MANUAL_WRITE" not in source
        assert "protocolTransaction(" not in source

    assert "appAutomaticWrite" in MONITOR
    assert "false" in MONITOR
    assert FIXTURE["autonomous"][0]["host_write_allowed"] is False


def test_predictor_and_v7_suggestion_path_is_manual_review_only():
    coordinator = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt").read_text(encoding="utf-8")
    writer = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/ExistingCalibrationWriterV7.kt").read_text(encoding="utf-8")

    assert "private val ecuWriter" not in coordinator
    assert "active.applySuggestionToEcu(" not in coordinator
    assert "MANUAL_REVIEW_REQUIRED" in coordinator
    assert "automaticWriteBlocked" in coordinator
    assert "writesStarted" in coordinator
    assert "LAB_ONLY" in writer
    assert "labOnlyWriterEnabled: Boolean = false" in writer


if __name__ == "__main__":
    test_matrix_has_no_unclassified_host_mutation()
    test_exact_native_action_frames_remain_bound()
    test_finish_is_not_misrepresented_as_acquisition_stop()
    test_reset_k_is_mul_act_30_q14_with_readback()
    test_point_reacquisition_preserves_both_full_masks_and_single_commit()
    test_native_automatch_and_inferred_math_are_not_writers()
    test_predictor_and_v7_suggestion_path_is_manual_review_only()
    print("PLATINUM_AUTOCAL_ACTION_PARITY_CONTRACT=PASS")
