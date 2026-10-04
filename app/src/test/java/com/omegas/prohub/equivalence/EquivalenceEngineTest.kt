package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/** Sintético: gasolina T = 10·MAP ms; GNV = gasolina × (1 + m) por MAP; 20 leituras por célula. */
class EquivalenceEngineTest {
    private val axisRaw: IntArray = EquivalenceReplaySupport.curve(REFERENCE, 95).first
    private val flatK = IntArray(30) { 16384 }
    private val reference = Reference(
        "REF-SINTETICA", 1L, "fp",
        (2..10).map { RefPoint(0.1 * it, 10.0 * 0.1 * it, 10) },
    )

    private fun observations(factor: (Double) -> Double, noise: (Int) -> Double): List<EquivalenceLedger.Obs> {
        val out = ArrayList<EquivalenceLedger.Obs>()
        for (cell in 5 until 45) {
            val center = OwnCurveFitter.center(cell)
            // A leitura i de cada célula é uma visita própria (70 s de uma à outra): evidência independente, não 20 quadros de 1 s.
            repeat(20) { i -> out += EquivalenceLedger.Obs(i * 70_000L + cell, 2000.0, center, 10.0 * center * factor(center) * noise(i)) }
        }
        return out
    }

    private val alternating: (Int) -> Double = { if (it % 2 == 0) 1.005 else 0.995 }
    private val petrol get() = observations({ 1.0 }, alternating)
    private fun gasWithRichPlateau(plateau: Double) = observations({ if (it in 0.60..0.70) plateau else 1.0 }, alternating)

    private fun input(
        gas: List<EquivalenceLedger.Obs>,
        usage: UsageMeter.Reading = UsageMeter(null).reading(),
        reference: Reference? = this.reference,
        provisional: Reference? = null,
        operation: String? = null,
    ) = EquivalenceInput(axisRaw, flatK, reference, provisional, petrol, gas, ExperienceMeter(null).reading(), usage, operation)

    @Test
    fun `GNV 6 por cento pobre em 6 a 7 ms vira POBRE e a proposta sobe K ali`() {
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06)))
        assertEquals(PointState.POBRE, r.points[12].state)
        assertEquals(0.062, r.points[12].mixture!!, 0.015)
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        assertEquals("refino", r.nextAction.route)
        assertEquals(null, r.nextAction.subpage)
        assertTrue(12 in r.nextAction.pointIndexes)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.proposal!!.mode)
        assertTrue(r.proposal!!.refinedRaw[12] > r.proposal!!.currentRaw[12])
        r.proposal!!.refinedRaw.forEachIndexed { j, v ->
            assertTrue("ponto $j: $v fora do intervalo do AutoMatch", v == r.proposal!!.currentRaw[j] || v in 12288..19661)
        }
    }

    @Test
    fun `celula rala ganha folga - tolerancia duas vezes a dispersao`() {
        val s = 0.02023
        val wide: (Int) -> Double = { exp(((it % 4) - 1.5) * s) }
        val gas = observations({ 1.04 }, wide)
        val noisyPetrol = observations({ 1.0 }, wide)
        val r = EquivalenceEngine.evaluate(
            EquivalenceInput(axisRaw, flatK, reference, null, noisyPetrol, gas, ExperienceMeter(null).reading(), UsageMeter(null).reading()),
        )
        val p = r.points[12]
        // 2 × dispersão ≈ 6%, mas o critério nunca passa de ±5%: dispersão alta não alarga a tolerância.
        assertEquals(EquivalenceTolerances.MAX, p.tolerance, 1e-12)
        assertEquals(0.04, p.mixture!!, 0.012)
        assertEquals(PointState.EQUIVALENTE, p.state)
    }

    @Test
    fun `indice pondera por uso e ignora pontos sem confianca`() {
        val cells = DoubleArray(OwnCurveFitter.GRID_CELLS)
        cells[OwnCurveFitter.cellOf(0.51)!!] = 75.0
        cells[OwnCurveFitter.cellOf(0.65)!!] = 25.0
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06), UsageMeter.Reading(cells)))
        assertEquals(PointState.EQUIVALENTE, r.points[9].state)
        assertEquals(0.75, r.index!!, 1e-9)
        assertTrue(r.coverage >= 2)
        // sem GNV nenhum ponto é julgável: sem índice
        val none = EquivalenceEngine.evaluate(input(emptyList(), UsageMeter.Reading(cells)))
        assertNull(none.index)
        assertEquals(0, none.coverage)
    }

    @Test
    fun `prioridade da proxima acao segue a spec`() {
        val flat = gasWithRichPlateau(1.0)
        val contested = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06))) { ProofOutcome(mapOf(12 to PointState.CONTESTADO), null) }
        assertEquals(NextActionKind.CONTESTED, contested.nextAction.kind)
        assertEquals("Ajuste em 6,5–6,5 ms piorou a suavidade · Desfazer", contested.nextAction.text)
        val proving = EquivalenceEngine.evaluate(input(flat)) { ProofOutcome(mapOf(12 to PointState.EM_PROVA), 7) }
        assertEquals(NextActionKind.PROVING, proving.nextAction.kind)
        assertEquals("Rodando para provar o ajuste · faltam ~7 min de condução nessa faixa", proving.nextAction.text)
        val operating = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06), operation = "Gravando Curva K"))
        assertEquals(NextActionKind.OPERATION, operating.nextAction.kind)
        assertEquals("Gravando Curva K", operating.nextAction.text)
        val quiet = EquivalenceEngine.evaluate(input(flat, UsageMeter.Reading(DoubleArray(OwnCurveFitter.GRID_CELLS).also {
            it[OwnCurveFitter.cellOf(0.51)!!] = 10.0
        })))
        assertEquals(NextActionKind.NOTHING, quiet.nextAction.kind)
        assertEquals("Equivalente. Nada a fazer.", quiet.nextAction.text)
    }

    @Test
    fun `sem referencia propoe com curva propria madura e avisa`() {
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06), reference = null, provisional = null))
        assertTrue(r.provisional)
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        assertTrue(r.nextAction.text, r.nextAction.text.endsWith(" · sem referência da ECU"))
    }

    @Test
    fun `referencia provisoria pede para congelar antes de qualquer ajuste`() {
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06), reference = null, provisional = reference))
        assertTrue(r.provisional)
        assertEquals(NextActionKind.FREEZE_REFERENCE, r.nextAction.kind)
        assertEquals("refino", r.nextAction.route)
        assertEquals(null, r.nextAction.subpage)
    }

    @Test
    fun `curva K invalida devolve resultado vazio sem lancar`() {
        val r = EquivalenceEngine.evaluate(EquivalenceInput(IntArray(3), IntArray(3), reference, null, emptyList(), emptyList(),
            ExperienceMeter(null).reading(), UsageMeter(null).reading()))
        assertTrue(r.points.isEmpty())
        assertNotNull(r.nextAction)
    }
}
