package com.omegas.prohub.diagnostics

import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decorador que só OBSERVA: repassa cada transação serial ao agendador real, sem tocar nos bytes, e deixa a trilha na sessão.
 * - escritas (MANUAL_WRITE/SAFETY): evento `ecu_command` completo (pedido e resposta em hex, ACK, tempo);
 * - leituras: resumo `ecu_read_summary` a cada 30 s (por motivo: quantas, falhas, tempo total), para não inflar a sessão.
 */
class RecordingSerialScheduler(
    private val delegate: Mp48SerialScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val record: (type: String, source: String, data: JSONObject) -> Unit,
) : Mp48SerialScheduler {
    private class ReadStat(var count: Int = 0, var failures: Int = 0, var totalMs: Long = 0L, var lastError: String = "")

    private val lock = Any()
    private val reads = LinkedHashMap<String, ReadStat>()
    private var lastFlushAt = clock()

    override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long) =
        delegate.recentTelemetryFrames(fromElapsedMs, toElapsedMs)

    override fun isConnected(): Boolean = delegate.isConnected()

    override fun currentSessionId(): Long = delegate.currentSessionId()

    override fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        purgeBefore: Boolean,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
    ): UsbProtocolReply {
        val started = clock()
        val reply = try {
            delegate.transaction(request, reason, timeoutMs, purgeBefore, expectedSessionId, workClass, telemetryAfter)
        } catch (error: Exception) {
            note(request, reason, workClass, null, clock() - started, error.message ?: error.javaClass.simpleName)
            throw error
        }
        note(request, reason, workClass, reply, clock() - started, null)
        return reply
    }

    override fun <T> unit(
        reason: String,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
        waitTimeoutMs: Long,
        block: (Mp48SerialUnit) -> T,
    ): T = delegate.unit(reason, expectedSessionId, workClass, telemetryAfter, waitTimeoutMs) { unit ->
        block(RecordingUnit(unit, workClass))
    }

    private inner class RecordingUnit(
        private val inner: Mp48SerialUnit,
        private val workClass: Mp48WorkClass,
    ) : Mp48SerialUnit {
        override val sessionId: Long get() = inner.sessionId

        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean): UsbProtocolReply {
            val started = clock()
            val reply = try {
                inner.transaction(request, reason, timeoutMs, purgeBefore)
            } catch (error: Exception) {
                note(request, reason, workClass, null, clock() - started, error.message ?: error.javaClass.simpleName)
                throw error
            }
            note(request, reason, workClass, reply, clock() - started, null)
            return reply
        }
    }

    private fun note(request: ByteArray, reason: String, workClass: Mp48WorkClass, reply: UsbProtocolReply?, elapsedMs: Long, error: String?) {
        try {
            if (workClass != Mp48WorkClass.READ_ONLY) {
                record(
                    "ecu_command", "serial",
                    JSONObject()
                        .put("reason", reason.take(120))
                        .put("workClass", workClass.name)
                        .put("requestHex", hex(request))
                        .put("ok", reply?.ok ?: false)
                        .put("status", reply?.status ?: -1)
                        .put("payloadHex", reply?.let { hex(it.payload, 64) } ?: "")
                        .put("error", error ?: reply?.error ?: "")
                        .put("elapsedMs", elapsedMs)
                        .put("sessionId", delegate.currentSessionId()),
                )
                return
            }
            val flush: JSONObject? = synchronized(lock) {
                val key = reason.take(60).replace(Regex("\\d+"), "#")
                val stat = reads.getOrPut(key) { ReadStat() }
                stat.count += 1
                stat.totalMs += elapsedMs
                if (reply?.ok != true) { stat.failures += 1; stat.lastError = (error ?: reply?.error ?: "").take(80) }
                val now = clock()
                if (now - lastFlushAt < FLUSH_MS) null else summary(now).also { lastFlushAt = now; reads.clear() }
            }
            if (flush != null) record("ecu_read_summary", "serial", flush)
        } catch (_: Exception) {
            // a trilha nunca pode atrapalhar a comunicação com a ECU
        }
    }

    private fun summary(now: Long): JSONObject {
        val items = JSONArray()
        reads.forEach { (key, s) ->
            items.put(
                JSONObject().put("reason", key).put("count", s.count).put("failures", s.failures)
                    .put("totalMs", s.totalMs).put("lastError", s.lastError),
            )
        }
        return JSONObject().put("windowMs", now - lastFlushAt).put("reads", items)
    }

    private fun hex(bytes: ByteArray, limit: Int = 256): String =
        bytes.take(limit).joinToString("") { "%02X".format(it.toInt() and 0xFF) } + if (bytes.size > limit) "…(${bytes.size}B)" else ""

    private companion object {
        const val FLUSH_MS = 30_000L
    }
}
