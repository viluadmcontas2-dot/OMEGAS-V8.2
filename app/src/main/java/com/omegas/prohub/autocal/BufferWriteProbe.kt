package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Liga a sonda de escrita única (BufferWriteProbeCore) à porta serial. Só roda por toque do dono na aba Ferramentas;
 * nenhuma outra parte do app a chama. Escrita sempre pelo agendador MANUAL_WRITE e, portanto, registrada na sessão
 * como `ecu_command` (hex do pedido/resposta). Não altera AutoCalProtocol, KFactorManager nem KWriteManager.
 */
class BufferWriteProbe(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-buffer-probe").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    @Volatile private var status: JSONObject = JSONObject().put("state", "IDLE").put("busy", false)

    fun statusJson(): String = status.toString()

    fun start(): JSONObject {
        if (!serial.isConnected()) return fail("USB desconectado")
        if (!busy.compareAndSet(false, true)) return fail("A sonda já está rodando")
        status = JSONObject().put("state", "RUNNING").put("busy", true).put("message", "Lendo os pontos do GNV…")
        executor.execute {
            try {
                val result = runOnce()
                val json = toJson(result)
                record("buffer_write_probe", json)
                status = JSONObject(json.toString()).put("state", "DONE").put("busy", false)
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                record("buffer_write_probe", JSONObject().put("outcome", "ERROR").put("message", message))
                status = JSONObject().put("state", "DONE").put("busy", false).put("outcome", "ERROR").put("message", message)
            } finally {
                busy.set(false)
            }
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    private fun runOnce(): BufferWriteProbeCore.Result {
        val sessionId = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
            ?: throw IllegalStateException("USB desconectado")
        return serial.unit(
            reason = "sonda de escrita única nos pontos do GNV",
            expectedSessionId = sessionId,
            workClass = Mp48WorkClass.MANUAL_WRITE,
            telemetryAfter = true,
            waitTimeoutMs = 1_000L,
        ) { unit ->
            BufferWriteProbeCore.run(
                BufferWriteProbeCore.Io(
                    readTimes = { readVector(unit, AutoCalProtocol.PETR_INJ_TBUF_GAS.address, "sonda: tempo GNV") },
                    readCounters = { readVector(unit, AutoCalProtocol.NUM_BUF_UPD_GAS.address, "sonda: contadores GNV") },
                    awaitFrames = { count -> repeat(count) { awaitTelemetryFrame(unit) } },
                    write = { index, raw -> write(unit, index, raw) },
                ),
            )
        }
    }

    private fun readVector(unit: Mp48SerialUnit, address: Int, reason: String): IntArray {
        val reply = unit.transaction(AutoCalProtocol.readVector(address), reason, 900, purgeBefore = false)
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException(reply.error.ifBlank { "ECU não respondeu $reason" })
        }
        val bytes = reply.payload
        if (bytes.size < BufferWriteProbeCore.BAND_COUNT * 2) throw IllegalStateException("$reason: resposta curta (${bytes.size} bytes)")
        return IntArray(BufferWriteProbeCore.BAND_COUNT) { i ->
            (bytes[i * 2].toInt() and 0xFF) or ((bytes[i * 2 + 1].toInt() and 0xFF) shl 8)
        }
    }

    /**
     * Um quadro de telemetria novo (pedido 48 01, o mesmo do fluxo normal) dentro da unidade da sonda: a espera entre as
     * leituras de conferência é contada em respostas da ECU, sem relógio. Só leitura; nenhum byte de escrita muda.
     */
    private fun awaitTelemetryFrame(unit: Mp48SerialUnit) {
        val reply = unit.transaction(Mp48Protocol.CMD_TELEMETRY, "sonda: quadro de telemetria entre leituras", 900, purgeBefore = false)
        if (!reply.ok) throw IllegalStateException(reply.error.ifBlank { "ECU não respondeu ao quadro de telemetria" })
    }

    private fun write(unit: Mp48SerialUnit, index: Int, raw: Int): BufferWriteProbeCore.WriteReply {
        val request = AutoCalProtocol.writeIndexedU16(AutoCalProtocol.PETR_INJ_TBUF_GAS.address, index, raw)
        val reply: UsbProtocolReply = try {
            unit.transaction(request, "sonda: escrita única GNV[$index]", 900, purgeBefore = false)
        } catch (error: Exception) {
            return BufferWriteProbeCore.WriteReply(
                acked = false, transportFailed = true, requestHex = hex(request), replyHex = "",
                detail = error.message ?: error.javaClass.simpleName,
            )
        }
        val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
        // Sem resposta nenhuma (timeout/cabo) é transporte; resposta da ECU diferente de ACK (ex.: CA) é recusa dela.
        val transport = !acked && reply.rawResponse.isEmpty() && reply.status < 0
        return BufferWriteProbeCore.WriteReply(
            acked = acked,
            transportFailed = transport,
            requestHex = hex(request),
            replyHex = hex(reply.rawResponse),
            detail = if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim(),
        )
    }

    private fun toJson(result: BufferWriteProbeCore.Result): JSONObject = JSONObject()
        .put("outcome", result.outcome.name)
        .put("message", result.message)
        .put("band", result.band)
        .put("counter", result.counter)
        .put("original", result.original)
        .put("target", result.target)
        .put("readbacks", JSONArray(result.readbacks))
        .put("restoreNeeded", result.restoreNeeded)
        .put("restored", result.restored)
        .put("finalValue", result.finalValue)
        .put("neighborsChanged", result.neighborsChanged)
        .put("writeAcked", result.write?.acked ?: JSONObject.NULL)
        .put("writeRequestHex", result.write?.requestHex ?: "")
        .put("writeReplyHex", result.write?.replyHex ?: "")
        .put("writeDetail", result.write?.detail ?: "")

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun fail(message: String) = JSONObject().put("ok", false).put("error", message)

    fun close() = executor.shutdownNow()
}
