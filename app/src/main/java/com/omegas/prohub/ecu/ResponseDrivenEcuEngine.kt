package com.omegas.prohub.ecu

import android.os.SystemClock
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.learning.MotorSampleAnalyzer
import com.omegas.prohub.learning.SampleDecision
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.usb.UsbProtocolStatusClass
import com.omegas.prohub.usb.UsbSerialManager
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoridade única da sessão MP48.
 *
 * O loop é orientado pelo fim da transação anterior. Falhas são recuperadas em
 * etapas: continuar, recuperar suavemente, testar a sessão existente e somente
 * então refazer o handshake. Respostas estendidas não-retryable nunca entram
 * em retry cego: aguardam uma nova sessão USB física.
 */
class ResponseDrivenEcuEngine(
    private val usb: UsbSerialManager,
    private val log: RingLog,
    private val onTelemetry: (Mp48Telemetry, SampleDecision, EngineMetrics) -> Unit,
    private val onStateChanged: (JSONObject) -> Unit = {},
) {
    companion object {
        private const val HANDSHAKE_BACKOFF_MS = 250L
        private const val HANDSHAKE_BACKOFF_MAX_MS = 5_000L
        private const val HARD_HANDSHAKE_WARNING_AFTER = 20
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-native-ecu").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val queue = LinkedBlockingDeque<QueuedTransaction>()
    private val analyzer = MotorSampleAnalyzer()
    private val stateLock = Any()

    @Volatile private var sessionReady = false
    @Volatile private var state = EngineState.STOPPED
    @Volatile private var lastError = ""
    @Volatile private var telemetryFrames = 0L
    @Volatile private var telemetryFailures = 0L
    @Volatile private var consecutiveFailures = 0
    @Volatile private var handshakeFailures = 0
    @Volatile private var handshakeNonRetryable = false
    @Volatile private var lastTelemetryAtMs = 0L
    @Volatile private var lastValidTelemetryAtMs = 0L
    @Volatile private var lastResponseMs = 0L
    @Volatile private var lastIntervalMs = 0L
    @Volatile private var lastHandshakeAttemptMs = 0L
    @Volatile private var plannedWorkSinceLastTelemetry = false
    @Volatile private var hadOnlineSession = false
    @Volatile private var recoveringExistingSession = false
    @Volatile private var physicalSessionId = 0L

    @Synchronized
    fun beginUsbSession(sessionId: Long) {
        physicalSessionId = sessionId
        sessionReady = false
        hadOnlineSession = false
        recoveringExistingSession = false
        consecutiveFailures = 0
        handshakeFailures = 0
        handshakeNonRetryable = false
        lastTelemetryAtMs = 0L
        lastValidTelemetryAtMs = 0L
        analyzer.reset()
        queue.forEach { it.future.completeExceptionally(IllegalStateException("Nova sessão USB")) }
        queue.clear()
    }

    @Synchronized
    fun endUsbSession() {
        physicalSessionId = 0L
        sessionReady = false
        hadOnlineSession = false
        recoveringExistingSession = false
        handshakeNonRetryable = false
        analyzer.reset()
    }

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        stopRequested.set(false)
        executor.execute(::runLoop)
        return true
    }

    fun stop(graceful: Boolean = true) {
        if (!running.get()) return
        if (graceful) {
            stopRequested.set(true)
        } else {
            running.set(false)
            stopRequested.set(false)
        }
    }

    fun close() {
        stop(graceful = false)
        queue.forEach { it.future.completeExceptionally(IllegalStateException("Engine encerrada")) }
        queue.clear()
        executor.shutdownNow()
    }

    fun isRunning(): Boolean = running.get()
    fun isSessionReady(): Boolean = sessionReady

    fun submit(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1_800,
        purgeBefore: Boolean = false,
        highPriority: Boolean = true,
    ): CompletableFuture<UsbProtocolReply> {
        val future = CompletableFuture<UsbProtocolReply>()
        val transaction = QueuedTransaction(
            request = request.copyOf(),
            reason = reason,
            timeoutMs = timeoutMs,
            purgeBefore = purgeBefore,
            future = future,
        )
        if (highPriority) queue.offerFirst(transaction) else queue.offerLast(transaction)
        return future
    }

    fun execute(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1_800,
        purgeBefore: Boolean = false,
    ): UsbProtocolReply {
        return try {
            submit(request, reason, timeoutMs, purgeBefore)
                .get((timeoutMs + 1_000).toLong(), TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            UsbProtocolReply(false, error = "Timeout síncrono no ECU engine: ${e.message}", request = request)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            UsbProtocolReply(false, error = "Thread interrompida", request = request)
        } catch (e: Exception) {
            UsbProtocolReply(false, error = "Falha síncrona: ${e.message}", request = request)
        }
    }

    fun statusJson(): JSONObject = synchronized(stateLock) {
        val now = SystemClock.elapsedRealtime()
        val tolerances = LearningToleranceSettings.current
        JSONObject()
            .put("state", state.name)
            .put("running", running.get())
            .put("sessionReady", sessionReady)
            .put("lastError", lastError)
            .put("telemetryFrames", telemetryFrames)
            .put("telemetryFailures", telemetryFailures)
            .put("consecutiveFailures", consecutiveFailures)
            .put("handshakeFailures", handshakeFailures)
            .put("handshakeNonRetryable", handshakeNonRetryable)
            .put("lastResponseMs", lastResponseMs)
            .put("lastIntervalMs", lastIntervalMs)
            .put("lastValidTelemetryAgeMs", if (lastValidTelemetryAtMs > 0L) now - lastValidTelemetryAtMs else -1L)
            .put("queuedTransactions", queue.size)
            .put("recoveryMode", when {
                handshakeNonRetryable -> "NON_RETRYABLE_RESPONSE"
                handshakeFailures >= HARD_HANDSHAKE_WARNING_AFTER -> "HARD_ATTENTION_REQUIRED"
                !sessionReady && recoveringExistingSession -> "PROBING_EXISTING_SESSION"
                consecutiveFailures > tolerances.toleratedSerialFailures -> "SOFT"
                else -> "NONE"
            })
            .put("learningTolerances", tolerances.toJson())
    }

    private fun runLoop() {
        updateState(EngineState.WAITING_USB, "")
        try {
            while (running.get()) {
                if (stopRequested.get()) {
                    gracefulDisconnect()
                    break
                }
                if (!usb.connected) {
                    sessionReady = false
                    hadOnlineSession = false
                    recoveringExistingSession = false
                    handshakeNonRetryable = false
                    analyzer.reset()
                    updateState(EngineState.WAITING_USB, "Aguardando adaptador USB")
                    Thread.sleep(250L)
                    continue
                }
                if (handshakeNonRetryable) {
                    updateState(
                        EngineState.RECOVERING_HARD,
                        "ECU respondeu com status não-retryable; reconecte a interface para abrir uma nova sessão segura",
                    )
                    Thread.sleep(500L)
                    continue
                }
                if (!sessionReady) {
                    if (!performHandshakeOrResume()) {
                        SystemClock.sleep(80L)
                        continue
                    }
                }

                val queued = queue.pollFirst()
                if (queued != null) {
                    analyzer.markPlannedOperation()
                    runQueued(queued)
                    plannedWorkSinceLastTelemetry = true
                    continue
                }

                pollTelemetry()
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            lastError = error.message ?: "Falha inesperada na engine ECU"
            log.add("ERROR", "ECU-NATIVE", lastError)
            updateState(EngineState.ERROR, lastError)
        } finally {
            sessionReady = false
            analyzer.reset()
            running.set(false)
            stopRequested.set(false)
            updateState(EngineState.STOPPED, lastError)
        }
    }

    private fun performHandshakeOrResume(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val backoff = handshakeBackoffMs(handshakeFailures)
        val wait = backoff - (now - lastHandshakeAttemptMs)
        if (wait > 0L) SystemClock.sleep(wait)
        lastHandshakeAttemptMs = SystemClock.elapsedRealtime()
        val expectedSessionId = physicalSessionId

        if (hadOnlineSession || recoveringExistingSession) {
            recoveringExistingSession = true
            updateState(EngineState.RECOVERING, "Verificando se a sessão MP48 continua ativa")
            if (probeExistingSession(expectedSessionId)) {
                markSessionOnline("Sessão MP48 retomada sem novo handshake")
                return true
            }
        }

        updateState(EngineState.HANDSHAKE, "Inicializando sessão MP48")
        analyzer.reset()
        return try {
            if (handshakeFailures == 0 || handshakeFailures % 5 == 0) {
                usb.purge("início controlado da sessão MP48")
            }
            val init1 = usb.protocolTransaction(
                Mp48Protocol.CMD_INIT_1,
                "MP48 sessão 00 02",
                1_200,
                false,
                expectedSessionId,
            )
            when (init1.statusClass) {
                UsbProtocolStatusClass.EXTENDED_RETRYABLE -> {
                    if (probeExistingSession(expectedSessionId)) {
                        markSessionOnline("ECU já estava em sessão; telemetria retomada")
                        true
                    } else {
                        handshakeFailed("ECU retornou CA 01 08 (retryable) e a sessão existente não respondeu")
                        false
                    }
                }
                UsbProtocolStatusClass.EXTENDED_NON_RETRYABLE -> {
                    handshakeRejected("ECU retornou CA 01 10 (non-retryable) no comando 00 02")
                    false
                }
                UsbProtocolStatusClass.EXTENDED_UNKNOWN -> {
                    handshakeRejected(
                        "ECU retornou status estendido CA desconhecido (${init1.rawResponse.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }})",
                    )
                    false
                }
                UsbProtocolStatusClass.ACK -> {
                    val init2 = usb.protocolTransaction(
                        Mp48Protocol.CMD_INIT_2,
                        "MP48 sessão 01 00 3A",
                        1_200,
                        false,
                        expectedSessionId,
                    )
                    if (!init2.ok || init2.status != Mp48Protocol.STATUS_ACK) {
                        handshakeFailed("Comando de sessão 01 00 3A não confirmado: ${init2.error}")
                        false
                    } else {
                        val identify = usb.protocolTransaction(
                            Mp48Protocol.CMD_IDENTIFY,
                            "MP48 leitura opcional 00 25",
                            1_200,
                            false,
                            expectedSessionId,
                        )
                        if (!identify.ok) {
                            log.add("WARN", "ECU-NATIVE", "Leitura opcional 00 25 não confirmada: ${identify.error}")
                        }
                        markSessionOnline("Sessão MP48 ativa")
                        true
                    }
                }
                UsbProtocolStatusClass.UNKNOWN -> {
                    handshakeFailed("Comando de sessão 00 02 não confirmado: ${init1.error}")
                    false
                }
            }
        } catch (error: Throwable) {
            handshakeFailed(error.message ?: "Falha no handshake")
            false
        }
    }

    private fun probeExistingSession(expectedSessionId: Long = physicalSessionId): Boolean {
        return try {
            val started = SystemClock.elapsedRealtime()
            val reply = usb.protocolTransaction(
                Mp48Protocol.CMD_TELEMETRY,
                "sonda de sessão MP48 existente",
                350,
                false,
                expectedSessionId,
            )
            val responseMs = SystemClock.elapsedRealtime() - started
            if (validTelemetryReply(reply)) {
                acceptTelemetry(reply, responseMs, plannedGap = false)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun markSessionOnline(message: String) {
        sessionReady = true
        hadOnlineSession = true
        recoveringExistingSession = false
        handshakeNonRetryable = false
        consecutiveFailures = 0
        handshakeFailures = 0
        lastError = ""
        updateState(EngineState.ONLINE, message)
    }

    private fun pollTelemetry() {
        val started = SystemClock.elapsedRealtime()
        val expectedSessionId = physicalSessionId
        val reply = usb.protocolTransaction(
            Mp48Protocol.CMD_TELEMETRY,
            "telemetria MP48",
            240,
            false,
            expectedSessionId,
        )
        val responseMs = SystemClock.elapsedRealtime() - started
        if (!validTelemetryReply(reply)) {
            handleTelemetryFailure(reply)
            return
        }
        acceptTelemetry(reply, responseMs, plannedWorkSinceLastTelemetry)
        plannedWorkSinceLastTelemetry = false
    }

    private fun validTelemetryReply(reply: UsbProtocolReply): Boolean =
        reply.ok && reply.status == Mp48Protocol.STATUS_ACK &&
            reply.payload.size >= Mp48Protocol.TELEMETRY_PAYLOAD_SIZE

    private fun acceptTelemetry(reply: UsbProtocolReply, responseMs: Long, plannedGap: Boolean) {
        val toleratedGap = consecutiveFailures in 1..LearningToleranceSettings.current.toleratedSerialFailures
        val capturedAt = SystemClock.elapsedRealtime()
        lastResponseMs = responseMs
        lastIntervalMs = if (lastTelemetryAtMs == 0L) 0L else capturedAt - lastTelemetryAtMs
        lastTelemetryAtMs = capturedAt
        lastValidTelemetryAtMs = capturedAt
        val decoded = Mp48Protocol.decodeTelemetry(reply.payload, capturedAt)
        val decision = analyzer.add(
            decoded,
            plannedGap = plannedGap,
            toleratedGap = toleratedGap,
        )
        telemetryFrames += 1
        consecutiveFailures = 0
        handshakeFailures = 0
        sessionReady = true
        hadOnlineSession = true
        recoveringExistingSession = false
        lastError = ""
        updateState(EngineState.ONLINE, "")
        onTelemetry(decoded, decision, metrics())
    }

    private fun handleTelemetryFailure(reply: UsbProtocolReply) {
        telemetryFailures += 1
        consecutiveFailures += 1
        lastError = reply.error.ifBlank {
            "Telemetria incompleta: ${reply.payload.size}/${Mp48Protocol.TELEMETRY_PAYLOAD_SIZE}"
        }
        val now = SystemClock.elapsedRealtime()
        val silence = if (lastValidTelemetryAtMs > 0L) now - lastValidTelemetryAtMs else Long.MAX_VALUE
        val tolerances = LearningToleranceSettings.current
        val softRecoveryAfterFailures = tolerances.toleratedSerialFailures + 1
        val hardRecoveryAfterFailures = tolerances.hardRecoveryFailures
        val hardRecoverySilenceMs = tolerances.hardRecoverySilenceMs

        when {
            consecutiveFailures < softRecoveryAfterFailures -> {
                updateState(EngineState.ONLINE, "Atraso momentâneo na telemetria")
            }
            consecutiveFailures < hardRecoveryAfterFailures && silence < hardRecoverySilenceMs -> {
                if (consecutiveFailures == softRecoveryAfterFailures) {
                    analyzer.markContinuityLost()
                }
                updateState(
                    EngineState.RECOVERING_SOFT,
                    "Recuperando telemetria sem reiniciar a sessão (${consecutiveFailures}/$hardRecoveryAfterFailures)",
                )
                SystemClock.sleep(20L)
            }
            else -> {
                sessionReady = false
                recoveringExistingSession = true
                analyzer.reset()
                updateState(
                    EngineState.RECOVERING,
                    "Telemetria ausente por ${if (silence == Long.MAX_VALUE) "tempo desconhecido" else "$silence ms"}; testando sessão existente",
                )
            }
        }
    }

    private fun runQueued(transaction: QueuedTransaction) {
        try {
            val reply = usb.protocolTransaction(
                transaction.request,
                transaction.reason,
                transaction.timeoutMs,
                transaction.purgeBefore,
                physicalSessionId,
            )
            transaction.future.complete(reply)
        } catch (error: Throwable) {
            transaction.future.completeExceptionally(error)
        }
    }

    private fun gracefulDisconnect() {
        updateState(EngineState.DISCONNECTING, "Encerrando sessão MP48")
        try {
            if (usb.connected && sessionReady) {
                val reply = usb.protocolTransaction(
                    Mp48Protocol.CMD_DISCONNECT,
                    "desconexão MP48",
                    500,
                    false,
                    physicalSessionId,
                )
                if (!reply.ok) {
                    log.add("WARN", "ECU-NATIVE", "ECU não confirmou desconexão: ${reply.error}")
                }
            }
        } catch (error: Throwable) {
            log.add("WARN", "ECU-NATIVE", "Falha ao encerrar sessão: ${error.message}")
        }
    }

    private fun handshakeRejected(message: String) {
        sessionReady = false
        recoveringExistingSession = false
        handshakeNonRetryable = true
        lastError = message
        log.add("ERROR", "ECU-NATIVE", "$message • retry bloqueado até nova sessão USB")
        updateState(
            EngineState.RECOVERING_HARD,
            "$message • reconecte a interface para uma nova tentativa segura",
        )
    }

    private fun handshakeFailed(message: String) {
        sessionReady = false
        handshakeFailures += 1
        lastError = message
        log.add("WARN", "ECU-NATIVE", "$message • tentativa $handshakeFailures")
        val state = if (handshakeFailures >= HARD_HANDSHAKE_WARNING_AFTER) {
            EngineState.RECOVERING_HARD
        } else {
            EngineState.RECOVERING
        }
        val action = if (state == EngineState.RECOVERING_HARD) {
            "$message • comunicação persistente; verifique cabo, alimentação e interface USB"
        } else {
            message
        }
        updateState(state, action)
        if (handshakeFailures >= 40) {
            log.add("ERROR", "ECU-NATIVE", "Timeout de handshake atingido. Forçando parada.")
            stop(graceful = false)
            return
        }
        recoveringExistingSession = hadOnlineSession
    }

    private fun handshakeBackoffMs(failures: Int): Long {
        val multiplier = 1L shl failures.coerceIn(0, 4)
        return (HANDSHAKE_BACKOFF_MS * multiplier).coerceAtMost(HANDSHAKE_BACKOFF_MAX_MS)
    }

    private fun metrics(): EngineMetrics = EngineMetrics(
        telemetryFrames = telemetryFrames,
        telemetryFailures = telemetryFailures,
        responseMs = lastResponseMs,
        intervalMs = lastIntervalMs,
        queuedTransactions = queue.size,
        consecutiveFailures = consecutiveFailures,
        handshakeFailures = handshakeFailures,
    )

    private fun updateState(newState: EngineState, message: String) {
        val changed = state != newState || message.isNotBlank()
        state = newState
        if (message.isNotBlank() && newState == EngineState.ERROR) lastError = message
        if (changed) onStateChanged(statusJson().put("message", message))
    }

    private data class QueuedTransaction(
        val request: ByteArray,
        val reason: String,
        val timeoutMs: Int,
        val purgeBefore: Boolean,
        val future: CompletableFuture<UsbProtocolReply>,
    )
}

enum class EngineState {
    STOPPED,
    WAITING_USB,
    HANDSHAKE,
    ONLINE,
    RECOVERING_SOFT,
    RECOVERING,
    RECOVERING_HARD,
    DISCONNECTING,
    ERROR,
}

data class EngineMetrics(
    val telemetryFrames: Long,
    val telemetryFailures: Long,
    val responseMs: Long,
    val intervalMs: Long,
    val queuedTransactions: Int,
    val consecutiveFailures: Int = 0,
    val handshakeFailures: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("telemetry_frames", telemetryFrames)
        .put("telemetry_failures", telemetryFailures)
        .put("response_ms", responseMs)
        .put("interval_ms", intervalMs)
        .put("queued_transactions", queuedTransactions)
        .put("consecutive_failures", consecutiveFailures)
        .put("handshake_failures", handshakeFailures)
}
