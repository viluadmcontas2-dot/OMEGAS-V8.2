package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalenceEvidenceWeightTest {
    @Test
    fun `legacy threshold is continuous instead of binary`() {
        val below = EquivalenceEvidenceWeight.from(
            diagnostics(rpmCenterShift = 99.0, rpmCenterLimit = 100.0),
        ).stability
        val above = EquivalenceEvidenceWeight.from(
            diagnostics(rpmCenterShift = 101.0, rpmCenterLimit = 100.0),
        ).stability

        assertTrue(kotlin.math.abs(below - above) < 0.02)
        assertTrue(below > 0.0)
        assertTrue(above > 0.0)
    }

    @Test
    fun `weight is one when primary signals are stable`() {
        assertEquals(1.0, EquivalenceEvidenceWeight.from(diagnostics()).stability, 1e-12)
    }

    @Test
    fun `two times legacy reference keeps twenty percent scientific weight`() {
        val result = EquivalenceEvidenceWeight.from(
            diagnostics(rpmCenterShift = 200.0, rpmCenterLimit = 100.0),
        )

        assertEquals(0.2, result.stability, 1e-12)
        assertEquals("rpm_shift", result.limitingSignal)
    }

    @Test
    fun `pressure never changes primary equivalence weight`() {
        val quiet = EquivalenceEvidenceWeight.from(
            diagnostics(pressureCenterShift = 0.0, pressureOscillation = 0.0),
        ).stability
        val noisy = EquivalenceEvidenceWeight.from(
            diagnostics(pressureCenterShift = 99.0, pressureOscillation = 99.0),
        ).stability

        assertEquals(quiet, noisy, 1e-12)
    }

    @Test
    fun `water temperature never changes primary equivalence weight`() {
        val warm = EquivalenceEvidenceWeight.from(diagnostics(waterCenterC = 90.0)).stability
        val cold = EquivalenceEvidenceWeight.from(diagnostics(waterCenterC = 20.0)).stability

        assertEquals(warm, cold, 1e-12)
    }

    private fun diagnostics(
        waterCenterC: Double = 80.0,
        rpmCenterShift: Double = 0.0,
        rpmCenterLimit: Double = 100.0,
        rpmOscillation: Double = 0.0,
        rpmOscillationLimit: Double = 100.0,
        mapCenterShift: Double = 0.0,
        mapCenterLimit: Double = 0.03,
        mapOscillation: Double = 0.0,
        mapOscillationLimit: Double = 0.03,
        petrolCenterShift: Double = 0.0,
        petrolCenterLimit: Double = 0.25,
        petrolOscillationRatio: Double = 0.0,
        petrolOscillationLimit: Double = 0.10,
        pressureCenterShift: Double = 0.0,
        pressureOscillation: Double = 0.0,
    ) = SampleDiagnostics(
        frameCount = 10,
        durationMs = 450L,
        medianIntervalMs = 50L,
        waterCenterC = waterCenterC,
        minimumWaterC = 60,
        rpmCenterShift = rpmCenterShift,
        rpmCenterLimit = rpmCenterLimit,
        rpmOscillation = rpmOscillation,
        rpmOscillationLimit = rpmOscillationLimit,
        mapCenterShift = mapCenterShift,
        mapCenterLimit = mapCenterLimit,
        mapOscillation = mapOscillation,
        mapOscillationLimit = mapOscillationLimit,
        petrolCenterShift = petrolCenterShift,
        petrolCenterLimit = petrolCenterLimit,
        petrolOscillationRatio = petrolOscillationRatio,
        petrolOscillationLimit = petrolOscillationLimit,
        pressureCenterShift = pressureCenterShift,
        pressureCenterLimit = 0.10,
        pressureOscillation = pressureOscillation,
        pressureOscillationLimit = 0.10,
    )
}
