package com.omegas.prohub.physics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEvidenceDependenciesTest {
    @Test
    fun `new K2 evidence stales only dependent rules`() {
        val stale = PhysicsEvidenceDependencies.invalidate(setOf(PhysicsEvidenceId.K2_PRESSURE))
        assertTrue(PhysicsRule.K2_INTERPRETATION in stale)
        assertTrue(PhysicsRule.ENVIRONMENTAL_EXPLANATION in stale)
        assertFalse(PhysicsRule.K1_COMPONENT in stale)
        assertFalse(PhysicsRule.DEADTIME_ACTIVE_PULSE in stale)
    }

    @Test
    fun `new deadtime evidence stales deadtime dependent target but not static oracle decoding`() {
        val stale = PhysicsEvidenceDependencies.invalidate(setOf(PhysicsEvidenceId.GAS_DEADTIME))
        assertTrue(PhysicsRule.DEADTIME_ACTIVE_PULSE in stale)
        assertTrue(PhysicsRule.KSTAR_WITH_ACTIVE_PULSE in stale)
        assertFalse(PhysicsRule.K2_INTERPRETATION in stale)
        assertFalse(PhysicsRule.K4_INTERPRETATION in stale)
    }
}
