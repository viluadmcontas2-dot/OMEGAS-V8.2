package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.RealSessionReplaySupport.AUTOMATCH
import com.omegas.prohub.autocal.RealSessionReplaySupport.GNV_ONLY
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Classe 3 (replay de corpus real): as sessões reais do proprietário passam pelo livro de pontos,
 * pelo motor de refino e pelo piloto. Não prova nada sobre o carro; prova que a lógica faz,
 * sobre dado real, o que o contrato diz.
 */
class RefinementRealSessionTest {
    private fun replay(name: String): EquivalenceLedger {
        val ledger = EquivalenceLedger(null)
        RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name)).forEach {
            ledger.accept(RealSessionReplaySupport.ledgerFrame(it))
        }
        return ledger
    }

    private fun asPairs(list: List<EquivalenceLedger.EvidencePair>) = list.map { it.petrolRefMs to it.gasPetrolMs }

    // ------------------------------------------------------------------ marcha lenta

    @Test
    fun `na sessao do AutoMatch tres quartos dos pares sao de marcha lenta e nao podem propor curva`() {
        val ledger = replay(AUTOMATCH)
        val all = ledger.pairs()
        val driving = ledger.drivingPairs()
        val idle = all.count { it.rpm < EquivalenceLedger.DRIVING_MIN_RPM }
        // A janela estável recusa ms que pula > 10% (zigue-zague 8↔9 ms): menos leituras, mais limpas.
        assertTrue("pares ${all.size}", all.size in 15..90)
        assertTrue("lenta ${idle} de ${all.size}", idle >= all.size * 0.5)
        assertTrue(driving.all { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS })

        val snapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(AUTOMATCH), 1716)
        val feedAll = asPairs(all.filter { it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS })
        // O que o código antigo entregava ao motor: com a lenta, "fabrica" uma proposta. Com o gate de
        // cobertura (≥ 3 faixas distintas com ≥ 8 pares) nem alimentando tudo isso vira proposta.
        val oldBehaviour = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, feedAll)
        assertFalse("a lenta sozinha não cobre 3 faixas: não habilita proposta", oldBehaviour.getBoolean("telemetryOnly"))
        // O que entrega agora: só condução. Cobertura insuficiente → falha fechado, igual a não ter pares.
        val fixed = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, asPairs(driving))
        val none = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot)
        assertFalse(fixed.getBoolean("telemetryOnly"))
        assertEquals("NENHUMA", fixed.getString("evidenceSource"))
        assertEquals(none.getInt("changedCount"), fixed.getInt("changedCount"))
    }

    @Test
    fun `lenta fica no grafico mas nunca entra no indice nem no diario`() {
        val ledger = replay(REFERENCE)
        val index = ledger.index()
        assertEquals(ledger.drivingPairs().size, index.getInt("samples"))
        assertTrue(ledger.pairs().size > ledger.drivingPairs().size)
        // Os pontos densos (visuais) continuam no gráfico, cada um com a parcela de lenta explícita.
        val gas = ledger.denseBandsJson().getJSONArray("gas")
        assertTrue(gas.length() > 0)
        for (i in 0 until gas.length()) {
            val share = gas.getJSONObject(i).getDouble("idleShare")
            assertTrue(share in 0.0..1.0)
        }
    }

    // ------------------------------------------------------------------ independência do AutoMatch nativo

    @Test
    fun `a conducao real da sessao REFERENCE so propoe dentro da caixa de K e do passo maximo`() {
        // Com 2 faixas bastando (valor da Platina) a sessão pode propor; o que não pode é sair da caixa 12288–19661 nem do passo de 15%.
        val ledger = replay(REFERENCE)
        val pairs = ledger.drivingPairs()
        val snapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 962)
        val withEpisodes = AutoMatchSnapshotAnalysis.analyzeRefined(
            snapshot, asPairs(pairs), null, pairs.map { it.episode },
        )
        val points = withEpisodes.optJSONArray("points")
        for (i in 0 until (points?.length() ?: 0)) {
            val p = points!!.getJSONObject(i)
            val cur = p.getInt("currentRaw"); val calc = p.getInt("calculatedRaw")
            if (calc == cur) continue
            assertTrue("ponto $i fora da caixa: $calc", calc in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
            assertTrue("ponto $i passo ${calc.toDouble() / cur}", kotlin.math.abs(calc.toDouble() / cur - 1.0) <= 0.15 + 1e-3)
        }
    }

    @Test
    fun `logo depois do reset da ECU a conducao com cobertura propoe sem esperar faixas nativas`() {
        // Condução sintética COM cobertura: 12 pares por faixa, espalhados por dentro de cada uma, GNV 10% pobre.
        val driving = EvidenceTestSupport.pairsIn(1.10, 12, listOf(0, 1, 2, 3, 4))
        // seq 962: a ECU acabou de zerar a aquisição de gasolina; nenhuma faixa nativa madura.
        val snapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 962)
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, driving)

        assertTrue(analysis.getBoolean("available"))
        assertEquals("EQUIVALENCE", analysis.getString("refinementMode"))
        assertTrue(analysis.getBoolean("telemetryOnly"))
        assertEquals("CONDUCAO", analysis.getString("evidenceSource"))
        assertEquals(0, analysis.getInt("matureCommonPoints"))
        assertTrue(analysis.getInt("changedCount") > 0)
        assertFalse(analysis.getBoolean("automatic"))
        assertTrue(analysis.getBoolean("manualOnly"))

        val points = analysis.getJSONArray("points")
        var measured = 0
        for (i in 0 until points.length()) {
            val p = points.getJSONObject(i)
            val current = p.getInt("currentRaw").toDouble()
            val calculated = p.getInt("calculatedRaw").toDouble()
            assertTrue("ponto $i muda mais de 15%", abs(calculated / current - 1.0) <= 0.1501)
            if (p.getDouble("referenceTimeMs") < AutoMatchSnapshotAnalysis.LOW_GUARD_MS) {
                assertTrue("a baixa nunca empobrece (ponto $i)", calculated >= current)
            }
            if (p.getString("origin") == "MEASURED") measured++
        }
        assertTrue("há faixas medidas pela condução", measured >= 3)
        val after = analysis.getJSONObject("metricsAfter")
        assertTrue(after.getDouble("maxElasticity") <= analysis.getDouble("elasticityLimit") + 0.01)
    }

    @Test
    fun `com faixas nativas maduras a ECU continua mandando e a condução so complementa`() {
        val driving = asPairs(replay(REFERENCE).drivingPairs())
        val snapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 95)
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, driving)
        assertEquals("ECU_E_CONDUCAO", analysis.getString("evidenceSource"))
        assertFalse(analysis.getBoolean("telemetryOnly"))
        assertTrue(analysis.getInt("matureCommonPoints") >= AutoMatchRefinedEngine.MIN_COMMON_MATURE)
    }

    @Test
    fun `cobertura fina da conducao falha fechada`() {
        val few = asPairs(replay(REFERENCE).drivingPairs()).take(10)
        val snapshot = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 962)
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, few)
        val none = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot)
        assertEquals("NENHUMA", analysis.getString("evidenceSource"))
        assertFalse(analysis.getBoolean("telemetryOnly"))
        assertEquals("poucos pares = igual a não ter nenhum", none.getInt("changedCount"), analysis.getInt("changedCount"))
    }

    // ------------------------------------------------------------------ nossos pontos estáveis

    @Test
    fun `nossos pontos nunca caem sem motivo ao longo das sessoes reais`() {
        for (name in listOf(AUTOMATCH, GNV_ONLY, REFERENCE)) {
            val ledger = EquivalenceLedger(null)
            val frames = RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name))
            var lastPetrol = 0
            var lastGas = 0
            var maxSamples = 0
            frames.forEachIndexed { i, frame ->
                ledger.accept(RealSessionReplaySupport.ledgerFrame(frame))
                if (i % 100 == 99) {
                    val index = ledger.index()
                    val petrol = index.getInt("petrolObservations")
                    val gas = index.getInt("gasObservations")
                    assertTrue("$name quadro $i: gasolina caiu $lastPetrol → $petrol", petrol >= lastPetrol)
                    assertTrue("$name quadro $i: GNV caiu $lastGas → $gas", gas >= lastGas)
                    lastPetrol = petrol
                    lastGas = gas
                    maxSamples = maxOf(maxSamples, index.getInt("samples"))
                }
            }
            val finalSamples = ledger.index().getInt("samples")
            assertTrue("$name: amostras da condução caíram de $maxSamples para $finalSamples", finalSamples >= maxSamples * 0.9)
        }
    }

    // ------------------------------------------------------------------ piloto na sessão real do AutoMatch

    @Test
    fun `piloto na sessao real do AutoMatch espera a ECU e nunca oferece gravar antes do automatico fechar`() {
        val root = RealSessionReplaySupport.fixture(AUTOMATCH)
        val frames = RealSessionReplaySupport.telemetry(root).sortedBy { it.t }
        var cursor = 0
        val ledger = EquivalenceLedger(null)
        var now = 0L
        val pilot = EquivalencePhases(null) { now }
        val journal = RefinementJournal(null) { now }
        val phases = ArrayList<Pair<Int, String>>()
        val counts = ArrayList<Int>()
        for (snapshot in RealSessionReplaySupport.snapshots(root)) {
            val at = snapshot.getLong("capturedAtMs")
            while (cursor < frames.size && frames[cursor].t <= at) ledger.accept(RealSessionReplaySupport.ledgerFrame(frames[cursor++]))
            now = at
            val count = RealSessionReplaySupport.rawValues(snapshot, "NUM_AUTOMATCH_EXECUTED")!![0]
            counts += count
            val monitor = JSONObject().put("autoMatchCount", count).put("maxAutomatch", 3).put("autoCalEnabled", 1)
            val result = pilot.observe(
                ecuOnline = true,
                monitor = monitor,
                acquisition = RealSessionReplaySupport.acquisition(snapshot),
                index = ledger.index(),
                journal = journal.json(),
                restoreCount = 0,
            )
            phases += snapshot.getInt("sequence") to result.getString("phase")
            if (count < 3) {
                assertFalse("seq ${snapshot.getInt("sequence")}: ECU ainda no automático", result.getBoolean("ecuDone"))
                assertTrue("seq ${snapshot.getInt("sequence")}: fase ${result.getString("phase")} com contador $count",
                    result.getString("phase") == "ECU_TRABALHANDO")
                assertNull("não avisa enquanto a ECU trabalha", pilot.takeAlert())
            } else {
                assertEquals("MAX_AUTOMATCH", result.getString("ecuDoneReason"))
            }
        }
        // O corpus real tem 3 (partida) → 0 (reset da ECU) → 1 (AutoMatch nativo executado).
        // O ciclo 0 → 3 completo não está neste corpus: o teste não finge que está.
        assertEquals(3, counts.first())
        assertTrue(counts.contains(0))
        assertEquals(1, counts.last())
        assertNotNull(phases.firstOrNull { it.first == 53 })
        assertEquals("ECU_TRABALHANDO", phases.last().second)
    }
}
