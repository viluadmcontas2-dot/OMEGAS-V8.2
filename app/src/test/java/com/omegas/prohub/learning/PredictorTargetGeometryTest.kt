package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTargetGeometryTest {
    @Test
    fun `runtime axes project equilibrium reference into petrol rows and rpm columns`() {
        val calibration = binding()
        val result = PredictorTargetGeometry.project(
            calibration = calibration,
            expectedGeometryFingerprint = calibration.geometryFingerprint,
            rpm = 2500.0,
            petrolReferenceMs = 4.0,
        )

        assertTrue(result.available)
        assertEquals(PredictorEquilibriumCoordinate(2500.0, 4.0), result.coordinate)
        assertEquals(1.0, result.weights.sumOf { it.weight }, 1e-12)
        assertTrue(result.weights.all { it.row in 0..11 && it.column in 0..11 })
        assertEquals(calibration.geometryFingerprint, result.geometryFingerprint)
    }

    @Test
    fun `current petrol on gas cannot move equilibrium projection`() {
        val calibration = binding()
        val first = PredictorTargetGeometry.project(
            calibration,
            calibration.geometryFingerprint,
            rpm = 2500.0,
            petrolReferenceMs = 4.0,
        )
        val second = PredictorTargetGeometry.project(
            calibration,
            calibration.geometryFingerprint,
            rpm = 2500.0,
            petrolReferenceMs = 4.0,
        )

        val arbitraryCurrentPetrolOnGasA = 3.2
        val arbitraryCurrentPetrolOnGasB = 7.8
        assertTrue(arbitraryCurrentPetrolOnGasA != arbitraryCurrentPetrolOnGasB)
        assertEquals(first, second)
    }

    @Test
    fun `changing petrol reference moves time axis projection`() {
        val calibration = binding()
        val lower = PredictorTargetGeometry.project(calibration, calibration.geometryFingerprint, 2500.0, 3.1)
        val upper = PredictorTargetGeometry.project(calibration, calibration.geometryFingerprint, 2500.0, 6.7)

        assertTrue(lower.available)
        assertTrue(upper.available)
        assertTrue(lower.weights != upper.weights)
    }

    @Test
    fun `geometry fingerprint mismatch fails closed`() {
        val result = PredictorTargetGeometry.project(binding(), "geometry-B", 2500.0, 4.0)

        assertFalse(result.available)
        assertEquals("GEOMETRY_MISMATCH", result.reason)
        assertTrue(result.weights.isEmpty())
    }

    @Test
    fun `unknown geometry fails closed`() {
        val calibration = binding().copy(petrolAxisMs = emptyList(), rpmAxis = emptyList())
        val result = PredictorTargetGeometry.project(
            calibration,
            calibration.geometryFingerprint,
            2500.0,
            4.0,
        )

        assertFalse(result.available)
        assertEquals("GEOMETRY_UNKNOWN", result.reason)
        assertTrue(result.weights.isEmpty())
    }

    @Test
    fun `non monotonic runtime axes fail closed`() {
        val calibration = binding().copy(
            petrolAxisMs = binding().petrolAxisMs.toMutableList().also { it[5] = it[4] },
        )
        val result = PredictorTargetGeometry.project(
            calibration,
            calibration.geometryFingerprint,
            2500.0,
            4.0,
        )

        assertFalse(result.available)
        assertEquals("GEOMETRY_INVALID", result.reason)
        assertTrue(result.weights.isEmpty())
    }

    @Test
    fun `invalid target coordinate fails closed`() {
        val calibration = binding()
        val result = PredictorTargetGeometry.project(
            calibration,
            calibration.geometryFingerprint,
            Double.NaN,
            4.0,
        )

        assertFalse(result.available)
        assertEquals("INVALID_TARGET_COORDINATE", result.reason)
        assertTrue(result.weights.isEmpty())
    }

    private fun binding(): LearningCalibrationBinding = LearningCalibrationBinding(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = 7,
        geometryFingerprint = "geometry-A",
        usbSessionId = 21L,
        mapHash = "map-A",
        petrolAxisMs = listOf(2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0),
        rpmAxis = listOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500),
    )
}
