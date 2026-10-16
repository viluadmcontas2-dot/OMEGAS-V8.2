package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prior dos nós baixos (0,5–3 ms): levanta o que a ECU deixa afundado, nunca baixa, e não mexe no resto. */
class LowNodePriorTest {
    private val axisMs = (1..20).map { it * 0.5 } + listOf(11, 12, 13, 14, 15, 16, 17, 18, 20, 22).map { it.toDouble() }
    private val axisRaw = axisMs.map { (it * 512).toInt() }.toIntArray()

    private fun refine(lowFactor: Double, mid: Double = 1.30): AutoMatchRefinedEngine.Result {
        val k = IntArray(30) { j -> ((if (axisMs[j] <= 3.0) lowFactor else mid) * 16384).toInt() }
        return AutoMatchRefinedEngine.refine(
            AutoMatchRefinedEngine.Input(axisRaw, k, null, null, null, null, null, null),
        )
    }

    @Test
    fun `no baixo afundado e levantado perto dos vizinhos e o resto fica`() {
        val r = refine(lowFactor = 0.95)
        for (j in 0 until 30) if (axisMs[j] <= 3.0) {
            val f = r.refinedRaw[j] / 16384.0
            assertTrue("no $j (${axisMs[j]} ms) = $f", f in 1.20..1.31)
        }
        for (j in 0 until 30) if (axisMs[j] >= 5.0) assertTrue("no $j mudou", kotlin.math.abs(r.currentRaw[j] - r.refinedRaw[j]) <= 50)
    }

    @Test
    fun `curva sem afundamento fica inalterada`() {
        val r = refine(lowFactor = 1.30)
        assertEquals(r.currentRaw, r.refinedRaw)
    }

    @Test
    fun `no baixo acima da referencia nao e baixado`() {
        val r = refine(lowFactor = 1.45)
        for (j in 0 until 30) if (axisMs[j] <= 3.0) assertTrue("no $j", r.refinedRaw[j] >= r.currentRaw[j] * 0.95)
    }
}
