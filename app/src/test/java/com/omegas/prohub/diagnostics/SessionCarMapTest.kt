package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapa do carro dentro do resumo: mapa K e curva K ATIVOS (valores completos), cada mudança de ponto (antes, depois,
 * quando, quem causou, em qual tanque) e a linha do tempo humana. Desconhecido é null, nunca 0.
 */
class SessionCarMapTest {
    private val wall0 = 1_790_000_000_000L
    private val elapsed0 = 5_000_000L
    private val ctx = SummaryContext(sessionId = "session_x", sessionUuid = "11111111-2222-3333-4444-555555555555", startMs = wall0, appVersion = "8.2.test", commit = "abc")
    private var seq = 0L

    private fun send(b: SessionSummaryBuilder, type: String, data: JSONObject, tMs: Long) = b.observe(++seq, type, "test", data, wall0 + tMs)
    private fun tel(b: SessionSummaryBuilder, tMs: Long, level: Int = 60, fuel: String = "GNV") = send(
        b, "telemetry",
        JSONObject().put("captured_elapsed_ms", elapsed0 + tMs).put("rpm", 1500).put("level_raw", level).put("fuel", fuel)
            .put("gas_ms_diagnostic", 8.0).put("gas_pulse_present", true).put("load_bar", 0.4), tMs,
    )

    private fun mapRows(base: Int = 128) = JSONArray().also { rows -> repeat(12) { r -> rows.put(JSONArray().also { c -> repeat(12) { col -> c.put(base + r + col) } }) } }
    private fun mapEvent(rows: JSONArray, hash: String) = JSONObject().put("rows", rows).put("hash", hash).put("trigger", "AUTO_START")
    private fun curveEvent(factors: List<Int>, origin: String? = null, previous: List<Int>? = null) = JSONObject()
        .put("factorsRaw", JSONArray(factors)).put("axisRaw", JSONArray(List(30) { 1024 + it * 512 }))
        .also { if (origin != null) it.put("origin", origin) }
        .also { if (previous != null) it.put("previousFactorsRaw", JSONArray(previous)) }

    private fun summary(b: SessionSummaryBuilder, final: Boolean = true) = b.summary(final)

    @Test
    fun `sem leitura nenhuma o mapa e a curva ficam desconhecidos e nunca viram zero`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0); tel(b, 1000)
        val s = summary(b)
        val car = s.getJSONObject("carMap")
        assertFalse(car.getJSONObject("map").getBoolean("known"))
        assertTrue(car.getJSONObject("map").isNull("rows"))
        assertFalse(car.getJSONObject("curve").getBoolean("known"))
        assertTrue(car.getJSONObject("curve").isNull("points"))
        assertEquals(0, s.getJSONArray("changeLog").length())
        assertEquals(0, s.getJSONObject("netChange").getJSONArray("map").length())
    }

    @Test
    fun `leitura completa guarda o mapa K ativo com eixos e o ponto de partida`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        send(b, "k_read_map", mapEvent(mapRows(), "hashA"), 500)
        val map = summary(b).getJSONObject("carMap").getJSONObject("map")
        assertTrue(map.getBoolean("known"))
        assertEquals(12, map.getJSONArray("rows").length())
        assertEquals(128, map.getJSONArray("rows").getJSONArray(0).getInt(0))
        assertEquals(128 + 11 + 11, map.getJSONArray("rows").getJSONArray(11).getInt(11))
        assertEquals("hashA", map.getString("hashLast"))
        assertEquals(12, map.getJSONObject("axes").getJSONArray("rpmBins").length())
        assertEquals("petrolMs", map.getJSONObject("axes").getString("rowAxis"))
        assertEquals("rpm", map.getJSONObject("axes").getString("columnAxis"))
        assertNotNull(map.getJSONArray("rowsAtStart"))
    }

    @Test
    fun `gravacao confirmada vira mudanca por celula com antes depois motivo e tanque`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        send(b, "k_read_map", mapEvent(mapRows(), "hashA"), 500)
        val ev = JSONArray()
            .put(JSONObject().put("row", 3).put("column", 4).put("before", 128 + 3 + 4).put("after", 150).put("petrolMs", 3.5).put("rpm", 3000).put("reason", "Ajuste manual confirmado na UI clean-slate").put("timestamp", 1))
            .put(JSONObject().put("row", 5).put("column", 6).put("before", 128 + 5 + 6).put("after", 120).put("petrolMs", 6.0).put("rpm", 4000).put("reason", "Ajuste manual confirmado na UI clean-slate").put("timestamp", 2))
        send(b, "k_batch_confirmed", JSONObject().put("calibrationType", "MAP_K").put("adjustmentId", "ADJ-1").put("oldHash", "hashA").put("newHash", "hashB").put("confirmedEvents", ev).put("origin", "APP_UI_MANUAL"), 2000)
        val s = summary(b)
        val log = s.getJSONArray("changeLog")
        assertEquals(1, log.length())
        val c = log.getJSONObject(0)
        assertEquals("MAP_K", c.getString("kind"))
        assertEquals("DONO_MANUAL", c.getString("cause"))
        assertEquals("ADJ-1", c.getString("adjustmentId"))
        assertEquals("hashA", c.getString("oldHash"))
        assertEquals("hashB", c.getString("newHash"))
        assertEquals(2, c.getInt("cellsChanged"))
        assertEquals(0, c.getInt("tankIndex"))
        val cell = c.getJSONArray("changes").getJSONObject(0)
        assertEquals(3, cell.getInt("row")); assertEquals(4, cell.getInt("column"))
        assertEquals(135, cell.getInt("before")); assertEquals(150, cell.getInt("after"))
        assertEquals(3.5, cell.getDouble("petrolMs"), 0.0001); assertEquals(3000, cell.getInt("rpm"))
        // o mapa ativo já reflete a gravação, e o líquido (início → fim) aponta as mesmas células
        assertEquals(150, s.getJSONObject("carMap").getJSONObject("map").getJSONArray("rows").getJSONArray(3).getInt(4))
        assertEquals(135, s.getJSONObject("carMap").getJSONObject("map").getJSONArray("rowsAtStart").getJSONArray(3).getInt(4))
        val net = s.getJSONObject("netChange").getJSONArray("map")
        assertEquals(2, net.length())
        assertEquals(135, net.getJSONObject(0).getInt("start")); assertEquals(150, net.getJSONObject(0).getInt("end"))
        assertEquals("hashB", s.getJSONObject("carMap").getJSONObject("map").getString("hashLast"))
    }

    @Test
    fun `motivo do app define quem causou a mudanca`() {
        fun causeFor(reason: String): String {
            val b = SessionSummaryBuilder(ctx)
            tel(b, 0)
            val ev = JSONArray().put(JSONObject().put("row", 0).put("column", 0).put("before", 130).put("after", 140).put("reason", reason))
            send(b, "k_batch_confirmed", JSONObject().put("adjustmentId", "A").put("confirmedEvents", ev), 1000)
            return summary(b).getJSONArray("changeLog").getJSONObject(0).getString("cause")
        }
        assertEquals("RESTAURAR_BACKUP", causeFor("Restaurar backup do Mapa K"))
        assertEquals("CURVA_PARA_MAPA", causeFor("Curva K → Mapa K: curva refatorada"))
        assertEquals("REFINO", causeFor("Refino: gravar no Mapa K"))
        assertEquals("DONO_MANUAL", causeFor("Ajuste manual confirmado na UI clean-slate"))
    }

    @Test
    fun `curva mudada pela central entra por ponto e e atribuida ao AutoMatch da central`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        val before = List(30) { 16384 }
        send(b, "k_read_curve", curveEvent(before), 400)
        val after = before.toMutableList().also { it[4] = 17000; it[5] = 17500 }
        send(b, "k_curve_state", curveEvent(after, "ECU_NATIVE_OR_EXTERNAL", before), 3000)
        val s = summary(b)
        val c = s.getJSONArray("changeLog").getJSONObject(0)
        assertEquals("CURVE_K", c.getString("kind"))
        assertEquals("ECU_AUTOMATCH", c.getString("cause"))
        assertEquals(2, c.getInt("cellsChanged"))
        val p = c.getJSONArray("changes").getJSONObject(0)
        assertEquals(4, p.getInt("index")); assertEquals(16384, p.getInt("beforeRaw")); assertEquals(17000, p.getInt("afterRaw"))
        assertEquals(1.0, p.getDouble("beforeFactor"), 0.0001)
        assertEquals(17000 / 16384.0, p.getDouble("afterFactor"), 0.0001)
        assertEquals(1024 + 4 * 512, p.getInt("axisRaw"))
        assertEquals(17000, s.getJSONObject("carMap").getJSONObject("curve").getJSONArray("points").getJSONObject(4).getInt("factorRaw"))
        assertEquals(2, s.getJSONObject("netChange").getJSONArray("curve").length())
    }

    @Test
    fun `AutoMatch pedido pelo dono antes da mudanca da curva e atribuido ao dono uma vez so`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        val c0 = List(30) { 16384 }
        send(b, "k_read_curve", curveEvent(c0), 400)
        send(b, "autocal_native_action", JSONObject().put("action", "MANUAL_AUTOMATCH").put("outcome", "CONFIRMED").put("label", "AutoMatch manual"), 2000)
        val c1 = c0.toMutableList().also { it[2] = 15000 }
        send(b, "k_curve_state", curveEvent(c1, "ECU_NATIVE_OR_EXTERNAL", c0), 2500)
        val c2 = c1.toMutableList().also { it[3] = 15500 }
        send(b, "k_curve_state", curveEvent(c2, "ECU_NATIVE_OR_EXTERNAL", c1), 9000)
        val log = summary(b).getJSONArray("changeLog")
        assertEquals("OWNER_AUTOMATCH", log.getJSONObject(0).getString("cause"))
        assertEquals("ECU_AUTOMATCH", log.getJSONObject(1).getString("cause"))
    }

    @Test
    fun `leitura diferente da anterior sem gravacao registrada vira mudanca de origem nao registrada`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        send(b, "k_read_map", mapEvent(mapRows(), "hashA"), 400)
        val changed = mapRows().also { it.getJSONArray(1).put(1, 200) }
        send(b, "k_read_map", mapEvent(changed, "hashZ"), 5000)
        val c = summary(b).getJSONArray("changeLog").getJSONObject(0)
        assertEquals("UNKNOWN_EXTERNAL", c.getString("cause"))
        assertEquals(1, c.getInt("cellsChanged"))
        assertEquals(200, c.getJSONArray("changes").getJSONObject(0).getInt("after"))
    }

    @Test
    fun `leitura repetida igual nao inventa mudanca`() {
        val b = SessionSummaryBuilder(ctx)
        tel(b, 0)
        send(b, "k_read_map", mapEvent(mapRows(), "hashA"), 400)
        send(b, "k_read_map", mapEvent(mapRows(), "hashA"), 5000)
        assertEquals(0, summary(b).getJSONArray("changeLog").length())
    }

    @Test
    fun `linha do tempo humana traz os marcos em ordem sem inventar o que nao aconteceu`() {
        val b = SessionSummaryBuilder(ctx)
        send(b, "session_started", JSONObject(), 0)
        send(b, "lifecycle", JSONObject().put("kind", "usb_connected").put("data", JSONObject().put("usbSessionId", 3)), 100)
        tel(b, 200, level = 200)
        send(b, "autocal_native_snapshot", JSONObject().put("fields", JSONArray().put(JSONObject().put("key", "MODULE_VERSION").put("status", "VALID").put("rawValues", JSONArray().put(17)))), 300)
        send(b, "level_calibration", JSONObject().put("complete", true).put("refs", JSONArray(listOf(39, 93, 143, 219))), 400)
        send(b, "refuel_detected", JSONObject().put("source", "IN_SESSION").put("fromRaw", 200).put("toRaw", 60).put("drop", 140).put("atElapsedMs", elapsed0 + 800), 900)
        tel(b, 1000, level = 60)
        send(b, "fuel_change", JSONObject().put("from", "GNV").put("to", "GASOLINA").put("atElapsedMs", elapsed0 + 1500).put("rpm", 1200), 1600)
        send(b, "session_stopped", JSONObject().put("reason", "dono parou"), 3000)
        val t = summary(b).getJSONArray("timeline")
        val kinds = (0 until t.length()).map { t.getJSONObject(it).getString("kind") }
        assertEquals(listOf("session_started", "usb_connected", "central_recognized", "level_calibration", "refuel", "fuel_change", "session_stopped"), kinds)
        val texts = (0 until t.length()).map { t.getJSONObject(it).getString("text") }
        assertTrue(texts[2], texts[2].contains("17"))
        assertTrue(texts[4], texts[4].contains("200") && texts[4].contains("60"))
        assertTrue(texts[5], texts[5].contains("gasolina"))
        val times = (0 until t.length()).map { t.getJSONObject(it).getLong("tMs") }
        assertEquals(times.sorted(), times)
        // sem calibração lida, nada de "calibração lida"
        val none = SessionSummaryBuilder(ctx).also { tel(it, 0) }
        assertEquals(0, summary(none).getJSONArray("timeline").length())
        assertNull(summary(none).getJSONObject("carMap").getJSONObject("map").opt("rowsAtStart").takeIf { it != JSONObject.NULL })
    }
}
