from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_runtime_uses_one_canonical_frame_and_bounded_independent_consumers():
    runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    consume = runtime.split("    private fun consumeTelemetry(", 1)[1].split(
        "\n    private fun projectTelemetryCompatibility", 1
    )[0]
    assert "CanonicalEvidence.from(" in consume
    assert "latestCanonicalEvidence.publish(evidence)" in consume
    assert "telemetryDeliveryPipeline.submit(sequence)" in consume
    assert "adaptiveShadowPipeline.submit(sequence)" in consume
    assert "EvidenceWorkClassifier.classify(" in consume
    assert "learningPipeline.submit(" in consume
    assert "learning.ingest(evidence.rawTelemetry, evidence.sampleDecision)" in consume
    assert "generation = generation" in consume
    assert "if (generation != currentUsbSessionId)" in consume
    assert "RealtimeLearningBuffer" in runtime


def test_usb_generation_purges_old_hot_science_without_stopping_acquisition():
    runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    begin = runtime.split("    fun beginUsbSession(", 1)[1].split("\n    /** Fecha somente", 1)[0]
    end = runtime.split("    fun endUsbSession(", 1)[1].split("\n    @Synchronized", 1)[0]
    assert "learningPipeline.beginGeneration(sessionId)" in begin
    assert "latestLearningSequence = 0L" in begin
    assert begin.index("learningPipeline.beginGeneration(sessionId)") < begin.index("learning.startSession()")
    assert "learningPipeline.flush(750L)" in end
    assert "learningPipeline.endGeneration(endingSession" in end
    assert "currentUsbSessionId = 0L" in end


def test_science_backpressure_keeps_three_valuable_items_plus_latest_diagnostic():
    buffer = read("app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt")
    for marker in (
        "const val MAX_HOT_EVIDENCE = 3",
        "private val capacityImportant = importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)",
        "private val importantQueue = ArrayDeque<Task>()",
        "private var latestTransient: Task? = null",
        "coalescedTransient.incrementAndGet()",
        "importantQueue.remove(best)",
        "supersededImportant.incrementAndGet()",
        "purgeQueuedLocked()",
        'put("durableBacklog", "SESSION_RECORDER")',
        'put("overloadPolicy", "SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING")',
        'put("acquisitionDropAllowed", false)',
        'put("pendingImportant", importantQueue.size)',
        'put("maxQueueDelayMs"',
        'put("maxProcessingMs"',
        'put("marginalInformationModel", "QUALITATIVE_ORDER_ONLY_NOT_CONFIDENCE_OR_PROBABILITY")',
    ):
        assert marker in buffer, f"contrato realtime ausente: {marker}"
    assert "Executors.newSingleThreadExecutor" not in buffer


def test_visual_delivery_is_latest_only_not_a_history_queue():
    delivery = read("app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt")
    for marker in (
        "private var pending: Task? = null",
        "pending?.let",
        "coalesced.incrementAndGet()",
        "pending = task",
        'put("mode", "LATEST_ONLY_LIVE_STATE")',
        'put("queueBound", 1)',
        'put("pending", if (pending == null) 0 else 1)',
        'put("coalesced", coalesced.get())',
    ):
        assert marker in delivery
    assert "ArrayDeque" not in delivery


def test_scientific_snapshot_persistence_is_material_revision_driven_and_coalesced():
    learning = read("app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt")
    writer = read("app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt")
    ingest = learning.split(
        "    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject", 1
    )[1].split("\n    fun statusJson()", 1)[0]
    assert "val result = delegate.ingest(telemetry, prepared)" in ingest
    assert "if (prepared.learningEligible && prepared.sample != null)" in ingest
    assert "persistenceGate.markMaterialChange()" in ingest
    assert "persistEvidenceState()" in ingest
    assert "if (source != null) persistEvidenceState()" not in ingest
    assert "writeText(" not in ingest
    assert "private val persistenceGate = MaterialPersistenceGate()" in learning
    assert "evidenceStateWriter.request { buildEvidencePayload(snapshot) }" in learning
    assert "latestPayloadProvider = payloadProvider" in writer
    assert "latestPayloadProvider?.invoke()" in writer
    assert "while (dirty.getAndSet(false))" in writer
    assert "writeAtomically(payload)" in writer
