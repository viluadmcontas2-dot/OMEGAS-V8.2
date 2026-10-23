package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinIntervalGateTest {
    @Test
    fun `no maximo um por segundo`() {
        val gate = MinIntervalGate(1_000L)
        assertTrue(gate.allow(10_000L))
        assertFalse(gate.allow(10_100L))
        assertFalse(gate.allow(10_999L))
        assertTrue(gate.allow(11_000L))
        val passed = (0 until 50).count { gate.allow(11_000L + it * 100L) }
        assertEquals(4, passed) // 11.0 já usado; passam 12.0, 13.0, 14.0, 15.0 (só até 15.9)
    }

    @Test
    fun `primeiro evento sempre passa e reset libera de novo`() {
        val gate = MinIntervalGate(1_000L)
        assertTrue(gate.allow(0L))
        assertFalse(gate.allow(500L))
        gate.reset()
        assertTrue(gate.allow(600L))
    }

    @Test
    fun `relogio voltando para tras nao trava para sempre`() {
        val gate = MinIntervalGate(1_000L)
        assertTrue(gate.allow(50_000L))
        assertTrue(gate.allow(1_000L))
        assertFalse(gate.allow(1_500L))
    }
}
