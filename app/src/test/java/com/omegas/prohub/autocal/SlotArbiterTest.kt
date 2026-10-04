package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotArbiterTest {
    private var now = 0L
    private var frames = 0L

    private fun arbiter(known: Boolean = true) = SlotArbiter(
        clock = { now },
        liveFrames = { if (known) frames else -1L },
    )

    @Test
    fun `only one group per slot and three live frames before the next one`() {
        val arbiter = arbiter()
        assertTrue(arbiter.tryBegin())
        assertFalse("slot ocupado: nenhum segundo grupo", arbiter.tryBegin())
        now += 150
        arbiter.end()
        frames += 2
        now += 90
        assertFalse("2 quadros vivos ainda não bastam", arbiter.tryBegin())
        frames += 1
        assertTrue("3 quadros vivos liberam o próximo grupo", arbiter.tryBegin())
        assertEquals(2L, arbiter.json().getLong("slotsGranted"))
        assertEquals(2L, arbiter.json().getLong("deferrals"))
    }

    @Test
    fun `without a live frame counter 150 ms of live polling opens the slot`() {
        val arbiter = arbiter(known = false)
        assertTrue(arbiter.tryBegin())
        arbiter.end()
        now += 149
        assertFalse(arbiter.tryBegin())
        now += 1
        assertTrue(arbiter.tryBegin())
    }

    @Test
    fun `a silent live poll never deadlocks the acquisition`() {
        val arbiter = arbiter()
        assertTrue(arbiter.tryBegin())
        arbiter.end()
        now += SlotArbiter.MAX_DEFER_MS - 1
        assertFalse(arbiter.tryBegin())
        now += 1
        assertTrue(arbiter.tryBegin())
    }

    @Test
    fun `awaitSlot sleeps in small steps until the slot opens or the wait expires`() {
        val arbiter = arbiter()
        assertTrue(arbiter.tryBegin())
        arbiter.end()
        val slept = ArrayList<Long>()
        val opened = arbiter.awaitSlot(maxWaitMs = 500L, sleep = { ms ->
            slept += ms
            now += ms
            if (slept.size == 5) frames += 3 // o ciclo vivo entregou 3 quadros durante a espera
        })
        assertTrue(opened)
        assertEquals(5, slept.size)

        arbiter.end()
        val timedOut = arbiter.awaitSlot(maxWaitMs = 50L, sleep = { ms -> now += ms })
        assertFalse("sem quadros vivos nem tempo suficiente: desiste no prazo", timedOut)
    }

    @Test
    fun `duty counters report per kind age duration and bus share`() {
        val duty = AcquisitionDuty { now }
        duty.record("G2", startedAtMs = 1_000L, durationMs = 150L, ok = true)
        duty.record("G3", startedAtMs = 1_500L, durationMs = 100L, ok = false, error = "timeout")
        now = 2_000L
        val json = duty.json(liveFrameAgeMs = 40L, liveFrameCount = 123L)
        assertEquals(40L, json.getLong("liveFrameAgeMs"))
        assertEquals(123L, json.getLong("liveFrameCount"))
        assertEquals(250L, json.getLong("busyMs"))
        assertEquals(150L, json.getLong("maxGroupMs"))
        assertEquals(850L, json.getJSONObject("groups").getJSONObject("G2").getLong("ageMs"))
        assertEquals(1L, json.getJSONObject("groups").getJSONObject("G3").getLong("failures"))
        assertEquals(0.25, json.getDouble("busShare"), 0.001)
        assertEquals("timeout", json.getString("lastError"))
    }
}
