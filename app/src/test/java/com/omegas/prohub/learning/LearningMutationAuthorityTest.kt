package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningMutationAuthorityTest {
    private fun binding(session: Long = 77L) = LearningCalibrationBinding(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = 3,
        geometryFingerprint = "geometry-A",
        usbSessionId = session,
        mapHash = "map-A",
        petrolAxisMs = List(12) { (it + 1).toDouble() },
        rpmAxis = List(12) { 500 + it * 250 },
    )

    @Test
    fun `quarantine converts otherwise eligible decision into diagnostic only`() {
        LearningMutationAuthority.endPhysicalSession()
        LearningMutationAuthority.beginManualWrite(77L, "MAP_K manual write")
        val original = SampleDecision.transition(
            state = "SAMPLE_ACCEPTED_FIXTURE",
            reason = "fixture",
            learningEligible = true,
            reasonCode = "FIXTURE_ELIGIBLE",
        )
        val gated = LearningMutationAuthority.gate(original)
        assertEquals(LearningMutationState.QUARANTINED_MUTATION_WINDOW.name, gated.state)
        assertEquals(LearningMutationState.QUARANTINED_MUTATION_WINDOW.name, gated.reasonCode)
        assertFalse(gated.learningEligible)
        assertNull(gated.sample)
        assertTrue(gated.plannedOperation)
        LearningMutationAuthority.endPhysicalSession()
    }

    @Test
    fun `partial failure stays unknown and blocks active science`() {
        LearningMutationAuthority.endPhysicalSession()
        LearningMutationAuthority.beginManualWrite(77L, "K_FACTOR")
        LearningMutationAuthority.markUnknown(77L, "readback mismatch")
        val state = LearningMutationAuthority.current()
        assertEquals(LearningMutationState.UNKNOWN, state.state)
        assertTrue(state.blocksActiveScience)
        assertTrue(state.toJson().getBoolean("telemetry_continues"))
        LearningMutationAuthority.endPhysicalSession()
    }

    @Test
    fun `fresh calibration identity after mutation opens post write revalidation`() {
        LearningMutationAuthority.endPhysicalSession()
        LearningMutationAuthority.beginManualWrite(77L, "MAP_K")
        LearningMutationAuthority.onCalibrationIdentityKnown(binding())
        val state = LearningMutationAuthority.current()
        assertEquals(LearningMutationState.POST_WRITE_REVALIDATING, state.state)
        assertFalse(state.blocksActiveScience)
        assertEquals("calibration-A", state.calibrationFingerprint)
        assertEquals(3, state.calibrationGeneration)
        LearningMutationAuthority.endPhysicalSession()
    }

    @Test
    fun `identity from different usb session cannot release quarantine`() {
        LearningMutationAuthority.endPhysicalSession()
        LearningMutationAuthority.beginManualWrite(77L, "MAP_K")
        LearningMutationAuthority.onCalibrationIdentityKnown(binding(session = 78L))
        assertEquals(LearningMutationState.QUARANTINED_MUTATION_WINDOW, LearningMutationAuthority.current().state)
        LearningMutationAuthority.endPhysicalSession()
    }
}
