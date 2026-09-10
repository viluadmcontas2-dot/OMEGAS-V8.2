package com.omegas.prohub.blue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueWitnessGnvOnlyTest {
    @Test
    fun `GNV STFT supporting MP48 error accelerates confidence`() {
        val result = BlueWitnessConfidence.assess(8.0, 0.60, 7.0, 0.80)
        assertEquals(0.68, result.effectiveConfidence, 0.0001)
    }

    @Test
    fun `distant independent OBD region cannot boost MP48 comparison`() {
        val projected = BlueWitnessConfidence.project(
            baseJson = JSONObject().put("correctionMultiplier", 1.08),
            blueErrorPercent = 8.0, baseQuality = 0.60,
            witness = JSONObject().put("state", "READY").put("stftMedianPct", 7.0)
                .put("quality", 0.90).put("rpm", 3500.0).put("map_bar", 0.85),
            expectedCalibrationState = "ignored", expectedRpm = 2000.0,
            expectedMapBar = 0.55, expectedPetrolOnCngMs = 4.8,
        )
        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.0)
        assertEquals("INSUFFICIENT", projected.getJSONObject("obdWitness").getString("state"))
    }
}
