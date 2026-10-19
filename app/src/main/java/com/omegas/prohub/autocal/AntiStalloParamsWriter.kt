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
 * Liga a escrita manual dos 5 números do anti-apagão (AntiStalloParamsCore) à porta serial. Espelho de AntiStalloFlagWriter.
 * SÓ roda por toque do dono: `prepare(changes)` (valida a faixa, lê ANTI_STALLO ao vivo e guarda a prévia), depois
 * `confirm(token)` (2º toque). Nada aqui dispara sozinho: nenhum laço nem relógio de decisão; esperar a central = esperar a
 * resposta de cada transação. Escrita pelo agendador MANUAL_WRITE (registrada na sessão). Só usa `AutoCalProtocol.readVector/writeVectorU16`.
 *
 * `changes` = objeto JSON `{"índice": valor}` com inteiros; os campos que não aparecem ficam como a leitura ao vivo.
 * "Restaurar fábrica" e "Desfazer" são pedidos comuns (`factoryChangesJson`, `undo.values`): passam pela mesma prévia e pelo mesmo 2º toque.
 */
class AntiStalloParamsWriter(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
    /** Valores lidos de ANTI_STALLO (prévia ou leitura de volta): atualiza o que a tela mostra. */
    private val onRead: (session: Long, values: IntArray, trigger: String) -> Unit,
    /** Calibração em andamento: enquanto for verdade, nada é lido nem gravado aqui. */
    private val busy: () -> Boolean = { false },
    /** A leitura de volta falhou: o valor é desconhecido, a tela não pode mostrar o antigo como "lido". */
    private val onUnknown: (session: Long, trigger: String) -> Unit = { _, _ -> },
) {
    private class Pending(val token: String, val plan: AntiStalloParamsCore.Plan, val session: Long)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-anti-stallo-params").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var pending: Pending? = null
    /** Valores anteriores da última gravação CONFIRMADA (para "Desfazer"), da sessão USB em que foi feita. */
    private var undoValues: IntArray? = null
    private var undoSession = 0L
    @Volatile private var status: JSONObject = JSONObject().put("state", "IDLE")

    fun statusJson(): String = synchronized(lock) {
        val copy = JSONObject(status.toString())
        val session = currentSessionOrZero()
        val undo = undoValues
        copy.put("busy", running.get())
        copy.put(
            "undo",
            if (undo != null && undoSession == session && session > 0L) JSONObject().put("available", true).put("values", JSONArray(undo.toList()))
            else JSONObject().put("available", false),
        )
        copy.toString()
    }

    /** `{"0":1700}` → mapa índice→valor. Só inteiros (1.5, "abc" e lista são recusados). Devolve texto de erro em linguagem simples. */
    private fun parseChanges(text: String): Pair<Map<Int, Int>?, String?> {
        val obj = try { JSONObject(text) } catch (_: Exception) { return null to "Pedido inválido." }
        val out = LinkedHashMap<Int, Int>()
        for (key in obj.keys()) {
            val index = key.toIntOrNull() ?: return null to "Esse campo não existe."
            val raw = obj.get(key)
            if (raw !is Int && raw !is Long) return null to "O valor tem que ser um número inteiro."
            val value = (raw as Number).toLong()
            if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) return null to "O valor está fora da faixa."
            out[index] = value.toInt()
        }
        if (out.isEmpty()) return null to "Nenhum número foi informado."
        return out to AntiStalloParamsCore.validate(out)
    }

    /** 1º toque: valida, lê ANTI_STALLO ao vivo e monta a prévia. Não escreve nada. */
    fun prepare(changesJson: String): JSONObject {
        val (changes, problem) = parseChanges(changesJson)
        if (changes == null || problem != null) return fail(problem ?: "Pedido inválido.")
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail(CALIBRATION_BUSY)
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "PREPARING") }
        try { executor.execute {
            var published = false
            fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
            try {
                val session = sessionOrThrow()
                val values = esperandoAVez { serial.unit(
                    reason = "anti-apagão: ler ANTI_STALLO para a prévia",
                    expectedSessionId = session,
                    workClass = Mp48WorkClass.READ_ONLY,
                    telemetryAfter = true,
                    waitTimeoutMs = 8_000L,
                ) { unit -> readValues(unit) } }
                onRead(session, values, "AO_VIVO")
                val plan = AntiStalloParamsCore.plan(values, changes)
                val token = UUID.randomUUID().toString()
                synchronized(lock) { pending = Pending(token, plan, session) }
                publish(
                    JSONObject()
                        .put("state", "READY")
                        .put("token", token)
                        .put("before", JSONArray(plan.before.toList()))
                        .put("after", JSONArray(plan.after.toList()))
                        .put("changed", JSONArray(plan.changedIndices))
                        .put("frameHex", hex(plan.frame))
                        .put("noop", plan.noop),
                )
            } catch (error: Exception) {
                publish(JSONObject().put("state", "ERROR").put("message", "Não consegui ler a central. Nada foi enviado. Confira o cabo e tente de novo."))
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
        if (ready == null || ready.token != token) return fail("Não há conferência válida; refaça o toque.")
        if (ready.plan.noop) return fail("Esses números já estão assim na central.")
        if (!serial.isConnected() || currentSessionOrZero() != ready.session) return fail("O cabo USB mudou desde a conferência; refaça o toque.")
        if (busy()) return fail(CALIBRATION_BUSY) // a prévia continua válida: nada mudou
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { pending = null; status = JSONObject().put("state", "WRITING") }
        try { executor.execute {
            var published = false
            fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
            try {
                val result = serial.unit(
                    reason = "anti-apagão: gravar ANTI_STALLO (5 números) com leitura de volta",
                    expectedSessionId = ready.session,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 8_000L,
                ) { unit -> AntiStalloParamsCore.execute(io(unit), ready.plan) }
                finish(ready, result) { publish(it) }
            } catch (error: Exception) {
                val message = "A central está ocupada ou o cabo soltou. Nada foi dado como gravado. Confira o cabo e tente de novo."
                record("anti_stallo_params_write", JSONObject().put("outcome", "ERROR").put("message", message).put("origin", "APP_UI_MANUAL"))
                publish(JSONObject().put("state", "DONE").put("outcome", "ERROR").put("message", message))
            } finally {
                if (!published) running.set(false)
            }
        } } catch (_: RejectedExecutionException) {
            return stopped()
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    /** Descarta a prévia (outro toque, troca de aba, USB caiu). Não mexe na central. */
    fun cancel(): JSONObject {
        synchronized(lock) {
            if (status.optString("state") == "READY") status = JSONObject().put("state", "IDLE")
            pending = null
        }
        return JSONObject().put("ok", true)
    }

    private fun finish(ready: Pending, result: AntiStalloParamsCore.Result, publish: (JSONObject) -> Unit) {
        val plan = ready.plan
        result.readback?.let { onRead(ready.session, it, "APOS_GRAVAR") }
        if (result.outcome == AntiStalloParamsCore.Outcome.READBACK_FAILED) onUnknown(ready.session, "APOS_GRAVAR_FALHOU")
        val json = JSONObject()
            .put("state", "DONE")
            .put("outcome", result.outcome.name)
            .put("message", result.message)
            .put("before", JSONArray(plan.before.toList()))
            .put("after", JSONArray(plan.after.toList()))
            .put("changed", JSONArray(plan.changedIndices))
            .put("readback", result.readback?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("valueChanged", result.valueChanged)
            .put("frameHex", result.frame?.let { hex(it) } ?: "")
            .put("replyHex", result.reply?.replyHex ?: "")
        synchronized(lock) {
            if (result.outcome == AntiStalloParamsCore.Outcome.CONFIRMED) {
                undoValues = plan.before.copyOf()
                undoSession = ready.session
            } else if (result.outcome == AntiStalloParamsCore.Outcome.READBACK_FAILED || result.outcome == AntiStalloParamsCore.Outcome.READBACK_DIVERGED || result.valueChanged) {
                // A central pode já não estar no estado que o "Desfazer" supõe: não oferecer desfazer sobre valor incerto.
                undoValues = null
            }
        }
        publish(json)
        record("anti_stallo_params_write", JSONObject(json.toString()).put("origin", "APP_UI_MANUAL"))
    }

    private fun io(unit: Mp48SerialUnit) = AntiStalloParamsCore.Io(
        read = { readValues(unit) },
        write = { frame ->
            val reply = try {
                unit.transaction(frame, "anti-apagão: SetVector ANTI_STALLO", 900, purgeBefore = false)
            } catch (error: Exception) {
                return@Io AntiStalloParamsCore.WriteReply(false, true, "", error.message ?: error.javaClass.simpleName)
            }
            val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
            AntiStalloParamsCore.WriteReply(
                acked, !acked && reply.rawResponse.isEmpty() && reply.status < 0, hex(reply.rawResponse),
                if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim(),
            )
        },
    )

    private fun readValues(unit: Mp48SerialUnit): IntArray {
        val reply = unit.transaction(AutoCalProtocol.readVector(AntiStalloParamsCore.ADDRESS), "anti-apagão: ler ANTI_STALLO", 900, purgeBefore = false)
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) throw IllegalStateException(reply.error.ifBlank { "central não respondeu à leitura de ANTI_STALLO" })
        return AntiStalloParamsCore.valuesFromPayload(IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF })
            ?: throw IllegalStateException("ANTI_STALLO veio com ${reply.payload.size} bytes (esperado 10)")
    }

    private fun sessionOrThrow(): Long = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
        ?: throw IllegalStateException("USB desconectado")

    private fun currentSessionOrZero(): Long = if (serial.isConnected()) serial.currentSessionId() else 0L

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

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
