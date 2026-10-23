package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eventos que faltavam: troca de combustível, ciclo de vida do serviço/USB (com pré-gravação) e ações do dono. */
class SessionEventNotesTest {
    private class Rec { val events = ArrayList<Pair<String, JSONObject>>(); fun emit(t: String, d: JSONObject) { events += t to d } }

    private fun frames(t: FuelTracker, from: Int, n: Int, fuelByte: Int, rpm: Int = 900, water: Int = 40) {
        repeat(n) { i -> t.frame((from + i) * 66L, fuelByte, rpm, water) }
    }

    @Test
    fun `troca de gasolina para GNV vira fuel_change com antes, depois, rpm e agua crua`() {
        val r = Rec(); val t = FuelTracker(r::emit)
        frames(t, 0, 10, 0x80)
        frames(t, 10, 10, 0x88)   // transicao
        frames(t, 20, 10, 0x90, rpm = 1500, water = 38)
        val changes = r.events.map { it.second }
        assertEquals(listOf("GASOLINA", "TRANSICAO", "GNV"), changes.map { it.getString("to") })
        assertTrue(changes[0].isNull("from"))
        assertEquals("TRANSICAO", changes[2].getString("from"))
        assertEquals(0x90, changes[2].getInt("toByte"))
        assertEquals(1500, changes[2].getInt("rpm"))
        assertEquals(38, changes[2].getInt("waterRaw"))
    }

    @Test
    fun `piscada de 1 ou 2 quadros nao e troca de combustivel`() {
        val r = Rec(); val t = FuelTracker(r::emit)
        frames(t, 0, 10, 0x90)
        frames(t, 10, 2, 0x80)
        frames(t, 12, 10, 0x90)
        assertEquals(1, r.events.size) // so o primeiro GNV
    }

    @Test
    fun `variantes 0x20 do mesmo estado nao contam como troca, motor desligado e OFF`() {
        val r = Rec(); val t = FuelTracker(r::emit)
        frames(t, 0, 10, 0x90)
        frames(t, 10, 10, 0xB0)
        frames(t, 20, 10, 0x00, rpm = 0)
        assertEquals(listOf("GNV", "MOTOR_OFF"), r.events.map { it.second.getString("to") })
    }

    @Test
    fun `anel do ciclo de vida guarda os ultimos 64 em ordem e entra no inicio da sessao`() {
        val ring = LifecycleRing(64)
        for (i in 0 until 100) ring.add("k$i", 1_000L + i, 50L + i, JSONObject().put("i", i))
        val snap = ring.snapshot()
        assertEquals(64, snap.length())
        assertEquals("k36", snap.getJSONObject(0).getString("kind"))
        assertEquals("k99", snap.getJSONObject(63).getString("kind"))
        assertEquals(149L, snap.getJSONObject(63).getLong("elapsedMs"))
        assertEquals(1_099L, snap.getJSONObject(63).getLong("wallMs"))
    }

    @Test
    fun `acao do dono leva o que foi pedido e o resultado resumido`() {
        val e = UserActionEvent.json("registerRefuel", "cilindro", JSONObject().put("addedM3", 12.5).put("distanceKm", 310.0),
            JSONObject().put("ok", true).put("remaining_m3", 14.2).put("huge", "x".repeat(5000)))
        assertEquals("registerRefuel", e.getString("action"))
        assertEquals("cilindro", e.getString("target"))
        assertEquals(12.5, e.getJSONObject("value").getDouble("addedM3"), 0.0)
        assertEquals(true, e.getJSONObject("result").getBoolean("ok"))
        assertTrue(e.getJSONObject("result").toString().length < 600)
        assertEquals("OWNER_TOUCH", e.getString("origin"))
    }

    @Test
    fun `acao sem valor ou resultado fica null e nao zero`() {
        val e = UserActionEvent.json("setGpsEnabled", "gps", null, null)
        assertTrue(e.isNull("value"))
        assertTrue(e.isNull("result"))
    }
}
