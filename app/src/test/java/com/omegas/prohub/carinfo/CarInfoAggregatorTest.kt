package com.omegas.prohub.carinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CarInfoAggregatorTest {
    private fun props(vararg p: Pair<Int, Float>) =
        CarPropBatch("x", p.map { CarProp(it.first, 0, "java.lang.Float", it.second.toString(), it.second) }, p.size, true)

    @Test
    fun `tx17 so preenche o que o painel nao trouxe`() {
        val agg = CarInfoAggregator()
        agg.onDashboard(CarInfoSample(speed = 40, cruisingRange = 35, complete = true))
        val m = agg.onProps(props(0x11600207 to 20.0f, 0x11600305 to 1850f))!!
        assertEquals(40, m.speed)                                 // painel manda
        assertEquals(1850, m.rotationRate)                        // tx17 preenche o vazio
        assertEquals("tx17", m.sources["rotationRate"])
        assertNull(m.sources["speed"])
        assertEquals("tx6", m.toJson().getJSONObject("sources").getString("speed"))
        assertEquals("tx17", m.toJson().getJSONObject("sources").getString("rotationRate"))
    }

    @Test
    fun `velocidade em metros por segundo vira km por hora e impossivel nao entra`() {
        val agg = CarInfoAggregator()
        val m = agg.onProps(props(0x11600207 to 10.0f, 0x11600204 to -5f))!!
        assertEquals(36, m.speed)
        assertNull(m.odo)
    }

    @Test
    fun `propId desconhecido nao muda nada`() {
        val agg = CarInfoAggregator()
        assertNull(agg.onProps(props(0x1234 to 5f)))
        assertNull(agg.merged())
    }

    @Test
    fun `novo painel mantem o que veio de tx17 e reset limpa`() {
        val agg = CarInfoAggregator()
        agg.onProps(props(0x11600305 to 900f))
        val m = agg.onDashboard(CarInfoSample(cruisingRange = 30))
        assertEquals(900, m.rotationRate)
        assertEquals(30, m.cruisingRange)
        agg.reset()
        assertNull(agg.merged())
        assertNotNull(agg.onDashboard(CarInfoSample()))
    }
}
