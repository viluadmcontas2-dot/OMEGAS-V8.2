from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_g3a_same_chain_keeps_gasoline_reference_and_calibration_bound_cng_separate():
    memory = read("app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt")
    binding = read("app/src/main/java/com/omegas/prohub/learning/LearningCalibrationBinding.kt")
    live = read("app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt")

    assert "PetrolReferenceSelector.estimate(" in memory
    assert "FuelEquivalenceObjective.evaluate(" in memory
    assert "calibrationFingerprint" in binding
    assert "calibrationGeneration" in binding
    assert "geometryFingerprint" in binding
    assert "CALIBRATION_IDENTITY_REQUIRED" in live
    assert "LIVE_ONLY_RESET_DERIVED_PRESERVE_PETROL" in live


def test_g3a_reference_provenance_and_uncertainty_reach_the_same_comparison_projection():
    selector = read("app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt")
    ui = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    reconciler = read("app/src/main/java/com/omegas/prohub/learning/LearningSnapshotReconciler.kt")

    for token in ("spreadMs", "quality", "regionIds", "selectedRegionContexts"):
        assert token in selector
    for token in (
        '"reference_region_ids"',
        '"reference_contexts"',
        '"request_environment"',
        '"reference_denominator_ms"',
        '"equivalence_reason_code"',
    ):
        assert token in reconciler
    for token in (
        '"reference_provenance"',
        '"cng_value_provenance"',
        '"provenance_effective_weight"',
    ):
        assert token in ui


def test_g3a_has_one_equivalence_authority_and_no_invalid_numeric_fallback():
    memory = read("app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt")
    reconciler = read("app/src/main/java/com/omegas/prohub/learning/LearningSnapshotReconciler.kt")
    objective = read("app/src/main/java/com/omegas/prohub/learning/FuelEquivalenceObjective.kt")

    assert "FuelEquivalenceObjective.evaluate(" in memory
    assert "FuelEquivalenceObjective.evaluate(" in reconciler
    assert "REFERENCE_DENOMINATOR_TOO_SMALL" in objective
    assert "if (!equivalence.valid) return null" in reconciler
    assert "if (petrolTarget <= 0.05) 0.0" not in reconciler


def test_g3a_mutation_window_cannot_turn_intermediate_telemetry_into_active_evidence():
    scheduler = read("app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt")
    evidence = read("app/src/main/java/com/omegas/prohub/telemetry/CanonicalEvidence.kt")
    mutation = read("app/src/main/java/com/omegas/prohub/learning/LearningMutationAuthority.kt")
    calibration = read("app/src/main/java/com/omegas/prohub/learning/LearningCalibrationAuthority.kt")

    assert "LearningMutationAuthority.beginManualWrite(expectedSessionId, reason)" in scheduler
    assert "sampleDecision = LearningMutationAuthority.gate(decision)" in evidence
    assert "QUARANTINED_MUTATION_WINDOW" in mutation
    assert "UNKNOWN" in mutation
    assert "POST_WRITE_REVALIDATING" in mutation
    assert "LearningMutationAuthority.onCalibrationIdentityKnown(binding)" in calibration
    assert '"telemetry_continues", true' in mutation


def test_g3a_prediction_or_ui_never_becomes_observation_authority():
    advisor = read("app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt")
    ui = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    map_writer = read("app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt")

    assert "Mp48SerialScheduler" not in advisor
    assert "KWriteManager" not in advisor
    assert "Mp48SerialScheduler" not in ui
    assert "FuelEquivalenceObjective" not in ui
    assert 'put("humanConfirmed", true)' in map_writer
    assert 'put("readbackValid", true)' in map_writer
