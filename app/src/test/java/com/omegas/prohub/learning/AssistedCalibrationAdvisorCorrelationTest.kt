package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AssistedCalibrationAdvisorCorrelationTest {
    @Test
    fun `correlated projections from one physical visit do not dilute spread uncertainty`() {
        val comparisons = JSONArray()
            .put(comparison(observedMs = 5.5))
            .put(comparison(observedMs = 6.0))

        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("comparisons", comparisons),
        )
        val point = pointAt(result, 5.0)

        val spread = 0.05
        val expectedUncertainty = sqrt(
            spread * spread +
                0.06 * 0.06 +
                0.03 * 0.03,
        )
        assertEquals(1, point.getInt("uniqueVisits"))
        assertEquals(2.0, point.getDouble("effectiveSamples"), 1e-12)
        assertEquals(expectedUncertainty * 100.0, point.getDouble("uncertaintyPercent"), 1e-9)
    }

    private fun comparison(observedMs: Double): JSONObject = JSONObject()
        .put("visit_id", "physical-cng-window-1")
        .put("petrol_target_ms", 5.0)
        .put("petrol_on_cng_ms", observedMs)
        .put("rpm", 2_200.0)
        .put("map_bar", 0.50)
        .put("quality", 0.5)
        .put(
            "continuous_cell_weights",
            JSONArray().put(
                JSONObject()
                    .put("row", 5)
                    .put("column", 4)
                    .put("weight", 1.0),
            ),
        )

    private fun pointAt(result: JSONObject, petrolMs: Double): JSONObject {
        val points = result.getJSONArray("kFactorSuggestions")
        repeat(points.length()) { index ->
            val point = points.getJSONObject(index)
            if (kotlin.math.abs(point.getDouble("petrolMs") - petrolMs) < 0.000001) return point
        }
        error("Ponto $petrolMs ms não encontrado")
    }
}
