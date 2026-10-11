package com.omegas.prohub.web

import com.omegas.prohub.learning.LearningGridProjection
import com.omegas.prohub.runtime.RuntimeSnapshotBus
import com.omegas.prohub.telemetry.TelemetryStateStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4a: a ponte só monta o quadro ao vivo quando o TelemetryStateStore mudou. Sem quadro novo a resposta é
 * leve (`changed:false` + idade); qualquer mudança (quadro, runtime, GPS, sessão, outro serviço) volta completa.
 */
class PresentSnapshotGateTest {
    private val bus = RuntimeSnapshotBus()

    /** Mesmo quadro que HubJavascriptBridge.presentEnvelope monta (liveJson + interpolação + RuntimeSnapshotBus). */
    private fun presentEnvelope(store: TelemetryStateStore, gate: PresentSnapshotGate): JSONObject {
        val root = JSONObject(store.liveJson())
        val live = root.optJSONObject("live") ?: JSONObject()
        val interpolation = gate.interpolation(
            rpm = live.optDouble("rpm", 0.0),
            petrolMs = live.optDouble("petrol_ms", 0.0),
            mapBar = live.optDouble("load_bar", 0.0),
            valid = root.optBoolean("valid", false),
            sequence = root.optLong("sequence", 0L),
            updatedAt = root.optLong("updatedAt", 0L),
        )
        root.put("ok", true).put("telemetryAgeMs", root.optLong("ageMs", -1L)).put("interpolation", interpolation)
        bus.publishPresent(root)
        return bus.presentJson()
    }

    private fun frame(sessionId: Long, capturedElapsedMs: Long, rpm: Int, petrolMs: Double, mapBar: Double): JSONObject =
        JSONObject()
            .put("event", "telemetry")
            .put("session_id", sessionId)
            .put("live", JSONObject()
                .put("session_id", sessionId)
                .put("captured_elapsed_ms", capturedElapsedMs)
                .put("rpm", rpm)
                .put("petrol_ms", petrolMs)
                .put("load_bar", mapBar)
                .put("fuel", "GNV"))

    private class Counter(val build: () -> JSONObject) : () -> JSONObject {
        var builds = 0
        override fun invoke(): JSONObject {
            builds += 1
            return build()
        }
    }

    private fun sequenceOf(reply: JSONObject): Long = reply.getJSONObject("data").getLong("sequence")

    /** JSON com chaves ordenadas: compara conteúdo, não a ordem interna do mapa. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "$it:${canonical(value.opt(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        else -> value.toString()
    }

    @Test
    fun `sem quadro novo a resposta e leve e o quadro nao e montado`() {
        val store = TelemetryStateStore().apply { beginSession(7L) }
        store.updateFromEngineEvent(frame(7L, 1_000L, 2_000, 4.2, 0.55))
        val gate = PresentSnapshotGate()
        val full = Counter { presentEnvelope(store, gate) }

        val first = JSONObject(gate.respond(store, -1L, full))
        assertTrue(first.getBoolean("changed"))
        assertEquals(2_000, first.getJSONObject("data").getJSONObject("live").getInt("rpm"))
        val sequence = sequenceOf(first)

        repeat(20) {
            val light = JSONObject(gate.respond(store, sequence, full))
            assertTrue(light.getBoolean("ok"))
            assertFalse(light.getBoolean("changed"))
            assertEquals(sequence, light.getLong("sequence"))
            assertEquals(sequence, light.getLong("revision"))
            assertTrue(light.getLong("telemetryAgeMs") >= 0L)
            assertFalse("resposta leve não carrega o quadro", light.has("data"))
        }
        assertEquals("20 perguntas sem quadro novo não montam nenhum quadro", 1, full.builds)
    }

    @Test
    fun `a idade da resposta leve anda com o relogio`() {
        val store = TelemetryStateStore().apply { beginSession(8L) }
        store.updateFromEngineEvent(frame(8L, 1_000L, 2_000, 4.2, 0.55))
        val gate = PresentSnapshotGate()
        val sequence = sequenceOf(JSONObject(gate.respond(store, -1L) { presentEnvelope(store, gate) }))

        val before = JSONObject(gate.respond(store, sequence) { error("sem quadro novo") }).getLong("telemetryAgeMs")
        Thread.sleep(30L)
        val after = JSONObject(gate.respond(store, sequence) { error("sem quadro novo") }).getLong("telemetryAgeMs")
        assertTrue("idade $before -> $after", after >= before + 25L)
    }

    @Test
    fun `quadro novo runtime GPS e desconexao voltam completos com o valor novo`() {
        val store = TelemetryStateStore().apply { beginSession(9L) }
        store.updateFromEngineEvent(frame(9L, 1_000L, 2_000, 4.2, 0.55))
        val gate = PresentSnapshotGate()
        val full = Counter { presentEnvelope(store, gate) }
        var sequence = sequenceOf(JSONObject(gate.respond(store, -1L, full)))

        store.updateFromEngineEvent(frame(9L, 1_100L, 2_350, 4.4, 0.60))
        val newFrame = JSONObject(gate.respond(store, sequence, full))
        assertTrue(newFrame.getBoolean("changed"))
        assertEquals(2_350, newFrame.getJSONObject("data").getJSONObject("live").getInt("rpm"))
        assertEquals(2_350.0, newFrame.getJSONObject("data").getJSONObject("interpolation").getDouble("rpm"), 0.0)
        assertTrue(sequenceOf(newFrame) > sequence)
        sequence = sequenceOf(newFrame)

        store.updateFromEngineEvent(JSONObject().put("event", "runtime").put("runtime", JSONObject().put("link", "READY")))
        val runtime = JSONObject(gate.respond(store, sequence, full))
        assertTrue(runtime.getBoolean("changed"))
        assertEquals("READY", runtime.getJSONObject("data").getJSONObject("runtime").getString("link"))
        sequence = sequenceOf(runtime)

        store.updateGps(JSONObject().put("speedKmh", 42.0))
        val gps = JSONObject(gate.respond(store, sequence, full))
        assertTrue(gps.getBoolean("changed"))
        sequence = sequenceOf(gps)

        store.invalidate("USB_DISCONNECTED")
        val offline = JSONObject(gate.respond(store, sequence, full))
        assertTrue(offline.getBoolean("changed"))
        assertFalse(offline.getJSONObject("data").getBoolean("valid"))
        assertEquals(-1L, offline.getJSONObject("data").getLong("telemetryAgeMs"))
        val light = JSONObject(gate.respond(store, sequenceOf(offline), full))
        assertFalse(light.getBoolean("changed"))
        assertEquals("idade desconhecida continua -1, nunca 0", -1L, light.getLong("telemetryAgeMs"))
        assertEquals(5, full.builds)
    }

    @Test
    fun `outro servico ou sequencia que o portao nao entregou recebem o quadro completo`() {
        val first = TelemetryStateStore().apply { beginSession(1L) }
        first.updateFromEngineEvent(frame(1L, 1_000L, 2_000, 4.2, 0.55))
        val gate = PresentSnapshotGate()
        val sequence = sequenceOf(JSONObject(gate.respond(first, -1L) { presentEnvelope(first, gate) }))

        // Serviço recriado: store novo que chega ao mesmo número de sequência com outro motor.
        val second = TelemetryStateStore().apply { beginSession(2L) }
        second.updateFromEngineEvent(frame(2L, 5_000L, 900, 2.1, 0.30))
        assertEquals(sequence, second.sequenceNow())
        val recreated = JSONObject(gate.respond(second, sequence) { presentEnvelope(second, gate) })
        assertTrue(recreated.getBoolean("changed"))
        assertEquals(900, recreated.getJSONObject("data").getJSONObject("live").getInt("rpm"))

        // Portão novo (ponte recriada) não conhece o que a página diz ter: completo.
        val freshGate = PresentSnapshotGate()
        val claimed = JSONObject(freshGate.respond(second, second.sequenceNow()) { presentEnvelope(second, freshGate) })
        assertTrue(claimed.getBoolean("changed"))
    }

    @Test
    fun `interpolacao so e reaproveitada com os mesmos valores e e igual a calculada sem cache`() {
        val gate = PresentSnapshotGate()
        val a = gate.interpolation(2_000.0, 4.2, 0.55, true, 10L, 111L)
        val b = gate.interpolation(2_000.0, 4.2, 0.55, true, 11L, 222L)
        assertSame("mesmos valores: célula reaproveitada", a.getJSONObject("cell"), b.getJSONObject("cell"))
        assertEquals(10L, a.getLong("sequence"))
        assertEquals(11L, b.getLong("sequence"))
        assertEquals(222L, b.getLong("updatedAt"))
        assertEquals(canonical(LearningGridProjection.liveInterpolationJson(2_000.0, 4.2, 0.55, 11L, 222L, true)), canonical(b))

        val c = gate.interpolation(2_000.0, 4.21, 0.55, true, 12L, 333L)
        assertNotSame(b.getJSONObject("cell"), c.getJSONObject("cell"))
        assertEquals(canonical(LearningGridProjection.liveInterpolationJson(2_000.0, 4.21, 0.55, 12L, 333L, true)), canonical(c))
        assertFalse(gate.interpolation(2_000.0, 4.21, 0.55, false, 13L, 444L).getBoolean("valid"))

        // Colado na borda 3,0|3,5 ms (meio = 3,25): arredondar a chave trocaria a célula.
        val below = gate.interpolation(2_000.0, 3.249, 0.55, true, 14L, 555L)
        val above = gate.interpolation(2_000.0, 3.251, 0.55, true, 15L, 666L)
        assertNotEquals(below.getJSONObject("cell").getInt("row"), above.getJSONObject("cell").getInt("row"))
        assertEquals(canonical(LearningGridProjection.liveInterpolationJson(2_000.0, 3.251, 0.55, 15L, 666L, true)), canonical(above))
    }
}
