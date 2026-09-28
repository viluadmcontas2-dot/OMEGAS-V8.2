package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pontos crus recebidos da ECU. Nenhuma suavização, interpolação ou correção é aplicada.
 *
 * CALIBRATION_VAL_1 tem 10 elementos no módulo observado enquanto a UI genérica
 * do ProgBase expõe 12 conceitos. Até o mapeamento 10↔12 ser provado, o app não
 * promove índices desse vetor a limiares de maturidade. Atividade e zonas
 * adquiridas continuam visíveis por estado nativo direto.
 */
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
        val petrolIdleMinUpdateThreshold = fields["VECT_AUTOCAL_U8_1"]?.rawValues()?.firstOrNull()
        val maxAutomatch = (fields["MAX_AUTOMATCH"] ?: fields["VECT_AUTOCAL_U8_2"])
            ?.rawValues()?.firstOrNull()
        val calibration = fields["CALIBRATION_VAL_1"]?.rawValues() ?: intArrayOf()
        val petrolZones = fields["ACQUIRED_ZONES_PETROL"]?.rawValues() ?: intArrayOf()
        val gasZones = fields["ACQUIRED_ZONES_GAS"]?.rawValues() ?: intArrayOf()
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
                val zoneIndex = zone(index)
                val zoneFlag = when (source.fuel) {
                    "GASOLINA" -> petrolZones.getOrNull(zoneIndex)
                    "GNV" -> gasZones.getOrNull(zoneIndex)
                    else -> null
                }
                val activityPresent = timeRaw != null && mapRaw != null && count != null &&
                    (timeRaw != 0 || mapRaw != 0 || count > 0)
                val state = when {
                    timeRaw == null || mapRaw == null || count == null -> "SEM_DADO"
                    source.previous && activityPresent -> { valid++; "ANTERIOR" }
                    activityPresent && zoneFlag == 1 -> { valid++; "ZONA_ADQUIRIDA" }
                    activityPresent -> { collecting++; "ATIVIDADE" }
                    else -> { unknown++; "AGUARDANDO" }
                }
                points.put(JSONObject()
                    .put("index", index)
                    .put("zone", zoneIndex)
                    .put("fuel", source.fuel)
                    .put("timeRaw", timeRaw ?: JSONObject.NULL)
                    .put("timeMs", timeRaw?.let { AutoCalScale.injectionMs(it) } ?: JSONObject.NULL)
                    .put("mapRaw", mapRaw ?: JSONObject.NULL)
                    .put("mapBar", mapRaw?.let { AutoCalScale.mapBar(it) } ?: JSONObject.NULL)
                    .put("counter", count ?: JSONObject.NULL)
                    .put("threshold", JSONObject.NULL)
                    .put("thresholdSemantics", "UNRESOLVED_CALIBRATION_VAL_MAPPING")
                    .put("zoneAcquired", zoneFlag == 1)
                    .put("state", state)
                    .put("draw", activityPresent)
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
                .put("petrolIdleMinUpdate", petrolIdleMinUpdateThreshold ?: JSONObject.NULL)
                .put("petrolLow", JSONObject.NULL)
                .put("petrolNormal", JSONObject.NULL)
                .put("gasLow", JSONObject.NULL)
                .put("gasNormal", JSONObject.NULL)
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("calibrationValues", JSONArray(calibration.toList()))
                .put("calibrationValueMapping", "UNRESOLVED_10_OF_12")
                .put("maturityThresholdsPromoted", false)
                .put("source", "ECU_DUMP_BOUNDED"))
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
