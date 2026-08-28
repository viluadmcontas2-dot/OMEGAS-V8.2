package com.omegas.prohub.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestOnlyStateTest {
    data class Item(val sequence: Long, val session: Long)

    @Test
    fun `mantem somente o item mais novo da geracao corrente`() {
        val state = LatestOnlyState<Item>(sequenceOf = { it.sequence }, generationOf = { it.session })
        state.beginGeneration(77L)
        assertTrue(state.publish(Item(1, 77)))
        assertTrue(state.publish(Item(2, 77)))
        assertEquals(Item(2, 77), state.current())
        assertEquals(2L, state.metrics().published)
        assertEquals(1L, state.metrics().replaced)
    }

    @Test
    fun `sessao errada ou sequence regressiva nao substituem current`() {
        val state = LatestOnlyState<Item>({ it.sequence }, { it.session })
        state.beginGeneration(77L)
        state.publish(Item(5, 77))
        assertFalse(state.publish(Item(6, 78)))
        assertFalse(state.publish(Item(4, 77)))
        assertEquals(Item(5, 77), state.current())
        assertEquals(2L, state.metrics().rejected)
    }

    @Test
    fun `troca de geracao limpa o slot imediatamente`() {
        val state = LatestOnlyState<Item>({ it.sequence }, { it.session })
        state.beginGeneration(77L)
        state.publish(Item(1, 77))
        state.beginGeneration(78L)
        assertNull(state.current())
        assertEquals(78L, state.metrics().generation)
    }
}
