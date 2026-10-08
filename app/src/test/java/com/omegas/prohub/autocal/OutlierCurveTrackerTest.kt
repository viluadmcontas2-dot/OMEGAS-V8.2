package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classe 1: detecção pura de "ponto fora da curva" por combustível (spec 2026-10-07 rev2). Candidato = fora da curva em
 * DUAS leituras de instantes distintos (conteúdo igual conta; o mesmo instante repetido não).
 */
class OutlierCurveTrackerTest {
    private val tracker = OutlierCurveTracker()
    private var at = 1_000L

    @Test
    fun `curva sem outlier nao tem candidato`() {
        tracker.observe(Fuel.GAS, reading())
        tracker.observe(Fuel.PETROL, reading())
        assertTrue(tracker.candidates(Fuel.GAS).isEmpty())
        assertTrue(tracker.candidates(Fuel.PETROL).isEmpty())
    }

    @Test
    fun `banda rejeitada pelo ajuste robusto do Refino e fora da curva por combustivel`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        assertTrue("uma leitura só não basta", tracker.candidates(Fuel.GAS).isEmpty())
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.observe(Fuel.PETROL, reading())
        tracker.observe(Fuel.PETROL, reading())
        val gas = tracker.candidates(Fuel.GAS)
        assertEquals(listOf(6), gas.map { it.band })
        assertEquals(5, gas.single().counter)
        assertTrue(tracker.candidates(Fuel.PETROL).isEmpty())
    }

    @Test
    fun `contador zero MAP zero e bandas acima de 15 nao entram`() {
        val r = reading(outlierBand = 6)
        r.counters[6] = 0
        tracker.observe(Fuel.GAS, r)
        tracker.observe(Fuel.GAS, reading(outlierBand = 6).also { it.counters[6] = 0 })
        assertTrue(tracker.candidates(Fuel.GAS).isEmpty())
    }

    @Test
    fun `o mesmo instante repetido nao e leitura nova e conteudo igual em instante novo confirma`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        val same = reading(outlierBand = 6)
        at -= 2_000
        assertEquals(false, tracker.observe(Fuel.GAS, OutlierCurveTracker.Reading(same.counters, same.timeRaw, same.mapRaw, at)))
        assertTrue("mesmo instante: não confirma", tracker.candidates(Fuel.GAS).isEmpty())
        assertEquals(true, tracker.observe(Fuel.GAS, reading(outlierBand = 6)))
        assertEquals(listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
    }

    @Test
    fun `mesmo ponto fora da curva voltando igual tres vezes continua candidato as tres vezes`() {
        // Decisão do dono: repetição não é forma real (parado o carro injeta mais e o ponto volta no mesmo lugar).
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        repeat(3) { cycle ->
            assertEquals("ciclo $cycle", listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
            tracker.onDeleteStarted(Fuel.GAS)
            tracker.onDeleted(Fuel.GAS, 6)
            assertTrue("apagado: sai até a próxima leitura", tracker.candidates(Fuel.GAS).isEmpty())
            tracker.observe(Fuel.GAS, reading(outlierBand = 6, outlierCounter = 1))
            assertTrue("readquirida: ainda falta a segunda leitura", tracker.candidates(Fuel.GAS).isEmpty())
            tracker.observe(Fuel.GAS, reading(outlierBand = 6, outlierCounter = 1))
        }
        assertEquals(listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
    }

    @Test
    fun `reset esquece as leituras`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.reset()
        assertTrue(tracker.candidates(Fuel.GAS).isEmpty())
        assertEquals(null, tracker.lastCounters(Fuel.GAS))
    }

    @Test
    fun `leitura fora de ordem e ignorada`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        at -= 10_000
        assertEquals(false, tracker.observe(Fuel.GAS, reading()))
        assertEquals(listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
    }

    @Test
    fun `leitura velha nao conta como uma das duas leituras frescas`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        at -= 10_000
        assertEquals(false, tracker.observe(Fuel.GAS, reading(outlierBand = 6)))
        assertTrue("uma fresca + uma velha: não confirma", tracker.candidates(Fuel.GAS).isEmpty())
        at += 10_000
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        assertEquals(listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
    }

    @Test
    fun `depois do nosso apagamento, leitura velha + uma fresca nao bastam`() {
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.observe(Fuel.GAS, reading(outlierBand = 6))
        tracker.onDeleteStarted(Fuel.GAS)
        tracker.onDeleted(Fuel.GAS, 6)
        at -= 10_000
        assertEquals(false, tracker.observe(Fuel.GAS, reading(outlierBand = 6, outlierCounter = 1)))
        at += 10_000
        tracker.observe(Fuel.GAS, reading(outlierBand = 6, outlierCounter = 1))
        assertTrue("apagado: a velha não conta, só uma fresca ainda não basta", tracker.candidates(Fuel.GAS).isEmpty())
        tracker.observe(Fuel.GAS, reading(outlierBand = 6, outlierCounter = 1))
        assertEquals(listOf(6), tracker.candidates(Fuel.GAS).map { it.band })
    }

    // ---- Sessão real de 08/10/2026 (buffers lidos da ECU, valores brutos): o que o app apagou e o que não devia apagar.

    private fun real(counters: List<Int>, time: List<Int>, map: List<Int>): OutlierCurveTracker.Reading {
        at += 2_000
        fun pad(list: List<Int>) = IntArray(18) { list.getOrElse(it) { 0 } }
        return OutlierCurveTracker.Reading(pad(counters), pad(time), pad(map), at)
    }

    private fun realReading(kind: String) = when (kind) {
        // Gasolina às 10:58 (t=1555 s): 2,00 ms em 0,29 bar, 1,98 ms em 0,31 bar, 2,91 ms em 0,42 bar. Platô de pulso
        // mínimo e subida: a banda 2 (3 passagens) é BOA e foi apagada pelo app por engano.
        "gasolina-dobra" -> real(
            listOf(10, 6, 3, 0, 8, 1, 6, 1, 2, 0, 1, 10, 1, 0, 0, 0),
            listOf(1023, 1023, 1015, 0, 1491, 1608, 1826, 1905, 2096, 0, 2707, 2953, 3206, 0, 0, 0),
            listOf(226, 293, 320, 0, 425, 462, 548, 567, 632, 0, 767, 793, 825, 0, 0, 0),
        )
        // GNV às 11:06 (t=2088 s): banda 1 com 1,67 ms em 0,30 bar, ABAIXO do vizinho de 1,85 ms em 0,23 bar (ordem
        // quebrada em 10%): é ponto contaminado de verdade (apagado 4 vezes seguidas na sessão).
        "gnv-contaminado" -> real(
            listOf(7, 1, 3, 1, 5, 1, 0, 3, 4, 2, 1, 6, 5, 1, 1, 8),
            listOf(945, 856, 1124, 1404, 1545, 1820, 0, 1937, 2153, 2384, 3220, 3019, 3652, 4352, 4708, 4921),
            listOf(237, 306, 319, 362, 448, 500, 0, 594, 647, 686, 760, 794, 837, 874, 940, 1000),
        )
        // Gasolina às 10:32 (t=13 s), curva de fábrica da ECU: banda 1 em 2,92 ms ACIMA da corda (e da vizinha de 2,86 ms).
        "gasolina-pico" -> real(
            listOf(6, 3, 1, 10, 10, 10, 5, 4, 0, 0, 0, 2, 0, 2, 1, 3),
            listOf(971, 1493, 1464, 1721, 1918, 2195, 2311, 2587, 0, 0, 0, 3251, 0, 4088, 4635, 5061),
            listOf(204, 296, 338, 391, 432, 487, 518, 585, 0, 0, 0, 781, 0, 890, 939, 1000),
        )
        else -> error(kind)
    }

    @Test
    fun `sessao real - dobra da gasolina nao e fora da curva e a banda boa nao e candidata`() {
        tracker.observe(Fuel.PETROL, realReading("gasolina-dobra"))
        tracker.observe(Fuel.PETROL, realReading("gasolina-dobra"))
        assertTrue("1,98 ms em 0,31 bar é platô de pulso mínimo, não anomalia", tracker.candidates(Fuel.PETROL).isEmpty())
    }

    @Test
    fun `sessao real - o ponto de GNV contaminado abaixo da ordem continua candidato`() {
        tracker.observe(Fuel.GAS, realReading("gnv-contaminado"))
        tracker.observe(Fuel.GAS, realReading("gnv-contaminado"))
        assertEquals(listOf(1), tracker.candidates(Fuel.GAS).map { it.band })
    }

    @Test
    fun `sessao real - pico acima da corda continua candidato`() {
        tracker.observe(Fuel.PETROL, realReading("gasolina-pico"))
        tracker.observe(Fuel.PETROL, realReading("gasolina-pico"))
        assertEquals(listOf(1), tracker.candidates(Fuel.PETROL).map { it.band })
    }

    @Test
    fun `o Refino continua com a regra de sempre - a tolerancia da dobra e so do apagamento`() {
        val points = AutoMatchRefinedEngine.bandPoints(
            realReading("gasolina-dobra").timeRaw, realReading("gasolina-dobra").mapRaw, realReading("gasolina-dobra").counters,
        )
        val (_, rejectedDefault) = AutoMatchRefinedEngine.monotoneFit(points)
        assertEquals("regra do Refino inalterada", listOf(2), rejectedDefault.map { it.band })
        val (_, rejectedCleanup) = AutoMatchRefinedEngine.monotoneFit(points, AutoMatchRefinedEngine.OUTLIER_KNEE_TOLERANCE_LOG)
        assertTrue(rejectedCleanup.isEmpty())
    }

    private fun mapRaw(band: Int) = 300 + 40 * band
    private fun timeRaw(band: Int) = ((1.5 + 0.25 * band) * 512).toInt()

    private fun reading(
        outlierBand: Int? = null,
        outlierCounter: Int = 5,
        outlierFactor: Double = 1.3,
    ): OutlierCurveTracker.Reading {
        at += 2_000
        val counters = IntArray(18) { if (it < 16) 5 else 0 }
        val time = IntArray(18) { if (it < 16) timeRaw(it) else 0 }
        val map = IntArray(18) { if (it < 16) mapRaw(it) else 0 }
        if (outlierBand != null) {
            time[outlierBand] = (timeRaw(outlierBand) * outlierFactor).toInt()
            counters[outlierBand] = outlierCounter
        }
        return OutlierCurveTracker.Reading(counters, time, map, at)
    }
}
