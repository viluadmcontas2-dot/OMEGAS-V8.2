package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class AssistedCalibrationAdvisorAuthorityCeilingTest {
    @Test
    fun `advisor never floors tiny upstream quality into stronger evidence`() {
        val upstreamQuality = 0.001
        val comparison = comparison(upstreamQuality, observedMs = 5.5)

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

    @Test
    fun `large residual cannot manufacture high confidence from tiny scientific mass`() {
        val upstreamQuality = 0.001
        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("comparisons", JSONArray().put(comparison(upstreamQuality, observedMs = 10.0))),
        )
        val points = result.getJSONArray("kFactorSuggestions")
        var maximumConfidence = 0.0
        repeat(points.length()) { index ->
            maximumConfidence = maxOf(
                maximumConfidence,
                points.getJSONObject(index).optDouble("confidence", 0.0),
            )
        }
        val evidenceAuthority = 1.0 - exp(-sqrt(upstreamQuality))

        assertTrue(
            "Signal magnitude may improve utility, but cannot create confidence beyond evidence authority",
            maximumConfidence <= evidenceAuthority + 1e-12,
        )
    }

    private fun comparison(upstreamQuality: Double, observedMs: Double): JSONObject = JSONObject()
        .put("visit_id", "tiny-authority")
        .put("petrol_target_ms", 5.0)
        .put("petrol_on_cng_ms", observedMs)
        .put("rpm", 2_200.0)
        .put("map_bar", 0.50)
        .put("quality", upstreamQuality)
        .put(
            "continuous_cell_weights",
            JSONArray().put(JSONObject().put("row", 5).put("column", 4).put("weight", 1.0)),
        )
}
