from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CALIBRATION = ROOT / "app/src/main/java/com/omegas/prohub/calibration"


def read(name: str) -> str:
    return (CALIBRATION / name).read_text("utf-8")


def read_root(name: str) -> str:
    return (ROOT / name).read_text("utf-8")


def test_rpm_is_not_a_manual_write_gate():
    source = read("CalibrationWriteSafetyPolicy.kt")
    assert "status.rpm >=" not in source
    assert "DRIVING_PROBABLE" not in source


def test_k_bounds_have_one_authority_at_100_through_180():
    writer = read("KWriteManager.kt")
    policy = read("KOperatingPolicy.kt")
    planner = read("MapKManualPlanner.kt")
    advisor = read("AdvisorSuggestionAdapterV7.kt")
    assert "const val MIN_TARGET_K = 100" in policy
    assert "const val MAX_TARGET_K = 180" in policy
    assert "value in MIN_TARGET_K..MAX_TARGET_K" in policy
    assert "KOperatingPolicy.MIN_TARGET_K" in writer
    assert "KOperatingPolicy.MAX_TARGET_K" in writer
    assert "!isAllowedTarget(target)" in writer
    assert "KWriteManager.MAX_ALLOWED_K" in planner
    assert "KWriteManager.MAX_ALLOWED_K" in advisor

    map_editor = read_root("app/src/main/assets/ui/map-editor.js")
    native_api = read_root("app/src/main/assets/ui/core/native-api.js")
    predictor = read_root("app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt")
    assert "const MAX_K = 180" in map_editor
    assert "const PROTOCOL_MAX_K = 255" in map_editor
    assert "const maximumK = 180" in native_api
    assert "MapKManualPlanner.target" in predictor


if __name__ == "__main__":
    test_rpm_is_not_a_manual_write_gate()
    test_k_bounds_have_one_authority_at_100_through_180()
    print("RED_MANUAL_AUTHORITY_CONTRACT=PASS")
