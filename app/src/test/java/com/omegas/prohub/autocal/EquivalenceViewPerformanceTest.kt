package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.RealSessionReplaySupport.AUTOMATCH
import com.omegas.prohub.autocal.RealSessionReplaySupport.GNV_ONLY
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prioridade 4: o custo de uma consulta ao Refino, medido no CI com o corpus real inteiro
 * (as três sessões do proprietário no mesmo livro de pontos).
 *
 * - consulta fria (cada vez com o livro mudado): mediana abaixo de 30 ms;
 * - consulta pronta (cache em segundo plano): o que a WebView paga, praticamente zero.
 * Os números vão para build/perf/equivalence-view.txt e para o log do CI.
 */
class EquivalenceViewPerformanceTest {
    private fun corpus(): EquivalenceLedger {
        val ledger = EquivalenceLedger(null)
        var offset = 0L
        for (name in listOf(GNV_ONLY, AUTOMATCH, REFERENCE)) {
            val frames = RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name))
            frames.forEach { ledger.accept(RealSessionReplaySupport.ledgerFrame(it).copy(t = it.t + offset)) }
            offset += (frames.last().t - frames.first().t) + 60_000L
        }
        return ledger
    }

    @Test
    fun `consulta fria do Refino com o corpus real fica abaixo de 30 ms e a pronta custa quase nada`() {
        val ledger = corpus()
        val journal = RefinementJournal(null)
        val pilot = RefinementAutopilot(null)
        val stalls = StallWatch(null)
        RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(REFERENCE))
            .forEach { stalls.accept(RealSessionReplaySupport.stallFrame(it)) }
        pilot.observe(true, null, null, ledger.index(), journal.json(), 0)
        assertTrue("o corpus precisa ter volume real", ledger.index().getInt("petrolObservations") + ledger.index().getInt("gasObservations") > 500)

        var t = 9_000_000_000_000L
        fun touch() {
            // Três quadros estáveis novos: o livro muda e o cache do índice é descartado.
            repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 1_500.0, 0.50, 4.0)); t += 280 }
        }
        val samples = ArrayList<Double>()
        repeat(45) { i ->
            touch()
            val start = System.nanoTime()
            val json = EquivalenceView.build(ledger, journal, pilot, stalls).toString()
            val ms = (System.nanoTime() - start) / 1e6
            if (i >= 15) samples += ms // 15 de aquecimento do JIT
            assertTrue(json.length > 100)
        }
        samples.sort()
        val median = samples[samples.size / 2]
        val p95 = samples[(samples.size * 95) / 100]

        // Consulta pronta: 20 mil chamadas da tela, o cálculo roda uma única vez.
        var runs = 0
        val memo = BackgroundMemo(2_000L, 6_000L) { runs++; EquivalenceView.build(ledger, journal, pilot, stalls).toString() }
        memo.get()
        val readyStart = System.nanoTime()
        repeat(20_000) { memo.get() }
        val readyMs = (System.nanoTime() - readyStart) / 1e6 / 20_000

        val report = "PERF equivalence_cold_median_ms=%.2f cold_p95_ms=%.2f ready_per_call_ms=%.5f runs=%d obs_petrol=%d obs_gas=%d"
            .format(java.util.Locale.ROOT, median, p95, readyMs, runs,
                ledger.index().getInt("petrolObservations"), ledger.index().getInt("gasObservations"))
        println(report)
        File("build/perf").apply { mkdirs() }.resolve("equivalence-view.txt").writeText(report + "\n")

        assertEquals(1, runs)
        assertTrue("consulta fria mediana %.2f ms (limite 30)".format(java.util.Locale.ROOT, median), median < 30.0)
        assertTrue("consulta fria p95 %.2f ms (limite 100)".format(java.util.Locale.ROOT, p95), p95 < 100.0)
        assertTrue("consulta pronta %.5f ms por chamada".format(java.util.Locale.ROOT, readyMs), readyMs < 0.5)
    }
}
