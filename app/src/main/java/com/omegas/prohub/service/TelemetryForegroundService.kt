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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val scheduler = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-native-service").apply { isDaemon = true }
    }

    /** Leitura automática da curva e do Mapa K (só LER; gravar continua exigindo ação explícita). Fora da thread do monitor. */
    private val autoReadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-auto-read").apply { isDaemon = true }
    }
    private val calibrationReads = CalibrationReadEvents()
    /** Leitura automática ou D1 em curso: a leitura pedida pela tela espera o fim dela (evento) em vez de dar "ocupado". */
    private val autoReadGate = AutoReadGate()
    /** Borda ocupado → livre do KWriteManager/KFactorManager: retoma reinício do núcleo e D1 que esperavam a serial. */
    private val calibrationIdle = BusyEdge()
    /** Sonda do AutoCal e `full_snapshot` por contagem de quadros válidos (sem relógio; conta em ServiceEventPolicy). */
    private val autoCalFrames = FrameCountTrigger(ServiceEventPolicy.AUTOCAL_TICK_FRAMES)
    private val snapshotFrames = FrameCountTrigger(ServiceEventPolicy.FULL_SNAPSHOT_FRAMES)
    /** Reinícios automáticos do núcleo sem quadro válido entre eles: limitados por contagem, por sessão USB. */
    private val restartBudget = EngineRestartBudget()
    @Volatile private var restartLimitLogged = false
    /**
     * D1 (refino depois de cada AutoMatch da ECU): roda no MESMO executor da leitura automática, então nunca disputa a
     * serial com ela; se perder para uma operação da tela, fica pendente e volta na borda ocupado → livre (nunca grava).
     */
    @Volatile private var preAutoMatchSnapshot: JSONObject? = null
    @Volatile private var autoRefineLastEpoch: String? = null
    private val autoRefineRetry = AutoRefineRetry<JSONObject>()
    /** Sugestão do Mapa K memorizada pela revisão do ledger + sessão + versão do cache do mapa (sem reler o disco à toa). */
    @Volatile private var mapSuggestionMemo: Pair<String, JSONObject>? = null
    /** Quem pediu a leitura em curso: UI (botão/abertura de tela), AUTO_START ou evento confirmado. Vai junto no evento da sessão. */
    @Volatile private var readTrigger = "UI"
    /**
     * Última curva vista (leitura da ECU ou readback de gravação manual). A gravação manual já é registrada com
     * origem APP_UI_MANUAL no próprio lote; mudança vista depois no snapshot é sempre da ECU ou externa.
     */
    @Volatile private var lastCurveRaw: String? = null

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
    /** Ferramentas > Avançado: último param_snapshot da sessão e escrita manual do anti-apagão (só por toque). */
    val advancedParams = com.omegas.prohub.autocal.AdvancedParamsStore()
    lateinit var antiStalloWriter: com.omegas.prohub.autocal.AntiStalloFlagWriter
    lateinit var antiStalloParamsWriter: com.omegas.prohub.autocal.AntiStalloParamsWriter
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
    private val frameWorkPending = AtomicBoolean(false)
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
            onBusyChanged = { onCalibrationBusyChanged() },
            // Chamado ao fim de TODA leitura completa e de toda gravação confirmada do mapa: nunca pede releitura
            // (a gravação já tem ACK + leitura de volta; pedir aqui fazia a leitura automática se repetir sem fim).
            onConfirmedWrite = {
                executeOnScheduler {
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
                mapSuggestionMemo = null
            },
        )
        kFactor = KFactorManager(
            paths = paths,
            serial = recordingSerial,
            log = log,
            onBusyChanged = { onCalibrationBusyChanged() },
            onCurveRead = { payload ->
                lastCurveRaw = payload.optJSONArray("factorsRaw")?.toString()
                sessionRecorder.record("k_read_curve", "k_factor", payload.put("trigger", readTrigger), force = true)
            },
            onConfirmedBatch = { payload ->
                sessionRecorder.record("k_factor_batch_confirmed", "k_factor", JSONObject(payload.toString()).put("origin", "APP_UI_MANUAL"), force = true)
                // Sem releitura automática: o lote já terminou com ACK + leitura de volta e atualizou o cache da curva.
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
        antiStalloWriter = com.omegas.prohub.autocal.AntiStalloFlagWriter(
            serial = recordingSerial,
            record = { type, data -> sessionRecorder.record(type, "anti_stallo_flag", data, force = true) },
            onFlagRead = { session, words, trigger ->
                advancedParams.replaceVector(session, "FLAG_CONF3", com.omegas.prohub.autocal.AntiStalloFlagCore.ADDRESS, words, trigger)
            },
            // Calibração, mapa K, curva K ou sonda de buffer na serial: o anti-apagão não disputa a porta (recusa sem enviar).
            busy = { kWriter.isBusy() || kFactor.isBusy() || bufferProbe.statusJson().let { runCatching { JSONObject(it).optBoolean("busy", false) }.getOrDefault(false) } },
            onFlagUnknown = { session, trigger ->
                advancedParams.markUnknown(session, "FLAG_CONF3", com.omegas.prohub.autocal.AntiStalloFlagCore.ADDRESS, trigger)
            },
        )
        // Anti-apagão, os 5 números (ANTI_STALLO). Mesma regra do escritor da caixa: só por toque do dono, prévia + 2º toque + leitura de volta.
        antiStalloParamsWriter = com.omegas.prohub.autocal.AntiStalloParamsWriter(
            serial = recordingSerial,
            record = { type, data -> sessionRecorder.record(type, "anti_stallo_params", data, force = true) },
            onRead = { session, values, trigger ->
                advancedParams.replaceVector(session, "ANTI_STALLO", com.omegas.prohub.autocal.AntiStalloParamsCore.ADDRESS, values, trigger)
            },
            busy = { kWriter.isBusy() || kFactor.isBusy() || bufferProbe.statusJson().let { runCatching { JSONObject(it).optBoolean("busy", false) }.getOrDefault(false) } },
            onUnknown = { session, trigger ->
                advancedParams.markUnknown(session, "ANTI_STALLO", com.omegas.prohub.autocal.AntiStalloParamsCore.ADDRESS, trigger)
            },
        )
        nativeAutoCal = NativeAutoCalMonitor(
            serial = recordingSerial,
            calibrationBusy = { kWriter.isBusy() || kFactor.isBusy() },
            onFreshSnapshot = { snapshot ->
                runtime.importNativeAutoCalSnapshot(snapshot)
                sessionRecorder.record("autocal_native_snapshot", "autocal", snapshot, force = true)
                recordCurveChangeFromSnapshot(snapshot)
                if (refinement.evaluate(refinementDriving())) stateChanged()
                observeRefinement()
            },
            onNativeCalibrationObserved = { payload ->
                requestCalibrationRead("ECU_CALIBRATION_CHANGED")
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
                requestCalibrationRead("AUTO_MATCH")
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
        scheduler.execute { healthTick() }
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
        autoReadGate.wakeAll()
        try { equivalence.flush() } catch (_: Exception) {}
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
        try { antiStalloWriter.close() } catch (_: Exception) {}
        try { antiStalloParamsWriter.close() } catch (_: Exception) {}
        autoReadExecutor.shutdownNow()
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
    /** Leitura pedida pela tela: reserva a serial, espera a leitura automática em curso terminar (evento) e só então lê. */
    fun readKMap(): String = awaitAutoRead { readKMapNow() }
    @Synchronized private fun readKMapNow(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readFullMap().toString()
    @Synchronized fun recoverKInsertionState(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.recoverInsertionState().toString()
    fun previewKMapCell(row: Int, column: Int, targetValue: Int): String = runtime.previewKWrite(row, column, targetValue).toString()
    fun kWriteStatusJson(): String = kWriter.statusJson()
    fun kWriteHistoryJson(): String = kWriter.historyJson()

    fun readKFactorCurve(): String = awaitAutoRead { readKFactorCurveNow() }
    @Synchronized private fun readKFactorCurveNow(): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.readCurve().toString()

    /**
     * Espera o FIM da leitura automática/D1 (sem prazo) e roda [read]; o serviço parando libera a espera. Nunca na thread
     * da tela. Da espera até o fim de [read] a serial fica reservada: um quadro válido não abre nova leitura automática.
     */
    private fun <T> awaitAutoRead(read: () -> T): T = autoReadGate.forUi({ stopping }, read)

    /** Sessão serial vigente (0 = desconectado). A sugestão do Mapa K só vale com o mapa lido NESTA sessão. */
    fun currentEcuSessionId(): Long = if (usb.connected) runtime.serialScheduler().currentSessionId() else 0L

    /**
     * Sugestão do Mapa K para a tela (só leitura). Memorizada pela revisão do ledger, pela sessão e pela versão do cache do
     * mapa (tamanho + modificação do arquivo, sem lê-lo); qualquer exceção vira null sem derrubar a Equivalência.
     */
    fun mapSuggestionJson(): Any {
        return try {
            val sid = currentEcuSessionId()
            val mapFile = File(paths.runtimeRoot, "k_map_cache.json")
            val key = "${equivalence.revision()}|$sid|${mapFile.length()}|${mapFile.lastModified()}"
            mapSuggestionMemo?.takeIf { it.first == key }?.let { return JSONObject(it.second.toString()) }
            val decided = com.omegas.prohub.autocal.MapSuggestionPolicy.decide(equivalence.attribution(), kWriter.cachedMapJson(), sid)
            mapSuggestionMemo = key to decided
            JSONObject(decided.toString())
        } catch (error: Exception) {
            log.add("WARN", "REFINO", "Sugestão do Mapa K indisponível: ${error.message ?: error.javaClass.simpleName}")
            JSONObject.NULL
        }
    }
    fun kFactorStatusJson(): String = kFactor.statusJson()
    /** Conferência em segundo plano da última gravação K (Curva ou Mapa): a que está ativa/falhou tem prioridade. */
    fun kWriteVerifyJson(): String {
        val a = org.json.JSONObject(kWriter.writeVerifyJson()); val b = org.json.JSONObject(kFactor.writeVerifyJson())
        fun rank(o: org.json.JSONObject) = when (o.optString("phase")) { "failed" -> 3; "fixing" -> 2; "checking" -> 1; else -> 0 }
        return (if (rank(a) >= rank(b)) a else b).toString()
    }
    fun startBufferWriteProbe(): String = bufferProbe.start().toString()
    fun bufferWriteProbeStatusJson(): String = bufferProbe.statusJson()
    // Ferramentas > Avançado. Escrita só por toque do dono (prévia + confirmação); nada aqui dispara sozinho.
    fun advancedParamsJson(): String = advancedParams.json(
        if (usb.connected) runtime.serialScheduler().currentSessionId() else 0L,
    ).put("antiStallSuggestion", sessionRecorder.antiStallSuggestion()).toString()
    fun prepareAntiStalloFlag(enable: Boolean): String = antiStalloWriter.prepare(enable).toString()
    fun confirmAntiStalloFlag(token: String): String = antiStalloWriter.confirm(token).toString()
    fun cancelAntiStalloFlag(): String = antiStalloWriter.cancel().toString()
    fun antiStalloFlagStatusJson(): String = antiStalloWriter.statusJson()
    // Anti-apagão, os 5 números: mesmo fluxo (prévia → 2º toque → leitura de volta → desfazer).
    fun prepareAntiStalloValues(changesJson: String): String = antiStalloParamsWriter.prepare(changesJson).toString()
    fun confirmAntiStalloValues(token: String): String = antiStalloParamsWriter.confirm(token).toString()
    fun cancelAntiStalloValues(): String = antiStalloParamsWriter.cancel().toString()
    fun antiStalloValuesStatusJson(): String = antiStalloParamsWriter.statusJson()
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
        val queued = executeOnScheduler {
            handleUsbTransition()
            healthTick()
            stateChanged()
        }
        if (!queued) stateChanged()
    }

    /** Fila do serviço sem RejectedExecutionException no encerramento (onDestroy desliga o executor antes do núcleo). */
    private fun executeOnScheduler(task: () -> Unit): Boolean {
        if (stopping) return false
        return try {
            scheduler.execute(Runnable { task() })
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            false
        }
    }

    /**
     * KWriteManager/KFactorManager mudaram de ocupado/livre. Na borda ocupado → livre (evento, não prazo) roda o healthTick
     * (o reinício do núcleo que ficou barrado pela calibração) e o D1 que perdeu a serial para outra operação.
     */
    private fun onCalibrationBusyChanged() {
        stateChanged()
        if (stopping || !::kWriter.isInitialized || !::kFactor.isInitialized) return
        try {
            if (!calibrationIdle.observe(kWriter.isBusy() || kFactor.isBusy())) return
            executeOnScheduler { healthTick() }
            autoRefineRetry.take()?.let { pending -> autoRefineAfterEcuAutoMatch(pending) }
        } catch (error: Exception) {
            log.add("WARN", "SERVICE", "Borda livre da calibração: ${error.message}")
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
            autoCalFrames.reset()
            snapshotFrames.reset()
            mapSuggestionMemo = null
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
            // Avaliação disparada pelo estado recebido; não há relógio de recuperação do serviço.
            if (refinement.evaluate(refinementDriving())) stateChanged()
            observeRefinement()
            handleUsbTransition()
            if (!usb.connected && settings.autoReconnectUsb && !monitoringPausedByUser && !enginePausedByUser && usb.hasCompatibleDevice()) {
                connectUsb()
            }
            // Calibração ocupada: o reinício espera a borda ocupado → livre (onCalibrationBusyChanged chama de novo).
            if (usb.connected && settings.autoStartEngine && !enginePausedByUser &&
                !runtime.running && !runtime.stuck && !kWriter.isBusy() && !kFactor.isBusy()
            ) {
                // Núcleo que cai sem nunca entregar quadro válido: no máximo N reinícios por sessão USB (contagem, não prazo).
                if (restartBudget.tryConsume(usb.connectionSessionId)) {
                    engineRestarts += 1
                    startEngine("recuperação automática do núcleo")
                } else if (!restartLimitLogged) {
                    restartLimitLogged = true
                    log.add("WARN", "ECU-NATIVE", "Núcleo reiniciado ${ServiceEventPolicy.MAX_ENGINE_RESTARTS_WITHOUT_FRAME} vezes sem quadro válido; aguardando reconexão ou reinício manual")
                }
            }
            if (!usb.connected && runtime.running) runtime.stop(2)
            // Transição de estado: retrato completo na gravação (além do retrato a cada N quadros válidos).
            recordFullSnapshotIfRecording()
            renewWakeLockIfNeeded()
            updateOverlay()
            updateNotification()
        } catch (error: Exception) {
            healthFailures += 1
            log.add("WARN", "SERVICE", "Monitor nativo: ${error.message}")
        }
    }

    private fun recordFullSnapshotIfRecording() {
        if (sessionRecorder.statusObject().optBoolean("recording")) {
            sessionRecorder.record(
                "full_snapshot",
                "native",
                try { JSONObject(fullEngineSnapshotJson()) } catch (_: Exception) { JSONObject() },
            )
        }
    }

    /** Dono (2026-10-18): o app NÃO grava sozinho. "LIGADO" não existe mais; um valor antigo guardado vira SOMBRA. */
    fun autoRefineModeName(): String = if (settings.autoRefineMode == "OFF") "OFF" else "SHADOW"

    fun setAutoRefineModeName(mode: String): String {
        // Só OFF e SHADOW existem; "ON" (gravar sozinho) não é modo: valueOf recusa.
        val parsed = try { com.omegas.prohub.autocal.AutoRefinePolicy.Mode.valueOf(mode) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Modo inválido").toString()
        }
        settings.autoRefineMode = parsed.name
        sessionRecorder.record("settings_changed", "auto_refine", JSONObject().put("autoRefineMode", parsed.name), force = true)
        return JSONObject().put("ok", true).put("mode", parsed.name).toString()
    }

    /**
     * D1 (decisão do dono, 2026-10-16): depois de cada AutoMatch da ECU, calcular a sugestão de Curva K dentro das travas absolutas
     * (AutoRefinePolicy). Usa a leitura de ANTES do AutoMatch (a ECU zera o GNV logo depois) e lê a curva viva. Só registra o que sugeriria; a gravação é sempre manual (dono, 2026-10-18). Uma vez por AutoMatch.
     */
    private fun autoRefineAfterEcuAutoMatch(payload: JSONObject) {
        // Nunca grava sozinho (dono, 2026-10-18): qualquer valor guardado que não seja OFF roda como SOMBRA.
        val mode = if (settings.autoRefineMode == "OFF") com.omegas.prohub.autocal.AutoRefinePolicy.Mode.OFF
        else com.omegas.prohub.autocal.AutoRefinePolicy.Mode.SHADOW
        if (mode == com.omegas.prohub.autocal.AutoRefinePolicy.Mode.OFF) return
        val pre = preAutoMatchSnapshot
        val count = payload.optInt("nativeAutoMatchCount", -1)
        val epochKey = "${runtime.serialScheduler().currentSessionId()}:$count"
        // Mesmo executor da leitura automática: o D1 e a releitura do AutoMatch nunca disputam a serial entre si; a tela
        // espera os dois terminarem (autoReadGate).
        autoReadGate.enter()
        try {
            autoReadExecutor.execute {
                try { runAutoRefine(payload, mode, pre, count, epochKey) } finally { autoReadGate.exit() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            autoReadGate.exit()
        }
    }

    /**
     * O D1 perdeu a serial (outra operação ocupada ou leitura falhou): tenta de novo na próxima borda ocupado → livre. Se
     * a serial já está livre (a borda passou antes de o pendente ser guardado, ex.: o próprio readCurve do D1), roda já,
     * na fila do mesmo executor (depois deste registro). Até MAX_AUTO_REFINE_RETRIES por AutoMatch: sem laço.
     */
    private fun retryAutoRefineOnIdle(payload: JSONObject, epochKey: String): Boolean {
        val offer = autoRefineRetry.offer(payload, epochKey) { kWriter.isBusy() || kFactor.isBusy() }
        offer.runNow?.let { pending -> autoRefineAfterEcuAutoMatch(pending) }
        return offer.accepted
    }

    private fun runAutoRefine(
        payload: JSONObject,
        mode: com.omegas.prohub.autocal.AutoRefinePolicy.Mode,
        pre: JSONObject?,
        count: Int,
        epochKey: String,
    ) {
        run {
            val record = JSONObject().put("mode", mode.name).put("epochKey", epochKey).put("autoMatchCount", count)
            try {
                val review = if (pre != null) engineReview(pre) else JSONObject().put("available", false).put("error", "SEM_LEITURA_ANTES_DO_AUTOMATCH")
                val idealArray = review.optJSONArray("idealFactorsRaw")
                val ideal = idealArray?.let { a -> IntArray(a.length()) { a.optInt(it) } }
                record.put("evidenceErrorBefore", review.opt("evidenceErrorBefore") ?: JSONObject.NULL)
                    .put("evidenceErrorAfter", review.opt("evidenceErrorAfter") ?: JSONObject.NULL)
                    .put("idealFactorsRaw", idealArray ?: JSONObject.NULL)
                if (kWriter.isBusy() || kFactor.isBusy()) {
                    val retry = retryAutoRefineOnIdle(payload, epochKey)
                    sessionRecorder.record("d1_decision", "auto_refine", record.put("verdict", "SKIP").put("reason", "OCUPADO").put("retryOnIdle", retry), force = true)
                    return
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
                // Curva viva não lida (serial ocupada por outra operação): a época NÃO é gasta; nova tentativa na borda livre.
                if (com.omegas.prohub.autocal.AutoRefinePolicy.consumesEpoch(decision)) {
                    autoRefineLastEpoch = epochKey
                } else if (live == null && decision.reason == "CURVA_INVALIDA") {
                    record.put("retryOnIdle", retryAutoRefineOnIdle(payload, epochKey))
                }
                val points = org.json.JSONArray()
                decision.points.forEach {
                    points.put(JSONObject().put("index", it.index).put("currentRaw", it.currentRaw).put("targetRaw", it.targetRaw).put("clamped", it.clamped))
                }
                record.put("verdict", decision.verdict.name).put("reason", decision.reason).put("points", points)
                    .put("floorRaw", decision.floorRaw).put("ceilingRaw", decision.ceilingRaw).put("mapMaxCell", mapMax ?: JSONObject.NULL)
                // Sugestão do Mapa K por faixa de RPM no mesmo evento (só registra; gravar é sempre manual).
                record.put("mapSuggestion", mapSuggestionJson())
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
            val previous = lastCurveRaw
            when (CurveSnapshotChange.of(raw, previous, curveBusy = kFactor.isBusy())) {
                CurveSnapshotChange.SAME -> return
                // Leitura ou lote do próprio app em curso (o lote só atualiza lastCurveRaw em onConfirmedBatch): não é da ECU.
                CurveSnapshotChange.APP_OPERATION -> return
                // Primeira vez: o k_read_curve já registrou o ponto de partida.
                CurveSnapshotChange.BASELINE -> { lastCurveRaw = raw; return }
                CurveSnapshotChange.ECU_CHANGE -> Unit
            }
            if (previous == null) return
            lastCurveRaw = raw
            // A curva gravada pelo app já virou lastCurveRaw no próprio lote confirmado; diferença aqui é da ECU ou externa.
            val origin = "ECU_NATIVE_OR_EXTERNAL"
            requestCalibrationRead("ECU_CALIBRATION_CHANGED")
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

    private fun requestCalibrationRead(trigger: String) {
        calibrationReads.request(runtime.serialScheduler().currentSessionId(), trigger)
    }

    /** Só o primeiro quadro válido da sessão ou um evento confirmado pode pedir nova leitura. */
    private fun autoReadCalibrationOnce() {
        if (!usb.connected || !runtime.running || !runtime.ready || !telemetryStore.isValid()) return
        val ticket = calibrationReads.next(
            // Leitura da tela reservada (esperando ou em curso) conta como ocupado: o pedido fica para o próximo quadro.
            runtime.serialScheduler().currentSessionId(), kWriter.isBusy() || kFactor.isBusy() || autoReadGate.uiReadPending(),
        ) ?: return
        autoReadGate.enter()
        try {
            launchAutoRead(ticket)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            calibrationReads.complete(ticket, false)
            autoReadGate.exit()
        }
    }

    private fun launchAutoRead(ticket: CalibrationReadEvents.Ticket) {
        autoReadExecutor.execute {
            var ok = false
            fun sameSession() = usb.connected && runtime.serialScheduler().currentSessionId() == ticket.session
            try {
                if (!sameSession()) return@execute
                readTrigger = ticket.trigger
                val curve = kFactor.readCurve()
                if (!sameSession()) return@execute
                val map = kWriter.readFullMap()
                if (!sameSession()) return@execute
                if (ticket.parameters) {
                    // Uma tentativa por conexão; falha não vira repetição periódica.
                    try {
                        val parameters = parameterReader.readOnce("AUTO_START")
                        if (sameSession()) {
                            sessionRecorder.record("param_snapshot", "ecu_params", parameters, force = true)
                            advancedParams.update(ticket.session, parameters)
                        }
                    } catch (error: Exception) {
                        log.add("WARN", "AUTO-READ", "Leitura de parâmetros falhou: ${error.message}")
                    }
                }
                ok = sameSession() && curve.optBoolean("ok", false) && map.optBoolean("ok", false)
                if (!ok) log.add("WARN", "AUTO-READ", "Leitura incompleta; nova tentativa no próximo quadro válido")
            } catch (error: Exception) {
                log.add("WARN", "AUTO-READ", "Leitura automática falhou: ${error.message}")
            } finally {
                readTrigger = "UI"
                calibrationReads.complete(ticket, ok)
                autoReadGate.exit()
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
            }
        } catch (error: Exception) {
            log.add("WARN", "AUTOCAL-NATIVE", "Refresh nativo: ${error.message}")
        }
    }

    private fun consumeEngineEvent(root: JSONObject) {
        val accepted = telemetryStore.updateFromEngineEvent(root) ?: return
        // Quadro válido: o núcleo voltou a entregar dados, o saldo de reinícios automáticos é devolvido.
        if (usb.connected) restartBudget.onValidFrame(usb.connectionSessionId)
        restartLimitLogged = false
        autoReadCalibrationOnce()
        // Sonda do AutoCal a cada N quadros válidos (não a cada quadro: ela é uma transação serial) e retrato da gravação
        // a cada M quadros; contagem, sem relógio.
        val autoCalDue = autoCalFrames.onFrame()
        val snapshotDue = snapshotFrames.onFrame()
        if ((autoCalDue || snapshotDue) && !stopping && frameWorkPending.compareAndSet(false, true)) {
            val queued = executeOnScheduler {
                try {
                    if (autoCalDue) {
                        autoCalTick()
                        renewWakeLockIfNeeded()
                    }
                    if (snapshotDue) recordFullSnapshotIfRecording()
                } finally {
                    frameWorkPending.set(false)
                }
            }
            if (!queued) frameWorkPending.set(false)
        }
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
        executeOnScheduler { healthTick() }
        stateChanged()
    }

    private fun stateChanged() {
        if (stopping || !::notifications.isInitialized) return
        updateOverlay()
        updateNotification()
    }

    private fun updateOverlay() {
        if (!::overlay.isInitialized || (!overlay.requestedEnabled() && !overlay.visible())) return
        // A cada quadro: só RPM e injeção, direto do estado (status() montaria o HubStatus inteiro para usar dois números).
        val (rpm, petrolMs) = telemetryStore.rpmAndPetrolMs()
        overlay.update(
            TelemetryOverlayController.Snapshot(
                cell = "—",
                stft = null,
                petrolMs = petrolMs.takeIf { it > 0.0 },
                rpm = rpm.toDouble().takeIf { it > 0.0 },
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
