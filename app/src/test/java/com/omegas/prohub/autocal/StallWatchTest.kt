package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StallWatchTest {
    private fun feed(w: StallWatch, start: Long, fuel: String, rpms: List<Double>, petrol: Double = 2.4, map: Double = 0.32): Pair<Long, Any?> {
        var t = start
        var last: Any? = null
        rpms.forEach { rpm -> w.accept(StallWatch.Frame(t, fuel, rpm, map, petrol))?.let { last = it }; t += 100 }
        return t to last
    }

    @Test
    fun `apagao no GNV desacelerando e registrado com o lugar`() {
        val w = StallWatch(null)
        val decel = (0 until 25).map { 1_800.0 - it * 40.0 } // reduzindo para o quebra-molas
        val (t, _) = feed(w, 0, "GNV", decel, petrol = 2.1, map = 0.30)
        val (_, event) = feed(w, t, "GNV", List(12) { 0.0 })
        assertNotNull(event)
        val json = w.json()
        assertEquals(1, json.getInt("count"))
        val region = json.getJSONArray("regions").getJSONObject(0)
        assertEquals(2.0, region.getDouble("fromMs"), 1e-9)
        assertEquals(0.30, region.getDouble("mapBar"), 1e-9)
    }

    @Test
    fun `desligar o carro na marcha lenta nao conta como apagao`() {
        val w = StallWatch(null)
        val (t, _) = feed(w, 0, "GNV", List(40) { 850.0 + (it % 3) * 10.0 })
        val (_, event) = feed(w, t, "GNV", List(12) { 0.0 })
        assertNull(event)
        assertEquals(0, w.json().getInt("count"))
    }

    @Test
    fun `queda na gasolina ou piscada de um quadro nao contam`() {
        val w = StallWatch(null)
        var (t, _) = feed(w, 0, "GASOLINA", (0 until 25).map { 1_800.0 - it * 40.0 })
        var r = feed(w, t, "GASOLINA", List(12) { 0.0 }); t = r.first
        assertNull(r.second)
        t = feed(w, t, "GNV", List(30) { 1_500.0 }).first
        t = feed(w, t, "GNV", listOf(0.0, 0.0)).first // ruído: 200 ms
        r = feed(w, t, "GNV", List(10) { 1_500.0 })
        assertNull(r.second)
        assertTrue(w.json().getInt("count") == 0)
    }
}
