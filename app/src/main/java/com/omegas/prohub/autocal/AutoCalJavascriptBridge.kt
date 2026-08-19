package com.omegas.prohub.autocal

import android.app.AlertDialog
import android.os.Build
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
    private var projectedSnapshotHash = ""
    private var cachedHumanProjection: JSONObject? = null
    private var humanProjectionRecomputeCount = 0L

    @JavascriptInterface
    fun getStatus(): String = currentManager()?.statusJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getSnapshot(): String = currentManager()?.latestSnapshotJson()?.toString() ?: unavailable()

    @JavascriptInterface
    fun getNativeMonitorStatus(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: return unavailable()
        val monitor = JSONObject(service.nativeAutoCalStatusJson())
        monitor.put(
            "stationaryCalibration",
            StationaryCalibrationProjection.project(
                monitorStatus = monitor,
                frame = service.runtime.currentTelemetryFrame(),
            ),
        ).toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Estado AutoCal nativo indisponível")
    }

    @JavascriptInterface
    fun getNativeMonitorSnapshot(): String = try {
        val service = activityRef.get()?.serviceOrNull() ?: return unavailable()
        val snapshot = JSONObject(service.nativeAutoCalSnapshotJson())
        if (!snapshot.optBoolean("available", false)) return snapshot.toString()
        val hash = snapshot.optString("snapshotHash")
        val projection = synchronized(managerLock) {
            if (hash.isBlank() || hash != projectedSnapshotHash || cachedHumanProjection == null) {
                cachedHumanProjection = NativeAutoCalSnapshotHumanProjector.project(
                    snapshot = snapshot,
                    autoMatchRevalidating = snapshot.optString("snapshotReason") == "AUTOMATCH_COUNT_CHANGED",
                )
                projectedSnapshotHash = hash
                humanProjectionRecomputeCount += 1L
            }
            JSONObject(requireNotNull(cachedHumanProjection).toString())
        }
        snapshot
            .put("humanProjection", projection)
            .put("humanProjectionSnapshotHash", hash)
            .put("humanProjectionRecomputeCount", humanProjectionRecomputeCount)
            .toString()
    } catch (error: Exception) {
        localFailure(error.message ?: "Projeção AutoCal indisponível")
    }

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
        localFailure(error.message ?: "Ponto inválido")
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
        .put("feature", "Auto Calibration nativa — V8.2")
        .put("nativeFirmwareExact", false)
        .put("nativeProtocolEvidenceExact", true)
        .put("readOnly", false)
        .put("readOnlyScope", "STATUS_AND_SNAPSHOT_ONLY")
        .put("localDraft", true)
        .put("nativeActionsManual", true)
        .put("nativeActionsMutateEcu", true)
        .put("nativeAndroidConfirmation", true)
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
            draft = null
            nativeConfirmationPendingId = null
            projectedSnapshotHash = ""
            cachedHumanProjection = null
            humanProjectionRecomputeCount = 0L
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
                        synchronized(managerLock) { draft = null }
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
            draft = null
            nativeConfirmationPendingId = null
            projectedSnapshotHash = ""
            cachedHumanProjection = null
            humanProjectionRecomputeCount = 0L
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
