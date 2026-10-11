package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * Curva de gasolina que a ECU já tem, extraída da aquisição lida (AutoCalAcquisition): pontos de
 * gasolina cuja zona a ECU deu como adquirida, como pares (MAP bar, Petrol Inj. ms).
 *
 * A ECU guarda isso e entrega ao conectar. É a referência de gasolina do Refino onde ela cobre o MAP
 * (ver EquivalenceLedger.setEcuPetrolReference). Portado da Platina (commit 00f6fd02).
 */
object EcuPetrolReference {
    fun fromAcquisition(acquisition: JSONObject?): List<Pair<Double, Double>> {
        val points = acquisition?.optJSONArray("points") ?: return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            if (p.optString("fuel") != "GASOLINA" || p.optBoolean("previous")) continue
            if (p.optString("state") != "ZONA_ADQUIRIDA") continue
            if (p.isNull("timeMs") || p.isNull("mapBar")) continue
            val time = p.optDouble("timeMs", Double.NaN)
            val map = p.optDouble("mapBar", Double.NaN)
            if (time.isFinite() && map.isFinite() && time > 0.0 && map > 0.0) out += map to time
        }
        return out
    }

    /**
     * Pontos de gasolina com atividade nos buffers da ECU (aquisição atual). null quando os campos não vieram
     * (leitura incompleta não pode parecer "aquisição recomeçou"). Cair de >0 para 0 = a ECU limpou a
     * gasolina ("Readquirir gasolina" ou aquisição nova).
     */
    fun petrolActivity(acquisition: JSONObject?): Int? {
        val points = acquisition?.optJSONArray("points") ?: return null
        var seen = 0
        var active = 0
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            if (p.optString("fuel") != "GASOLINA" || p.optBoolean("previous")) continue
            if (p.optString("state") == "SEM_DADO") return null
            seen++
            if (p.optBoolean("draw")) active++
        }
        return if (seen == 0) null else active
    }
}
