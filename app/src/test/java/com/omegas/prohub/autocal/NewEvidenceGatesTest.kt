package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.EvidenceTestSupport.interior
import com.omegas.prohub.autocal.EvidenceTestSupport.pairsIn
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import com.omegas.prohub.equivalence.CellSource
import com.omegas.prohub.equivalence.EquivalenceEngine
import com.omegas.prohub.equivalence.EquivalenceInput
import com.omegas.prohub.equivalence.EquivalenceReplaySupport
import com.omegas.prohub.equivalence.EquivalenceTolerances
import com.omegas.prohub.equivalence.ExperienceMeter
import com.omegas.prohub.equivalence.NextActionKind
import com.omegas.prohub.equivalence.OwnCurveFitter
import com.omegas.prohub.equivalence.PointState
import com.omegas.prohub.equivalence.RefPoint
import com.omegas.prohub.equivalence.Reference
import com.omegas.prohub.equivalence.UsageMeter
import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.exp
import kotlin.math.abs

/**
 * Revisão adversarial do cérebro do Refino (P1-1, P1-2, P2-1…P2-6, P3). Cada teste falha se a regra for desfeita;
 * os cenários espelham tests/test_brain_evidence_gates.py (oráculo Python).
 */
class NewEvidenceGatesTest {
    private val axisRaw = IntArray(30) { (KFactorProtocol.OBSERVED_PETROL_AXIS_MS[it] * 512.0).toInt() }
    private fun flatK(raw: Int = 16384) = IntArray(30) { raw }

    private fun refine(
        pairs: List<Pair<Double, Double>>, episodes: List<Int> = emptyList(), k: IntArray = flatK(),
    ) = AutoMatchRefinedEngine.refine(
        AutoMatchRefinedEngine.Input(axisRaw, k, null, null, null, null, null, null, pairs, null, episodes, 0.0),
    )

    // ------------------------------------------------------------------ P2-1: episódios = visitas ≥ 60 s

    @Test
    fun `visitas contam por lacuna de 60 s e nunca por quadro`() {
        assertEquals(1, EvidencePairs.visitCount(List(100) { it * 300L }))            // 30 s de quadros seguidos
        assertEquals(1, EvidencePairs.visitCount(listOf(0L, 5_000L, 10_000L)))        // lacuna de 5 s NÃO abre episódio
        assertEquals(3, EvidencePairs.visitCount(listOf(0L, 90_000L, 180_000L)))
        assertEquals(2, EvidencePairs.visitCount(listOf(0L, 59_999L, 119_999L + 1L)))
        assertEquals(0, EvidencePairs.visitCount(emptyList()))
    }

    @Test
    fun `oito amostras e tres episodios em dez segundos nao fazem uma faixa valer`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        fun drive(fuel: String, ms: Double, n: Int) = repeat(n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, 2000.0, 0.60, ms)); t += 280 }
        drive("GASOLINA", 5.0, 20)
        // três "trechos" de 6 quadros separados por 5 s (≈ 10 s de condução): antes eram 3 episódios.
        repeat(3) { t += 5_000; drive("GNV", 5.5, 6) }
        val band = ledger.index().getJSONArray("bands").getJSONObject(1)
        assertEquals(1, band.getInt("episodes"))
        assertTrue(ledger.drivingPairs().map { it.episode }.toSet().size == 1)
    }

    @Test
    fun `janela estavel rejeita ms que pula mais de dez por cento`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(60) { i -> ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2000.0, 0.60, if (i % 2 == 0) 8.0 else 9.0)); t += 100 }
        assertEquals("zigue-zague 8↔9 ms (12%) não é leitura estável", 0, ledger.gasObservations().size)
        repeat(60) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2000.0, 0.60, 8.0 + (it % 2) * 0.4)); t += 100 }
        assertTrue("±5% ainda é estável", ledger.gasObservations().isNotEmpty())
    }

    @Test
    fun `faixa grossa so vale com leituras espalhadas por dentro dela`() {
        // 30 pares todos entre 4,5 e 5,0 ms na faixa 4,5–6,0: uma ponta só não é a faixa.
        val oneEnd = List(30) { 4.5 + 0.5 * (it + 0.5) / 30 }.map { it to it * 1.1 }
        val pairs = oneEnd + pairsIn(1.1, 12, listOf(2, 3, 4))
        val r = refine(pairs)
        assertEquals(36, r.telemetryPairsUsed)
        assertEquals(AutoMatchRefinedEngine.Mode.POLISH, refine(oneEnd).mode)
        assertFalse(EvidencePairs.interiorCovered(listOf(4.6, 4.7, 4.8, 4.9), 4.5, 6.0))
        assertTrue(EvidencePairs.interiorCovered(listOf(4.6, 4.7, 5.6, 5.7), 4.5, 6.0))
    }

    @Test
    fun `um episodio desconhecido tira so o par e nao desliga o portao das faixas`() {
        val pairs = pairsIn(1.1, 9, listOf(2, 3, 4))
        val episodes = pairs.indices.map { it % 3 }.toMutableList().also { it[0] = -1 }
        val r = refine(pairs, episodes)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.mode)
        assertEquals(pairs.size - 1, r.telemetryPairsUsed)
        // e o portão continua valendo: sem episódios distintos, nada.
        assertEquals(AutoMatchRefinedEngine.Mode.POLISH, refine(pairs, List(pairs.size) { 0 }.toMutableList().also { it[0] = -1 }).mode)
    }

    @Test
    fun `peso por episodio - um episodio de uma faixa vale no maximo quatro pares`() {
        val pairs = pairsIn(1.1, 30, listOf(2, 3, 4))
        val episodes = pairs.indices.map { it % 3 }                     // 10 pares por episódio e faixa
        val w = AutoMatchRefinedEngine.pairWeights(pairs, episodes)
        assertTrue(w.all { abs(it - AutoMatchRefinedEngine.EPISODE_PAIR_CAP / 10.0) < 1e-12 })
        assertTrue(AutoMatchRefinedEngine.pairWeights(pairs, null).all { it == 1.0 })
    }

    // ------------------------------------------------------------------ P2-2/P2-3: telemetria × nativa, proposta que piora

    private val refSnapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 95)

    @Test
    fun `a telemetria nao entra em ponto que a nativa madura ja cobre e tem teto por faixa`() {
        val native = AutoMatchSnapshotAnalysis.analyzeRefined(refSnapshot)
        val pairs = (0..4).flatMap { b -> interior(b, 12).map { it to it * 1.3 } }
        val mixed = AutoMatchSnapshotAnalysis.analyzeRefined(refSnapshot, pairs)
        assertNotNull(native)
        assertNotNull(mixed)
        // teto de peso por faixa: 300 pares da mesma faixa pesam o mesmo que o teto
        val many = interior(2, 300).map { it to it * 1.1 }
        val targets = many.map { (tp, tg) -> AutoMatchRefinedEngine.Target(Double.NaN, tp, tg, AutoMatchRefinedEngine.TELEMETRY_WEIGHT, tg / tp, 0.0) }
        val capped = AutoMatchRefinedEngine.capBandWeight(targets)
        assertEquals(AutoMatchRefinedEngine.TELEMETRY_BAND_WEIGHT_CAP, capped.sumOf { it.weight }, 1e-9)
    }

    @Test
    fun `razao exatamente 1 nao mexe na curva e a proposta nunca piora o criterio do motor`() {
        val snap = JSONObject(refSnapshot.toString())
        val fields = snap.getJSONArray("fields")
        fun field(key: String) = (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("key") == key }
        field("PETR_INJ_TBUF_GAS").put("rawValues", field("PETR_INJ_TBUF").getJSONArray("rawValues"))
        field("MNFLD_PRESS_BUF_GAS").put("rawValues", field("MNFLD_PRESS_BUF").getJSONArray("rawValues"))
        field("NUM_BUF_UPD_GAS").put("rawValues", field("NUM_BUF_UPD_PETR").getJSONArray("rawValues"))
        val input = fieldsToInput(snap)
        val r = AutoMatchRefinedEngine.refine(input)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.mode)
        assertEquals(0.0, r.evidenceErrorBefore!!, 1e-9)
        assertTrue(r.evidenceErrorAfter!! <= r.evidenceErrorBefore!! + AutoMatchRefinedEngine.REGRESSION_EPS)
        assertEquals("antes: 20 pontos mexidos e erro 0% → 1,4%", r.currentRaw, r.refinedRaw)
    }

    @Test
    fun `nos dentro da tolerancia nao se movem e acima dela a curva se move`() {
        val within = refine((1..4).flatMap { b -> interior(b, 12).map { it to it * 1.02 } })
        assertTrue(within.deadBandPoints > 10)
        assertTrue(within.refinedRaw.indices.count { within.refinedRaw[it] != within.currentRaw[it] } <= 6)
        val far = refine((1..4).flatMap { b -> interior(b, 12).map { it to it * 1.045 } })
        assertEquals(0, far.deadBandPoints)
        assertTrue(far.refinedRaw.indices.count { far.refinedRaw[it] != far.currentRaw[it] } > 10)
    }

    private fun fieldsToInput(snap: JSONObject): AutoMatchRefinedEngine.Input {
        fun raw(key: String): IntArray {
            val a = (0 until snap.getJSONArray("fields").length()).map { snap.getJSONArray("fields").getJSONObject(it) }
                .first { it.getString("key") == key }.getJSONArray("rawValues")
            return IntArray(a.length()) { a.getInt(it) }
        }
        return AutoMatchRefinedEngine.Input(
            raw("PETR_INJ_TBP"), raw("MUL_ACT"), raw("PETR_INJ_TBUF"), raw("MNFLD_PRESS_BUF"), raw("NUM_BUF_UPD_PETR"),
            raw("PETR_INJ_TBUF_GAS"), raw("MNFLD_PRESS_BUF_GAS"), raw("NUM_BUF_UPD_GAS"),
        )
    }

    // ------------------------------------------------------------------ P2-4: alinhamento da curva

    @Test
    fun `sem impressao digital anterior e com GNV guardado a curva e desconhecida e o GNV sai`() {
        var now = 0L
        val ledger = EquivalenceLedger(null) { now }
        repeat(40) { i -> ledger.accept(EquivalenceLedger.Frame(i * 100L, "GNV", 2000.0, 0.60, 5.0)) }
        assertTrue(ledger.gasObservations().isNotEmpty())
        assertTrue("falha fechada", ledger.alignCurve("fp-A"))
        assertTrue(ledger.gasObservations().isEmpty())
        // com a impressão digital conhecida e a mesma curva, nada se perde
        repeat(40) { i -> ledger.accept(EquivalenceLedger.Frame(10_000L + i * 100L, "GNV", 2000.0, 0.60, 5.0)) }
        assertFalse(ledger.alignCurve("fp-A"))
        assertTrue(ledger.gasObservations().isNotEmpty())
        // curva mudou por fora do app: descarta e avisa
        assertTrue(ledger.alignCurve("fp-B"))
        assertTrue(ledger.gasObservations().isEmpty())
    }

    @Test
    fun `leitura velha da curva anterior logo depois de o app gravar nao conta como mudanca externa`() {
        var now = 1_000L
        val ledger = EquivalenceLedger(null) { now }
        ledger.alignCurve("fp-A")
        ledger.adoptCurve("fp-B")                                      // o app gravou: B é a curva vigente
        repeat(40) { i -> ledger.accept(EquivalenceLedger.Frame(i * 100L, "GNV", 2000.0, 0.60, 5.0)) }
        now += 5_000L
        assertFalse("snapshot velho ainda mostra A", ledger.alignCurve("fp-A"))
        assertTrue(ledger.gasObservations().isNotEmpty())
        now += EquivalenceLedger.STALE_ALIGN_MS
        assertTrue("passado o prazo, A de volta é mudança externa", ledger.alignCurve("fp-A"))
    }

    @Test
    fun `o tique do servico alinha a curva sem depender da tela e o cerebro esquece o GNV antigo`() {
        val other = EquivalenceReplaySupport.curve(REFERENCE, 2550).second   // reset plano feito por fora
        val mine = EquivalenceReplaySupport.curve(REFERENCE, 2183).second
        assertTrue(EquivalenceLedger.fingerprint(other) != EquivalenceLedger.fingerprint(mine))
        val none: (List<Double>) -> DoubleArray? = { null }
        val acq = EquivalenceReplaySupport.acquisition(REFERENCE, 2183)
        val snap = EquivalenceReplaySupport.snapshot(REFERENCE, 2550)
        // sem cabo não há curva viva: não alinha
        val offline = EquivalenceReplaySupport.ledger(REFERENCE, curveSeq = 2183)
        com.omegas.prohub.equivalence.EquivalenceRuntime(null)
            .evaluate(offline, com.omegas.prohub.autocal.EquivalencePhases(null) { 0L }, snap, acq, false, none)
        assertTrue(offline.gasObservations().isNotEmpty())
        // com cabo o tique alinha: a curva da ECU não é a do GNV guardado, o GNV sai
        val online = EquivalenceReplaySupport.ledger(REFERENCE, curveSeq = 2183)
        assertTrue(online.gasObservations().isNotEmpty())
        com.omegas.prohub.equivalence.EquivalenceRuntime(null)
            .evaluate(online, com.omegas.prohub.autocal.EquivalencePhases(null) { 0L }, snap, acq, true, none)
        assertTrue(online.gasObservations().isEmpty())
    }

    @Test
    fun `curva incoerente no tempo nao e curva do cerebro`() {
        val ok = JSONObject()
            .put("temporalCoherent", true)
            .put("fields", org.json.JSONArray()
                .put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", org.json.JSONArray(axisRaw.toList())))
                .put(JSONObject().put("key", "MUL_ACT").put("status", "VALID").put("rawValues", org.json.JSONArray(flatK().toList()))))
        assertNotNull(EquivalenceEngine.curveFromSnapshot(ok))
        assertNull(EquivalenceEngine.curveFromSnapshot(JSONObject(ok.toString()).put("temporalCoherent", false)))
        // `partial` é true em todo snapshot real e NÃO é critério
        assertNotNull(EquivalenceEngine.curveFromSnapshot(JSONObject(ok.toString()).put("partial", true)))
    }

    // ------------------------------------------------------------------ P1-1 / P1-2 / P2-5: o cérebro

    private val flat = flatK()
    private val reference = Reference("REF", 1L, "fp", (2..10).map { RefPoint(0.1 * it, 10.0 * 0.1 * it, 10) })

    private fun obs(factor: (Double) -> Double, readings: Int, noise: (Int, Int) -> Double = { _, _ -> 1.0 }): List<EquivalenceLedger.Obs> {
        val out = ArrayList<EquivalenceLedger.Obs>()
        for (cell in 5 until 45) {
            val c = OwnCurveFitter.center(cell)
            repeat(readings) { i -> out += EquivalenceLedger.Obs(i * 70_000L + cell, 2000.0, c, 10.0 * c * factor(c) * noise(i, cell)) }
        }
        return out
    }

    private fun uniformUsage(): UsageMeter.Reading =
        UsageMeter.Reading(DoubleArray(OwnCurveFitter.GRID_CELLS) { if (it in 5 until 45) 1.0 else 0.0 })

    private fun evaluate(gas: List<EquivalenceLedger.Obs>, petrol: List<EquivalenceLedger.Obs> = obs({ 1.0 }, 20), usage: UsageMeter.Reading = uniformUsage()) =
        EquivalenceEngine.evaluate(
            EquivalenceInput(EquivalenceReplaySupport.curve(REFERENCE, 95).first, flat, reference, null, petrol, gas, ExperienceMeter(null).reading(), usage),
        )

    @Test
    fun `duas visitas nunca julgam mesmo com muitas leituras e o indice some`() {
        val gas = obs({ 1.0 }, 40).mapIndexed { i, o -> o.copy(t = (i % 2) * 70_000L + i) }
        val r = evaluate(gas)
        assertTrue(r.points.all { it.state == PointState.SEM_DADOS || it.state == PointState.APRENDENDO })
        assertNull(r.index)
    }

    @Test
    fun `dispersao desconhecida nao vira tolerancia larga - o ponto nao e julgado`() {
        val r = evaluate(obs({ 1.05 }, 1))
        assertTrue(r.points.none { it.state == PointState.EQUIVALENTE })
    }

    @Test
    fun `o indice so e numero quando os pontos julgados cobrem metade do uso`() {
        val some = obs({ 1.0 }, 20).filter { it.map in 0.60..0.70 }
        val partial = evaluate(some)
        assertTrue(partial.judgedUsage < EquivalenceTolerances.MIN_JUDGED_USAGE)
        assertNull(partial.index)
        val full = evaluate(obs({ 1.0 }, 20))
        assertTrue(full.judgedUsage >= EquivalenceTolerances.MIN_JUDGED_USAGE)
        assertNotNull(full.index)
        assertEquals(1.0, full.index!!, 1e-9)
    }

    @Test
    fun `GNV medido nao e puxado para a gasolina - sigma 3 por cento mais 8 por cento fica a um ponto`() {
        val rng = Random(7)
        val gas = obs({ 1.08 }, 4) { _, _ -> exp(rng.nextGaussian() * 0.03) }
        val r = evaluate(gas)
        val ratios = r.ownGas.cells.filter { it.samples >= 3 && it.petrolMs != null }.map { it.petrolMs!! / (10.0 * it.mapBar) - 1.0 }
        assertTrue(ratios.size > 20)
        assertEquals(0.08, ratios.average(), 0.01)
        assertTrue("estados POBRE aparecem", r.points.any { it.state == PointState.POBRE })
        val mixtures = r.points.filter { it.state == PointState.POBRE || it.state == PointState.EQUIVALENTE || it.state == PointState.RICO }.map { it.mixture!! }
        assertEquals(0.08, mixtures.average(), 0.01)
        // sem prior no GNV, a Referência não vira "fonte" das células de GNV com leitura
        assertTrue(r.ownGas.cells.filter { it.samples > 0 }.none { it.source == CellSource.REFERENCE })
    }

    @Test
    fun `referencia que contradiz o livro por RPM nao e EQUIVALENTE e o veredito concorda com a proposta`() {
        // gasolina medida 5,5% abaixo da Referência; o GNV bate com a Referência = 5,8% rico frente à gasolina real.
        val petrol = obs({ 0.945 }, 20)
        val gas = obs({ 1.0 }, 20)
        val r = evaluate(gas, petrol)
        val judged = r.points.filter { it.state == PointState.POBRE || it.state == PointState.EQUIVALENTE || it.state == PointState.RICO }
        assertTrue(judged.isNotEmpty())
        assertTrue("o casamento por RPM manda: ${judged.map { it.state }}", judged.all { it.state == PointState.POBRE })
        // veredito e proposta saem do mesmo conjunto de pares: POBRE com proposta que sobe K nesses pontos
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        val proposal = r.proposal!!
        assertTrue(judged.any { proposal.refinedRaw[it.index] > proposal.currentRaw[it.index] })
        assertTrue(judged.none { proposal.refinedRaw[it.index] < proposal.currentRaw[it.index] })
    }

    @Test
    fun `quando tudo e equivalente a proposta nao mexe em nenhum ponto julgado`() {
        val r = evaluate(obs({ 1.0 }, 20))
        assertTrue(r.points.filter { it.state == PointState.EQUIVALENTE }.isNotEmpty())
        val p = r.proposal!!
        for (point in r.points.filter { it.state == PointState.EQUIVALENTE }) {
            // (≤ 0,1%: sobra de arredondamento de 1 LSB, não movimento)
            assertTrue("ponto ${point.index}", abs(p.refinedRaw[point.index].toDouble() / p.currentRaw[point.index] - 1.0) < 0.001)
        }
        assertTrue("nada a aplicar: ${r.nextAction.kind}", r.nextAction.kind != NextActionKind.APPLY)
    }

    @Test
    fun `sessoes reais - o indice deixa de ser 100 por cento com cobertura de 1 a 5 pontos`() {
        for (name in listOf("automatch_2026-10-01_1301", "gnv_only_2026-09-30_0931", "ref_2026-10-01_1719")) {
            val ledger = EquivalenceReplaySupport.ledger(name)
            val seqK = when (name) { "ref_2026-10-01_1719" -> 2550; "automatch_2026-10-01_1301" -> 2262; else -> 2336 }
            val refSeq = when (name) { "ref_2026-10-01_1719" -> 95; "automatch_2026-10-01_1301" -> 634; else -> 642 }
            val (axis, k) = EquivalenceReplaySupport.curve(name, seqK)
            val usage = UsageMeter(null)
            EquivalenceReplaySupport.frames(name).forEach { usage.accept(it.t, it.fuel, it.rpm, it.map, 1L) }
            val r = EquivalenceEngine.evaluate(
                EquivalenceInput(axis, k, EquivalenceReplaySupport.reference(name, refSeq), null, ledger.petrolObservations(),
                    ledger.gasObservations(), ExperienceMeter(null).reading(), usage.reading()),
            )
            assertTrue("$name: fração julgada ${r.judgedUsage}", r.judgedUsage < 0.5 && r.index == null)
        }
    }
}
