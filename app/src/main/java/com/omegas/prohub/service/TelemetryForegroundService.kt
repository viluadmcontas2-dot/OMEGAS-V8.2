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
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.omegas.prohub.autocal.EcuPetrolReference
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EvidenceInvalidation
import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.RefinementJournal
import com.omegas.prohub.autocal.StallWatch
import com.omegas.prohub.equivalence.EquivalenceRuntime
import androidx.core.app.ServiceCompat
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.KFactorManager
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.autocal.NativeAutoCalMonitor
import com.omegas.prohub.diagnostics.DocumentsSessionMirror
import com.omegas.prohub.diagnostics.LegacyDataSweeper
import com.omegas.prohub.diagnostics.SessionRecorder
import com.omegas.prohub.ecu.NativeRuntimeManager
import com.omegas.prohub.gps.GpsTelemetryManager
import com.omegas.prohub.ecu.LearningTemperatureSettings
import com.omegas.prohub.ecu.LearningToleranceSettings
import com.omegas.prohub.link.OmegasLinkManager
import com.omegas.prohub.model.HubStatus
import com.omegas.prohub.network.LanPanelServer
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.storage.DataArchiveManager
import com.omegas.prohub.runtime.RuntimeSnapshotBus
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
    /**
     * Faixa única de análise (refino, diário, full_snapshot, overlay): thread própria, para o trabalho pesado
     * do `healthTick` nunca atrasar o `autoCalTick` que divide o `scheduler` com ações de serviço.
     */
    private val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-native-analysis").apply { isDaemon = true }
    }
    private val analysisLane = AnalysisLane(analysisExecutor)

    /**
     * Revisões por tipo de dado (live/evidence/tables/session) que a UI consulta antes de reler qualquer coisa.
     * `live` espelha a sequência do TelemetryStateStore; as demais só sobem quando o dado realmente mudou.
     */
    val revisions = RuntimeSnapshotBus()
    @Volatile private var revisionListener: ((RuntimeSnapshotBus.Kind, Long) -> Unit)? = null

    /** A Activity registra (e limpa com null) o empurrão `OmegasOnRevision`; o poll de segurança da UI continua. */
    fun setRevisionListener(listener: ((RuntimeSnapshotBus.Kind, Long) -> Unit)?) {
        revisionListener = listener
    }

    private fun publishRevision(kind: RuntimeSnapshotBus.Kind) {
        val revision = revisions.bump(kind)
        try { revisionListener?.invoke(kind, revision) } catch (_: Exception) {}
    }

    /** `{ok, revisions:{live,evidence,tables,session}}`; `live` = sequência atual da telemetria. */
    fun revisionsObject(): JSONObject = revisions.revisionsJson(
        liveRevision = if (::telemetryStore.isInitialized) telemetryStore.sequenceNow() else 0L,
    ).also { root ->
        if (::nativeAutoCal.isInitialized) {
            root.getJSONObject("revisions").put("tables", maxOf(revisions.revision(RuntimeSnapshotBus.Kind.TABLES), nativeAutoCal.tablesRevision()))
        }
    }

    fun revisionsJson(): String = revisionsObject().toString()

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
    lateinit var link: OmegasLinkManager
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
    lateinit var equivalencePhases: EquivalencePhases
        private set
    /** Onde o motor apagou no GNV (desaceleração/embreagem): só observa, mostra no Refino. */
    lateinit var stallWatch: StallWatch
        private set
    /** Cérebro único (Referência, Curvas Próprias, índice, próxima ação): só observa, nunca fala com a ECU. */
    lateinit var equivalenceRuntime: EquivalenceRuntime
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
    /** O que a limpeza única do aprendizado antigo apagou; vai para os metadados da primeira sessão gravada. */
    @Volatile private var legacySweepRemoved: List<String> = emptyList()

    /**
     * Só para a evidência de render no emulador: congela o piloto/diário do Refino para o teste
     * conduzir o estado (replay real) sem corrida com o relógio do serviço. Nunca é ligado em produção.
     */
    @Volatile var refinementFrozenForRender = false

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
        val loadedExperiment = refinementJournal.json().optJSONObject("latest")
        lastJournalDecisionSignature = journalDecisionSignature(loadedExperiment)
        // Só o experimento já fechado no disco pertence ao histórico de outra sessão.
        // A primeira checagem pode chegar depois que um experimento novo já terminou.
        lastVerdictRecordedId = loadedExperiment?.takeIf { it.optString("status") != "VERIFICANDO" }
            ?.optString("id").orEmpty()
        verdictBaselineSet = true
        if (lastVerdictRecordedId.isNotBlank()) recordedVerdictIds.add(lastVerdictRecordedId)
        equivalencePhases = EquivalencePhases(File(paths.runtimeRoot, "refinement_autopilot.json"), durationClock = SystemClock::elapsedRealtime)
        stallWatch = StallWatch(File(paths.runtimeRoot, "stall_watch.json"))
        equivalenceRuntime = EquivalenceRuntime(paths.runtimeRoot)
        val documentsMirror = DocumentsSessionMirror(this)
        sessionRecorder = SessionRecorder(paths, settings, documentsMirror)
        refinementJournal.setDecisionListener(::recordJournalTransition)
        journalTransitionsObserved = true
        sessionRecorder.recoverDocumentsMirrorAsync()
        log.setListener { item ->
            sessionRecorder.record("app_log", "native", item, force = true)
        }
        // Limpeza única dos arquivos do aprendizado antigo, fora da thread principal.
        scheduler.execute {
            legacySweepRemoved = LegacyDataSweeper.sweep(paths.runtimeRoot, paths.runtimeBackupsRoot)
            if (legacySweepRemoved.isNotEmpty()) {
                log.add("INFO", "LIMPEZA", "Dados do aprendizado antigo removidos: " + legacySweepRemoved.joinToString())
            }
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
                    if (::link.isInitialized) link.markDataChanged("mapa K confirmado")
                }
            },
            onConfirmedBatch = { payload ->
                // Mapa K mudou o gás: o GNV medido antes não vale mais. Invalida PRIMEIRO; gravar a sessão
                // (que pode falhar por disco cheio) só depois.
                EvidenceInvalidation.run(
                    invalidate = listOf(
                        "resetGas" to { equivalence.resetGas("MAPA_K_GRAVADO") },
                        "cerebro" to { equivalenceRuntime.onGasReset("MAPA_K_GRAVADO", equivalencePhases) },
                        "journal" to { refinementJournal.interrupt("MAPA_K_GRAVADO") },
                    ),
                    record = { sessionRecorder.record("k_batch_confirmed", "map_k", payload, force = true) },
                    warn = { log.add("WARN", "EVIDENCIA", it) },
                )
                link.markDataChanged("escrita K confirmada")
            },
        )
        kFactor = KFactorManager(
            paths = paths,
            serial = runtime.serialScheduler(),
            log = log,
            onBusyChanged = { stateChanged() },
            onConfirmedBatch = { payload ->
                recordCurveExperiment(payload)
                link.markDataChanged("escrita K factor confirmada")
            },
            onFailedBatch = { payload -> recordFailedCurveWrite(payload) },
            publishManualBackup = { file -> documentsMirror.publishRootFile(file) },
        )
        nativeAutoCal = NativeAutoCalMonitor(
            serial = runtime.serialScheduler(),
            calibrationBusy = { kWriter.isBusy() || kFactor.isBusy() },
            onFreshSnapshot = { snapshot ->
                sessionRecorder.record("autocal_native_snapshot", "autocal", snapshot, force = true)
            },
            onNativeCalibrationObserved = { payload ->
                sessionRecorder.record(
                    "autocal_native_calibration_epoch",
                    "autocal",
                    payload,
                    force = true,
                )
                // A ECU trocou a Curva K sozinha: GNV antigo descartado, verificação interrompida.
                equivalence.resetGas("AUTOMATCH_NATIVO")
                equivalenceRuntime.onGasReset("AUTOMATCH_NATIVO", equivalencePhases)
                refinementJournal.interrupt("AUTOMATCH_NATIVO")
                publishRevision(RuntimeSnapshotBus.Kind.EVIDENCE)
                if (::link.isInitialized) link.markDataChanged("AutoCal nativo alterou Curva K")
            },
            onNativeAutoMatchObserved = { payload ->
                sessionRecorder.record(
                    "autocal_native_automatch_epoch",
                    "autocal",
                    payload,
                    force = true,
                )
                publishRevision(RuntimeSnapshotBus.Kind.EVIDENCE)
            },
            // Overlay/notificação nunca rodam na thread do autoCalTick: vão para a faixa de análise (coalescente).
            onStateChanged = { analysisLane.submit { stateChanged() } },
            onTablesChanged = { publishRevision(RuntimeSnapshotBus.Kind.TABLES) },
        )
        link = OmegasLinkManager(
            settings = settings,
            log = log,
            usbConnected = { usb.connected },
            coreTelemetry = ::coreTelemetryForLink,
            exportHistory = { kWriter.exportHistoryComponent(settings.deviceId) },
            mergeHistory = { payload -> kWriter.mergeHistoryComponent(payload) },
            onStateChanged = ::stateChanged,
            exportAutoCalContext = {
                JSONObject()
                    .put("schema", "landi-autocal-18x30-v2")
                    .put("source", "ECU_NATIVE")
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
        if (settings.autoConnectUsb && usb.hasCompatibleDevice()) usb.connect()
        healthTask = scheduler.scheduleWithFixedDelay(::healthTick, 200L, 3000L, TimeUnit.MILLISECONDS)
        // Cadência curta (100 ms): cada tick lê no máximo UM grupo AutoCal (SlotArbiter) ou volta de imediato.
        autoCalTask = scheduler.scheduleWithFixedDelay(::autoCalTick, 1_000L, 100L, TimeUnit.MILLISECONDS)
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
        refinementJournal.setDecisionListener(null)
        journalTransitionsObserved = false
        try { equivalence.flush() } catch (_: Exception) {}
        try { equivalenceRuntime.flush() } catch (_: Exception) {}
        healthTask?.cancel(true)
        autoCalTask?.cancel(true)
        scheduler.shutdownNow()
        analysisExecutor.shutdownNow()
        try { runtime.stop(3) } catch (_: Exception) {}
        try { runtime.endUsbSession("SERVICE_DESTROYED") } catch (_: Exception) {}
        try { usb.disconnect() } catch (_: Exception) {}
        try { link.close() } catch (_: Exception) {}
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
            .put("link_status", try { JSONObject(link.statusJson()) } catch (_: Exception) { JSONObject() })
            .put("consumption", consumptionTracker.buildTelemetryJson(settings.gnvCylinderCapacityM3.toFloat()))
            .put("native_updated_at", System.currentTimeMillis())
            .put("telemetry_age_ms", telemetryStore.ageMs().let { if (it == Long.MAX_VALUE) -1L else it })
        return root.toString()
    }

    fun engineMetricsJson(): String = runtime.metricsJson()
    fun engineSelfTestJson(): String = runtime.selfTestJson()
    fun protocolLabJson(): String = runtime.protocolJson()

    @Synchronized fun readKCell(row: Int, column: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readCell(row, column).toString()
    @Synchronized fun readKLine(row: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readLine(row).toString()
    @Synchronized fun readKMap(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readFullMap().toString()
    @Synchronized fun recoverKInsertionState(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.recoverInsertionState().toString()
    fun kWriteStatusJson(): String = kWriter.statusJson()
    fun kWriteHistoryJson(): String = kWriter.historyJson()

    @Synchronized fun readKFactorCurve(): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.readCurve().toString()
    fun kFactorStatusJson(): String = kFactor.statusJson()
    fun kFactorHistoryJson(): String = kFactor.historyJson()

    @Synchronized fun saveKFactorBackup(label: String): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.saveCurrentBackup(label).toString()

    fun listKFactorBackups(): String = kFactor.listBackups().toString()

    @Synchronized fun prepareKFactorRestore(fileName: String): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.prepareRestore(fileName).toString()

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
        writerConflict(SerialWriteGuard.OWNER_K_MAP)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
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
        writerConflict(SerialWriteGuard.OWNER_K_MAP)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return try {
            kWriter.startBatchWrite(JSONArray(cellsJson), maxStep, pauseMs, reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Lote de células inválido").toString()
        }
    }

    fun listKMapBackups(): String = kWriter.listMapBackups().toString()

    /** Prévia do Desfazer do Mapa K: relê o mapa (somente leitura) e lista o que voltaria. */
    @Synchronized fun prepareKMapRestore(adjustmentId: String): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.prepareRestore(adjustmentId).toString()

    /** Desfazer do Mapa K: o mesmo escritor em lote (foto antes, ACK por célula, readback). */
    @Synchronized fun startKMapRestoreWrite(cellsJson: String, adjustmentId: String, reason: String): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kFactor.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração K factor está em andamento").toString()
        }
        writerConflict(SerialWriteGuard.OWNER_K_MAP)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return try {
            kWriter.startRestoreWrite(JSONArray(cellsJson), adjustmentId, reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Restauração do Mapa K inválida").toString()
        }
    }

    @Synchronized fun startKFactorReset(): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kWriter.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração do mapa K está em andamento").toString()
        }
        writerConflict(SerialWriteGuard.OWNER_K_FACTOR)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
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

    /**
     * Lote da Curva K. Com [restoreFile], é a restauração de uma foto: os alvos são conferidos contra
     * o arquivo e o piso 0.60 (só para alvos novos) não se aplica a valores que a ECU já teve.
     */
    @Synchronized fun startKFactorWrite(pointsJson: String, reason: String, restoreFile: String = ""): String {
        if (!usb.connected) return JSONObject().put("ok", false).put("error", "USB desconectado").toString()
        if (kWriter.isBusy()) {
            return JSONObject().put("ok", false).put("error", "Uma alteração do mapa K está em andamento").toString()
        }
        writerConflict(SerialWriteGuard.OWNER_K_FACTOR)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return try {
            val points = JSONArray(pointsJson)
            if (restoreFile.isNotBlank()) kFactor.startRestoreWrite(points, restoreFile, reason).toString()
            else kFactor.startBatchWrite(points, reason).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Lote K factor inválido").toString()
        }
    }

    /** Outra operação que MUDA a ECU (AutoCal ou o outro escritor K) já detém a serial? Devolve o aviso humano. */
    private fun writerConflict(self: String): String? =
        SerialWriteGuard.shared.holder()?.takeIf { it != self }?.let {
            JSONObject().put("ok", false)
                .put("error", "Aguarde: ${SerialWriteGuard.label(it)} em andamento")
                .toString()
        }

    private fun calibrationBusy(operation: String): String = JSONObject()
        .put("ok", false)
        .put("error", "Uma operação de $operation está em andamento")
        .toString()

    fun sessionRecorderStatusJson(): String = sessionRecorder.statusJson()
    // A lista de sessões lê pastas e, se preciso, reconstrói resumos: nunca na thread da WebView.
    // Devolve a última lista pronta na hora ("null" até a primeira ficar pronta) e atualiza em segundo plano.
    private val sessionListExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "omegas-session-list").apply { isDaemon = true }
    }
    private val sessionListBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var sessionListJson = "null"
    @Volatile private var sessionListAt = -1L

    fun sessionRecorderListJson(): String {
        val now = android.os.SystemClock.elapsedRealtime()
        if ((sessionListAt < 0L || now - sessionListAt > 3_000L) && sessionListBusy.compareAndSet(false, true)) {
            sessionListExecutor.execute {
                try {
                    sessionListJson = sessionRecorder.listSessionsJson()
                    sessionListAt = android.os.SystemClock.elapsedRealtime()
                } catch (_: Exception) {
                } finally {
                    sessionListBusy.set(false)
                }
            }
        }
        return sessionListJson
    }
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
    private fun startJournalSession(reason: String, metadata: JSONObject): JSONObject {
        val started = sessionRecorder.start(reason, metadata)
        if (started.optBoolean("ok")) {
            legacySweepRemoved = emptyList()
            refinementJournal.setDecisionListener(::recordJournalTransition)
            journalTransitionsObserved = true
        }
        return started
    }

    /**
     * Fecha a entrega do Journal antes de drenar o worker: uma decisão que já entrou no Journal
     * termina de enfileirar antes do recibo da sessão ser fechado. A transição posterior ao
     * desligamento pertence à próxima sessão, não pode reabrir a anterior.
     */
    private fun stopJournalSession(reason: String): JSONObject {
        refinementJournal.setDecisionListener(null)
        journalTransitionsObserved = false
        return sessionRecorder.stop(reason)
    }

    fun startSessionRecording(reason: String): String = startJournalSession(
        reason.ifBlank { "manual" },
        JSONObject()
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("native", true)
            .put("usbSessionId", if (usb.connected) usb.connectionSessionId else 0L)
            .put("legacySweep", JSONArray(legacySweepRemoved)),
    ).toString()
    fun stopSessionRecording(reason: String): String = stopJournalSession(reason.ifBlank { "manual" }).toString()
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

    fun overlayReady(): Boolean = ::overlay.isInitialized

    fun overlayStatusJson(): String = if (::overlay.isInitialized) overlay.statusJson().toString() else "{}"
    fun setTelemetryOverlayEnabled(enabled: Boolean): String {
        if (!::overlay.isInitialized) return JSONObject().put("ok", false).put("error", "Overlay indisponível").toString()
        val result = overlay.setEnabled(enabled)
        updateOverlay()
        return result.toString()
    }

    fun setTelemetryOverlayScale(scale: Double): String {
        if (!::overlay.isInitialized) return JSONObject().put("ok", false).put("error", "Overlay indisponível").toString()
        val result = overlay.setScale(scale)
        updateOverlay()
        return result.toString()
    }

    fun nativeAutoCalStatusJson(): String =
        if (::nativeAutoCal.isInitialized) {
            nativeAutoCal.statusJson().put("analysisLane", analysisLane.json()).toString()
        } else "{}"

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
        // Fim da sessão USB: a foto do Desfazer da Referência vai embora e os medidores gravam em disco.
        try {
            equivalenceRuntime.references.endSession()
            equivalenceRuntime.flush()
        } catch (error: Exception) {
            log.add("WARN", "EQUIVALENCIA", "Fim de sessão: ${error.message}")
        }

        if (connected) {
            val generationChanged = transition == UsbSessionTransition.GENERATION_CHANGED
            monitoringPausedByUser = false
            if (generationChanged) {
                if (sessionRecorder.isRecording()) {
                    refinementJournal.setDecisionListener(null)
                    journalTransitionsObserved = false
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
            publishRevision(RuntimeSnapshotBus.Kind.SESSION)
            if (!wasConnected) enginePausedByUser = false
            // A gravação é sempre automática ao conectar a ECU; não depende de preferência.
            if (!sessionRecorder.isRecording()) {
                startJournalSession(
                    "MP48 conectado",
                    JSONObject()
                        .put("appVersion", BuildConfig.VERSION_NAME)
                        .put("usb", usb.deviceLabel)
                        .put("usbSessionId", sessionId)
                        .put("legacySweep", JSONArray(legacySweepRemoved)),
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
            publishRevision(RuntimeSnapshotBus.Kind.SESSION)
            if (sessionRecorder.isRecording()) {
                stopJournalSession("MP48 desconectado")
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

    /** Gravação de Curva K confirmada → experimento com o índice medido com a curva antiga. */
    private fun recordCurveExperiment(payload: JSONObject) {
        // O índice "antes" precisa ser lido antes de a evidência ser invalidada.
        val indexBefore = try { equivalence.index() } catch (_: Throwable) { null }
        val rawNow = payload.optJSONObject("curve")?.optJSONArray("factorsRaw")?.takeIf { it.length() == 30 }
        // Curva nova: o GNV medido com a antiga sai; a gasolina (referência) fica. Invalida PRIMEIRO.
        EvidenceInvalidation.run(
            invalidate = listOf(
                "resetGas" to { equivalence.resetGas("CURVA_K_GRAVADA") },
                "cerebro" to { equivalenceRuntime.onGasReset("CURVA_K_GRAVADA", equivalencePhases) },
                "adoptCurve" to {
                    rawNow?.let { raw -> equivalence.adoptCurve(EquivalenceLedger.fingerprint(IntArray(30) { raw.optInt(it) })) }
                },
            ),
            record = { sessionRecorder.record("k_factor_batch_confirmed", "k_factor", payload, force = true) },
            warn = { log.add("WARN", "EVIDENCIA", it) },
        )
        try {
            val curve = payload.optJSONObject("curve") ?: return
            val axis = curve.optJSONArray("axisRaw") ?: return
            val after = curve.optJSONArray("factorsRaw") ?: return
            if (axis.length() != 30 || after.length() != 30) return
            val afterRaw = IntArray(30) { after.optInt(it) }
            val beforeRaw = afterRaw.copyOf()
            val points = payload.optJSONArray("points")
            if (points != null) for (i in 0 until points.length()) {
                val point = points.optJSONObject(i) ?: continue
                val index = point.optInt("index", -1)
                if (index in 0 until 30) beforeRaw[index] = point.optInt("currentRaw", beforeRaw[index])
            }
            refinementJournal.recordCurveWrite(
                beforeRaw = beforeRaw,
                afterRaw = afterRaw,
                axisRaw = IntArray(30) { axis.optInt(it) },
                indexBefore = indexBefore ?: JSONObject(),
                source = payload.optString("adjustmentId", "K_FACTOR"),
                photoFile = payload.optString("photoFile", ""),
            )
            // Cada ponto que o dono acabou de mudar entra em prova no cérebro único.
            equivalenceRuntime.onCurveWritten(beforeRaw, afterRaw, equivalencePhases)
        } catch (error: Exception) {
            log.add("WARN", "REFINO", "Experimento não registrado: ${error.message}")
        }
    }

    /**
     * Escrita da Curva K falhou depois de começar: a ECU pode ter mudado. O GNV medido não vale mais e o diário
     * guarda a foto de antes para o Desfazer aparecer (fora do <details>). Só observa; nada vai à ECU.
     */
    private fun recordFailedCurveWrite(payload: JSONObject) {
        EvidenceInvalidation.run(
            invalidate = listOf(
                "resetGas" to { equivalence.resetGas("CURVA_K_FALHA_PARCIAL") },
                "cerebro" to { equivalenceRuntime.onGasReset("CURVA_K_FALHA_PARCIAL", equivalencePhases) },
                "journal" to {
                    refinementJournal.recordFailedWrite(
                        photoFile = payload.optString("photoFile", ""),
                        source = payload.optString("adjustmentId", "K_FACTOR"),
                        partial = payload.optBoolean("partial", false),
                    )
                },
            ),
            record = { sessionRecorder.record("k_factor_batch_failed", "k_factor", payload, force = true) },
            warn = { log.add("WARN", "EVIDENCIA", it) },
        )
        link.markDataChanged("escrita K factor falhou")
    }

    /** O que veio depois de cada apagão (religou, telemetria parou, sem religar) entra na sessão. */
    private fun recordStallAnnotations() {
        for (note in stallWatch.drainAnnotations()) sessionRecorder.record("engine_stall_after", "autocal", note, force = true)
    }

    private var verdictBaselineSet = false
    private var lastVerdictRecordedId = ""
    @Volatile private var journalTransitionsObserved = false
    private val journalRecordingLock = Any()
    private val recordedVerdictIds = LinkedHashSet<String>()

    /** Um instantâneo por transição, em ordem; nenhuma releitura pode substituir evento intermediário. */
    private fun recordJournalTransition(latest: JSONObject) = synchronized(journalRecordingLock) {
        recordJournalDecisionSnapshot(latest)
        recordVerdictSnapshot(latest)
    }

    /**
     * Veredito de cada gravação entra na sessão uma única vez, quando a verificação fecha.
     * O veredito que já estava fechado quando o app abriu pertence a outra sessão e não é repetido.
     */
    private var lastJournalDecisionSignature = ""

    /** Amostras novas não são decisão nova: somente mudança de veredito/estado entra na sessão. */
    private fun journalDecisionSignature(latest: JSONObject?): String {
        if (latest == null) return ""
        val bands = latest.optJSONArray("bands") ?: org.json.JSONArray()
        return latest.optString("id") + "|" + latest.optString("status") + "|" +
            (0 until bands.length()).joinToString(",") { bands.optJSONObject(it)?.optString("verdict").orEmpty() }
    }

    private fun recordJournalDecision() {
        if (journalTransitionsObserved) return
        val latest = refinementJournal.json().optJSONObject("latest") ?: return
        synchronized(journalRecordingLock) { recordJournalDecisionSnapshot(latest) }
    }

    private fun recordJournalDecisionSnapshot(latest: JSONObject) {
        val signature = journalDecisionSignature(latest)
        if (signature == lastJournalDecisionSignature) return
        lastJournalDecisionSignature = signature
        val status = latest.optString("status")
        val anomaly = status in setOf("PIOROU_EM_PARTE", "INCONCLUSIVO", "INTERROMPIDO")
        val headline = when (status) {
            "PIOROU_EM_PARTE" -> "A comparação detectou piora em parte das faixas medidas."
            "INCONCLUSIVO" -> "Faltou condução suficiente para concluir a comparação."
            "INTERROMPIDO" -> "A comparação foi interrompida porque a calibração mudou."
            "SEM_BASE" -> "Faltou medição anterior para comparar a curva."
            "VERIFICADO" -> "A comparação terminou; o resultado está separado por faixa."
            else -> "A curva gravada está sendo comparada nas faixas medidas."
        }
        sessionRecorder.record(
            if (anomaly) "refinement_diagnostic" else "refinement_decision", "autocal",
            JSONObject().put("component", "JOURNAL").put("phase", equivalencePhases.json().optString("phase"))
                .put("reasonCode", latest.optString("reasonCode", "POST_WRITE_SAMPLES_PENDING"))
                .put("failureDomain", latest.optString("failureDomain", "NONE")).put("headline", headline)
                .put("diagnostic", JSONObject().put("experimentId", latest.optString("id"))
                    .put("status", status).put("onlineMs", latest.optLong("onlineMs"))
                    .put("interruptReason", latest.opt("interruptReason") ?: JSONObject.NULL)
                    .put("ratioBefore", latest.optJSONObject("indexBefore")?.opt("ratio") ?: JSONObject.NULL)
                    .put("ratioAfter", latest.optJSONObject("indexAfter")?.opt("ratio") ?: JSONObject.NULL)
                    .put("bands", latest.optJSONArray("bands") ?: org.json.JSONArray())),
            force = true,
        )
    }

    private fun recordVerdictIfClosed() {
        if (journalTransitionsObserved) return
        val latest = refinementJournal.json().optJSONObject("latest") ?: return
        synchronized(journalRecordingLock) { recordVerdictSnapshot(latest) }
    }

    private fun recordVerdictSnapshot(latest: JSONObject) {
        val id = latest.optString("id")
        val status = latest.optString("status")
        verdictBaselineSet = true
        if (status == "VERIFICANDO" || id.isBlank() || id == lastVerdictRecordedId || id in recordedVerdictIds) return
        lastVerdictRecordedId = id
        recordedVerdictIds.add(id)
        while (recordedVerdictIds.size > RefinementJournal.MAX_EXPERIMENTS) {
            recordedVerdictIds.remove(recordedVerdictIds.first())
        }
        sessionRecorder.record(
            "refinement_verdict", "autocal",
            JSONObject().put("id", id).put("status", status)
                .put("reasonCode", latest.optString("reasonCode")).put("failureDomain", latest.optString("failureDomain"))
                .put("onlineMs", latest.optLong("onlineMs"))
                .put("appliedAt", latest.optLong("appliedAt")).put("closedAt", latest.optLong("closedAt"))
                .put("ratioBefore", latest.optJSONObject("indexBefore")?.opt("ratio") ?: JSONObject.NULL)
                .put("ratioAfter", latest.optJSONObject("indexAfter")?.opt("ratio") ?: JSONObject.NULL)
                .put("bands", latest.optJSONArray("bands") ?: org.json.JSONArray()),
            force = true,
        )
    }

    /** Piloto do refino: decide a fase e avisa uma vez por fase. Nunca grava na ECU. */
    private var lastRefinementDecision = ""

    private fun observeRefinement() {
        try {
            val progress = if (::nativeAutoCal.isInitialized) nativeAutoCal.autoMatchProgressJson() else null
            // A curva de gasolina que a ECU já tem vira referência (app recém-instalado, outra versão,
            // outra sessão: a ECU guarda e entrega ao conectar).
            // Offline mantém a última referência lida: a fase não pode oscilar só porque o cabo saiu.
            val acquisition = progress?.optJSONObject("acquisition")
            val frozenReference = equivalenceRuntime.references.current()
            if (frozenReference != null) {
                // Referência congelada pelo dono: vale ela, não a curva viva da ECU (que ela pode reaprender).
                equivalence.setEcuPetrolReference(frozenReference.points.map { it.mapBar to it.petrolMs })
            } else if (usb.connected) {
                equivalence.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(acquisition))
            }
            val before = equivalencePhases.json().optString("phase")
            val decided = equivalencePhases.observe(
                ecuOnline = usb.connected && runtime.ready,
                monitor = progress,
                acquisition = progress?.optJSONObject("acquisition"),
                index = equivalence.index(),
                journal = refinementJournal.json(),
                restoreCount = refinementJournal.restorePoints().length(),
            )
            observeEquivalenceBrain(acquisition)
            // Registra mudanças de decisão, não cada tick da telemetria. Números completos
            // ficam no evento curto e no RESUMO; timeout nunca vira recibo de gravação.
            val numbers = decided.optJSONObject("diagnostic") ?: JSONObject()
            val decisionKey = listOf(decided.optString("phase"), decided.optString("reasonCode"),
                decided.optString("failureDomain"), numbers.optInt("bandsMeasured"),
                numbers.optInt("bandsOff"), numbers.opt("autoMatchCount"), numbers.optString("journalStatus")).joinToString("|")
            if (decisionKey != lastRefinementDecision) {
                lastRefinementDecision = decisionKey
                sessionRecorder.record(
                    if (decided.optBoolean("watchdogExpired")) "refinement_diagnostic" else "refinement_decision",
                    "autocal", decided, force = true,
                )
            }
            if (decided.optString("phase") != before) {
                sessionRecorder.record("refinement_phase", "autocal", decided, force = true)
                stateChanged()
            }
            equivalencePhases.takeAlert()?.let { alert ->
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

    private var lastEquivalenceKey = ""

    /**
     * Cérebro único: reavalia no executor do serviço (tique de 3 s) e grava na sessão só quando o resultado
     * muda (índice guardado por sessão). Só observa: zero comandos à ECU.
     */
    private fun observeEquivalenceBrain(acquisition: JSONObject?) {
        try {
            val snapshot = try { JSONObject(nativeAutoCalSnapshotJson()) } catch (_: Exception) { null }
            val result = equivalenceRuntime.evaluate(
                equivalence, equivalencePhases, snapshot, acquisition,
                usb.connected && runtime.ready, refinementJournal::pointGainScale,
            ) ?: return
            val index = result.index?.let { Math.round(it * 1000.0) / 1000.0 }
            val key = listOf(index, result.coverage, result.nextAction.kind.name, result.provisional).joinToString("|")
            if (key != lastEquivalenceKey) {
                lastEquivalenceKey = key
                val payload = JSONObject().put("index", index ?: JSONObject.NULL).put("coverage", result.coverage)
                    .put("provisional", result.provisional)
                    .put("nextAction", JSONObject().put("kind", result.nextAction.kind.name).put("text", result.nextAction.text))
                sessionRecorder.record("equivalence_result", "autocal", payload, force = true)
            }
        } catch (error: Exception) {
            log.add("WARN", "EQUIVALENCIA", "Cérebro: ${error.message}")
        }
    }

    /** Aquisição de pontos que a ECU entregou por último (nula antes da primeira leitura). */
    private fun latestAcquisition(): JSONObject? =
        if (::nativeAutoCal.isInitialized) nativeAutoCal.autoMatchProgressJson().optJSONObject("acquisition") else null

    /** O dono tocou "Congelar": a gasolina madura da ECU vira a Referência. Não toca a ECU. */
    fun freezeReference(): String = try {
        val reference = equivalenceRuntime.freeze(latestAcquisition(), equivalencePhases)
        val frozenPayload = JSONObject().put("id", reference.id).put("frozenAt", reference.frozenAt).put("points", reference.points.size)
        sessionRecorder.record("reference_frozen", "autocal", frozenPayload, force = true)
        JSONObject().put("ok", true).put("reference", equivalenceRuntime.json(latestAcquisition()).optJSONObject("reference")).toString()
    } catch (error: IllegalStateException) {
        JSONObject().put("ok", false).put("reason", error.message ?: "AQUISICAO_IMATURA")
            .put("message", "A ECU ainda não tem curva de gasolina madura para congelar.").toString()
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("reason", "ERRO").put("message", error.message ?: "Falha ao congelar a referência").toString()
    }

    /** Desfazer do congelamento: volta à Referência anterior desta sessão. */
    fun restorePreviousReference(): String = try {
        val back = equivalenceRuntime.restorePreviousReference(equivalencePhases)
        if (back == null) {
            JSONObject().put("ok", false).put("reason", "SEM_REFERENCIA_ANTERIOR")
                .put("message", "Não há referência anterior nesta sessão.").toString()
        } else {
            sessionRecorder.record("reference_restored", "autocal", JSONObject().put("id", back.id), force = true)
            JSONObject().put("ok", true).put("reference", equivalenceRuntime.json(latestAcquisition()).optJSONObject("reference")).toString()
        }
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("reason", "ERRO").put("message", error.message ?: "Falha ao restaurar a referência").toString()
    }

    fun equivalenceResultJson(): String = try {
        equivalenceRuntime.json(latestAcquisition()).toString()
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("error", error.message ?: "Resultado indisponível").toString()
    }

    private fun healthTick() {
        if (stopping) return
        // O trabalho pesado (refino, diário, full_snapshot JSON, overlay) sai desta thread: o autoCalTick
        // divide o `scheduler` com este tick e nunca pode esperar por ele.
        analysisLane.submit(::analysisTick)
        try {
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
            renewWakeLockIfNeeded()
        } catch (error: Exception) {
            healthFailures += 1
            log.add("WARN", "SERVICE", "Monitor nativo: ${error.message}")
        }
    }

    /** Roda na faixa de análise (thread própria, coalescente). Só observa e grava; não toca a ECU. */
    private fun analysisTick() {
        if (stopping) return
        try {
            if (!refinementFrozenForRender) {
                // O orçamento da verificação conta só condução: rpm ≥ 1000 numa faixa alterada (quadro fresco).
                val frameFresh = System.currentTimeMillis() - lastDriveFrameAt < 3_500L
                if (refinementJournal.evaluate(
                        equivalence.index(), ecuOnline = usb.connected && runtime.ready,
                        rpm = if (frameFresh) lastDriveRpm else 0.0,
                        petrolMs = if (frameFresh) lastDrivePetrolMs else null,
                    )
                ) stateChanged()
                // Silêncio da telemetria depois de uma queda de RPM decide "desligou" × "apagou".
                stallWatch.tick(System.currentTimeMillis())
                recordStallAnnotations()
                recordJournalDecision()
                recordVerdictIfClosed()
                observeRefinement()
            }
            if (sessionRecorder.isRecording()) {
                sessionRecorder.record(
                    "full_snapshot",
                    "native",
                    try { JSONObject(fullEngineSnapshotJson()) } catch (_: Exception) { JSONObject() },
                )
            }
            updateOverlay()
            updateNotification()
        } catch (error: Exception) {
            synchronized(this) { healthFailures += 1 }
            log.add("WARN", "SERVICE", "Análise nativa: ${error.message}")
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

    @Volatile private var lastDriveRpm = 0.0
    @Volatile private var lastDrivePetrolMs = 0.0
    @Volatile private var lastDriveFrameAt = 0L

    private fun consumeEngineEvent(root: JSONObject) {
        val accepted = telemetryStore.updateFromEngineEvent(root) ?: return
        val live = root.optJSONObject("live") ?: root.optJSONObject("data") ?: JSONObject()
        lastDriveRpm = live.optDouble("rpm", 0.0)
        lastDrivePetrolMs = live.optDouble("petrol_ms", 0.0)
        lastDriveFrameAt = System.currentTimeMillis()
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
            ),
        )
        try {
            equivalenceRuntime.onFrame(
                accepted.optLong("timestamp", System.currentTimeMillis()),
                live.optString("fuel").uppercase(),
                live.optDouble("rpm", 0.0),
                live.optDouble("load_bar", 0.0),
                live.optDouble("petrol_ms", 0.0),
                if (usb.connected) usb.connectionSessionId else 0L,
            )
        } catch (error: Exception) {
            log.add("WARN", "EQUIVALENCIA", "Quadro não entrou no cérebro: ${error.message}")
        }

        // Velocidade do GPS (se ligado) entra no evento: motor morrendo com o carro andando é apagão,
        // sem telemetria depois e parado é a chave desligada.
        val gpsSpeedKmh = if (::gps.isInitialized && gps.running) {
            gps.json().optDouble("speedKmh", -1.0).takeIf { it >= 0.0 }
        } else null
        stallWatch.accept(
            StallWatch.Frame(
                t = accepted.optLong("timestamp", System.currentTimeMillis()),
                fuel = live.optString("fuel").uppercase(),
                rpm = live.optDouble("rpm", 0.0),
                map = live.optDouble("load_bar", 0.0),
                petrolMs = live.optDouble("petrol_ms", 0.0),
                speedKmh = gpsSpeedKmh,
            ),
        )?.let { event ->
            try { equivalenceRuntime.onStall(event) } catch (_: Exception) {}
            sessionRecorder.record("engine_stall", "autocal", event, force = true)
            val verb = if (event.optString("kind") == StallWatch.KIND_NEAR) "Motor quase apagou" else "Motor apagou"
            log.add("WARN", "REFINO", "$verb no GNV em %.2f ms · MAP %.2f bar".format(event.optDouble("petrolMs"), event.optDouble("mapBar")))
        }
        recordStallAnnotations()

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
        if (!::overlay.isInitialized || !overlay.wantsUpdate()) return
        val hub = status()
        // Telemetria fresca (≤ 3 s) com a ECU conectada; senão o balão mostra "—" em vez do último número.
        val live = hub.usbConnected && hub.directTelemetryAgeMs in 0L..3_000L
        val fuel = hub.fuelState.trim().uppercase().takeIf { it.isNotEmpty() && it != "--" }
        overlay.update(
            TelemetryOverlayController.Snapshot(
                cell = "—",
                stft = null,
                petrolMs = hub.petrolMs.takeIf { it > 0.0 },
                rpm = hub.rpm.toDouble().takeIf { it > 0.0 },
                fuel = fuel,
                mapBar = hub.mapBar.takeIf { it > 0.0 },
                gasMs = hub.gasMs.takeIf { it > 0.0 },
                live = live,
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

    private var lastNotificationContent: NotificationController.Content? = null

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < 900L) return
        // Só reposta quando título/texto/ações mudaram: montar e postar a mesma notificação a cada
        // segundo gasta CPU e bateria sem mostrar nada de novo.
        val content = notifications.content(status())
        if (content == lastNotificationContent) return
        lastNotificationAt = now
        try {
            NotificationManagerCompat.from(this)
                .notify(NotificationController.NOTIFICATION_ID, notifications.build(content))
            lastNotificationContent = content
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