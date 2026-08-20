package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Derived, read-only evidence dimensions for science/UI consumers.
 *
 * No new acceptance threshold is introduced here. The projection only gives
 * explicit names to observables that already exist in the learning authority:
 * quality = precision of the accepted physical window(s), weight = effective
 * evidence mass after novelty/dwell weighting, and visit_count = independent
 * physical returns to the region.
 */
object LearningEvidenceDimensions {
    fun enrichRegions(source: JSONObject): JSONObject {
        val copy = JSONObject(source.toString())
        val regions = copy.optJSONArray("regions") ?: return copy
        val enriched = JSONArray()
        repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            enriched.put(enrichRegion(region))
        }
        copy.put("regions", enriched)
        return copy
    }

    fun enrichRegion(region: JSONObject): JSONObject {
        val copy = JSONObject(region.toString())
        val retainedVisits = copy.optJSONArray("visits")?.length() ?: 0
        val independentVisits = max(copy.optInt("visit_count", retainedVisits), retainedVisits)
        val rawSamples = copy.optDouble("samples", 0.0).coerceAtLeast(0.0)
        val effectiveMass = copy.optDouble("weight", rawSamples).coerceAtLeast(0.0)
        val precision = copy.optDouble("quality", 0.0).coerceIn(0.0, 1.0)
        return copy
            .put("precision_within_visit", precision)
            .put("effective_evidence_mass", effectiveMass)
            .put("independent_visits", independentVisits)
            .put("evidence_dimensions_policy", "EXISTING_OBSERVABLES_NO_NEW_THRESHOLD")
    }
}
