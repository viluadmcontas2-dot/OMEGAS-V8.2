package com.omegas.prohub.blue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueWitnessProjectionTest {
    @Test
    fun `supporting OBD boosts only confidence and preserves MP48 target`() {
        val projected = BlueWitnessConfidence.project(
            baseJson = JSONObject().put("available", true).put("state", "PROPOSAL_READY")
                .put("errorPercent", 8.0).put("correctionMultiplier", 1.125).put("targetK", 137),
            blueErrorPercent = 8.0, baseQuality = 0.60,
            witness = JSONObject().put("state", "READY").put("stftMedianPct", 7.0)
                .put("quality", 0.80).put("rpm", 2000.0).put("map_bar", 0.55),
            expectedCalibrationState = "ignored", expectedRpm = 2000.0,
            expectedMapBar = 0.55, expectedPetrolOnCngMs = 4.8,
        )
        assertEquals(1.125, projected.getDouble("correctionMultiplier"), 0.0)
        assertEquals(137, projected.getInt("targetK"))
        assertEquals(0.68, projected.getDouble("effectiveConfidence"), 0.0001)
    }

    @Test
    fun `conflicting OBD is diagnostic and cannot block MP48`() {
        val projected = BlueWitnessConfidence.project(
            baseJson = JSONObject().put("available", true).put("state", "PROPOSAL_READY")
                .put("correctionMultiplier", 1.125).put("targetK", 137),
            blueErrorPercent = 8.0, baseQuality = 0.60,
            witness = JSONObject().put("state", "READY").put("stftMedianPct", -7.0)
                .put("quality", 1.0).put("rpm", 2000.0).put("map_bar", 0.55),
            expectedCalibrationState = "ignored", expectedRpm = 2000.0,
            expectedMapBar = 0.55, expectedPetrolOnCngMs = 4.8,
        )
        assertTrue(projected.getBoolean("available"))
        assertEquals("PROPOSAL_READY", projected.getString("state"))
        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.0)
        assertEquals("CONFLICTS", projected.getJSONObject("obdWitness").getString("state"))
    }
}
