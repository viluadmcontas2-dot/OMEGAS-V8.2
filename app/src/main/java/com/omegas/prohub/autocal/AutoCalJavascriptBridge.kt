package com.omegas.prohub.autocal

import android.app.AlertDialog
import android.os.Build
import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.service.TelemetryForegroundService
import org.json.JSONObject
import java.io.File

/**
 * Bridge modular do AutoMatch OMEGAS.
 *
 * Reconstrução, análise e rascunho permanecem somente leitura. As ações nativas
 * ficam numa superfície separada, sempre preparadas e confirmadas pelo operador
 * também em diálogo Android nativo, além da revisão crítica da WebView.
 */
class AutoCalJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val managerLock = Any()
    private var managerService: TelemetryForegroundService? = null
    private var manager: AutoCalSnapshotManager? = null
    private var nativeActions: AutoCalNativeActionManager? = null
    private var draft: AutoMatchKFactorDraft? = null
    private var nativeConfirmationPendingId: String? = null

    @JavascriptInterface
    fun getStatus(): String = currentManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getSnapshot(): String = currentManager()?.latestSnapshotJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun importSnapshotIntoLearning(snapshotJson: String): String = try {
        val activity = activityRef.get() ?: throw IllegalStateException("Tela indisponível")
        val service = activity.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        service.importNativeAutoCalSnapshot(snapshotJson)
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível importar o snapshot")
    }

    @JavascriptInterface
    fun getAnalysis(): String = currentManager()?.let { active ->
        AutoMatchSnapshotAnalysis.analyze(active.latestSnapshotJson()).toString()
    } ?: unavailable()

    @JavascriptInterface
    fun getResidualAnalysis(): String = try {
        val activity = activityRef.get() ?: throw IllegalStateException("Tela indisponível")
        val service = activity.serviceOrNull() ?: throw IllegalStateException("Serviço indisponível")
        val active = currentManager() ?: throw IllegalStateException("Leitura AutoCal indisponível")
        val analysis = AutoMatchSnapshotAnalysis.analyze(active.latestSnapshotJson())
        val learning = service.runtime.exportLearning(service.settings.deviceId)
        AutoMatchResidualPlanner.analyze(analysis, learning).toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Residual indisponível")
    }

    @JavascriptInterface
    fun startRead(): String {
        synchronized(managerLock) { draft = null }
        return currentManager()?.startRead()?.toString() ?: unavailable()
    }

    @JavascriptInterface
    fun cancelRead(): String = currentManager()?.cancel()?.toString() ?: unavailable()

    @JavascriptInterface
    fun createDraft(): String = try {
        val active = currentManager() ?: throw IllegalStateException("Serviço indisponível")
        val analysis = AutoMatchSnapshotAnalysis.analyze(active.latestSnapshotJson())
        val created = AutoMatchKFactorDraftPlanner.create(analysis)
        synchronized(managerLock) { draft = created }
        created.toJson().toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Não foi possível criar o rascunho")
    }

    @JavascriptInterface
    fun getDraft(): String = synchronized(managerLock) {
        val current = draft
        if (current == null) emptyDraft().toString() else current.toJson().toString()
    }

    @JavascriptInterface
    fun selectDraftPoint(index: Int, selected: Boolean): String = try {
        synchronized(managerLock) {
            val current = draft ?: throw IllegalStateException("Crie um rascunho local primeiro")
            AutoMatchKFactorDraftPlanner.select(current, index, selected)
                .also { draft = it }
                .toJson()
                .toString()
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Ponto inválido")
    }

    @JavascriptInterface
    fun setDraftTargetFactor(index: Int, factor: Double): String = try {
        synchronized(managerLock) {
            val current = draft ?: throw IllegalStateException("Crie um rascunho local primeiro")
            AutoMatchKFactorDraftPlanner.setTargetFactor(current, index, factor)
                .also { draft = it }
                .toJson()
                .toString()
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Fator inválido")
    }

    @JavascriptInterface
    fun getDraftReviewPayload(): String = try {
        synchronized(managerLock) {
            (draft ?: throw IllegalStateException("Crie um rascunho local primeiro"))
                .selectedPointsForReview()
                .toString()
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "Rascunho indisponível")
    }

    @JavascriptInterface
    fun validateDraftReviewCurve(curveJson: String): String = try {
        synchronized(managerLock) {
            val current = draft ?: throw IllegalStateException("Crie um rascunho local primeiro")
            AutoMatchDraftReviewValidator.validate(current, JSONObject(curveJson)).toString()
        }
    } catch (error: Exception) {
        localFailure(error.message ?: "A Curva K não confirmou o rascunho")
    }

    @JavascriptInterface
    fun clearDraft(): String = synchronized(managerLock) {
        draft = null
        emptyDraft().put("cleared", true).toString()
    }

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
     * Não executa a ação diretamente. Agenda um AlertDialog Android não
     * cancelável por toque externo; somente o botão positivo chama o manager.
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
                    "A ECU modificará buffers de aquisição AutoCal."
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
        .put("feature", "AutoMatch — Reconstrução V8")
        .put("nativeFirmwareExact", false)
        .put("readOnly", true)
        .put("readOnlyScope", "RECONSTRUCTION_ANALYSIS_AND_DRAFT")
        .put("localDraft", true)
        .put("nativeActionsManual", true)
        .put("nativeActionsMutateEcu", true)
        .put("nativeAndroidConfirmation", true)
        .put("automatic", false)
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
            draft = null
            nativeConfirmationPendingId = null
        }
    }

    private fun currentManager(): AutoCalSnapshotManager? {
        val activity = activityRef.get() ?: return null
        val service = activity.serviceOrNull() ?: return null
        synchronized(managerLock) {
            bindService(service)
            if (manager == null) {
                manager = AutoCalSnapshotManager(
                    isConnected = { service.usb.connected },
                    currentSessionId = { service.usb.connectionSessionId },
                    otherCalibrationBusy = {
                        service.kWriter.isBusy() || service.kFactor.isBusy() || nativeActions?.isBusy() == true
                    },
                    transaction = { request, reason, timeoutMs, expectedSessionId ->
                        service.usb.protocolTransaction(
                            request = request,
                            reason = reason,
                            timeoutMs = timeoutMs,
                            purgeBefore = true,
                            expectedSessionId = expectedSessionId,
                        )
                    },
                    onStateChanged = activity::refreshWebUi,
                    onSnapshotReady = { snapshot ->
                        service.runtime.importNativeAutoCalSnapshot(snapshot)
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
                nativeActions = AutoCalNativeActionManager(
                    receiptFile = File(service.paths.runtimeRoot, "autocal_native_receipts.json"),
                    isConnected = { service.usb.connected },
                    currentSessionId = { service.usb.connectionSessionId },
                    otherCalibrationBusy = {
                        service.kWriter.isBusy() || service.kFactor.isBusy() || manager?.isBusy() == true
                    },
                    unsafeMutationReason = {
                        CalibrationWriteSafetyPolicy.unsafeReason(service.status())
                    },
                    transaction = { request, reason, timeoutMs, expectedSessionId ->
                        service.usb.protocolTransaction(
                            request = request,
                            reason = reason,
                            timeoutMs = timeoutMs,
                            purgeBefore = true,
                            expectedSessionId = expectedSessionId,
                        )
                    },
                    onConfirmed = { receipt ->
                        synchronized(managerLock) { draft = null }
                        service.sessionRecorder.record("autocal_native_action", "autocal", receipt, force = true)
                        if (receipt.optString("action") == AutoCalNativeActionManager.Action.NATIVE_AUTOMATCH.name) {
                            service.kFactor.beginUsbSession(service.usb.connectionSessionId)
                            service.runtime.notifyCalibrationAdjustment(receipt)
                            service.learningArchive.saveInternalCheckpoint("Após AutoMatch nativo confirmado")
                        }
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
            draft = null
            nativeConfirmationPendingId = null
            managerService = service
            return
        }
    }

    private fun emptyDraft(): JSONObject = JSONObject()
        .put("ok", true)
        .put("available", false)
        .put("selectedCount", 0)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("requiresReview", true)

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