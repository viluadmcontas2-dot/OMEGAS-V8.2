package com.omegas.prohub.autocal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalRefreshPlannerTest {
    @Test
    fun `full snapshot anchors two and four second cadences`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.reset()
        planner.markFullSnapshot(10_000L)

        assertFalse(planner.due(11_999L).acquisition)
        assertFalse(planner.due(11_999L).reference)

        val atTwoSeconds = planner.due(12_000L)
        assertTrue(atTwoSeconds.acquisition)
        assertFalse(atTwoSeconds.reference)

        planner.markAcquisition(12_000L)
        assertFalse(planner.due(13_999L).acquisition)

        val atFourSeconds = planner.due(14_000L)
        assertTrue(atFourSeconds.acquisition)
        assertTrue(atFourSeconds.reference)
    }

    @Test
    fun `failed refresh is naturally retried until caller marks success`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)

        assertTrue(planner.due(22_000L).acquisition)
        assertTrue(planner.due(22_500L).acquisition)

        planner.markAcquisition(22_500L)
        assertFalse(planner.due(24_499L).acquisition)
        assertTrue(planner.due(24_500L).acquisition)
    }
}
