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
    // Rotações aceitas, só para a tabela por Rotação máxima. -1 separa trechos (leitura interrompida). Limitado.
    private val rpmSeries = ArrayList<Int>()

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
            if (rpmSeries.isNotEmpty() && rpmSeries.last() != -1 && rpmSeries.size < MAX_SERIES) rpmSeries.add(-1)
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
        if (rpmSeries.size < MAX_SERIES) rpmSeries.add(rpm.toInt())
        recent.addLast(frame)
        while (recent.size > 8) recent.removeFirst()
    }

    /** Mesma regra de tools/sessoes/sugerir_antiapagao.py (corpus_quedas), contando leituras (0,25 s cada, 2 leituras = 0,5 s):
     * queda forte = >= 350 rpm/s, ou seja >= 175 rpm em 2 leituras; cruzamento de M quando rpm[i-2] >= M > rpm[i];
     * "sem recuperar" = rpm < 350 nas 24 leituras seguintes. Só informação: nada é gravado. */
    private fun tabelaPorRotacaoMaxima(): JSONArray {
        val out = JSONArray()
        if (reading < MIN_READINGS) return out
        val v = rpmSeries
        val ms = (1200..2200 step 200).toList()
        val acion = IntArray(ms.size); val semRec = IntArray(ms.size)
        val pegas = IntArray(ms.size); var total = 0
        var episodeEnd = -1; var hit = BooleanArray(ms.size)
        for (i in 2 until v.size - 23) {
            val window = v.subList(i - 2, i + 24)
            if (window.any { it < 0 } || v[i] <= 0 || v[i - 2] - v[i] < DROP_PER_TWO_READINGS) continue
            val nearStall = (i until i + 24).any { v[it] < 350 }
            if (nearStall && i > episodeEnd) { total++; episodeEnd = i + 23; hit = BooleanArray(ms.size) }
            for ((k, m) in ms.withIndex()) {
                if (v[i - 2] >= m && m > v[i]) {
                    acion[k]++
                    if (nearStall) { semRec[k]++; if (!hit[k]) { hit[k] = true; pegas[k]++ } }
                }
            }
        }
        for ((k, m) in ms.withIndex()) out.put(JSONObject().put("rotacaoMaxima", m).put("quedasPegas", pegas[k])
            .put("quedasTotal", total).put("acionamentos", acion[k]).put("quedasSemRecuperar", semRec[k]))
        return out
    }

    private companion object {
        const val MAX_SERIES = 200_000
        const val MIN_READINGS = 50
        const val DROP_PER_TWO_READINGS = 175
    }

    fun finish(sessionId: String): JSONObject = JSONObject()
        .put("schema", "omegas.anti-stall-session.v1").put("sessionId", sessionId)
        .put("leituras", reading).put("quedas", drops).put("paradasDoMotor", stopped)
        .put("eventos", JSONArray(events.map { JSONObject(it.toString()) }))
        .put("eventosOmitidos", (drops-events.size).coerceAtLeast(0))
        .put("maiorAjusteComQueda", largestFailedSetting ?: JSONObject.NULL)
        .put("changes", JSONObject()).put("extras", "SEM_DADO_PARA_SUGERIR")
        .put("automaticEcuWrite", false)
        .put("porRotacaoMaxima", tabelaPorRotacaoMaxima())
        .put("motivo", if (drops > 0) "O motor teve $drops quedas fortes nesta viagem. Ainda não há dados para escolher um novo ajuste."
            else "Nenhuma queda forte foi registrada nesta viagem. Isso ainda não prova que diminuir a proteção economiza gasolina.")
}
