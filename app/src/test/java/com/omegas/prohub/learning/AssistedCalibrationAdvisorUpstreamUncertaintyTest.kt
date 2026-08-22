package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistedCalibrationAdvisorUpstreamUncertaintyTest {
    @Test
    fun `bounded upstream uncertainty is not penalized again by legacy Advisor prior`() {
        val comparison = JSONObject()
            .put("origin", "BOUNDED_EQUIVALENCE_SURFACE")
            .put("visit_id", "cng-visit-1")
            .put("petrol_target_ms", 5.0)
            .put("petrol_on_cng_ms", 5.4)
            .put("rpm", 2_200.0)
            .put("map_bar", 0.50)
            .put("quality", 1.0)
            .put("upstream_uncertainty_fraction", 0.03)
            .put(
                "continuous_cell_weights",
                JSONArray().put(
                    JSONObject()
                        .put("row", 5)
                        .put("column", 4)
                        .put("weight", 1.0),
                ),
            )

        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("comparisons", JSONArray().put(comparison)),
        )
        val point = pointAt(result, 5.0)

        assertEquals(3.0, point.getDouble("uncertaintyPercent"), 1e-9)
        assertTrue(point.getBoolean("actionable"))
    }

    private fun pointAt(result: JSONObject, petrolMs: Double): JSONObject {
        val points = result.getJSONArray("kFactorSuggestions")
        repeat(points.length()) { index ->
            val point = points.getJSONObject(index)
            if (kotlin.math.abs(point.getDouble("petrolMs") - petrolMs) < 0.000001) return point
        }
        error("Ponto $petrolMs ms não encontrado")
    }
}
