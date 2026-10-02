package com.omegas.prohub.autocal

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.service.TelemetryForegroundService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bridge de paridade host-side AutoCal com o ProgBase.
 *
 * A ECU continua sendo a autoridade do AutoMatch automático. O bridge apenas
 * reproduz operações host-side comprovadas do ProgBase; inteligência adicional
 * do OMEGAS não substitui comandos/estados nativos. Projeção, leitura manual e monitor nativo permanecem separados. As ações nativas
 * ficam numa superfície separada: são preparadas, revisadas no OMEGAS e então
 * executadas diretamente pelo manager canônico com ACK/readback.
 */
class AutoCalJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val managerLock = Any()
    private var managerService: TelemetryForegroundService? = null
    private var manager: AutoCalSnapshotManager? = null
    private var nativeActions: AutoCalNativeActionManager? = null

    @JavascriptInterface
    fun getStatus(): String = currentManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getSnapshot(): String = currentManager()?.latestSnapshotJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorStatus(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalStatusJson() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorSnapshot(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalSnapshotJson() ?: unavailable()

    @JavascriptInterface
    fun getUiProjection(): String = try {
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
    fun listAutoCalSessions(): String = activityRef.get()?.serviceOrNull()?.sessionRecorderListJson() ?: "[]"

    @JavascriptInterface
    fun exportAutoCalSession(sessionId: String) {
        activityRef.get()?.exportSession(sessionId)
    }

    @JavascriptInterface
    fun startRead(): String = currentManager()?.startRead()?.toString() ?: unavailable()

    @JavascriptInterface
    fun cancelRead(): String = currentManager()?.cancel()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeActionStatus(): String =
        currentNativeManager()?.statusJson()?.toString() ?: unavailable()

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
                AutoCalNativeActionManager.Action.RESET_ALL,
                AutoCalNativeActionManager.Action.RESET_K_FACTOR,
                AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH,
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
        return actionManager.execute(prepared.getString("preparationId"))
            .put("operationalOneTouch", true)
            .put("requestedEnabled", enabled)
            .toString()
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
                AutoCalNativeActionManager.Action.RESET_ALL,
                AutoCalNativeActionManager.Action.RESET_K_FACTOR,
                AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH,
                AutoCalNativeActionManager.Action.FINISH_AUTOCAL,
                AutoCalNativeActionManager.Action.FINISH_AUTOMATCH,
                AutoCalNativeActionManager.Action.DELETE_POINT,
            )
        ) {
            actionManager.clearPreparation()
            return localFailure("Ação destrutiva não suportada")
        }

        val result = actionManager.execute(preparationId)
        if (!result.optBoolean("ok", false)) actionManager.clearPreparation()
        return result
            .put("confirmationPending", false)
            .put("nativeAndroidConfirmation", false)
            .put("writesStarted", result.optBoolean("ok", false))
            .put("automatic", false)
            .put("manualOnly", true)
            .toString()
    }

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
        .put("appAutomaticWrite", false)
        .put("nativeAutoMatchInsideEcu", true)
        .put("manualAutoMatchExposed", true)
        .put("obdIndependent", true)
        .toString()

    /**
     * Equivalência Refinada (o "cérebro" do refino) sobre o snapshot nativo mais recente.
     * Memoizada por snapshot + evidência: a tela consulta periodicamente sem recalcular.
     */
    @JavascriptInterface
    fun getRefinedAnalysis(): String = try {
        val snapshot = refinementSnapshot()
        val evidence = refinementEvidence(snapshot)
        val key = snapshot.optString("snapshotHash") + "|" + snapshot.optLong("capturedAtMs", 0L) + "|" + evidence.signature
        synchronized(managerLock) {
            refinedMemo?.takeIf { it.first == key }?.second
                ?: AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, evidence.pairs, evidence.gainScale)
                    .toString().also { refinedMemo = key to it }
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Equivalência refinada indisponível")
    }

    /** Pontos próprios + diário + piloto, para a aba Refino. Só leitura. */
    @JavascriptInterface
    fun getEquivalence(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.equivalence.index()
            .put("denseBands", service.equivalence.denseBandsJson())
            .put("typicalBands", service.equivalence.typicalBandsJson())
            .put("refinement", service.refinementJournal.json())
            .put("restorePoints", service.refinementJournal.restorePoints())
            .put("autopilot", service.refinementAutopilot.json())
            .put("stalls", service.stallWatch.json())
            .toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Equivalência indisponível")
    }

    private class Evidence(val pairs: List<Pair<Double, Double>>, val gainScale: DoubleArray?, val signature: String)

    @Volatile private var refinedMemo: Pair<String, String>? = null

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
        mulAct?.let { raw -> service.equivalence.alignCurve(EquivalenceLedger.fingerprint(IntArray(30) { raw.optInt(it) })) }
        val pairs = service.equivalence.pairs().map { it.petrolRefMs to it.gasPetrolMs }
        val scale = axisMs?.let { service.refinementJournal.pointGainScale(it) }
        return Evidence(pairs, scale, "${pairs.size}|${service.equivalence.gasEpochToken()}|${scale?.joinToString(",") { "%.3f".format(it) }}")
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
        synchronized(managerLock) {
            manager?.close()
            nativeActions?.clearPreparation()
            nativeActions?.close()
            manager = null
            nativeActions = null
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
                        service.kWriter.isBusy() || service.kFactor.isBusy() || nativeActions?.isBusy() == true
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
                        service.runtime.importNativeAutoCalSnapshot(snapshot)
                        service.sessionRecorder.record("autocal_manual_snapshot", "autocal", snapshot, force = true)
                    },
                )
            }
            manager?.onUsbSessionChanged(service.usb.connectionSessionId)
            return manager
        }
    }

    private fun currentNativeManager(): AutoCalNativeActionManager? {
        val activity = activityRef.get() ?: return null
        val service = activity.serviceOrNull() ?: return null
        synchronized(managerLock) {
            bindService(service)
            if (nativeActions == null) {
                val serial = service.runtime.serialScheduler()
                nativeActions = AutoCalNativeActionManager(
                    receiptFile = File(service.paths.runtimeRoot, "autocal_native_receipts.json"),
                    isConnected = serial::isConnected,
                    currentSessionId = serial::currentSessionId,
                    otherCalibrationBusy = {
                        service.kWriter.isBusy() || service.kFactor.isBusy() || manager?.isBusy() == true
                    },
                    unsafeMutationReason = {
                        CalibrationWriteSafetyPolicy.unsafeReason(service.status())
                    },
                    transaction = { request, reason, timeoutMs, expectedSessionId ->
                        val workClass = when (request.firstOrNull()?.toInt()?.and(0xFF)) {
                            0x09, 0x29, 0x0A -> Mp48WorkClass.READ_ONLY
                            else -> Mp48WorkClass.MANUAL_WRITE
                        }
                        serial.transaction(
                            request = request,
                            reason = reason,
                            timeoutMs = timeoutMs,
                            purgeBefore = true,
                            expectedSessionId = expectedSessionId,
                            workClass = workClass,
                        )
                    },
                    onConfirmed = { receipt ->
                        service.sessionRecorder.record("autocal_native_action", "autocal", receipt, force = true)
                        service.nativeAutoCal.onManualActionConfirmed(receipt)
                        try { service.link.markDataChanged("ação AutoCal nativa confirmada") } catch (_: Exception) {}
                    },
                    onStateChanged = activity::refreshWebUi,
                )
            }
            return nativeActions
        }
    }

    private fun bindService(service: TelemetryForegroundService) {
        if (managerService !== service) {
            manager?.close()
            nativeActions?.clearPreparation()
            nativeActions?.close()
            manager = null
            nativeActions = null
            managerService = service
            return
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