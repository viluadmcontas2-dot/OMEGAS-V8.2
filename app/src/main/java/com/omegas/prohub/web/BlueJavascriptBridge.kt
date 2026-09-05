package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.MapBatchPlan
import com.omegas.prohub.calibration.MapKManualPlanner
import com.omegas.prohub.service.blueCalibrationStateJson
import com.omegas.prohub.service.blueIngestLearningSnapshot
import com.omegas.prohub.service.blueReconcileConfirmedManualWrite
import com.omegas.prohub.service.blueSynchronizeCalibration
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Única ponte da interface para leitura e escrita manual da calibração.
 * Toda operação serial ocorre fora da thread da WebView. Esta ponte não calcula
 * equivalência nem alvo K; ela apenas orquestra os writers físicos comprovados.
 */
class BlueJavascriptBridge(activity: MainActivity) {
    companion object {
        private const val OPERATION_TIMEOUT_MS = 15 * 60 * 1000L
    }

    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-blue-calibration").apply { isDaemon = true }
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
    fun getState(): String = activity?.serviceOrNull()?.blueCalibrationStateJson()
        ?: unavailable()

    @JavascriptInterface
    fun getLastOperation(): String = JSONObject(lastOperation.toString())
        .put("busy", busy.get())
        .toString()

    @JavascriptInterface
    fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String =
        MapKManualPlanner.preview(cellsJson, mode, adjustment).toString()

    /** Importa somente evidência física já coletada; não executa escrita. */
    @JavascriptInterface
    fun ingestLearningSnapshot(snapshotJson: String): String =
        activity?.serviceOrNull()?.blueIngestLearningSnapshot(snapshotJson)
            ?: unavailable()

    @JavascriptInterface
    fun synchronizeFromEcu(): String = startOperation("SYNCHRONIZING_ECU") { service ->
        service.blueSynchronizeCalibration()
    }

    @JavascriptInterface
    fun startCurveRead(): String = startOperation("CURVE_READING") { service ->
        service.readKFactorCurve()
    }

    @JavascriptInterface
    fun startCurveBatchWrite(pointsJson: String, reason: String): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
            return safetyBlocked(reasonUnsafe)
        }
        val points = try { JSONArray(pointsJson) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de pontos inválido").toString()
        }
        if (points.length() !in 1..30) {
            return JSONObject().put("ok", false).put("error", "Selecione entre 1 e 30 pontos da Curva K").toString()
        }
        if (!busy.compareAndSet(false, true)) {
            return JSONObject().put("ok", false).put("busy", true).put("error", "Outra operação de calibração está em andamento").toString()
        }
        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject()
            .put("ok", true)
            .put("state", "CURVE_WRITE_QUEUED")
            .put("busy", true)
            .put("progress", 0)
            .put("startedAt", startedAt)
            .put("totalPoints", points.length())

        executor.execute {
            var finalStatus: JSONObject? = null
            try {
                unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
                    finalStatus = JSONObject()
                        .put("ok", false)
                        .put("state", "CURVE_WRITE_FAILED")
                        .put("safetyBlocked", true)
                        .put("error", reasonUnsafe)
                }
                if (finalStatus == null) {
                    val started = JSONObject(service.startKFactorWrite(points.toString(), reason))
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        finalStatus = JSONObject(started.toString()).put("state", "CURVE_WRITE_FAILED")
                    } else {
                        val deadline = System.currentTimeMillis() + OPERATION_TIMEOUT_MS
                        while (finalStatus == null) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Operação interrompida")
                            if (System.currentTimeMillis() > deadline) {
                                finalStatus = JSONObject().put("ok", false).put("state", "TIMEOUT")
                                    .put("error", "Tempo limite aguardando confirmação da Curva K")
                                break
                            }
                            val status = try { JSONObject(service.kFactorStatusJson()) }
                            catch (error: Exception) {
                                JSONObject().put("state", "FAILED").put("error", error.message ?: "Status da Curva K indisponível")
                            }
                            val writerState = status.optString("state", "")
                            lastOperation = JSONObject(status.toString())
                                .put("ok", !writerState.contains("FAILED"))
                                .put("state", "CURVE_WRITING")
                                .put("writerState", writerState)
                                .put("busy", true)
                                .put("startedAt", startedAt)
                                .put("totalPoints", points.length())
                            if (writerState == "BATCH_CONFIRMED" || writerState.contains("FAILED")) {
                                finalStatus = status
                            } else {
                                Thread.sleep(80L)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                finalStatus = JSONObject().put("ok", false).put("state", "CURVE_WRITE_FAILED")
                    .put("error", error.message ?: "Falha ao coordenar Curva K")
            }
            val status = finalStatus ?: JSONObject().put("ok", false).put("error", "Confirmação ausente")
            val details = status.optJSONObject("details") ?: JSONObject()
            val confirmed = status.optString("state") == "BATCH_CONFIRMED" && details.optBoolean("readbackValid", false)
            val reconciliation = if (confirmed) {
                try { JSONObject(service.blueReconcileConfirmedManualWrite()) }
                catch (error: Exception) {
                    JSONObject().put("ok", false).put("error", error.message ?: "Readback Blue indisponível")
                }
            } else null
            lastOperation = if (confirmed) {
                JSONObject(status.toString())
                    .put("ok", true)
                    .put("state", "BATCH_CONFIRMED")
                    .put("busy", false)
                    .put("progress", 100)
                    .put("readbackValid", true)
                    .put("humanConfirmed", true)
                    .put("blueReconciliation", reconciliation ?: JSONObject().put("ok", false))
                    .put("startedAt", startedAt)
                    .put("finishedAt", System.currentTimeMillis())
            } else {
                JSONObject(status.toString())
                    .put("ok", false)
                    .put("state", "CURVE_WRITE_FAILED")
                    .put("busy", false)
                    .put("startedAt", startedAt)
                    .put("finishedAt", System.currentTimeMillis())
            }
            busy.set(false)
            currentActivity.refreshWebUi()
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "CURVE_WRITE_QUEUED")
            .put("startedAt", startedAt)
            .put("totalPoints", points.length())
            .toString()
    }

    /**
     * Uma intenção humana pode conter toda a grade editável. Internamente o lote
     * é dividido em blocos pequenos para preservar backup, ACK, readback e
     * recuperação parcial do writer físico.
     */
    @JavascriptInterface
    fun startMapBatchWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
            return safetyBlocked(reasonUnsafe)
        }
        val cells = try { JSONArray(cellsJson) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de células inválido").toString()
        }
        val plan = try { MapBatchPlan.build(cells) } catch (error: IllegalArgumentException) {
            return JSONObject().put("ok", false).put("error", error.message ?: "Lote inválido").toString()
        }
        if (!busy.compareAndSet(false, true)) {
            return JSONObject().put("ok", false).put("busy", true)
                .put("error", "Outra operação de calibração está em andamento").toString()
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
            try {
                plan.chunks.forEachIndexed { chunkIndex, chunk ->
                    if (failure != null) return@forEachIndexed
                    unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
                        failure = JSONObject().put("ok", false).put("safetyBlocked", true)
                            .put("error", reasonUnsafe).put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)
                        return@forEachIndexed
                    }
                    lastOperation = JSONObject()
                        .put("ok", true).put("state", "MAP_K_STARTING_CHUNK").put("busy", true)
                        .put("progress", completedCells * 100 / plan.totalCells).put("startedAt", startedAt)
                        .put("totalCells", plan.totalCells).put("confirmedCells", completedCells)
                        .put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)

                    val started = try {
                        JSONObject(service.startKBatchWrite(
                            chunk.toString(), maxStep, pauseMs,
                            "$reason • bloco ${chunkIndex + 1}/${plan.chunks.size}",
                        ))
                    } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Falha ao iniciar lote K")
                    }
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        failure = JSONObject(started.toString()).put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)
                        return@forEachIndexed
                    }
                    adjustmentIds.put(started.optString("adjustmentId"))

                    val deadline = System.currentTimeMillis() + OPERATION_TIMEOUT_MS
                    var chunkFinished = false
                    while (!chunkFinished && failure == null) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Operação interrompida")
                        if (System.currentTimeMillis() > deadline) {
                            failure = JSONObject().put("ok", false).put("state", "TIMEOUT")
                                .put("error", "Tempo limite aguardando confirmação do bloco ${chunkIndex + 1}")
                            break
                        }
                        val writer = try { JSONObject(service.kWriteStatusJson()) }
                        catch (error: Exception) {
                            JSONObject().put("state", "FAILED").put("error", error.message ?: "Status K indisponível")
                        }
                        val writerState = writer.optString("state", "")
                        val writerProgress = writer.optInt("progress", 0).coerceIn(0, 100)
                        val chunkProgressCells = chunk.length() * (writerProgress / 100.0)
                        val overallProgress = (((completedCells + chunkProgressCells) / plan.totalCells) * 100.0)
                            .toInt().coerceIn(0, 99)
                        lastOperation = JSONObject()
                            .put("ok", true).put("state", "MAP_K_WRITING").put("busy", true)
                            .put("progress", overallProgress).put("startedAt", startedAt)
                            .put("totalCells", plan.totalCells).put("confirmedCells", completedCells)
                            .put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)
                            .put("writerState", writerState).put("writerMessage", writer.optString("message", ""))
                            .put("writerProgress", writerProgress)
                        when {
                            writerState == "BATCH_CONFIRMED" -> {
                                completedCells += chunk.length()
                                chunkFinished = true
                            }
                            writerState.contains("FAILED") || writerState.startsWith("SAFETY_LOCKED") -> {
                                failure = JSONObject(writer.toString()).put("chunk", chunkIndex + 1).put("chunks", plan.chunks.size)
                            }
                            else -> Thread.sleep(80L)
                        }
                    }
                }
            } catch (error: Exception) {
                failure = JSONObject().put("ok", false).put("state", "FAILED")
                    .put("error", error.message ?: "Falha ao coordenar lote K")
            }

            val finishedAt = System.currentTimeMillis()
            val fullyConfirmed = failure == null && completedCells == plan.totalCells
            val reconciliation = if (fullyConfirmed) {
                try { JSONObject(service.blueReconcileConfirmedManualWrite()) }
                catch (error: Exception) {
                    JSONObject().put("ok", false).put("error", error.message ?: "Readback Blue indisponível")
                }
            } else null
            lastOperation = if (fullyConfirmed) {
                JSONObject()
                    .put("ok", true).put("state", "BATCH_CONFIRMED").put("busy", false).put("progress", 100)
                    .put("startedAt", startedAt).put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells).put("confirmedCells", completedCells)
                    .put("internalChunks", plan.chunks.size).put("adjustmentIds", adjustmentIds)
                    .put("humanConfirmed", true).put("readbackValid", true)
                    .put("blueReconciliation", reconciliation ?: JSONObject().put("ok", false))
            } else {
                JSONObject()
                    .put("ok", false).put("state", "BATCH_PARTIAL_FAILED").put("busy", false)
                    .put("progress", if (plan.totalCells > 0) completedCells * 100 / plan.totalCells else 0)
                    .put("startedAt", startedAt).put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells).put("confirmedCells", completedCells)
                    .put("internalChunks", plan.chunks.size).put("adjustmentIds", adjustmentIds)
                    .put("partial", completedCells > 0)
                    .put("failure", failure ?: JSONObject().put("error", "Confirmação incompleta"))
            }
            busy.set(false)
            currentActivity.refreshWebUi()
        }

        return JSONObject()
            .put("ok", true).put("started", true).put("state", "MAP_K_QUEUED")
            .put("startedAt", startedAt).put("totalCells", plan.totalCells)
            .put("internalChunks", plan.chunks.size).toString()
    }

    private fun unsafeCalibrationWriteReason(service: com.omegas.prohub.service.TelemetryForegroundService): String? =
        CalibrationWriteSafetyPolicy.unsafeReason(service.status())

    private fun safetyBlocked(reason: String): String = JSONObject()
        .put("ok", false).put("safetyBlocked", true).put("error", reason).toString()

    private fun startOperation(
        state: String,
        action: (com.omegas.prohub.service.TelemetryForegroundService) -> String,
    ): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        if (!busy.compareAndSet(false, true)) {
            return JSONObject().put("ok", false).put("busy", true)
                .put("error", "Outra operação de calibração está em andamento").toString()
        }
        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject().put("ok", true).put("state", state).put("busy", true).put("startedAt", startedAt)
        executor.execute {
            val result = try { JSONObject(action(service)) }
            catch (error: Exception) { JSONObject().put("ok", false).put("error", error.message ?: "Falha de calibração") }
            lastOperation = JSONObject(result.toString())
                .put("state", if (result.optBoolean("ok")) "COMPLETED" else "FAILED")
                .put("busy", false).put("startedAt", startedAt).put("finishedAt", System.currentTimeMillis())
            busy.set(false)
            currentActivity.refreshWebUi()
        }
        return JSONObject().put("ok", true).put("started", true).put("state", state).put("startedAt", startedAt).toString()
    }

    private fun unavailable(): String = JSONObject()
        .put("ok", false).put("error", "Serviço indisponível").toString()
}
