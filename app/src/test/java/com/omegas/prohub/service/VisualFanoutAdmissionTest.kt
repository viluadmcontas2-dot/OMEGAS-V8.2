package com.omegas.prohub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFanoutAdmissionTest {
    @Test
    fun `admits first overlay projection and throttles before work`() {
        val gate = VisualFanoutAdmission(250L)

        assertTrue(gate.tryAcquire(1_000L))
        assertFalse(gate.tryAcquire(1_100L))
        assertTrue(gate.tryAcquire(1_250L))
    }

    @Test
    fun `force bypasses cadence without corrupting the next window`() {
        val gate = VisualFanoutAdmission(250L)

        assertTrue(gate.tryAcquire(2_000L))
        assertTrue(gate.tryAcquire(2_020L, force = true))
        assertFalse(gate.tryAcquire(2_200L))
        assertTrue(gate.tryAcquire(2_270L))
    }

    @Test
    fun `backwards clock sample fails open once and restarts cadence`() {
        val gate = VisualFanoutAdmission(250L)

        assertTrue(gate.tryAcquire(10_000L))
        assertTrue(gate.tryAcquire(9_000L))
        assertFalse(gate.tryAcquire(9_100L))
        assertTrue(gate.tryAcquire(9_250L))
    }
}
