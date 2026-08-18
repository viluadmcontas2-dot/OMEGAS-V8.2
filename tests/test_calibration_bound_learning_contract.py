from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_cng_learning_requires_material_calibration_identity():
    source = read("app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt")
    assert 'CALIBRATION_REQUIRED_REASON_CODE = "CALIBRATION_IDENTITY_REQUIRED"' in source
    assert "source?.fuel == Mp48Fuel.CNG && activeCalibrationBinding == null" in source
    assert "sample = null" in source
    assert "learningEligible = false" in source


def test_material_calibration_key_excludes_usb_session_but_keeps_required_identity():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningCalibrationBinding.kt")
    assert 'fun key(): String = "$calibrationFingerprint:$calibrationGeneration:$geometryFingerprint"' in source
    assert '.put("usb_session_id", usbSessionId)' in source
    assert '.put("map_hash", mapHash)' in source


def test_runtime_reconciles_identity_from_composite_read_and_clears_on_boundaries():
    source = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    assert "CompositeCalibrationReader(serialAdmission)" in source
    assert "CompositeCalibrationSnapshot.promote(raw)" in source
    assert "CalibrationIdentity.fromComposite(" in source
    assert "LearningCalibrationBinding.fromIdentity(identity)" in source
    assert "LearningCalibrationAuthority.publish(binding)" in source
    assert source.count("LearningCalibrationAuthority.clear()") >= 3


def test_policy_v3_refuses_to_relabel_legacy_cng_and_stamps_active_evidence():
    source = read("app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt")
    assert "const val POLICY_VERSION = 3" in source
    assert "toda evidência GNV anterior sem identidade material foi zerada" in source
    assert 'if (region.optString("fuel") == Mp48Fuel.CNG.wireName) stamp(region)' in source
    assert 'repeat(comparisons.length()) { index -> stamp(comparisons.optJSONObject(index)) }' in source
    assert 'reasonCode", "CALIBRATION_IDENTITY_CHANGED"' in source
