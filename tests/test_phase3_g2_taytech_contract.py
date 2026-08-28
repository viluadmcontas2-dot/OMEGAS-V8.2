from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_ecu_callback_creates_one_typed_envelope_before_downstream_work():
    runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    consume = runtime.split("private fun consumeTelemetry(", 1)[1].split("private fun projectTelemetryCompatibility(", 1)[0]
    assert consume.count("CanonicalEvidence.from(") == 1
    assert "latestCanonicalEvidence.publish(evidence)" in consume
    assert "telemetryDeliveryPipeline.submit(sequence)" in consume
    assert "adaptiveShadowPipeline.submit(sequence)" in consume
    assert "learningPipeline.submit(" in consume
    assert "adaptiveShadow.observe(evidence)" in consume
    assert "learning.ingest(evidence.rawTelemetry, evidence.sampleDecision)" in consume
    # JSON pertence à projeção downstream; não à callback tipada da aquisição.
    assert "JSONObject(" not in consume
    assert ".toJson()" not in consume


def test_live_projection_is_latest_only_and_observable_for_cost():
    source = read("app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt")
    assert 'put("queueBound", 1)' in source
    assert 'put("overloadPolicy", "COALESCE_PENDING_TO_LATEST")' in source
    assert 'put("dropAffectsAcquisition", false)' in source
    for metric in (
        '"lastQueueDelayMs"', '"maxQueueDelayMs"',
        '"lastProcessingMs"', '"maxProcessingMs"',
        '"lastThreadCpuMs"', '"maxThreadCpuMs"',
        '"pendingEstimatedBytes"', '"activeEstimatedBytes"',
    ):
        assert metric in source


def test_science_backlog_is_hard_bounded_and_never_controls_acquisition():
    source = read("app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt")
    assert "const val MAX_HOT_EVIDENCE = 3" in source
    assert "importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)" in source
    assert 'put("queueBoundDiagnostic", 1)' in source
    assert 'put("acquisitionDropAllowed", false)' in source
    assert 'put("overloadPolicy", "SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING")' in source
    for metric in (
        '"lastQueueDelayMs"', '"maxQueueDelayMs"',
        '"lastProcessingMs"', '"maxProcessingMs"',
        '"lastThreadCpuMs"', '"maxThreadCpuMs"',
        '"queuedEstimatedBytes"', '"activeEstimatedBytes"',
        '"costByClass"',
    ):
        assert metric in source


def test_persistence_and_ui_projection_are_revision_driven_not_frame_driven():
    store = read("app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt")
    gate = read("app/src/main/java/com/omegas/prohub/learning/MaterialPersistenceGate.kt")
    ui = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    assert "prepared.learningEligible && prepared.sample != null" in store
    assert "persistenceGate.markMaterialChange()" in store
    assert "if (!persistenceGate.shouldRequest(forceBoundary)) return" in store
    assert "PERSIST_AFTER_MATERIAL_EVIDENCE_REVISION" in gate
    assert "cachedRevision" in ui and "cachedPayload" in ui
    assert 'rawSnapshot.optString("stateDigest")' in ui
    assert '"PERSISTED_REVISION_CACHE"' in ui


def test_adaptive_shadow_reuses_same_canonical_evidence_and_has_no_writer_or_polling():
    runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    adaptive = read("app/src/main/java/com/omegas/prohub/adaptive/AdaptiveShadowObserver.kt")
    contract = read("app/src/main/java/com/omegas/prohub/adaptive/AdaptiveEvidenceContracts.kt")
    assert "adaptiveShadow.observe(evidence)" in runtime
    assert 'typealias CanonicalEvidence = com.omegas.prohub.telemetry.CanonicalEvidence' in contract
    assert "SINGLE_PHYSICAL_ACQUISITION = true" in contract
    assert "MAY_CREATE_SECOND_MP48_POLLING = false" in contract
    assert "MAY_REPARSE_JSON_TO_FORM_SCIENCE = false" in contract
    assert "MAY_WRITE_ECU = false" in contract
    assert 'put("polling", false)' in adaptive
    assert 'put("writer", false)' in adaptive
