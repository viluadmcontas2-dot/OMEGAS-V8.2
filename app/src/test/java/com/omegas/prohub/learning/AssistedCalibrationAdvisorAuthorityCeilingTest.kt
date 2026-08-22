package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistedCalibrationAdvisorAuthorityCeilingTest {
    @Test
    fun `advisor never floors tiny upstream quality into stronger evidence`() {
        val upstreamQuality = 0.001
        val comparison = JSONObject()
            .put("visit_id", "tiny-authority")
            .put("petrol_target_ms", 5.0)
            .put("petrol_on_cng_ms", 5.5)
            .put("rpm", 2_200.0)
            .put("map_bar", 0.50)
            .put("quality", upstreamQuality)
            .put(
                "continuous_cell_weights",
                JSONArray().put(JSONObject().put("row", 5).put("column", 4).put("weight", 1.0)),
            )

        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("comparisons", JSONArray().put(comparison)),
        )
        val points = result.getJSONArray("kFactorSuggestions")
        var maximumEffectiveWeight = 0.0
        repeat(points.length()) { index ->
            maximumEffectiveWeight = maxOf(
                maximumEffectiveWeight,
                points.getJSONObject(index).optDouble("effectiveWeight", 0.0),
            )
        }

        assertTrue(
            "Advisor must preserve the upstream authority ceiling",
            maximumEffectiveWeight <= upstreamQuality + 1e-12,
        )
    }
}
