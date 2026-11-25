package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.MapBatchPlan
import com.omegas.prohub.calibration.MapKManualPlanner
import com.omegas.prohub.service.v7CalibrationStateJson
import com.omegas.prohub.service.v7IngestLearningSnapshot
import com.omegas.prohub.service.v7LoadSession
import com.omegas.prohub.service.v7ReconcileConfirmedManualWrite
import com.omegas.prohub.service.v7SaveSession
import com.omegas.prohub.service.v7SessionFilesJson
import com.omegas.prohub.service.v7SynchronizeAdvisorSuggestions
import com.omegas.prohub.service.v7SynchronizeCalibration
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Ponte de compatibilidade V7 dentro do OMEGAS V8. Toda operação serial ocorre fora da thread da WebView. */
class V7JavascriptBridge private constructor(
    activity: MainActivity?,
    private val testWriteHost: WriteHost?,
) {
    constructor(activity: MainActivity) : this(activity, null)
    internal constructor(writeHost: WriteHost) : this(null, writeHost)

    internal interface WriteHost {
        fun unsafeReason(): String?
        fun startCurveReset(onVerified: (JSONObject) -> Unit): String
        fun startCurveBatchWrite(points: String, reason: String, onVerified: (JSONObject) -> Unit): String
        fun startMapBatchWrite(cells: String, reason: String, onVerified: (JSONObject) -> Unit): String
        fun curveStatus(): String
        fun mapStatus(): String
        fun reconcile(target: String): String
        fun refresh()
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 15 * 60 * 1000L
    }

    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-v7-web-operations").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    @Volatile private var lastOperation = JSONObject()
        .put("ok", true)
        .put("state", "IDLE")
        .put("busy", false)

    fun destroy() {
        executor.shutdownNow()
    }

    @JavascriptInterface
    fun getState(): String = activity?.serviceOrNull()?.v7CalibrationStateJson()
        ?: unavailable()

    @JavascriptInterface
    fun listSessionFiles(): String = activity?.serviceOrNull()?.v7SessionFilesJson()
        ?: "[]"

    @JavascriptInterface
    fun getLastOperation(): String {
        val operation = lastOperation
        return synchronized(operation) {
            JSONObject(operation.toString()).put("busy", busy.get() && !operation.optBoolean("acked")).toString()
        }
    }

    /** Conferência em segundo plano da última gravação K (barra do topo): {phase, detail, label}. */
    @JavascriptInterface
    fun getWriteVerify(): String = activity?.serviceOrNull()?.kWriteVerifyJson() ?: "{\"phase\":\"idle\"}"

    @JavascriptInterface
    fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String =
        MapKManualPlanner.preview(cellsJson, mode, adjustment).toString()

    /** Operação pura: converte a saída do advisor em sugestões versionadas. */
    @JavascriptInterface
    fun synchronizeAdvisorSuggestions(adviceJson: String): String =
        activity?.serviceOrNull()?.v7SynchronizeAdvisorSuggestions(adviceJson)
            ?: unavailable()

    /** Importação explícita da memória física atual para a sessão V8. */
    @JavascriptInterface
    fun ingestLearningSnapshot(snapshotJson: String): String =
        activity?.serviceOrNull()?.v7IngestLearningSnapshot(snapshotJson)
            ?: unavailable()

    @JavascriptInterface
    fun synchronizeFromEcu(fileName: String): String = startOperation("SYNCHRONIZING_ECU") { service ->
        service.v7SynchronizeCalibration(fileName)
    }

    /**
     * Compatibilidade de API: sugestão nunca mais alcança writer diretamente.
     * A interface deve abrir Curva K/Mapa K, preparar a proposta e exigir revisão.
     */
    @JavascriptInterface
    fun applySuggestion(suggestionId: String): String = JSONObject()
        .put("ok", true)
        .put("suggestionId", suggestionId)
        .put("state", "MANUAL_REVIEW_REQUIRED")
        .put("prepared", false)
        .put("writesStarted", false)
        .put("automatic", false)
        .put("humanConfirmationRequired", true)
        .put("message", "Sugestão não escreve diretamente. Abra o editor, revise a proposta e confirme manualmente.")
        .toString()

    @JavascriptInterface
    fun startCurveRead(): String = startOperation("CURVE_READING") { service ->
        service.readKFactorCurve()
    }

    @JavascriptInterface
    fun startCurveBackup(label: String): String = startOperation("CURVE_BACKUP_SAVING") { service ->
        service.saveKFactorBackup(label)
    }

    @JavascriptInterface
    fun listCurveBackups(): String =
        activity?.serviceOrNull()?.listKFactorBackups() ?: "[]"

    @JavascriptInterface
    fun startCurveRestorePrepare(fileName: String): String =
        startOperation("CURVE_RESTORE_PREPARING") { service ->
            service.prepareKFactorRestore(fileName)
        }

    @JavascriptInterface
    fun startCurveReset(): String {
        val host = writeHost() ?: return unavailable()
        return startCurveWrite(host, reset = true) { verified ->
            host.startCurveReset(verified)
        }
    }

    @JavascriptInterface
    fun startCurveBatchWrite(pointsJson: String, reason: String): String {
        val host = writeHost() ?: return unavailable()
        val points = try { JSONArray(pointsJson) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de pontos inválido").toString()
        }
        if (points.length() !in 1..30) {
            return JSONObject().put("ok", false).put("error", "Selecione entre 1 e 30 pontos da Curva K").toString()
        }
        return startCurveWrite(host, reset = false, totalPoints = points.length()) { verified ->
            host.startCurveBatchWrite(points.toString(), reason, verified)
        }
    }

    private fun startCurveWrite(
        host: WriteHost,
        reset: Boolean,
        totalPoints: Int = 0,
        start: ((JSONObject) -> Unit) -> String,
    ): String {
        host.unsafeReason()?.let { return safetyBlocked(it) }
        if (!busy.compareAndSet(false, true)) {
            return JSONObject().put("ok", false).put("busy", true)
                .put("error", "Outra operação V8 está em andamento").toString()
        }

        val startedAt = System.currentTimeMillis()
        val queued = if (reset) "CURVE_RESET_QUEUED" else "CURVE_WRITE_QUEUED"
        val running = if (reset) "CURVE_RESETTING" else "CURVE_WRITING"
        val failed = if (reset) "CURVE_RESET_FAILED" else "CURVE_WRITE_FAILED"
        lastOperation = JSONObject().put("ok", true).put("state", queued).put("progress", 0)
            .put("startedAt", startedAt).put("totalPoints", totalPoints)

        executor.execute {
            val verification = CompletableFuture<JSONObject>()
            val result = try {
                val unsafe = host.unsafeReason()
                if (unsafe != null) {
                    JSONObject(safetyBlocked(unsafe))
                } else {
                    val started = JSONObject(start { verification.complete(it) })
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        started
                    } else {
                        awaitWriter(verification, host::curveStatus) { status, state ->
                            lastOperation = JSONObject(status.toString()).put("ok", true).put("state", running)
                                .put("writerState", state).put("startedAt", startedAt).put("totalPoints", totalPoints)
                        }
                    }
                }
            } catch (error: Exception) {
                JSONObject().put("error", error.message ?: "Falha ao coordenar Curva K")
            }
            val state = result.optString("state")
            val confirmed = state == "BATCH_CONFIRMED" || (reset && state == "RESET_CONFIRMED")
            result.put("ok", confirmed).put("startedAt", startedAt).put("finishedAt", System.currentTimeMillis())
            if (confirmed) {
                publishConfirmed(host, result, verification, if (reset) null else "CURVE_K")
            } else {
                lastOperation = result.put("state", failed)
                busy.set(false)
                host.refresh()
            }
        }

        return JSONObject().put("ok", true).put("started", true).put("state", queued)
            .put("startedAt", startedAt).put("totalPoints", totalPoints).put("busy", true)
            .put("humanConfirmationRequired", true).toString()
    }

    private fun awaitWriter(
        verification: CompletableFuture<JSONObject>,
        readStatus: () -> String,
        progress: (JSONObject, String) -> Unit,
    ): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OPERATION_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            verification.getNow(null)?.let { return JSONObject(it.toString()) }
            val status = JSONObject(readStatus())
            val state = status.optString("state")
            if (state == "BATCH_CONFIRMED" || state == "RESET_CONFIRMED" ||
                state.contains("FAILED") || state.startsWith("SAFETY_LOCKED")
            ) return status
            progress(status, state)
            Thread.sleep(80L)
        }
        return JSONObject().put("state", "TIMEOUT").put("error", "Tempo limite aguardando confirmação da central")
    }

    private fun publishConfirmed(
        host: WriteHost,
        operation: JSONObject,
        verification: CompletableFuture<JSONObject>,
        target: String?,
    ) {
        operation.put("ok", true).put("state", "BATCH_CONFIRMED").put("progress", 100)
            .put("acked", true).put("humanConfirmed", true).put("readbackValid", false)
            .put("verificationPending", true)
        lastOperation = operation
        host.refresh()

        // A leitura de volta e a reconciliação continuam no executor, com ACK já visível na interface.
        val result = try {
            verification.get(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            JSONObject().put("error", error.message ?: "Conferência não concluída")
        }
        val readbackValid = result.optString("state") == "RESET_CONFIRMED" ||
            result.optJSONObject("details")?.optBoolean("readbackValid") == true
        val suggestionReconciliation = if (readbackValid && target != null) {
            try {
                JSONObject(host.reconcile(target))
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: "Reconciliação de sugestões indisponível")
            }
        } else null
        synchronized(operation) {
            operation.put("readbackValid", readbackValid).put("verificationPending", false)
                .put("suggestionReconciliation", suggestionReconciliation)
            operation.optJSONObject("details")?.put("readbackValid", readbackValid)
            if (!readbackValid) operation.put("error", result.optString("error", "A leitura de volta não confirmou a gravação"))
            busy.set(false)
        }
        host.refresh()
    }

    /**
     * Uma única intenção humana pode conter toda a grade 12x12 (até 144 células).
     * O plano preserva essa intenção como um único lote nativo: sem ramping e
     * sem pausas artificiais, mantendo backup, ACK por write, insertion seguro
     * e readback final das linhas afetadas.
     */
    @JavascriptInterface
    fun startMapBatchWrite(cellsJson: String, reason: String): String {
        val host = writeHost() ?: return unavailable()
        host.unsafeReason()?.let { return safetyBlocked(it) }
        val cells = try {
            JSONArray(cellsJson)
        } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de células inválido").toString()
        }
        val plan = try {
            MapBatchPlan.build(cells)
        } catch (error: IllegalArgumentException) {
            return JSONObject().put("ok", false).put("error", error.message ?: "Lote inválido").toString()
        }
        if (!busy.compareAndSet(false, true)) {
            return JSONObject()
                .put("ok", false)
                .put("busy", true)
                .put("error", "Outra operação V8 está em andamento")
                .toString()
        }

        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject()
            .put("ok", true)
            .put("state", "MAP_K_QUEUED")
            .put("busy", true)
            .put("progress", 0)
            .put("startedAt", startedAt)
            .put("totalCells", plan.totalCells)
            .put("internalChunks", plan.chunks.size)

        executor.execute {
            val adjustmentIds = JSONArray()
            var completedCells = 0
            var failure: JSONObject? = null
            val verification = CompletableFuture<JSONObject>()
            try {
                plan.chunks.forEachIndexed { chunkIndex, chunk ->
                    if (failure != null) return@forEachIndexed
                    host.unsafeReason()?.let { reasonUnsafe ->
                        failure = JSONObject()
                            .put("ok", false)
                            .put("safetyBlocked", true)
                            .put("error", reasonUnsafe)
                            .put("chunk", chunkIndex + 1)
                            .put("chunks", plan.chunks.size)
                        return@forEachIndexed
                    }
                    lastOperation = JSONObject()
                        .put("ok", true)
                        .put("state", "MAP_K_STARTING_CHUNK")
                        .put("busy", true)
                        .put("progress", (completedCells * 100 / plan.totalCells))
                        .put("startedAt", startedAt)
                        .put("totalCells", plan.totalCells)
                        .put("confirmedCells", completedCells)
                        .put("chunk", chunkIndex + 1)
                        .put("chunks", plan.chunks.size)

                    val started = try {
                        JSONObject(
                            host.startMapBatchWrite(
                                chunk.toString(),
                                "$reason • bloco ${chunkIndex + 1}/${plan.chunks.size}",
                            ) { verification.complete(it) },
                        )
                    } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Falha ao iniciar lote K")
                    }
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        failure = JSONObject(started.toString())
                            .put("chunk", chunkIndex + 1)
                            .put("chunks", plan.chunks.size)
                        return@forEachIndexed
                    }
                    adjustmentIds.put(started.optString("adjustmentId"))

                    val writer = awaitWriter(verification, host::mapStatus) { status, writerState ->
                        val writerProgress = status.optInt("progress", 0).coerceIn(0, 100)
                        val chunkProgressCells = chunk.length() * (writerProgress / 100.0)
                        val overallProgress = (((completedCells + chunkProgressCells) / plan.totalCells) * 100.0)
                            .toInt().coerceIn(0, 99)
                        lastOperation = JSONObject()
                            .put("ok", true)
                            .put("state", "MAP_K_WRITING")
                            .put("progress", overallProgress)
                            .put("startedAt", startedAt)
                            .put("totalCells", plan.totalCells)
                            .put("confirmedCells", completedCells)
                            .put("chunk", chunkIndex + 1)
                            .put("chunks", plan.chunks.size)
                            .put("writerState", writerState)
                            .put("writerMessage", status.optString("message", ""))
                    }
                    if (writer.optString("state") == "BATCH_CONFIRMED") {
                        completedCells += chunk.length()
                    } else {
                        failure = writer.put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)
                    }
                }
            } catch (error: Exception) {
                failure = JSONObject()
                    .put("ok", false)
                    .put("state", "FAILED")
                    .put("error", error.message ?: "Falha ao coordenar lote K")
            }

            val finishedAt = System.currentTimeMillis()
            val fullyConfirmed = failure == null && completedCells == plan.totalCells
            if (fullyConfirmed) {
                publishConfirmed(host, JSONObject()
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells)
                    .put("confirmedCells", completedCells)
                    .put("internalChunks", plan.chunks.size)
                    .put("adjustmentIds", adjustmentIds), verification, "MAP_K")
            } else {
                lastOperation = JSONObject()
                    .put("ok", false)
                    .put("state", "BATCH_PARTIAL_FAILED")
                    .put("busy", false)
                    .put("progress", if (plan.totalCells > 0) completedCells * 100 / plan.totalCells else 0)
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells)
                    .put("confirmedCells", completedCells)
                    .put("internalChunks", plan.chunks.size)
                    .put("adjustmentIds", adjustmentIds)
                    .put("partial", completedCells > 0)
                    .put("failure", failure ?: JSONObject().put("error", "Confirmação incompleta"))
                busy.set(false)
                host.refresh()
            }
        }

        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "MAP_K_QUEUED")
            .put("startedAt", startedAt)
            .put("totalCells", plan.totalCells)
            .put("internalChunks", plan.chunks.size)
            .toString()
    }

    @JavascriptInterface
    fun saveSession(fileName: String): String = activity?.serviceOrNull()?.v7SaveSession(fileName)
        ?: unavailable()

    @JavascriptInterface
    fun loadSession(fileName: String): String = activity?.serviceOrNull()?.v7LoadSession(fileName)
        ?: unavailable()

    private fun writeHost(): WriteHost? {
        testWriteHost?.let { return it }
        val currentActivity = activity ?: return null
        val service = currentActivity.serviceOrNull() ?: return null
        return object : WriteHost {
            override fun unsafeReason() = CalibrationWriteSafetyPolicy.unsafeReason(service.status())
            override fun startCurveReset(onVerified: (JSONObject) -> Unit) = service.startKFactorReset(onVerified)
            override fun startCurveBatchWrite(points: String, reason: String, onVerified: (JSONObject) -> Unit) =
                service.startKFactorWrite(points, reason, onVerified)
            override fun startMapBatchWrite(cells: String, reason: String, onVerified: (JSONObject) -> Unit) =
                service.startKBatchWrite(cells, reason, onVerified)
            override fun curveStatus() = service.kFactorStatusJson()
            override fun mapStatus() = service.kWriteStatusJson()
            override fun reconcile(target: String) = service.v7ReconcileConfirmedManualWrite(target)
            override fun refresh() = currentActivity.refreshWebUi()
        }
    }

    private fun safetyBlocked(reason: String): String = JSONObject()
        .put("ok", false)
        .put("safetyBlocked", true)
        .put("error", reason)
        .toString()

    private fun startOperation(
        state: String,
        action: (com.omegas.prohub.service.TelemetryForegroundService) -> String,
    ): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        if (!busy.compareAndSet(false, true)) {
            return JSONObject()
                .put("ok", false)
                .put("busy", true)
                .put("error", "Outra operação V8 está em andamento")
                .toString()
        }
        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject()
            .put("ok", true)
            .put("state", state)
            .put("busy", true)
            .put("startedAt", startedAt)
        executor.execute {
            val result = try {
                JSONObject(action(service))
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: "Falha V8")
            }
            lastOperation = JSONObject(result.toString())
                .put("state", if (result.optBoolean("ok")) "COMPLETED" else "FAILED")
                .put("busy", false)
                .put("startedAt", startedAt)
                .put("finishedAt", System.currentTimeMillis())
            busy.set(false)
            try { service.sessionRecorder.noteUserAction("v8:$state", "calibracao", null, result) } catch (_: Exception) { /* nunca atrapalha */ }
            currentActivity.refreshWebUi()
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", state)
            .put("startedAt", startedAt)
            .toString()
    }

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço V8 indisponível")
        .toString()
}