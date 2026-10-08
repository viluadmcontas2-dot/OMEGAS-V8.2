package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.RealSessionReplaySupport.AUTOMATCH
import com.omegas.prohub.autocal.RealSessionReplaySupport.GNV_ONLY
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import com.omegas.prohub.ecu.KFactorProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fatia H-evidência: a proposta de K só nasce de evidência plausível, madura e coberta, e fica na
 * faixa do AutoMatch nativo [0,75; 1,20]. Sintético (classe 2) + replay das sessões reais (classe 3).
 * Paridade Python: tests/test_refined_autocal_oracle.py (SafetyGates).
 */
class AutoMatchProposalSafetyTest {
    private val axisRaw = IntArray(30) { (KFactorProtocol.OBSERVED_PETROL_AXIS_MS[it] * 512.0).toInt() }

    private fun flatK(raw: Int = 16384) = IntArray(30) { raw }

    private fun refine(pairs: List<Pair<Double, Double>>, k: IntArray = flatK()) =
        AutoMatchRefinedEngine.refine(
            AutoMatchRefinedEngine.Input(axisRaw, k, null, null, null, null, null, null, pairs),
        )

    /** n pares (gasolina de referência, GNV) por faixa do livro, espalhados por DENTRO dela (cobertura interna). */
    private fun pairsIn(ratio: Double, perBand: Int, bands: List<Int>) = EvidenceTestSupport.pairsIn(ratio, perBand, bands)

    private fun assertNoProposal(r: AutoMatchRefinedEngine.Result) {
        assertEquals(AutoMatchRefinedEngine.Mode.POLISH, r.mode)
        assertEquals(AutoMatchRefinedEngine.REASON_NO_EVIDENCE, r.reason)
        assertEquals("sem evidência suficiente", r.message)
        assertEquals(r.currentRaw, r.refinedRaw)
        assertTrue(r.origins.all { it == AutoMatchRefinedEngine.Origin.HELD })
    }

    @Test
    fun `razao GNV gasolina implausivel em faixas da conducao nao gera proposta`() {
        for (ratio in listOf(0.5, 2.0)) {
            val r = refine(pairsIn(ratio, 8, listOf(2, 3)))
            assertNoProposal(r)
            assertEquals("razão $ratio: as 2 faixas são outlier", 2, r.telemetryOutlierBands)
        }
        // Mesmo com 3+ faixas de 8 pares, se todas são implausíveis não há proposta.
        assertNoProposal(refine(pairsIn(2.0, 12, listOf(1, 2, 3, 4))))
    }

    @Test
    fun `conducao exige duas faixas distintas com oito pares plausiveis cada`() {
        assertNoProposal(refine(pairsIn(1.1, 80, listOf(4))))               // só 1 faixa, mesmo com 80 pares
        assertNoProposal(refine(pairsIn(1.1, 7, listOf(2, 3, 4))))          // 3 faixas, mas 7 pares cada
        assertNoProposal(refine(pairsIn(1.1, 2, listOf(0, 1, 2, 3, 4))))    // faixas finas (2 pares) não são evidência
        val two = refine(pairsIn(1.1, 8, listOf(3, 4)))                    // 2 faixas bastam (valor da Platina)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, two.mode)
        assertEquals(16, two.telemetryPairsUsed)
        val ok = refine(pairsIn(1.1, 8, listOf(2, 3, 4)))
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, ok.mode)
        assertTrue(ok.telemetryOnly)
        assertTrue(ok.refinedRaw != ok.currentRaw)
        assertEquals(24, ok.telemetryPairsUsed)
    }

    @Test
    fun `uma faixa implausivel e descartada e as outras seguem valendo`() {
        val pairs = pairsIn(1.1, 8, listOf(1, 2, 3, 4)) + pairsIn(2.0, 20, listOf(0))
        val r = refine(pairs)
        assertEquals(1, r.telemetryOutlierBands)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.mode)
        assertEquals(32, r.telemetryPairsUsed)
    }

    @Test
    fun `sem nenhuma evidencia a curva fica exatamente como esta`() {
        assertNoProposal(refine(emptyList()))
        assertNoProposal(refine(emptyList(), IntArray(30) { 14000 + it * 120 }))
    }

    @Test
    fun `proposta limitada a faixa do AutoMatch nativo e K fora da faixa sa e rejeitado`() {
        // Evidência pedindo mais que o teto sobre uma curva em 1,15: o novo K para em 1,20.
        val r = refine(pairsIn(1.18, 12, listOf(1, 2, 3, 4)), flatK((1.15 * 16384).toInt()))
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r.mode)
        assertTrue(r.refinedRaw.all { it in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL })
        // K = 4,0 (valor de fábrica/lixo) não é "mantido": a entrada é rejeitada.
        val insane = refine(pairsIn(1.1, 12, listOf(1, 2, 3, 4)), flatK(65535))
        assertEquals(AutoMatchRefinedEngine.Mode.UNAVAILABLE, insane.mode)
        assertEquals("MUL_ACT_FORA_DA_FAIXA", insane.reason)
        assertEquals(30, insane.outOfRangePoints)
        // K = 1,50 está fora da faixa nativa e não entra nela em ±15%: nunca é alterado, e fica sinalizado.
        val high = refine(pairsIn(1.1, 12, listOf(1, 2, 3, 4)), flatK((1.5 * 16384).toInt()))
        assertEquals(high.currentRaw, high.refinedRaw)
        assertEquals(30, high.outOfRangePoints)
        // K abaixo do piso nativo: só muda se o passo o leva para dentro de [0,75; 1,20].
        val low = refine(pairsIn(0.95, 12, listOf(1, 2, 3, 4)), flatK((0.6 * 16384).toInt()))
        assertTrue(low.refinedRaw.indices.all { low.refinedRaw[it] == low.currentRaw[it] || low.refinedRaw[it] >= 12288 })
    }

    @Test
    fun `MAP bruto com bit 0x8000 e evidencia invalida contada e nao zero alvos silencioso`() {
        val time = IntArray(18)
        val map = IntArray(18)
        val counts = IntArray(18)
        time[5] = 5 * 512; map[5] = 0x8000 or 700; counts[5] = 6
        time[6] = 6 * 512; map[6] = 0xFFFF; counts[6] = 6
        val r = AutoMatchRefinedEngine.refine(
            AutoMatchRefinedEngine.Input(axisRaw, flatK(), time, map, counts, time, map, counts),
        )
        assertEquals(4, r.invalidEvidenceBands) // 2 faixas × (gasolina + GNV)
        assertTrue(r.targets.isEmpty())
        assertNoProposal(r)
    }

    @Test
    fun `faixa nativa fina nao conta como evidencia nem muda a curva`() {
        // 8 faixas comuns, todas com 2 amostras (< 3): sem evidência.
        val time = IntArray(18)
        val map = IntArray(18)
        val gas = IntArray(18)
        val counts = IntArray(18)
        for (b in 0 until 8) {
            time[b] = ((3.0 + b) * 512).toInt()
            map[b] = 400 + b * 60
            gas[b] = ((3.0 + b) * 512 * 1.1).toInt()
            counts[b] = 2
        }
        val r = AutoMatchRefinedEngine.refine(
            AutoMatchRefinedEngine.Input(axisRaw, flatK(), time, map, counts, gas, map, counts),
        )
        assertEquals(16, r.thinBandsIgnored)
        assertEquals(0, r.matureCommonPoints)
        assertNoProposal(r)
    }

    @Test
    fun `snapshot incoerente no tempo e ignorado pelo refino`() {
        val real = RealSessionReplaySupport.fixture(AUTOMATCH)
        val incoherent = RealSessionReplaySupport.snapshot(real, 634)
        assertFalse(incoherent.getBoolean("temporalCoherent"))
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(incoherent)
        assertFalse(analysis.getBoolean("available"))
        assertEquals("SNAPSHOT_INCOERENTE_NO_TEMPO", analysis.getString("reason"))
        // `partial` é true em todo snapshot real e NÃO é critério.
        val coherent = RealSessionReplaySupport.snapshot(real, 1716)
        assertTrue(coherent.getBoolean("partial"))
        assertTrue(AutoMatchSnapshotAnalysis.analyzeRefined(coherent).getBoolean("available"))
    }

    /** Sessão inteira reproduzida no livro, como o piloto faz: os pares da condução entram no refino. */
    private fun drivingPairs(name: String): List<Pair<Double, Double>> {
        val ledger = EquivalenceLedger(null)
        RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name)).forEach {
            ledger.accept(RealSessionReplaySupport.ledgerFrame(it))
        }
        return ledger.drivingPairs().map { it.petrolRefMs to it.gasPetrolMs }
    }

    @Test
    fun `sessoes reais - toda proposta respeita faixa nativa, faixas maduras e cobertura`() {
        val report = StringBuilder()
        for (name in listOf(GNV_ONLY, AUTOMATCH, REFERENCE)) {
            val root = RealSessionReplaySupport.fixture(name)
            val pairs = drivingPairs(name)
            var proposals = 0
            var total = 0
            for (snapshot in RealSessionReplaySupport.snapshots(root)) {
                if (RealSessionReplaySupport.rawValues(snapshot, "MUL_ACT") == null) continue
                total++
                val a = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, pairs)
                val seq = snapshot.getInt("sequence")
                if (!snapshot.getBoolean("temporalCoherent")) {
                    assertFalse("$name#$seq incoerente é ignorado", a.getBoolean("available"))
                    continue
                }
                if (a.getString("refinementMode") == "POLISH") {
                    assertEquals("$name#$seq sem evidência não há proposta", 0, a.getInt("changedCount"))
                    assertEquals("SEM_EVIDENCIA_SUFICIENTE", a.getString("reason"))
                    assertFalse(a.getBoolean("proposalAvailable"))
                    continue
                }
                val points = a.getJSONArray("points")
                var changed = 0
                for (i in 0 until points.length()) {
                    val p = points.getJSONObject(i)
                    val cur = p.getInt("currentRaw")
                    val new = p.getInt("calculatedRaw")
                    if (new != cur) {
                        changed++
                        assertTrue(
                            "$name#$seq ponto $i novo K ${new / 16384.0} fora de [0,75; 1,20]",
                            new in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL,
                        )
                    }
                }
                if (changed > 0) proposals++
                // Alvos nativos só de faixas com ≥ 3 amostras (peso ≥ 3/6).
                val targets = a.getJSONArray("targets")
                for (i in 0 until targets.length()) {
                    assertTrue(
                        "$name#$seq alvo com peso ${targets.getJSONObject(i).getDouble("weight")}",
                        targets.getJSONObject(i).getDouble("weight") >= 3.0 / 6.0 - 1e-9,
                    )
                }
                if (a.getBoolean("telemetryOnly")) {
                    val usable = pairs.filter { it.first >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }
                    val bandsWithEight = EquivalenceLedger.BANDS.count { (lo, hi) ->
                        usable.count { it.first >= lo && it.first < hi } >= 8
                    }
                    assertTrue("$name#$seq condução-só com $bandsWithEight faixas", bandsWithEight >= 3)
                    assertEquals(0, a.getInt("telemetryOutlierBands"))
                } else {
                    assertTrue(a.getInt("matureCommonPoints") >= AutoMatchRefinedEngine.MIN_COMMON_MATURE)
                }
            }
            report.append("$name proposals=$proposals/$total ")
        }
        println("PROPOSALS $report")
    }

    @Test
    fun `sessao de referencia e do AutoMatch ainda produzem proposta onde a evidencia e real`() {
        val ref = AutoMatchSnapshotAnalysis.analyzeRefined(
            RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(REFERENCE), 95),
            drivingPairs(REFERENCE),
        )
        assertTrue(ref.getInt("changedCount") > 0)
        val am = AutoMatchSnapshotAnalysis.analyzeRefined(
            RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(AUTOMATCH), 2262),
            drivingPairs(AUTOMATCH),
        )
        assertEquals("EQUIVALENCE", am.getString("refinementMode"))
        assertTrue(am.getInt("changedCount") > 0)
    }
}
