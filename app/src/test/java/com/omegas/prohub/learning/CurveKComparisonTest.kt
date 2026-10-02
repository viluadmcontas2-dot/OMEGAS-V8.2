package com.omegas.prohub.learning

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveKComparisonTest {
    @Test
    fun `unsupported points are explicit sem previsao and never invent target`() {
        val comparison = CurveKComparison.build(ecuCurve(1.10), learning(JSONArray()))
        val point = comparison.getJSONArray("points").getJSONObject(7)

        assertEquals("SEM_PREVISAO", point.getString("state"))
        assertEquals(1.10, point.getDouble("ecuCurrentFactor"), 0.001)
        assertTrue(point.isNull("omegasTargetFactor"))
        assertFalse(point.getBoolean("requiresHumanReview"))
        assertFalse(point.getBoolean("automaticWrite"))
    }

    @Test
    fun `actionable global evidence stays separate from confirmed ecu value`() {
        val advice = JSONArray().put(JSONObject()
            .put("index", 7)
            .put("petrolMs", 4.0)
            .put("suggestedDeltaPercent", 10.0)
            .put("errorPercent", 12.0)
            .put("uncertaintyPercent", 1.0)
            .put("confidence", 0.84)
            .put("confidenceStage", "CONFIRMED")
            .put("readiness", "AVAILABLE")
            .put("actionable", true))
        val point = CurveKComparison.build(ecuCurve(1.10), learning(advice))
            .getJSONArray("points").getJSONObject(7)

        assertEquals("PREVISAO_OMEGAS", point.getString("state"))
        assertEquals("ECU_CONFIRMED_K_FACTOR", point.getString("sourceCurrent"))
        assertEquals("OMEGAS_GLOBAL_ADVISOR", point.getString("sourcePrediction"))
        assertEquals(1.10, point.getDouble("ecuCurrentFactor"), 0.001)
        assertTrue(point.getDouble("omegasTargetFactor") > 1.10)
        assertTrue(point.getBoolean("requiresHumanReview"))
    }

    @Test
    fun `observed but not actionable remains observed without target`() {
        val advice = JSONArray().put(JSONObject()
            .put("index", 2)
            .put("petrolMs", 2.2)
            .put("errorPercent", 1.0)
            .put("confidence", 0.30)
            .put("readiness", "OBSERVING")
            .put("actionable", false))
        val point = CurveKComparison.build(ecuCurve(1.0), learning(advice))
            .getJSONArray("points").getJSONObject(2)

        assertEquals("OBSERVADO_SEM_PREVISAO", point.getString("state"))
        assertTrue(point.isNull("omegasTargetFactor"))
    }

    private fun ecuCurve(factor: Double): JSONObject {
        val points = JSONArray()
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("petrolMs", 1.5 + index * 0.35)
                .put("factor", factor))
        }
        return JSONObject().put("points", points)
    }

    private fun learning(advice: JSONArray): JSONObject = JSONObject()
        .put("assistedCalibration", JSONObject().put("kFactorSuggestions", advice))
}
