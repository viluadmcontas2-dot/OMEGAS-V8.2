#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
runtime = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt").read_text(encoding="utf-8")
buffer = (ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt").read_text(encoding="utf-8")
delivery = (ROOT / "app/src/main/java/com/omegas/prohub/util/OrderedBackgroundPipeline.kt").read_text(encoding="utf-8")
learning = (ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt").read_text(encoding="utf-8")
snapshot_writer = (ROOT / "app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt").read_text(encoding="utf-8")

consume_start = runtime.index("    private fun consumeTelemetry(")
consume_end = runtime.index("\n    private fun publishLearningState", consume_start)
consume_body = runtime[consume_start:consume_end]

# A ECU continua livre do trabalho pesado.
assert "learning.ingest(telemetry, decision)" in consume_body
assert "val processed = learning.ingest(telemetry, decision)" in consume_body
assert "val learningState = learningLiveSummary()" in consume_body
assert "cachedLearningState()" not in consume_body
assert "telemetryDeliveryPipeline.submit(sequence)" in consume_body
assert "RealtimeLearningBuffer" in runtime
assert "OrderedBackgroundPipeline(\n        threadName = \"omegas-learning-pipeline\"" not in runtime

# O hot path diferencia evidência de estado transitório.
assert "val important = decision.sample != null" in consume_body
assert "important = important" in consume_body
assert "generation = generation" in consume_body
assert "if (generation != currentUsbSessionId)" in consume_body
assert "learningSessionLock" in runtime

# Nova conexão = nova geração. Trabalho quente antigo não pode entrar na sessão nova.
begin_start = runtime.index("    fun beginUsbSession(")
begin_end = runtime.index("\n    /** Fecha somente", begin_start)
begin_body = runtime[begin_start:begin_end]
assert "learningPipeline.beginGeneration(sessionId)" in begin_body
assert "latestLearningSequence = 0L" in begin_body
assert begin_body.index("learningPipeline.beginGeneration(sessionId)") < begin_body.index("learning.startSession()")

end_start = runtime.index("    fun endUsbSession(")
end_end = runtime.index("\n    @Synchronized", end_start)
end_body = runtime[end_start:end_end]
assert "learningPipeline.flush(750L)" in end_body
assert "learningPipeline.endGeneration(endingSession" in end_body
assert "currentUsbSessionId = 0L" in end_body

# Buffer quente tem limite duro de três janelas sobrepostas. Em saturação mantém
# a evidência recente em vez de transformar RAM em histórico de minutos.
for marker in (
    "const val MAX_HOT_EVIDENCE = 3",
    "private val capacityImportant = importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)",
    "private val importantQueue = ArrayDeque<Task>()",
    "private var latestTransient: Task? = null",
    "coalescedTransient.incrementAndGet()",
    "importantQueue.removeFirst()",
    "supersededImportant.incrementAndGet()",
    "purgeQueuedLocked()",
    'put("durableBacklog", "SESSION_RECORDER")',
    'put("overloadPolicy", "SUPERSEDE_OLDEST_OVERLAPPING_PENDING_EVIDENCE")',
    'put("capacityImportant", capacityImportant)',
    'put("pendingImportant", importantQueue.size)',
    'put("maxQueueDelayMs"',
    'put("maxProcessingMs"',
):
    assert marker in buffer, f"contrato realtime ausente: {marker}"
assert "Executors.newSingleThreadExecutor" not in buffer
assert "Thread.MIN_PRIORITY" not in runtime

# A entrega da telemetria ao serviço pode continuar em fila ordenada própria; ela
# não é a memória de aprendizado e mantém métricas de backpressure.
assert "Executors.newSingleThreadExecutor" in delivery
assert '.put("pending"' in delivery

# Persistência auxiliar continua coalescida e fora do ingest quente.
ingest_start = learning.index("    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject")
ingest_end = learning.index("\n    fun statusJson()", ingest_start)
ingest_body = learning[ingest_start:ingest_end]
assert "val result = delegate.ingest(telemetry, prepared)" in ingest_body
assert "if (source != null) persistEvidenceState()" in ingest_body
assert "writeText(" not in ingest_body
assert "CoalescedSnapshotWriter" in learning
assert "evidenceStateWriter.submit(payload.toString())" in learning
assert "temporary.writeText" not in learning
assert "latestPayload = payload" in snapshot_writer
assert "while (dirty.getAndSet(false))" in snapshot_writer
assert '.put("coalesced"' in snapshot_writer
assert "writeAtomically(payload)" in snapshot_writer

print("MULTIMEDIA_REALTIME_LEARNING_BUFFER_CONTRACT=PASS")
