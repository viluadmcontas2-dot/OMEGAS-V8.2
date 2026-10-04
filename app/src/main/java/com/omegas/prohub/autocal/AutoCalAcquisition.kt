package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pontos crus recebidos da ECU. Nenhuma suavização, interpolação ou correção é aplicada.
 *
 * O DUMP do ProgBase canônico fecha a seleção dos limiares operacionais:
 * gasolina bandas 0..5 -> VECT_AUTOCAL_U8_1; gasolina 6..17 ->
 * CALIBRATION_VAL_1[2]; GNV 0..5 -> CALIBRATION_VAL_1[5]; GNV 6..17 ->
 * CALIBRATION_VAL_1[8]. A sequência de fronteiras 5/9/13 também está embutida
 * no executável original. Estes índices escolhem valores lidos da ECU; o host
 * não inventa limiares nem reproduz a aritmética interna do AutoMatch.
 *
 * O DUMP expõe buffers GAS_PREV de tempo/MAP, mas não um contador
 * NUM_BUF_UPD_GAS_PREV. Por isso GNV_ANTERIOR nunca reutiliza o contador atual
 * como se pertencesse ao ciclo arquivado.
 */
object AutoCalAcquisition {
    private const val LOW_BAND_MAX_INDEX = 5

    private data class Source(
        val fuel: String,
        val time: String,
        val map: String,
        val count: String?,
        val previous: Boolean = false,
    )
    private val sources = listOf(
        Source("GASOLINA", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR"),
        Source("GNV", "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS"),
        Source("GNV_ANTERIOR", "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV", null, previous = true),
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
        val petrolNormalThreshold = calibration.getOrNull(2)
        val gasLowThreshold = calibration.getOrNull(5)
        val gasNormalThreshold = calibration.getOrNull(8)
        val maturityThresholdsAvailable =
            petrolIdleMinUpdateThreshold != null &&
                petrolNormalThreshold != null &&
                gasLowThreshold != null &&
                gasNormalThreshold != null
        val petrolZones = fields["ACQUIRED_ZONES_PETROL"]?.rawValues() ?: intArrayOf()
        val gasZones = fields["ACQUIRED_ZONES_GAS"]?.rawValues() ?: intArrayOf()
        val points = JSONArray()
        var valid = 0
        var collecting = 0
        var unknown = 0
        sources.forEach { source ->
            val times = fields[source.time]?.rawValues() ?: intArrayOf()
            val maps = fields[source.map]?.rawValues() ?: intArrayOf()
            val counts = source.count?.let { fields[it]?.rawValues() } ?: intArrayOf()
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
                val threshold = when {
                    source.previous -> null
                    source.fuel == "GASOLINA" && index <= LOW_BAND_MAX_INDEX ->
                        petrolIdleMinUpdateThreshold
                    source.fuel == "GASOLINA" -> petrolNormalThreshold
                    source.fuel == "GNV" && index <= LOW_BAND_MAX_INDEX -> gasLowThreshold
                    source.fuel == "GNV" -> gasNormalThreshold
                    else -> null
                }
                val thresholdSemantics = when {
                    source.previous -> "HISTORICAL_BUFFER_NO_MATURITY_GATE"
                    threshold != null -> "PROGBASE_DUMP_RUNTIME_SELECTOR"
                    else -> "RUNTIME_THRESHOLD_UNAVAILABLE"
                }
                val activityPresent = when {
                    timeRaw == null || mapRaw == null -> false
                    source.previous -> timeRaw != 0 || mapRaw != 0
                    count == null -> false
                    else -> timeRaw != 0 || mapRaw != 0 || count > 0
                }
                val state = when {
                    timeRaw == null || mapRaw == null -> "SEM_DADO"
                    source.previous && activityPresent -> { valid++; "ANTERIOR" }
                    source.previous -> { unknown++; "AGUARDANDO" }
                    count == null -> "SEM_DADO"
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
                    .put("threshold", threshold ?: JSONObject.NULL)
                    .put("thresholdSemantics", thresholdSemantics)
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
            // Flags de zona como a ECU entregou (nulo = o campo não veio): a verdade da ECU, sem interpretação.
            .put("zoneFlags", JSONObject()
                .put("petrol", if (petrolZones.isEmpty()) JSONObject.NULL else JSONArray(petrolZones.take(4).map { it > 0 }))
                .put("gas", if (gasZones.isEmpty()) JSONObject.NULL else JSONArray(gasZones.take(4).map { it > 0 })))
            .put("validCount", valid)
            .put("collectingCount", collecting)
            .put("unknownCount", unknown)
            .put("thresholds", JSONObject()
                .put("petrolIdleMinUpdate", petrolIdleMinUpdateThreshold ?: JSONObject.NULL)
                .put("petrolLow", petrolIdleMinUpdateThreshold ?: JSONObject.NULL)
                .put("petrolNormal", petrolNormalThreshold ?: JSONObject.NULL)
                .put("gasLow", gasLowThreshold ?: JSONObject.NULL)
                .put("gasNormal", gasNormalThreshold ?: JSONObject.NULL)
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("calibrationValues", JSONArray(calibration.toList()))
                .put("calibrationValueMapping", "PROGBASE_DUMP_GRID_PROVEN")
                .put("runtimeSelectorBoundaryInclusive", LOW_BAND_MAX_INDEX)
                .put("zoneBoundariesInclusive", JSONArray(listOf(5, 9, 13)))
                .put("maturityThresholdsPromoted", maturityThresholdsAvailable)
                .put("source", "ECU_VALUES_PROGBASE_DUMP_SELECTOR"))
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
