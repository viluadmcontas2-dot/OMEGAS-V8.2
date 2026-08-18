from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_runtime_marks_physical_session_as_geometry_managed():
    source = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    assert "LearningCalibrationAuthority.beginPhysicalSession()" in source
    assert source.count("LearningCalibrationAuthority.endPhysicalSession()") >= 2
    assert "LearningCalibrationBinding.fromIdentity(identity, composite.mapGeometry)" in source


def test_managed_session_has_no_historical_axis_fallback_when_geometry_is_unknown():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt")
    assert "if (LearningCalibrationAuthority.requiresKnownGeometry()) return null" in source
    assert 'reasonCode\", \"MAP_GEOMETRY_UNKNOWN\"' in source
    assert '.put("row", -1)' in source
    assert '.put("column", -1)' in source
    assert 'if (!cell.optBoolean("geometryKnown", false)) return@repeat' in source


def test_current_ecu_axes_drive_cell_and_weight_projection():
    projection = read("app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt")
    math = read("app/src/main/java/com/omegas/prohub/learning/ContinuousLearningMath.kt")
    binding = read("app/src/main/java/com/omegas/prohub/learning/LearningCalibrationBinding.kt")
    assert 'source = "ECU_CURRENT"' in projection
    assert "rpm = binding.rpmAxis.toIntArray()" in projection
    assert "petrolMs = binding.petrolAxisMs.toDoubleArray()" in projection
    assert "rpmAxis = rpmAxis" in projection
    assert "petrolAxisMs = axis.petrolMs" in projection
    assert "fun bilinearWeights(" in math and "rpmAxis: DoubleArray" in math
    assert "fun trilinearWeights(" in math and "petrolAxisMs: DoubleArray" in math
    assert 'val petrolAxisMs: List<Double>' in binding
    assert 'val rpmAxis: List<Int>' in binding
