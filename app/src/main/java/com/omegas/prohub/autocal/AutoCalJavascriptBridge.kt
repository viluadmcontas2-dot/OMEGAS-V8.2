package com.omegas.prohub.autocal

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.service.TelemetryForegroundService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Bridge de paridade host-side AutoCal com o ProgBase.
 *
 * A ECU continua sendo a autoridade do AutoMatch automático. O bridge apenas
 * reproduz operações host-side comprovadas do ProgBase; inteligência adicional
 * do OMEGAS não substitui comandos/estados nativos. Projeção, leitura manual e monitor nativo permanecem separados. As ações nativas
 * ficam numa superfície separada: são preparadas, revisadas no OMEGAS e então
 * executadas diretamente pelo manager canônico com ACK/readback. O manager mora no serviço
 * ([TelemetryForegroundService.nativeActions]): o apagamento automático de pontos fora da curva
 * (spec 2026-10-07) funciona com a tela fechada, e esta ponte só usa a mesma instância.
 */
class AutoCalJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val managerLock = Any()
    private var managerService: TelemetryForegroundService? = null
    private var manager: AutoCalSnapshotManager? = null

    // Resultados prontos para a WebView: o cálculo pesado roda em segundo plano enquanto a tela
    // está aberta e a chamada devolve o último valor na hora (a bridge bloqueia o JavaScript).
    private val projectionMemo = BackgroundMemo(refreshMs = 1_000L, staleMs = 2_500L) { computeUiProjection() }
    private val equivalenceMemo = BackgroundMemo(refreshMs = 2_000L, staleMs = 6_000L) { computeEquivalence() }
    private val refinedAnalysisMemo = BackgroundMemo(refreshMs = 2_000L, staleMs = 6_000L) { computeRefinedAnalysis() }
    private val warmer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "omegas-autocal-warm").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }.also { executor ->
        executor.scheduleWithFixedDelay({
            try { projectionMemo.refreshIfWatched() } catch (_: Throwable) {}
            try { equivalenceMemo.refreshIfWatched() } catch (_: Throwable) {}
            try { refinedAnalysisMemo.refreshIfWatched() } catch (_: Throwable) {}
        }, 300L, 300L, TimeUnit.MILLISECONDS)
    }

    @JavascriptInterface
    fun getStatus(): String = currentManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getSnapshot(): String = currentManager()?.latestSnapshotJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorStatus(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalStatusJson() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorSnapshot(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalSnapshotJson() ?: unavailable()

    @JavascriptInterface
    fun getUiProjection(): String = projectionMemo.getNonBlocking(
        """{"ok":false,"source":"NONE","referenceUsable":false,"snapshot":{"available":false},"error":"Atualizando projeção AutoCal"}""",
    )

    private fun computeUiProjection(): String = try {
        val activity = activityRef.get() ?: throw IllegalStateException("Tela indisponível")
        val service = activity.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        val manual = currentManager()
        val nativeStatus = JSONObject(service.nativeAutoCalStatusJson())
        val nativeSnapshot = JSONObject(service.nativeAutoCalSnapshotJson())
        val manualStatus = manual?.statusJson() ?: JSONObject()
        val manualSnapshot = manual?.latestSnapshotJson() ?: JSONObject().put("available", false)
        AutoCalUiProjection.project(
            nativeStatus = nativeStatus,
            nativeSnapshot = nativeSnapshot,
            manualStatus = manualStatus,
            manualSnapshot = manualSnapshot,
        ).toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Projeção AutoCal indisponível")
    }

    @JavascriptInterface
    fun getSessionLedgerStatus(): String = activityRef.get()?.serviceOrNull()?.sessionRecorderStatusJson() ?: unavailable()

    @JavascriptInterface
    fun listAutoCalSessions(): String = activityRef.get()?.serviceOrNull()?.sessionRecorderListJson()?.takeIf { it != "null" } ?: "[]"

    @JavascriptInterface
    fun exportAutoCalSession(sessionId: String) {
        activityRef.get()?.exportSession(sessionId)
    }

    @JavascriptInterface
    fun startRead(): String {
        projectionMemo.invalidate()
        return currentManager()?.startRead()?.toString() ?: unavailable()
    }

    @JavascriptInterface
    fun cancelRead(): String = currentManager()?.cancel()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeActionStatus(): String =
        currentNativeManager()?.statusJson()?.toString() ?: unavailable()

    /**
     * Limpeza automática (pontos fora da curva do GNV e da gasolina), para a linha discreta do AutoCal: ligada ou pausada,
     * o motivo (código; a tela traduz), quantos pontos o app pediu para reaprender nesta conexão e os apagamentos
     * recentes (a tela acinzenta esses pontos até a próxima leitura da ECU, como no apagamento pelo dono).
     */
    @JavascriptInterface
    fun getAutoCleanupStatus(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        val summary = service.autoIdleCleanup.uiSummary()
        JSONObject()
            .put("ok", true)
            .put("active", summary.active)
            .put("armed", summary.armed)
            .put("enabled", summary.enabled)
            .put("pauseCode", summary.pauseCode?.name ?: JSONObject.NULL)
            .put("waitReason", summary.waitReason)
            .put("relearnedThisSession", summary.relearnedThisSession)
            .put(
                "recentDeletes",
                JSONArray().also { array ->
                    summary.recentDeletes.forEach { item ->
                        array.put(
                            JSONObject()
                                .put("receiptId", item.receiptId)
                                .put("fuel", item.fuel.wireName)
                                .put("indexes", JSONArray(item.indexes))
                                .put("atMs", item.atMs),
                        )
                    }
                },
            )
            .toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Limpeza automática indisponível")
    }

    /**
     * Toque do dono no rodapé do AutoCal: arma ("Ativar limpeza automática") ou desarma ("Desativar limpeza").
     * Armar exige sessão USB válida e não apaga nada no toque; o serviço publica a revisão para a tela.
     */
    @JavascriptInterface
    fun setAutoCleanupArmed(armed: Boolean): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.setAutoCleanupArmed(armed).toString()
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("armed", false).put("error", error.message ?: "Limpeza automática indisponível").toString()
    }

    @JavascriptInterface
    fun prepareNativeAction(action: String): String {
        val requested = action.trim().uppercase()
        if (requested == "NEUTRALIZE_LIVE_K") {
            return localFailure("NEUTRALIZE_LIVE_K foi removido: use RESET_K_FACTOR, que executa o Reset K provado do ProgBase em MUL_ACT")
        }

        val parsed = try {
            AutoCalNativeActionManager.Action.valueOf(requested)
        } catch (_: Exception) {
            return localFailure("Ação nativa inválida")
        }
        if (parsed.operationalToggle) {
            return localFailure("Iniciar/Pausar usa a ação operacional de um toque")
        }
        if (parsed !in setOf(
                AutoCalNativeActionManager.Action.RESET_PETROL,
                AutoCalNativeActionManager.Action.RESET_GAS,
                AutoCalNativeActionManager.Action.RESET_K_FACTOR,
                AutoCalNativeActionManager.Action.FINISH_AUTOCAL,
                AutoCalNativeActionManager.Action.FINISH_AUTOMATCH,
                AutoCalNativeActionManager.Action.DELETE_POINT,
            )
        ) {
            return localFailure("Ação destrutiva não suportada")
        }
        return currentNativeManager()?.prepare(parsed.name)?.toString() ?: unavailable()
    }

    @JavascriptInterface
    fun preparePointDelete(fuel: String, index: Int): String =
        currentNativeManager()?.preparePointDelete(fuel, index)?.toString() ?: unavailable()

    @JavascriptInterface
    fun preparePointDeleteBatch(targetsJson: String): String = try {
        val array = org.json.JSONArray(targetsJson)
        val targets = buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("Seleção de ponto inválida")
                add(
                    com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Target(
                        fuel = com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel.parse(item.optString("fuel")),
                        index = item.getInt("index"),
                    ),
                )
            }
        }
        currentNativeManager()?.preparePointDeletes(targets)?.toString() ?: unavailable()
    } catch (error: Exception) {
        localFailure(error.message ?: "Seleção de pontos inválida")
    }

    @JavascriptInterface
    fun setAcquisitionEnabled(enabled: Boolean): String {
        val actionManager = currentNativeManager() ?: return unavailable()
        noLocalControlFailure()?.let { return it }
        val action = if (enabled) {
            AutoCalNativeActionManager.Action.ENABLE_AUTO_CAL
        } else {
            AutoCalNativeActionManager.Action.DISABLE_AUTO_CAL
        }
        val prepared = actionManager.prepare(action.name)
        if (!prepared.optBoolean("ok", false) || !prepared.optBoolean("prepared", false)) {
            return prepared.toString()
        }
        if (prepared.optBoolean("requiresCriticalConfirmation", true)) {
            actionManager.clearPreparation()
            return localFailure("Ação operacional foi classificada incorretamente como crítica")
        }
        // Intenção manual do dono: desarma a limpeza automática ANTES de a escrita começar.
        activityRef.get()?.serviceOrNull()?.onManualAutoCalIntent(action.name)
        val executed = actionManager.execute(prepared.getString("preparationId"))
            .put("operationalOneTouch", true)
            .put("requestedEnabled", enabled)
            .toString()
        invalidateAnalysis()
        return executed
    }

    /**
     * Executa após a revisão crítica dentro do OMEGAS. Os interlocks, ACK e
     * readback continuam no manager canônico.
     */
    @JavascriptInterface
    fun executeNativeAction(preparationId: String): String {
        val actionManager = currentNativeManager() ?: return unavailable()
        val preparedStatus = actionManager.statusJson()
        if (preparedStatus.optString("state") != "PREPARED" ||
            preparedStatus.optString("preparationId") != preparationId
        ) {
            return localFailure("A preparação não corresponde à ação revisada")
        }
        val actionName = preparedStatus.optString("action")
        val action = try {
            AutoCalNativeActionManager.Action.valueOf(actionName)
        } catch (_: Exception) {
            return localFailure("Ação nativa inválida")
        }
        if (action.operationalToggle) {
            actionManager.clearPreparation()
            return localFailure("Iniciar/Pausar não usa revisão crítica; use a ação operacional de um toque")
        }
        if (action !in setOf(
                AutoCalNativeActionManager.Action.RESET_PETROL,
                AutoCalNativeActionManager.Action.RESET_GAS,
                AutoCalNativeActionManager.Action.RESET_K_FACTOR,
                AutoCalNativeActionManager.Action.FINISH_AUTOCAL,
                AutoCalNativeActionManager.Action.FINISH_AUTOMATCH,
                AutoCalNativeActionManager.Action.DELETE_POINT,
            )
        ) {
            actionManager.clearPreparation()
            return localFailure("Ação destrutiva não suportada")
        }

        // Mesma regra de qualquer escrita K: sem o controle principal do MP48 (Link), nenhuma ação sai deste aparelho.
        noLocalControlFailure()?.let { return it }
        // Intenção manual do dono: desarma a limpeza automática ANTES de a escrita começar.
        // Exceção (regra 17): apagar ponto manualmente nunca desarma nem pausa a limpeza automática.
        if (action != AutoCalNativeActionManager.Action.DELETE_POINT) {
            activityRef.get()?.serviceOrNull()?.onManualAutoCalIntent(actionName)
        }
        val result = actionManager.execute(preparationId)
        invalidateAnalysis()
        if (!result.optBoolean("ok", false)) actionManager.clearPreparation()
        return result
            .put("confirmationPending", false)
            .put("nativeAndroidConfirmation", false)
            .put("writesStarted", result.optBoolean("ok", false))
            .put("automatic", false)
            .put("manualOnly", true)
            .toString()
    }

    private fun noLocalControlFailure(): String? =
        if (activityRef.get()?.serviceOrNull()?.canWriteLocally() == false) {
            localFailure("Este aparelho não possui o controle principal do MP48")
        } else null

    @JavascriptInterface
    fun clearNativeActionPreparation(): String =
        currentNativeManager()?.clearPreparation()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getIdentity(): String = JSONObject()
        .put("feature", "Auto Calibration nativa — V8.2")
        .put("nativeFirmwareExact", false)
        .put("nativeProtocolEvidenceExact", true)
        .put("readOnly", false)
        .put("readOnlyScope", "STATUS_AND_SNAPSHOT_ONLY")
        .put("localDraft", false)
        .put("nativeActionsManual", true)
        .put("nativeActionsMutateEcu", true)
        .put("nativeAndroidConfirmation", false)
        // Única escrita automática do app: apagar pontos fora da curva do GNV e da gasolina (spec 2026-10-07 rev2).
        .put("appAutomaticWrite", activityRef.get()?.serviceOrNull()?.autoIdleCleanup?.automaticEnabled() == true)
        .put("appAutomaticWriteScope", "DELETE_OUTLIER_POINTS")
        .put("nativeAutoMatchInsideEcu", true)
        .put("manualAutoMatchExposed", false)
        .put("obdIndependent", true)
        .toString()

    /**
     * Equivalência Refinada (o "cérebro" do refino) sobre o snapshot nativo mais recente.
     * Pronta em segundo plano; recalcula só quando o snapshot ou a evidência mudam.
     */
    @JavascriptInterface
    fun getRefinedAnalysis(): String = refinedAnalysisMemo.get()

    private fun computeRefinedAnalysis(): String = try {
        val snapshot = refinementSnapshot()
        val evidence = refinementEvidence(snapshot)
        val key = snapshot.optString("snapshotHash") + "|" + snapshot.optLong("capturedAtMs", 0L) + "|" + evidence.signature
        // ECU#2: o cálculo pesado roda FORA do managerLock (o mesmo de getStatus/getSnapshot do JavaScript);
        // o lock fica só no memo, senão a troca de zona travava a tela enquanto a análise rodava.
        refinedMemo?.takeIf { it.first == key }?.second
            ?: AutoMatchSnapshotAnalysis.analyzeRefined(
                snapshot, evidence.pairs, evidence.gainScale, evidence.episodes, AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG, evidence.fineBins,
            )
                .toString().also { value -> synchronized(refinedMemoLock) { refinedMemo = key to value } }
    } catch (error: Exception) {
        localFailure(error.message ?: "Equivalência refinada indisponível")
    }

    /** Pontos próprios + diário + piloto, para a aba Refino. Só leitura; resposta pronta em segundo plano. */
    @JavascriptInterface
    fun getEquivalence(): String = equivalenceMemo.get()

    /** Igual a [getEquivalence], mas descarta o valor guardado (depois de gravar, desfazer, restaurar). */
    @JavascriptInterface
    fun getEquivalenceFresh(): String {
        invalidateAnalysis()
        return equivalenceMemo.getFresh()
    }

    private fun invalidateAnalysis() {
        projectionMemo.invalidate()
        equivalenceMemo.invalidate()
        refinedAnalysisMemo.invalidate()
    }

    private fun computeEquivalence(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        // O resultado do cérebro único já foi calculado no tique do serviço; aqui só se lê (sem cálculo na thread da WebView).
        val brain = try { JSONObject(service.equivalenceResultJson()).takeIf { it.optBoolean("ok", false) } } catch (_: Exception) { null }
        EquivalenceView.build(service.equivalence, service.refinementJournal, service.equivalencePhases, service.stallWatch, brain).toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Equivalência indisponível")
    }

    /** Resultado completo do cérebro único (índice, próxima ação, pontos, Curvas Próprias, proposta). Só leitura. */
    @JavascriptInterface
    fun getEquivalenceResult(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.equivalenceResultJson()
    } catch (error: Exception) {
        localFailure(error.message ?: "Resultado do cérebro indisponível")
    }

    /**
     * Toque do dono: congela a gasolina madura da ECU como Referência. Não grava na ECU; o Desfazer é
     * [restorePreviousReference]. Provisório até a fila de operações (F5) assumir este verbo.
     */
    @JavascriptInterface
    fun freezeReference(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.freezeReference().also { invalidateAnalysis() }
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível congelar a referência")
    }

    /** Toque explícito: reinicia apenas a evidência local do GNV, nunca envia comando à ECU. */
    @JavascriptInterface
    fun resetGasEvidence(): String = try {
        noLocalControlFailure() ?: run {
            val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
            service.resetGasEvidence().also { invalidateAnalysis() }
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível reiniciar o aprendizado do GNV")
    }

    /** Desfazer do congelamento: volta à Referência anterior desta sessão. */
    @JavascriptInterface
    fun restorePreviousReference(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.restorePreviousReference().also { invalidateAnalysis() }
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível restaurar a referência")
    }

    /** Só a fase do piloto (Agora e Sugestões a cada 2–3 s): não recalcula pares nem bandas. */
    @JavascriptInterface
    fun getRefinementPhase(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        JSONObject().put("ok", true).put("autopilot", service.equivalencePhases.json()).toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Refino indisponível")
    }

    private class Evidence(
        val pairs: List<Pair<Double, Double>>,
        val gainScale: DoubleArray?,
        val signature: String,
        val episodes: List<Int> = emptyList(),
        /** Lote H: bins finos; só preenchido quando [AutoMatchRefinedEngine.FINE_BINS_ENABLED]. */
        val fineBins: List<FineBins.Bin>? = null,
    )

    @Volatile private var refinedMemo: Pair<String, String>? = null
    private val refinedMemoLock = Any()

    /** Telemetria da curva vigente + ganho aprendido; alinha o acumulador à MUL_ACT lida da ECU. */
    private fun refinementEvidence(snapshot: JSONObject): Evidence {
        val service = activityRef.get()?.serviceOrNull() ?: return Evidence(emptyList(), null, "sem-servico")
        val fields = snapshot.optJSONArray("fields")
        var mulAct: JSONArray? = null
        var axisMs: List<Double>? = null
        if (fields != null) for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            val raw = field.optJSONArray("rawValues") ?: continue
            if (field.optString("status") != AutoCalFieldStatus.VALID.name || raw.length() != 30) continue
            when (field.optString("key")) {
                "MUL_ACT" -> mulAct = raw
                "PETR_INJ_TBP" -> axisMs = (0 until 30).map { raw.optInt(it) / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS }
            }
        }
        mulAct?.let { raw -> service.equivalenceRuntime.alignCurve(service.equivalence, service.equivalencePhases, IntArray(30) { raw.optInt(it) }) }
        // Só condução: a marcha lenta (~870 rpm) tem estratégia própria da ECU e criava degrau em ~4,5 ms.
        val driving = service.equivalence.drivingPairs()
        val pairs = driving.map { it.petrolRefMs to it.gasPetrolMs }
        val episodes = driving.map { it.episode }
        val scale = axisMs?.let { service.refinementJournal.pointGainScale(it) }
        val fineBins = if (AutoMatchRefinedEngine.FINE_BINS_ENABLED) service.equivalence.fineBins() else null
        return Evidence(pairs, scale, "${service.equivalence.revision()}|${service.equivalence.gasEpochToken()}|${scale?.joinToString(",") { "%.3f".format(it) }}|${fineBins != null}", episodes, fineBins)
    }

    /** Snapshot mais recente entre o monitor nativo e a leitura manual. */
    private fun refinementSnapshot(): JSONObject {
        val monitor = activityRef.get()?.serviceOrNull()?.let { service ->
            try { JSONObject(service.nativeAutoCalSnapshotJson()) } catch (_: Exception) { null }
        }
        val manual = currentManager()?.latestSnapshotJson()
        val monitorAt = monitor?.takeIf { (it.optJSONArray("fields")?.length() ?: 0) > 0 }?.optLong("capturedAtMs", 0L) ?: -1L
        val manualAt = manual?.takeIf { (it.optJSONArray("fields")?.length() ?: 0) > 0 }?.optLong("capturedAtMs", 0L) ?: -1L
        return when {
            monitorAt < 0 && manualAt < 0 -> throw IllegalStateException("Nenhuma leitura AutoCal da ECU ainda")
            monitorAt >= manualAt -> monitor!!
            else -> manual!!
        }
    }

    fun destroy() {
        warmer.shutdownNow()
        synchronized(managerLock) {
            // As ações nativas são do serviço (o automático segue com a tela fechada). Só se esta ponte ainda for
            // a registrada: a Activity nova pode já ter assumido, e o destroy da antiga não pode desarmá-la.
            managerService?.let { service ->
                if (service.releaseAutoCalUi(this)) {
                    try { service.nativeActions.clearPreparation() } catch (_: Exception) {}
                }
            }
            manager?.close()
            manager = null
            managerService = null
        }
    }

    private fun currentManager(): AutoCalSnapshotManager? {
        val activity = activityRef.get() ?: return null
        val service = activity.serviceOrNull() ?: return null
        synchronized(managerLock) {
            bindService(service)
            if (manager == null) {
                val serial = service.runtime.serialScheduler()
                manager = AutoCalSnapshotManager(
                    isConnected = serial::isConnected,
                    currentSessionId = serial::currentSessionId,
                    otherCalibrationBusy = {
                        service.kWriter.isBusy() || service.kFactor.isBusy() || service.nativeActions.isBusy()
                    },
                    transaction = { request, reason, timeoutMs, expectedSessionId ->
                        serial.transaction(
                            request = request,
                            reason = reason,
                            timeoutMs = timeoutMs,
                            purgeBefore = true,
                            expectedSessionId = expectedSessionId,
                            workClass = Mp48WorkClass.READ_ONLY,
                        )
                    },
                    onStateChanged = activity::refreshWebUi,
                    onSnapshotReady = { snapshot ->
                        service.sessionRecorder.record("autocal_manual_snapshot", "autocal", snapshot, force = true)
                    },
                )
            }
            manager?.onUsbSessionChanged(service.usb.connectionSessionId)
            return manager
        }
    }

    /**
     * O manager de ações nativas é do serviço (dono: [TelemetryForegroundService.nativeActions]). Recibos na
     * sessão, invalidação do round e a política de segurança de escrita ficam na fiação do serviço.
     */
    private fun currentNativeManager(): AutoCalNativeActionManager? {
        val activity = activityRef.get() ?: return null
        val service = activity.serviceOrNull() ?: return null
        synchronized(managerLock) { bindService(service) }
        return service.nativeActions
    }

    private fun bindService(service: TelemetryForegroundService) {
        if (managerService !== service) {
            manager?.close()
            manager = null
            managerService = service
        }
        if (!service.isAutoCalUiOwner(this)) {
            service.bindAutoCalUi(
                owner = this,
                refreshUi = { activityRef.get()?.refreshWebUi() },
                manualReadBusy = { manager?.isBusy() == true },
            )
        }
    }

    private fun localFailure(message: String): String = JSONObject()
        .put("ok", false)
        .put("error", message)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("requiresReview", true)
        .toString()

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço indisponível")
        .put("automatic", false)
        .put("manualOnly", true)
        .toString()
}