package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Troca de combustível (GNV ↔ gasolina), por CONTAGEM de quadros (sem relógio): o estado só vale depois de
 * [CONFIRM_FRAMES] quadros seguidos iguais, então piscada de 1–2 quadros não vira evento. Variantes `0x20` do mesmo estado
 * (0x80/0xA0, 0x88/0xA8, 0x90/0xB0) contam como o mesmo. Emite `fuel_change` com água CRUA (a conversão é offline). Puro.
 */
class FuelTracker(private val emit: (type: String, data: JSONObject) -> Unit) {
    companion object { const val CONFIRM_FRAMES = 3 }

    private var current: String? = null
    private var currentByte: Int? = null
    private var candidate: String? = null
    private var candidateCount = 0
    private var candidateFirst: Triple<Long, Int, Pair<Int, Int>>? = null // elapsed, byte, (rpm, water)

    private fun classOf(fuelByte: Int, rpm: Int): String = when {
        fuelByte == 0x00 || rpm <= 0 -> "MOTOR_OFF"
        fuelByte == 0x80 || fuelByte == 0xA0 -> "GASOLINA"
        fuelByte == 0x88 || fuelByte == 0xA8 -> "TRANSICAO"
        fuelByte == 0x90 || fuelByte == 0xB0 -> "GNV"
        else -> "DESCONHECIDO"
    }

    @Synchronized
    fun frame(elapsedMs: Long, fuelByte: Int, rpm: Int, waterRaw: Int) {
        val c = classOf(fuelByte, rpm)
        if (c == current) { candidate = null; candidateCount = 0; return }
        if (c != candidate) {
            candidate = c; candidateCount = 1; candidateFirst = Triple(elapsedMs, fuelByte, rpm to waterRaw)
        } else candidateCount += 1
        if (candidateCount < CONFIRM_FRAMES) return
        val first = candidateFirst ?: return
        emit(
            "fuel_change",
            JSONObject().put("from", current ?: JSONObject.NULL).put("to", c)
                .put("fromByte", currentByte ?: JSONObject.NULL).put("toByte", first.second)
                .put("atElapsedMs", first.first).put("rpm", first.third.first).put("waterRaw", first.third.second),
        )
        current = c; currentByte = first.second; candidate = null; candidateCount = 0
    }
}

/** Ciclo de vida (serviço, USB, motor) guardado MESMO sem gravação: os últimos [capacity] entram no início da sessão seguinte. */
class LifecycleRing(private val capacity: Int = 64) {
    private val items = ArrayDeque<JSONObject>()

    @Synchronized
    fun add(kind: String, wallMs: Long, elapsedMs: Long, data: JSONObject = JSONObject()) {
        items.addLast(JSONObject().put("kind", kind).put("wallMs", wallMs).put("elapsedMs", elapsedMs).put("data", data))
        while (items.size > capacity) items.removeFirst()
    }

    @Synchronized
    fun snapshot(): JSONArray = JSONArray().also { arr -> items.forEach { arr.put(JSONObject(it.toString())) } }
}

/** Evento `user_action`: o que o dono tocou, o valor pedido e o resultado (resumido: só valores simples, texto curto). */
object UserActionEvent {
    fun json(action: String, target: String, value: JSONObject?, result: JSONObject?): JSONObject =
        JSONObject().put("action", action).put("target", target).put("origin", "OWNER_TOUCH")
            .put("value", value?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
            .put("result", result?.let { summarize(it) } ?: JSONObject.NULL)

    private fun summarize(r: JSONObject): JSONObject {
        val out = JSONObject()
        var kept = 0
        for (k in r.keys()) {
            if (kept >= 12) break
            when (val v = r.opt(k)) {
                is Boolean, is Number -> { out.put(k, v); kept++ }
                is String -> { out.put(k, v.take(120)); kept++ }
                JSONObject.NULL -> { out.put(k, JSONObject.NULL); kept++ }
            }
        }
        return out
    }
}
