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
    fun `o mesmo instante repetido nao e leitura nova; conteudo igual em instante novo confirma`() {
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
