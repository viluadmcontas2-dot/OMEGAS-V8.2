package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdPidCycleTest {
    @Test
    fun `complete bounded 010C 010B 0106 cycle decodes physical values`() {
        val cycle = ObdPidCycle.decode(
            rpmBytes = listOf(0x1F, 0x40),
            mapBytes = listOf(55),
            stftBytes = listOf(141),
            startedAtMs = 1_000L,
            endedAtMs = 1_420L,
        )!!

        assertEquals(2_000.0, cycle.rpm, 0.001)
        assertEquals(0.55, cycle.mapBar, 0.001)
        assertEquals(10.15625, cycle.stftPct, 0.000001)
        assertEquals(420L, cycle.acquisitionSpanMs)
        assertTrue(cycle.complete)
    }

    @Test
    fun `missing PID or stale cycle is never data ready`() {
        assertNull(ObdPidCycle.decode(listOf(0x1F, 0x40), listOf(55), null, 1_000L, 1_200L))
        assertNull(ObdPidCycle.decode(listOf(0x1F, 0x40), listOf(55), listOf(141), 1_000L, 1_751L))
    }
}
