package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvisorRevisionGateTest {
    @Test
    fun `same scientific token does not create another revision`() {
        val gate = AdvisorRevisionGate()
        assertEquals(1L, gate.revise("CMP:a:1"))
        assertNull(gate.revise("CMP:a:1"))
        assertEquals(2L, gate.revise("CMP:a:2"))
    }

    @Test
    fun `observation milestones grow logarithmically instead of per frame`() {
        assertEquals(1, AdvisorRevisionGate.observationMilestone(1))
        assertEquals(2, AdvisorRevisionGate.observationMilestone(2))
        assertEquals(2, AdvisorRevisionGate.observationMilestone(3))
        assertEquals(4, AdvisorRevisionGate.observationMilestone(7))
        assertEquals(8, AdvisorRevisionGate.observationMilestone(8))
    }

    @Test
    fun `force advances revision for merge or calibration even without token`() {
        val gate = AdvisorRevisionGate()
        assertEquals(1L, gate.force())
        assertEquals(2L, gate.force())
    }
}
