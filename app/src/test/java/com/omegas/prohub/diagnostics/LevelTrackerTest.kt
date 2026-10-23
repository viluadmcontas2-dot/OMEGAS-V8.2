package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Degraus do nível e abastecimento (queda >= 40 contra o último nível conhecido, inclusive entre sessões). Por contagem de quadros, sem relógio. */
class LevelTrackerTest {
    private class Rec { val events = ArrayList<Pair<String, JSONObject>>() ; fun emit(t: String, d: JSONObject) { events += t to d } }

    private fun feed(t: LevelTracker, from: Int, n: Int, level: Int, dither: Boolean = true): Int {
        var e = from
        repeat(n) { i -> t.frame(e.toLong() * 66, if (dither) level + (i % 2) else level); e += 1 }
        return e
    }

    @Test
    fun `primeiro nivel firme da sessao vira level_step INITIAL`() {
        val r = Rec(); val t = LevelTracker(null, r::emit)
        feed(t, 0, 30, 126)
        val e = r.events.single()
        assertEquals("level_step", e.first)
        assertEquals("INITIAL", e.second.getString("kind"))
        assertTrue(e.second.isNull("fromRaw"))
        assertEquals(126, e.second.getInt("toRaw"))
    }

    @Test
    fun `salto de degrau de 125 para 177 emite um level_step com antes e depois e quando`() {
        val r = Rec(); val t = LevelTracker(null, r::emit)
        var n = feed(t, 0, 30, 126)
        n = feed(t, n, 8, 150, dither = false)      // rampa do filtro da ECU: ainda nao firmou
        n = feed(t, n, 30, 177)
        val steps = r.events.filter { it.first == "level_step" && it.second.getString("kind") == "STEP" }
        assertEquals(1, steps.size)
        assertEquals(126, steps[0].second.getInt("fromRaw"))
        assertEquals(177, steps[0].second.getInt("toRaw"))
        assertTrue(steps[0].second.getLong("atElapsedMs") > 0)
        assertTrue(r.events.none { it.first == "refuel_detected" })
    }

    @Test
    fun `dither de 1 ou 2 contagens nao gera degrau`() {
        val r = Rec(); val t = LevelTracker(null, r::emit)
        feed(t, 0, 200, 126)
        assertEquals(1, r.events.size) // so o INITIAL
    }

    @Test
    fun `queda de 40 ou mais dentro da sessao e abastecimento IN_SESSION`() {
        val r = Rec(); val t = LevelTracker(null, r::emit)
        var n = feed(t, 0, 30, 220)
        n = feed(t, n, 30, 60)
        val refuel = r.events.single { it.first == "refuel_detected" }
        assertEquals("IN_SESSION", refuel.second.getString("source"))
        assertEquals(220, refuel.second.getInt("fromRaw"))
        assertEquals(60, refuel.second.getInt("toRaw"))
        assertEquals(160, refuel.second.getInt("drop"))
    }

    @Test
    fun `queda contra o ultimo nivel da sessao anterior e abastecimento BETWEEN_SESSIONS`() {
        val r = Rec(); val t = LevelTracker(240, r::emit)
        feed(t, 0, 30, 50)
        val refuel = r.events.single { it.first == "refuel_detected" }
        assertEquals("BETWEEN_SESSIONS", refuel.second.getString("source"))
        assertEquals(240, refuel.second.getInt("fromRaw"))
    }

    @Test
    fun `queda pequena entre sessoes ou nivel igual nao e abastecimento`() {
        val r = Rec(); val t = LevelTracker(90, r::emit)
        feed(t, 0, 30, 60)
        assertTrue(r.events.none { it.first == "refuel_detected" })
    }

    @Test
    fun `valor sem leitura 19 a 22 e ignorado e nunca vira degrau nem abastecimento`() {
        val r = Rec(); val t = LevelTracker(200, r::emit)
        feed(t, 0, 100, 21, dither = false)
        assertTrue(r.events.isEmpty())
        assertEquals(100L, t.invalidFrames())
        feed(t, 100, 30, 180)
        assertTrue(r.events.none { it.first == "refuel_detected" })
    }

    @Test
    fun `ultimo nivel firme fica disponivel e e desconhecido antes de firmar`() {
        val r = Rec(); val t = LevelTracker(null, r::emit)
        assertEquals(null, t.lastSettled())
        feed(t, 0, 30, 126)
        assertEquals(126, t.lastSettled())
    }
}
