from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACCESS = ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt"
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_normal_blue_state_ingests_learning_snapshot():
    source = text(ACCESS)
    assert "refreshBlueLearningEvidence(this, coordinator)" in source
    assert "service.runtime.exportLearning(service.settings.deviceId)" in source
    assert "coordinator.ingestLearningSnapshot(learning)" in source


def test_runtime_hydrates_only_confirmed_same_session_calibration_cache():
    access = text(ACCESS)
    coordinator = text(COORDINATOR)
    assert 'File(service.paths.runtimeRoot, "k_map_cache.json")' in access
    assert 'File(service.paths.runtimeRoot, "k_factor_cache.json")' in access
    assert 'coordinator.synchronizeFromConfirmedSnapshot(map, curve)' in access
    assert 'serialReadStarted", false' in access
    assert "mapSessionId >= 0L && mapSessionId == curveSessionId" in coordinator
    assert '"CONFIRMED_SESSION_CACHE"' in coordinator


def test_measured_deadband_is_not_filtered_from_comparisons():
    source = text(ENGINE)
    compare_body = source.split("private fun compare(", 1)[1].split("private fun normalizedDistance", 1)[0]
    assert "isWithinActionDeadband" not in compare_body
    assert "return FuelComparison(" in compare_body
    assert "fun isWithinActionDeadband" in source
