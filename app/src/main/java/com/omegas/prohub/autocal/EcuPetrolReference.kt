package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * Curva de gasolina que a ECU já tem, extraída da aquisição lida (AutoCalAcquisition): pontos de
 * gasolina cuja zona a ECU deu como adquirida, como pares (MAP bar, Petrol Inj. ms).
 *
 * A ECU guarda isso e entrega ao conectar, mesmo num app recém-instalado. Serve de referência de
 * gasolina para o Refino onde o app não mediu gasolina (ver EquivalenceLedger.setEcuPetrolReference).
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
}
