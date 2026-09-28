package com.omegas.prohub.autocal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalRefreshPlannerTest {
    @Test
    fun `full snapshot anchors one and four second cadences` {
        val planner = NativeAutoCalRefreshPlanner()
        planner.reset()
        planner.markFullSnapshot(10_000L)

        assertFalse(planner.due(10_999L).acquisition)
        assertFalse(planner.due(10_999L).reference)

        val atOneSecond = planner.due(11_000L)
        assertTrue(atOneSecond.acquisition)
        assertFalse(atOneSecond.reference)

        planner.markAcquisition(11_000L)
        assertFalse(planner.due(11_999L).acquisition)

        val atTwoSeconds = planner.due(12_000L)
        assertTrue(atTwoSeconds.acquisition)
        assertFalse(atTwoSeconds.reference)

        planner.markAcquisition(13_000L)
        val atFourSeconds = planner.due(14_000L)
        assertTrue(atFourSeconds.acquisition)
        assertTrue(atFourSeconds.reference)
    }

    @Test
    fun `failed refresh is naturally retried until caller marks success`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)

        assertTrue(planner.due(21_000L).acquisition)
        assertTrue(planner.due(21_500L).acquisition)

        planner.markAcquisition(21_500L)
        assertFalse(planner.due(22_499L).acquisition)
        assertTrue(planner.due(22_500L).acquisition)
    }
}
