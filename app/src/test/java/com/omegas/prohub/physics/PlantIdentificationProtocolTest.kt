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
    fun `gain authority promotes only after informative paired intervention outcome`() {
        assertEquals(
            MagnitudeAuthority.POLICY_ONLY,
            PlantIdentificationProtocol.gainAuthority(
                hasIntervention = false,
                hasRevalidation = false,
                beforeLogError = null,
                afterLogError = null,
                appliedLogFactorDelta = null,
            ),
        )
        assertEquals(
            MagnitudeAuthority.POLICY_ONLY,
            PlantIdentificationProtocol.gainAuthority(
                hasIntervention = true,
                hasRevalidation = true,
                beforeLogError = 0.10,
                afterLogError = 0.08,
                appliedLogFactorDelta = 0.0,
            ),
        )
        assertEquals(
            MagnitudeAuthority.POLICY_ONLY,
            PlantIdentificationProtocol.gainAuthority(
                hasIntervention = true,
                hasRevalidation = true,
                beforeLogError = 0.10,
                afterLogError = 0.15,
                appliedLogFactorDelta = 0.08,
            ),
        )
        assertEquals(
            MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            PlantIdentificationProtocol.gainAuthority(
                hasIntervention = true,
                hasRevalidation = true,
                beforeLogError = 0.10,
                afterLogError = 0.02,
                appliedLogFactorDelta = 0.08,
            ),
        )
    }
}
