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
 * Resumo leve da sessão (`omegas-session-summary-v1`): consome o mesmo fluxo de eventos do SessionRecorder.
 * Regra do dono: desconhecido é null, nunca 0; cada fonte de velocidade é rotulada e nunca misturada em silêncio.
 */
class SessionSummaryBuilderTest {
    private val wall0 = 1_790_000_000_000L // 2026
    private val elapsed0 = 5_000_000L

    private fun context(startMs: Long = wall0, previousEnd: Int? = null) = SummaryContext(
        sessionId = "session_2026-10-23_10-00-00_abcd1234",
        sessionUuid = "11111111-2222-3333-4444-555555555555",
        startMs = startMs,
        appVersion = "8.2.test",
        commit = "abc123def456",
        deviceId = "abcd1234",
        previousGnvEndRaw = previousEnd,
    )

    private class Feed(val builder: SessionSummaryBuilder, val wall0: Long, val elapsed0: Long) {
        var seq = 0L
        fun send(type: String, data: JSONObject, tMs: Long, source: String = "test") {
            builder.observe(++seq, type, source, data, wall0 + tMs)
        }
        fun tel(tMs: Long, rpm: Int = 900, level: Int? = 60, gasMs: Double? = null, petrolMs: Double = 3.0,
                fuel: String = "GNV", map: Double = 0.45, plausible: Boolean = true) {
            val d = JSONObject().put("captured_elapsed_ms", elapsed0 + tMs).put("rpm", rpm)
                .put("petrol_ms", petrolMs).put("gas_ms_diagnostic", gasMs ?: JSONObject.NULL)
                .put("gas_pulse_present", gasMs != null).put("load_bar", map).put("fuel", fuel)
                .put("plausible", plausible)
            if (level != null) d.put("level_raw", level)
            send("telemetry", d, tMs, "mp48")
        }
        fun car(tMs: Long, vararg pairs: Pair<String, Number>) {
            val d = JSONObject().put("complete", true)
            pairs.forEach { d.put(it.first, it.second) }
            send("car_info", d, tMs, "multimidia")
        }
    }

    private fun feed(ctx: SummaryContext = context()) = Feed(SessionSummaryBuilder(ctx), ctx.startMs, elapsed0)

    private fun JSONObject.at(path: String): Any? {
        var cur: Any? = this
        for (key in path.split('.')) {
            cur = (cur as? JSONObject)?.opt(key)
            if (cur == null || cur == JSONObject.NULL) return null
        }
        return cur
    }

    private fun JSONObject.num(path: String): Double? = (at(path) as? Number)?.toDouble()

    @Test
    fun `sessao vazia nao inventa zero e traz identidade`() {
        val f = feed()
        val s = f.builder.summary(final = false)
        assertEquals("omegas-session-summary-v1", s.getString("schema"))
        assertEquals("11111111-2222-3333-4444-555555555555", s.at("identity.sessionUuid"))
        assertNull(s.at("movement.bySource.MM.km"))
        assertNull(s.at("movement.bySource.GPS.km"))
        assertNull(s.at("engine.rpmMean"))
        assertNull(s.at("gnvLevel.startRaw"))
        assertNull(s.at("gnvLevel.endRaw"))
        assertNull(s.at("movement.primary.source"))
        assertNull(s.at("quality.plausiblePct"))
    }

    @Test
    fun `relogio de 2025 e marcado suspeito e a base monotonica segue valendo`() {
        val f = feed(context(startMs = 1_760_000_000_000L)) // 2025
        for (i in 0..20) f.tel(i * 250L)
        val s = f.builder.summary(final = false)
        assertEquals(true, s.at("clock.clockSuspect"))
        assertTrue(s.getJSONObject("clock").getJSONArray("suspectReasons").toString().contains("WALL_BEFORE_2026"))
        assertEquals(5_000.0, s.num("identity.elapsedMs")!!, 1.0)
        assertNotNull(s.num("clock.wallMinusElapsedFirstMs"))
    }

    @Test
    fun `salto do relogio de parede durante a sessao e detectado`() {
        val f = feed()
        f.tel(0)
        f.tel(250)
        // parede salta +1 h entre dois quadros, o monotonico segue 250 ms
        f.builder.observe(99, "telemetry", "mp48",
            JSONObject().put("captured_elapsed_ms", elapsed0 + 500).put("rpm", 900).put("fuel", "GNV"),
            wall0 + 500 + 3_600_000L)
        val s = f.builder.summary(final = false)
        assertEquals(true, s.at("clock.clockSuspect"))
        assertTrue(s.getJSONObject("clock").getJSONArray("suspectReasons").toString().contains("WALL_JUMP"))
        assertEquals(3_600_000.0, s.num("clock.driftMs")!!, 1.0)
    }

    @Test
    fun `km da multimidia por trapezio ignora buraco maior que 2 s e rotula a fonte`() {
        val f = feed()
        // 10 s a 36 km/h (1 leitura por segundo) = 0,1 km; depois buraco de 5 s; depois 2 s a 72 km/h
        for (t in 0..10) f.car(t * 1000L, "speed" to 36)
        f.car(15_000, "speed" to 72)
        f.car(16_000, "speed" to 72)
        f.car(17_000, "speed" to 72)
        val s = f.builder.summary(final = false)
        // 0,1 km + trapezio 72 km/h por 2 s = 0,04 km
        assertEquals(0.14, s.num("movement.bySource.MM.km")!!, 0.0005)
        assertEquals(12_000.0, s.num("movement.bySource.MM.movingMs")!!, 1.0)
        assertEquals(5_000.0, s.num("movement.bySource.MM.gapMs")!!, 1.0)
        assertEquals("MM", s.at("movement.primary.source"))
        assertEquals(false, s.at("movement.primary.proxy"))
        assertNull(s.at("movement.bySource.GPS.km"))
    }

    @Test
    fun `GPS desligado com velocidade 0 nao vira km zero`() {
        val f = feed()
        for (i in 0..5) f.tel(i * 1000L, rpm = 2000)
        val off = JSONObject().put("gps", JSONObject().put("enabled", false).put("speedKmh", 0.0).put("timestamp", 0L))
        f.send("full_snapshot", off, 2_000)
        f.send("full_snapshot", off, 5_000)
        val s = f.builder.summary(final = false)
        assertNull(s.at("movement.bySource.GPS.km"))
        assertEquals(0.0, s.num("movement.bySource.GPS.samples") ?: 0.0, 0.0)
        // sem MM e sem GPS: cai no proxy de rpm, rotulado
        assertEquals("RPM_PROXY", s.at("movement.primary.source"))
        assertEquals(true, s.at("movement.primary.proxy"))
        assertNull(s.at("movement.bySource.RPM_PROXY.km"))
    }

    @Test
    fun `GPS ligado entra com buraco de 5 s e fonte propria sem misturar com a multimidia`() {
        val f = feed()
        f.tel(0)
        for (k in 0..3) {
            val gps = JSONObject().put("enabled", true).put("latitude", -23.5).put("longitude", -46.6)
                .put("speedKmh", 36.0).put("timestamp", wall0 + 10_000L + k * 3_000L)
            f.send("full_snapshot", JSONObject().put("gps", gps), k * 3_000L)
        }
        val s = f.builder.summary(final = false)
        // 9 s a 36 km/h = 0,09 km
        assertEquals(0.09, s.num("movement.bySource.GPS.km")!!, 0.0005)
        assertNull(s.at("movement.bySource.MM.km"))
        assertEquals("GPS", s.at("movement.primary.source"))
        // referencia de relogio vinda do GPS: GPS(UTC) - parede do aparelho
        assertEquals(10_000.0, s.num("clock.gpsReferenceOffsetMs")!!, 1.0)
    }

    @Test
    fun `proxy de rpm so conta tempo parado e andando e nunca km`() {
        val f = feed()
        for (i in 0..8) f.tel(i * 250L, rpm = if (i < 4) 900 else 2000) // 1 s parado (i=0..3 hold) depois andando
        val s = f.builder.summary(final = false)
        assertEquals(1_000.0, s.num("movement.bySource.RPM_PROXY.stoppedMs")!!, 1.0)
        assertEquals(1_000.0, s.num("movement.bySource.RPM_PROXY.movingMs")!!, 1.0)
        assertEquals(true, s.at("movement.bySource.RPM_PROXY.proxy"))
        assertNull(s.at("movement.bySource.RPM_PROXY.km"))
    }

    @Test
    fun `minutos de injetor de gas usam gas_ms vezes rpm sobre 120 vezes 4 cilindros vezes dt`() {
        val f = feed()
        // 4 s a 1200 rpm com gas 10 ms: 10 ms * (1200/120) * 4 = 400 ms de injetor aberto por s => 1600 ms
        for (i in 0..16) f.tel(i * 250L, rpm = 1200, gasMs = 10.0)
        val s = f.builder.summary(final = false)
        assertEquals(1600.0 / 60_000.0, s.num("engine.injectorOpenMinutesGnv")!!, 1e-6)
        assertEquals(4_000.0, s.num("engine.gasInjectingMs")!!, 1.0)
        assertEquals(1200.0, s.num("engine.rpmMean")!!, 0.5)
        assertEquals(1200.0, s.num("engine.rpmMax")!!, 0.0)
    }

    @Test
    fun `lacuna de telemetria maior que 2 s vira desligamento com inicio e fim e nao entra nas somas`() {
        val f = feed()
        for (i in 0..4) f.tel(i * 250L, rpm = 1000, gasMs = 5.0)
        for (i in 0..4) f.tel(10_000L + i * 250L, rpm = 1000, gasMs = 5.0)
        val s = f.builder.summary(final = false)
        assertEquals(1.0, s.num("gaps.count")!!, 0.0)
        val item = s.getJSONObject("gaps").getJSONArray("items").getJSONObject(0)
        assertEquals(1_000L, item.getLong("startT"))
        assertEquals(10_000L, item.getLong("endT"))
        assertEquals(2_000.0, s.num("durations.telemetryCoveredMs")!!, 1.0)
    }

    @Test
    fun `nivel do cilindro e invertido e abastecimento entre sessoes e queda de 40 ou mais`() {
        // sessao anterior terminou quase vazia (230), esta comeca cheia (~50): queda de 180
        val f = feed(context(previousEnd = 230))
        for (i in 0..12) f.tel(i * 1000L, level = 50 + (i % 3))
        val s = f.builder.summary(final = true)
        assertEquals("INVERTED_255_EMPTY", s.at("gnvLevel.orientation"))
        assertEquals("raw_byte", s.at("gnvLevel.unit"))
        assertEquals(51.0, s.num("gnvLevel.startRaw")!!, 1.0)
        assertEquals(true, s.at("gnvLevel.refuel.betweenSessions"))
        assertEquals(179.0, s.num("gnvLevel.refuel.dropBetween")!!, 2.0)
        assertEquals(40.0, s.num("gnvLevel.refuel.threshold")!!, 0.0)
    }

    @Test
    fun `sem sessao anterior conhecida o abastecimento entre sessoes e desconhecido e nao falso`() {
        val f = feed(context(previousEnd = null))
        for (i in 0..12) f.tel(i * 1000L, level = 50)
        val s = f.builder.summary(final = true)
        assertNull(s.at("gnvLevel.refuel.betweenSessions"))
        assertNull(s.at("gnvLevel.refuel.dropBetween"))
    }

    @Test
    fun `queda pequena de nivel entre sessoes nao e abastecimento`() {
        val f = feed(context(previousEnd = 70))
        for (i in 0..12) f.tel(i * 1000L, level = 50)
        assertEquals(false, f.builder.summary(final = true).at("gnvLevel.refuel.betweenSessions"))
    }

    @Test
    fun `abastecimento no meio da sessao divide em segmentos e a soma bate`() {
        val f = feed()
        // 30 s com nivel 200 (andando a 36 km/h), depois sobe o cilindro (nivel cai para 50) e mais 30 s
        for (i in 0..30) { f.tel(i * 1000L, level = 200, rpm = 1500, gasMs = 8.0); f.car(i * 1000L, "speed" to 36) }
        for (i in 31..70) { f.tel(i * 1000L, level = 50, rpm = 1500, gasMs = 8.0); f.car(i * 1000L, "speed" to 36) }
        val s = f.builder.summary(final = true)
        val segs = s.getJSONArray("tankSegments")
        assertEquals(2, segs.length())
        assertEquals(200.0, segs.getJSONObject(0).getDouble("startRaw"), 1.0)
        assertEquals(50.0, segs.getJSONObject(1).getDouble("startRaw"), 1.0)
        assertEquals(1, s.getJSONObject("gnvLevel").getJSONObject("refuel").getJSONArray("intraSession").length())
        val kmTotal = s.num("movement.bySource.MM.km")!!
        val kmSum = (0 until segs.length()).sumOf { segs.getJSONObject(it).getJSONObject("km").getDouble("MM") }
        assertEquals(kmTotal, kmSum, 1e-9)
        assertTrue(segs.getJSONObject(0).getDouble("injectorOpenMinutesGnv") > 0.0)
    }

    @Test
    fun `km sobre GNV so conta enquanto o combustivel e GNV`() {
        val f = feed()
        for (i in 0..10) { f.tel(i * 1000L, fuel = "GNV"); f.car(i * 1000L, "speed" to 36) }
        for (i in 11..20) { f.tel(i * 1000L, fuel = "PETROL"); f.car(i * 1000L, "speed" to 36) }
        val s = f.builder.summary(final = true)
        assertEquals(0.2, s.num("movement.bySource.MM.km")!!, 0.001)
        assertEquals(0.1, s.num("movement.bySource.MM.kmOnGnv")!!, 0.002)
    }

    @Test
    fun `odometro da multimidia vira km de referencia sem substituir a velocidade`() {
        val f = feed()
        f.car(0, "odo" to 10_000, "speed" to 36)
        f.car(60_000, "odo" to 10_001, "speed" to 36)
        val s = f.builder.summary(final = false)
        assertEquals(1.0, s.num("movement.odometer.km")!!, 0.0)
        assertEquals(10_000.0, s.num("movement.odometer.firstKm")!!, 0.0)
    }

    @Test
    fun `estatistica da multimidia diz quais campos chegaram e quantos faltaram`() {
        val f = feed()
        for (t in 0..4) f.car(t * 1000L, "cruisingRange" to 35, "avgFuelCont" to 11.7)
        f.send("car_info", JSONObject().put("complete", false).put("cruisingRange", 34), 5_000, "multimidia")
        val s = f.builder.summary(final = false)
        assertEquals(6.0, s.num("multimedia.events")!!, 0.0)
        assertEquals(6.0, s.num("multimedia.fieldsSeen.cruisingRange.n")!!, 0.0)
        assertEquals(5.0, s.num("multimedia.fieldsSeen.avgFuelCont.n")!!, 0.0)
        assertNull(s.at("multimedia.fieldsSeen.speed"))
        assertEquals(1.0, s.num("multimedia.incompleteEvents")!!, 0.0)
        assertEquals(1.0, s.num("multimedia.absentCounts.avgFuelCont")!!, 0.0)
        assertEquals(0.0, s.num("multimedia.speedValidPct")!!, 0.0)
    }

    @Test
    fun `canais crus vistos em car_info_raw sao resumidos`() {
        val f = feed()
        val raw = JSONObject().put("channelsActive", 3).put("channelsTotal", 17)
            .put("tx", JSONObject().put("6", JSONObject().put("n", 40).put("bytes", 72)).put("17", JSONObject().put("n", 5).put("bytes", 24)))
            .put("props", JSONObject().put("values", JSONArray().put("0x11600207/0=3.2")))
        f.send("car_info_raw", raw, 1_000, "multimidia")
        val s = f.builder.summary(final = false)
        assertEquals(3.0, s.num("multimedia.rawChannels.channelsActiveMax")!!, 0.0)
        assertEquals(40.0, s.num("multimedia.rawChannels.txCalls.6")!!, 0.0)
        assertTrue(s.getJSONObject("multimedia").getJSONObject("rawChannels").getJSONArray("propsSeen").toString().contains("0x11600207"))
    }

    @Test
    fun `identidade do mapa e da curva ativos e mudancas ficam no resumo`() {
        val ctx = context().copy(mapHashAtStart = "mapaA", curveHashAtStart = "curvaA", carProfile = "208", vin = null)
        val f = feed(ctx)
        f.tel(0)
        f.send("k_read_curve", JSONObject().put("hash", "curvaA"), 500)
        f.send("k_read_map", JSONObject().put("hash", "mapaB"), 800)
        f.send("k_curve_state", JSONObject().put("factorsRaw", JSONArray().put(1).put(2)).put("origin", "ECU_NATIVE_OR_EXTERNAL"), 900)
        val s = f.builder.summary(final = false)
        assertEquals(true, s.at("mapCurve.identityKnown"))
        assertEquals("mapaA", s.at("mapCurve.map.hashAtStart"))
        assertEquals("mapaB", s.at("mapCurve.map.hashLast"))
        assertEquals("curvaA", s.at("mapCurve.curve.hashAtStart"))
        val last = s.at("mapCurve.curve.hashLast") as String
        assertEquals(64, last.length) // hash da lista crua da Curva K (30 pontos no campo)
        assertEquals("208", s.at("context.carProfile"))
        assertNull(s.at("context.vin"))
        assertEquals("abc123def456", s.at("build.commit"))
        assertTrue(s.getJSONObject("mapCurve").getJSONArray("changes").length() >= 2)
    }

    @Test
    fun `sem hash visto a identidade do mapa e desconhecida`() {
        val f = feed()
        f.tel(0)
        val s = f.builder.summary(final = false)
        assertEquals(false, s.at("mapCurve.identityKnown"))
        assertNull(s.at("mapCurve.map.hashLast"))
    }

    @Test
    fun `qualidade dos dados em percentual so com denominador`() {
        val f = feed()
        for (i in 0..9) f.tel(i * 250L, plausible = i != 0, level = if (i < 5) 60 else null)
        val s = f.builder.summary(final = false)
        assertEquals(90.0, s.num("quality.plausiblePct")!!, 0.01)
        assertEquals(50.0, s.num("quality.levelValidPct")!!, 0.01)
    }

    @Test
    fun `nivel fora de 0 a 255 e desconhecido`() {
        val f = feed()
        for (i in 0..9) f.tel(i * 1000L, level = -1)
        val s = f.builder.summary(final = false)
        assertNull(s.at("gnvLevel.startRaw"))
        assertEquals(0.0, s.num("quality.levelValidPct")!!, 0.0)
    }

    @Test
    fun `serie de 1 Hz tem cabecalho colunas e celula vazia para desconhecido`() {
        val f = feed()
        for (i in 0..11) {
            f.tel(i * 250L, rpm = 1000 + i, level = 60, gasMs = if (i < 4) 6.0 else null)
            if (i == 2) f.car(500, "speed" to 20, "cruisingRange" to 35)
        }
        val csv = f.builder.seriesCsv().trim().lines()
        val header = csv.first().split(',')
        assertEquals(listOf("t_s", "rpm", "map_bar", "speed_kmh", "speed_src", "moving", "moving_src", "gnv_level_raw",
            "gas_ms", "petrol_ms", "fuel"), header.take(11))
        assertTrue(header.contains("mm_range_km"))
        assertEquals(4, csv.size) // t=0,1,2 e cabecalho => 3 linhas de dados (0..2,75 s)
        val first = csv[1].split(',')
        assertEquals("0", first[0])
        assertEquals("20", first[3])
        assertEquals("MM", first[4])
        assertEquals("35", first[header.indexOf("mm_range_km")])
        val second = csv[2].split(',')
        assertEquals("", second[3]) // sem velocidade nesse segundo: vazio, nao 0
        assertEquals("", second[4])
    }

    @Test
    fun `serie respeita 1 MB por hora`() {
        val f = feed()
        for (sec in 0 until 3600) {
            f.tel(sec * 1000L, rpm = 1800, level = 60, gasMs = 12.345678, petrolMs = 3.456789, map = 0.4321)
            f.car(sec * 1000L, "speed" to 87, "odo" to 123456, "cruisingRange" to 350, "avgFuelCont" to 11.7,
                "instFuelCont" to 9.9, "fuelPercentage" to 50)
        }
        val bytes = f.builder.seriesCsv().toByteArray().size
        assertTrue("serie com $bytes bytes", bytes <= 1_000_000)
        assertTrue(f.builder.summary(final = true).toString().length <= 300_000)
    }

    @Test
    fun `janela de escrita periodica por tempo dos dados e nao do relogio`() {
        val f = feed()
        f.tel(0)
        assertFalse(f.builder.dueForWrite())
        f.tel(59_000)
        assertFalse(f.builder.dueForWrite())
        f.tel(61_000)
        assertTrue(f.builder.dueForWrite())
        f.builder.markWritten()
        assertFalse(f.builder.dueForWrite())
    }

    @Test
    fun `fechamento marca closedCleanly so com session_stopped`() {
        val f = feed()
        f.tel(0)
        assertNull(f.builder.summary(final = false).at("closedCleanly"))
        assertEquals(false, f.builder.summary(final = true).at("closedCleanly"))
        f.send("session_stopped", JSONObject().put("reason", "manual"), 500)
        val s = f.builder.summary(final = true)
        assertEquals(true, s.at("closedCleanly"))
        assertEquals("manual", s.at("identity.stopReason"))
    }
}
