package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2 (comportamento sintético): antes, durante e depois da queda decidem o que foi. */
class StallWatchTest {
    private fun feed(
        w: StallWatch,
        start: Long,
        fuel: String,
        rpms: List<Double>,
        petrol: Double = 2.4,
        map: Double = 0.32,
        speed: Double? = null,
    ): Pair<Long, JSONObject?> {
        var t = start
        var last: JSONObject? = null
        rpms.forEach { rpm -> w.accept(StallWatch.Frame(t, fuel, rpm, map, petrol, speed))?.let { last = it }; t += 100 }
        return t to last
    }

    private val decel = (0 until 25).map { 1_800.0 - it * 40.0 } // reduzindo para o quebra-molas

    @Test
    fun `apagao no GNV desacelerando e registrado com o lugar e confirma quando religa`() {
        val w = StallWatch(null)
        val (t, _) = feed(w, 0, "GNV", decel, petrol = 2.1, map = 0.30, speed = 22.0)
        val (t2, event) = feed(w, t, "GNV", List(12) { 0.0 }, speed = 15.0)
        assertNotNull(event)
        assertEquals(StallWatch.KIND_STALL, event!!.getString("kind"))
        assertTrue(event.isNull("religou"))
        assertEquals(22.0, event.getDouble("speedKmh"), 1e-9)
        feed(w, t2, "GNV", List(5) { 900.0 })
        val json = w.json()
        assertEquals(1, json.getInt("count"))
        assertEquals(0, json.getInt("nearCount"))
        val stored = json.getJSONArray("events").getJSONObject(0)
        assertTrue(stored.getBoolean("religou"))
        assertTrue(stored.getDouble("religouEmS") < 2.0)
        val region = json.getJSONArray("regions").getJSONObject(0)
        assertEquals(2.0, region.getDouble("fromMs"), 1e-9)
        assertEquals(0.30, region.getDouble("mapBar"), 1e-9)
        assertEquals(1, region.getInt("stallCount"))
    }

    @Test
    fun `desligar o carro na marcha lenta nao conta como apagao`() {
        val w = StallWatch(null)
        val (t, _) = feed(w, 0, "GNV", List(40) { 850.0 + (it % 3) * 10.0 })
        val (_, event) = feed(w, t, "GNV", List(12) { 0.0 })
        assertNull(event)
        assertEquals(0, w.json().getInt("count"))
        assertTrue(w.json().getJSONObject("ignored").getInt("idleShutdowns") >= 1)
    }

    @Test
    fun `queda na gasolina ou piscada de um quadro nao contam`() {
        val w = StallWatch(null)
        var (t, _) = feed(w, 0, "GASOLINA", decel)
        var r = feed(w, t, "GASOLINA", List(12) { 0.0 }); t = r.first
        assertNull(r.second)
        t = feed(w, t, "GNV", List(30) { 1_500.0 }).first
        t = feed(w, t, "GNV", listOf(0.0, 0.0)).first // ruído: 200 ms
        r = feed(w, t, "GNV", List(10) { 1_500.0 })
        assertNull(r.second)
        assertEquals(0, w.json().getInt("count"))
    }

    @Test
    fun `queda seguida de telemetria calada e chave desligada, nao apagao`() {
        var now = 0L
        val w = StallWatch(null) { now }
        val (t, _) = feed(w, 0, "GNV", decel)
        // Dois quadros a 0 rpm e a telemetria para (chave/cabo/app): sem confirmar.
        val (t2, event) = feed(w, t, "GNV", listOf(0.0, 0.0))
        assertNull(event)
        now = t2 + StallWatch.GAP_MS + 1_000
        w.tick(now)
        val json = w.json()
        assertEquals(0, json.getInt("count"))
        assertFalse(json.getBoolean("pending"))
        assertEquals(1, json.getJSONObject("ignored").getInt("cutShutdowns"))
    }

    @Test
    fun `apagao confirmado nunca some, so ganha a anotacao do que veio depois`() {
        var now = 0L
        // Sem religar, sem GPS: o motor segue morto por 62 s com a telemetria viva (chave ligada).
        val w = StallWatch(null) { now }
        val (t, event) = feed(w, 0, "GNV", decel + List(12) { 0.0 })
        assertNotNull(event)
        val (tAlive, _) = feed(w, t, "GNV", List(620) { 0.0 })
        now = tAlive + 500
        w.tick(now)
        assertEquals(1, w.json().getInt("count"))
        val stored = w.json().getJSONArray("events").getJSONObject(0)
        assertFalse(stored.getBoolean("religou"))
        assertEquals(StallWatch.AFTER_NO_RESTART, stored.getString("depois"))

        // Telemetria cala depois de confirmado: continua apagão, anotado como telemetria parou.
        now = 0L
        val cut = StallWatch(null) { now }
        val (t2, e2) = feed(cut, 0, "GNV", decel + List(12) { 0.0 }, speed = 20.0)
        assertNotNull(e2)
        now = t2 + StallWatch.GAP_MS + 1_000
        cut.tick(now)
        val cutEvent = cut.json().getJSONArray("events").getJSONObject(0)
        assertEquals(1, cut.json().getInt("count"))
        assertEquals(StallWatch.AFTER_TELEMETRY_STOPPED, cutEvent.getString("depois"))
        assertEquals(20.0, cutEvent.getDouble("speedKmh"), 1e-9)

        // Religou: anotado como religou, com o tempo.
        val ok = StallWatch(null) { now }
        val (t3, _) = feed(ok, 0, "GNV", decel + List(12) { 0.0 })
        feed(ok, t3, "GNV", List(5) { 900.0 })
        assertEquals(StallWatch.AFTER_RESTARTED, ok.json().getJSONArray("events").getJSONObject(0).getString("depois"))
        assertEquals(1, ok.json().getInt("restartedCount"))
    }

    @Test
    fun `quase apagao conta separado e vira apagao se o motor morrer em seguida`() {
        val w = StallWatch(null)
        var (t, near) = feed(w, 0, "GNV", listOf(1_600.0, 1_500.0, 1_300.0, 1_100.0, 800.0, 450.0))
        assertNotNull(near)
        assertEquals(StallWatch.KIND_NEAR, near!!.getString("kind"))
        assertEquals(450.0, near.getDouble("rpmMin"), 1e-9)
        t = feed(w, t, "GNV", List(8) { 850.0 }).first
        assertEquals(1, w.json().getInt("nearCount"))
        assertEquals(0, w.json().getInt("count"))

        // Segundo tranco: agora o motor morre de verdade.
        t = feed(w, t, "GNV", listOf(1_400.0, 1_200.0, 500.0)).first
        val (_, stall) = feed(w, t, "GNV", List(12) { 0.0 })
        assertNotNull(stall)
        assertEquals(StallWatch.KIND_STALL, stall!!.getString("kind"))
        val json = w.json()
        assertEquals(1, json.getInt("count"))
        assertEquals("o quase-apagão virou o apagão, não dois eventos", 1, json.getInt("nearCount"))
    }

    @Test
    fun `lenta normal e arrancada nao viram quase apagao`() {
        val w = StallWatch(null)
        var t = feed(w, 0, "GNV", List(30) { 820.0 + (it % 4) * 15.0 }).first
        t = feed(w, t, "GNV", listOf(700.0, 650.0, 590.0, 640.0, 800.0)).first // ar-condicionado ligou
        assertEquals(0, w.json().getInt("nearCount"))
        feed(w, t, "GNV", listOf(1_200.0, 1_800.0, 2_400.0))
        assertEquals(0, w.json().getInt("nearCount"))
    }

    @Test
    fun `arquivo antigo v1 carrega os eventos como apagao`() {
        val dir = java.nio.file.Files.createTempDirectory("stall").toFile()
        val file = java.io.File(dir, "stall_watch.json")
        file.writeText("""{"format":"omegas-stall-watch-v1","events":[{"at":10,"rpmBefore":1500,"petrolMs":2.2,"mapBar":0.31,"minPetrolMs":2.0,"decelerating":true}]}""")
        val w = StallWatch(file)
        val json = w.json()
        assertEquals(1, json.getInt("count"))
        assertEquals(StallWatch.KIND_STALL, json.getJSONArray("events").getJSONObject(0).getString("kind"))
    }
}
