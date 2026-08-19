package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Retira a restauração pesada do Learning do caminho crítico de abertura.
 *
 * A telemetria pode iniciar imediatamente. Enquanto a memória persistida é
 * restaurada em uma thread dedicada, o Learning publica estado explícito de
 * RESTORING e não cria evidência de telemetria nova. Operações materiais que
 * chegam nesse intervalo (snapshot AutoCal read-only e ajuste de calibração
 * confirmado) ficam em uma fila curta, limitada e causalmente ordenada para
 * replay antes de a memória restaurada ser exposta como READY.
 */
class DeferredLiveOnlyLearningStore(
    private val runtimeRoot: File,
    private val log: RingLog,
    restoreFactory: ((File, RingLog) -> RestoredLearning)? = null,
) {
    data class RestoredLearning(
        val migration: JSONObject,
        val store: LiveOnlyLearningStore,
    )

    private sealed interface DeferredOperation {
        data class NativeSnapshot(val key: String, val payload: JSONObject) : DeferredOperation
        data class CalibrationAdjustment(val payload: JSONObject) : DeferredOperation
    }

    companion object {
        const val STATE_RESTORING = "LEARNING_RESTORING"
        const val STATE_READY = "LEARNING_READY"
        const val STATE_FAILED = "LEARNING_RESTORE_FAILED"
        const val STATE_CLOSED = "LEARNING_CLOSED"
        const val RESTORE_PENDING_REASON = "LEARNING_RESTORE_PENDING"
        private const val MAX_DEFERRED_OPERATIONS = 64

        private fun restore(runtimeRoot: File, log: RingLog): RestoredLearning {
            val migration = LearningTelemetrySchemaMigration.prepare(runtimeRoot, log)
            val stateFile = File(runtimeRoot, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            return RestoredLearning(migration, LiveOnlyLearningStore(stateFile, log))
        }
    }

    private val stateLock = Any()
    private val closed = AtomicBoolean(false)
    private val skippedFrames = AtomicLong(0L)
    private val restoreExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-learning-restore").apply { isDaemon = true }
    }
    private val loader = restoreFactory ?: Companion::restore

    @Volatile private var delegate: LiveOnlyLearningStore? = null
    @Volatile private var restoreState = STATE_RESTORING
    @Volatile private var restoreError = ""
    @Volatile private var restoreStartedAt = System.currentTimeMillis()
    @Volatile private var restoreFinishedAt = 0L
    @Volatile private var migration = JSONObject()
        .put("pending", true)
        .put("activeState", LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)

    private var sessionRequested = false
    private var deferredCalibrationAdjustments = 0L
    private val deferredOperations = ArrayDeque<DeferredOperation>()
    private val deferredSnapshotKeys = linkedSetOf<String>()
    private var deferredNativeSnapshots = 0L
    private var replayedNativeSnapshots = 0L
    private var duplicateNativeSnapshots = 0L
    private var rejectedNativeSnapshots = 0L
    private var failedDeferredOperations = 0L

    init {
        restoreExecutor.execute {
            try {
                val restored = loader(runtimeRoot, log)
                var expose = false
                synchronized(stateLock) {
                    if (closed.get()) {
                        restoreState = STATE_CLOSED
                        if (restoreFinishedAt <= 0L) restoreFinishedAt = System.currentTimeMillis()
                        try { restored.store.close() } catch (_: Exception) {}
                    } else {
                        migration = JSONObject(restored.migration.toString())
                        if (sessionRequested) restored.store.startSession()
                        replayDeferredOperationsLocked(restored.store)
                        delegate = restored.store
                        restoreState = STATE_READY
                        restoreFinishedAt = System.currentTimeMillis()
                        expose = true
                    }
                }
                if (expose) {
                    log.add(
                        "INFO",
                        "LEARNING-RESTORE",
                        "Learning restaurado fora do startup em ${restoreDurationMs()}ms; operações AutoCal pendentes reconciliadas",
                    )
                }
            } catch (error: Exception) {
                var reportFailure = false
                synchronized(stateLock) {
                    if (closed.get()) {
                        restoreState = STATE_CLOSED
                        if (restoreFinishedAt <= 0L) restoreFinishedAt = System.currentTimeMillis()
                    } else {
                        restoreError = error.message ?: error.javaClass.simpleName
                        restoreState = STATE_FAILED
                        restoreFinishedAt = System.currentTimeMillis()
                        reportFailure = true
                    }
                }
                if (reportFailure) {
                    log.add(
                        "ERROR",
                        "LEARNING-RESTORE",
                        "Learning não restaurado; telemetria permanece disponível: $restoreError",
                    )
                }
            }
        }
    }

    fun startSession(): JSONObject = synchronized(stateLock) {
        sessionRequested = true
        delegate?.let { decorateReady(it.startSession()) }
            ?: restoringStatus("Sessão MP48 iniciada enquanto a memória é restaurada")
    }

    fun endSession(reason: String): JSONObject = synchronized(stateLock) {
        sessionRequested = false
        delegate?.let { decorateReady(it.endSession(reason)) }
            ?: restoringStatus("Sessão MP48 encerrada antes do fim da restauração")
                .put("endReason", reason)
    }

    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject {
        val active = delegate
        if (active == null) {
            skippedFrames.incrementAndGet()
            return restoringStatus("Telemetria ativa; Learning aguardando restauração")
        }
        return decorateReady(active.ingest(telemetry, decision))
    }

    fun statusJson(): JSONObject = delegate?.let { decorateReady(it.statusJson()) }
        ?: restoringStatus(
            when (restoreState) {
                STATE_FAILED -> "Learning indisponível; telemetria continua independente"
                STATE_CLOSED -> "Learning encerrado; nenhuma operação científica será enfileirada"
                else -> "Restaurando Learning em segundo plano; telemetria continua independente"
            },
        )

    fun export(deviceId: String): JSONObject = delegate?.let { decorateReady(it.export(deviceId)) }
        ?: unavailable("export", deviceId)

    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject = delegate?.let {
        decorateReady(it.merge(payload, localDeviceId))
    } ?: unavailable("merge", localDeviceId.ifBlank { payload.optString("deviceId") })

    fun importNativeSnapshot(snapshot: JSONObject): JSONObject = synchronized(stateLock) {
        delegate?.let { return@synchronized decorateReady(it.importNativeSnapshot(snapshot)) }
        deferredAdmissionFailureLocked()?.let { reasonCode ->
            rejectedNativeSnapshots += 1L
            return@synchronized unavailable("native_snapshot", snapshot.optString("snapshotId", snapshot.optString("sessionId")))
                .put("deferred", false)
                .put("reasonCode", reasonCode)
        }

        val copy = JSONObject(snapshot.toString())
        val key = nativeSnapshotKey(copy)
        if (!deferredSnapshotKeys.add(key)) {
            duplicateNativeSnapshots += 1L
            return@synchronized unavailable("native_snapshot", key)
                .put("ok", true)
                .put("deferred", true)
                .put("duplicate", true)
                .put("reasonCode", "AUTOCAL_SNAPSHOT_ALREADY_DEFERRED")
        }
        if (deferredOperations.size >= MAX_DEFERRED_OPERATIONS) {
            deferredSnapshotKeys.remove(key)
            rejectedNativeSnapshots += 1L
            log.add(
                "ERROR",
                "LEARNING-RESTORE",
                "Snapshot AutoCal não enfileirado: limite de $MAX_DEFERRED_OPERATIONS operações pendentes atingido; recorder continua como evidência bruta",
            )
            return@synchronized unavailable("native_snapshot", key)
                .put("deferred", false)
                .put("reasonCode", "AUTOCAL_DEFERRED_QUEUE_FULL")
                .put("queueBound", MAX_DEFERRED_OPERATIONS)
        }
        deferredOperations.addLast(DeferredOperation.NativeSnapshot(key, copy))
        deferredNativeSnapshots += 1L
        unavailable("native_snapshot", key)
            .put("ok", true)
            .put("deferred", true)
            .put("reasonCode", "AUTOCAL_SNAPSHOT_DEFERRED_UNTIL_RESTORE")
            .put("pendingDeferredOperations", deferredOperations.size)
    }

    /**
     * ACK/readback confirmado não pode reusar evidência GNV antiga. Se o
     * Learning ainda restaura, a operação entra na mesma fila causal dos
     * snapshots AutoCal. Assim snapshot→ajuste→snapshot mantém essa ordem ao
     * restaurar, preservando gasolina e evitando ressuscitar GNV obsoleto.
     */
    fun onCalibrationAdjustment(payload: JSONObject): JSONObject = synchronized(stateLock) {
        delegate?.let { return@synchronized decorateReady(it.onCalibrationAdjustment(payload)) }
        deferredAdmissionFailureLocked()?.let { reasonCode ->
            return@synchronized unavailable("calibration_adjustment", payload.optString("adjustmentId"))
                .put("deferred", false)
                .put("reasonCode", reasonCode)
                .put("resetPerformed", false)
        }

        if (!isConfirmedCalibrationAdjustment(payload)) {
            return@synchronized unavailable("calibration_adjustment", payload.optString("adjustmentId"))
                .put("deferred", false)
                .put("reasonCode", "UNCONFIRMED_CALIBRATION_UPDATE")
                .put("resetPerformed", false)
        }

        ensureCapacityForCalibrationAdjustmentLocked()
        deferredOperations.addLast(DeferredOperation.CalibrationAdjustment(JSONObject(payload.toString())))
        deferredCalibrationAdjustments += 1L
        log.add(
            "WARN",
            "LEARNING-RESTORE",
            "Ajuste confirmado aguardará restauração na mesma ordem causal dos snapshots AutoCal",
        )
        unavailable("calibration_adjustment", payload.optString("adjustmentId"))
            .put("deferred", true)
            .put("resetPerformed", false)
            .put("pendingCalibrationAdjustments", deferredCalibrationAdjustments)
            .put("pendingDeferredOperations", deferredOperations.size)
    }

    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject = delegate?.let {
        decorateReady(it.previewKWrite(row, column, value))
    } ?: unavailable("preview_k_write", "$row:$column:$value")

    fun migrationStatus(): JSONObject = JSONObject(migration.toString())
        .put("restore", restoreMetrics())

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = synchronized(stateLock) {
            sessionRequested = false
            deferredOperations.clear()
            deferredSnapshotKeys.clear()
            restoreState = STATE_CLOSED
            if (restoreFinishedAt <= 0L) restoreFinishedAt = System.currentTimeMillis()
            val current = delegate
            delegate = null
            current
        }
        try { active?.close() } catch (_: Exception) {}
        restoreExecutor.shutdownNow()
    }

    private fun replayDeferredOperationsLocked(store: LiveOnlyLearningStore) {
        while (deferredOperations.isNotEmpty()) {
            when (val operation = deferredOperations.removeFirst()) {
                is DeferredOperation.NativeSnapshot -> {
                    deferredSnapshotKeys.remove(operation.key)
                    try {
                        val result = store.importNativeSnapshot(operation.payload)
                        if (result.optBoolean("ok", false)) {
                            replayedNativeSnapshots += 1L
                        } else {
                            failedDeferredOperations += 1L
                            log.add(
                                "ERROR",
                                "LEARNING-RESTORE",
                                "Snapshot AutoCal pendente foi rejeitado no replay ${operation.key}: ${result.optString("reasonCode", "UNKNOWN_REASON")}",
                            )
                        }
                    } catch (error: Exception) {
                        failedDeferredOperations += 1L
                        log.add(
                            "ERROR",
                            "LEARNING-RESTORE",
                            "Snapshot AutoCal pendente falhou no replay ${operation.key}: ${error.message}",
                        )
                    }
                }
                is DeferredOperation.CalibrationAdjustment -> {
                    try {
                        val result = store.onCalibrationAdjustment(operation.payload)
                        if (!result.optBoolean("ok", false)) {
                            failedDeferredOperations += 1L
                            log.add(
                                "ERROR",
                                "LEARNING-RESTORE",
                                "Ajuste de calibração pendente foi rejeitado no replay: ${result.optString("reasonCode", "UNKNOWN_REASON")}",
                            )
                        }
                    } catch (error: Exception) {
                        failedDeferredOperations += 1L
                        log.add(
                            "ERROR",
                            "LEARNING-RESTORE",
                            "Ajuste de calibração pendente falhou no replay: ${error.message}",
                        )
                    }
                }
            }
        }
    }

    private fun ensureCapacityForCalibrationAdjustmentLocked() {
        if (deferredOperations.size < MAX_DEFERRED_OPERATIONS) return
        val snapshotIndex = deferredOperations.indexOfFirst { it is DeferredOperation.NativeSnapshot }
        if (snapshotIndex >= 0) {
            val removed = deferredOperations.removeAt(snapshotIndex) as DeferredOperation.NativeSnapshot
            deferredSnapshotKeys.remove(removed.key)
            rejectedNativeSnapshots += 1L
            log.add(
                "ERROR",
                "LEARNING-RESTORE",
                "Snapshot AutoCal ${removed.key} removido da fila para preservar invalidação de calibração confirmada; recorder mantém evidência bruta",
            )
            return
        }
        deferredOperations.removeFirstOrNull()
        failedDeferredOperations += 1L
        log.add(
            "ERROR",
            "LEARNING-RESTORE",
            "Fila de ajustes confirmados saturou; ajuste mais antigo foi substituído pelo mais recente para manter boundedness",
        )
    }

    private fun deferredAdmissionFailureLocked(): String? = when {
        closed.get() || restoreState == STATE_CLOSED -> STATE_CLOSED
        restoreState != STATE_RESTORING -> restoreState
        else -> null
    }

    private fun isConfirmedCalibrationAdjustment(payload: JSONObject): Boolean {
        val readbackValid = payload.optBoolean("readbackValid", false)
        val manualConfirmed = payload.optBoolean("humanConfirmed", false) && readbackValid
        val nativeObserved = payload.optString("source") == "ECU_NATIVE_AUTOCAL" &&
            payload.optBoolean("ecuNativeObserved", false) &&
            !payload.optBoolean("appWritePerformed", true) &&
            readbackValid
        return manualConfirmed || nativeObserved
    }

    private fun nativeSnapshotKey(snapshot: JSONObject): String {
        val session = snapshot.optString("sessionId", "UNKNOWN_SESSION")
        val material = snapshot.optString("snapshotHash").ifBlank {
            snapshot.optString("snapshotId").ifBlank {
                snapshot.optString("id").ifBlank { snapshot.toString().hashCode().toString() }
            }
        }
        return "$session:$material"
    }

    private fun restoringStatus(reason: String): JSONObject = JSONObject()
        .put("ok", restoreState !in setOf(STATE_FAILED, STATE_CLOSED))
        .put("state", restoreState)
        .put("reason", reason)
        .put(
            "reasonCode",
            when (restoreState) {
                STATE_FAILED -> STATE_FAILED
                STATE_CLOSED -> STATE_CLOSED
                else -> RESTORE_PENDING_REASON
            },
        )
        .put("learning", false)
        .put("restoring", restoreState == STATE_RESTORING)
        .put("restore", restoreMetrics())

    private fun unavailable(operation: String, subject: String = ""): JSONObject = restoringStatus(
        when (restoreState) {
            STATE_FAILED -> "Learning indisponível; operação não executada"
            STATE_CLOSED -> "Learning encerrado; operação não executada"
            else -> "Learning ainda restaurando; operação aguardando restauração quando suportado"
        },
    )
        .put("ok", false)
        .put("operation", operation)
        .apply { if (subject.isNotBlank()) put("subject", subject) }
        .put("format", LiveOnlyLearningStore.FORMAT)

    private fun decorateReady(source: JSONObject): JSONObject = JSONObject(source.toString())
        .put("restoring", false)
        .put("restore", restoreMetrics())

    private fun restoreMetrics(): JSONObject = JSONObject()
        .put("state", restoreState)
        .put("startedAt", restoreStartedAt)
        .put("finishedAt", restoreFinishedAt)
        .put("durationMs", restoreDurationMs())
        .put("skippedFramesWhileRestoring", skippedFrames.get())
        .put("pendingCalibrationAdjustments", deferredCalibrationAdjustments)
        .put("pendingDeferredOperations", synchronized(stateLock) { deferredOperations.size })
        .put("deferredNativeSnapshots", deferredNativeSnapshots)
        .put("replayedNativeSnapshots", replayedNativeSnapshots)
        .put("duplicateNativeSnapshots", duplicateNativeSnapshots)
        .put("rejectedNativeSnapshots", rejectedNativeSnapshots)
        .put("failedDeferredOperations", failedDeferredOperations)
        .put("deferredQueueBound", MAX_DEFERRED_OPERATIONS)
        .apply { if (restoreError.isNotBlank()) put("error", restoreError) }

    private fun restoreDurationMs(): Long {
        val end = if (restoreFinishedAt > 0L) restoreFinishedAt else System.currentTimeMillis()
        return (end - restoreStartedAt).coerceAtLeast(0L)
    }
}