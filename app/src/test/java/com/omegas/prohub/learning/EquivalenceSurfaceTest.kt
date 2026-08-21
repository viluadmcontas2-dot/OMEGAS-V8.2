package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalenceSurfaceTest {
    @Test
    fun `bilinear update conserves scientific weight and touches at most four points`() {
        val surface = testSurface()

        val update = surface.observe(
            lane = FuelLane.PETROL_REFERENCE,
            rpm = 2_480.0,
            mapBar = 0.51,
            petrolTinjMs = 3.0,
            weight = 0.8,
            materialRevision = 1L,
        )

        assertEquals(0.8, surface.debugTotalWeight(FuelLane.PETROL_REFERENCE), 1e-12)
        assertTrue(update.touchedNodes in 1..4)
    }

    @Test
    fun `cng evidence remains represented before petrol reference exists`() {
        val surface = testSurface()
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_500.0, 0.50, 3.30, 0.4, 1L)

        val result = surface.query(2_500.0, 0.50)

        assertTrue(result.cng != null)
        assertNull(result.petrol)
        assertEquals(3.30, result.cng!!.meanTinjMs, 1e-9)
    }

    @Test
    fun `fuel lanes never contaminate each other`() {
        val surface = testSurface()
        surface.observe(FuelLane.PETROL_REFERENCE, 2_500.0, 0.50, 3.00, 1.0, 1L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_500.0, 0.50, 3.30, 1.0, 2L)

        val result = surface.query(2_500.0, 0.50)

        assertEquals(3.00, result.petrol!!.meanTinjMs, 1e-9)
        assertEquals(3.30, result.cng!!.meanTinjMs, 1e-9)
    }

    @Test
    fun `weighted node moments expose mean variance and effective support`() {
        val surface = testSurface()
        surface.observe(FuelLane.PETROL_REFERENCE, 2_400.0, 0.50, 3.0, 1.0, 1L)
        surface.observe(FuelLane.PETROL_REFERENCE, 2_400.0, 0.50, 5.0, 1.0, 2L)

        val result = surface.query(2_400.0, 0.50).petrol!!

        assertEquals(4.0, result.meanTinjMs, 1e-9)
        assertEquals(1.0, result.varianceMs2, 1e-9)
        assertEquals(2.0, result.effectiveSupport, 1e-9)
    }

    @Test
    fun `query recovers nearby support with a fixed sixteen node ceiling`() {
        val surface = testSurface()
        // 2360 is an exact lattice node. Querying 2480 puts it 1.5 cells away,
        // outside the old four-corner lookup but inside the approved local radius.
        surface.observe(FuelLane.PETROL_REFERENCE, 2_360.0, 0.50, 3.0, 1.0, 1L)

        val nearby = surface.query(2_480.0, 0.50).petrol

        assertTrue(nearby != null)
        assertEquals(3.0, nearby!!.meanTinjMs, 1e-9)
        assertTrue(nearby.nearestSupportDistanceCells <= 1.5)
        assertEquals(16, surface.debugMaximumQueryNodes())
    }

    @Test
    fun `state size is fixed regardless of observation count`() {
        val surface = testSurface()
        val before = surface.debugAllocatedScalarCount()

        repeat(100_000) { index ->
            surface.observe(
                lane = if (index % 2 == 0) FuelLane.PETROL_REFERENCE else FuelLane.CNG_PETROL_OBSERVED,
                rpm = 2_000.0 + (index % 10) * 20.0,
                mapBar = 0.40 + (index % 5) * 0.01,
                petrolTinjMs = 3.0 + (index % 3) * 0.1,
                weight = 0.5,
                materialRevision = index.toLong(),
            )
        }

        assertEquals(before, surface.debugAllocatedScalarCount())
    }

    @Test
    fun `zero novelty weight does not mutate scientific state`() {
        val surface = testSurface()
        val update = surface.observe(
            FuelLane.PETROL_REFERENCE,
            rpm = 2_500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.0,
            weight = 0.0,
            materialRevision = 1L,
        )

        assertEquals(0, update.touchedNodes)
        assertEquals(0.0, surface.debugTotalWeight(FuelLane.PETROL_REFERENCE), 0.0)
    }

    private fun testSurface() = EquivalenceSurface(
        EquivalenceSurface.Config(
            minRpm = 1_000.0,
            maxRpm = 4_000.0,
            rpmStep = 80.0,
            minMapBar = 0.20,
            maxMapBar = 1.20,
            mapStepBar = 0.02,
        ),
    )
}
