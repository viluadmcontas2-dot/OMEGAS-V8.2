package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueWitnessProjectionTest {
    @Test
    fun `supporting witness boosts only effective confidence and preserves correction target`() {
        val base = JSONObject()
            .put("errorPercent", 8.0)
            .put("correctionMultiplier", 1.125)
            .put("targetK", 137)
            .put("quality", 0.60)
        val witness = JSONObject()
            .put("state", "SUPPORTS")
            .put("residualPp", 7.0)
            .put("quality", 0.80)
            .put("calibrationState", "map-2:curve-3")

        val projected = BlueWitnessConfidence.project(
            baseJson = base,
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            witness = witness,
            expectedCalibrationState = "map-2:curve-3",
        )

        assertEquals(1.125, projected.getDouble("correctionMultiplier"), 0.000001)
        assertEquals(137, projected.getInt("targetK"))
        assertEquals(8.0, projected.getDouble("errorPercent"), 0.000001)
        assertEquals(0.60, projected.getDouble("baseConfidence"), 0.000001)
        assertEquals(0.68, projected.getDouble("effectiveConfidence"), 0.000001)
        assertEquals(ObdWitnessState.SUPPORTS.name, projected.getJSONObject("obdWitness").getString("state"))
    }

    @Test
    fun `stale calibration witness is unavailable and cannot boost confidence`() {
        val base = JSONObject()
            .put("correctionMultiplier", 1.125)
            .put("targetK", 137)
        val witness = JSONObject()
            .put("state", "SUPPORTS")
            .put("residualPp", 7.0)
            .put("quality", 1.0)
            .put("calibrationState", "map-1:curve-3")

        val projected = BlueWitnessConfidence.project(
            baseJson = base,
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            witness = witness,
            expectedCalibrationState = "map-2:curve-3",
        )

        assertEquals(1.125, projected.getDouble("correctionMultiplier"), 0.000001)
        assertEquals(137, projected.getInt("targetK"))
        assertEquals(0.60, projected.getDouble("effectiveConfidence"), 0.000001)
        assertEquals(ObdWitnessState.UNAVAILABLE.name, projected.getJSONObject("obdWitness").getString("state"))
    }
}
