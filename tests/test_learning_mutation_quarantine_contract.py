from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_manual_write_boundary_opens_quarantine_before_delegate_transport():
    scheduler = read("app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt")
    assert "workClass == Mp48WorkClass.MANUAL_WRITE" in scheduler
    assert "LearningMutationAuthority.beginManualWrite(expectedSessionId, reason)" in scheduler
    assert "mutationAware(workClass, expectedSessionId, reason)" in scheduler


def test_canonical_evidence_is_diagnostic_during_quarantine_without_dropping_telemetry():
    evidence = read("app/src/main/java/com/omegas/prohub/telemetry/CanonicalEvidence.kt")
    authority = read("app/src/main/java/com/omegas/prohub/learning/LearningMutationAuthority.kt")
    assert "sampleDecision = LearningMutationAuthority.gate(decision)" in evidence
    assert "blocks_active_science" in authority
    assert '"telemetry_continues", true' in authority
    assert "learningEligible = false" in authority
    assert "sample =" not in authority or "SampleDecision.transition(" in authority


def test_failure_is_fail_closed_until_fresh_identity_reconciles():
    scheduler = read("app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt")
    calibration = read("app/src/main/java/com/omegas/prohub/learning/LearningCalibrationAuthority.kt")
    authority = read("app/src/main/java/com/omegas/prohub/learning/LearningMutationAuthority.kt")
    assert "LearningMutationAuthority.markUnknown(" in scheduler
    assert "LearningMutationAuthority.onCalibrationIdentityKnown(binding)" in calibration
    assert "POST_WRITE_REVALIDATING" in authority
    assert "UNKNOWN" in authority


def test_no_new_writer_transport_or_automatic_write_was_introduced():
    authority = read("app/src/main/java/com/omegas/prohub/learning/LearningMutationAuthority.kt")
    assert "Mp48SerialScheduler" not in authority
    assert "UsbSerialManager" not in authority
    assert "writeKCell" not in authority
    assert "writeFactor" not in authority
