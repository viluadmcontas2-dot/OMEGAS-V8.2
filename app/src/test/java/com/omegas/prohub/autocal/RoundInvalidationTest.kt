package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leituras do round feitas antes de uma gravação K / ação AutoCal não valem depois dela (defeitos P2-1/P2-2).
 * O monitor depende de SystemClock, então a lógica pura (RoundScratch, WriteFence, planejador) é testada aqui.
 */
class RoundInvalidationTest {
    @Test
    fun `invalidate zera pendente, retidos e contadores de gasolina`() {
        val scratch = RoundScratch<String, Int>()
        scratch.pending = "G4"
        scratch.petrolCounters = intArrayOf(1, 2, 3)
        scratch.hold += 5
        scratch.hold += 7
        scratch.invalidate()
        assertNull(scratch.pending)
        assertNull(scratch.petrolCounters)
        assertTrue(scratch.hold.isEmpty())
    }

    @Test
    fun `geracao de escrita detecta leitura que atravessou uma gravacao`() {
        val fence = WriteFence()
        val sample = fence.current()
        assertFalse(fence.changedSince(sample))
        fence.bump()
        assertTrue("snapshot fatiado atravessou a escrita: descartar", fence.changedSince(sample))
        assertFalse(fence.changedSince(fence.current()))
    }

    @Test
    fun `invalidar o round abandona o round e pede referencia ja`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(1_000L)
        // abre um round de referência vencido
        val first = planner.nextGroup(1_000L + 600_000L, acquisitionEnabled = true)
        assertTrue(first != null)
        assertTrue(planner.roundInProgress())
        // o que o monitor faz em invalidateRound():
        planner.abandonRound()
        planner.requestReferenceNow()
        assertFalse(planner.roundInProgress())
        assertTrue("referência pedida já", planner.due(1_001L).reference)
    }
}
