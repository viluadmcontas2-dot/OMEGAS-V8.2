package com.omegas.prohub.ecu

import android.os.SystemClock
import com.omegas.prohub.adaptive.AdaptiveShadowObserver
import com.omegas.prohub.calibration.CalibrationIdentity
import com.omegas.prohub.calibration.CalibrationProvenance
import com.omegas.prohub.calibration.CompositeCalibrationReader
import com.omegas.prohub.calibration.CompositeCalibrationSnapshot
import com.omegas.prohub.learning.DeferredLiveOnlyLearningStore
import com.omegas.prohub.learning.LearningCalibrationAuthority
import com.omegas.prohub.learning.LearningCalibrationBinding
import com.omegas.prohub.learning.LiveOnlyLearningStore
import com.omegas.prohub.learning.SampleDecision
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.telemetry.CanonicalEvidence
import com.omegas.prohub.telemetry.LatestOnlyState
import com.omegas.prohub.telemetry.RuntimeTelemetryFrame
import com.omegas.prohub.usb.UsbSerialManager
import com.omegas.prohub.util.LatestOnlyBackgroundPipeline
import com.omegas.prohub.util.RealtimeLearningBuffer
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime único do aplicativo.
 *
 * O ciclo de vida da conexão técnica acompanha a conexão física USB. Um
 * simples reinício do loop não cria outra sessão nem aumenta confiança.
 *
 * A thread da ECU publica um único CanonicalEvidence tipado e enfileira
 * consumidores bounded. JSON existe apenas na projeção de compatibilidade,
 * construída no worker de delivery depois que a thread ECU já foi liberada.
 */
class NativeRuntimeManager(
    paths: AppPaths,
    private val usb: UsbSerialManager,
    private val log: RingLog,
    private val onStateChanged: () -> Unit,
    private val onTelemetryEvent: (JSONObject) -> Unit,
    private val onEngineExited: (Boolean) -> Unit,
) {
    private val snapshotLock = Any()
    private val learningSessionLock = Any()
    private val learning = DeferredLiveOnlyLearningStore(paths.runtimeRoot, log)
    private val adaptiveShadow = AdaptiveShadowObserver()
    private val telemetryDeliveryPipeline = LatestOnlyBackgroundPipeline(
        threadName = "omegas-telemetry-delivery",
        threadPriority = Thread.NORM_PRIORITY,
        consumerName = "UI_PROJECTION",
        onFailure = { sequence, error ->
            log.add(
                "ERROR",
                "TELEMETRY-DELIVERY",
                "Falha ao entregar quadro $sequence fora da thread ECU: ${error.message}",
            )
        },
    )
    private val adaptiveShadowPipeline = LatestOnlyBackgroundPipeline(
        threadName = "omegas-adaptive-shadow",
        threadPriority = Thread.NORM_PRIORITY - 1,
        consumerName = "ADAPTIVE_SHADOW",
        onFailure = { sequence, error ->
            log.add(
                "ERROR",
                "ADAPTIVE-SHADOW",
                "Falha ao observar CanonicalEvidence $sequence: ${error.message}",
            )
        },
    )
    private val learningPipeline = RealtimeLearningBuffer(
        threadName = "omegas-learning-realtime",
        importantCapacity = 128,
        threadPriority = Thread.NORM_PRIORITY - 1,
        consumerName = "CLASSIC_SCIENCE",
        onFailure = { sequence, error ->
            log.add(
                "ERROR",
                "LEARNING-PIPELINE",
                "Falha ao processar quadro $sequence: ${error.message}",
            )
        },
    )
    private val engine = ResponseDrivenEcuEngine(
        usb = usb,
        log = log,
        onTelemetry = ::consumeTelemetry,
        onStateChanged = ::consumeState,
    )
    private val serialAdmission = Mp48BackpressureScheduler(engine)
    private val calibrationIdentityReader = CompositeCalibrationReader(serialAdmission)
    private val calibrationIdentityExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-calibration-identity").apply { isDaemon = true }
    }
    private val calibrationIdentityRefreshPending = AtomicBoolean(false)
    private val latestCanonicalEvidence = LatestOnlyState<CanonicalEvidence>(
        sequenceOf = { it.sequence },
        generationOf = { it.usbSessionId },
    )

    @Volatile private var latestLearningState = safeLearningStatus()
    @Volatile private var latestLearningSequence = 0L
    @Volatile private var latestSnapshot = emptySnapshot()
    @Volatile private var intentionalStop = false
    @Volatile private var crashed = false
    @Volatile private var exitReported = false
    @Volatile private var currentUsbSessionId = 0L
    @Volatile private var calibrationIdentityState = "UNKNOWN"
    @Volatile private var calibrationIdentityFingerprint = ""
    @Volatile private var calibrationIdentityGeometry = ""
    @Volatile private var calibrationIdentityGeneration = -1
    @Volatile private var calibrationIdentityError = ""

    @Volatile var running = false
        private set
    @Volatile var ready = false
        private set
    @Volatile var stuck = false
        private set
    @Volatile var lastError = ""
        private set
    @Volatile var startedAt = 0L
        private set
    @Volatile var exitCount = 0
        private set

    /** Deve ser chamado somente quando uma nova conexão física USB é aberta. */
    fun beginUsbSession(sessionId: Long): JSONObject {
        require(sessionId > 0L) { "Sessão USB inválida" }
        LearningCalibrationAuthority.beginPhysicalSession()
        calibrationIdentityState = "PENDING"
        calibrationIdentityFingerprint = ""
        calibrationIdentityGeometry = ""
        calibrationIdentityGeneration = -1
        calibrationIdentityError = ""
        val learningState = synchronized(learningSessionLock) {
            currentUsbSessionId = sessionId
            latestLearningSequence = 0L
            learningPipeline.beginGeneration(sessionId)
            learning.startSession()
        }
        latestCanonicalEvidence.beginGeneration(sessionId)
        adaptiveShadow.beginSession(sessionId)
        engine.beginUsbSession(sessionId)
        publishLearningState(0L, learningState)
        synchronized(snapshotLock) { latestSnapshot = emptySnapshot(sessionId, "INITIALIZING") }
        ready = false
        return JSONObject(learningState.toString())
    }

    /** Fecha somente a conexão física; a memória confirmada e a sessão gravada permanecem. */
    fun endUsbSession(reason: String): JSONObject {
        val endingSession = currentUsbSessionId
        learningPipeline.flush(750L)
        adaptiveShadowPipeline.flush(250L)
        currentUsbSessionId = 0L
        LearningCalibrationAuthority.endPhysicalSession()
        calibrationIdentityState = "UNKNOWN"
        calibrationIdentityFingerprint = ""
        calibrationIdentityGeometry = ""
        calibrationIdentityGeneration = -1
        latestCanonicalEvidence.clear()
        adaptiveShadow.endSession(endingSession)
        learningPipeline.endGeneration(endingSession, 1L)
        engine.endUsbSession()
        val learningState = synchronized(learningSessionLock) { learning.endSession(reason) }
        publishLearningState(latestLearningSequence, learningState)
        synchronized(snapshotLock) { latestSnapshot = emptySnapshot(0L, reason) }
        ready = false
        return JSONObject(learningState.toString())
    }

    @Synchronized
    fun start(): Boolean {
        if (running || stuck || !usb.connected) return false
        intentionalStop = false
        crashed = false
        exitReported = false
        lastError = ""
        ready = false
        startedAt = System.currentTimeMillis()
        val ok = engine.start()
        running = ok
        if (!ok) {
            lastError = "A engine Android nativa não iniciou"
        } else {
            log.add("INFO", "ECU-NATIVE", "Runtime Android iniciado")
            scheduleCalibrationIdentityRefresh(
                expectedSessionId = currentUsbSessionId,
                provenance = CalibrationProvenance.FULL_ECU_READ,
                force = false,
            )
        }
        onStateChanged()
        return ok
    }

    @Synchronized
    fun stop(timeoutSeconds: Long = 8): Boolean {
        if (!running && !engine.isRunning()) {
            ready = false
            flushPipelines("parada com engine já inativa", timeoutSeconds * 1_000L)
            return true
        }
        intentionalStop = true
        engine.stop(graceful = true)
        val deadline = SystemClock.elapsedRealtime() + timeoutSeconds.coerceAtLeast(1) * 1_000L
        while (engine.isRunning() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20L)
        }
        val stopped = !engine.isRunning()
        if (!stopped) {
            stuck = true
            lastError = "O núcleo Android não encerrou em ${timeoutSeconds}s"
            log.add("ERROR", "ECU-NATIVE", lastError)
        } else {
            flushPipelines("após parar engine", timeoutSeconds * 1_000L)
            running = false
            ready = false
            stuck = false
            reportExit(false)
        }
        onStateChanged()
        return stopped
    }

    @Synchronized
    fun restart(): Boolean {
        if (!stop()) return false
        return start()
    }

    /** Única autoridade serial disponibilizada aos managers Android. */
    fun serialScheduler(): Mp48SerialScheduler = serialAdmission

    /** Estado vivo tipado derivado do envelope canônico, nunca reconstruído de JSON. */
    fun currentTelemetryFrame(): RuntimeTelemetryFrame? = latestCanonicalEvidence.current()?.frame

    fun telemetryStateMetricsJson(): JSONObject = latestCanonicalEvidence.metrics().let { metrics ->
        JSONObject()
            .put("schema", CanonicalEvidence.SCHEMA)
            .put("generation", metrics.generation)
            .put("published", metrics.published)
            .put("replaced", metrics.replaced)
            .put("rejected", metrics.rejected)
    }

    private fun calibrationIdentityStatusJson(): JSONObject = JSONObject()
        .put("state", calibrationIdentityState)
        .put("fingerprint", calibrationIdentityFingerprint.ifBlank { JSONObject.NULL })
        .put("geometryFingerprint", calibrationIdentityGeometry.ifBlank { JSONObject.NULL })
        .put("generation", if (calibrationIdentityGeneration >= 0) calibrationIdentityGeneration else JSONObject.NULL)
        .put("usbSessionId", currentUsbSessionId)
        .put("refreshPending", calibrationIdentityRefreshPending.get())
        .apply { if (calibrationIdentityError.isNotBlank()) put("error", calibrationIdentityError) }

    fun statusJson(): JSONObject = engine.statusJson()
        .put("native", true)
        .put("running", running)
        .put("ready", ready)
        .put("startedAt", startedAt)
        .put("last_error", lastError)
        .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
        .put("learningScaleMigration", learning.migrationStatus())
        .put("telemetryDeliveryPipeline", telemetryDeliveryPipeline.metricsJson())
        .put("learningPipeline", learningPipeline.metricsJson())
        .put("adaptiveShadowPipeline", adaptiveShadowPipeline.metricsJson())
        .put("adaptiveShadow", adaptiveShadow.metricsJson())
        .put("serialAdmission", serialAdmission.metricsJson())
        .put("typedTelemetryState", telemetryStateMetricsJson())
        .put("calibrationIdentity", calibrationIdentityStatusJson())

    fun fullSnapshotJson(): String = snapshotJson()

    fun metricsJson(): String = statusJson()
        .put("learning", cachedLearningState())
        .toString()

    fun protocolJson(): String = JSONObject()
        .put("ok", true)
        .put("native", true)
        .put("mode", "response-driven")
        .put("baud", 9_600)
        .put("format", "8N1")
        .put("telemetry", hex(Mp48Protocol.CMD_TELEMETRY))
        .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
        .put("disconnect", hex(Mp48Protocol.CMD_DISCONNECT))
        .put("mapRows", Mp48Protocol.MAP_ROWS)
        .put("mapColumns", Mp48Protocol.MAP_COLUMNS)
        .put("status", statusJson())
        .toString()

    fun selfTestJson(): String {
        val telemetryChecksum = Mp48Protocol.checksum(byteArrayOf(0x48, 0x01))
        val disconnectChecksum = Mp48Protocol.checksum(byteArrayOf(0x00, 0x01))
        val readRow = Mp48Protocol.readKRow(0)
        val writeCell = Mp48Protocol.writeKCell(0, 0, 0x93)
        val ok = telemetryChecksum == 0x49 &&
            disconnectChecksum == 0x01 &&
            (readRow.last().toInt() and 0xFF) ==
                Mp48Protocol.checksum(readRow.copyOfRange(0, readRow.lastIndex)) &&
            (writeCell.last().toInt() and 0xFF) ==
                Mp48Protocol.checksum(writeCell.copyOfRange(0, writeCell.lastIndex))
        return JSONObject()
            .put("ok", ok)
            .put("native", true)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("telemetryChecksum", telemetryChecksum)
            .put("disconnectChecksum", disconnectChecksum)
            .put("readRowFrame", hex(readRow))
            .put("writeCellFrame", hex(writeCell))
            .put("telemetryDeliveryPipeline", telemetryDeliveryPipeline.metricsJson())
            .put("learningPipeline", learningPipeline.metricsJson())
            .put("adaptiveShadowPipeline", adaptiveShadowPipeline.metricsJson())
            .put("adaptiveShadow", adaptiveShadow.metricsJson())
            .put("serialAdmission", serialAdmission.metricsJson())
            .put("typedTelemetryState", telemetryStateMetricsJson())
            .put("calibrationIdentity", calibrationIdentityStatusJson())
            .toString()
    }

    fun exportLearning(deviceId: String): JSONObject {
        flushLearning("antes de exportar aprendizado")
        val exported = learning.export(deviceId)
        if (!exported.optBoolean("ok", false)) return exported.put("componentRevision", 0L)
        val canonical = JSONObject(exported.toString()).apply {
            remove("exportedAt")
            remove("componentRevision")
        }.toString()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        var revision = 0L
        repeat(7) { index -> revision = (revision shl 8) or (digest[index].toLong() and 0xFFL) }
        return exported.put("componentRevision", revision)
    }

    fun mergeLearning(payload: JSONObject, localDeviceId: String = ""): JSONObject {
        flushLearning("antes de mesclar aprendizado")
        val result = learning.merge(payload, localDeviceId)
        publishLearningState(latestLearningSequence, safeLearningStatus())
        return result
    }

    /**
     * Imports only contextual evidence from a read-only AutoCal snapshot.
     * It never prepares or invokes any ECU writer.
     */
    fun importNativeAutoCalSnapshot(snapshot: JSONObject): JSONObject {
        flushLearning("antes de importar contexto AutoCal")
        val result = learning.importNativeSnapshot(snapshot)
            .put("automatic", false)
            .put("manualOnly", true)
        publishLearningState(latestLearningSequence, safeLearningStatus())
        return result
    }

    fun learningStatus(): JSONObject {
        val cached = cachedLearningState()
        if (cached.optString("state") == DeferredLiveOnlyLearningStore.STATE_RESTORING) {
            val refreshed = safeLearningStatus()
            if (!refreshed.optBoolean("restoring", false)) {
                publishLearningState(latestLearningSequence, refreshed)
            }
        }
        val current = cachedLearningState()
        return current
            .put("ok", current.optBoolean("ok", true))
            .put("format", LiveOnlyLearningStore.FORMAT)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("scaleMigration", learning.migrationStatus())
            .put("pipeline", learningPipeline.metricsJson())
            .put("calibrationIdentity", calibrationIdentityStatusJson())
    }

    fun notifyCalibrationAdjustment(payload: JSONObject): JSONObject {
        flushLearning("antes de registrar ajuste confirmado")
        LearningCalibrationAuthority.clear()
        calibrationIdentityState = "PENDING"
        calibrationIdentityFingerprint = ""
        calibrationIdentityGeometry = ""
        calibrationIdentityGeneration = -1
        calibrationIdentityError = ""
        val result = learning.onCalibrationAdjustment(payload)
        publishLearningState(latestLearningSequence, safeLearningStatus())
        val provenance = if (payload.optString("source") == "ECU_NATIVE_AUTOCAL") {
            CalibrationProvenance.AUTOCAL_RECONCILE
        } else {
            CalibrationProvenance.POST_WRITE_READBACK
        }
        scheduleCalibrationIdentityRefresh(currentUsbSessionId, provenance, force = true)
        return result
    }

    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject {
        flushLearning("antes de preparar sugestão manual")
        return learning.previewKWrite(row, column, value)
    }

    fun close() {
        stop(3)
        LearningCalibrationAuthority.endPhysicalSession()
        flushPipelines("encerramento do runtime", 2_000L)
        try { telemetryDeliveryPipeline.close() } catch (_: Exception) {}
        try { adaptiveShadowPipeline.close() } catch (_: Exception) {}
        try { learningPipeline.close() } catch (_: Exception) {}
        try { engine.close() } catch (_: Exception) {}
        try { learning.close() } catch (_: Exception) {}
        calibrationIdentityExecutor.shutdownNow()
    }

    private fun scheduleCalibrationIdentityRefresh(
        expectedSessionId: Long,
        provenance: CalibrationProvenance,
        force: Boolean,
    ) {
        if (expectedSessionId <= 0L || expectedSessionId != currentUsbSessionId || !usb.connected) return
        val current = LearningCalibrationAuthority.snapshot()
        if (!force && current != null && current.usbSessionId == expectedSessionId) return
        if (!calibrationIdentityRefreshPending.compareAndSet(false, true)) return
        calibrationIdentityState = "READING"
        calibrationIdentityError = ""
        calibrationIdentityExecutor.execute {
            try {
                if (expectedSessionId != currentUsbSessionId || !usb.connected) return@execute
                val raw = calibrationIdentityReader.readAtSessionStart(expectedSessionId)
                val composite = CompositeCalibrationSnapshot.promote(raw)
                val identity = CalibrationIdentity.fromComposite(
                    composite = composite,
                    capturedAtMs = SystemClock.elapsedRealtime(),
                    mapRevision = null,
                    curveRevision = null,
                    provenance = provenance,
                )
                if (expectedSessionId != currentUsbSessionId || !usb.connected) return@execute
                val binding = LearningCalibrationBinding.fromIdentity(identity, composite.mapGeometry)
                LearningCalibrationAuthority.publish(binding)
                calibrationIdentityFingerprint = binding.calibrationFingerprint
                calibrationIdentityGeometry = binding.geometryFingerprint
                calibrationIdentityGeneration = binding.calibrationGeneration
                calibrationIdentityState = "KNOWN"
                calibrationIdentityError = ""
                log.add(
                    "INFO",
                    "CALIBRATION-IDENTITY",
                    "Identidade física reconciliada para ciência GNV • generation=${binding.calibrationGeneration}",
                )
            } catch (error: Exception) {
                if (expectedSessionId == currentUsbSessionId) {
                    LearningCalibrationAuthority.clear()
                    calibrationIdentityState = "UNKNOWN"
                    calibrationIdentityFingerprint = ""
                    calibrationIdentityGeometry = ""
                    calibrationIdentityGeneration = -1
                    calibrationIdentityError = error.message ?: error.javaClass.simpleName
                    log.add(
                        "WARN",
                        "CALIBRATION-IDENTITY",
                        "GNV permanece fora da ciência ativa até nova identidade física válida: $calibrationIdentityError",
                    )
                }
            } finally {
                calibrationIdentityRefreshPending.set(false)
            }
        }
    }

    private fun consumeTelemetry(
        telemetry: Mp48Telemetry,
        decision: SampleDecision,
        metrics: EngineMetrics,
    ) {
        val sequence = metrics.telemetryFrames
        val generation = currentUsbSessionId
        if (generation <= 0L) return
        val evidence = CanonicalEvidence.from(
            telemetry = telemetry,
            decision = decision,
            sequence = sequence,
            usbSessionId = generation,
        )
        if (!latestCanonicalEvidence.publish(evidence)) return

        running = true
        ready = true
        lastError = ""
        if (!telemetryDeliveryPipeline.submit(sequence) {
                projectTelemetryCompatibility(
                    evidence = evidence,
                    metrics = metrics,
                    generation = generation,
                )
            }
        ) {
            log.add("WARN", "TELEMETRY-DELIVERY", "Quadro $sequence não aceito porque a fila está encerrando")
        }

        adaptiveShadowPipeline.submit(sequence) {
            if (generation == currentUsbSessionId) adaptiveShadow.observe(evidence)
        }

        val important = evidence.sampleDecision.sample != null
        val accepted = learningPipeline.submit(
            generation = generation,
            sequence = sequence,
            important = important,
        ) {
            if (generation != currentUsbSessionId) return@submit
            synchronized(learningSessionLock) {
                if (generation != currentUsbSessionId) return@synchronized
                val processed = learning.ingest(evidence.rawTelemetry, evidence.sampleDecision)
                if (generation == currentUsbSessionId) publishLearningState(sequence, processed)
            }
        }
        if (!accepted && important && generation == currentUsbSessionId) {
            log.add(
                "WARN",
                "LEARNING-BUFFER",
                "Amostra $sequence não coube no buffer científico bounded; sessão gravada preserva a evidência.",
            )
        }
    }

    /**
     * Projeção visual executada somente no worker de delivery. O mesmo
     * CanonicalEvidence que alimenta State/Classic/Adaptive fornece os dados e a
     * proveniência gravados pelo Recorder através deste evento de compatibilidade.
     */
    private fun projectTelemetryCompatibility(
        evidence: CanonicalEvidence,
        metrics: EngineMetrics,
        generation: Long,
    ) {
        if (generation != currentUsbSessionId || evidence.usbSessionId != generation) return
        val telemetry = evidence.rawTelemetry
        val decision = evidence.sampleDecision
        val live = telemetry.toJson()
            .put("session_id", generation)
            .put("version", "OMEGAS-NATIVE-CORE-5")
            .put("link", "ONLINE")
            .put("transaction", "IDLE")
            .put("sample_state", decision.state)
            .put("sample_reason", decision.reason)
            .put("sample_frame_count", decision.frameCount)
            .put("sample_minimum_frames", decision.minimumFrames)
            .put("sample_desired_frames", decision.desiredFrames)
            .put("sample_duration_ms", decision.durationMs)
            .put("sample", decision.toTelemetryJson())
            .put("learning_quality", decision.sample?.quality ?: 0.0)
            .put("stable_ms", decision.durationMs)
            .put("k_interpolated", 0.0)
            .put("k_suggested", JSONObject.NULL)
            .put("delta_k", JSONObject.NULL)
            .put("canonical_evidence_schema", CanonicalEvidence.SCHEMA)
            .put("canonical_provenance", evidence.provenance.toJson())
            .put("last_frame_at", System.currentTimeMillis() / 1000.0)
            .put("last_frame_age_ms", 0)

        val runtime = metrics.toJson()
            .put("native", true)
            .put("link", "ONLINE")
            .put("serial_ready", true)
            .put("last_error", "")
            .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("telemetry_delivery_pipeline", telemetryDeliveryPipeline.metricsJson())
            .put("learning_pipeline", learningPipeline.metricsJson())
            .put("adaptive_shadow_pipeline", adaptiveShadowPipeline.metricsJson())
            .put("adaptive_shadow", adaptiveShadow.metricsJson())
            .put("typed_telemetry_state", telemetryStateMetricsJson())
            .put("calibration_identity", calibrationIdentityStatusJson())

        val event = synchronized(snapshotLock) {
            if (generation != currentUsbSessionId) return@synchronized null
            val learningState = learningLiveSummary()
            live.put("surface_cell", learningState.optString("state", "OBSERVING_ENGINE"))
                .put(
                    "current_cell_confidence",
                    learningState.optDouble("reference_confidence", learningState.optDouble("quality", 0.0)),
                )
            val root = JSONObject()
                .put("event", "telemetry")
                .put("session_id", generation)
                .put("version", "OMEGAS-NATIVE-CORE-5")
                .put("canonical_evidence_schema", CanonicalEvidence.SCHEMA)
                .put("canonical_provenance", evidence.provenance.toJson())
                .put("live", live)
                .put("runtime", runtime)
                .put("learning_state", learningState)
                .put("learning", learningState)
            latestSnapshot = root
            root
        } ?: return
        if (generation == currentUsbSessionId) onTelemetryEvent(event)
    }

    private fun publishLearningState(sequence: Long, source: JSONObject) {
        if (sequence < latestLearningSequence) return
        val copy = JSONObject(source.toString())
        latestLearningSequence = sequence
        latestLearningState = copy
        val summary = learningLiveSummary()
        synchronized(snapshotLock) {
            val root = JSONObject(latestSnapshot.toString())
            root.put("learning_state", summary)
            root.put("learning", summary)
            root.optJSONObject("live")?.let { live ->
                live.put("surface_cell", summary.optString("state", "OBSERVING_ENGINE"))
                live.put(
                    "current_cell_confidence",
                    summary.optDouble("reference_confidence", summary.optDouble("quality", 0.0)),
                )
            }
            latestSnapshot = root
        }
    }

    private fun consumeState(status: JSONObject) {
        val state = status.optString("state")
        val wasRunning = running
        running = engine.isRunning()
        ready = engine.isSessionReady()
        lastError = status.optString("lastError", status.optString("message", lastError))
        if (state == "ERROR") crashed = true
        synchronized(snapshotLock) {
            val root = JSONObject(latestSnapshot.toString())
            root.put(
                "runtime",
                statusJson()
                    .put("link", if (ready) "ONLINE" else state)
                    .put("last_error", lastError)
                    .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA),
            )
            latestSnapshot = root
        }
        if (state == "STOPPED") {
            running = false
            ready = false
            if (wasRunning && !intentionalStop) reportExit(crashed)
        }
        onStateChanged()
    }

    private fun reportExit(wasCrash: Boolean) {
        if (exitReported) return
        exitReported = true
        exitCount += 1
        onEngineExited(wasCrash)
    }

    private fun snapshotJson(): String = synchronized(snapshotLock) {
        val fullLearning = cachedLearningState()
        JSONObject(latestSnapshot.toString())
            .put("learning", fullLearning)
            .put("learning_state", learningLiveSummary())
            .put("telemetry_delivery_pipeline", telemetryDeliveryPipeline.metricsJson())
            .put("learning_pipeline", learningPipeline.metricsJson())
            .put("adaptive_shadow_pipeline", adaptiveShadowPipeline.metricsJson())
            .put("adaptive_shadow", adaptiveShadow.metricsJson())
            .put("typed_telemetry_state", telemetryStateMetricsJson())
            .put("calibration_identity", calibrationIdentityStatusJson())
            .toString()
    }

    private fun cachedLearningState(): JSONObject = JSONObject(latestLearningState.toString())

    private fun learningLiveSummary(): JSONObject {
        val state = latestLearningState
        return JSONObject()
            .put("state", state.optString("state", "OBSERVING_ENGINE"))
            .put("reason", state.optString("reason", ""))
            .put("learning", state.optBoolean("learning", false))
            .put("reference_confidence", state.optDouble("reference_confidence", 0.0))
            .put("quality", state.optDouble("quality", 0.0))
            .put("epoch", state.optInt("epoch", 1))
            .put("calibration_identity_ready", state.optBoolean("calibration_identity_ready", false))
            .put("pipeline_pending", learningPipeline.metricsJson().optLong("pending", 0L))
    }

    private fun safeLearningStatus(): JSONObject = try {
        JSONObject(learning.statusJson().toString())
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("state", "LEARNING_STATUS_UNAVAILABLE")
            .put("error", error.message ?: "Estado de aprendizado indisponível")
    }

    private fun flushLearning(boundary: String, timeoutMs: Long = 10_000L): Boolean {
        val ok = learningPipeline.flush(timeoutMs)
        if (!ok) {
            log.add("WARN", "LEARNING-PIPELINE", "Buffer não drenou em $boundary dentro de ${timeoutMs}ms")
        }
        return ok
    }

    private fun flushPipelines(boundary: String, timeoutMs: Long = 10_000L): Boolean {
        val deliveryOk = telemetryDeliveryPipeline.flush(timeoutMs)
        val adaptiveOk = adaptiveShadowPipeline.flush(timeoutMs.coerceAtMost(1_000L))
        val learningOk = learningPipeline.flush(timeoutMs.coerceAtMost(2_000L))
        if (!deliveryOk) {
            log.add("WARN", "TELEMETRY-DELIVERY", "Fila não drenou em $boundary dentro de ${timeoutMs}ms")
        }
        if (!adaptiveOk) {
            log.add("WARN", "ADAPTIVE-SHADOW", "Fila não drenou em $boundary dentro de ${timeoutMs.coerceAtMost(1_000L)}ms")
        }
        if (!learningOk) {
            log.add("WARN", "LEARNING-PIPELINE", "Buffer não drenou em $boundary dentro de ${timeoutMs.coerceAtMost(2_000L)}ms")
        }
        return deliveryOk && adaptiveOk && learningOk
    }

    private fun emptySnapshot(sessionId: Long = 0L, reason: String = "OFFLINE"): JSONObject = JSONObject()
        .put("version", "OMEGAS-NATIVE-CORE-5")
        .put("session_id", sessionId)
        .put("telemetry_valid", false)
        .put("canonical_evidence_schema", CanonicalEvidence.SCHEMA)
        .put(
            "live",
            JSONObject()
                .put("state", reason)
                .put("rpm", 0)
                .put("petrol_ms", 0.0)
                .put("load_bar", 0.0),
        )
        .put(
            "runtime",
            JSONObject()
                .put("native", true)
                .put("link", "OFFLINE")
                .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA),
        )
        .put("learning", JSONObject())
        .put("learning_state", JSONObject())

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
