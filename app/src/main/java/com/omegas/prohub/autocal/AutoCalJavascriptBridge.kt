package com.omegas.prohub.autocal

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.service.TelemetryForegroundService
import org.json.JSONObject
import java.io.File

/**
 * Bridge modular do AutoMatch OMEGAS.
 *
 * Projeção, leitura manual e monitor nativo permanecem separados. As ações nativas
 * ficam numa superfície separada: são preparadas, revisadas no OMEGAS e então
 * executadas diretamente pelo manager canônico com ACK/readback.
 */
class AutoCalJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val managerLock = Any()
    private var managerService: TelemetryForegroundService? = null
    private var manager: AutoCalSnapshotManager? = null
    private var nativeActions: AutoCalNativeActionManager? = null
    private var nativeConfirmationPendingId: String? = null
    private var kFactorResetPreparationId: String? = null
    private var kFactorResetPreparedAtMs: Long = 0L

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
    fun getNativeActionStatus(): String {
        val service = activityRef.get()?.serviceOrNull()
        if (service != null) {
            val kStatus = try { JSONObject(service.kFactor.statusJson()) } catch (_: Exception) { JSONObject() }
            if (kStatus.optBoolean("busy", false)) {
                return kStatus.put("action", "RESET_K_FACTOR").put("manualOnly", true).toString()
            }
        }
        return currentNativeManager()?.statusJson()?.toString() ?: unavailable()
    }

    @JavascriptInterface
    fun prepareNativeAction(action: String): String {
        val normalized = action.trim().uppercase()
        if (normalized == "RESET_K_FACTOR") {
            val activity = activityRef.get() ?: return unavailable()
            val service = activity.serviceOrNull() ?: return unavailable()
            if (service.kWriter.isBusy() || service.kFactor.isBusy() || currentNativeManager()?.isBusy() == true) {
                return localFailure("Outra operação de calibração está em andamento")
            }
            CalibrationWriteSafetyPolicy.unsafeReason(service.status())?.let { return localFailure(it) }
            val sessionId = service.runtime.serialScheduler().currentSessionId()
            if (sessionId <= 0L) return localFailure("Sessão USB inválida")
            val now = System.currentTimeMillis()
            val preparationId = "KRESET-" + now
            synchronized(managerLock) {
                if (kFactorResetPreparationId != null) {
                    return localFailure("Já existe uma ação crítica preparada")
                }
                kFactorResetPreparationId = preparationId
                kFactorResetPreparedAtMs = now
            }
            return JSONObject()
                .put("ok", true)
                .put("prepared", true)
                .put("preparationId", preparationId)
                .put("action", "RESET_K_FACTOR")
                .put("label", "Reset Curva K")
                .put("description", "ProgBase ActionResetKFactorExecute grava MUL_ACT[i] = 1.0 em toda a curva. O OMEGAS usa o writer existente com ACK e readback; backup é manual e opcional.")
                .put("commandHex", "MUL_ACT[0..29] = 1.0")
                .put("sessionId", sessionId)
                .put("ecuMutation", true)
                .put("mayChangeMulAct", true)
                .put("requiresCriticalConfirmation", true)
                .put("automatic", false)
                .put("manualOnly", true)
                .toString()
        }

        val parsed = try {
            AutoCalNativeActionManager.Action.valueOf(normalized)
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
     * Executa após a revisão crítica dentro do OMEGAS. Não abre confirmação Android
     * redundante; os interlocks, ACK e readback continuam no manager canônico.
     */
    @JavascriptInterface
    fun executeNativeAction(preparationId: String): String {
        val isKFactorReset = synchronized(managerLock) {
            val valid = preparationId == kFactorResetPreparationId &&
                kFactorResetPreparedAtMs > 0L &&
                System.currentTimeMillis() - kFactorResetPreparedAtMs <= CRITICAL_PREPARATION_TTL_MS
            if (!valid && preparationId == kFactorResetPreparationId) {
                kFactorResetPreparationId = null
                kFactorResetPreparedAtMs = 0L
            }
            valid
        }
        if (isKFactorReset) return executeKFactorReset(preparationId)

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
    fun clearNativeActionPreparation(): String {
        synchronized(managerLock) {
            kFactorResetPreparationId = null
            kFactorResetPreparedAtMs = 0L
        }
        return currentNativeManager()?.clearPreparation()?.toString() ?: unavailable()
    }

    private fun executeKFactorReset(preparationId: String): String {
        val activity = activityRef.get() ?: return unavailable()
        synchronized(managerLock) {
            if (preparationId != kFactorResetPreparationId) {
                return localFailure("A preparação não corresponde à ação revisada")
            }
            kFactorResetPreparationId = null
            kFactorResetPreparedAtMs = 0L
        }
        return try {
            activity.serviceOrNull()?.startKFactorReset() ?: return unavailable()
            activity.refreshWebUi()
            JSONObject()
                .put("ok", true)
                .put("action", "RESET_K_FACTOR")
                .put("confirmationPending", false)
                .put("nativeAndroidConfirmation", false)
                .put("writesStarted", true)
                .put("automatic", false)
                .put("manualOnly", true)
                .toString()
        } catch (error: Exception) {
            localFailure(error.message ?: "Não foi possível iniciar o reset da Curva K")
        }
    }

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
        .put("manualAutoMatchExposed", false)
        .put("obdIndependent", true)
        .toString()

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
            kFactorResetPreparationId = null
            kFactorResetPreparedAtMs = 0L
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

    companion object {
        private const val CRITICAL_PREPARATION_TTL_MS = 120_000L
    }

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço indisponível")
        .put("automatic", false)
        .put("manualOnly", true)
        .toString()
}