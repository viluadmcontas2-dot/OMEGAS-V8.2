package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Liga as preferências da central (AutoCalPreferenceCore) à porta serial. Só por toque do dono.
 * `read({"ids":[...]})` -> {ok, values:{id: valor|null}}; `apply({"id":..,"raw":..})` -> {ok, outcome, before, after, message}.
 * Desfazer = a tela chama `apply` de novo com `before`. Nada aqui dispara sozinho; ACK + releitura igual para dar "Gravado".
 */
class AutoCalPreferenceWriter(
    private val serial: Mp48SerialScheduler,
    private val record: (type: String, data: JSONObject) -> Unit,
    private val busy: () -> Boolean = { false },
) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "omegas-autocal-pref").apply { isDaemon = true } }

    fun read(json: String): JSONObject {
        val ids = try {
            val arr = JSONObject(json).getJSONArray("ids")
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { return fail("Pedido inválido.") }
        if (ids.isEmpty() || ids.size > 64 || ids.any { AutoCalPreferenceCore.spec(it) == null }) return fail("Pedido inválido.")
        if (!serial.isConnected()) return fail("USB desconectado")
        val session = serial.currentSessionId()
        return onWorker {
            serial.unit("preferências do AutoCal: ler", session, Mp48WorkClass.READ_ONLY, true, 8_000L) { unit ->
                val values = AutoCalPreferenceCore.readMany(io(unit), ids)
                val out = JSONObject()
                for ((k, v) in values) out.put(k, v ?: JSONObject.NULL)
                JSONObject().put("ok", true).put("values", out)
            }
        } ?: fail("A central está ocupada ou o cabo soltou.")
    }

    fun apply(json: String): JSONObject {
        val req = try { JSONObject(json) } catch (_: Exception) { return fail("Pedido inválido.") }
        val id = req.optString("id")
        val raw = req.opt("raw")
        if (AutoCalPreferenceCore.spec(id) == null || raw !is Int) return fail("Pedido inválido.")
        if (!serial.isConnected()) return fail("USB desconectado")
        if (busy()) return fail("Há calibração em andamento, tente de novo.")
        val session = serial.currentSessionId()
        val result = onWorker {
            serial.unit("preferências do AutoCal: $id (ler, gravar, reler)", session, Mp48WorkClass.MANUAL_WRITE, true, 15_000L) { unit ->
                AutoCalPreferenceCore.execute(io(unit), id, raw)
            }
        } ?: return fail("A central está ocupada ou o cabo soltou. Nada foi dado como gravado.")
        record(
            "autocal_preference_write",
            JSONObject().put("id", id).put("raw", raw).put("outcome", result.outcome.name)
                .put("before", result.before ?: JSONObject.NULL).put("after", result.after ?: JSONObject.NULL)
                .put("frame", result.frame?.let(::hex) ?: JSONObject.NULL).put("origin", "APP_UI_MANUAL"),
        )
        return JSONObject().put("ok", result.ok).put("outcome", result.outcome.name).put("id", id)
            .put("before", result.before ?: JSONObject.NULL).put("after", result.after ?: JSONObject.NULL)
            .put("message", result.message)
            .put("frameHex", JSONArray(listOfNotNull(result.frame?.let(::hex))))
    }

    private fun <T> onWorker(block: () -> T): T? = try {
        executor.submit(Callable { block() }).get(25, TimeUnit.SECONDS)
    } catch (_: Exception) { null }

    private fun io(unit: Mp48SerialUnit) = AutoCalPreferenceCore.Io(
        read = { frame ->
            val reply = unit.transaction(frame, "preferências do AutoCal: ler", 900, purgeBefore = false)
            if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) throw IllegalStateException(reply.error.ifBlank { "sem resposta" })
            IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF }
        },
        write = { frame ->
            val reply = unit.transaction(frame, "preferências do AutoCal: gravar", 900, purgeBefore = false)
            val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
            AutoCalPreferenceCore.WriteReply(acked, if (acked) "ACK" else "status 0x%02X".format(reply.status and 0xFF))
        },
    )

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    private fun fail(message: String) = JSONObject().put("ok", false).put("error", message)
    fun close() = executor.shutdownNow()
}
