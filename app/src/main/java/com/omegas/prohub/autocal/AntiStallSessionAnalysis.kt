package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/** Observational triage, not an emulation of the ECU's unknown internal cycle.
 * Pure native core. Decisions use readings and session events, never elapsed time.
 * No writer reference. Unknown motion/consumption cannot become a numeric recommendation.
 */
class AntiStallSessionAnalysis {
    private val recent = ArrayDeque<JSONObject>()
    private val events = ArrayList<JSONObject>()
    private var active: JSONObject? = null
    private var armed = false
    private var reading = 0
    private var drops = 0
    private var stopped = 0
    private var setting: Int? = null
    private var largestFailedSetting: Int? = null

    fun observe(type: String, data: JSONObject) {
        when (type) {
            "param_snapshot" -> {
                val p = data.optJSONObject("params")?.optJSONObject("ANTI_STALLO")
                setting = if (p?.optString("state") == "OK") readSetting(p.optJSONArray("values")) else null
            }
            "anti_stallo_params_write" -> {
                setting = if (data.optString("outcome") == "CONFIRMED") readSetting(data.optJSONArray("readback")) else null
            }
            "telemetry" -> accept(data)
        }
    }

    private fun readSetting(values: JSONArray?): Int? {
        if (values == null || values.length() != 5 || values.isNull(0)) return null
        val n = values.optDouble(0, Double.NaN)
        return n.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 0.0..6000.0 }?.toInt()
    }

    private fun accept(data: JSONObject) {
        reading++
        val rpm = data.optDouble("rpm", Double.NaN)
        if (!rpm.isFinite() || rpm !in 0.0..10000.0 || data.optString("sample_state") in setOf("TELEMETRY_GAP", "INVALID")) {
            active?.put("desfecho", "LEITURA_INTERROMPIDA")
            active = null; armed = false; recent.clear()
            return
        }
        val frame = JSONObject().put("leitura", reading).put("rpm", rpm)
        for (key in listOf("fuel", "fuel_byte", "state", "sample_state", "load_bar", "petrol_ms", "gas_ms_diagnostic")) {
            frame.put(key, data.opt(key) ?: JSONObject.NULL)
        }
        var event = active
        if (event == null && armed && rpm < 350) {
            drops++
            val gasBefore = recent.any { it.optInt("fuel_byte", -1) == 144 || it.optString("fuel") == "GNV" }
            event = JSONObject().put("inicio", reading).put("tipo", "QUEDA_FORTE")
                .put("movimento", "DESCONHECIDO").put("gnvAntes", gasBefore)
                .put("rotacaoMaximaLida", setting ?: JSONObject.NULL)
                .put("minRpm", rpm).put("parada", JSONObject.NULL)
                .put("leiturasAteRecuperar", JSONObject.NULL)
                .put("desfecho", "SEM_RECUPERACAO_REGISTRADA")
                .put("linhaDoTempo", JSONArray(recent.map { JSONObject(it.toString()) }))
            if (gasBefore && setting != null) largestFailedSetting = maxOf(largestFailedSetting ?: 0, setting!!)
            if (events.size < 100) events.add(event)
            active = event; armed = false
        }
        if (event != null) {
            event.put("minRpm", minOf(event.getDouble("minRpm"), rpm))
            if (rpm < 100 && event.isNull("parada")) {
                event.put("parada", reading).put("tipo", "MOTOR_PAROU"); stopped++
            }
            val timeline = event.getJSONArray("linhaDoTempo")
            if (timeline.length() < 48) timeline.put(frame) else event.put("linhaDoTempoLimitada", true)
            if (rpm >= 500) {
                val from = if (event.isNull("parada")) event.getInt("inicio") else event.getInt("parada")
                event.put("leiturasAteRecuperar", reading-from).put("desfecho", "RECUPEROU")
                    .put("recuperacao", frame)
                active = null
            }
        }
        if (rpm >= 500) armed = true
        recent.addLast(frame)
        while (recent.size > 8) recent.removeFirst()
    }

    fun finish(sessionId: String): JSONObject = JSONObject()
        .put("schema", "omegas.anti-stall-session.v1").put("sessionId", sessionId)
        .put("leituras", reading).put("quedas", drops).put("paradasDoMotor", stopped)
        .put("eventos", JSONArray(events.map { JSONObject(it.toString()) }))
        .put("eventosOmitidos", (drops-events.size).coerceAtLeast(0))
        .put("maiorAjusteComQueda", largestFailedSetting ?: JSONObject.NULL)
        .put("changes", JSONObject()).put("extras", "SEM_DADO_PARA_SUGERIR")
        .put("automaticEcuWrite", false)
        .put("motivo", if (drops > 0) "O motor teve $drops quedas fortes nesta viagem. Ainda não há dados para escolher um novo ajuste."
            else "Nenhuma queda forte foi registrada nesta viagem. Isso ainda não prova que diminuir a proteção economiza gasolina.")
}
