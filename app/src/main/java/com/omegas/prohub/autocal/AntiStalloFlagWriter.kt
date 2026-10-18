package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Liga a escrita manual do anti-apagão (AntiStalloFlagCore) à porta serial. SÓ roda por toque do dono em Ferramentas >
 * Avançado: `prepare` (lê FLAG_CONF3 ao vivo e guarda a prévia), depois `confirm(token)` (segundo toque). Nada aqui
 * dispara sozinho: nenhum laço, nenhum relógio de decisão; esperar a ECU = esperar a resposta de cada transação.
 * Escrita pelo agendador MANUAL_WRITE (registrada como `ecu_command` na sessão). Não altera AutoCalProtocol,
 * KFactorManager nem KWriteManager: só usa `AutoCalProtocol.readVector/writeVectorU16`.
 */
class AntiStalloFlagWriter(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
    /** Valor lido de FLAG_CONF3 (prévia ou leitura de volta): atualiza o que a tela mostra. */
    private val onFlagRead: (session: Long, words: IntArray, trigger: String) -> Unit,
) {
    private class Pending(val token: String, val plan: AntiStalloFlagCore.Plan, val session: Long)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-anti-stallo-flag").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private val lock = Any()
    private var pending: Pending? = null
    /** Bit anterior da última gravação CONFIRMADA (para "Desfazer"), da sessão USB em que foi feita. */
    private var undoEnable: Boolean? = null
    private var undoSession = 0L
    @Volatile private var status: JSONObject = JSONObject().put("state", "IDLE")

    fun statusJson(): String = synchronized(lock) {
        val copy = JSONObject(status.toString())
        val session = currentSessionOrZero()
        val undo = undoEnable
        copy.put("busy", busy.get())
        copy.put(
            "undo",
            if (undo != null && undoSession == session && session > 0L) JSONObject().put("available", true).put("enable", undo)
            else JSONObject().put("available", false),
        )
        copy.toString()
    }

    /** 1º toque: lê FLAG_CONF3 ao vivo e monta a prévia. Não escreve nada. */
    fun prepare(enable: Boolean): JSONObject {
        if (!serial.isConnected()) return fail("USB desconectado")
        if (!busy.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "PREPARING") }
        executor.execute {
            try {
                val session = sessionOrThrow()
                val words = serial.unit(
                    reason = "anti-apagão: ler FLAG_CONF3 para a prévia",
                    expectedSessionId = session,
                    workClass = Mp48WorkClass.READ_ONLY,
                    telemetryAfter = true,
                    waitTimeoutMs = 1_000L,
                ) { unit -> readFlags(unit) }
                onFlagRead(session, words, "AO_VIVO")
                val plan = AntiStalloFlagCore.plan(words, enable)
                val token = UUID.randomUUID().toString()
                synchronized(lock) {
                    pending = Pending(token, plan, session)
                    status = JSONObject()
                        .put("state", "READY")
                        .put("token", token)
                        .put("enable", enable)
                        .put("before", JSONArray(plan.before.toList()))
                        .put("after", JSONArray(plan.after.toList()))
                        .put("frameHex", hex(plan.frame))
                        .put("noop", plan.noop)
                }
            } catch (error: Exception) {
                synchronized(lock) {
                    status = JSONObject().put("state", "ERROR").put("message", "Não foi possível ler a ECU: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                busy.set(false)
            }
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    /** 2º toque (confirmação): só com o token da prévia, na mesma sessão USB. */
    fun confirm(token: String): JSONObject {
        val ready = synchronized(lock) { pending }
        if (ready == null || ready.token != token) return fail("Não há prévia válida; refaça o toque.")
        if (ready.plan.noop) return fail("O anti-apagão já está no estado pedido.")
        if (!serial.isConnected() || currentSessionOrZero() != ready.session) return fail("USB mudou desde a prévia; refaça o toque.")
        if (!busy.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "WRITING") }
        executor.execute {
            try {
                val result = serial.unit(
                    reason = "anti-apagão: gravar FLAG_CONF3 (bit 0x20) com leitura de volta",
                    expectedSessionId = ready.session,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 1_000L,
                ) { unit -> AntiStalloFlagCore.execute(io(unit), ready.plan) }
                finish(ready, result)
            } catch (error: Exception) {
                val message = "A ECU está ocupada ou desconectou; nada foi dado como gravado (${error.message ?: error.javaClass.simpleName})."
                record("anti_stallo_flag_write", JSONObject().put("outcome", "ERROR").put("message", message).put("origin", "APP_UI_MANUAL"))
                synchronized(lock) { status = JSONObject().put("state", "DONE").put("outcome", "ERROR").put("message", message) }
            } finally {
                busy.set(false)
            }
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    /** Descarta a prévia (outro toque, troca de rota, USB caiu). Não mexe na ECU. */
    fun cancel(): JSONObject {
        synchronized(lock) {
            if (status.optString("state") == "READY") status = JSONObject().put("state", "IDLE")
            pending = null
        }
        return JSONObject().put("ok", true)
    }

    private fun finish(ready: Pending, result: AntiStalloFlagCore.Result) {
        val plan = ready.plan
        result.readback?.let { onFlagRead(ready.session, it, "APOS_GRAVAR") }
        val json = JSONObject()
            .put("state", "DONE")
            .put("outcome", result.outcome.name)
            .put("message", result.message)
            .put("before", JSONArray(plan.before.toList()))
            .put("after", JSONArray(plan.after.toList()))
            .put("readback", result.readback?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("valueChanged", result.valueChanged)
            .put("frameHex", result.frame?.let { hex(it) } ?: "")
            .put("replyHex", result.reply?.replyHex ?: "")
        synchronized(lock) {
            if (result.outcome == AntiStalloFlagCore.Outcome.CONFIRMED) {
                undoEnable = (plan.before[0] and AntiStalloFlagCore.MASK) != 0
                undoSession = ready.session
            }
            status = json
        }
        record("anti_stallo_flag_write", JSONObject(json.toString()).put("origin", "APP_UI_MANUAL").put("enableRequested", plan.enable))
    }

    private fun io(unit: Mp48SerialUnit) = AntiStalloFlagCore.Io(
        read = { readFlags(unit) },
        write = { frame ->
            val reply = try {
                unit.transaction(frame, "anti-apagão: SetVector FLAG_CONF3", 900, purgeBefore = false)
            } catch (error: Exception) {
                return@Io AntiStalloFlagCore.WriteReply(false, true, "", error.message ?: error.javaClass.simpleName)
            }
            val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
            AntiStalloFlagCore.WriteReply(
                acked, !acked && reply.rawResponse.isEmpty() && reply.status < 0, hex(reply.rawResponse),
                if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim(),
            )
        },
    )

    private fun readFlags(unit: Mp48SerialUnit): IntArray {
        val reply = unit.transaction(AutoCalProtocol.readVector(AntiStalloFlagCore.ADDRESS), "anti-apagão: ler FLAG_CONF3", 900, purgeBefore = false)
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) throw IllegalStateException(reply.error.ifBlank { "ECU não respondeu à leitura de FLAG_CONF3" })
        return AntiStalloFlagCore.wordsFromPayload(IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF })
            ?: throw IllegalStateException("FLAG_CONF3 veio com ${reply.payload.size} bytes (esperado 4)")
    }

    private fun sessionOrThrow(): Long = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
        ?: throw IllegalStateException("USB desconectado")

    private fun currentSessionOrZero(): Long = if (serial.isConnected()) serial.currentSessionId() else 0L

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun fail(message: String) = JSONObject().put("ok", false).put("error", message)

    fun close() = executor.shutdownNow()
}
