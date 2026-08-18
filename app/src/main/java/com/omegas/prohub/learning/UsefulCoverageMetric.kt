package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cobertura científica por tempo: conta regiões físicas com suporte/contexto
 * utilizável. Nunca usa número bruto de frames como cobertura.
 */
internal object UsefulCoverageMetric {
    private const val MILLIS_PER_MINUTE = 60_000.0

    fun fromLearningExport(
        exported: JSONObject,
        elapsedMsOverride: Long? = null,
    ): JSONObject {
        val regions = exported.optJSONArray("regions") ?: JSONArray()
        val useful = mutableListOf<String>()
        repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            if (isUseful(region)) useful += region.optString("id", "region-$index")
        }

        val elapsedMs = (elapsedMsOverride
            ?: exported.optJSONObject("session_summary")?.optLong("duration_ms", 0L)
            ?: 0L).coerceAtLeast(0L)
        val rate = if (elapsedMs <= 0L) 0.0 else useful.size * MILLIS_PER_MINUTE / elapsedMs.toDouble()
        return JSONObject()
            .put("basis", "SUPPORTED_CONTEXT_VALID_REGIONS")
            .put("raw_region_count", regions.length())
            .put("useful_region_count", useful.size)
            .put("useful_region_ids", JSONArray(useful))
            .put("ignored_region_count", regions.length() - useful.size)
            .put("elapsed_ms", elapsedMs)
            .put("useful_regions_per_minute", rate)
            .put("raw_frame_count_used", false)
            .put("reason", if (useful.isEmpty()) "NO_VALID_SUPPORTED_REGION" else "USEFUL_COVERAGE_AVAILABLE")
    }

    private fun isUseful(region: JSONObject): Boolean {
        val samples = region.optInt("samples", 0)
        val visits = region.optInt("visit_count", 0)
        val rpm = region.optDouble("rpm", Double.NaN)
        val map = region.optDouble("map_bar", Double.NaN)
        val petrol = region.optDouble("petrol_ms", Double.NaN)
        val water = region.optDouble("water_c", Double.NaN)
        val pressure = region.optDouble("pressure_diff_bar", Double.NaN)
        val confidence = region.optDouble("confidence", Double.NaN)
        return samples > 0 && visits > 0 &&
            rpm.isFinite() && rpm >= 0.0 &&
            map.isFinite() && map >= 0.0 &&
            petrol.isFinite() && petrol > 0.05 &&
            water.isFinite() && pressure.isFinite() &&
            confidence.isFinite() && confidence > 0.0
    }
}

/** API de produto fora do hot path; consumidores podem consultar sob demanda. */
internal fun SignalLearningStore.usefulCoverageMetric(elapsedMsOverride: Long? = null): JSONObject =
    UsefulCoverageMetric.fromLearningExport(export("USEFUL_COVERAGE_METRIC"), elapsedMsOverride)
