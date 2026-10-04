package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ritmo da aquisição: recuo do probe e espera do slot do snapshot (revisão de desempenho). */
class PacingRobustnessTest {
    @Test
    fun `probe que falha recua 2 s, 4 s, 8 s ate 30 s e zera no sucesso`() {
        val backoff = ProbeBackoff()
        var now = 1_000L
        val waits = ArrayList<Long>()
        repeat(7) {
            backoff.onFailure(now)
            waits += backoff.untilMs - now
            now = backoff.untilMs
        }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), waits)
        backoff.onSuccess()
        assertFalse(backoff.active(now))
        backoff.onFailure(now)
        assertEquals(2_000L, backoff.untilMs - now)
    }

    @Test
    fun `durante o recuo o stepper devolve IDLE em vez de PROBE e o vivo nao e esfomeado`() {
        var now = 10_000L
        val planner = NativeAutoCalRefreshPlanner()
        val arbiter = SlotArbiter(clock = { now })
        val stepper = SliceStepper(planner, arbiter)
        // sem recuo e sem probe conhecido: pede PROBE
        assertEquals(SliceStepper.Step.PROBE, stepper.decide(now, false, false, true, -1L).step)
        val backoff = ProbeBackoff()
        backoff.onFailure(now)
        repeat(15) { // 100 ms de tique, 1,5 s: nenhum PROBE
            now += 100L
            assertEquals(SliceStepper.Step.IDLE, stepper.decide(now, false, false, true, -1L, backoff.untilMs).step)
        }
        now = backoff.untilMs
        assertEquals(SliceStepper.Step.PROBE, stepper.decide(now, false, false, true, -1L, backoff.untilMs).step)
    }

    @Test
    fun `com vivo de 281 ms a espera de 1200 ms abre o slot e a de 600 ms nao`() {
        fun waitWith(maxWaitMs: Long): Boolean {
            var now = 0L
            val arbiter = SlotArbiter(clock = { now }, liveFrames = { now / 281L })
            assertTrue(arbiter.tryBegin())
            arbiter.end()
            return arbiter.awaitSlot(maxWaitMs, sleep = { ms -> now += ms })
        }
        assertFalse("600 ms só garante ~2 quadros vivos", waitWith(600L))
        assertTrue("1200 ms garante os 3 quadros vivos entre fatias", waitWith(1_200L))
    }
}
