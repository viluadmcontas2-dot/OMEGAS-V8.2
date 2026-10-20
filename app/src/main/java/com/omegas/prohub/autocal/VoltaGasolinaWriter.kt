package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Liga a volta automática à gasolina (VoltaGasolinaCore) à porta serial. Só roda por toque do dono: UMA chamada `apply(json)`
 * = ler → gravar (cada quadro com ACK) → reler → comparar; "Gravado" só depois da releitura igual. Desfazer = outro toque.
 * Nada aqui dispara sozinho. `readCurrent()` só lê (TEMPO_RITORNO_BENZINA, RIF_PRESS_DIFF, COEFF_PRESS_DIFF).
 *
 * `apply`: {"acao":"desligar"} | {"acao":"ligar"} | {"acao":"espera","segundos":0..3} | {"acao":"pressao","mbar":200..2000}
 * | {"acao":"desfazer"}. A pressão só grava com VoltaGasolinaCore.PRESSURE_WRITE_ENABLED e se a tabela lida bater com a do
 * original (VoltaGasolinaCore.consistentWithOriginal); senão recusa sem enviar.
 */
class VoltaGasolinaWriter(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
    /** Valor lido da central (leitura, prévia ou releitura): atualiza o que a tela mostra. */
    private val onRead: (session: Long, name: String, address: Int, values: IntArray, trigger: String) -> Unit,
    private val busy: () -> Boolean = { false },
    private val onUnknown: (session: Long, name: String, address: Int, trigger: String) -> Unit = { _, _, _, _ -> },
) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "omegas-volta-gasolina").apply { isDaemon = true } }
    private val running = AtomicBoolean(false)
    private val reading = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var status: JSONObject = JSONObject().put("state", "IDLE")
    /** Tempo guardado ao desligar (como o campo +0x1270 do formulário original): religar devolve este tempo. */
    private var savedTempo = 0
    private var undoTempo: Int? = null
    private var undoRif: IntArray? = null
    private var undoCoeff: IntArray? = null
    private var undoSession = 0L

    fun statusJson(): String = synchronized(lock) {
        val copy = JSONObject(status.toString())
        val session = currentSessionOrZero()
        val available = session > 0L && session == undoSession && (undoTempo != null || undoRif != null)
        copy.put("busy", running.get())
        copy.put("pressureWritable", VoltaGasolinaCore.PRESSURE_WRITE_ENABLED)
        copy.put("notProven", JSONArray(VoltaGasolinaCore.NOT_PROVEN))
        copy.put("undo", JSONObject().put("available", available))
        copy.toString()
    }

    /** Só leitura, na fila READ_ONLY. Duas visitas seguidas não duplicam a leitura. */
    fun readCurrent(): JSONObject {
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail(CALIBRATION_BUSY)
        if (!reading.compareAndSet(false, true)) return JSONObject().put("ok", true).put("alreadyReading", true)
        try {
            executor.execute {
                try {
                    val session = sessionOrThrow()
                    serial.unit("volta à gasolina: ler tempo e tabela de pressão", session, Mp48WorkClass.READ_ONLY, true, 8_000L) { unit ->
                        publishRead(session, "TEMPO_RITORNO_BENZINA", VoltaGasolinaCore.TEMPO_ADDRESS, intArrayOf(readTempo(unit)), "ABRIR_VOLTA")
                        publishRead(session, "RIF_PRESS_DIFF", VoltaGasolinaCore.RIF_ADDRESS, readWords(unit, VoltaGasolinaCore.RIF_ADDRESS), "ABRIR_VOLTA")
                        publishRead(session, "COEFF_PRESS_DIFF", VoltaGasolinaCore.COEFF_ADDRESS, readWords(unit, VoltaGasolinaCore.COEFF_ADDRESS), "ABRIR_VOLTA")
                    }
                } catch (error: Exception) {
                    record("volta_gasolina_read", JSONObject().put("outcome", "ERROR").put("error", (error.message ?: error.javaClass.simpleName).take(120)))
                } finally {
                    reading.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            reading.set(false)
            return fail("O aplicativo está encerrando.")
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    fun apply(json: String): JSONObject {
        val req = try { JSONObject(json) } catch (_: Exception) { return fail("Pedido inválido.") }
        val acao = req.optString("acao")
        if (acao !in setOf("ligar", "desligar", "espera", "pressao", "salvar", "desfazer")) return fail("Pedido inválido.")
        val seconds: Int? = if (acao in setOf("espera", "salvar") && req.has("segundos")) {
            val v = req.opt("segundos")
            if (v !is Int || v !in 0..VoltaGasolinaCore.TEMPO_MAX) return fail("A espera tem que ficar entre 0 e ${VoltaGasolinaCore.TEMPO_MAX} segundos.")
            v
        } else null
        val mbar: Int? = if (acao in setOf("pressao", "salvar") && req.has("mbar")) {
            if (!VoltaGasolinaCore.PRESSURE_WRITE_ENABLED) return fail("Mudar a pressão não está liberado neste aplicativo. Nada foi enviado.")
            val v = req.opt("mbar")
            if (v !is Int || v !in VoltaGasolinaCore.PMP_MIN_MBAR..VoltaGasolinaCore.PMP_MAX_MBAR || v % 5 != 0) return fail("A pressão tem que ficar entre 0,2 e 2,0 bar.")
            v
        } else null
        if (acao == "espera" && seconds == null) return fail("A espera tem que ficar entre 0 e ${VoltaGasolinaCore.TEMPO_MAX} segundos.")
        if (acao == "pressao" && mbar == null) return fail("A pressão tem que ficar entre 0,2 e 2,0 bar.")
        if (acao == "salvar" && seconds == null && mbar == null) return fail("Nada mudou para salvar.")
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail(CALIBRATION_BUSY)
        val session = try { sessionOrThrow() } catch (_: Exception) { return fail("USB desconectado") }
        val undo = synchronized(lock) { if (undoSession == session) Triple(undoTempo, undoRif, undoCoeff) else Triple(null, null, null) }
        if (acao == "desfazer" && undo.first == null && undo.second == null) return fail("Não há nada para desfazer.")
        if (!running.compareAndSet(false, true)) return fail("Há outra operação em andamento; tente de novo.")
        synchronized(lock) { status = JSONObject().put("state", "WRITING").put("acao", acao) }
        try {
            executor.execute {
                var published = false
                fun publish(next: JSONObject) { if (published) return; synchronized(lock) { running.set(false); status = next }; published = true }
                try {
                    val result = serial.unit("volta à gasolina: $acao (ler, gravar, reler)", session, Mp48WorkClass.MANUAL_WRITE, true, 15_000L) { unit ->
                        val io = io(unit)
                        when (acao) {
                            "desligar" -> {
                                val now = io.readTempo()
                                if (now != VoltaGasolinaCore.OFF) synchronized(lock) { savedTempo = now }
                                VoltaGasolinaCore.executeTempo(io, VoltaGasolinaCore.OFF)
                            }
                            "ligar" -> VoltaGasolinaCore.executeTempo(io, VoltaGasolinaCore.toggleTarget(true, synchronized(lock) { savedTempo }))
                            "espera" -> VoltaGasolinaCore.executeTempo(io, seconds!!)
                            "pressao" -> VoltaGasolinaCore.executePressure(io, mbar!!)
                            // Salvar = espera e pressão num toque; a pressão só segue se a espera deu certo.
                            "salvar" -> {
                                val t = seconds?.let { VoltaGasolinaCore.executeTempo(io, it) }
                                val p = if (t == null || t.ok) mbar?.let { VoltaGasolinaCore.executePressure(io, it) } else null
                                VoltaGasolinaCore.combine(t, p)
                            }
                            else -> {
                                val t = undo.first?.let { VoltaGasolinaCore.executeTempo(io, it) }
                                val p = if (t == null || t.ok) undo.second?.let { VoltaGasolinaCore.executeRestore(io, it, undo.third!!) } else null
                                VoltaGasolinaCore.combine(t, p)
                            }
                        }
                    }
                    publish(finish(session, acao, result))
                } catch (error: Throwable) {
                    val message = "A central está ocupada ou o cabo soltou. Nada foi dado como gravado. Confira o cabo e tente de novo."
                    record("volta_gasolina_write", JSONObject().put("outcome", "ERROR").put("acao", acao).put("message", message))
                    publish(JSONObject().put("state", "DONE").put("outcome", "ERROR").put("message", message))
                } finally {
                    if (!published) running.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            synchronized(lock) { running.set(false); status = JSONObject().put("state", "ERROR").put("message", "O app está encerrando; nada foi enviado.") }
            return fail("O app está encerrando; nada foi enviado.")
        }
        return JSONObject().put("ok", true).put("started", true)
    }

    private fun finish(session: Long, acao: String, r: VoltaGasolinaCore.Result): JSONObject {
        r.tempo?.let { publishRead(session, "TEMPO_RITORNO_BENZINA", VoltaGasolinaCore.TEMPO_ADDRESS, intArrayOf(it), "APOS_GRAVAR") }
        r.rif?.let { publishRead(session, "RIF_PRESS_DIFF", VoltaGasolinaCore.RIF_ADDRESS, it, "APOS_GRAVAR") }
        r.coeff?.let { publishRead(session, "COEFF_PRESS_DIFF", VoltaGasolinaCore.COEFF_ADDRESS, it, "APOS_GRAVAR") }
        if (r.outcome == VoltaGasolinaCore.Outcome.READBACK_FAILED) {
            if (r.beforeTempo != null) onUnknown(session, "TEMPO_RITORNO_BENZINA", VoltaGasolinaCore.TEMPO_ADDRESS, "APOS_GRAVAR_FALHOU")
            if (r.beforeRif != null) {
                onUnknown(session, "RIF_PRESS_DIFF", VoltaGasolinaCore.RIF_ADDRESS, "APOS_GRAVAR_FALHOU")
                onUnknown(session, "COEFF_PRESS_DIFF", VoltaGasolinaCore.COEFF_ADDRESS, "APOS_GRAVAR_FALHOU")
            }
        }
        synchronized(lock) {
            val wrote = r.sent > 0 && r.outcome != VoltaGasolinaCore.Outcome.NOT_ACKED
            if (acao == "desfazer" && r.outcome == VoltaGasolinaCore.Outcome.CONFIRMED) {
                undoTempo = null; undoRif = null; undoCoeff = null
            } else if (acao != "desfazer" && wrote) {
                // Desfazer = voltar ao que a central tinha ANTES deste toque (também depois de falha no meio).
                undoSession = session
                undoTempo = r.beforeTempo
                undoRif = r.beforeRif?.copyOf()
                undoCoeff = r.beforeCoeff?.copyOf()
            }
        }
        val json = JSONObject()
            .put("state", "DONE")
            .put("acao", acao)
            .put("outcome", r.outcome.name)
            .put("message", r.message)
            .put("tempo", r.tempo ?: JSONObject.NULL)
            .put("beforeTempo", r.beforeTempo ?: JSONObject.NULL)
            .put("sent", r.sent)
            .put("frameHex", JSONArray(r.frames.take(r.sent).map { hex(it) }))
        record("volta_gasolina_write", JSONObject(json.toString()).put("origin", "APP_UI_MANUAL"))
        return json
    }

    private fun publishRead(session: Long, name: String, address: Int, values: IntArray, trigger: String) {
        // COEFF_PRESS_DIFF é s16 (o original limita a ±99,9 × 100): a tela recebe com sinal, como no snapshot.
        val shown = if (address == VoltaGasolinaCore.COEFF_ADDRESS) IntArray(values.size) { values[it].toShort().toInt() } else values
        if (serial.isConnected() && serial.currentSessionId() == session) onRead(session, name, address, shown, trigger)
    }

    private fun io(unit: Mp48SerialUnit) = VoltaGasolinaCore.Io(
        readTempo = { readTempo(unit) },
        readRif = { readWords(unit, VoltaGasolinaCore.RIF_ADDRESS) },
        readCoeff = { readWords(unit, VoltaGasolinaCore.COEFF_ADDRESS) },
        write = { frame ->
            val reply = try { unit.transaction(frame, "volta à gasolina: gravar", 900, purgeBefore = false) } catch (e: Exception) {
                return@Io VoltaGasolinaCore.WriteReply(false, true, "", e.message ?: e.javaClass.simpleName)
            }
            val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
            VoltaGasolinaCore.WriteReply(acked, !acked && reply.rawResponse.isEmpty() && reply.status < 0, hex(reply.rawResponse),
                if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim())
        },
    )

    private fun payload(unit: Mp48SerialUnit, request: ByteArray, what: String): IntArray {
        val reply = unit.transaction(request, "volta à gasolina: ler $what", 900, purgeBefore = false)
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) throw IllegalStateException(reply.error.ifBlank { "central não respondeu à leitura de $what" })
        return IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF }
    }

    private fun readTempo(unit: Mp48SerialUnit): Int {
        val p = payload(unit, AutoCalProtocol.readScalar(VoltaGasolinaCore.TEMPO_ADDRESS), "TEMPO_RITORNO_BENZINA")
        if (p.size != 1) throw IllegalStateException("TEMPO_RITORNO_BENZINA veio com ${p.size} bytes (esperado 1)")
        return p[0]
    }

    private fun readWords(unit: Mp48SerialUnit, address: Int): IntArray {
        val p = payload(unit, AutoCalProtocol.readVector(address), "SC $address")
        return VoltaGasolinaCore.wordsFromPayload(p, VoltaGasolinaCore.POINTS)
            ?: throw IllegalStateException("SC $address veio com ${p.size} bytes (esperado 30)")
    }

    private fun sessionOrThrow(): Long = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
        ?: throw IllegalStateException("USB desconectado")

    private fun currentSessionOrZero(): Long = if (serial.isConnected()) serial.currentSessionId() else 0L
    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    private fun fail(message: String) = JSONObject().put("ok", false).put("error", message)
    fun close() = executor.shutdownNow()

    private companion object {
        const val CALIBRATION_BUSY = "Há calibração em andamento, tente de novo."
    }
}
