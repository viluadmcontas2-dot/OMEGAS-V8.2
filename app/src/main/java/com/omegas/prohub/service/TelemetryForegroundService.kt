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
import com.omegas.prohub.autocal.NativeGasEvidenceEpoch
import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.RefinementJournal
import com.omegas.prohub.autocal.StallWatch
import com.omegas.prohub.equivalence.EquivalenceRuntime
import androidx.core.app.ServiceCompat
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.FailureKind
import com.omegas.prohub.calibration.KFactorManager
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.autocal.NativeAutoCalMonitor
import com.omegas.prohub.autocal.AutoCalNativeActionManager
import com.omegas.prohub.autocal.AutoIdleCleanupCoordinator
import com.omegas.prohub.ecu.Mp48WorkClass
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
import com.omegas.prohub.util.Units
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
    private fun newAnalysisExecutor(): java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-native-analysis").apply { isDaemon = true }
    }
    @Volatile private var analysisExecutor: java.util.concurrent.ExecutorService = newAnalysisExecutor()
    private val analysisLane = AnalysisLane(
        analysisExecutor,
        onHang = { ms -> log.add("WARN", "SERVICE", "Faixa de análise travada há ${ms / 1_000L} s; rodada presa descartada, executor novo") },
        // Recupera de verdade: encerra o executor preso (interrompe) e entrega um novo à faixa.
        replaceExecutor = { stuck ->
            (stuck as? java.util.concurrent.ExecutorService)?.shutdownNow()
            newAnalysisExecutor().also { analysisExecutor = it }
        },
    )
    /**
     * Thread própria do `autoCalTick`: o snapshot completo dorme no árbitro por segundos e um grupo bloqueia até 4 s;
     * no `scheduler` isso atrasava reconexão, wake lock e botões. O monitor só usa seus locks/atômicos próprios.
     */
    private val autoCalExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "omegas-autocal-tick").apply { isDaemon = true }
    }
    /**
     * Thread própria do apagamento automático de pontos fora da curva (GNV e gasolina): decide fora do autoCalTick
     * (que só entrega leituras) e reavalia a cada 500 ms, porque o carro pode começar a andar sem leitura nova.
     */
    private val autoIdleExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "omegas-autocal-idle-cleanup").apply { isDaemon = true }
    }

    /**
     * Revisões por tipo de dado (live/evidence/tables/session) que a UI consulta antes de reler qualquer coisa.
     * `live` espelha a sequência do TelemetryStateStore; as demais só sobem quando o dado realmente mudou.
     */
    val revisions = RuntimeSnapshotBus()
    private val revisionSlot = com.omegas.prohub.runtime.RevisionListenerSlot()

    /** A Activity registra (e limpa com null) o empurrão `OmegasOnRevision`; o poll de segurança da UI continua. */
    fun setRevisionListener(listener: ((RuntimeSnapshotBus.Kind, Long) -> Unit)?) {
        revisionSlot.set(listener)
    }

    private fun publishRevision(kind: RuntimeSnapshotBus.Kind) {
        val revision = revisions.bump(kind)
        revisionSlot.publish(kind, revision)
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
    /**
     * Ações AutoCal nativas (manuais e o apagamento automático de pontos fora da curva). Mora no serviço, não na tela:
     * o automático funciona com a WebView fechada e a ponte só usa esta instância.
     */
    lateinit var nativeActions: AutoCalNativeActionManager
        private set
    /** Detecção + política + execução do apagamento automático (spec 2026-10-07-autocal-apagar-lenta). */
    lateinit var autoIdleCleanup: AutoIdleCleanupCoordinator
        private set
    /** Quem recarrega a WebView quando o estado da ação AutoCal muda (a ponte da Activity atual). */
    private val autoCalActionUi = com.omegas.prohub.autocal.OwnedSlot<() -> Unit>()
    /** A leitura AutoCal manual mora na ponte; ela informa aqui se está lendo, para nenhuma ação cruzar a leitura. */
    private val manualAutoCalRead = com.omegas.prohub.autocal.OwnedSlot<() -> Boolean>()

    /** A ponte da tela atual se registra como dona do listener de UI e do "lendo" manual. */
    fun bindAutoCalUi(owner: Any, refreshUi: () -> Unit, manualReadBusy: () -> Boolean) {
        autoCalActionUi.set(owner, refreshUi)
        manualAutoCalRead.set(owner, manualReadBusy)
    }

    /**
     * Solta o registro só se [owner] ainda for o dono: o destroy de uma Activity antiga não desarma a ponte da
     * nova (revisão 2026-10-07 #5). Devolve se era o dono.
     */
    fun releaseAutoCalUi(owner: Any): Boolean {
        val wasOwner = autoCalActionUi.clearIf(owner)
        manualAutoCalRead.clearIf(owner)
        return wasOwner
    }

    fun isAutoCalUiOwner(owner: Any): Boolean = autoCalActionUi.isOwner(owner)

    private fun manualAutoCalReadBusy(): Boolean = try { manualAutoCalRead.get()?.invoke() == true } catch (_: Exception) { false }
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
    /** O dono parou a gravação de sessão com o toque: o monitor não a reinicia até a próxima conexão USB. */
    @Volatile private var sessionStoppedByOwner = false
    private var sessionRestartFailures = 0
    private var sessionRestartNotBefore = 0L
    /** O que a limpeza única do aprendizado antigo apagou; vai para os metadados da primeira sessão gravada. */
    @Volatile private var legacySweepRemoved: List<String> = emptyList()

    /**
     * Só para a evidência de render no emulador: congela o piloto/diário do Refino para o teste
     * conduzir o estado (replay real) sem corrida com o relógio do serviço. Nunca é ligado em produção.
     */
    @Volatile var refinementFrozenForRender = false

    /** Teto de `app_log` gravado na sessão (por segundo). */
    private val appLogCap = com.omegas.prohub.util.RateCap(maxPerWindow = 40)

    override fun onCreate() {
        super.onCreate()
        // O Android dá poucos segundos para o serviço virar foreground: o aviso entra ANTES de qualquer
        // leitura de arquivo (ledger, diário, sessões...). O conteúdo real vem logo depois, no fim do onCreate.
        notifications = NotificationController(this)
        startForegroundBootstrap()
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
        // Curva K mudou por fora (ProgBase, outro aparelho): a verificação do diário e a foto do Desfazer perdem validade.
        equivalenceRuntime.onExternalCurveChange = { reason ->
            NativeGasEvidenceEpoch.shared.markWrite(System.currentTimeMillis())
            refinementJournal.interrupt(reason)
        }
        val documentsMirror = DocumentsSessionMirror(this)
        sessionRecorder = SessionRecorder(paths, settings, documentsMirror)
        refinementJournal.setDecisionListener(::recordJournalTransition)
        journalTransitionsObserved = true
        sessionRecorder.recoverDocumentsMirrorAsync()
        log.setListener { item ->
            // Teto por segundo: um laço de log (UI, USB) não pode inundar a sessão gravada nem o disco.
            if (appLogCap.allow(SystemClock.elapsedRealtime())) {
                sessionRecorder.record("app_log", "native", item, force = true)
            }
        }
        // Limpeza única dos arquivos do aprendizado antigo, fora da thread principal.
        scheduler.execute {
            legacySweepRemoved = LegacyDataSweeper.sweep(paths.runtimeRoot, paths.runtimeBackupsRoot)
            if (legacySweepRemoved.isNotEmpty()) {
                log.add("INFO", "LIMPEZA", "Dados do aprendizado antigo removidos: " + legacySweepRemoved.joinToString())
            }
        }
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
                        "epocaNativa" to { NativeGasEvidenceEpoch.shared.markWrite(System.currentTimeMillis()) },
                        "resetGas" to { equivalence.resetGas("MAPA_K_GRAVADO") },
                        "cerebro" to { equivalenceRuntime.onGasReset("MAPA_K_GRAVADO", equivalencePhases) },
                        "journal" to { refinementJournal.interrupt("MAPA_K_GRAVADO") },
                        // Leituras do round feitas antes desta gravação não valem depois dela.
                        "round" to { if (::nativeAutoCal.isInitialized) nativeAutoCal.invalidateRound() },
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
            // Escritores K e ações AutoCal (a trava serial é compartilhada): nenhuma leitura de round durante uma escrita.
            calibrationBusy = { kWriter.isBusy() || kFactor.isBusy() || SerialWriteGuard.shared.isHeld() },
            onFreshSnapshot = { snapshot ->
                NativeGasEvidenceEpoch.shared.observe(snapshot)
                sessionRecorder.record("autocal_native_snapshot", "autocal", snapshot, force = true)
            },
            onNativeCalibrationObserved = { payload ->
                // A ECU trocou a Curva K sozinha: GNV antigo descartado, verificação interrompida. Invalida PRIMEIRO;
                // gravar a sessão (pode falhar por disco cheio) só depois.
                EvidenceInvalidation.run(
                    invalidate = listOf(
                        "resetGas" to { equivalence.resetGas("AUTOMATCH_NATIVO") },
                        "cerebro" to { equivalenceRuntime.onGasReset("AUTOMATCH_NATIVO", equivalencePhases) },
                        "journal" to { refinementJournal.interrupt("AUTOMATCH_NATIVO") },
                    ),
                    record = {
                        sessionRecorder.record(
                            "autocal_native_calibration_epoch",
                            "autocal",
                            payload,
                            force = true,
                        )
                    },
                    warn = { log.add("WARN", "EVIDENCIA", it) },
                )
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
            onBuffersConfirmed = { buffers ->
                if (::autoIdleCleanup.isInitialized) autoIdleCleanup.onBuffers(buffers)
            },
            onAcquisitionReset = { sessionChanged, sessionId ->
                if (::autoIdleCleanup.isInitialized) {
                    if (sessionChanged) autoIdleCleanup.onSessionChanged(sessionId) else autoIdleCleanup.onRoundInvalidated()
                }
            },
            appAutomaticWriteEnabled = { ::autoIdleCleanup.isInitialized && autoIdleCleanup.automaticEnabled() },
        )
        val actionSerial = runtime.serialScheduler()
        nativeActions = AutoCalNativeActionManager(
            receiptFile = File(paths.runtimeRoot, "autocal_native_receipts.json"),
            isConnected = actionSerial::isConnected,
            currentSessionId = actionSerial::currentSessionId,
            otherCalibrationBusy = { kWriter.isBusy() || kFactor.isBusy() || manualAutoCalReadBusy() },
            unsafeMutationReason = { CalibrationWriteSafetyPolicy.unsafeReason(status()) },
            transaction = { request, reason, timeoutMs, expectedSessionId ->
                val workClass = when (request.firstOrNull()?.toInt()?.and(0xFF)) {
                    0x09, 0x29, 0x0A -> Mp48WorkClass.READ_ONLY
                    else -> Mp48WorkClass.MANUAL_WRITE
                }
                actionSerial.transaction(
                    request = request,
                    reason = reason,
                    timeoutMs = timeoutMs,
                    purgeBefore = true,
                    expectedSessionId = expectedSessionId,
                    workClass = workClass,
                )
            },
            onConfirmed = { receipt ->
                sessionRecorder.record("autocal_native_action", "autocal", receipt, force = true)
                nativeAutoCal.onManualActionConfirmed(receipt)
                autoIdleCleanup.onActionConfirmed(receipt)
                try { link.markDataChanged("ação AutoCal nativa confirmada") } catch (_: Exception) {}
            },
            onFailed = { receipt ->
                sessionRecorder.record("autocal_native_action", "autocal", receipt, force = true)
                // A escrita pode ter chegado à ECU: nada lido antes dela vale mais (M2).
                if (receipt.optBoolean("mutationMayHaveStarted", false)) nativeAutoCal.invalidateRound()
                autoIdleCleanup.onActionFailed(receipt)
            },
            onStateChanged = {
                publishRevision(RuntimeSnapshotBus.Kind.TABLES)
                try { autoCalActionUi.get()?.invoke() } catch (_: Exception) {}
            },
            lastKnownVector = { field -> nativeAutoCal.lastKnownVector(field) },
        )
        autoIdleCleanup = AutoIdleCleanupCoordinator(
            telemetry = actionSerial,
            executeDelete = { targets, evidence -> nativeActions.executeAutomaticPointDelete(targets, evidence) },
            autoCalEnabled = { nativeAutoCal.autoCalEnabledNow() },
            sessionAgeMs = { nativeAutoCal.sessionAgeMs() },
            // Mesma regra de qualquer escrita: sem o controle principal do MP48 (Link), nada sai deste aparelho.
            canAct = { if (stopping) "Serviço encerrando" else if (!canWriteLocally()) "Este aparelho não possui o controle principal do MP48" else null },
            record = { type, payload -> sessionRecorder.record(type, "autocal", payload, force = true) },
            clock = SystemClock::elapsedRealtime,
            executor = autoIdleExecutor,
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
                    // Única escrita automática: apagar pontos fora da curva do GNV e da gasolina (spec 2026-10-07 rev2).
                    .put("automaticCalibration", autoIdleCleanup.automaticEnabled())
                    .put("automaticScope", "DELETE_OUTLIER_POINTS")
                    .put("manualOnly", false)
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
        autoCalTask = autoCalExecutor.scheduleWithFixedDelay(::autoCalTick, 1_000L, 100L, TimeUnit.MILLISECONDS)
        autoIdleExecutor.scheduleWithFixedDelay({ if (!stopping) autoIdleCleanup.evaluate() }, 2_000L, 500L, TimeUnit.MILLISECONDS)
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
            ACTION_STOP_SERVICE -> scheduler.execute { stopFromUser() }
            else -> scheduler.execute {
                if (settings.autoConnectUsb && !usb.connected && usb.hasCompatibleDevice()) usb.connect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * "Parar" da notificação: com a Activity ligada (bind), `stopSelf()` sozinho NÃO destrói o serviço.
     * Avisa a Activity para fechar (ela desliga o bind no onDestroy) e pede o fim do serviço.
     */
    @Volatile private var stopListener: (() -> Unit)? = null
    fun setStopListener(listener: (() -> Unit)?) { stopListener = listener }

    private fun stopFromUser() {
        try { stopListener?.invoke() } catch (_: Throwable) {}
        stopSelf()
    }

    override fun onDestroy() {
        if (stopping) return
        stopping = true
        // O aviso sai primeiro; o resto do desligamento (ECU, USB, disco) corre em thread própria. A thread
        // principal espera só um pouco: o que passar disso termina sozinho em segundo plano (nunca ANR).
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        healthTask?.cancel(true)
        autoCalTask?.cancel(true)
        val teardown = Thread({ teardownBlocking() }, "omegas-service-teardown")
        try {
            teardown.start()
            teardown.join(4_000L)
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    /** Fecha tudo sem pressa e sem abortar gravação em curso (espera o escritor terminar, com teto). */
    private fun teardownBlocking() {
        try { refinementJournal.setDecisionListener(null) } catch (_: Throwable) {}
        journalTransitionsObserved = false
        try { equivalence.flush() } catch (_: Throwable) {}
        try { equivalenceRuntime.flush() } catch (_: Throwable) {}
        try { refinementJournal.flush() } catch (_: Throwable) {}
        try { equivalencePhases.flush() } catch (_: Throwable) {}
        scheduler.shutdownNow()
        autoCalExecutor.shutdownNow()
        autoIdleExecutor.shutdownNow()
        analysisExecutor.shutdownNow()
        // Uma escrita em curso termina (ACK + saída segura do K insertion + readback) antes de o USB cair.
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        while (SystemClock.elapsedRealtime() < deadline &&
            (kWriter.isBusy() || kFactor.isBusy() || SerialWriteGuard.shared.isHeld())
        ) {
            try { Thread.sleep(100L) } catch (_: InterruptedException) { break }
        }
        try { runtime.stop(3) } catch (_: Throwable) {}
        try { runtime.endUsbSession("SERVICE_DESTROYED") } catch (_: Throwable) {}
        try { usb.disconnect() } catch (_: Throwable) {}
        try { link.close() } catch (_: Throwable) {}
        try { lanServer.close() } catch (_: Throwable) {}
        try { gps.stop() } catch (_: Throwable) {}
        try { overlay.close() } catch (_: Throwable) {}
        try { sessionRecorder.close() } catch (_: Throwable) {}
        try { nativeAutoCal.endUsbSession() } catch (_: Throwable) {}
        try { nativeActions.clearPreparation() } catch (_: Throwable) {}
        try { nativeActions.close() } catch (_: Throwable) {}
        try { kFactor.close() } catch (_: Throwable) {}
        try { kWriter.close() } catch (_: Throwable) {}
        try { runtime.close() } catch (_: Throwable) {}
        try { usb.close() } catch (_: Throwable) {}
        releaseWakeLock()
        log.setListener(null)
    }

    /**
     * Motivo (em português simples) pelo qual gravar na ECU está bloqueado agora, ou null se pode revisar e gravar.
     * Mesma regra das escritas reais: controle principal do MP48 + CalibrationWriteSafetyPolicy.
     */
    fun writeBlockedReason(): String? {
        if (::link.isInitialized && !link.canWriteLocally()) return "Este aparelho não possui o controle principal"
        return CalibrationWriteSafetyPolicy.unsafeReason(status())
    }

    fun status(): HubStatus {
        val live = telemetryStore.telemetryCopy()
        val parityLetter = settings.parity.firstOrNull()?.uppercaseChar() ?: 'N'
        return HubStatus(
            serviceRunning = true,
            engineRunning = runtime.running,
            engineReady = runtime.ready,
            engineStuck = runtime.stuck,
            ecuLinkState = runtime.engineState,
            ecuLinkMessage = runtime.engineMessage,
            usbRecovering = usb.recovering,
            enginePausedByUser = enginePausedByUser,
            engineVersion = BuildConfig.VERSION_NAME,
            usbConnected = usb.connected,
            usbDevice = usb.deviceLabel,
            usbPermissionPending = usb.permissionPending,
            usbPermissionDenied = usb.permissionDenied,
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
            gpsSpeedKmh = gpsValueOrNull("speedKmh"),
            gpsAccuracyM = gpsValueOrNull("accuracyM"),
            lanEnabled = lanServer.running,
            lanAddress = if (lanServer.running) lanServer.address() else "",
            directTelemetryAgeMs = telemetryStore.ageMs().let { if (it == Long.MAX_VALUE) -1L else it },
        )
    }

    /** Valor do GPS só se ele está ligado e a chave existe com número finito; senão desconhecido (nulo), nunca 0. */
    private fun gpsValueOrNull(key: String): Double? {
        if (!gps.running) return null
        val json = gps.json()
        if (!json.has(key) || json.isNull(key)) return null
        return json.optDouble(key, Double.NaN).takeIf { it.isFinite() }
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

    fun engineSelfTestJson(): String = runtime.selfTestJson()

    @Synchronized fun readKCell(row: Int, column: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readCell(row, column).toString()
    @Synchronized fun readKLine(row: Int): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readLine(row).toString()
    @Synchronized fun readKMap(): String =
        if (kFactor.isBusy()) calibrationBusy("K factor") else kWriter.readFullMap().toString()
    /** "Liberar Mapa K" (toque do dono, via CalibrationOperationsBridge): mesmas guardas de qualquer escrita. */
    @Synchronized fun recoverKInsertionState(): String {
        if (!usb.connected) {
            return JSONObject().put("ok", false).put("failureKind", FailureKind.TRANSPORT)
                .put("error", "USB desconectado").toString()
        }
        if (kFactor.isBusy()) return calibrationBusy("K factor")
        writerConflict(SerialWriteGuard.OWNER_K_MAP)?.let { return it }
        if (::link.isInitialized && !link.canWriteLocally()) {
            return JSONObject().put("ok", false)
                .put("error", "Este aparelho não possui o controle principal do MP48")
                .toString()
        }
        return kWriter.recoverInsertionState().toString()
    }
    fun kWriteStatusJson(): String = kWriter.statusJson()

    @Synchronized fun readKFactorCurve(): String =
        if (kWriter.isBusy()) calibrationBusy("mapa K") else kFactor.readCurve().toString()
    fun kFactorStatusJson(): String = kFactor.statusJson()

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

    /** Este aparelho tem o controle principal do MP48 (Link)? Vale para QUALQUER ação que mude a ECU. */
    fun canWriteLocally(): Boolean = !::link.isInitialized || link.canWriteLocally()

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

    fun startSessionRecording(reason: String): String {
        sessionStoppedByOwner = false
        return startJournalSession(
            reason.ifBlank { "manual" },
            JSONObject()
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("native", true)
                .put("usbSessionId", if (usb.connected) usb.connectionSessionId else 0L)
                .put("legacySweep", JSONArray(legacySweepRemoved)),
        ).toString()
    }
    fun stopSessionRecording(reason: String): String {
        sessionStoppedByOwner = true
        return stopJournalSession(reason.ifBlank { "manual" }).toString()
    }
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
            nativeAutoCal.statusJson()
                .put("analysisLane", analysisLane.json())
                .put("autoIdleCleanup", if (::autoIdleCleanup.isInitialized) autoIdleCleanup.json() else JSONObject.NULL)
                .toString()
        } else "{}"

    fun nativeAutoCalSnapshotJson(): String =
        if (::nativeAutoCal.isInitialized) nativeAutoCal.latestSnapshotJson().toString() else "{}"

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
                // Exceção aqui derrubava o app (thread do scheduler sem rede). Vira aviso legível; o próximo
                // healthTick repete a transição (lastUsbConnected só avança depois do sucesso).
                try {
                    handleUsbTransition()
                    stateChanged()
                } catch (error: Throwable) {
                    synchronized(this) { healthFailures += 1 }
                    try { log.add("WARN", "USB", "Transição USB: ${error.message ?: error.javaClass.simpleName}") } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            try { stateChanged() } catch (_: Throwable) {}
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
        // Fim da sessão USB: a foto do Desfazer da Referência vai embora e os medidores gravam em disco.
        guarded("EQUIVALENCIA", "Fim de sessão") {
            equivalenceRuntime.references.endSession()
            equivalenceRuntime.flush()
        }

        if (connected) {
            val generationChanged = transition == UsbSessionTransition.GENERATION_CHANGED
            monitoringPausedByUser = false
            if (generationChanged) {
                guarded("USB", "Troca de geração: fechar sessão gravada") {
                    if (sessionRecorder.isRecording()) {
                        refinementJournal.setDecisionListener(null)
                        journalTransitionsObserved = false
                        sessionRecorder.stop("USB_SESSION_REPLACED")
                    }
                }
                guarded("USB", "Troca de geração: engine") { runtime.endUsbSession("USB_SESSION_REPLACED") }
                guarded("USB", "Troca de geração: AutoCal") { nativeAutoCal.endUsbSession() }
                guarded("USB", "Troca de geração: telemetria") { telemetryStore.invalidate("USB_SESSION_REPLACED") }
                log.add("INFO", "USB", "Nova geração USB detectada • $previousSessionId → $sessionId")
            }
            // Cada começo de sessão é isolado: o disco cheio de um não impede a engine nem os outros.
            guarded("USB", "Telemetria") { telemetryStore.beginSession(sessionId) }
            guarded("USB", "Engine") { runtime.beginUsbSession(sessionId) }
            guarded("USB", "Mapa K") { kWriter.beginUsbSession(sessionId) }
            guarded("USB", "Curva K") { kFactor.beginUsbSession(sessionId) }
            guarded("USB", "AutoCal") { nativeAutoCal.beginUsbSession(sessionId) }
            guarded("USB", "Revisão") { publishRevision(RuntimeSnapshotBus.Kind.SESSION) }
            if (!wasConnected) enginePausedByUser = false
            // A gravação é sempre automática ao conectar a ECU; não depende de preferência.
            sessionStoppedByOwner = false
            sessionRestartFailures = 0
            if (!sessionRecorder.isRecording()) {
                guarded("SESSAO", "Gravação ao conectar") {
                    startJournalSession(
                        "MP48 conectado",
                        JSONObject()
                            .put("appVersion", BuildConfig.VERSION_NAME)
                            .put("usb", usb.deviceLabel)
                            .put("usbSessionId", sessionId)
                            .put("legacySweep", JSONArray(legacySweepRemoved)),
                    )
                }
            }
            if (settings.autoStartEngine && !enginePausedByUser) {
                guarded("ECU-NATIVE", "Início da engine") {
                    startEngine(if (generationChanged) "nova geração física MP48" else "nova conexão física MP48")
                }
            }
        } else {
            guarded("USB", "Parar engine") { runtime.stop(2) }
            guarded("USB", "Fim da sessão da engine") { runtime.endUsbSession("USB_DISCONNECTED") }
            guarded("USB", "Fim da sessão AutoCal") { nativeAutoCal.endUsbSession() }
            guarded("USB", "Telemetria") { telemetryStore.invalidate("USB_DISCONNECTED") }
            guarded("USB", "Revisão") { publishRevision(RuntimeSnapshotBus.Kind.SESSION) }
            guarded("SESSAO", "Fechar gravação") {
                if (sessionRecorder.isRecording()) {
                    stopJournalSession("MP48 desconectado")
                }
            }
            if (monitoringPausedByUser || !settings.autoReconnectUsb) {
                stopSelf()
            }
        }
        // Só agora a transição conta como feita: se algo acima escapou, o próximo tick repete.
        lastUsbConnected = connected
        lastUsbSessionId = sessionId
        guarded("USB", "Link") { if (::link.isInitialized) link.onLocalCapabilitiesChanged() }
        guarded("USB", "Wake lock") { updateWakeLock() }
    }

    /** Roda [block]; qualquer Throwable vira aviso legível e a transição segue (regra 5: nada derruba o app). */
    private inline fun guarded(category: String, what: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            synchronized(this) { healthFailures += 1 }
            try { log.add("WARN", category, "$what: ${error.message ?: error.javaClass.simpleName}") } catch (_: Throwable) {}
        }
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
                "epocaNativa" to { NativeGasEvidenceEpoch.shared.markWrite(System.currentTimeMillis()) },
                "resetGas" to { equivalence.resetGas("CURVA_K_GRAVADA") },
                "cerebro" to { equivalenceRuntime.onGasReset("CURVA_K_GRAVADA", equivalencePhases) },
                "adoptCurve" to {
                    rawNow?.let { raw -> equivalence.adoptCurve(EquivalenceLedger.fingerprint(IntArray(30) { raw.optInt(it) })) }
                },
                "round" to { if (::nativeAutoCal.isInitialized) nativeAutoCal.invalidateRound() },
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
            val restoreWrite = RefinementJournal.isUndoReason(
                payload.optJSONArray("confirmedEvents")?.optJSONObject(0)?.optString("reason").orEmpty(),
            )
            refinementJournal.recordCurveWrite(
                beforeRaw = beforeRaw,
                afterRaw = afterRaw,
                axisRaw = IntArray(30) { axis.optInt(it) },
                indexBefore = indexBefore ?: JSONObject(),
                source = payload.optString("adjustmentId", "K_FACTOR"),
                photoFile = payload.optString("photoFile", ""),
                // Desfazer/Restaurar/Reset não é passada de ganho (o motivo vem do escritor da ECU, sem mudar comando algum).
                restore = restoreWrite,
            )
            // Cada ponto que o dono acabou de mudar entra em prova no cérebro único; voltar a uma foto não é experimento
            // (senão a prova podia dar CONTESTADO e oferecer "desfazer o Desfazer").
            if (!restoreWrite) equivalenceRuntime.onCurveWritten(beforeRaw, afterRaw, equivalencePhases)
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
                "epocaNativa" to { NativeGasEvidenceEpoch.shared.markWrite(System.currentTimeMillis()) },
                "resetGas" to { equivalence.resetGas("CURVA_K_FALHA_PARCIAL") },
                "cerebro" to { equivalenceRuntime.onGasReset("CURVA_K_FALHA_PARCIAL", equivalencePhases) },
                "round" to { if (::nativeAutoCal.isInitialized) nativeAutoCal.invalidateRound() },
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
            } else if (usb.connected && acquisition != null) {
                // Sem aquisição neste tick (assentamento de 8 s, snapshot falhou) a última referência lida continua valendo.
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
                fuel = equivalence.liveFuel(),
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
        val acquisitionNow = latestAcquisition()
            ?: return JSONObject().put("ok", false).put("reason", "SEM_ECU").put("message", "Conecte a ECU para salvar a referência.").toString()
        val reference = equivalenceRuntime.freeze(acquisitionNow, equivalencePhases)
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

    /** Reinício manual da evidência LOCAL: mesma invalidação usada quando a curva muda. */
    fun resetGasEvidence(): String {
        if (!usb.connected || kWriter.isBusy() || kFactor.isBusy() || SerialWriteGuard.shared.isHeld()) {
            return JSONObject().put("ok", false).put("message", "Conecte a ECU e aguarde o fim da operação atual.").toString()
        }
        val reason = "REINICIO_GNV_PELO_DONO"
        var failed = false
        EvidenceInvalidation.run(
            invalidate = listOf(
                "resetGas" to { equivalence.resetGas(reason) },
                "cerebro" to { equivalenceRuntime.onGasReset(reason, equivalencePhases) },
                "journal" to { refinementJournal.interrupt(reason) },
            ),
            record = { sessionRecorder.record("refino_gas_learning_reset", "refino", JSONObject().put("reason", reason), force = true) },
            warn = { if (it.startsWith("Invalidação")) failed = true; log.add("WARN", "EVIDENCIA", it) },
        )
        stateChanged()
        return JSONObject().put("ok", !failed).put("message", if (failed) "Não foi possível confirmar o reinício completo do aprendizado." else "Aprendizado GNV reiniciado. A gasolina continua como referência.").toString()
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
        analysisLane.submit(rerunKey = "analysisTick", task = ::analysisTick)
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
            resumeSessionRecordingIfStopped()
        } catch (error: Throwable) {
            healthFailures += 1
            try { log.add("WARN", "SERVICE", "Monitor nativo: ${error.message ?: error.javaClass.simpleName}") } catch (_: Throwable) {}
        }
    }

    /**
     * A gravação de sessão cai (disco cheio, limite de tamanho sem continuação, falha ao abrir) e antes não voltava
     * até reconectar o USB. Com a ECU conectada e a gravação parada (e o dono não a parou), tenta de novo com
     * espera crescente (5 s → 60 s). Só grava arquivo de sessão; nada vai à ECU.
     */
    private fun resumeSessionRecordingIfStopped() {
        if (!usb.connected || sessionStoppedByOwner || sessionRecorder.isRecording()) {
            if (sessionRecorder.isRecording()) sessionRestartFailures = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < sessionRestartNotBefore) return
        val started = startJournalSession(
            "retomada automática da gravação",
            JSONObject()
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("usb", usb.deviceLabel)
                .put("usbSessionId", usb.connectionSessionId)
                .put("legacySweep", JSONArray(legacySweepRemoved)),
        )
        if (started.optBoolean("ok", false)) {
            sessionRestartFailures = 0
            log.add("INFO", "SESSAO", "Gravação da sessão retomada")
        } else {
            sessionRestartFailures += 1
            val waitMs = (5_000L shl (sessionRestartFailures - 1).coerceAtMost(4)).coerceAtMost(60_000L)
            sessionRestartNotBefore = now + waitMs
            log.add("WARN", "SESSAO", "Gravação parada e sem retomar (${started.optString("error")}); nova tentativa em ${waitMs / 1_000L} s")
        }
    }

    /** Roda na faixa de análise (thread própria, coalescente). Só observa e grava; não toca a ECU. */
    private fun analysisTick() {
        if (stopping) return
        // Cada passo no próprio try/catch: uma exceção no diário não pode pular o stallWatch, o veredito,
        // o full_snapshot, o overlay nem a notificação.
        val steps = ArrayList<Pair<String, () -> Unit>>()
        fun step(name: String, block: () -> Unit) { steps += name to block }
        if (!refinementFrozenForRender) {
            step("diario") {
                // O orçamento da verificação conta só condução: rpm ≥ 1000 numa faixa alterada (quadro fresco).
                val frameFresh = System.currentTimeMillis() - lastDriveFrameAt < 3_500L
                if (refinementJournal.evaluate(
                        equivalence.index(), ecuOnline = usb.connected && runtime.ready,
                        rpm = if (frameFresh) lastDriveRpm else 0.0,
                        petrolMs = if (frameFresh) lastDrivePetrolMs else null,
                    )
                ) stateChanged()
            }
            // Silêncio da telemetria depois de uma queda de RPM decide "desligou" × "apagou".
            step("stallWatch") { stallWatch.tick(System.currentTimeMillis()) }
            step("stallNotas") { recordStallAnnotations() }
            step("decisaoDiario") { recordJournalDecision() }
            step("veredito") { recordVerdictIfClosed() }
            step("refino") { observeRefinement() }
        }
        step("full_snapshot") {
            if (sessionRecorder.shouldRecordFullSnapshot()) {
                sessionRecorder.record(
                    "full_snapshot",
                    "native",
                    try { JSONObject(fullEngineSnapshotJson()) } catch (_: Exception) { JSONObject() },
                )
            }
        }
        step("overlay") { updateOverlay() }
        step("notificacao") { updateNotification() }
        GuardedSteps.run(steps) { message ->
            synchronized(this) { healthFailures += 1 }
            log.add("WARN", "SERVICE", "Análise nativa: $message")
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
        } catch (error: Throwable) {
            try { log.add("WARN", "AUTOCAL-NATIVE", "Refresh nativo: ${error.message ?: error.javaClass.simpleName}") } catch (_: Throwable) {}
        }
    }

    @Volatile private var lastDriveRpm = 0.0
    /** Nulo = quadro sem leitura de tempo de injeção (desconhecido, nunca 0 ms medido). */
    @Volatile private var lastDrivePetrolMs: Double? = null
    @Volatile private var lastDriveFrameAt = 0L

    private fun consumeEngineEvent(root: JSONObject) {
        val accepted = telemetryStore.updateFromEngineEvent(root) ?: return
        val live = root.optJSONObject("live") ?: root.optJSONObject("data") ?: JSONObject()
        lastDriveRpm = live.optDouble("rpm", 0.0)
        lastDrivePetrolMs = if (live.has("petrol_ms") && !live.isNull("petrol_ms")) {
            live.optDouble("petrol_ms", Double.NaN).takeIf { it.isFinite() }
        } else null
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
                waterC = if (live.has("water_c") && !live.isNull("water_c")) live.optDouble("water_c", Double.NaN) else Double.NaN,
                dynamicCorrection = if (live.has("dynamic_correction") && !live.isNull("dynamic_correction")) live.optInt("dynamic_correction", -1) else -1,
                capturedMs = live.optLong("captured_elapsed_ms", -1L),
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
            log.add("WARN", "REFINO", "$verb no GNV em ${Units.msUnit(event.optDouble("petrolMs"))} · MAP ${Units.mapUnit(event.optDouble("mapBar"))}")
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

    /** Primeiro foreground do serviço: não depende de nenhum estado ainda não carregado. */
    private fun startForegroundBootstrap() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                NotificationController.NOTIFICATION_ID,
                notifications.build(HubStatus(serviceRunning = true)),
                type,
            )
        } catch (_: Exception) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NotificationController.NOTIFICATION_ID,
                    notifications.build(HubStatus(serviceRunning = true)),
                    0,
                )
            } catch (_: Exception) {}
        }
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
        // Primeiro o tempo (>= 1 s desde a última avaliação, mudou ou não), depois o conteúdo: montar status()
        // a cada quadro só para descobrir que nada mudou gastava CPU.
        if (now - lastNotificationAt < 1_000L) return
        lastNotificationAt = now
        // Só reposta quando título/texto/ações mudaram.
        val content = notifications.content(status())
        if (content == lastNotificationContent) return
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