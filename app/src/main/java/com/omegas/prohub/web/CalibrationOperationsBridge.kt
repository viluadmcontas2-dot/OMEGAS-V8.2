package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.FailureKind
import com.omegas.prohub.calibration.MapBatchPlan
import com.omegas.prohub.calibration.MapKManualPlanner
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Única ponte de escrita/operações de Curva K e Mapa K chamada pela UI (JS `OmegasCalibration`).
 * Uma única fila de execução: um executor e uma flag `busy`. Toda operação serial ocorre fora
 * da thread da WebView e termina no readback; nada aqui é automático.
 *
 * `busy` só se solta no `finally` de cada operação: nenhuma exceção (nem `Error`) a deixa presa.
 */
class CalibrationOperationsBridge(activity: MainActivity) {
    companion object {
        const val JS_NAME = "OmegasCalibration"
        private const val OPERATION_TIMEOUT_MS = 15 * 60 * 1000L

        // Fila, `busy` e último resultado vivem no PROCESSO, não na Activity: se o Android recriar a
        // Activity no meio de uma gravação (tela, tema, central multimídia), a gravação não é
        // interrompida e a Activity nova lê o mesmo resultado ("Gravado"/"parcial") em getLastOperation.
        private val sharedExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "omegas-calibration-operations").apply { isDaemon = true }
        }
        private val sharedBusy = AtomicBoolean(false)
        @Volatile private var sharedLastOperation = JSONObject()
            .put("ok", true)
            .put("state", "IDLE")
            .put("busy", false)
        /** Atualização da tela da Activity VIVA (a que está na frente), nunca da que foi destruída. */
        @Volatile private var uiRefresher: (() -> Unit)? = null
    }

    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()
    private val executor get() = sharedExecutor
    private val busy get() = sharedBusy
    private var lastOperation: JSONObject
        get() = sharedLastOperation
        set(value) { sharedLastOperation = value }
    private val ownRefresher: () -> Unit = { activityRef.get()?.refreshWebUi() }

    init {
        uiRefresher = ownRefresher
    }

    /**
     * Chamado no onDestroy da Activity. NÃO encerra a fila: uma gravação em curso termina e o resultado
     * fica guardado para a próxima Activity. Só deixa de empurrar atualização para a tela morta.
     */
    fun destroy() {
        if (uiRefresher === ownRefresher) uiRefresher = null
    }

    @JavascriptInterface
    fun getLastOperation(): String = JSONObject(lastOperation.toString())
        .put("busy", busy.get())
        .toString()

    @JavascriptInterface
    fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String =
        MapKManualPlanner.preview(cellsJson, mode, adjustment).toString()

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

    /** Desfazer do Mapa K, passo 1: relê o mapa (somente leitura) e devolve o que voltaria. */
    @JavascriptInterface
    fun startMapRestorePrepare(adjustmentId: String): String =
        startOperation("MAP_RESTORE_PREPARING") { service ->
            service.prepareKMapRestore(adjustmentId)
        }

    /** Desfazer do Mapa K, passo 2 (toque do dono): o mesmo escritor em lote, com foto e readback. */
    @JavascriptInterface
    fun startMapRestoreWrite(cellsJson: String, adjustmentId: String): String =
        startMapWrite(cellsJson, 0, 0, "Desfazer Mapa K $adjustmentId", adjustmentId)

    /**
     * "Liberar Mapa K" (toque do dono): a saída do K insertion não foi confirmada (cabo caiu / app fechou)
     * e o Mapa K ficou travado. Manda a SAÍDA pela fila normal; só vira "Mapa K liberado" depois do ACK da
     * ECU. Resultado legível em getLastOperation (`recovered`, `failureKind`).
     */
    @JavascriptInterface
    fun startInsertionRecovery(): String = startOperation("INSERTION_RECOVERING") { service ->
        service.recoverKInsertionState()
    }

    @JavascriptInterface
    fun startCurveReset(): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        unsafeCalibrationWriteReason(service)?.let { return safetyBlocked(it) }
        if (!busy.compareAndSet(false, true)) {
            return JSONObject().put("ok", false).put("busy", true)
                .put("error", "Outra operação V8 está em andamento").toString()
        }
        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject()
            .put("ok", true)
            .put("state", "CURVE_RESET_QUEUED")
            .put("busy", true)
            .put("progress", 0)
            .put("message", "Preparando reset da Curva K para 1.0")
            .put("startedAt", startedAt)

        val accepted = launch("CURVE_RESET_FAILED", startedAt) {
            var finalStatus: JSONObject? = null
            try {
                unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
                    finalStatus = JSONObject()
                        .put("ok", false)
                        .put("state", "CURVE_RESET_FAILED")
                        .put("safetyBlocked", true)
                        .put("failureKind", FailureKind.APP)
                        .put("error", reasonUnsafe)
                }
                if (finalStatus == null) {
                    val started = JSONObject(service.startKFactorReset())
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        finalStatus = withKind(JSONObject(started.toString()))
                            .put("state", "CURVE_RESET_FAILED")
                    } else {
                        val deadline = System.currentTimeMillis() + OPERATION_TIMEOUT_MS
                        while (finalStatus == null) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Reset da Curva K interrompido")
                            if (System.currentTimeMillis() > deadline) {
                                finalStatus = JSONObject()
                                    .put("ok", false)
                                    .put("state", "CURVE_RESET_FAILED")
                                    .put("failureKind", FailureKind.APP)
                                    .put("error", "Tempo limite aguardando confirmação do reset da Curva K")
                                break
                            }
                            val status = try {
                                JSONObject(service.kFactorStatusJson())
                            } catch (error: Exception) {
                                JSONObject().put("state", "RESET_FAILED")
                                    .put("error", error.message ?: "Status da Curva K indisponível")
                            }
                            val writerState = status.optString("state", "")
                            val details = status.optJSONObject("details") ?: JSONObject()
                            val progress = status.optInt("progress", 0)
                            lastOperation = JSONObject(status.toString())
                                .put("ok", !writerState.contains("FAILED"))
                                .put("state", "CURVE_RESETTING")
                                .put("writerState", writerState)
                                .put("busy", true)
                                .put("progress", progress)
                                .put("startedAt", startedAt)
                            when {
                                writerState == "RESET_CONFIRMED" -> {
                                    // Só existe quando a leitura já mostrou a curva neutra: nada foi gravado.
                                    // A tela diz "Nada a gravar", nunca "Gravado".
                                    finalStatus = JSONObject(status.toString())
                                        .put("ok", true)
                                        .put("state", "BATCH_CONFIRMED")
                                        .put("readbackValid", true)
                                        .put("nothingToChange", true)
                                }
                                writerState == "BATCH_CONFIRMED" && details.optBoolean("readbackValid", false) -> {
                                    finalStatus = JSONObject(status.toString())
                                        .put("ok", true)
                                        .put("state", "BATCH_CONFIRMED")
                                        .put("readbackValid", true)
                                }
                                writerState.contains("FAILED") -> {
                                    finalStatus = JSONObject(status.toString())
                                        .put("ok", false)
                                        .put("state", "CURVE_RESET_FAILED")
                                }
                                else -> Thread.sleep(80L)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                finalStatus = JSONObject()
                    .put("ok", false)
                    .put("state", "CURVE_RESET_FAILED")
                    .put("failureKind", FailureKind.APP)
                    .put("error", error.message ?: "Falha ao coordenar reset da Curva K")
            }

            val status = hoistWriterDetails(
                finalStatus ?: JSONObject()
                    .put("ok", false)
                    .put("state", "CURVE_RESET_FAILED")
                    .put("failureKind", FailureKind.APP)
                    .put("error", "Confirmação do reset ausente"),
            )
            val confirmed = status.optString("state") == "BATCH_CONFIRMED" &&
                status.optBoolean("readbackValid", false)
            lastOperation = JSONObject(status.toString())
                .put("ok", confirmed)
                .put("busy", false)
                .put("state", if (confirmed) "BATCH_CONFIRMED" else "CURVE_RESET_FAILED")
                .put("readbackValid", confirmed)
                .put("completedAt", System.currentTimeMillis())
            refreshUi()
        }
        if (!accepted) return executorClosed()

        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "CURVE_RESET_QUEUED")
            .put("busy", true)
            .put("humanConfirmationRequired", true)
            .toString()
    }

    @JavascriptInterface
    fun startCurveBatchWrite(pointsJson: String, reason: String): String =
        runCurveWrite(pointsJson, reason, "")

    /**
     * Desfazer da Curva K (toque do dono): grava de volta SOMENTE os valores da foto [fileName],
     * pelo mesmo escritor em lote (foto antes, ACK por ponto, readback final).
     */
    @JavascriptInterface
    fun startCurveRestoreWrite(pointsJson: String, fileName: String): String =
        runCurveWrite(pointsJson, "Restaurar backup Curva K $fileName", fileName)

    private fun runCurveWrite(pointsJson: String, reason: String, restoreFile: String): String {
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
            return JSONObject().put("ok", false).put("busy", true).put("error", "Outra operação V8 está em andamento").toString()
        }
        val startedAt = System.currentTimeMillis()
        lastOperation = JSONObject()
            .put("ok", true)
            .put("state", "CURVE_WRITE_QUEUED")
            .put("busy", true)
            .put("progress", 0)
            .put("startedAt", startedAt)
            .put("totalPoints", points.length())

        val accepted = launch("CURVE_WRITE_FAILED", startedAt) {
            var finalStatus: JSONObject? = null
            try {
                unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
                    finalStatus = JSONObject()
                        .put("ok", false)
                        .put("state", "CURVE_WRITE_FAILED")
                        .put("safetyBlocked", true)
                        .put("failureKind", FailureKind.APP)
                        .put("error", reasonUnsafe)
                }
                if (finalStatus == null) {
                    val started = JSONObject(service.startKFactorWrite(points.toString(), reason, restoreFile))
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        finalStatus = withKind(JSONObject(started.toString()))
                            .put("state", "CURVE_WRITE_FAILED")
                    } else {
                        val deadline = System.currentTimeMillis() + OPERATION_TIMEOUT_MS
                        while (finalStatus == null) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Operação V8 interrompida")
                            if (System.currentTimeMillis() > deadline) {
                                finalStatus = JSONObject().put("ok", false).put("state", "TIMEOUT")
                                    .put("failureKind", FailureKind.APP)
                                    .put("error", "Tempo limite aguardando confirmação da Curva K")
                                break
                            }
                            val status = try { JSONObject(service.kFactorStatusJson()) }
                            catch (error: Exception) { JSONObject().put("state", "FAILED").put("error", error.message ?: "Status Curva K indisponível") }
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
                    .put("failureKind", FailureKind.APP)
                    .put("error", error.message ?: "Falha ao coordenar Curva K")
            }
            val status = hoistWriterDetails(
                finalStatus ?: JSONObject().put("ok", false).put("failureKind", FailureKind.APP).put("error", "Confirmação ausente"),
            )
            val details = status.optJSONObject("details") ?: JSONObject()
            val confirmed = status.optString("state") == "BATCH_CONFIRMED" && details.optBoolean("readbackValid", false)
            lastOperation = if (confirmed) {
                JSONObject(status.toString())
                    .put("ok", true)
                    .put("state", "BATCH_CONFIRMED")
                    .put("busy", false)
                    .put("progress", 100)
                    .put("readbackValid", true)
                    .put("humanConfirmed", true)
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
            refreshUi()
        }
        if (!accepted) return executorClosed()
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "CURVE_WRITE_QUEUED")
            .put("startedAt", startedAt)
            .put("totalPoints", points.length())
            .toString()
    }

    /**
     * Uma única intenção humana pode conter toda a grade 12x12 (até 144 células).
     * O plano preserva essa intenção como um único lote nativo: sem ramping e
     * sem pausas artificiais, mantendo backup, ACK por write, insertion seguro
     * e readback final das linhas afetadas.
     */
    @JavascriptInterface
    fun startMapBatchWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String): String =
        startMapWrite(cellsJson, maxStep, pauseMs, reason, "")

    private fun startMapWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String, restoreId: String): String {
        val currentActivity = activity ?: return unavailable()
        val service = currentActivity.serviceOrNull() ?: return unavailable()
        unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
            return safetyBlocked(reasonUnsafe)
        }
        val cells = try {
            JSONArray(cellsJson)
        } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de células inválido").toString()
        }
        val plan = try {
            MapBatchPlan.build(cells)
        } catch (error: Exception) {
            // IllegalArgumentException (lote inválido) e JSONException (item malformado): nunca derruba a ponte.
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

        val accepted = launch("BATCH_PARTIAL_FAILED", startedAt) {
            val adjustmentIds = JSONArray()
            var completedCells = 0
            var failure: JSONObject? = null
            try {
                plan.chunks.forEachIndexed { chunkIndex, chunk ->
                    if (failure != null) return@forEachIndexed
                    unsafeCalibrationWriteReason(service)?.let { reasonUnsafe ->
                        failure = JSONObject()
                            .put("ok", false)
                            .put("safetyBlocked", true)
                            .put("failureKind", FailureKind.APP)
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
                            if (restoreId.isNotBlank()) {
                                service.startKMapRestoreWrite(chunk.toString(), restoreId, reason)
                            } else {
                                service.startKBatchWrite(
                                    chunk.toString(),
                                    maxStep,
                                    pauseMs,
                                    "$reason • bloco ${chunkIndex + 1}/${plan.chunks.size}",
                                )
                            },
                        )
                    } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Falha ao iniciar lote K")
                    }
                    if (!started.optBoolean("ok") || !started.optBoolean("started")) {
                        failure = withKind(JSONObject(started.toString()))
                            .put("chunk", chunkIndex + 1)
                            .put("chunks", plan.chunks.size)
                        return@forEachIndexed
                    }
                    adjustmentIds.put(started.optString("adjustmentId"))

                    val deadline = System.currentTimeMillis() + OPERATION_TIMEOUT_MS
                    var chunkFinished = false
                    while (!chunkFinished && failure == null) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Operação V8 interrompida")
                        }
                        if (System.currentTimeMillis() > deadline) {
                            failure = JSONObject()
                                .put("ok", false)
                                .put("state", "TIMEOUT")
                                .put("failureKind", FailureKind.APP)
                                .put("error", "Tempo limite aguardando confirmação do bloco ${chunkIndex + 1}")
                            break
                        }

                        val writer = try {
                            JSONObject(service.kWriteStatusJson())
                        } catch (error: Exception) {
                            JSONObject().put("state", "FAILED").put("error", error.message ?: "Status K indisponível")
                        }
                        val writerState = writer.optString("state", "")
                        val writerProgress = writer.optInt("progress", 0).coerceIn(0, 100)
                        val chunkProgressCells = chunk.length() * (writerProgress / 100.0)
                        val overallProgress = (((completedCells + chunkProgressCells) / plan.totalCells) * 100.0)
                            .toInt().coerceIn(0, 99)

                        lastOperation = JSONObject()
                            .put("ok", true)
                            .put("state", "MAP_K_WRITING")
                            .put("busy", true)
                            .put("progress", overallProgress)
                            .put("startedAt", startedAt)
                            .put("totalCells", plan.totalCells)
                            .put("confirmedCells", completedCells)
                            .put("chunk", chunkIndex + 1)
                            .put("chunks", plan.chunks.size)
                            .put("writerState", writerState)
                            .put("writerMessage", writer.optString("message", ""))
                            .put("writerProgress", writerProgress)

                        when {
                            writerState == "BATCH_CONFIRMED" -> {
                                completedCells += chunk.length()
                                chunkFinished = true
                            }
                            writerState.contains("FAILED") || writerState.startsWith("SAFETY_LOCKED") -> {
                                failure = JSONObject(writer.toString())
                                    .put("chunk", chunkIndex + 1)
                                    .put("chunks", plan.chunks.size)
                            }
                            else -> Thread.sleep(80L)
                        }
                    }
                }
            } catch (error: Exception) {
                failure = JSONObject()
                    .put("ok", false)
                    .put("state", "FAILED")
                    .put("failureKind", FailureKind.APP)
                    .put("error", error.message ?: "Falha ao coordenar lote K")
            }

            val finishedAt = System.currentTimeMillis()
            val fullyConfirmed = failure == null && completedCells == plan.totalCells
            lastOperation = if (fullyConfirmed) {
                JSONObject()
                    .put("ok", true)
                    .put("state", "BATCH_CONFIRMED")
                    .put("busy", false)
                    .put("progress", 100)
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells)
                    .put("confirmedCells", completedCells)
                    .put("internalChunks", plan.chunks.size)
                    .put("adjustmentIds", adjustmentIds)
                    .put("humanConfirmed", true)
                    .put("readbackValid", true)
            } else {
                val failed = failure ?: JSONObject().put("error", "Confirmação incompleta")
                val details = failed.optJSONObject("details") ?: JSONObject()
                // O lote é um bloco só: "blocos confirmados" nunca vê células já escritas numa falha no meio.
                // As células com ACK vêm dos eventos confirmados do escritor.
                val ackedCells = details.optJSONArray("confirmedEvents")?.length() ?: 0
                val reportedCells = maxOf(completedCells, ackedCells)
                val ecuPartiallyChanged = reportedCells > 0 || details.optBoolean("mutationMayHaveStarted", false)
                JSONObject()
                    .put("ok", false)
                    .put("state", "BATCH_PARTIAL_FAILED")
                    .put("busy", false)
                    .put("progress", if (plan.totalCells > 0) reportedCells * 100 / plan.totalCells else 0)
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt)
                    .put("totalCells", plan.totalCells)
                    .put("confirmedCells", reportedCells)
                    .put("internalChunks", plan.chunks.size)
                    .put("adjustmentIds", adjustmentIds)
                    .put("backupId", adjustmentIds.optString(adjustmentIds.length() - 1, ""))
                    .put("partial", ecuPartiallyChanged)
                    .put("ecuPartiallyChanged", ecuPartiallyChanged)
                    .put("failureKind", failed.optString("failureKind", details.optString("failureKind", FailureKind.APP)))
                    .put("error", failed.optString("error", failed.optString("message", "")))
                    .put("failure", failed)
            }
            refreshUi()
        }
        if (!accepted) return executorClosed()

        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "MAP_K_QUEUED")
            .put("startedAt", startedAt)
            .put("totalCells", plan.totalCells)
            .put("internalChunks", plan.chunks.size)
            .toString()
    }

    private fun unsafeCalibrationWriteReason(service: com.omegas.prohub.service.TelemetryForegroundService): String? =
        CalibrationWriteSafetyPolicy.unsafeReason(service.status())

    private fun safetyBlocked(reason: String): String = JSONObject()
        .put("ok", false)
        .put("safetyBlocked", true)
        .put("failureKind", FailureKind.APP)
        .put("error", reason)
        .toString()

    /**
     * Sobe para o topo do resultado o que a UI precisa para ser honesta: a foto feita antes da
     * escrita (Desfazer restaura ESTA), se a ECU pode ter sido alterada e de onde veio a falha
     * (cabo/USB × ECU × app). Sempre preenche `error` a partir da mensagem do escritor.
     */
    private fun hoistWriterDetails(status: JSONObject): JSONObject {
        val details = status.optJSONObject("details") ?: return status
        for (key in arrayOf("photoFile", "partial", "mutationMayHaveStarted", "failureKind", "backupId")) {
            if (details.has(key) && !status.has(key)) status.put(key, details.get(key))
        }
        val failed = !status.optBoolean("ok", true) || status.optString("state").contains("FAILED")
        if (failed && status.optString("error").isBlank()) {
            status.optString("message").takeIf { it.isNotBlank() }?.let { status.put("error", it) }
        }
        return status
    }

    /**
     * Roda [task] na fila única. Qualquer `Throwable` vira estado de falha legível e `busy` é solto
     * no `finally`; se o executor já foi encerrado, `busy` também é solto e devolve `false`.
     */
    private fun launch(failedState: String, startedAt: Long, task: () -> Unit): Boolean = try {
        executor.execute {
            try {
                task()
            } catch (error: Throwable) {
                lastOperation = JSONObject()
                    .put("ok", false)
                    .put("state", failedState)
                    .put("busy", false)
                    .put("startedAt", startedAt)
                    .put("finishedAt", System.currentTimeMillis())
                    .put("failureKind", FailureKind.APP)
                    .put("error", error.message ?: "Falha inesperada na operação V8")
            } finally {
                busy.set(false)
            }
        }
        true
    } catch (_: RejectedExecutionException) {
        busy.set(false)
        false
    }

    private fun refreshUi() {
        try { uiRefresher?.invoke() } catch (_: Throwable) {}
    }

    internal fun startOperation(
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
        val accepted = launch("FAILED", startedAt) {
            val result = try {
                val raw = JSONObject(action(service))
                if (raw.optBoolean("ok")) raw else withKind(raw)
            } catch (error: Throwable) {
                JSONObject().put("ok", false)
                    .put("failureKind", FailureKind.of(error))
                    .put("error", error.message ?: "Falha V8")
            }
            lastOperation = JSONObject(result.toString())
                .put("state", if (result.optBoolean("ok")) "COMPLETED" else "FAILED")
                .put("busy", false)
                .put("startedAt", startedAt)
                .put("finishedAt", System.currentTimeMillis())
            refreshUi()
        }
        if (!accepted) return executorClosed()
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", state)
            .put("startedAt", startedAt)
            .toString()
    }

    /** Falha sem origem declarada: classifica pela mensagem (cabo/USB × ECU × app). Transporte nunca vira "ECU recusou". */
    private fun withKind(failure: JSONObject): JSONObject {
        if (failure.optString("failureKind").isBlank()) {
            failure.put("failureKind", FailureKind.ofMessage(failure.optString("error", failure.optString("message", ""))))
        }
        return failure
    }

    private fun executorClosed(): String = JSONObject()
        .put("ok", false)
        .put("failureKind", FailureKind.APP)
        .put("error", "A fila de operações foi encerrada; reabra o aplicativo")
        .toString()

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço V8 indisponível")
        .toString()
}
