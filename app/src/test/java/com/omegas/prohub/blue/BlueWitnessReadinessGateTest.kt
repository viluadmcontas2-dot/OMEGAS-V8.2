package com.omegas.prohub.blue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueWitnessReadinessGateTest {
    @Test
    fun `insufficient obd witness never boosts mp48 confidence`() {
        val projected = BlueWitnessConfidence.project(
            baseJson = JSONObject(),
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            witness = JSONObject()
                .put("state", "INSUFFICIENT")
                .put("stftMedianPct", 7.0)
                .put("quality", 1.0)
                .put("rpm", 870.0)
                .put("map_bar", 0.44),
            expectedCalibrationState = "curve=1,map=1",
            expectedRpm = 870.0,
            expectedMapBar = 0.44,
            expectedPetrolOnCngMs = 4.86,
        )
        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.000001)
        assertEquals("INSUFFICIENT", projected.getJSONObject("obdWitness").getString("state"))
    }
}
