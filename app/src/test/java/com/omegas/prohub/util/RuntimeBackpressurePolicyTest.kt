package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBackpressurePolicyTest {
    @Test
    fun `acquisition nunca depende de fila secundaria`() {
        val rule = RuntimeBackpressurePolicy.rule(RuntimeWorkLane.ACQUISITION)
        assertEquals(OverflowStrategy.BYPASS_SECONDARY_QUEUE, rule.strategy)
        assertEquals(0, rule.capacity)
    }

    @Test
    fun `visual e latest only`() {
        val rule = RuntimeBackpressurePolicy.rule(RuntimeWorkLane.VISUAL)
        assertEquals(1, rule.capacity)
        assertEquals(OverflowStrategy.OVERWRITE_LATEST, rule.strategy)
    }

    @Test
    fun `science e secondary possuem hard bounds e overflow explicito`() {
        listOf(RuntimeWorkLane.SCIENTIFIC_LEDGER, RuntimeWorkLane.SECONDARY_READ).forEach { lane ->
            val rule = RuntimeBackpressurePolicy.rule(lane)
            assertTrue(rule.capacity > 0)
            assertEquals(OverflowStrategy.REJECT_AND_COUNT, rule.strategy)
        }
    }

    @Test
    fun `manual write e safety nunca sao descartados silenciosamente`() {
        listOf(RuntimeWorkLane.MANUAL_WRITE, RuntimeWorkLane.SAFETY).forEach { lane ->
            assertEquals(OverflowStrategy.RESERVED_BOUNDED_WAIT, RuntimeBackpressurePolicy.rule(lane).strategy)
        }
    }
}
