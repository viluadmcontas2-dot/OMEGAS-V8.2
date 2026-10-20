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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Liga a escrita manual do anti-apagão (AntiStalloFlagCore) à porta serial. SÓ roda por toque do dono em Ferramentas >
 * Avançado: `apply` (um toque = ler, gravar e reler numa só etapa). `prepare`/`confirm` seguem como caminho em dois passos (bancadas/ponte antiga). Nada aqui
 * dispara sozinho: nenhum laço, nenhum relógio de decisão; esperar a ECU = esperar a resposta de cada transação.
 * Escrita pelo agendador MANUAL_WRITE (registrada como `ecu_command` na sessão). Não altera AutoCalProtocol,
 * KFactorManager nem KWriteManager: só usa `AutoCalProtocol.readVector/writeVectorU16`.
 */
class AntiStalloFlagWriter(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
    /** Valor lido de FLAG_CONF3 (prévia ou leitura de volta): atualiza o que a tela mostra. */
    private val onFlagRead: (session: Long, words: IntArray, trigger: String) -> Unit,
    /** Calibração em andamento (mapa K, curva K, sonda de buffer...): enquanto for verdade, nada é lido nem gravado aqui. */
    private val busy: () -> Boolean = { false },
    /** A leitura de volta falhou: o valor de FLAG_CONF3 é desconhecido, a tela não pode mostrar o antigo como "lido". */
    private val onFlagUnknown: (session: Long, trigger: String) -> Unit = { _, _ -> },
) {
    private class Pending(val token: String, val plan: AntiStalloFlagCore.Plan, val session: Long)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-anti-stallo-flag").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
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
        copy.put("busy", running.get())
        copy.put(
            "undo",
            if (undo != null && undoSession == session && session > 0L) JSONObject().put("available", true).put("enable", undo)
            else JSONObject().put("available", false),
        )
        copy.toString()
    }

    /**
     * Toque no interruptor = UMA chamada: dentro de uma só etapa MANUAL_WRITE da serial, lê FLAG_CONF3, monta o quadro,
     * grava e relê (mesmo desenho do Mapa K / Curva K). Sem prévia, sem token, sem segundo passo entre o JS e a central:
     * o resultado (ou o erro) vem pelo status. Nada dispara sozinho: só o toque do dono chega aqui.
     */
    fun apply(enable: Boolean): JSONObject {
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail(CALIBRATION_BUSY)
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "WRITING") }
        try { executor.execute {
            var published = false
            fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
            try {
                val session = sessionOrThrow()
                var plan: AntiStalloFlagCore.Plan? = null
                val result = serial.unit(
                    reason = "anti-apagão: ler, gravar FLAG_CONF3 (bit 0x20) e reler",
                    expectedSessionId = session,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 8_000L,
                ) { unit ->
                    val words = readFlags(unit)
                    onFlagRead(session, words, "AO_VIVO")
                    AntiStalloFlagCore.plan(words, enable).also { plan = it }.let { AntiStalloFlagCore.execute(io(unit), it) }
                }
                finish(Pending("", plan!!, session), result) { publish(it) }
            } catch (error: Exception) {
                val message = "A ECU está ocupada ou desconectou; nada foi dado como gravado (${error.message ?: error.javaClass.simpleName})."
                record("anti_stallo_flag_write", JSONObject().put("outcome", "ERROR").put("message", message).put("origin", "APP_UI_MANUAL"))
                publish(JSONObject().put("state", "DONE").put("outcome", "ERROR").put("message", message))
            } finally {
                if (!published) running.set(false)
            }
        } } catch (_: RejectedExecutionException) {
            return stopped()
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    /** 1º toque: lê FLAG_CONF3 ao vivo e monta a prévia. Não escreve nada. */
    fun prepare(enable: Boolean): JSONObject {
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail(CALIBRATION_BUSY)
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "PREPARING") }
        try { executor.execute {
            var published = false
            // Solta o "ocupado" e publica o estado no MESMO bloco: quem vê READY/ERROR já pode pedir outra operação.
            fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
            try {
                val session = sessionOrThrow()
                val words = esperandoAVez { serial.unit(
                    reason = "anti-apagão: ler FLAG_CONF3 para a prévia",
                    expectedSessionId = session,
                    workClass = Mp48WorkClass.READ_ONLY,
                    telemetryAfter = true,
                    waitTimeoutMs = 8_000L,
                ) { unit -> readFlags(unit) } }
                onFlagRead(session, words, "AO_VIVO")
                val plan = AntiStalloFlagCore.plan(words, enable)
                val token = UUID.randomUUID().toString()
                synchronized(lock) { pending = Pending(token, plan, session) }
                publish(
                    JSONObject()
                        .put("state", "READY")
                        .put("token", token)
                        .put("enable", enable)
                        .put("before", JSONArray(plan.before.toList()))
                        .put("after", JSONArray(plan.after.toList()))
                        .put("frameHex", hex(plan.frame))
                        .put("noop", plan.noop),
                )
            } catch (error: Exception) {
                publish(JSONObject().put("state", "ERROR").put("message", "Não foi possível ler a ECU: ${error.message ?: error.javaClass.simpleName}"))
            } finally {
                if (!published) running.set(false)
            }
        } } catch (_: RejectedExecutionException) {
            return stopped()
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    /** 2º toque (confirmação): só com o token da prévia, na mesma sessão USB. */
    fun confirm(token: String): JSONObject {
        val ready = synchronized(lock) { pending }
        if (ready == null || ready.token != token) return fail("Não há prévia válida; refaça o toque.")
        if (ready.plan.noop) return fail("O anti-apagão já está no estado pedido.")
        if (!serial.isConnected() || currentSessionOrZero() != ready.session) return fail("USB mudou desde a prévia; refaça o toque.")
        if (busy()) return fail(CALIBRATION_BUSY) // a prévia continua valida: nada mudou
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "WRITING") }
        try { executor.execute {
            var published = false
            fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
            try {
                val result = serial.unit(
                    reason = "anti-apagão: gravar FLAG_CONF3 (bit 0x20) com leitura de volta",
                    expectedSessionId = ready.session,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 8_000L,
                ) { unit -> AntiStalloFlagCore.execute(io(unit), ready.plan) }
                finish(ready, result) { publish(it) }
            } catch (error: Exception) {
                val message = "A ECU está ocupada ou desconectou; nada foi dado como gravado (${error.message ?: error.javaClass.simpleName})."
                record("anti_stallo_flag_write", JSONObject().put("outcome", "ERROR").put("message", message).put("origin", "APP_UI_MANUAL"))
                publish(JSONObject().put("state", "DONE").put("outcome", "ERROR").put("message", message))
            } finally {
                if (!published) running.set(false)
            }
        } } catch (_: RejectedExecutionException) {
            return stopped()
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

    private fun finish(ready: Pending, result: AntiStalloFlagCore.Result, publish: (JSONObject) -> Unit) {
        val plan = ready.plan
        result.readback?.let { onFlagRead(ready.session, it, "APOS_GRAVAR") }
        if (result.outcome == AntiStalloFlagCore.Outcome.READBACK_FAILED) onFlagUnknown(ready.session, "APOS_GRAVAR_FALHOU")
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
            } else if (result.outcome == AntiStalloFlagCore.Outcome.READBACK_FAILED || result.outcome == AntiStalloFlagCore.Outcome.READBACK_DIVERGED || result.valueChanged) {
                // A ECU pode já não estar no estado que o "Desfazer" supõe: não oferecer desfazer sobre valor incerto.
                undoEnable = null
            }
        }
        record("anti_stallo_flag_write", JSONObject(json.toString()).put("origin", "APP_UI_MANUAL").put("enableRequested", plan.enable))
        publish(json)
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

    /** Escritor já encerrado (serviço parando): nada foi enviado, libera o ocupado e diz isso. */
    private fun stopped(): JSONObject {
        synchronized(lock) {
            running.set(false)
            pending = null
            status = JSONObject().put("state", "ERROR").put("message", "O app está encerrando; nada foi enviado.")
        }
        return fail("O app está encerrando; nada foi enviado.")
    }

    private fun fail(message: String) = JSONObject().put("ok", false).put("error", message)

    fun close() = executor.shutdownNow()

    private companion object {
        const val CALIBRATION_BUSY = "Há calibração em andamento, tente de novo."
    }
}

/**
 * A leitura prévia usa a faixa de leitura, que RECUSA na hora quando a serial está ocupada (telemetria/AutoCal lendo).
 * Para quem tocou no botão isso não pode virar erro: espera a vez, por evento (cada recusa = a serial ainda está ocupada), no máximo ~3 s.
 */
internal inline fun <T> esperandoAVez(block: () -> T): T {
    var tentativa = 0
    while (true) {
        try { return block() } catch (error: com.omegas.prohub.ecu.Mp48BackpressureRejectedException) {
            if (++tentativa >= 30) throw error
            Thread.sleep(100)
        }
    }
}
