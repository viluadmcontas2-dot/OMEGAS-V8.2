package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.EvidenceTestSupport.interior
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.equivalence.EquivalenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * Trava da baixa (lenta real ≈ 4,5 ms; limiar 5,0 ms) entra como LIMITE na caixa da proposta, antes da trava de
 * coerência: a curva entregue nunca empobrece abaixo de 5,0 ms E continua sem degrau (|Δ ln K/Δ ln t| ≤ limite).
 */
class LowGuardInBoxTest {
    private val axisRaw = IntArray(30) { (KFactorProtocol.OBSERVED_PETROL_AXIS_MS[it] * 512.0).toInt() }

    private fun richEverywhere(): AutoMatchRefinedEngine.Result {
        // Condução pedindo K 15% menor em todas as faixas (3–12 ms): a proposta quer empobrecer tudo.
        val pairs = (0..4).flatMap { b -> interior(b, 12).map { it to it * 0.86 } }
        return AutoMatchRefinedEngine.refine(
            AutoMatchRefinedEngine.Input(axisRaw, IntArray(30) { 16384 }, null, null, null, null, null, null, pairs, null, emptyList(), 0.0),
        )
    }

    @Test
    fun `limiar da trava e 5 ms`() {
        assertEquals(5.0, AutoMatchRefinedEngine.LOW_GUARD_MS, 0.0)
        assertEquals(AutoMatchRefinedEngine.LOW_GUARD_MS, AutoMatchSnapshotAnalysis.LOW_GUARD_MS, 0.0)
    }

    @Test
    fun `motor nunca empobrece abaixo de 5 ms e a curva final nao tem degrau`() {
        val r = richEverywhere()
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.mode)
        assertTrue("a proposta empobrece acima da trava", (0 until 30).any { r.axisMs[it] >= 6.0 && r.refinedRaw[it] < r.currentRaw[it] })
        for (j in 0 until 30) if (r.axisMs[j] < AutoMatchRefinedEngine.LOW_GUARD_MS) {
            assertTrue("nó ${r.axisMs[j]} ms empobreceu", r.refinedRaw[j] >= r.currentRaw[j])
        }
        // A curva que vai para a ECU (com a trava da tela/cérebro) é a mesma do motor e não tem degrau.
        val guarded = EquivalenceEngine.guardedRefined(r)
        assertEquals(r.refinedRaw, guarded)
        // Sem degrau na transição da trava (antes o corte depois da coerência criava um degrau logo acima de 3,5 ms).
        for (j in 0 until 29) if (r.axisMs[j + 1] <= 8.0) {
            val e = abs(ln(guarded[j + 1].toDouble() / guarded[j]) / ln(r.axisMs[j + 1] / r.axisMs[j]))
            assertTrue("degrau entre ${r.axisMs[j]} e ${r.axisMs[j + 1]} ms: elasticidade $e", e <= r.elasticityLimit + 0.02)
        }
    }
}
