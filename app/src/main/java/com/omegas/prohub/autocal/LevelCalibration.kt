package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Evento `level_calibration`: a calibração do nível que a ECU guarda (lida SÓ-LEITURA ao conectar, grupo NIVEL do
 * [EcuParameterSnapshot]) em forma pronta para análise. Desconhecido (NACK, falha, tamanho errado) é null e `complete`
 * fica false; nunca 0. A escala documentada vem do campo: 255 = vazio, escada ≈ 75/125/177/253, 19–22 = sem leitura.
 * Sem prova no app (por isso fora): STATO_DIAGNOSI (29 1D 00) e DIAGNOSI_INJ_BENZ (09 21 00).
 */
object LevelCalibration {
    private fun values(params: JSONObject, name: String): List<Int>? {
        val p = params.optJSONObject(name) ?: return null
        if (p.optString("state") != "OK") return null
        val arr = p.optJSONArray("values") ?: return null
        return List(arr.length()) { arr.optInt(it) }
    }

    fun fromParams(params: JSONObject): JSONObject {
        val type = values(params, "TIPO_SENSORE")?.firstOrNull()
        val refs = values(params, "RIF_SENSORE")?.takeIf { it.size == 4 }
        val fast = values(params, "LO_PASS_FILT_CON_FAST")?.firstOrNull()
        val slow = values(params, "LO_PASS_FILT_CON_SLOW")?.firstOrNull()
        val hysteresis = values(params, "ISTERESI_RIACCENSIONE")?.firstOrNull()
        val ledNames = listOf("SOGLIA_LED_1", "SOGLIA_LED_2", "SOGLIA_LED_3", "SOGLIA_LED_4")
        val ledValues = ledNames.map { values(params, it)?.firstOrNull() }
        val leds = if (ledValues.all { it != null }) ledValues.map { it!! } else null
        val states = JSONObject()
        listOf("TIPO_SENSORE", "RIF_SENSORE", "LO_PASS_FILT_CON_FAST", "LO_PASS_FILT_CON_SLOW", "ISTERESI_RIACCENSIONE").plus(ledNames)
            .forEach { states.put(it, params.optJSONObject(it)?.optString("state") ?: "NAO_LIDO") }
        val filter = JSONObject()
            .put("fastRaw", fast ?: JSONObject.NULL).put("slowRaw", slow ?: JSONObject.NULL)
            .put("fast", fast?.let { it / 32768.0 } ?: JSONObject.NULL).put("slow", slow?.let { it / 32768.0 } ?: JSONObject.NULL)
            .put("note", "raw/32768; periodo de atualizacao do filtro DESCONHECIDO (nao prova que o byte 13 e a saida dele)")
        return JSONObject()
            .put("complete", type != null && refs != null && fast != null && slow != null && hysteresis != null && leds != null)
            .put("typeRaw", type ?: JSONObject.NULL)
            .put("typeHex", type?.let { "0x%02X".format(it) } ?: JSONObject.NULL)
            .put("typeNote", "0x81 na ECU do dono; significado DESCONHECIDO (hipotese: indice 1 + bit 7 invertido)")
            .put("refs", refs?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("filter", filter)
            .put("hysteresis", hysteresis ?: JSONObject.NULL)
            .put("leds", leds?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("states", states)
            .put(
                "scale",
                JSONObject().put("unit", "raw_byte").put("orientation", "INVERTED_255_EMPTY")
                    .put("ladderObserved", JSONArray(listOf(75, 125, 177, 253)))
                    .put("noReading", "19-22 (central sem motor); <= 25 e invalido")
                    .put("zones", "<ref1 cheio | ref1-ref2 3/4 | ref2-ref3 1/2 | ref3-ref4 1/4 | >=ref4 reserva (dados do campo)")
                    .put("refuel", "queda >= 40 no nivel firme"),
            )
    }
}
