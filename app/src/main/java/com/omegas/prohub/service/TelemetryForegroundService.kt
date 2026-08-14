package com.omegas.prohub.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.calibration.KFactorManager
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.autocal.NativeAutoCalMonitor
import com.omegas.prohub.diagnostics.SessionRecorder
import com.omegas.prohub.ecu.NativeRuntimeManager
import com.omegas.prohub.gps.GpsTelemetryManager
import com.omegas.prohub.learning.LearningArchiveManager
import com.omegas.prohub.learning.LearningTemperatureSettings
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.link.OmegasLinkManager
import com.omegas.prohub.model.HubStatus
import com.omegas.prohub.network.LanPanelServer
import com.omegas.prohub.obd.ObdAssistManager
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.storage.DataArchiveManager
import com.omegas.prohub.telemetry.ConsumptionTracker
import com.omegas.prohub.telemetry.TelemetryStateStore
import com.omegas.prohub.usb.UsbSerialManager
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Serviço nativo único do OMEGAS Pro Hub.
 *
 * Não existe projeto ativo, engine editável, polling HTTP, rollback de script,
 * promoção de banco ou leitura automática do mapa K. USB, MP48, aprendizado,
 * sessões e calibração manual são coordenados diretamente no Android.
 */
class TelemetryForegroundService : Service() {
    companion object {
        const val ACTION_TOGGLE_ENGINE = "com.omegas.prohub.TOGGLE_ENGINE"
        const val ACTION_DISCONNECT_USB = "com.omegas.prohub.DISCONNECT_USB"
        const val ACTION_RESTART_ENGINE = "com.omegas.prohub.RESTART_ENGINE"
        const val ACTION_STOP_SERVICE = "com.omegas.prohub.STOP_SERVICE"
    }

    inner class LocalBinder : Binder() {
        fun service(): TelemetryForegroundService = this@TelemetryForegroundService
    }

    private val binder = LocalBinder()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "omegas-native-service").apply { isDaemon = true }
    }

    lateinit var paths: AppPaths
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var log: RingLog
        private set
    lateinit var archives: DataArchiveManager
        private set
    lateinit var usb: UsbSerialManager
        private set
    lateinit var runtime: NativeRuntimeManager
        private set
    lateinit var telemetryStore: TelemetryStateStore
        private set
    lateinit var consumptionTracker: ConsumptionTracker
        private set
    lateinit var sessionRecorder: SessionRecorder
        private set
    lateinit var gps: GpsTelemetryManager
        private set
    lateinit var lanServer: LanPanelServer
        private set
    lateinit var kWriter: KWriteManager
        private set
    lateinit var kFactor: KFactorManager
        private set
    lateinit var nativeAutoCal: NativeAutoCalMonitor
        private set
    var obd: ObdAssistManager? = null
        private set
    lateinit var link: OmegasLinkManager
        private set
    lateinit var learningArchive: LearningArchiveManager
        private set
    lateinit var overlay: TelemetryOverlayController
        private set
    private lateinit var learningTemperature: LearningTemperatureSettings
    private lateinit var learningTolerances: LearningToleranceSettings

    private lateinit var notifications: NotificationController
    private var healthTask: ScheduledFuture<*>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val startedAt = System.currentTimeMillis()
    private var lastNotificationAt = 0L
    private var lastUsbConnected = false
    private var enginePausedByUser = false
    /**
     * Diferencia uma desconexão física/serial de uma parada solicitada pelo usuário.
     * Enquanto o usuário não pedir para parar, o serviço permanece vivo para
     * observar a volta do adaptador USB mesmo com a tela apagada.
     */
    private var monitoringPausedByUser = false
    private var engineRestarts = 0
    private var healthFailures = 0
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        paths = AppPaths(this)
        settings = AppSettings(this)
        learningTemperature = LearningTemperatureSettings(this)
        learningTolerances = LearningToleranceSettings(this)
        log = RingLog(1_500, paths.logFile)
        archives = DataArchiveManager(paths, log)
        telemetryStore = TelemetryStateStore()
        consumptionTracker = ConsumptionTracker(this)
        sessionRecorder = SessionRecorder(paths, settings)
        log.setListener { item ->
            sessionRecorder.record("app_log", "native", item, force = true)
        }
        notifications = NotificationController(this)
        overlay = TelemetryOverlayController(this)
        gps = GpsTelemetryManager(this, log, ::consumeGpsUpdate)
        usb = UsbSerialManager(this, settings, log, ::usbStateChanged, sessionRecorder::recordRawUsb)
        runtime = NativeRuntimeManager(
            paths = paths,
            usb = usb,
            log = log,
            onStateChanged = ::stateChanged,
            onTelemetryEvent = ::consumeEngineEvent,
            onEngineExited = ::onEngineExited,
        )
        kWriter = KWriteManager(
            paths = paths,
            serial = runtime.serialScheduler(),
            log = log,
            isEngineRunning = { runtime.running },
            stopEngine = { true },
            startEngine = { true },
            onBusyChanged = { stateChanged() },
            onConfirmedWrite = {
                scheduler.execute {
                    if (::learningArchive.isInitialized) {
                        learningArchive.saveInternalCheckpoint("Mapa K confirmado")
                    }
                    if (::link.isInitialized) link.markDataChanged("mapa K confirmado")
                }
            },
            onConfirmedBatch = { payload ->
                sessionRecorder.record("k_batch_confirmed", "map_k", payload, force = true)
                obd?.recordConfirmedAdjustment("MAP_K", payload)
                runtime.notifyCalibrationAdjustment(payload)
                learningArchive.saveInternalCheckpoint("Após escrita K confirmada")
                link.markDataChanged("escrita K confirmada")
            },
        )
        kFactor = KFactorManager(
            paths = paths,
            serial = runtime.serialScheduler(),
            log = log,
            onBusyChanged = { stateChanged() },
            onConfirmedBatch = { payload ->
                sessionRecorder.record("k_factor_batch_confirmed", "k_factor", payload, force = true)
                obd?.recordConfirmedAdjustment("K_FACTOR", payload)
                runtime.notifyCalibrationAdjustment(payload)
                learningArchive.saveInternalCheckpoint("Após escrita K factor confirmada")
                link.markDataChanged("escrita K factor confirmada")
            },
        )
        nativeAutoCal = NativeAutoCalMonitor(
            serial = runtime.serialScheduler(),
            calibrationBusy = { kWriter.isBusy() || kFactor.isBusy() },
            onFreshSnapshot = { snapshot ->
                runtime.importNativeAutoCalSnapshot(snapshot)
                sessionRecorder.record("autocal_native_snapshot", "autocal", snapshot, force = true)
            },
            onNativeCalibrationObserved = { payload ->
                val result = runtime.notifyCalibrationAdjustment(payload)
                sessionRecorder.record(
                    "autocal_native_calibration_epoch",
                    "autocal",
                    JSONObject(payload.toString()).put("learningResult", result),
                    force = true,
                )
                if (::link.isInitialized) link.markDataChanged("AutoCal nativo alterou Curva K")
            },
            onStateChanged = { stateChanged() },
        )
        // OBD é somente observacional: registra STFT/LTFT e nunca altera o motor de aprendizado.
        obd = ObdAssistManager(
            context = this,
            paths = paths,
            settings = settings,
            log = log,
            localCoreProvider = ::coreTelemetryForLink,
            onStateChanged = ::stateChanged,
            onLiveSample = { sample ->
                sessionRecorder.record("obd", "obd", sample, force = true)
            },
        )
        learningArchive = LearningArchiveManager(paths, settings, runtime, obd, kWriter, log)
        link = OmegasLinkManager(
            settings = settings,
            log = log,
            usbConnected = { usb.connected },
            coreTelemetry = ::coreTelemetryForLink,
            exportLearning = { runtime.exportLearning(settings.deviceId) },
            mergeLearning = { payload -> runtime.mergeLearning(payload, settings.deviceId) },
            exportHistory = { kWriter.exportHistoryComponent(settings.deviceId) },
            mergeHistory = { payload -> kWriter.mergeHistoryComponent(payload) },
            obd = obd,
            onStateChanged = ::stateChanged,
            exportAutoCalContext = {
                JSONObject()
                    .put("schema", "landi-autocal-18x30-v2")
                    .put("source", "ECU_NATIVE")
                    .put("nativeEcuEvidence", runtime.exportLearning(settings.deviceId).optJSONArray("nativeEcuEvidence") ?: org.json.JSONArray())
                    .put("automaticCalibration", false)
                    .put("manualOnly", true)
            },
            mergeAutoCalContext = { payload ->
                JSONObject().put("ok", true).put("accepted", false).put("source", payload.optString("source", "UNKNOWN"))
                    .put("reason", "context-only-no-local-mutation")
            },
            exportNativeReceipts = {
                val file = File(paths.runtimeRoot, "autocal_native_receipts.json")
                JSONObject().put("receipts", if (file.isFile) {
                    try { org.json.JSONArray(file.readText(Charsets.UTF_8)) } catch (_: Exception) { org.json.JSONArray() }
                } else org.json.JSONArray())
            },
        )
        lanServer = LanPanelServer(this, log, ::fullEngineSnapshotJson, ::serviceStatusJson)

        startForegroundCompat()
        overlay.restoreIfAllowed()
        updateWakeLock()
        if (settings.gpsEnabled && gps.hasPermission()) {
            gps.start(settings.gpsIntervalMs)
            startForegroundCompat()
        }
        if (settings.lanServerEnabled) lanServer.start(settings.lanServerPort, settings.lanAccessToken)
        if (settings.linkEnabled) link.start()
        if (settings.obdMode == "local" && settings.obdAutoConnect && settings.obdDeviceAddress.isNotBlank()) {
            obd?.connect(settings.obdDeviceAddress)
        }
        if (settings.autoConnectUsb && usb.hasCompatibleDevice()) usb.connect()
        healthTask = scheduler.scheduleWithFixedDelay(::healthTick, 200L, 3000L, TimeUnit.MILLISECONDS)
        updateOverlay()
        log.add("INFO", "SERVICE", "OMEGAS Pro Hub ${BuildConfig.VERSION_NAME} iniciado com núcleo Android")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_ENGINE -> scheduler.execute { toggleEngine() }
            ACTION_DISCONNECT_USB -> scheduler.execute {
                if (usb.connected) disconnectUsb() else connectUsb()
            }
            ACTION_RESTART_ENGINE -> scheduler.execute { restartEngine() }
            ACTION_STOP_SERVICE -> scheduler.execute { stopSelf() }
            else -> scheduler.execute {
                if (settings.autoConnectUsb && !usb.connected && usb.hasCompatibleDevice()) usb.connect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (stopping) return
        stopping = true
        healthTask?.cancel(true)
        scheduler.shutdownNow()
        try { runtime.stop(3) } catch (_: Exception) {}
        try { runtime.endUsbSession("SERVICE_DESTROYED") } catch (_: Exception) {}
        try { usb.disconnect() } catch (_: Exception) {}
        try { link.close() } catch (_: Exception) {}
        try { obd?.close() } catch (_: Exception) {}
        try { lanServer.close() } catch (_: Exception) {}
        try { gps.stop() } catch (_: Exception) {}
        try { overlay.close() } catch (_: Exception) {}
        try { sessionRecorder.close() } catch (_: Exception) {}
        try { nativeAutoCal.endUsbSession() } catch (_: Exception) {}
        try { kFactor.close() } catch (_: Exception) {}
        try { kWriter.close() } catch (_: Exception) {}
        try { runtime.close() } catch (_: Exception) {}
        try { usb.close() } catch (_: Exception) {}
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        log.setListener(null)
        super.onDestroy()
    }

    fun status(): HubStatus {
        val live = telemetryStore.telemetryCopy()
        val learning = try { runtime.learningStatus() } catch (_: Exception) { JSONObject() }
        val parityLetter = settings.parity.firstOrNull()?.uppercaseChar() ?: 'N'
        return HubStatus(
            serviceRunning = true,
            engineRunning = runtime.running,
            engineReady = runtime.ready,
            engineStuck = runtime.stuck,
            engineVersion = BuildConfig.VERSION_NAME,
            usbConnected = usb.connected,
            usbDevice = usb.deviceLabel,
            usbPermissionPending = usb.permissionPending,
            autoReconnectUsb = settings.autoReconnectUsb,
            baudRate = settings.baudRate,
            serialFormat = "${settings.dataBits}$parityLetter${settings.stopBits}",
            ecuState = when {
                runtime.ready -> "ONLINE"
                runtime.running -> "INICIALIZANDO"
                usb.connected -> "MP48 CONECTADO"
                else -> "OFFLINE"
            },
            fuelState = live.optString("fuel", live.optString("state", "--")),
            rpm = live.optInt("rpm", 0),
            petrolMs = live.optDouble("petrol_ms", 0.0),
            gasMs = live.optDouble("gas_ms_diagnostic", 0.0),
            mapBar = live.optDouble("load_bar", live.optDouble("map_bar", 0.0)),
            gasPressureBar = live.optDouble("pressure_diff_bar", live.optDouble("gas_pressure_abs_bar", 0.0)),
            currentCell = learning.optString("state", "--"),
            confidence = learning.optDouble("reference_confidence", 0.0) * 100.0,
            lastError = runtime.lastError,
            wakeLockHeld = wakeLock?.isHeld == true,
            uptimeSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L).coerceAtLeast(0L),
            engineRestarts = engineRestarts,
            healthFailures = healthFailures,
            storagePath = paths.externalRoot.absolutePath,
            workspaceConfigured = false,
            gpsEnabled = gps.running,
            gpsSpeedKmh = gps.json().optDouble("speedKmh", 0.0),
            gpsAccuracyM = gps.json().optDouble("accuracyM", 0.0),
            lanEnabled = lanServer.running,
            lanAddress = if (lanServer.running) lanServer.address() else "",
            directTelemetryAgeMs = telemetryStore.ageMs().let { if (it == Long.MAX_VALUE) -1L else it },
        )
    }

    fun restartEngine(): Boolean {
        if (!usb.connected) return false
        enginePausedByUser = false
        engineRestarts += 1
        return runtime.restart().also { stateChanged() }
    }

    fun toggleEngine(): Boolean {
        return if (runtime.running) {
            enginePausedByUser = true
            runtime.stop(3)
        } else {
            enginePausedByUser = false
            startEngine("retomada manual")
        }.also { stateChanged() }
    }

    fun connectUsb(deviceName: String? = null): Boolean {
        enginePausedByUser = false
        monitoringPausedByUser = false
        return usb.connect(deviceName).also { stateChanged() }
    }

    fun disconnectUsb() {
        monitoringPausedByUser = true
        enginePausedByUser = false
        runtime.stop(3)
        usb.disconnect()
        stopSelf()
        stateChanged()
    }

    fun usbDevicesJson(): String = usb.devicesJson()
    fun logsJson(): String = log.json()

    fun fullEngineSnapshotJson(): String {
        val root = try { JSONObject(runtime.fullSnapshotJson()) } catch (_: Exception) { JSONObject() }
        val live = telemetryStore.telemetryCopy()
        val gpsData = gps.json()
        root.put("gps", gpsData)
            .put("k_write", try { JSONObject(kWriter.statusJson()) } catch (_: Exception) { JSONObject() })
            .put("k_factor", try { JSONObject(kFactor.statusJson()) } catch (_: Exception) { JSONObject() })
            .put("session_recorder", try { JSONObject(sessionRecorder.statusJson()) } catch (_: Exception) { JSONObject() })
            .put("obd", try { JSONObject(obd?.statusJson() ?: "{}") } catch (_: Exception) { JSONObject() })
            .put("link_status", try { JSONObject(link.statusJson()) } catch (_: Exception) { JSONObject() })
            .put("consumption", consumptionTracker.buildTelemetryJson(settings.gnvCylinderCapacityM3.toFloat()))
            .put("native_updated_at", System.currentTimeMillis())
            .put("telemetry_age_ms", telemetryStore.ageMs().let { if (it == Long.MAX_VALUE) -1L else it })
        return root.toString()
    }

    fun engineMetricsJson(): String = runtime.metricsJson()
    fun engineSelfTestJson(): String = runtime.selfTestJson()
    fun protocolLabJson(): String = runtime.protocolJson()
    fun learningSyncStatusJson(): String = runtime.learningStatus().toString()

    @Synchronized fun readKCell(row: Int, column: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readCell(row, column).toString()
    @Synchronized fun readKLine(row: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readLine(row).toString()
    @Synchronized fun readKMap(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readFullMap().toString()
    @Synchronized fun recoverKInsertionState(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.recoverInsertionState().toString()
    fun previewKMapCell(row: Int, column: Int, targetValue: Int): String = runtime.previewKWrite(row, column, targetValue).toString()
    fun kWriteStatusJson(): String = kWriter.statusJson()
    fun kWriteHistoryJson(): String = kWriter.historyJson()

    @Synchronized fun readKFactorCurve(): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.readCurve().toString()
    fun kFactorStatusJson(): String = kFactor.statusJson()
    fun kFactorHistoryJson(): String = kFactor.historyJson()

    @Synchronized fun startKWrite(
        row: Int,
        column: Int,
        current: Int,
        target: Int,
        maxStep: Int,
        pauseMs: Int,
    ): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kFactor.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração K factor está em andamento").toString()
        }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        learningArchive.saveInternalCheckpoint("Antes de ajustar célula K")
        return kWriter.startWrite(row, column, current, target, maxStep, pauseMs).toString()
    }

    @Synchronized fun startKBatchWrite(
        cellsJson: String,
        maxStep: Int,
        pauseMs: Int,
        reason: String,
    ): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kFactor.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração K factor está em andamento").toString()
        }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return try {
            learningArchive.saveInternalCheckpoint("Antes de ajustar mapa K: " + reason.take(100))
            kWriter.startBatchWrite(JSONArray(cellsJson), maxStep, pauseMs, reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Lote de células inválido").toString()
        }
    }

    @Synchronized fun startKFactorWrite(pointsJson: String, reason: String): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kWriter.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração do mapa K está em andamento").toString()
        }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return try {
            val points = JSONArray(pointsJson)
            learningArchive.saveInternalCheckpoint("Antes de ajustar K factor: " + reason.take(100))
            kFactor.startBatchWrite(points, reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Lote K factor inválido").toString()
        }
    }

    private fun calibrationBusy(operation: String): String = JSONObject()
        .put("ok", false)
        .put("error", "Uma operação de $operation está em andamento")
        .toString()

    fun importLearningArchive(uri: Uri): String = learningArchive.import(contentResolver, uri).toString()
    fun exportLearningArchive(uri: Uri): String = learningArchive.export(contentResolver, uri).toString()
    fun importNativeAutoCalSnapshot(payload: String): String = try {
        runtime.importNativeAutoCalSnapshot(JSONObject(payload)).toString()
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("error", error.message ?: "Snapshot AutoCal inválido").toString()
    }
    fun learningCheckpointStatusJson(): String = learningArchive.checkpointStatus().toString()

    fun sessionRecorderStatusJson(): String = sessionRecorder.statusJson()
    fun sessionRecorderListJson(): String = sessionRecorder.listSessionsJson()
    fun updateSessionRecorderSettings(
        telemetryEveryMs: Long,
        maxSessionMb: Int,
        keepSessions: Int,
        autoStartOnUsb: Boolean,
        captureRawUsb: Boolean,
    ): String {
        settings.sessionTelemetryEveryMs = telemetryEveryMs
        settings.sessionLogMaxMb = maxSessionMb
        settings.sessionKeepCount = keepSessions
        settings.sessionRecorderAutoStartOnUsb = autoStartOnUsb
        settings.sessionCaptureRawUsb = captureRawUsb
        return sessionRecorder.statusObject().put("ok", true).toString()
    }
    fun startSessionRecording(reason: String): String = sessionRecorder.start(
        reason.ifBlank { "manual" },
        JSONObject().put("appVersion", BuildConfig.VERSION_NAME).put("native", true),
    ).toString()
    fun stopSessionRecording(reason: String): String = sessionRecorder.stop(reason.ifBlank { "manual" }).toString()
    fun exportSession(uri: Uri, sessionId: String): String = sessionRecorder.exportSession(contentResolver, uri, sessionId).toString()

    fun setGpsEnabled(enabled: Boolean): JSONObject {
        settings.gpsEnabled = enabled
        val ok = if (enabled) gps.start(settings.gpsIntervalMs) else {
            gps.stop()
            true
        }
        startForegroundCompat()
        updateWakeLock()
        stateChanged()
        return JSONObject().put("ok", ok).put("enabled", gps.running)
            .apply { if (!ok) put("error", gps.lastError) }
    }

    fun setLanEnabled(enabled: Boolean): JSONObject {
        settings.lanServerEnabled = enabled
        val ok = if (enabled) lanServer.start(settings.lanServerPort, settings.lanAccessToken) else {
            lanServer.stop()
            true
        }
        stateChanged()
        return JSONObject().put("ok", ok).put("enabled", lanServer.running)
            .put("address", if (lanServer.running) lanServer.address() else "")
            .apply { if (!ok) put("error", lanServer.lastError) }
    }

    fun obdDevicesJson(): String = obd?.pairedDevicesJson() ?: "[]"
    fun obdStatusJson(): String = obd?.statusJson() ?: "{}"
    fun obdMapsJson(): String = obd?.mapsJson() ?: "{}"
    fun setObdMode(mode: String): String = obd?.setMode(mode)?.toString() ?: "{}"
    fun setObdManualFuel(fuel: String): String = obd?.setManualFuel(fuel)?.toString() ?: "{}"
    fun connectObd(address: String): String = obd?.connect(address)?.toString() ?: "{}"
    fun disconnectObd(): String {
        obd?.disconnect()
        return JSONObject().put("ok", true).toString()
    }

    fun overlayStatusJson(): String = if (::overlay.isInitialized) overlay.statusJson().toString() else "{}"
    fun setTelemetryOverlayEnabled(enabled: Boolean): String {
        if (!::overlay.isInitialized) return JSONObject().put("ok", false).put("error", "Overlay indisponível").toString()
        val result = overlay.setEnabled(enabled)
        updateOverlay()
        return result.toString()
    }

    fun nativeAutoCalStatusJson(): String =
        if (::nativeAutoCal.isInitialized) nativeAutoCal.statusJson().toString() else "{}"

    fun nativeAutoCalSnapshotJson(): String =
        if (::nativeAutoCal.isInitialized) nativeAutoCal.latestSnapshotJson().toString() else "{}"

    fun linkStatusJson(): String {
        val raw = try { JSONObject(link.statusJson()) } catch (_: Exception) { JSONObject() }
        return raw.put("connected", raw.optBoolean("peerConnected", false))
            .put(
                "message",
                raw.optString("lastError").ifBlank {
                    if (raw.optBoolean("peerConnected")) "Outro aparelho conectado" else "Aguardando aparelho na rede local"
                },
            )
            .toString()
    }
    fun configureOmegasLink(enabled: Boolean, pairCode: String): String {
        val normalizedCode = pairCode.filter(Char::isDigit)
        if (normalizedCode.isNotBlank() && normalizedCode.length != 6) {
            return JSONObject().put("ok", false).put("error", "O código do Link deve ter 6 números").toString()
        }
        if (normalizedCode.length == 6) settings.linkPairCode = normalizedCode
        settings.linkEnabled = enabled
        return link.applySettings().toString()
    }
    fun claimLinkMain(): String = link.claimMain().toString()
    fun releaseLinkMain(): String = link.releaseMain().toString()
    fun syncLinkNow(): String = link.syncNow().toString()

    private fun usbStateChanged() {
        if (stopping) return
        try {
            scheduler.execute {
                handleUsbTransition()
                stateChanged()
            }
        } catch (_: Exception) {
            stateChanged()
        }
    }

    private fun handleUsbTransition() {
        val connected = usb.connected
        if (connected == lastUsbConnected) return
        lastUsbConnected = connected
        if (connected) {
            monitoringPausedByUser = false
            val sessionId = usb.connectionSessionId
            telemetryStore.beginSession(sessionId)
            runtime.beginUsbSession(sessionId)
            kWriter.beginUsbSession(sessionId)
            kFactor.beginUsbSession(sessionId)
            nativeAutoCal.beginUsbSession(sessionId)
            enginePausedByUser = false
            if (settings.sessionRecorderEnabled && settings.sessionRecorderAutoStartOnUsb) {
                sessionRecorder.start(
                    "MP48 conectado",
                    JSONObject().put("appVersion", BuildConfig.VERSION_NAME).put("usb", usb.deviceLabel),
                )
            }
            if (settings.autoStartEngine) startEngine("nova conexão física MP48")
        } else {
            runtime.stop(2)
            runtime.endUsbSession("USB_DISCONNECTED")
            nativeAutoCal.endUsbSession()
            telemetryStore.invalidate("USB_DISCONNECTED")
            if (sessionRecorder.statusObject().optBoolean("recording")) {
                sessionRecorder.stop("MP48 desconectado")
            }
            if (monitoringPausedByUser || !settings.autoReconnectUsb) {
                stopSelf()
            }
        }
        if (::link.isInitialized) link.onLocalCapabilitiesChanged()
        updateWakeLock()
    }

    private fun startEngine(reason: String): Boolean {
        if (!usb.connected || runtime.running || runtime.stuck || enginePausedByUser) return false
        val ok = runtime.start()
        log.add(if (ok) "INFO" else "WARN", "ECU-NATIVE", "$reason • iniciado=$ok")
        return ok
    }

    private fun healthTick() {
        if (stopping) return
        try {
            handleUsbTransition()
            if (!usb.connected && settings.autoReconnectUsb && !enginePausedByUser && usb.hasCompatibleDevice()) {
                connectUsb()
            }
            if (usb.connected && settings.autoStartEngine && !enginePausedByUser &&
                !runtime.running && !runtime.stuck && !kWriter.isBusy() && !kFactor.isBusy()
            ) {
                engineRestarts += 1
                startEngine("recuperação automática do núcleo")
            }
            if (!usb.connected && runtime.running) runtime.stop(2)
            if (usb.connected && runtime.running && runtime.serialScheduler().currentSessionId() > 0L) {
                nativeAutoCal.tick()
            }
            if (sessionRecorder.statusObject().optBoolean("recording")) {
                sessionRecorder.record(
                    "full_snapshot",
                    "native",
                    try { JSONObject(fullEngineSnapshotJson()) } catch (_: Exception) { JSONObject() },
                )
            }
            renewWakeLockIfNeeded()
            updateOverlay()
            updateNotification()
        } catch (error: Exception) {
            healthFailures += 1
            log.add("WARN", "SERVICE", "Monitor nativo: ${error.message}")
        }
    }

    private fun consumeEngineEvent(raw: String) {
        val accepted = telemetryStore.updateFromEngineEvent(raw) ?: return
        val root = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val live = root.optJSONObject("live") ?: root.optJSONObject("data") ?: JSONObject()
        val cngActive = live.optString("fuel").uppercase() == "GNV"
        if (cngActive) {
            consumptionTracker.update(
                timestampMs = accepted.optLong("timestamp", System.currentTimeMillis()),
                rawPressure = live.optInt("level_raw", -1),
            )
        }

        sessionRecorder.record("telemetry", "mp48", live)
        sessionRecorder.record("engine_event", "native", root, force = false)
        stateChanged()
    }

    private fun consumeGpsUpdate() {
        telemetryStore.updateGps(gps.json())
        stateChanged()
    }

    private fun onEngineExited(crashed: Boolean) {
        if (crashed) healthFailures += 1
        stateChanged()
    }

    private fun coreTelemetryForLink(): JSONObject {
        val status = status()
        return JSONObject()
            .put("engineReady", status.engineReady)
            .put("rpm", status.rpm)
            .put("petrolMs", status.petrolMs)
            .put("petrol_ms", status.petrolMs)
            .put("gasMs", status.gasMs)
            .put("gas_ms_diagnostic", status.gasMs)
            .put("mapBar", status.mapBar)
            .put("load_bar", status.mapBar)
            .put("fuelState", status.fuelState)
            .put("fuel", status.fuelState)
            .put("state", status.ecuState)
            .put("updatedAt", System.currentTimeMillis() - status.directTelemetryAgeMs.coerceAtLeast(0L))
            .put("source", "android-native")
    }

    private fun serviceStatusJson(): JSONObject {
        val status = status()
        return JSONObject()
            .put("ok", true)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("engineRunning", status.engineRunning)
            .put("engineReady", status.engineReady)
            .put("usbConnected", status.usbConnected)
            .put("rpm", status.rpm)
            .put("fuel", status.fuelState)
            .put("lastError", status.lastError)
            .put("calibrationBusy", kWriter.isBusy() || kFactor.isBusy())
    }

    private fun stateChanged() {
        if (stopping || !::notifications.isInitialized) return
        updateOverlay()
        updateNotification()
    }

    private fun updateOverlay() {
        if (!::overlay.isInitialized || (!overlay.requestedEnabled() && !overlay.visible())) return
        val hub = status()
        val obdLive = try { JSONObject(obd?.statusJson() ?: "{}") } catch (_: Exception) { JSONObject() }
        val evidence = obdLive.optJSONObject("independentEvidence") ?: JSONObject()
        val cell = evidence.optString("cellKey", "").takeIf { it.isNotBlank() } ?: "—"
        val stft = if (obdLive.has("stft") && !obdLive.isNull("stft")) obdLive.optDouble("stft") else null
        val obdRpm = if (obdLive.has("rpm") && !obdLive.isNull("rpm")) obdLive.optDouble("rpm") else null
        overlay.update(
            TelemetryOverlayController.Snapshot(
                cell = cell,
                stft = stft,
                petrolMs = hub.petrolMs.takeIf { it > 0.0 },
                rpm = obdRpm?.takeIf { it > 0.0 } ?: hub.rpm.toDouble().takeIf { it > 0.0 },
            ),
        )
    }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var flags = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (::gps.isInitialized && gps.running) {
                flags = flags or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            flags
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                NotificationController.NOTIFICATION_ID,
                notifications.build(status()),
                type,
            )
        } catch (e: Exception) {
            log.add("ERROR", "SERVICE", "Falha ao iniciar ForegroundService: ${e.message}")
            try {
                ServiceCompat.startForeground(
                    this,
                    NotificationController.NOTIFICATION_ID,
                    notifications.build(status()),
                    0,
                )
            } catch (_: Exception) {}
        }
        lastNotificationAt = System.currentTimeMillis()
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < 900L) return
        lastNotificationAt = now
        try {
            NotificationManagerCompat.from(this)
                .notify(NotificationController.NOTIFICATION_ID, notifications.build(status()))
        } catch (_: SecurityException) {
        }
    }

    private fun updateWakeLock() {
        if (settings.keepCpuAwake && runtime.running) {
            renewWakeLockIfNeeded()
        } else {
            releaseWakeLock()
        }
    }

    private fun renewWakeLockIfNeeded() {
        if (!settings.keepCpuAwake || !runtime.running || !usb.connected) {
            releaseWakeLock()
            return
        }
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:NativeCore")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        if (!lock.isHeld) {
            try { lock.acquire(10 * 60_000L) } catch (_: Exception) {}
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}
