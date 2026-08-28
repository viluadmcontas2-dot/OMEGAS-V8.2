package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural/runtime contract for the TayTech-safe equivalence hot path.
 * These assertions intentionally test bounded work rather than wall-clock timing,
 * which is noisy on host hardware and cannot stand in for RK3326 evidence.
 */
class EquivalencePerformanceContractTest {
    private fun surface() = EquivalenceSurface(
        EquivalenceSurface.Config(
            minRpm = 0.0,
            maxRpm = 9_000.0,
            rpmStep = 80.0,
            minMapBar = 0.0,
            maxMapBar = 2.5,
            mapStepBar = 0.02,
        ),
    )

    @Test
    fun `one observation touches no more than four lattice nodes`() {
        val surface = surface()
        val result = surface.observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2_473.0,
            mapBar = 0.536,
            petrolTinjMs = 3.18,
            weight = 0.37,
            materialRevision = 1L,
        )

        assertTrue(result.touchedNodes in 1..4)
        assertEquals(0.37, result.acceptedWeight, 1e-12)
    }

    @Test
    fun `query work has a fixed sixteen node ceiling`() {
        val surface = surface()
        assertEquals(16, surface.debugMaximumQueryNodes())
    }

    @Test
    fun `scientific state allocation is independent of drive duration`() {
        val surface = surface()
        val allocated = surface.debugAllocatedScalarCount()

        repeat(100_000) { index ->
            surface.observe(
                lane = if (index and 1 == 0) FuelLane.PETROL_REFERENCE else FuelLane.CNG_PETROL_OBSERVED,
                rpm = 900.0 + (index % 7_000),
                mapBar = 0.20 + (index % 180) * 0.01,
                petrolTinjMs = 2.0 + (index % 400) * 0.01,
                weight = 0.20,
                materialRevision = index.toLong(),
            )
        }

        assertEquals(allocated, surface.debugAllocatedScalarCount())
    }

    @Test
    fun `duplicate scientific window performs zero surface mutation`() {
        val runtime = EquivalenceRuntime(surface = surface())
        val before = runtime.totalWeight(FuelLane.CNG_PETROL_OBSERVED)

        val duplicate = runtime.observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2_500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.30,
            stability = 0.75,
            novelty = 0.0,
            materialRevision = 99L,
        )

        assertEquals(0.0, duplicate.scientificWeight, 0.0)
        assertEquals(0, duplicate.touchedNodes)
        assertEquals(before, runtime.totalWeight(FuelLane.CNG_PETROL_OBSERVED), 0.0)
    }
}
