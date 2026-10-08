package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StageTimerTest {
    private var clockNs = 0L
    private fun timer() = StageTimer { clockNs }

    @Test
    fun `mede cada etapa e aponta a mais demorada`() {
        val t = timer()
        clockNs += 3_000_000; t.mark("store")
        clockNs += 150_000_000; t.mark("equivalencia")
        clockNs += 2_000_000; t.mark("sessao")
        assertEquals(155L, t.totalMs())
        assertEquals(150L, t.stageMs("equivalencia"))
        assertEquals("equivalencia" to 150L, t.worst())
        val json = t.toJson()
        assertEquals("equivalencia", json.getString("worst"))
        assertEquals(150.0, json.getJSONObject("stagesMs").getDouble("equivalencia"), 0.001)
    }

    @Test
    fun `sem etapa marcada nao inventa nada`() {
        val t = timer()
        clockNs += 5_000_000
        assertEquals(0L, t.totalMs())
        assertNull(t.worst())
        assertNotNull(t.toJson())
    }

    @Test
    fun `etapas demais nao estouram e continuam somando no total`() {
        val t = timer()
        repeat(StageTimer.MAX_STAGES + 5) { clockNs += 1_000_000; t.mark("e$it") }
        assertEquals((StageTimer.MAX_STAGES + 5).toLong(), t.totalMs())
    }
}
