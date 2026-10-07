package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Portão de plausibilidade da razão GNV/gasolina por faixa: a própria ECU mede 1,013 (IQR 0,975–1,062) e o
 * AutoMatch nativo trabalha em [0,75; 1,20]. Faixa com mediana fora de [0,80; 1,25] é erro de medida.
 */
class RatioGateTest {
    /** 12 pares espalhados pela faixa 4,5–6,0 ms (terços cobertos), todos com a mesma razão. */
    private fun band(ratio: Double) = (0 until 12).map { i -> val tp = 4.55 + i * 0.12; tp to tp * ratio }

    @Test
    fun `limites do portao sao 0,80 e 1,25`() {
        assertEquals(0.80, AutoMatchRefinedEngine.TELEMETRY_RATIO_MIN, 0.0)
        assertEquals(1.25, AutoMatchRefinedEngine.TELEMETRY_RATIO_MAX, 0.0)
    }

    @Test
    fun `faixa com razao 0,7 ou 1,4 e descartada como erro de medida`() {
        for (r in listOf(0.70, 0.78, 1.30, 1.40)) {
            val p = AutoMatchRefinedEngine.plausibleIndices(band(r))
            assertEquals("razão $r", 1, p.outliers)
            assertEquals("razão $r", 0, p.kept.size)
        }
        for (r in listOf(0.85, 1.0, 1.2)) {
            val p = AutoMatchRefinedEngine.plausibleIndices(band(r))
            assertEquals("razão $r", 0, p.outliers)
            assertEquals("razão $r", 12, p.kept.size)
        }
    }

    @Test
    fun `bins finos com razao 1,4 nao viram evidencia`() {
        val pairs = (0 until 30).map { i ->
            val tp = 4.55 + (i % 15) * 0.1
            EquivalenceLedger.EvidencePair(tp, tp * 1.4, 2_000.0, episode = 1)
        }
        val gate = FineBins.gate(FineBins.aggregate(pairs))
        assertEquals(0, gate.valid.count { it })
        assertEquals(true, gate.outlierBands > 0)
    }
}
