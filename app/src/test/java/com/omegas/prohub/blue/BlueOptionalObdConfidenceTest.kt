package com.omegas.prohub.blue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueOptionalObdConfidenceTest {
    @Test
    fun `agreeing OBD may boost MP48 confidence without changing its math`() {
        val projected = projection(stft = 7.0)
        assertTrue(projected.getDouble("effectiveConfidence") > 0.60)
        assertEquals(8.0, projected.getDouble("errorPercent"), 0.0)
        assertEquals(1.08, projected.getDouble("correctionMultiplier"), 0.0)
    }

    @Test
    fun `conflicting OBD never blocks an otherwise valid MP48 proposal`() {
        val projected = projection(stft = -7.0)
        assertTrue(projected.getBoolean("available"))
        assertEquals("PROPOSAL_READY", projected.getString("state"))
        assertEquals(8.0, projected.getDouble("errorPercent"), 0.0)
        assertEquals(1.08, projected.getDouble("correctionMultiplier"), 0.0)
        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.0)
    }

    private fun projection(stft: Double): JSONObject = BlueWitnessConfidence.project(
        baseJson = JSONObject()
            .put("available", true)
            .put("state", "PROPOSAL_READY")
            .put("errorPercent", 8.0)
            .put("correctionMultiplier", 1.08),
        blueErrorPercent = 8.0,
        baseQuality = 0.60,
        witness = JSONObject()
            .put("state", "READY")
            .put("stftMedianPct", stft)
            .put("gnvStftPct", stft)
            .put("quality", 0.90)
            .put("rpm", 2_000.0)
            .put("map_bar", 0.55),
        expectedCalibrationState = "",
        expectedRpm = 2_000.0,
        expectedMapBar = 0.55,
        expectedPetrolOnCngMs = 4.8,
    )
}
