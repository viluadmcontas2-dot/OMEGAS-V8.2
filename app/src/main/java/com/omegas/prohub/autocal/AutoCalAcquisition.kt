package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale
import org.json.JSONArray
import org.json.JSONObject

/** Pontos crus recebidos da ECU. Nenhuma suavização, interpolação ou correção é aplicada. */
object AutoCalAcquisition {
    private data class Source(
        val fuel: String,
        val time: String,
        val map: String,
        val count: String,
        val previous: Boolean = false,
    )
    private val sources = listOf(
        Source("GASOLINA", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR"),
        Source("GNV", "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS"),
        Source("GNV_ANTERIOR", "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV", "NUM_BUF_UPD_GAS", previous = true),
    )

    fun fromSnapshot(snapshot: JSONObject): JSONObject {
        val fields = buildMap<String, JSONObject> {
            val array = snapshot.optJSONArray("fields") ?: JSONArray()
            repeat(array.length()) { index ->
                array.optJSONObject(index)?.optString("key")?.takeIf { it.isNotBlank() }?.let { key ->
                    put(key, array.optJSONObject(index)!!)
                }
            }
        }
        val petrolLowThreshold = fields["VECT_AUTOCAL_U8_1"]?.rawValues()?.firstOrNull()
        val maxAutomatch = fields["VECT_AUTOCAL_U8_2"]?.rawValues()?.firstOrNull()
        val calibration = fields["CALIBRATION_VAL_1"]?.rawValues() ?: intArrayOf()
        val petrolNormalThreshold = calibration.getOrNull(2)
        val gasLowThreshold = calibration.getOrNull(5)
        val gasNormalThreshold = calibration.getOrNull(8)
        val points = JSONArray()
        var valid = 0
        var collecting = 0
        var unknown = 0
        sources.forEach { source ->
            val times = fields[source.time]?.rawValues() ?: intArrayOf()
            val maps = fields[source.map]?.rawValues() ?: intArrayOf()
            val counts = fields[source.count]?.rawValues() ?: intArrayOf()
            repeat(18) { index ->
                val timeRaw = times.getOrNull(index)
                val mapRaw = maps.getOrNull(index)
                val count = counts.getOrNull(index)
                val threshold = when {
                    source.fuel == "GASOLINA" && index in 0..5 -> petrolLowThreshold
                    source.fuel == "GASOLINA" -> petrolNormalThreshold
                    index in 0..5 -> gasLowThreshold
                    else -> gasNormalThreshold
                }
                val state = when {
                    timeRaw == null || mapRaw == null || count == null -> "SEM_DADO"
                    threshold == null -> "LIMIAR_NAO_LIDO"
                    count >= threshold -> { valid++; "VALIDO" }
                    count > 0 -> { collecting++; "COLETANDO" }
                    else -> { unknown++; "AGUARDANDO" }
                }
                points.put(JSONObject()
                    .put("index", index)
                    .put("zone", zone(index))
                    .put("fuel", source.fuel)
                    .put("timeRaw", timeRaw ?: JSONObject.NULL)
                    .put("timeMs", timeRaw?.let { AutoCalScale.injectionMs(it) } ?: JSONObject.NULL)
                    .put("mapRaw", mapRaw ?: JSONObject.NULL)
                    .put("mapBar", mapRaw?.let { AutoCalScale.mapBar(it) } ?: JSONObject.NULL)
                    .put("counter", count ?: JSONObject.NULL)
                    .put("threshold", threshold ?: JSONObject.NULL)
                    .put("state", state)
                    .put("draw", state == "VALIDO")
                    .put("previous", source.previous)
                    .put("rawOnly", true))
            }
        }
        return JSONObject()
            .put("points", points)
            .put("pointCount", points.length())
            .put("validCount", valid)
            .put("collectingCount", collecting)
            .put("unknownCount", unknown)
            .put("thresholds", JSONObject()
                .put("petrolUpdate", petrolLowThreshold ?: JSONObject.NULL)
                .put("gasUpdate", gasLowThreshold ?: JSONObject.NULL)
                .put("petrolLow", petrolLowThreshold ?: JSONObject.NULL)
                .put("petrolNormal", petrolNormalThreshold ?: JSONObject.NULL)
                .put("gasLow", gasLowThreshold ?: JSONObject.NULL)
                .put("gasNormal", gasNormalThreshold ?: JSONObject.NULL)
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("calibrationValues", JSONArray(calibration.toList()))
                .put("source", "ECU"))
            .put("rawOnly", true)
            .put("noCorrectionApplied", true)
    }

    private fun zone(index: Int): Int = when (index) {
        in 0..5 -> 0
        in 6..9 -> 1
        in 10..13 -> 2
        else -> 3
    }

    private fun JSONObject.rawValues(): IntArray {
        val array = optJSONArray("rawValues") ?: return intArrayOf()
        return IntArray(array.length()) { array.optInt(it) }
    }
}
