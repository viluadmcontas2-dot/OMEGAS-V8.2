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

    @Test
    fun `quase apagou - RPM cai abaixo de 500 por mais de 300 ms e volta - vira tropeco com o lugar e nao apagao`() {
        val w = StallWatch(null)
        var (t, _) = feed(w, 0, "GNV", (0 until 20).map { 1_700.0 - it * 25.0 }, petrol = 2.1, map = 0.25) // desacelerando
        t = feed(w, t, "GNV", List(6) { 420.0 }, petrol = 2.0, map = 0.30).first                        // 600 ms abaixo de 500
        val (_, event) = feed(w, t, "GNV", List(10) { 850.0 }, petrol = 3.9, map = 0.45)                 // o motor se recuperou
        assertNotNull(event)
        assertEquals(StallWatch.KIND_NEAR, (event as org.json.JSONObject).getString("kind"))
        assertEquals(420.0, event.getDouble("minRpm"), 1e-9)
        val json = w.json()
        assertEquals(0, json.getInt("count"))
        assertEquals(1, json.getInt("nearCount"))
        assertEquals(2.0, json.getJSONArray("nearRegions").getJSONObject(0).getDouble("fromMs"), 1e-9)
        val region = json.getJSONArray("nearRegions").getJSONObject(0)
        assertTrue(region.getDouble("rpmLow") <= region.getDouble("rpmHigh"))
        assertTrue(region.getDouble("rpmHigh") <= 1_700.0)
    }

    @Test
    fun `tropeco depois do corte (cutoff) e marcado`() {
        val w = StallWatch(null)
        var (t, _) = feed(w, 0, "GNV", List(15) { 1_600.0 }, petrol = 2.2)
        t = feed(w, t, "CUTOFF", List(4) { 1_400.0 }, petrol = 0.3).first
        t = feed(w, t, "GNV", List(5) { 430.0 }, petrol = 2.0).first
        val (_, event) = feed(w, t, "GNV", List(8) { 900.0 })
        assertEquals(true, (event as org.json.JSONObject).getBoolean("afterCutoff"))
    }

    @Test
    fun `um quadro isolado abaixo de 500 (ruido) nao e tropeco`() {
        val w = StallWatch(null)
        var (t, _) = feed(w, 0, "GNV", List(20) { 1_500.0 })
        t = feed(w, t, "GNV", listOf(300.0)).first
        val (_, event) = feed(w, t, "GNV", List(10) { 1_500.0 })
        assertNull(event)
        assertEquals(0, w.json().getInt("nearCount"))
    }

    @Test
    fun `chave desligada na lenta com o RPM decaindo nao e apagao nem tropeco`() {
        val w = StallWatch(null)
        val (t, _) = feed(w, 0, "GNV", List(45) { 872.0 + (it % 3) })        // lenta estável, nunca passou de 1000
        val (_, event) = feed(w, t, "GNV", listOf(860.0, 800.0, 700.0, 610.0, 450.0, 250.0) + List(12) { 0.0 })
        assertNull(event)
        assertEquals(0, w.json().getInt("count"))
        assertEquals(0, w.json().getInt("nearCount"))
    }

    @Test
    fun `apagao real depois de acelerar acima de 1000 continua sendo registrado`() {
        val w = StallWatch(null)
        val (t, _) = feed(w, 0, "GNV", (0 until 12).map { 1_500.0 - it * 60.0 } + List(8) { 780.0 }, petrol = 8.0, map = 0.9)
        val (_, event) = feed(w, t, "GNV", List(12) { 0.0 })
        assertEquals(StallWatch.KIND_STALL, (event as org.json.JSONObject).getString("kind"))
        assertEquals(1, w.json().getInt("count"))
        assertEquals(0, w.json().getInt("nearCount"))
    }

    @Test
    fun `apagao real de 2026-10-13 - desacelera em corte, volta a 2 ms, o giro despenca e apaga - e registrado`() {
        // Sequência reproduzida da sessão gravada (tests/fixtures/sessoes-uteis/apagao-2026-10-13-1613.json), quadros de 250 ms.
        // Antes da correção o tempo em CORTE contava contra o GNV e este apagão podia passar batido.
        val w = StallWatch(null)
        var t = 0L
        fun f(fuel: String, rpm: Double, petrol: Double, map: Double) { w.accept(StallWatch.Frame(t, fuel, rpm, map, petrol)); t += 250 }
        repeat(16) { f("CUTOFF", 2_200.0 - it * 40.0, 0.0, 0.18) }                 // ~4 s de desaceleração em corte
        listOf(1_489.0 to 1.82, 1_352.0 to 2.32, 1_293.0 to 1.87, 1_221.0 to 2.08, 749.0 to 2.98).forEach { (rpm, ms) -> f("GNV", rpm, ms, 0.22) }
        repeat(3) { f("GNV", 484.0, 5.84, 0.98) }
        var event: org.json.JSONObject? = null
        repeat(8) { event = w.accept(StallWatch.Frame(t, "DESLIGADO", 0.0, 0.98, 0.19)) ?: event; t += 250 }
        assertNotNull(event)
        assertEquals(StallWatch.KIND_STALL, event!!.getString("kind"))
        assertEquals(1, w.json().getInt("count"))
    }
}
