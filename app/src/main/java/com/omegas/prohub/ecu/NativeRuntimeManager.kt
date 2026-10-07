package com.omegas.prohub.ecu

import android.os.SystemClock
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.usb.UsbSerialManager
import com.omegas.prohub.util.LatestOnlyBackgroundPipeline
import com.omegas.prohub.util.RingLog
import org.json.JSONObject

/**
 * Runtime único do aplicativo.
 *
 * O ciclo de vida da conexão técnica acompanha a conexão física USB. Um
 * simples reinício do loop não cria outra sessão nem aumenta confiança.
 *
 * A thread da ECU publica somente o quadro leve e volta imediatamente ao ciclo
 * MP48: montar o JSON do quadro, copiar o snapshot e avisar overlay/notificação
 * acontecem em filas próprias (latest-only), nunca na thread serial. A entrega
 * visual mantém somente o quadro mais recente enquanto o consumidor está ocupado. A sessão gravada permanece como backlog frio/durável para
 * auditoria/exportação.
 */
class NativeRuntimeManager(
    @Suppress("UNUSED_PARAMETER") paths: AppPaths,
    private val usb: UsbSerialManager,
    private val log: RingLog,
    private val onStateChanged: () -> Unit,
    private val onTelemetryEvent: (JSONObject) -> Unit,
    private val onEngineExited: (Boolean) -> Unit,
) {
    private val snapshotLock = Any()
    private val telemetryDeliveryPipeline = LatestOnlyBackgroundPipeline(
        threadName = "omegas-telemetry-delivery",
        threadPriority = Thread.NORM_PRIORITY,
        onFailure = { sequence, error ->
            log.add(
                "ERROR",
                "TELEMETRY-DELIVERY",
                "Falha ao entregar quadro $sequence fora da thread ECU: ${error.message}",
            )
        },
    )
    /** Estado da engine (merge do snapshot + overlay/notificação) fora da thread serial. */
    private val stateDeliveryPipeline = LatestOnlyBackgroundPipeline(
        threadName = "omegas-engine-state-delivery",
        threadPriority = Thread.NORM_PRIORITY,
        onFailure = { sequence, error ->
            log.add(
                "ERROR",
                "ENGINE-STATE-DELIVERY",
                "Falha ao entregar estado $sequence fora da thread ECU: ${error.message}",
            )
        },
    )
    private val stateSequence = java.util.concurrent.atomic.AtomicLong(0L)
    /** Saída da engine ainda não avisada (true = crash); entregue pela fila de estado, nunca descartada. */
    private val pendingExitNotice = java.util.concurrent.atomic.AtomicReference<Boolean?>(null)
    private val engine = ResponseDrivenEcuEngine(
        usb = usb,
        log = log,
        onTelemetry = ::consumeTelemetry,
        onStateChanged = ::consumeState,
    )
    private val serialAdmission = Mp48BackpressureScheduler(engine)

    @Volatile private var latestSnapshot = emptySnapshot()
    @Volatile private var intentionalStop = false
    @Volatile private var crashed = false
    @Volatile private var exitReported = false
    @Volatile private var currentUsbSessionId = 0L

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
    fun beginUsbSession(sessionId: Long) {
        currentUsbSessionId = sessionId
        engine.beginUsbSession(sessionId)
        synchronized(snapshotLock) { latestSnapshot = emptySnapshot(sessionId, "INITIALIZING") }
        ready = false
    }

    /** Fecha somente a conexão física; a memória confirmada e a sessão gravada permanecem. */
    fun endUsbSession(reason: String) {
        currentUsbSessionId = 0L
        engine.endUsbSession()
        synchronized(snapshotLock) { latestSnapshot = emptySnapshot(0L, reason) }
        ready = false
    }

    @Synchronized
    fun start(): Boolean {
        // `stuck` só vale enquanto a engine de fato ainda roda; se ela terminou depois do prazo, solta.
        if (stuck && !engine.isRunning()) stuck = false
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
        }
        onStateChanged()
        return ok
    }

    @Synchronized
    fun stop(timeoutSeconds: Long = 8): Boolean {
        if (!running && !engine.isRunning()) {
            ready = false
            stuck = false
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

    fun statusJson(): JSONObject = engine.statusJson()
        .put("native", true)
        .put("running", running)
        .put("ready", ready)
        .put("startedAt", startedAt)
        .put("last_error", lastError)
        .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
        .put("telemetryDeliveryPipeline", telemetryDeliveryPipeline.metricsJson())
        .put("stateDeliveryPipeline", stateDeliveryPipeline.metricsJson())
        .put("serialAdmission", serialAdmission.metricsJson())

    fun fullSnapshotJson(): String = snapshotJson()

    fun metricsJson(): String = statusJson().toString()

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
            .toString()
    }

    fun close() {
        stop(3)
        flushPipelines("encerramento do runtime", 2_000L)
        try { telemetryDeliveryPipeline.close() } catch (_: Exception) {}
        try { stateDeliveryPipeline.close() } catch (_: Exception) {}
        try { engine.close() } catch (_: Exception) {}
    }

    /**
     * Roda na thread serial: só marca flags baratas e entrega o quadro imutável à fila.
     * O JSON (quadro + amostra + métricas) é montado na thread de entrega.
     */
    private fun consumeTelemetry(
        telemetry: Mp48Telemetry,
        decision: SampleDecision,
        metrics: EngineMetrics,
    ) {
        val sequence = metrics.telemetryFrames
        val generation = currentUsbSessionId
        val frameAtWallMs = System.currentTimeMillis()
        running = true
        ready = true
        lastError = ""

        val accepted = telemetryDeliveryPipeline.submit(sequence) {
            val root = telemetryEvent(telemetry, decision, metrics, generation, frameAtWallMs)
            synchronized(snapshotLock) {
                // Quadro atrasado de uma sessão USB já encerrada não sobrescreve o snapshot novo.
                if (generation == currentUsbSessionId) latestSnapshot = root
            }
            onTelemetryEvent(root)
        }
        if (!accepted) {
            log.add("WARN", "TELEMETRY-DELIVERY", "Quadro $sequence não aceito porque a fila está encerrando")
        }
    }

    private fun telemetryEvent(
        telemetry: Mp48Telemetry,
        decision: SampleDecision,
        metrics: EngineMetrics,
        generation: Long,
        frameAtWallMs: Long,
    ): JSONObject {
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
            .put("last_frame_at", frameAtWallMs / 1000.0)
            .put("last_frame_age_ms", 0)

        val runtime = metrics.toJson()
            .put("native", true)
            .put("link", "ONLINE")
            .put("serial_ready", true)
            .put("last_error", "")
            .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("telemetry_delivery_pipeline", telemetryDeliveryPipeline.metricsJson())

        return JSONObject()
            .put("event", "telemetry")
            .put("session_id", generation)
            .put("version", "OMEGAS-NATIVE-CORE-5")
            .put("live", live)
            .put("runtime", runtime)
    }

    /** Estado da engine como ela o declara (EngineState.name): a UI mostra "ECU não responde" ou "recuperando" por ele. */
    @Volatile var engineState: String = "STOPPED"
        private set
    @Volatile var engineMessage: String = ""
        private set

    /**
     * Roda na thread serial: atualiza só os campos voláteis baratos. A cópia do snapshot e o aviso a
     * overlay/notificação (onStateChanged) vão para a fila de estado, fora da thread serial.
     */
    private fun consumeState(status: JSONObject) {
        val state = status.optString("state")
        val wasRunning = running
        running = engine.isRunning()
        ready = engine.isSessionReady()
        engineState = state.ifBlank { "STOPPED" }
        engineMessage = status.optString("message")
        // `lastError` costuma vir vazio no JSON (a chave existe): aí vale a mensagem da engine, não o erro anterior.
        lastError = status.optString("lastError").ifBlank { status.optString("message").ifBlank { lastError } }
        if (state == "ERROR") crashed = true
        if (state == "STOPPED") {
            running = false
            ready = false
            // A engine terminou de fato: o "travado" de uma parada que passou do prazo deixa de valer.
            stuck = false
            if (wasRunning && !intentionalStop) reportExit(crashed, deferNotice = true)
        }
        if (!stateDeliveryPipeline.submit(stateSequence.incrementAndGet()) { deliverState(state) }) {
            // Fila encerrando (close): entrega aqui mesmo para não perder o último estado.
            deliverState(state)
        }
    }

    private fun deliverState(state: String) {
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
        pendingExitNotice.getAndSet(null)?.let { wasCrash -> onEngineExited(wasCrash) }
        onStateChanged()
    }

    private fun reportExit(wasCrash: Boolean, deferNotice: Boolean = false) {
        if (exitReported) return
        exitReported = true
        exitCount += 1
        if (deferNotice) pendingExitNotice.set(wasCrash) else onEngineExited(wasCrash)
    }

    private fun snapshotJson(): String = synchronized(snapshotLock) {
        JSONObject(latestSnapshot.toString())
            .put("telemetry_delivery_pipeline", telemetryDeliveryPipeline.metricsJson())
            .toString()
    }

    private fun flushPipelines(boundary: String, timeoutMs: Long = 10_000L): Boolean {
        val deliveryOk = telemetryDeliveryPipeline.flush(timeoutMs)
        if (!deliveryOk) {
            log.add("WARN", "TELEMETRY-DELIVERY", "Fila não drenou em $boundary dentro de ${timeoutMs}ms")
        }
        val stateOk = stateDeliveryPipeline.flush(timeoutMs)
        if (!stateOk) {
            log.add("WARN", "ENGINE-STATE-DELIVERY", "Fila de estado não drenou em $boundary dentro de ${timeoutMs}ms")
        }
        return deliveryOk && stateOk
    }

    private fun emptySnapshot(sessionId: Long = 0L, reason: String = "OFFLINE"): JSONObject = JSONObject()
        .put("version", "OMEGAS-NATIVE-CORE-5")
        .put("session_id", sessionId)
        .put("telemetry_valid", false)
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

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}