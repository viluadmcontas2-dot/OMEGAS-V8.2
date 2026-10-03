package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Lote H: 54 bins finos → 18 faixas da ECU + intervalos entre os pontos da ECU. Paridade Python:
 * tests/test_fine_bins.py (mesmas propriedades) e a paridade de números em test_refined_autocal_kotlin_parity.py.
 */
class FineBinsTest {
    private val axisRaw = IntArray(30) { (KFactorProtocol.OBSERVED_PETROL_AXIS_MS[it] * 512.0).toInt() }
    private fun flatK() = IntArray(30) { 16384 }

    private fun pair(tp: Double, ratio: Double, episode: Int, ecu: Boolean = false) =
        EquivalenceLedger.EvidencePair(tp, tp * ratio, 2000.0, ecu, episode, 0.5)

    private fun pairsAt(centers: List<Double>, ratio: Double, episodes: Int, perEpisode: Int) =
        centers.flatMap { c -> (0 until episodes).flatMap { e -> List(perEpisode) { pair(c, ratio, e) } } }

    private fun refine(bins: List<FineBins.Bin>?, hold: Double = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG) = AutoMatchRefinedEngine.refine(
        AutoMatchRefinedEngine.Input(axisRaw, flatK(), null, null, null, null, null, null, holdMinStepLog = hold, fineBins = bins),
    )

    @Test
    fun `54 bins em log com exatamente 3 por faixa e indice por borda`() {
        assertEquals(55, FineBins.EDGES.size)
        assertEquals(3.0, FineBins.EDGES.first(), 0.0)
        assertEquals(12.0, FineBins.EDGES.last(), 0.0)
        for (k in 0 until 54) {
            assertTrue(FineBins.EDGES[k + 1] > FineBins.EDGES[k])
            assertEquals(k, FineBins.fineIndex(FineBins.EDGES[k]))
        }
        assertEquals(53, FineBins.fineIndex(11.9999))
        assertNull(FineBins.fineIndex(2.99))
        assertNull(FineBins.fineIndex(12.0))
        assertEquals(18, FineBins.bandOfBin(53) + 1)
    }

    @Test
    fun `bands18 agrega os bins por amostras e betweenBands tem 2 bins finos em cada intervalo`() {
        val bins = FineBins.aggregate(pairsAt(listOf(5.0, 5.7), 1.10, 4, 3))
        val bands = FineBins.bands18Json(bins)
        assertEquals(18, bands.length())
        val withData = (0 until 18).map { bands.getJSONObject(it) }.filter { it.getInt("samples") > 0 }
        assertTrue(withData.isNotEmpty())
        withData.forEach {
            assertEquals(1.10, it.getDouble("ratio"), 1e-4)
            assertEquals(3, it.getJSONArray("fineBins").length())
            assertTrue(it.getDouble("confidence") in 0.0..1.0)
        }
        (0 until 18).map { bands.getJSONObject(it) }.filter { it.getInt("samples") == 0 }.forEach {
            assertTrue(it.isNull("ratio")); assertEquals(0.0, it.getDouble("confidence"), 0.0)
        }
        val between = FineBins.betweenJson(bins)
        val gaps = (0 until between.length()).map { between.getJSONObject(it) }.filter { it.getString("kind") == "gap" }
        assertEquals(17, gaps.size)
        assertTrue(gaps.all { it.getJSONArray("fineBins").length() == 2 })
        assertTrue((0 until between.length()).map { between.getJSONObject(it) }.all { it.getString("kind") == "gap" || it.getInt("samples") > 0 })
    }

    @Test
    fun `o indice do livro traz bands18 e betweenBands sem mexer nas bandas grossas`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(10) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0, 0.60, 5.0)); t += 280 }
        t += 5_000
        repeat(10) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2000.0, 0.60, 5.5)); t += 280 }
        val index = ledger.index()
        assertEquals(5, index.getJSONArray("bands").length())
        assertEquals(18, index.getJSONArray("bands18").length())
        val band = (0 until 18).map { index.getJSONArray("bands18").getJSONObject(it) }.first { it.getInt("samples") > 0 }
        assertEquals(8, band.getInt("samples"))
        assertEquals(1.1, band.getDouble("ratio"), 1e-4)
        assertTrue(index.getJSONArray("betweenBands").length() >= 17)
        assertEquals(54, index.getJSONObject("fineGrid").getInt("count"))
        assertEquals(54, ledger.fineBins().size)
    }

    @Test
    fun `mesmos quadros na mesma ordem dao o mesmo bands18 e o arquivo salvo e recarregado e identico`() {
        fun feed(ledger: EquivalenceLedger) {
            var t = 0L
            for (round in 0 until 4) {
                repeat(10) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0 + round * 40, 0.60, 5.0 + round * 0.1)); t += 280 }
                t += 5_000
                repeat(10) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2000.0 + round * 40, 0.60, 5.5 + round * 0.1)); t += 280 }
                t += 5_000
            }
        }
        val a = EquivalenceLedger(null)
        val b = EquivalenceLedger(null)
        feed(a); feed(b)
        assertEquals(a.index().getJSONArray("bands18").toString(), b.index().getJSONArray("bands18").toString())

        val dir = Files.createTempDirectory("fine-bins").toFile()
        val file = File(dir, "ledger.json")
        var now = 0L
        val clock = { now += 120_000L; now }
        val first = EquivalenceLedger(file, clock)
        first.alignCurve("fp")
        feed(first)
        first.flush()
        val text1 = file.readText()
        val second = EquivalenceLedger(file, clock)
        assertEquals(first.index().getJSONArray("bands18").toString(), second.index().getJSONArray("bands18").toString())
        second.alignCurve("fp")
        second.flush()
        assertEquals(text1, file.readText())
        dir.deleteRecursively()
    }

    @Test
    fun `arquivo antigo ou desconhecido recarrega vazio sem derrubar`() {
        val dir = Files.createTempDirectory("fine-bins-old").toFile()
        val file = File(dir, "ledger.json")
        file.writeText("{\"format\":\"omegas-equivalence-ledger-v0\",\"petrol\":[[1,2,3,4]]}")
        val ledger = EquivalenceLedger(file)
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        assertEquals(18, ledger.index().getJSONArray("bands18").length())
        file.writeText("não é json")
        assertEquals(0, EquivalenceLedger(file).fineBins().sumOf { it.n })
        dir.deleteRecursively()
    }

    @Test
    fun `reservatorio limita a memoria mas nao a contagem`() {
        val bins = FineBins.aggregate(List(500) { pair(5.0, 1.10, it) })
        val bin = bins[FineBins.fineIndex(5.0)!!]
        assertEquals(500, bin.n)
        assertEquals(FineBins.RESERVOIR, bin.episodeIds.size)
        assertEquals(1.10, bin.ratio!!, 1e-9)
    }

    @Test
    fun `bin fino e faixa com poucos trechos nao sao evidencia`() {
        val centers = listOf(3.75, 5.25, 6.75, 8.25)
        val few = refine(FineBins.aggregate(pairsAt(centers, 1.12, 2, 8)))
        assertNotEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, few.mode)
        val thin = refine(FineBins.aggregate(centers.mapIndexed { i, c -> pair(c, 1.12, i) }))
        assertNotEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, thin.mode)
        val absurd = refine(FineBins.aggregate(pairsAt(centers, 2.5, 4, 3)))
        assertNotEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, absurd.mode)
    }

    @Test
    fun `trechos suficientes propoem dentro da faixa nativa e o mesmo snapshot duas vezes nao muda a proposta`() {
        val bins = FineBins.aggregate(pairsAt(listOf(3.75, 5.25, 6.75, 8.25), 1.12, 4, 3))
        val result = refine(bins)
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, result.mode)
        assertTrue(result.refinedRaw != result.currentRaw)
        result.refinedRaw.forEachIndexed { j, v ->
            assertTrue(v == result.currentRaw[j] || v in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
        }
        assertTrue(result.metricsAfter!!.maxElasticity <= AutoMatchRefinedEngine.E_MAX + 0.01)
        assertEquals(result.refinedRaw, refine(FineBins.aggregate(pairsAt(listOf(3.75, 5.25, 6.75, 8.25), 1.12, 4, 3))).refinedRaw)
        assertEquals(result.refinedRaw, refine(bins).refinedRaw)
    }

    @Test
    fun `confianca fica em 0 a 1 e cresce com trechos e cai com dispersao`() {
        assertEquals(0.0, FineBins.confidence(0, null, null), 0.0)
        val few = FineBins.confidence(24, 2, 0.03)
        val many = FineBins.confidence(24, 6, 0.03)
        val noisy = FineBins.confidence(24, 6, 0.30)
        assertTrue(many > few && many > noisy)
        assertTrue(many <= 1.0 && noisy >= 0.0)
        assertFalse(abs(exp(ln(1.1)) - 1.1) > 1e-12)
    }
}
