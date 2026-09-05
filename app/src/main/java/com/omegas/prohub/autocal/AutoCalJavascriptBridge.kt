package com.omegas.prohub.autocal

import android.app.AlertDialog
import android.os.Build
import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.service.TelemetryForegroundService
import com.omegas.prohub.service.blueIngestLearningSnapshot
import com.omegas.prohub.service.blueProposalJson
import org.json.JSONObject
import java.io.File

/**
 * Auto-Cal do OMEGAS Blue.
 *
 * Esta bridge não calcula alvo K, residual ou confiança. Toda matemática de
 * equivalência e correção pertence ao BlueCausalEngine. Aqui permanecem somente
 * aquisição nativa, status e transação manual confirmada com ACK e readback.
 */
class AutoCalJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val managerLock = Any()
    private var managerService: TelemetryForegroundService? = null
    private var manager: AutoCalSnapshotManager? = null
    private var nativeActions: AutoCalNativeActionManager? = null
    private var nativeConfirmationPendingId: String? = null

    @JavascriptInterface
    fun getStatus(): String = currentManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getSnapshot(): String = currentManager()?.latestSnapshotJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorStatus(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalStatusJson() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorSnapshot(): String = activityRef.get()?.serviceOrNull()?.nativeAutoCalSnapshotJson() ?: unavailable()

    @JavascriptInterface
    fun importSnapshotIntoLearning(snapshotJson: String): String = try {
        val activity = activityRef.get() ?: throw IllegalStateException("Tela indisponível")
        val service = activity.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.importNativeAutoCalSnapshot(snapshotJson).also {
            service.blueIngestLearningSnapshot(snapshotJson)
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível importar o snapshot")
    }

    @JavascriptInterface
    fun getAnalysis(): String = activityRef.get()?.serviceOrNull()?.blueProposalJson() ?: unavailable()

    @JavascriptInterface
    fun getResidualAnalysis(): String = getAnalysis()

    @JavascriptInterface
    fun startRead(): String = currentManager()?.startRead()?.toString() ?: unavailable()

    @JavascriptInterface
    fun cancelRead(): String = currentManager()?.cancel()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeActionStatus(): String = currentNativeManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeActionReceipts(): String = currentNativeManager()?.receiptsJson()?.toString() ?: "[]"

    @JavascriptInterface
    fun prepareNativeAction(action: String): String = currentNativeManager()
        ?.prepare(action)
        ?.toString()
        ?: unavailable()

    /**
     * Não executa a ação diretamente. Abre confirmação Android explícita; apenas
     * o botão positivo chama o manager. Confirmação, ACK e readback permanecem
     * obrigatórios para qualquer alteração da ECU.
     */
    @JavascriptInterface
    fun executeNativeAction(preparationId: String): String {
        val activity = activityRef.get() ?: return unavailable()
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
        synchronized(managerLock) {
            if (nativeConfirmationPendingId != null) {
                return localFailure("Já existe uma confirmação Android aberta")
            }
            nativeConfirmationPendingId = preparationId
        }
        return try {
            activity.runOnUiThread {
                if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
                    synchronized(managerLock) { nativeConfirmationPendingId = null }
                    actionManager.clearPreparation()
                    return@runOnUiThread
                }
                val commandHex = action.request.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                val effect = if (action.mayChangeMulAct) {
                    "A ECU pode alterar MUL_ACT internamente."
                } else {
                    "A ECU modificará buffers de aquisição nativa."
                }
                AlertDialog.Builder(activity)
                    .setTitle("CONFIRMAÇÃO ANDROID — ECU")
                    .setMessage(
                        "${action.label}\n\n$effect\n\nComando: $commandHex\n\n" +
                            "Esta ação nunca é automática e não possui rollback automático.",
                    )
                    .setCancelable(false)
                    .setNegativeButton("CANCELAR") { dialog, _ ->
                        synchronized(managerLock) { nativeConfirmationPendingId = null }
                        actionManager.clearPreparation()
                        activity.refreshWebUi()
                        dialog.dismiss()
                    }
                    .setPositiveButton("ENVIAR COMANDO") { dialog, _ ->
                        synchronized(managerLock) { nativeConfirmationPendingId = null }
                        val result = actionManager.execute(preparationId)
                        if (!result.optBoolean("ok")) actionManager.clearPreparation()
                        activity.refreshWebUi()
                        dialog.dismiss()
                    }
                    .show()
            }
            JSONObject()
                .put("ok", true)
                .put("confirmationPending", true)
                .put("nativeAndroidConfirmation", true)
                .put("writesStarted", false)
                .put("automatic", false)
                .put("manualOnly", true)
                .toString()
        } catch (error: Exception) {
            synchronized(managerLock) { nativeConfirmationPendingId = null }
            actionManager.clearPreparation()
            localFailure(error.message ?: "Não foi possível abrir a confirmação Android")
        }
    }

    @JavascriptInterface
    fun clearNativeActionPreparation(): String {
        synchronized(managerLock) { nativeConfirmationPendingId = null }
        return currentNativeManager()?.clearPreparation()?.toString() ?: unavailable()
    }

    @JavascriptInterface
    fun getIdentity(): String = JSONObject()
        .put("feature", "OMEGAS Blue Auto-Cal")
        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
        .put("parallelCorrectionMath", false)
        .put("nativeProtocolEvidenceExact", true)
        .put("nativeActionsManual", true)
        .put("nativeActionsMutateEcu", true)
        .put("nativeAndroidConfirmation", true)
        .put("appAutomaticWrite", false)
        .put("manualOnly", true)
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
            nativeConfirmationPendingId = null
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
                        service.blueIngestLearningSnapshot(snapshot.toString())
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
                    unsafeMutationReason = { CalibrationWriteSafetyPolicy.unsafeReason(service.status()) },
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
            nativeConfirmationPendingId = null
            managerService = service
        }
    }

    private fun localFailure(message: String): String = JSONObject()
        .put("ok", false)
        .put("error", message)
        .put("automatic", false)
        .put("manualOnly", true)
        .toString()

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço indisponível")
        .put("automatic", false)
        .put("manualOnly", true)
        .toString()
}
