package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.RealSessionReplaySupport.AUTOMATCH
import com.omegas.prohub.autocal.RealSessionReplaySupport.GNV_ONLY
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import com.omegas.prohub.ecu.KFactorProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/**
 * Lote E: histerese da proposta (E2), ganho decrescente (E1), cobertura por episódio (E3/E4).
 * Paridade Python: tests/test_refined_autocal_hysteresis.py (mesmos números, sessões reais).
 */
class AutoMatchHysteresisTest {
    private val axisRaw = IntArray(30) { (KFactorProtocol.OBSERVED_PETROL_AXIS_MS[it] * 512.0).toInt() }
    private fun flatK() = IntArray(30) { 16384 }
    private val centers = listOf(3.75, 5.25, 6.75, 8.25, 10.5)

    private fun refine(
        pairs: List<Pair<Double, Double>>,
        episodes: List<Int> = emptyList(),
        hold: Double = 0.0,
    ) = AutoMatchRefinedEngine.refine(
        AutoMatchRefinedEngine.Input(axisRaw, flatK(), null, null, null, null, null, null, pairs, null, episodes, hold),
    )

    private fun pairsIn(ratio: Double, perBand: Int, bands: List<Int>) =
        bands.flatMap { b -> List(perBand) { centers[b] to centers[b] * ratio } }

    // ------------------------------------------------------------------ E2: histerese

    @Test
    fun `passo proposto menor que o limiar e mantido e passo grande continua valendo`() {
        val small = pairsIn(1.02, 12, listOf(1, 2, 3, 4))
        val free = refine(small)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, free.mode)
        assertTrue("sem histerese o ruído de 2% vira proposta", free.refinedRaw != free.currentRaw)
        val held = refine(small, hold = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG)
        assertEquals("com histerese nada muda", held.currentRaw, held.refinedRaw)
        assertTrue(held.origins.all { it == AutoMatchRefinedEngine.Origin.HELD })
        val big = refine(pairsIn(1.10, 12, listOf(1, 2, 3, 4)), hold = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG)
        assertTrue("10% é correção de verdade", big.refinedRaw != big.currentRaw)
        // Ponto mantido é exatamente o gravado; ponto proposto respeita a faixa nativa.
        big.refinedRaw.forEachIndexed { j, v ->
            assertTrue(v == big.currentRaw[j] || v in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
        }
    }

    // ------------------------------------------------------------------ E3: episódios

    @Test
    fun `faixa com oito pares de um unico trecho nao puxa proposta`() {
        val pairs = pairsIn(1.10, 9, listOf(2, 3, 4))
        val oneEpisode = refine(pairs, List(pairs.size) { 0 })
        assertEquals(AutoMatchRefinedEngine.Mode.POLISH, oneEpisode.mode)
        assertEquals(oneEpisode.currentRaw, oneEpisode.refinedRaw)
        val twoEpisodes = refine(pairs, List(pairs.size) { it % 2 })
        assertEquals(AutoMatchRefinedEngine.Mode.POLISH, twoEpisodes.mode)
        val threeEpisodes = refine(pairs, List(pairs.size) { it % 3 })
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, threeEpisodes.mode)
        assertTrue(threeEpisodes.telemetryOnly)
        // Episódios desconhecidos (lista vazia) não ligam o portão: comportamento anterior.
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, refine(pairs).mode)
    }

    private fun drive(ledger: EquivalenceLedger, fuel: String, ms: Double, start: Long, n: Int): Long {
        var t = start
        repeat(n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, 2000.0, 0.60, ms)); t += 280 }
        return t
    }

    @Test
    fun `o livro numera os episodios pela lacuna de 3 s e o indice guia onde dirigir`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 5.0, 0, 20)
        repeat(3) { t = drive(ledger, "GNV", 5.5, t + 5_000, 6) }
        val pairs = ledger.drivingPairs()
        assertEquals(3, pairs.map { it.episode }.toSet().size)
        assertTrue(pairs.all { it.episode >= 0 && it.map > 0.59 && it.map < 0.61 })
        val band = ledger.index().getJSONArray("bands").getJSONObject(1)
        assertEquals(3, band.getInt("episodes"))
        assertTrue(band.getInt("samples") >= 8)

        // Um único trecho longo tem pares de sobra, mas só 1 episódio: o índice diz onde dirigir.
        val single = EquivalenceLedger(null)
        var u = drive(single, "GASOLINA", 5.0, 0, 20)
        drive(single, "GNV", 5.5, u + 5_000, 40)
        val guidance = single.index().getString("coverageGuidance")
        assertTrue(guidance, guidance.contains("Falta dado na faixa 4,5–6,0 ms"))
        assertTrue(guidance, guidance.contains("0,60"))
        assertTrue(guidance, guidance.contains("1 de 3 trechos"))
    }

    @Test
    fun `episodios sobrevivem a reabrir o app`() {
        val dir = java.nio.file.Files.createTempDirectory("ledger-episodes").toFile()
        try {
            val file = java.io.File(dir, "ledger.json")
            val ledger = EquivalenceLedger(file)
            var t = drive(ledger, "GASOLINA", 5.0, 0, 20)
            repeat(2) { t = drive(ledger, "GNV", 5.5, t + 5_000, 6) }
            ledger.flush()
            val before = ledger.drivingPairs().map { it.episode }
            assertEquals(2, before.toSet().size)
            assertEquals(before, EquivalenceLedger(file).drivingPairs().map { it.episode })
        } finally { dir.deleteRecursively() }
    }

    // ------------------------------------------------------------------ sessões reais: reversões

    /** (reversões de sinal, passos propostos, snapshots com proposta) ao longo dos snapshots consecutivos. */
    private fun proposalSequence(name: String, hold: Double, gain: Boolean, episodes: Boolean): Triple<Int, Int, Int> {
        val root = RealSessionReplaySupport.fixture(name)
        val frames = RealSessionReplaySupport.telemetry(root).sortedBy { it.t }
        val ledger = EquivalenceLedger(null)
        val passes = IntArray(30)
        val lastSign = IntArray(30)
        var fed = 0
        var reversals = 0
        var steps = 0
        var proposals = 0
        for (snapshot in RealSessionReplaySupport.snapshots(root)) {
            val at = snapshot.getLong("capturedAtMs")
            while (fed < frames.size && frames[fed].t <= at) ledger.accept(RealSessionReplaySupport.ledgerFrame(frames[fed++]))
            if (!snapshot.optBoolean("temporalCoherent", true)) continue
            val axis = RealSessionReplaySupport.rawValues(snapshot, "PETR_INJ_TBP") ?: continue
            val mul = RealSessionReplaySupport.rawValues(snapshot, "MUL_ACT") ?: continue
            val driving = ledger.drivingPairs()
            val scale = if (gain) DoubleArray(30) { AutoMatchRefinedEngine.passGain(passes[it]) } else null
            val result = AutoMatchRefinedEngine.refine(
                AutoMatchRefinedEngine.Input(
                    axis, mul,
                    RealSessionReplaySupport.rawValues(snapshot, "PETR_INJ_TBUF"), RealSessionReplaySupport.rawValues(snapshot, "MNFLD_PRESS_BUF"),
                    RealSessionReplaySupport.rawValues(snapshot, "NUM_BUF_UPD_PETR"),
                    RealSessionReplaySupport.rawValues(snapshot, "PETR_INJ_TBUF_GAS"), RealSessionReplaySupport.rawValues(snapshot, "MNFLD_PRESS_BUF_GAS"),
                    RealSessionReplaySupport.rawValues(snapshot, "NUM_BUF_UPD_GAS"),
                    driving.map { it.petrolRefMs to it.gasPetrolMs }, scale,
                    if (episodes) driving.map { it.episode } else emptyList(), hold,
                ),
            )
            if (result.mode != AutoMatchRefinedEngine.Mode.EQUIVALENCE) continue
            var changed = false
            for (j in 0 until 30) {
                if (result.origins[j] == AutoMatchRefinedEngine.Origin.HELD || result.refinedRaw[j] == result.currentRaw[j]) continue
                changed = true
                steps++
                passes[j]++
                val sign = if (result.refinedRaw[j] > result.currentRaw[j]) 1 else -1
                if (lastSign[j] != 0 && lastSign[j] != sign) reversals++
                lastSign[j] = sign
            }
            if (changed) proposals++
        }
        return Triple(reversals, steps, proposals)
    }

    @Test
    fun `histerese e ganho decrescente nao aumentam as reversoes de sinal nas sessoes reais`() {
        val hold = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG
        var before = 0
        var after = 0
        for (name in listOf(AUTOMATCH, REFERENCE, GNV_ONLY)) {
            val base = proposalSequence(name, 0.0, gain = false, episodes = false)
            val e12 = proposalSequence(name, hold, gain = true, episodes = false)
            println("E_REVERSOES $name antes=${base.first} (passos ${base.second}) depois_E1E2=${e12.first} (passos ${e12.second})")
            assertTrue("$name: ${e12.first} > ${base.first}", e12.first <= base.first)
            before += base.first
            after += e12.first
        }
        println("E_REVERSOES total antes=$before depois=$after")
        assertTrue(after <= before)
    }
}
