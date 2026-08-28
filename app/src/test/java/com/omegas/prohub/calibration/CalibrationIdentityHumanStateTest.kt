package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CalibrationIdentityHumanStateTest {
    @Test
    fun `estados tecnicos viram mensagens humanas deterministicas`() {
        assertEquals("Pronto", CalibrationIdentityHumanState.project(CalibrationCompleteness.KNOWN, CalibrationFreshness.CURRENT_SESSION).label)
        assertEquals("Dados incompletos", CalibrationIdentityHumanState.project(CalibrationCompleteness.PARTIAL, CalibrationFreshness.CURRENT_SESSION).label)
        assertEquals("Revalidar calibração", CalibrationIdentityHumanState.project(CalibrationCompleteness.UNKNOWN, CalibrationFreshness.UNKNOWN).label)
        assertEquals("Calibração mudou", CalibrationIdentityHumanState.project(CalibrationCompleteness.KNOWN, CalibrationFreshness.STALE).label)
    }

    @Test
    fun `stale tem precedencia sobre known`() {
        val state = CalibrationIdentityHumanState.project(CalibrationCompleteness.KNOWN, CalibrationFreshness.STALE)
        assertEquals("CALIBRATION_CHANGED", state.reasonCode)
        assertFalse(state.actionable)
    }

    @Test
    fun `somente known current e actionable`() {
        assertEquals(true, CalibrationIdentityHumanState.project(CalibrationCompleteness.KNOWN, CalibrationFreshness.CURRENT_SESSION).actionable)
        assertEquals(false, CalibrationIdentityHumanState.project(CalibrationCompleteness.PARTIAL, CalibrationFreshness.CURRENT_SESSION).actionable)
    }
}
