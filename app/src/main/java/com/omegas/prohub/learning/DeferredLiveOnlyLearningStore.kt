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
 * RESTORING e não cria evidência nova. A sessão gravada continua preservando a
 * telemetria bruta para auditoria. Uma escrita K confirmada nesse intervalo não
 * é perdida: o reset derivado mais recente fica pendente e é aplicado antes de
 * a memória restaurada ser exposta como READY.
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

    companion object {
        const val STATE_RESTORING = "LEARNING_RESTORING"
        const val STATE_READY = "LEARNING_READY"
        const val STATE_FAILED = "LEARNING_RESTORE_FAILED"
        const val RESTORE_PENDING_REASON = "LEARNING_RESTORE_PENDING"

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
    private var pendingCalibrationAdjustment: JSONObject? = null

    init {
        restoreExecutor.execute {
            try {
                val restored = loader(runtimeRoot, log)
                var expose = false
                synchronized(stateLock) {
                    if (closed.get()) {
                        try { restored.store.close() } catch (_: Exception) {}
                    } else {
                        migration = JSONObject(restored.migration.toString())
                        if (sessionRequested) restored.store.startSession()
                        pendingCalibrationAdjustment?.let { restored.store.onCalibrationAdjustment(it) }
                        pendingCalibrationAdjustment = null
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
                        "Learning restaurado fora do startup em ${restoreDurationMs()}ms",
                    )
                }
            } catch (error: Exception) {
                restoreError = error.message ?: error.javaClass.simpleName
                restoreState = STATE_FAILED
                restoreFinishedAt = System.currentTimeMillis()
                log.add(
                    "ERROR",
                    "LEARNING-RESTORE",
                    "Learning não restaurado; telemetria permanece disponível: $restoreError",
                )
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
            if (restoreState == STATE_FAILED) {
                "Learning indisponível; telemetria continua independente"
            } else {
                "Restaurando Learning em segundo plano; telemetria continua independente"
            },
        )

    fun export(deviceId: String): JSONObject = delegate?.let { decorateReady(it.export(deviceId)) }
        ?: unavailable("export", deviceId)

    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject = delegate?.let {
        decorateReady(it.merge(payload, localDeviceId))
    } ?: unavailable("merge", localDeviceId.ifBlank { payload.optString("deviceId") })

    fun importNativeSnapshot(snapshot: JSONObject): JSONObject = delegate?.let {
        decorateReady(it.importNativeSnapshot(snapshot))
    } ?: unavailable("native_snapshot", snapshot.optString("snapshotId", snapshot.optString("sessionId")))

    /**
     * ACK/readback confirmado não pode reusar evidência GNV antiga. Se o
     * Learning ainda restaura, preservamos o reset mais recente e o aplicamos
     * antes de expor a memória como READY.
     */
    fun onCalibrationAdjustment(payload: JSONObject): JSONObject = synchronized(stateLock) {
        delegate?.let { return@synchronized decorateReady(it.onCalibrationAdjustment(payload)) }
        pendingCalibrationAdjustment = JSONObject(payload.toString())
        deferredCalibrationAdjustments += 1L
        log.add(
            "WARN",
            "LEARNING-RESTORE",
            "Ajuste confirmado aguardará restauração para invalidar evidência GNV anterior",
        )
        unavailable("calibration_adjustment", payload.optString("adjustmentId"))
            .put("deferred", true)
            .put("resetPerformed", false)
            .put("pendingCalibrationAdjustments", deferredCalibrationAdjustments)
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
            val current = delegate
            delegate = null
            current
        }
        try { active?.close() } catch (_: Exception) {}
        restoreExecutor.shutdownNow()
    }

    private fun restoringStatus(reason: String): JSONObject = JSONObject()
        .put("ok", restoreState != STATE_FAILED)
        .put("state", restoreState)
        .put("reason", reason)
        .put("reasonCode", if (restoreState == STATE_FAILED) STATE_FAILED else RESTORE_PENDING_REASON)
        .put("learning", false)
        .put("restoring", restoreState == STATE_RESTORING)
        .put("restore", restoreMetrics())

    private fun unavailable(operation: String, subject: String = ""): JSONObject = restoringStatus(
        if (restoreState == STATE_FAILED) {
            "Learning indisponível; operação não executada"
        } else {
            "Learning ainda restaurando; operação não executada"
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
        .apply { if (restoreError.isNotBlank()) put("error", restoreError) }

    private fun restoreDurationMs(): Long {
        val end = if (restoreFinishedAt > 0L) restoreFinishedAt else System.currentTimeMillis()
        return (end - restoreStartedAt).coerceAtLeast(0L)
    }
}
