package com.omegas.prohub.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousInterpolation3DTest {

    @Test
    fun `trilinear weights partition unity and blend smoothly across RPM, petrolMs, and MAP`() {
        val mapBins = doubleArrayOf(0.20, 0.40, 0.60, 0.80, 1.00)

        // Evaluate continuous weights across control points
        val weightsNearControl = ContinuousLearningMath.trilinearWeights(
            rpm = 2_500.0,
            petrolMs = 3.5,
            mapBar = 0.60,
            mapBins = mapBins,
        )

        // Sum of weights must equal 1.0 everywhere (partition of unity)
        assertEquals(1.0, weightsNearControl.sumOf { it.weight }, 1e-6)

        // Mid-point continuous coordinate: 2750 RPM, 4.0ms, 0.50 bar
        val weightsMid = ContinuousLearningMath.trilinearWeights(
            rpm = 2_750.0,
            petrolMs = 4.0,
            mapBar = 0.50,
            mapBins = mapBins,
        )

        assertEquals(1.0, weightsMid.sumOf { it.weight }, 1e-6)
        assertEquals(8, weightsMid.size)
        weightsMid.forEach {
            assertEquals(0.125, it.weight, 1e-6)
        }
    }

    @Test
    fun `continuous 3D interpolation avoids discrete step jumps across cell boundaries`() {
        val mapBins = doubleArrayOf(0.20, 0.40, 0.60, 0.80, 1.00)
        val getValue: (Int, Int, Int) -> Double = { r, c, m -> r * 10.0 + c * 2.0 + m * 5.0 }

        // Sweep RPM continuously from 2450.0 to 2550.0 across boundary in steps of 1 RPM
        var previousVal = ContinuousLearningMath.interpolate3D(2450.0, 3.5, 0.60, getValue, mapBins)
        var maxStepDiff = 0.0

        for (rpmInt in 2451..2550) {
            val rpm = rpmInt.toDouble()
            val currentVal = ContinuousLearningMath.interpolate3D(rpm, 3.5, 0.60, getValue, mapBins)
            val stepDiff = Math.abs(currentVal - previousVal)
            if (stepDiff > maxStepDiff) {
                maxStepDiff = stepDiff
            }
            previousVal = currentVal
        }

        // With continuous interpolation, max step diff for 1 RPM change must be tiny (< 0.05), proving NO discrete step jumps!
        assertTrue("Max step diff should be continuous ($maxStepDiff < 0.05)", maxStepDiff < 0.05)
    }

    @Test
    fun `continuous 3D interpolation sweeps MAP continuously without discrete step jumps`() {
        val mapBins = doubleArrayOf(0.20, 0.40, 0.60, 0.80, 1.00)
        val getValue: (Int, Int, Int) -> Double = { r, c, m -> r * 1.5 + c * 0.8 + m * 12.0 }

        // Sweep MAP continuously from 0.35 bar to 0.45 bar in steps of 0.001 bar
        var previousVal = ContinuousLearningMath.interpolate3D(2500.0, 3.5, 0.35, getValue, mapBins)
        var maxStepDiff = 0.0

        var mapBar = 0.351
        while (mapBar <= 0.450) {
            val currentVal = ContinuousLearningMath.interpolate3D(2500.0, 3.5, mapBar, getValue, mapBins)
            val stepDiff = Math.abs(currentVal - previousVal)
            if (stepDiff > maxStepDiff) {
                maxStepDiff = stepDiff
            }
            previousVal = currentVal
            mapBar += 0.001
        }

        // Max step diff for 0.001 bar MAP change must be tiny (< 0.1), proving smooth continuous MAP interpolation
        assertTrue("Max step diff across MAP boundary should be smooth ($maxStepDiff < 0.1)", maxStepDiff < 0.1)
    }

    @Test
    fun `LearningGridProjection cellFor includes continuous 3D weights`() {
        val cell = LearningGridProjection.cellFor(rpm = 2_750.0, petrolMs = 4.0, mapBar = 0.50)
        assertTrue(cell.has("trilinearWeights"))
        val trilinear = cell.getJSONArray("trilinearWeights")
        assertTrue(trilinear.length() > 0)

        val enriched = LearningGridProjection.enrichRegion(
            JSONObject()
                .put("rpm", 2_750.0)
                .put("petrol_ms", 4.0)
                .put("map_bar", 0.50),
        )
        assertTrue(enriched.getJSONObject("cell").has("trilinearWeights"))
    }
}

