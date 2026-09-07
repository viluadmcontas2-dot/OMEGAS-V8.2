package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueWitnessGnvOnlyTest {
    @Test
    fun `GNV STFT supporting Blue error accelerates confidence`() {
        val result = BlueWitnessConfidence.assess(
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            obdGnvStftPct = 7.0,
            obdQuality = 0.80,
        )

        assertEquals(ObdWitnessState.SUPPORTS, result.state)
        assertEquals(0.68, result.effectiveConfidence, 0.0001)
    }

    @Test
    fun `witness from another GNV region cannot boost comparison`() {
        val witness = JSONObject()
            .put("state", "SUPPORTS")
            .put("gnvStftPct", 7.0)
            .put("quality", 0.90)
            .put("calibrationState", "map-2:curve-3")
            .put("rpm", 3500.0)
            .put("map_bar", 0.85)
            .put("petrol_ms", 8.0)

        val projected = BlueWitnessConfidence.project(
            baseJson = JSONObject().put("correctionMultiplier", 1.08),
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            witness = witness,
            expectedCalibrationState = "map-2:curve-3",
            expectedRpm = 2000.0,
            expectedMapBar = 0.55,
            expectedPetrolOnCngMs = 4.8,
        )

        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.0001)
        assertEquals("INSUFFICIENT", projected.getJSONObject("obdWitness").getString("state"))
        assertEquals(false, projected.getJSONObject("obdWitness").getBoolean("regionMatched"))
        assertEquals(1.08, projected.getDouble("correctionMultiplier"), 0.0)
    }
}
