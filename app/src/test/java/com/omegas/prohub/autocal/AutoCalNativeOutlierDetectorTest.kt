package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalNativeOutlierDetectorTest {
    private fun timeRaw(): IntArray = IntArray(18) { i -> ((3.0 + i * 0.35) * AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS).toInt() }
    private fun smoothMapRaw(): IntArray = IntArray(18) { i -> ((0.18 + i * 0.035) * AutoMatchRefinedEngine.MAP_COUNTS_PER_BAR).toInt() }
    private fun fullCounts(): IntArray = IntArray(18) { AutoMatchRefinedEngine.BAND_FULL_COUNT }

    @Test
    fun `ponto GNV maduro isolado adquirido em lenta vira candidato a readquirir`() {
        val maps = smoothMapRaw()
        maps[8] = (0.16 * AutoMatchRefinedEngine.MAP_COUNTS_PER_BAR).toInt()
        val evidence = listOf(
            AutoCalNativeOutlierDetector.MaturityEvidence(
                bandIndex = 8,
                rpm = 870,
                correlationConfidence = 0.92,
                rpmConfidence = 0.88,
                matchedFrames = 4,
            ),
        )

        val candidates = AutoCalNativeOutlierDetector.detectGas(
            timeRaw = timeRaw(),
            mapRaw = maps,
            counts = fullCounts(),
            evidence = evidence,
        )

        assertEquals(1, candidates.size)
        assertEquals(AutoCalPointDeleteProtocol.Fuel.GAS, candidates.single().target.fuel)
        assertEquals(8, candidates.single().target.index)
        assertEquals("ISOLATED_NATIVE_POINT_AT_IDLE", candidates.single().reason)
        assertTrue(candidates.single().robustResidual > 0.0)
    }

    @Test
    fun `mesmo desvio em regime de conducao nao e apagado automaticamente`() {
        val maps = smoothMapRaw()
        maps[8] = (0.16 * AutoMatchRefinedEngine.MAP_COUNTS_PER_BAR).toInt()
        val evidence = listOf(
            AutoCalNativeOutlierDetector.MaturityEvidence(
                bandIndex = 8,
                rpm = 1650,
                correlationConfidence = 0.95,
                rpmConfidence = 0.90,
                matchedFrames = 5,
            ),
        )

        assertTrue(
            AutoCalNativeOutlierDetector.detectGas(timeRaw(), maps, fullCounts(), evidence).isEmpty(),
        )
    }

    @Test
    fun `baixa confianca ou faixa ainda fina nao autoriza mutacao`() {
        val maps = smoothMapRaw()
        maps[8] = (0.16 * AutoMatchRefinedEngine.MAP_COUNTS_PER_BAR).toInt()
        val thin = fullCounts().also { it[8] = AutoMatchRefinedEngine.BAND_MATURE_COUNT - 1 }

        assertTrue(
            AutoCalNativeOutlierDetector.detectGas(
                timeRaw(), maps, thin,
                listOf(AutoCalNativeOutlierDetector.MaturityEvidence(8, 850, 0.95, 0.95, 4)),
            ).isEmpty(),
        )
        assertTrue(
            AutoCalNativeOutlierDetector.detectGas(
                timeRaw(), maps, fullCounts(),
                listOf(AutoCalNativeOutlierDetector.MaturityEvidence(8, 850, 0.30, 0.90, 4)),
            ).isEmpty(),
        )
        assertTrue(
            AutoCalNativeOutlierDetector.detectGas(
                timeRaw(), maps, fullCounts(),
                listOf(AutoCalNativeOutlierDetector.MaturityEvidence(8, 850, 0.95, 0.90, 1)),
            ).isEmpty(),
        )
    }

    @Test
    fun `curva local coerente em lenta permanece mesmo com rpm baixo`() {
        val evidence = listOf(
            AutoCalNativeOutlierDetector.MaturityEvidence(
                bandIndex = 8,
                rpm = 880,
                correlationConfidence = 0.94,
                rpmConfidence = 0.91,
                matchedFrames = 4,
            ),
        )

        assertTrue(
            AutoCalNativeOutlierDetector.detectGas(
                timeRaw = timeRaw(),
                mapRaw = smoothMapRaw(),
                counts = fullCounts(),
                evidence = evidence,
            ).isEmpty(),
        )
    }
}
