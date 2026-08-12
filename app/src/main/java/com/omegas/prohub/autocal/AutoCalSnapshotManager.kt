package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordena uma leitura AutoCal sem possuir API de ação ou escrita.
 *
 * A transação é injetada pela autoridade Android existente e deve usar a trava
 * exclusiva de `UsbSerialManager.protocolTransaction`.
 */
class AutoCalSnapshotManager(
    private val isConnected: () -> Boolean,
    private val currentSessionId: () -> Long,
    private val otherCalibrationBusy: () -> Boolean,
    private val transaction: (
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        expectedSessionId: Long,
    ) -> UsbProtocolReply,
    private val onStateChanged: () -> Unit = {},
    private val onSnapshotReady: (JSONObject) -> Unit = {},
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-autocal-reader").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val stateLock = Any()

    @Volatile private var latestSnapshot: AutoCalSnapshot? = null
    @Volatile private var status = JSONObject()
        .put("state", "IDLE")
        .put("busy", false)
        .put("message", "AutoCal aguardando leitura manual")
        .put("automatic", false)
        .put("manualOnly", true)

    fun isBusy(): Boolean = busy.get()

    fun statusJson(): JSONObject = synchronized(stateLock) { JSONObject(status.toString()) }

    fun latestSnapshotJson(): JSONObject = latestSnapshot?.toJson()
        ?: JSONObject()
            .put("available", false)
            .put("automatic", false)
            .put("manualOnly", true)

    fun startRead(
        fields: List<AutoCalProtocol.Field> = AutoCalProtocol.READ_ONLY_FIELDS,
    ): JSONObject {
        if (fields.isEmpty()) return failure("Nenhum campo AutoCal selecionado")
        if (!isConnected()) return failure("USB desconectado")
        if (otherCalibrationBusy()) return failure("Outra operação de calibração está em andamento")
        if (!busy.compareAndSet(false, true)) return failure("Leitura AutoCal já está em andamento")
        val expectedSessionId = currentSessionId()
        if (expectedSessionId <= 0L) {
            busy.set(false)
            return failure("Sessão USB inválida")
        }
        val ticket = generation.incrementAndGet()
        update(
            state = "QUEUED",
            message = "Leitura AutoCal preparada",
            progress = 0,
            extra = JSONObject()
                .put("sessionId", expectedSessionId)
                .put("fieldCount", fields.distinctBy { "${it.address}:${it.index ?: -1}" }.size),
        )
        executor.execute { executeRead(ticket, expectedSessionId, fields.distinctBy { "${it.address}:${it.index ?: -1}" }) }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("sessionId", expectedSessionId)
            .put("automatic", false)
            .put("manualOnly", true)
    }

    fun cancel(): JSONObject {
        if (!busy.get()) return JSONObject().put("ok", true).put("cancelled", false)
        generation.incrementAndGet()
        update("CANCEL_REQUESTED", "Cancelamento solicitado", status.optInt("progress", 0))
        return JSONObject().put("ok", true).put("cancelled", true)
    }

    fun onUsbSessionChanged(sessionId: Long) {
        if (busy.get() && sessionId != status.optLong("sessionId", -1L)) cancel()
    }

    fun close() {
        generation.incrementAndGet()
        executor.shutdownNow()
    }

    private fun executeRead(
        ticket: Long,
        expectedSessionId: Long,
        fields: List<AutoCalProtocol.Field>,
    ) {
        val startedAt = System.currentTimeMillis()
        val observations = mutableListOf<AutoCalReadObservation>()
        var terminalState = "READY"
        var terminalMessage = "Snapshot AutoCal lido"
        try {
            fields.forEachIndexed { index, field ->
                ensureCurrent(ticket, expectedSessionId)
                val progress = 5 + (index * 90 / fields.size.coerceAtLeast(1))
                update(
                    "READING",
                    "Lendo ${field.key}",
                    progress,
                    JSONObject()
                        .put("sessionId", expectedSessionId)
                        .put("field", field.key)
                        .put("fieldIndex", index)
                        .put("fieldCount", fields.size),
                )
                val reply = transaction(
                    AutoCalProtocol.read(field),
                    "AutoCal ${field.key}",
                    1_200,
                    expectedSessionId,
                )
                val capturedAt = System.currentTimeMillis()
                observations += if (reply.ok) {
                    AutoCalReadObservation(
                        field = field,
                        status = reply.status,
                        payload = reply.payload,
                        capturedAtMs = capturedAt,
                    )
                } else {
                    AutoCalReadObservation(
                        field = field,
                        status = reply.status.takeIf { it >= 0 },
                        payload = reply.payload.takeIf { it.isNotEmpty() },
                        capturedAtMs = capturedAt,
                        error = reply.error.ifBlank { "Campo não confirmado" },
                    )
                }
            }
            ensureCurrent(ticket, expectedSessionId)
            val snapshot = AutoCalSnapshotBuilder.build(
                observations = observations,
                expectedFields = fields,
                sessionId = "AUTOCAL-$expectedSessionId-${UUID.randomUUID().toString().take(8)}",
                source = AutoCalSnapshotSource.ECU_READ,
                startedAtMs = startedAt,
                finishedAtMs = System.currentTimeMillis(),
            )
            ensureCurrent(ticket, expectedSessionId)
            latestSnapshot = snapshot
            // A leitura nativa entra no aprendizado apenas como contexto ECU_NATIVE.
            // Esse callback não possui acesso a nenhum writer.
            onSnapshotReady(snapshot.toJson())
            if (snapshot.partial) {
                terminalState = "READY_PARTIAL"
                terminalMessage = "Snapshot parcial: ${snapshot.validFieldCount}/${snapshot.fields.size} campos válidos"
            }
            update(
                terminalState,
                terminalMessage,
                100,
                snapshot.toJson().put("sessionId", expectedSessionId),
            )
        } catch (cancelled: ReadCancelled) {
            terminalState = cancelled.state
            terminalMessage = cancelled.message ?: "Leitura cancelada"
            update(
                terminalState,
                terminalMessage,
                100,
                JSONObject().put("sessionId", expectedSessionId),
            )
        } catch (error: Exception) {
            update(
                "FAILED",
                error.message ?: "Falha na leitura AutoCal",
                100,
                JSONObject().put("sessionId", expectedSessionId),
            )
        } finally {
            busy.set(false)
            synchronized(stateLock) { status.put("busy", false) }
            onStateChanged()
        }
    }

    private fun ensureCurrent(ticket: Long, expectedSessionId: Long) {
        if (generation.get() != ticket) throw ReadCancelled("CANCELLED", "Leitura AutoCal cancelada")
        if (!isConnected()) throw ReadCancelled("DISCONNECTED", "USB desconectado durante a leitura")
        if (currentSessionId() != expectedSessionId) {
            throw ReadCancelled("STALE_SESSION", "Sessão USB mudou durante a leitura")
        }
        if (otherCalibrationBusy()) {
            throw ReadCancelled("CALIBRATION_CONFLICT", "Outra calibração assumiu a sessão")
        }
    }

    private fun update(
        state: String,
        message: String,
        progress: Int,
        extra: JSONObject = JSONObject(),
    ) {
        synchronized(stateLock) {
            status = JSONObject(extra.toString())
                .put("state", state)
                .put("busy", busy.get())
                .put("message", message)
                .put("progress", progress.coerceIn(0, 100))
                .put("updatedAt", System.currentTimeMillis())
                .put("automatic", false)
                .put("manualOnly", true)
        }
        onStateChanged()
    }

    private fun failure(message: String): JSONObject = JSONObject()
        .put("ok", false)
        .put("error", message)
        .put("automatic", false)
        .put("manualOnly", true)

    private class ReadCancelled(val state: String, message: String) : IllegalStateException(message)
}
