package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.RealSessionReplaySupport.AUTOMATCH
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 3 de prova: replay das sessões reais do dono. */
class EquivalenceRealSessionTest {
    private fun usage(name: String): UsageMeter.Reading {
        val meter = UsageMeter(null)
        EquivalenceReplaySupport.frames(name).forEach { meter.accept(it.t, it.fuel, it.rpm, it.map, 1L) }
        return meter.reading()
    }

    /** Avalia a Curva K do snapshot [kSeq] com a gasolina da sessão inteira e o GNV dos quadros que passam em [gasKeep]. */
    private fun evaluate(
        name: String, refSeq: Int, kSeq: Int, gasKeep: (com.omegas.prohub.autocal.RealSessionReplaySupport.Telemetry) -> Boolean,
    ): EquivalenceResult {
        val (axis, k) = EquivalenceReplaySupport.curve(name, kSeq)
        val petrol = EquivalenceReplaySupport.ledger(name).petrolObservations()
        val gas = EquivalenceReplaySupport.ledger(name, gasKeep).gasObservations()
        return EquivalenceEngine.evaluate(
            EquivalenceInput(
                axis, k, EquivalenceReplaySupport.reference(name, refSeq), null, petrol, gas,
                ExperienceMeter(null).reading(), usage(name),
            ),
        )
    }

    @Test
    fun `app novo com ECU madura e sem telemetria pede para congelar`() {
        val (axis, k) = EquivalenceReplaySupport.curve(REFERENCE, 95)
        val provisional = ReferenceStore(null).provisional(EquivalenceReplaySupport.acquisition(REFERENCE, 95))
        val r = EquivalenceEngine.evaluate(
            EquivalenceInput(axis, k, null, provisional, emptyList(), emptyList(), ExperienceMeter(null).reading(), UsageMeter(null).reading()),
        )
        assertTrue(r.provisional)
        assertEquals(NextActionKind.FREEZE_REFERENCE, r.nextAction.kind)
        assertEquals("refino", r.nextAction.route)
        assertEquals(null, r.nextAction.subpage)
        assertTrue(r.ownPetrol.cells.filter { it.petrolMs != null }.all { it.source == CellSource.REFERENCE })
        // Sem nenhuma leitura de GNV não há evidência em ponto nenhum: SEM_DADOS (a Referência sozinha não julga o GNV).
        assertTrue(r.points.all { it.state == PointState.SEM_DADOS })
        assertNull(r.index)
        assertEquals(0, r.coverage)
    }

    @Test
    fun `na sessao REFERENCE a escrita foi um reset plano e o indice cai`() {
        // A escrita de 20:31:57 zerou a curva (MUL_ACT = 1,0 nos 30 pontos): o índice cai porque a curva boa foi perdida.
        val writeAt = EquivalenceReplaySupport.writeAtMs(REFERENCE)
        assertTrue(EquivalenceReplaySupport.curve(REFERENCE, 2550).second.all { it == 16384 })
        assertTrue(EquivalenceReplaySupport.curve(REFERENCE, 2183).second.any { it != 16384 })
        val before = evaluate(REFERENCE, 95, 2183) { it.fuel != "GNV" || it.t < writeAt }
        val after = evaluate(REFERENCE, 95, 2550) { it.fuel != "GNV" || it.t >= writeAt }
        println("INDEX_REFERENCE before=${before.index} cov=${before.coverage} after=${after.index} cov=${after.coverage}")
        // Achado da revisão adversarial: nesta sessão real os pontos julgados (≥ 3 visitas independentes, dispersão conhecida)
        // cobrem bem menos da metade do uso, então o índice NÃO é um número ("—"). Antes saía 100% com coverage 1–5.
        assertTrue("fração julgada ${before.judgedUsage}", before.judgedUsage < EquivalenceTolerances.MIN_JUDGED_USAGE)
        assertNull(before.index)
        assertTrue(after.index == null || after.index!! <= 1.0)
    }

    @Test
    fun `na sessao AUTOMATCH a janela curta nao da cobertura para afirmar que o indice subiu`() {
        // A escrita de 16:04:38 também foi um reset plano (K = 1,0). 5 minutos de GNV não cobrem 2 pontos com confiança.
        val writeAt = EquivalenceReplaySupport.writeAtMs(AUTOMATCH)
        val until = EquivalenceReplaySupport.snapshotAtMs(AUTOMATCH, 1401)
        assertTrue(EquivalenceReplaySupport.curve(AUTOMATCH, 699).second.all { it == 16384 })
        val before = evaluate(AUTOMATCH, 634, 634) { it.fuel != "GNV" || it.t < writeAt }
        val after = evaluate(AUTOMATCH, 634, 699) { it.fuel != "GNV" || (it.t >= writeAt && it.t <= until) }
        println("INDEX_AUTOMATCH before=${before.index} cov=${before.coverage} after=${after.index} cov=${after.coverage}")
        assertTrue(before.coverage < 2 && after.coverage < 2)
        listOf(before.index, after.index).forEach { assertTrue(it == null || it in 0.0..1.0) }
    }

    @Test
    fun `a proposta do cerebro nunca sai do intervalo do AutoMatch nem muda sem evidencia`() {
        val writeAt = EquivalenceReplaySupport.writeAtMs(REFERENCE)
        val r = evaluate(REFERENCE, 95, 2183) { it.fuel != "GNV" || it.t < writeAt }
        val proposal = r.proposal
        assertNotNull(proposal)
        proposal!!.refinedRaw.forEachIndexed { j, v ->
            assertTrue("ponto $j: $v", v == proposal.currentRaw[j] || v in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
        }
        if (proposal.mode != AutoMatchRefinedEngine.Mode.EQUIVALENCE) assertEquals(proposal.currentRaw, proposal.refinedRaw)
    }

    @Test
    fun `sessao so de GNV sem gasolina nao inventa julgamento`() {
        val name = "gnv_only_2026-09-30_0931"
        val (axis, k) = EquivalenceReplaySupport.curve(name, 642)
        val ledger = EquivalenceReplaySupport.ledger(name)
        assertTrue(ledger.petrolObservations().isEmpty())
        val r = EquivalenceEngine.evaluate(
            EquivalenceInput(axis, k, null, null, ledger.petrolObservations(), ledger.gasObservations(), ExperienceMeter(null).reading(), usage(name)),
        )
        assertTrue(r.points.all { it.state == PointState.SEM_DADOS })
        assertNull(r.index)
    }
}
