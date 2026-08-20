package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantIdentificationProtocolTest {
    @Test
    fun `plant identification remains a deferred final gate protocol`() {
        val protocol = PlantIdentificationProtocol.default()
        assertEquals("FINAL_PHYSICAL_GATE_ONLY", protocol.executionGate)
        assertEquals("HOLD", protocol.replayStatus)
        assertTrue(protocol.requiresPreWriteSnapshot)
        assertTrue(protocol.requiresHumanConfirmation)
        assertTrue(protocol.requiresAckAndReadback)
        assertTrue(protocol.requiresPostWriteRevalidation)
        assertFalse(protocol.mayExecuteInPhase6)
    }

    @Test
    fun `gain authority promotes only after paired intervention outcome`() {
        assertEquals(
            MagnitudeAuthority.POLICY_ONLY,
            PlantIdentificationProtocol.gainAuthority(hasIntervention = false, hasRevalidation = false),
        )
        assertEquals(
            MagnitudeAuthority.POLICY_ONLY,
            PlantIdentificationProtocol.gainAuthority(hasIntervention = true, hasRevalidation = false),
        )
        assertEquals(
            MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            PlantIdentificationProtocol.gainAuthority(hasIntervention = true, hasRevalidation = true),
        )
    }
}
