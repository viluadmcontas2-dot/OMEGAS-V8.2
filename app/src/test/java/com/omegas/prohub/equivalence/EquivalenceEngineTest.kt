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
    fun `resultado preserva curvas e avaliacao independentes de lenta e conducao`() {
        fun regimeObs(rpm: Double, factor: Double) = List(20) { i ->
            EquivalenceLedger.Obs(i * 70_000L, rpm, 0.31, 2.2 * factor * if (i % 2 == 0) 1.005 else 0.995)
        }
        val petrolBoth = regimeObs(850.0, 1.0) + regimeObs(2100.0, 1.45)
        val gasBoth = regimeObs(850.0, 1.08) + regimeObs(2100.0, 1.45)
        val result = EquivalenceEngine.evaluate(EquivalenceInput(
            axisRaw, flatK, reference, null, petrolBoth, gasBoth,
            ExperienceMeter(null).reading(), UsageMeter(null).reading(),
        ))
        val cell = OwnCurveFitter.cellOf(0.31)!!
        assertEquals(20, result.regimeCurves[OperatingRegime.IDLE]!!.petrol.cells[cell].samples)
        assertEquals(20, result.regimeCurves[OperatingRegime.DRIVING]!!.petrol.cells[cell].samples)
        assertTrue(result.regimeAssessments[OperatingRegime.IDLE]!!.pairs > 0)
        assertTrue(result.regimeAssessments[OperatingRegime.DRIVING]!!.pairs > 0)
        assertTrue(result.regimeAssessments[OperatingRegime.IDLE]!!.mixture!! > 0.04)
        assertTrue(kotlin.math.abs(result.regimeAssessments[OperatingRegime.DRIVING]!!.mixture!!) < 0.03)
    }

    @Test
    fun `proposta global e bloqueada quando melhora conducao mas piora lenta madura`() {
        fun pairs(rpm: Double, tp: Double, tg: Double) = List(20) { i ->
            EquivalenceLedger.EvidencePair(tp, tg * if (i % 2 == 0) 1.002 else 0.998, rpm, t = i * 70_000L)
        }
        val evidence = pairs(2100.0, 2.0, 2.2) + pairs(850.0, 3.0, 3.3)
        val axis = listOf(1.0, 2.0, 3.0, 4.0)
        val current = listOf(1.0, 1.0, 1.0, 1.0)
        val hurtsIdle = listOf(1.2, 1.1, 1.0, 1.2)
        assertEquals(false, EquivalenceEngine.proposalDoesNotWorsenMatureRegimes(evidence, axis, current, hurtsIdle))
    }

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
        // Fonte única do que a UI grava: a ação carrega a curva lida e a proposta do motor (com a trava da baixa), e
        // `pointIndexes` são exatamente os pontos que mudam — os mesmos N de "Pronto para gravar N pontos".
        val before = r.nextAction.currentRaw!!
        val after = r.nextAction.refinedRaw!!
        assertEquals(r.proposal!!.currentRaw, before)
        assertEquals(EquivalenceEngine.guardedRefined(r.proposal!!), after)
        assertEquals((0 until 30).filter { before[it] != after[it] }.toSet(), r.nextAction.pointIndexes.toSet())
        assertTrue(r.nextAction.pointIndexes.isNotEmpty())
        after.forEachIndexed { j, v -> if (r.proposal!!.axisMs[j] < com.omegas.prohub.autocal.AutoMatchSnapshotAnalysis.LOW_GUARD_MS) assertTrue(v >= before[j]) }
        // Ponta a ponta: o mesmo resultado, serializado como a ponte entrega, vira "Pronto para gravar N pontos" com botão
        // (canAct) e a ação carrega a curva que o botão grava. É isto que garante que o Refino SUGERE de verdade.
        val brain = EquivalenceJson.result(r, reference, null, null)
        val phases = com.omegas.prohub.autocal.EquivalencePhases(null) { 0L }
        phases.observe(true, org.json.JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1),
            org.json.JSONObject().put("points", org.json.JSONArray()), org.json.JSONObject().put("samples", 60).put("revision", 1).put("bands", org.json.JSONArray()),
            org.json.JSONObject().put("latest", org.json.JSONObject.NULL), 0)
        val view = com.omegas.prohub.autocal.EquivalenceView.build(
            EquivalenceLedger(null), com.omegas.prohub.autocal.RefinementJournal(null), phases, com.omegas.prohub.autocal.StallWatch(null), brain,
        )
        val rs = view.getJSONObject("refinoState")
        assertTrue(rs.getString("phase"), rs.getBoolean("canAct"))
        assertEquals("Pronto para gravar ${r.nextAction.pointIndexes.size} pontos", rs.getString("phase"))
        assertEquals(r.nextAction.pointIndexes.size, rs.getJSONObject("counts").getInt("pointsToWrite"))
        val action = view.getJSONObject("nextAction")
        assertEquals(30, action.getJSONArray("currentRaw").length()); assertEquals(30, action.getJSONArray("refinedRaw").length())
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
    fun `ponto em prova nao e regravado mas os outros pontos fora seguem propostos`() {
        val free = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06)))
        assertEquals(NextActionKind.APPLY, free.nextAction.kind)
        val inProof = free.nextAction.pointIndexes.first()
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06))) { ProofOutcome(mapOf(inProof to PointState.EM_PROVA), null) }
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        assertTrue(inProof !in r.nextAction.pointIndexes)
        assertEquals(r.nextAction.currentRaw!![inProof], r.nextAction.refinedRaw!![inProof])
    }

    @Test
    fun `prioridade da proxima acao segue a spec`() {
        val flat = gasWithRichPlateau(1.0)
        val contested = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06))) { ProofOutcome(mapOf(12 to PointState.CONTESTADO), null) }
        assertEquals(NextActionKind.CONTESTED, contested.nextAction.kind)
        assertEquals("Ajuste em 6,5–6,5 ms piorou a suavidade · Desfazer", contested.nextAction.text)
        val proving = EquivalenceEngine.evaluate(input(flat)) { ProofOutcome(mapOf(12 to PointState.EM_PROVA), 7) }
        assertEquals(NextActionKind.PROVING, proving.nextAction.kind)
        assertEquals("Rodando para provar o ajuste", proving.nextAction.text)
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
    fun `referencia provisoria nao impede proposta de pares proprios maduros`() {
        val r = EquivalenceEngine.evaluate(input(gasWithRichPlateau(1.06), reference = null, provisional = reference))
        assertTrue(r.provisional)
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        assertEquals("refino", r.nextAction.route)
        assertTrue(r.nextAction.pointIndexes.isNotEmpty())
        assertTrue(r.nextAction.text.endsWith(" · sem referência da ECU"))
    }

    @Test
    fun `curva K invalida devolve resultado vazio sem lancar`() {
        val r = EquivalenceEngine.evaluate(EquivalenceInput(IntArray(3), IntArray(3), reference, null, emptyList(), emptyList(),
            ExperienceMeter(null).reading(), UsageMeter(null).reading()))
        assertTrue(r.points.isEmpty())
        assertNotNull(r.nextAction)
    }
}
