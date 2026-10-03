package com.omegas.prohub.autocal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalRefreshPlannerTest {
    @Test
    fun `full snapshot anchors one and four second cadences`() {
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
    fun `failed refresh backs off exponentially instead of retrying every tick`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)
        assertTrue(planner.due(21_000L).acquisition)

        planner.markAcquisitionFailure(21_000L)
        assertFalse("1ª falha: espera 2 s", planner.due(22_999L).acquisition)
        assertTrue(planner.due(23_000L).acquisition)

        planner.markAcquisitionFailure(23_000L)
        assertFalse("2ª falha: espera 4 s", planner.due(26_999L).acquisition)
        assertTrue(planner.due(27_000L).acquisition)

        // Muitas falhas seguidas: o recuo para no teto de 30 s, nunca cresce sem limite.
        var now = 27_000L
        repeat(10) { planner.markAcquisitionFailure(now); now += 1L }
        assertFalse(planner.due(now + 29_000L).acquisition)
        assertTrue(planner.due(now + NativeAutoCalRefreshPlanner.BACKOFF_CAP_MS).acquisition)

        // Sucesso zera o recuo e volta à cadência de 1 s.
        planner.markAcquisition(100_000L)
        assertFalse(planner.due(100_999L).acquisition)
        assertTrue(planner.due(101_000L).acquisition)
    }

    @Test
    fun `reference failure backs off from four seconds and a full snapshot clears it`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)
        planner.markReferenceFailure(24_000L)
        assertFalse(planner.due(31_999L).reference)
        assertTrue(planner.due(32_000L).reference)
        planner.markFullSnapshot(40_000L)
        assertFalse(planner.due(43_999L).reference)
        assertTrue(planner.due(44_000L).reference)
    }
}
