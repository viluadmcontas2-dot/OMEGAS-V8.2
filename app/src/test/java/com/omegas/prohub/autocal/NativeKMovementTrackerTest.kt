package com.omegas.prohub.autocal

import org.junit.Assert.*
import org.junit.Test

class NativeKMovementTrackerTest {
    private val q = 16384
    private fun curve(vararg changes: Pair<IntRange, Int>): IntArray {
        val a = IntArray(30) { q }
        for ((range, v) in changes) for (i in range) a[i] = v
        return a
    }

    // Convergindo: a ECU mexe sempre para o mesmo lado -> nenhuma inversão, não é cabo de guerra.
    @Test fun monotoneMovementIsNotOscillation() {
        val t = NativeKMovementTracker()
        var prev = curve()
        for (n in 1..4) {
            val next = IntArray(30) { prev[it] + 200 }
            t.observe(n, prev, next); prev = next
        }
        val s = t.summary()
        assertEquals(4, s.events)
        assertEquals(0, s.reversalPoints)
        assertFalse(s.oscillating)
    }

    // Vai e volta: +, −, + nos mesmos pontos -> cabo de guerra medido.
    @Test fun upDownUpIsOscillation() {
        val t = NativeKMovementTracker()
        val a = curve(); val b = IntArray(30) { q + 1600 }; val c = IntArray(30) { q - 800 }; val d = IntArray(30) { q + 1200 }
        t.observe(1, a, b); t.observe(2, b, c)
        assertFalse("com 2 AutoMatch ainda não dá para dizer", t.summary().oscillating)
        val s = t.observe(3, c, d)
        assertTrue(s.oscillating)
        assertEquals(30, s.reversalPoints)
        assertEquals(1.0, s.reversalShare, 1e-9)
        assertEquals(3, s.lastAutoMatchCount)
        assertTrue(s.maxAbsPercent > 100.0 * 1200 / (q - 800) - 1.0)
    }

    // Poucos pontos invertendo não bastam (ruído de arredondamento de 1-2 pontos).
    @Test fun fewReversingPointsAreNotEnough() {
        val t = NativeKMovementTracker()
        val a = curve(); val b = IntArray(30) { q + 100 }
        val c = IntArray(30) { if (it < 2) q - 100 else q + 200 }
        val d = IntArray(30) { if (it < 2) q + 100 else q + 300 }
        t.observe(1, a, b); t.observe(2, b, c); t.observe(3, c, d)
        val s = t.summary()
        assertEquals(2, s.reversalPoints)
        assertFalse(s.oscillating)
    }

    @Test fun resetClearsHistoryAndBadShapeIsIgnored() {
        val t = NativeKMovementTracker()
        t.observe(1, curve(), IntArray(30) { q + 10 })
        t.observe(2, IntArray(5), IntArray(5)) // forma inválida: ignorada, sem exceção
        assertEquals(1, t.summary().events)
        t.reset()
        assertEquals(0, t.summary().events)
        assertNull(t.summary().lastAutoMatchCount)
    }
}
