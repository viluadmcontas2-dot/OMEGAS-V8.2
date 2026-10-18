package com.omegas.prohub.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RefinementAutopilot
import com.omegas.prohub.autocal.RefinementCycle
import com.omegas.prohub.autocal.RefinementJournal
import com.omegas.prohub.autocal.StallWatch
import androidx.core.app.ServiceCompat
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.KFactorManager
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.autocal.NativeAutoCalMonitor
import com.omegas.prohub.diagnostics.DocumentsSessionMirror
import com.omegas.prohub.diagnostics.SessionRecorder
import com.omegas.prohub.ecu.NativeRuntimeManager
import com.omegas.prohub.gps.GpsTelemetryManager
import com.omegas.prohub.learning.LearningArchiveManager
import com.omegas.prohub.learning.LearningTemperatureSettings
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.model.HubStatus
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

    /** Leitura automática da curva e do Mapa K (só LER; gravar continua exigindo ação explícita). Fora da thread do monitor. */
    private val autoReadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-auto-read").apply { isDaemon = true }
    }
    private var autoReadSeenSessionId = 0L
    private var autoReadSeenAtMs = 0L
    private var autoReadLastOkAtMs = 0L
    private var autoReadAttempts = 0
    @Volatile private var parametersRead = false
    /** Refino automático depois de cada AutoMatch da ECU (D1). Leitura + decisão + (só em LIGADO) escrita, fora das threads do USB e da tela. */
    private val autoRefineExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-auto-refine").apply { isDaemon = true }
    }
    @Volatile private var preAutoMatchSnapshot: JSONObject? = null
    @Volatile private var autoRefineLastEpoch: String? = null
    /** Quem pediu a leitura em curso: UI (botão/abertura de tela), AUTO_START ou AUTO_PERIODIC. Vai junto no evento da sessão. */
    @Volatile private var readTrigger = "UI"
    /** Última curva vista (factorsRaw em texto) e quando o app gravou a última curva; classificam a origem de cada mudança. */
    @Volatile private var lastCurveRaw: String? = null
    @Volatile private var lastAppCurveWriteMs = 0L

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
    lateinit var kWriter: KWriteManager
        private set
    lateinit var kFactor: KFactorManager
    lateinit var bufferProbe: com.omegas.prohub.autocal.BufferWriteProbe
    lateinit var parameterReader: com.omegas.prohub.autocal.EcuParameterReader
        private set
    lateinit var nativeAutoCal: NativeAutoCalMonitor
        private set
    lateinit var learningArchive: LearningArchiveManager
        private set
    lateinit var overlay: TelemetryOverlayController
        private set
    /** Pontos próprios GNV × gasolina (RPM×MAP); alimenta o refino e o gráfico do Refino. */
    lateinit var equivalence: EquivalenceLedger
        private set
    /** Ciclo fechado: cada gravação de Curva K vira experimento verificado por faixa. */
    lateinit var refinementJournal: RefinementJournal
        private set
    /** Fase do refino (ECU no automático → nossa vez → verificando → estável); só observa e avisa. */
    lateinit var refinementAutopilot: RefinementAutopilot
        private set
    /** Cola do refino (pura, testável na JVM): gravações → diário, referência da ECU, proposta, avaliação e piloto. */
    lateinit var refinement: RefinementCycle
        private set
    /** Onde o motor apagou no GNV (desaceleração/embreagem): só observa, mostra no Refino. */
    lateinit var stallWatch: StallWatch
        private set
    private lateinit var learningTemperature: LearningTemperatureSettings
    private lateinit var learningTolerances: LearningToleranceSettings

    private lateinit var notifications: NotificationController
    private var healthTask: ScheduledFuture<*>? = null
    private var autoCalTask: ScheduledFuture<*>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val startedAt = System.currentTimeMillis()
    private var lastNotificationAt = 0L
    private var lastUsbConnected = false
    private var lastUsbSessionId = 0L
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
        equivalence = EquivalenceLedger(File(paths.runtimeRoot, "equivalence_ledger.json"))
        refinementJournal = RefinementJournal(File(paths.runtimeRoot, "refinement_journal.json"))
        refinementAutopilot = RefinementAutopilot(File(paths.runtimeRoot, "refinement_autopilot.json"))
        refinement = RefinementCycle(equivalence, refinementJournal, refinementAutopilot)
        stallWatch = StallWatch(File(paths.runtimeRoot, "stall_watch.json"))
        val documentsMirror = DocumentsSessionMirror(this)
        sessionRecorder = SessionRecorder(paths, settings, documentsMirror)
        sessionRecorder.recoverDocumentsMirrorAsync()
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
        // Trilha de comandos na sessão: só observa e repassa (nenhum byte muda). Escritas completas; leituras em resumo.
        val recordingSerial = com.omegas.prohub.diagnostics.RecordingSerialScheduler(runtime.serialScheduler()) { type, source, data ->
            sessionRecorder.record(type, source, data, force = type == "ecu_command")
        }
        kWriter = KWriteManager(
            paths = paths,
            serial = recordingSerial,
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
                }
            },
            onMapRead = { payload -> sessionRecorder.record("k_read_map", "map_k", payload.put("trigger", readTrigger), force = true) },
            onConfirmedBatch = { payload ->
                sessionRecorder.record("k_batch_confirmed", "map_k", JSONObject(payload.toString()).put("origin", "APP_UI_MANUAL"), force = true)
                sessionRecorder.record("k_map_state", "map_k", kWriter.cachedMapJson().put("origin", "APP_UI_MANUAL").put("afterAdjustmentId", payload.optString("adjustmentId")), force = true)
                // Mapa K mudou o gás: o GNV medido antes não vale mais; a verificação e a oferta de restaurar perdem validade.
                refinement.mapKWritten()
                runtime.notifyCalibrationAdjustment(payload)
                learningArchive.saveInternalCheckpoint("Após escrita K confirmada")
            },
        )
        kFactor = KFactorManager(
            paths = paths,
            serial = recordingSerial,
            log = log,
            onBusyChanged = { stateChanged() },
            onCurveRead = { payload ->
                lastCurveRaw = payload.optJSONArray("factorsRaw")?.toString()
                sessionRecorder.record("k_read_curve", "k_factor", payload.put("trigger", readTrigger), force = true)
            },
            onConfirmedBatch = { payload ->
                sessionRecorder.record("k_factor_batch_confirmed", "k_factor", JSONObject(payload.toString()).put("origin", "APP_UI_MANUAL"), force = true)
                lastAppCurveWriteMs = System.currentTimeMillis()
                val state = kFactor.cachedCurveJson()
                lastCurveRaw = state.optJSONArray("factorsRaw")?.toString()
                sessionRecorder.record("k_curve_state", "k_factor", state.put("origin", "APP_UI_MANUAL").put("afterAdjustmentId", payload.optString("adjustmentId")), force = true)
                recordCurveExperiment(payload)
                runtime.notifyCalibrationAdjustment(payload)
                learningArchive.saveInternalCheckpoint("Após escrita K factor confirmada")
            },
            publishManualBackup = { file -> documentsMirror.publishRootFile(file) },
        )
        parameterReader = com.omegas.prohub.autocal.EcuParameterReader(recordingSerial)
        bufferProbe = com.omegas.prohub.autocal.BufferWriteProbe(recordingSerial) { type, data ->
            sessionRecorder.record(type, "buffer_probe", data, force = true)
        }
        nativeAutoCal = NativeAutoCalMonitor(
            serial = recordingSerial,
            calibrationBusy = { kWriter.isBusy() || kFactor.isBusy() },
            onFreshSnapshot = { snapshot ->
                runtime.importNativeAutoCalSnapshot(snapshot)
                sessionRecorder.record("autocal_native_snapshot", "autocal", snapshot, force = true)
                recordCurveChangeFromSnapshot(snapshot)
            },
            onNativeCalibrationObserved = { payload ->
                val result = runtime.notifyCalibrationAdjustment(payload)
                sessionRecorder.record(
                    "autocal_native_calibration_epoch",
                    "autocal",
                    JSONObject(payload.toString()).put("learningResult", result),
                    force = true,
                )
                // A ECU trocou a Curva K sozinha: GNV antigo descartado, verificação interrompida.
                refinement.nativeCurveChanged()
                autoRefineAfterEcuAutoMatch(payload)
            },
            onNativeAutoMatchObserved = { payload ->
                sessionRecorder.record(
                    "autocal_native_automatch_epoch",
                    "autocal",
                    payload,
                    force = true,
                )
            },
            onPreAutoMatchState = { payload ->
                sessionRecorder.record("autocal_pre_automatch_state", "autocal", payload, force = true)
            },
            onPreAutoMatchSnapshot = { snapshot -> preAutoMatchSnapshot = snapshot },
            onStateChanged = { stateChanged() },
        )
        learningArchive = LearningArchiveManager(paths, settings, runtime, kWriter, log)

        startForegroundCompat()
        overlay.restoreIfAllowed()
        updateWakeLock()
        if (settings.gpsEnabled && gps.hasPermission()) {
            gps.start(settings.gpsIntervalMs)
            startForegroundCompat()
        }
        if (settings.autoConnectUsb && usb.hasCompatibleDevice()) usb.connect()
        healthTask = scheduler.scheduleWithFixedDelay(::healthTick, 200L, 3000L, TimeUnit.MILLISECONDS)
        autoCalTask = scheduler.scheduleWithFixedDelay(::autoCalTick, 1_000L, 1_000L, TimeUnit.MILLISECONDS)
        updateOverlay()
        log.add("INFO", "SERVICE", "OMEGAS Pro Hub ${BuildConfig.VERSION_NAME} iniciado com núcleo Android")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_ENGINE -> scheduler.execute { toggleEngine() }
            ACTION_DISCONNECT_USB -> scheduler.execute {
                if (usb.connected) disconnectUsb() else connectUsb(userInitiated = true)
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
        try { equivalence.flush() } catch (_: Exception) {}
        healthTask?.cancel(true)
        autoCalTask?.cancel(true)
        scheduler.shutdownNow()
        try { runtime.stop(3) } catch (_: Exception) {}
        try { runtime.endUsbSession("SERVICE_DESTROYED") } catch (_: Exception) {}
        try { usb.disconnect() } catch (_: Exception) {}
        try { gps.stop() } catch (_: Exception) {}
        try { overlay.close() } catch (_: Exception) {}
        try { sessionRecorder.close() } catch (_: Exception) {}
        try { nativeAutoCal.endUsbSession() } catch (_: Exception) {}
        try { kFactor.close() } catch (_: Exception) {}
        try { bufferProbe.close() } catch (_: Exception) {}
        autoRefineExecutor.shutdownNow()
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

    fun connectUsb(deviceName: String? = null, userInitiated: Boolean = false): Boolean {
        enginePausedByUser = false
        monitoringPausedByUser = false
        return usb.connect(deviceName, allowPermissionRetry = userInitiated).also { stateChanged() }
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
    fun startBufferWriteProbe(): String = bufferProbe.start().toString()
    fun bufferWriteProbeStatusJson(): String = bufferProbe.statusJson()
    fun kFactorHistoryJson(): String = kFactor.historyJson()

    @Synchronized fun saveKFactorBackup(label: String): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.saveCurrentBackup(label).toString()

    fun listKFactorBackups(): String = kFactor.listBackups().toString()

    @Synchronized fun prepareKFactorRestore(fileName: String): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.prepareRestore(fileName).toString()

    @Synchronized fun startKWrite(row: Int, column: Int, current: Int, target: Int): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kFactor.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração K factor está em andamento").toString()
        }
        learningArchive.saveInternalCheckpoint("Antes de ajustar célula K")
        return kWriter.startWrite(row, column, current, target).toString()
    }

    @Synchronized fun startKBatchWrite(cellsJson: String, reason: String): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kFactor.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração K factor está em andamento").toString()
        }
        return try {
            learningArchive.saveInternalCheckpoint("Antes de ajustar mapa K: " + reason.take(100))
            kWriter.startBatchWrite(JSONArray(cellsJson), reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Lote de células inválido").toString()
        }
    }

    @Synchronized fun startKFactorReset(): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kWriter.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração do mapa K está em andamento").toString()
        }
        CalibrationWriteSafetyPolicy.unsafeReason(status())?.let { reason ->
            return JSONObject().put("ok", false).put("error", reason).toString()
        }
        return try {
            kFactor.startResetToNeutral("Reset Curva K · ProgBase MUL_ACT=1.0").toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Reset da Curva K inválido").toString()
        }
    }

    @Synchronized fun startKFactorWrite(pointsJson: String, reason: String): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kWriter.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração do mapa K está em andamento").toString()
        }
        return try {
            val points = JSONArray(pointsJson)
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
        JSONObject()
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("native", true)
            .put("usbSessionId", if (usb.connected) usb.connectionSessionId else 0L),
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

    fun overlayReady(): Boolean = ::overlay.isInitialized

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
        val sessionId = if (connected) usb.connectionSessionId else 0L
        val transition = UsbSessionTransitionPolicy.classify(
            lastConnected = lastUsbConnected,
            lastSessionId = lastUsbSessionId,
            connected = connected,
            sessionId = sessionId,
        )
        if (transition == UsbSessionTransition.NONE) return

        val previousSessionId = lastUsbSessionId
        val wasConnected = lastUsbConnected
        lastUsbConnected = connected
        lastUsbSessionId = sessionId

        if (connected) {
            val generationChanged = transition == UsbSessionTransition.GENERATION_CHANGED
            monitoringPausedByUser = false
            if (generationChanged) {
                if (sessionRecorder.statusObject().optBoolean("recording")) {
                    sessionRecorder.stop("USB_SESSION_REPLACED")
                }
                runtime.endUsbSession("USB_SESSION_REPLACED")
                nativeAutoCal.endUsbSession()
                telemetryStore.invalidate("USB_SESSION_REPLACED")
                log.add("INFO", "USB", "Nova geração USB detectada • $previousSessionId → $sessionId")
            }
            telemetryStore.beginSession(sessionId)
            runtime.beginUsbSession(sessionId)
            kWriter.beginUsbSession(sessionId)
            kFactor.beginUsbSession(sessionId)
            nativeAutoCal.beginUsbSession(sessionId)
            if (!wasConnected) enginePausedByUser = false
            if (settings.sessionRecorderEnabled && settings.sessionRecorderAutoStartOnUsb &&
                !sessionRecorder.statusObject().optBoolean("recording")
            ) {
                sessionRecorder.start(
                    "MP48 conectado",
                    JSONObject()
                        .put("appVersion", BuildConfig.VERSION_NAME)
                        .put("usb", usb.deviceLabel)
                        .put("usbSessionId", sessionId),
                )
            }
            if (settings.autoStartEngine && !enginePausedByUser) {
                startEngine(if (generationChanged) "nova geração física MP48" else "nova conexão física MP48")
            }
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
        updateWakeLock()
    }

    private fun startEngine(reason: String): Boolean {
        if (!usb.connected || runtime.running || runtime.stuck || enginePausedByUser) return false
        val ok = runtime.start()
        log.add(if (ok) "INFO" else "WARN", "ECU-NATIVE", "$reason • iniciado=$ok")
        return ok
    }

    /** Gravação de Curva K confirmada → experimento com o índice medido com a curva antiga. */
    private fun recordCurveExperiment(payload: JSONObject) {
        try {
            refinement.recordConfirmedCurve(payload)
        } catch (error: Exception) {
            log.add("WARN", "REFINO", "Experimento não registrado: ${error.message}")
        } finally {
            // A fase nova (medindo, sem a proposta antiga) já vale antes do BATCH_CONFIRMED chegar à tela.
            observeRefinement()
        }
    }

    /** ECU online e motor funcionando no GNV em qualquer rotação (injeção acima de 1 ms): só então a medição do refino anda. */
    private fun refinementDriving(): Boolean {
        if (!usb.connected || !runtime.ready || !telemetryStore.isValid()) return false
        val now = status()
        return RefinementCycle.driving(now.rpm.toDouble(), now.petrolMs, now.fuelState)
    }

    private val refinementLock = Any()

    /** Piloto do refino: decide a fase e avisa uma vez por fase. Nunca grava na ECU. */
    private fun observeRefinement() {
        synchronized(refinementLock) { observeRefinementLocked() }
    }

    private fun observeRefinementLocked() {
        try {
            val progress = if (::nativeAutoCal.isInitialized) nativeAutoCal.autoMatchProgressJson() else null
            val snapshot = if (::nativeAutoCal.isInitialized) nativeAutoCal.latestSnapshotJson() else null
            // Gravação da Curva K que falhou no meio (pontos com ACK) também é gravação do app.
            try { refinement.noteCurveOperation(JSONObject(kFactor.statusJson()), snapshot) } catch (_: Exception) {}
            val before = refinementAutopilot.json().optString("phase")
            // Referência de gasolina da ECU (leitura desconhecida mantém a última), proposta e fase: tudo na cola.
            val decided = refinement.observe(
                ecuOnline = usb.connected && runtime.ready,
                usbConnected = usb.connected,
                progress = progress,
                snapshot = snapshot,
            )
            if (decided.optString("phase") != before) {
                sessionRecorder.record("refinement_phase", "autocal", decided, force = true)
                stateChanged()
            }
            refinementAutopilot.takeAlert()?.let { alert ->
                // Android 13+: sem permissão o aviso é omitido; a fase continua na tela e na sessão.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        NotificationManagerCompat.from(this).notify(
                            NotificationController.REFINEMENT_NOTIFICATION_ID,
                            notifications.buildRefinementAlert(alert.optString("headline"), alert.optString("next")),
                        )
                    } catch (_: SecurityException) {
                    }
                }
            }
        } catch (error: Exception) {
            log.add("WARN", "REFINO", "Piloto do refino: ${error.message}")
        }
    }

    private fun healthTick() {
        if (stopping) return
        try {
            // "Depois" de cada gravação = só o GNV medido a partir dela; o prazo anda com o motor no GNV (lenta inclusive).
            if (refinement.evaluate(refinementDriving())) stateChanged()
            observeRefinement()
            handleUsbTransition()
            if (!usb.connected && settings.autoReconnectUsb && !monitoringPausedByUser && !enginePausedByUser && usb.hasCompatibleDevice()) {
                connectUsb()
            }
            if (usb.connected && settings.autoStartEngine && !enginePausedByUser &&
                !runtime.running && !runtime.stuck && !kWriter.isBusy() && !kFactor.isBusy()
            ) {
                engineRestarts += 1
                startEngine("recuperação automática do núcleo")
            }
            if (!usb.connected && runtime.running) runtime.stop(2)
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

    /** Curva que a ECU mostra no snapshot (MUL_ACT) diferente da última vista = mudança; registra com a origem provável. */
    fun autoRefineModeName(): String = settings.autoRefineMode

    fun setAutoRefineModeName(mode: String): String {
        val parsed = try { com.omegas.prohub.autocal.AutoRefinePolicy.Mode.valueOf(mode) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Modo inválido").toString()
        }
        settings.autoRefineMode = parsed.name
        sessionRecorder.record("settings_changed", "auto_refine", JSONObject().put("autoRefineMode", parsed.name), force = true)
        return JSONObject().put("ok", true).put("mode", parsed.name).toString()
    }

    /**
     * D1 (decisão do dono, 2026-10-16): depois de cada AutoMatch da ECU, refinar a Curva K sozinho, sem teto por ponto, só dentro das
     * travas absolutas (AutoRefinePolicy). Usa a leitura de ANTES do AutoMatch (a ECU zera o GNV logo depois), lê a curva viva e
     * grava com ACK + leitura de volta (KFactorManager). SOMBRA registra o que gravaria; LIGADO escreve. Uma vez por AutoMatch.
     */
    private fun autoRefineAfterEcuAutoMatch(payload: JSONObject) {
        val mode = try { com.omegas.prohub.autocal.AutoRefinePolicy.Mode.valueOf(settings.autoRefineMode) } catch (_: Exception) {
            com.omegas.prohub.autocal.AutoRefinePolicy.Mode.SHADOW
        }
        if (mode == com.omegas.prohub.autocal.AutoRefinePolicy.Mode.OFF) return
        val pre = preAutoMatchSnapshot
        val count = payload.optInt("nativeAutoMatchCount", -1)
        val epochKey = "${runtime.serialScheduler().currentSessionId()}:$count"
        autoRefineExecutor.execute {
            val record = JSONObject().put("mode", mode.name).put("epochKey", epochKey).put("autoMatchCount", count)
            try {
                val review = if (pre != null) engineReview(pre) else JSONObject().put("available", false).put("error", "SEM_LEITURA_ANTES_DO_AUTOMATCH")
                val idealArray = review.optJSONArray("idealFactorsRaw")
                val ideal = idealArray?.let { a -> IntArray(a.length()) { a.optInt(it) } }
                record.put("evidenceErrorBefore", review.opt("evidenceErrorBefore") ?: JSONObject.NULL)
                    .put("evidenceErrorAfter", review.opt("evidenceErrorAfter") ?: JSONObject.NULL)
                    .put("idealFactorsRaw", idealArray ?: JSONObject.NULL)
                if (kWriter.isBusy() || kFactor.isBusy()) {
                    sessionRecorder.record("d1_decision", "auto_refine", record.put("verdict", "SKIP").put("reason", "OCUPADO"), force = true)
                    return@execute
                }
                val read = kFactor.readCurve()
                val liveArray = read.optJSONArray("factorsRaw")
                val live = if (read.optBoolean("ok", false) && liveArray != null) IntArray(liveArray.length()) { liveArray.optInt(it) } else null
                record.put("liveFactorsRaw", liveArray ?: JSONObject.NULL)
                val mapMax = com.omegas.prohub.autocal.AutoRefinePolicy.maxMapCell(kWriter.cachedMapJson())
                val decision = com.omegas.prohub.autocal.AutoRefinePolicy.decide(
                    mode = mode,
                    available = review.optBoolean("available", false),
                    currentRaw = live,
                    idealRaw = ideal,
                    mapMaxCell = mapMax,
                    epochKey = epochKey,
                    lastEpochKey = autoRefineLastEpoch,
                )
                if (decision.reason != "MESMO_AUTOMATCH") autoRefineLastEpoch = epochKey
                val points = org.json.JSONArray()
                decision.points.forEach {
                    points.put(JSONObject().put("index", it.index).put("currentRaw", it.currentRaw).put("targetRaw", it.targetRaw).put("clamped", it.clamped))
                }
                record.put("verdict", decision.verdict.name).put("reason", decision.reason).put("points", points)
                    .put("floorRaw", decision.floorRaw).put("ceilingRaw", decision.ceilingRaw).put("mapMaxCell", mapMax ?: JSONObject.NULL)
                if (decision.verdict == com.omegas.prohub.autocal.AutoRefinePolicy.Verdict.WRITE && usb.connected) {
                    val batch = org.json.JSONArray()
                    decision.points.forEach { batch.put(JSONObject().put("index", it.index).put("currentRaw", it.currentRaw).put("targetRaw", it.targetRaw)) }
                    val started = kFactor.startBatchWrite(batch, "D1 · Refino automático após AutoMatch #$count")
                    record.put("writeStarted", started.optBoolean("ok", false)).put("writeStartResult", started)
                }
            } catch (error: Exception) {
                record.put("verdict", "ERRO").put("reason", (error.message ?: error.javaClass.simpleName).take(120))
            }
            sessionRecorder.record("d1_decision", "auto_refine", record, force = true)
        }
    }

    private fun recordCurveChangeFromSnapshot(snapshot: JSONObject) {
        try {
            val fields = snapshot.optJSONArray("fields") ?: return
            var mul: org.json.JSONArray? = null
            var axis: org.json.JSONArray? = null
            for (i in 0 until fields.length()) {
                val f = fields.optJSONObject(i) ?: continue
                if (f.optString("status") != "VALID") continue
                when (f.optString("key")) {
                    "MUL_ACT" -> mul = f.optJSONArray("rawValues")
                    "PETR_INJ_TBP" -> axis = f.optJSONArray("rawValues")
                }
            }
            val raw = mul?.takeIf { it.length() == 30 }?.toString() ?: return
            if (raw == lastCurveRaw) return
            val previous = lastCurveRaw
            lastCurveRaw = raw
            if (previous == null) return // primeira vez: o k_read_curve já registrou o ponto de partida
            val origin = if (System.currentTimeMillis() - lastAppCurveWriteMs < 20_000L) "APP_UI_MANUAL" else "ECU_NATIVE_OR_EXTERNAL"
            sessionRecorder.record(
                "k_curve_state", "autocal",
                JSONObject().put("origin", origin).put("source", "NATIVE_SNAPSHOT").put("factorsRaw", mul).put("axisRaw", axis ?: JSONObject.NULL)
                    .put("previousFactorsRaw", org.json.JSONArray(previous)).put("snapshotHash", snapshot.optString("snapshotHash"))
                    .put("engineReview", engineReview(snapshot)),
                force = true,
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Revisão do AutoMatch (aprender com cada um): o que o motor de equivalência do app considera o ideal para os buffers
     * deste snapshot, nó a nó, e o erro ponderado antes/depois. Comparar com a curva nova mostra quanto a ECU acertou.
     */
    private fun engineReview(snapshot: JSONObject): JSONObject = try {
        val analysis = com.omegas.prohub.autocal.AutoMatchSnapshotAnalysis.analyzeRefined(snapshot)
        val points = analysis.optJSONArray("points") ?: org.json.JSONArray()
        val ideal = org.json.JSONArray()
        for (i in 0 until points.length()) ideal.put(points.getJSONObject(i).optInt("calculatedRaw"))
        JSONObject()
            .put("available", analysis.optBoolean("available", false))
            .put("mode", analysis.optString("refinementMode"))
            .put("evidenceErrorBefore", analysis.opt("evidenceErrorBefore") ?: JSONObject.NULL)
            .put("evidenceErrorAfter", analysis.opt("evidenceErrorAfter") ?: JSONObject.NULL)
            .put("idealFactorsRaw", ideal)
    } catch (error: Exception) {
        JSONObject().put("available", false).put("error", (error.message ?: error.javaClass.simpleName).take(80))
    }

    /**
     * Leitura automática (só LER, nunca grava): 12 s depois de a sessão USB estabilizar e depois a cada 10 min com o núcleo ocioso.
     * Cada leitura (automática ou do botão/tela) vira evento da sessão com o gatilho (trigger). Mudança de curva pela ECU é
     * pega pelos snapshots do AutoMatch (recordCurveChangeFromSnapshot), sem custo extra na serial.
     */
    private fun autoReadCalibrationOnce() {
        val sid = runtime.serialScheduler().currentSessionId()
        if (sid <= 0L) return
        val now = System.currentTimeMillis()
        if (sid != autoReadSeenSessionId) {
            autoReadSeenSessionId = sid; autoReadSeenAtMs = now; autoReadAttempts = 0; autoReadLastOkAtMs = 0L; parametersRead = false
            return
        }
        val first = autoReadLastOkAtMs == 0L
        val due = if (first) now - autoReadSeenAtMs >= 12_000L + autoReadAttempts * 15_000L else now - autoReadLastOkAtMs >= 600_000L
        if (!due || kWriter.isBusy() || kFactor.isBusy() || (first && autoReadAttempts >= 4)) return
        if (first) autoReadAttempts += 1 else autoReadLastOkAtMs = now // periódica: não insiste em falha
        autoReadExecutor.execute {
            try {
                readTrigger = if (first) "AUTO_START" else "AUTO_PERIODIC"
                val curve = kFactor.readCurve()
                val map = kWriter.readFullMap()
                if (first && !parametersRead) {
                    // Só leitura: ANTI_STALLO, troca de combustível, corte e regras de aquisição da ECU, uma vez por conexão.
                    try {
                        sessionRecorder.record("param_snapshot", "ecu_params", parameterReader.readOnce("AUTO_START"), force = true)
                        parametersRead = true
                    } catch (error: Exception) {
                        log.add("WARN", "AUTO-READ", "Leitura de parâmetros falhou: ${error.message}")
                    }
                }
                if (first) {
                    if (curve.optBoolean("ok", false) && map.optBoolean("ok", false)) autoReadLastOkAtMs = System.currentTimeMillis()
                    else log.add("WARN", "AUTO-READ", "Leitura automática incompleta (curva=${curve.optBoolean("ok", false)} mapa=${map.optBoolean("ok", false)}); tentativa $autoReadAttempts")
                }
            } catch (error: Exception) {
                log.add("WARN", "AUTO-READ", "Leitura automática falhou: ${error.message}")
            } finally {
                readTrigger = "UI"
            }
        }
    }

    private fun autoCalTick() {
        if (stopping) return
        try {
            if (usb.connected && runtime.running && runtime.ready && telemetryStore.isValid() &&
                runtime.serialScheduler().currentSessionId() > 0L
            ) {
                nativeAutoCal.tick()
                autoReadCalibrationOnce()
            }
        } catch (error: Exception) {
            log.add("WARN", "AUTOCAL-NATIVE", "Refresh nativo: ${error.message}")
        }
    }

    private fun consumeEngineEvent(root: JSONObject) {
        val accepted = telemetryStore.updateFromEngineEvent(root) ?: return
        val live = root.optJSONObject("live") ?: root.optJSONObject("data") ?: JSONObject()
        val cngActive = live.optString("fuel").uppercase() == "GNV"
        if (cngActive) {
            consumptionTracker.update(
                timestampMs = accepted.optLong("timestamp", System.currentTimeMillis()),
                rawLevel = live.optInt("level_raw", -1),
            )
        }

        equivalence.accept(
            EquivalenceLedger.Frame(
                t = accepted.optLong("timestamp", System.currentTimeMillis()),
                fuel = live.optString("fuel").uppercase(),
                rpm = live.optDouble("rpm", 0.0),
                map = live.optDouble("load_bar", 0.0),
                petrolMs = live.optDouble("petrol_ms", 0.0),
                gasMs = live.optDouble("gas_ms_diagnostic", 0.0),
                // Gasolina com o motor frio (partida enriquecida) não vira referência; sem leitura de água = desconhecido.
                engineHot = if (live.has("water_c") && !live.isNull("water_c")) {
                    live.optDouble("water_c", Double.NaN).takeIf { it.isFinite() && it > -40.0 }
                        ?.let { it >= LearningTemperatureSettings.currentMinimumWaterC }
                } else null,
            ),
        )

        stallWatch.accept(
            StallWatch.Frame(
                t = accepted.optLong("timestamp", System.currentTimeMillis()),
                fuel = live.optString("fuel").uppercase(),
                rpm = live.optDouble("rpm", 0.0),
                map = live.optDouble("load_bar", 0.0),
                petrolMs = live.optDouble("petrol_ms", 0.0),
            ),
        )?.let { event ->
            sessionRecorder.record("engine_stall", "autocal", event, force = true)
            log.add("WARN", "REFINO", "Motor apagou no GNV em %.2f ms · MAP %.2f bar".format(event.optDouble("petrolMs"), event.optDouble("mapBar")))
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

    private fun stateChanged() {
        if (stopping || !::notifications.isInitialized) return
        updateOverlay()
        updateNotification()
    }

    private fun updateOverlay() {
        if (!::overlay.isInitialized || (!overlay.requestedEnabled() && !overlay.visible())) return
        val hub = status()
        overlay.update(
            TelemetryOverlayController.Snapshot(
                cell = "—",
                stft = null,
                petrolMs = hub.petrolMs.takeIf { it > 0.0 },
                rpm = hub.rpm.toDouble().takeIf { it > 0.0 },
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