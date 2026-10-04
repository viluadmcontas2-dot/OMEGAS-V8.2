package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalenceEngine
import com.omegas.prohub.equivalence.EquivalenceInput
import com.omegas.prohub.equivalence.EquivalenceJson
import com.omegas.prohub.equivalence.EquivalenceReplaySupport
import com.omegas.prohub.equivalence.ExperienceMeter
import com.omegas.prohub.equivalence.UsageMeter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B (engasgo → ajuste local), C (pontos entre as bolinhas da ECU), D (refinoState só humano) e fluidez. */
class LogicFixesTest {
    @Test
    fun `regra de julgar - intervalo de confianca do erro, n efetivo minimo e sem relogio`() {
        assertFalse(EquivalenceEngine.isJudgeable(10, 2.5, 0.002))   // n efetivo pequeno: intervalo largo, ainda sem certeza
        assertFalse(EquivalenceEngine.isJudgeable(10, 3.0, 0.03))    // ruidoso demais para a margem de 4%
        assertTrue(EquivalenceEngine.isJudgeable(10, 3.0, 0.01))
        assertTrue(EquivalenceEngine.isJudgeable(80, 50.0, 0.05))    // muito dado julga mesmo com ruido de 5%
        assertFalse(EquivalenceEngine.isJudgeable(2, 50.0, 0.001))   // poucos pares
        assertFalse(EquivalenceEngine.isJudgeable(10, 50.0, null))   // dispersao desconhecida nunca julga
    }

    // ------------------------------------------------------------------ B: engasgo → ajuste local

    private fun point(i: Int, ms: Double, k: Double, state: String, mixture: Double?) = JSONObject()
        .put("index", i).put("axisMs", ms).put("kCurrent", k).put("kTarget", JSONObject.NULL)
        .put("mixture", mixture ?: JSONObject.NULL).put("tolerance", 0.04).put("state", state).put("samples", 20)

    /** Curva K de 30 pontos de 1 ms a 30 ms; [override] troca pontos específicos. */
    private fun brain(vararg override: JSONObject): JSONObject {
        val pts = JSONArray()
        for (i in 0 until 30) pts.put(override.firstOrNull { it.getInt("index") == i } ?: point(i, 1.0 + i, 1.0, "APRENDENDO", null))
        return JSONObject().put("available", true).put("points", pts)
            .put("nextAction", JSONObject().put("kind", "COLLECT").put("text", "Rode mais").put("pointIndexes", JSONArray()))
    }

    private fun stalls(count: Int, ms: Double = 5.2, map: Double = 0.45, rpm: Double = 1100.0, startAt: Long = 1_000L): JSONObject {
        val ats = JSONArray((0 until count).map { startAt + it * 90_000L })
        return JSONObject().put("regions", JSONArray().put(JSONObject()
            .put("fromMs", 5.0).put("toMs", 5.5).put("count", count).put("stallCount", 0).put("nearCount", count)
            .put("mapBar", map).put("rpmBefore", rpm).put("rpm", rpm).put("ms", ms)
            .put("firstAt", ats.getLong(0)).put("lastAt", ats.getLong(count - 1)).put("ats", ats)))
    }

    private fun region(j: JSONObject) = j.getJSONArray("regions").getJSONObject(0)

    @Test
    fun `regiao carrega onde e quantas vezes e os pontos da Curva K que a cobrem`() {
        val out = StallLocalFix.enrich(stalls(3), brain())
        val r = region(out)
        assertEquals(3, r.getInt("count")); assertEquals(0.45, r.getDouble("mapBar"), 1e-9); assertEquals(1100.0, r.getDouble("rpm"), 1e-9)
        assertEquals(5.2, r.getDouble("ms"), 1e-9); assertTrue(r.getLong("firstAt") <= r.getLong("lastAt"))
        // ms 5,0–5,5: nós 4 (5,0) e 5 (6,0)
        assertEquals(listOf(4, 5), (0 until r.getJSONArray("curvePoints").length()).map { r.getJSONArray("curvePoints").getInt(it) })
    }

    @Test
    fun `direcao desconhecida nao inventa - pede mais coleta e nao propoe`() {
        val out = StallLocalFix.enrich(stalls(4), brain())
        val r = region(out)
        assertFalse(r.has("proposal"))
        assertTrue(out.isNull("localProposal"))
        val text = r.getJSONObject("diagnosis").getString("text")
        assertTrue(text, text.contains("faltam dados para saber se é rico ou pobre"))
        assertEquals("DESCONHECIDA", r.getJSONObject("diagnosis").getString("direction"))
    }

    @Test
    fun `engasgo repetido com mistura pobre propoe ajuste local limitado a 8 por cento e a 1,20`() {
        val b = brain(point(4, 5.0, 1.00, "POBRE", 0.15), point(5, 6.0, 1.19, "POBRE", 0.10))
        val out = StallLocalFix.enrich(stalls(3), b)
        val p = region(out).getJSONObject("proposal")
        assertEquals("POBRE", p.getString("direction"))
        val items = p.getJSONArray("points")
        assertEquals(2, items.length())
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val step = it.getDouble("kAfter") / it.getDouble("kBefore") - 1.0
            assertTrue("passo ${step}", step in 0.0..0.0801)
            assertTrue(it.getDouble("kAfter") in 0.75..1.2001)
        }
        assertEquals(1.08, items.getJSONObject(0).getDouble("kAfter"), 0.001) // 15% pedido → 8%
        assertEquals(1.2, items.getJSONObject(1).getDouble("kAfter"), 0.001)  // 1,19 → teto 1,20 (não 1,29)
        // forma do `proposal` do cérebro: 30 inteiros, só os pontos da região mudam
        val lp = out.getJSONObject("localProposal")
        val cur = lp.getJSONArray("currentRaw"); val ref = lp.getJSONArray("refinedRaw")
        assertEquals(30, ref.length())
        for (i in 0 until 30) if (i !in listOf(4, 5)) assertEquals(cur.getInt(i), ref.getInt(i))
        assertTrue(ref.getInt(4) > cur.getInt(4))
        assertFalse(lp.getBoolean("automatic"))
        // vira a próxima ação do Refino (APPLY local) quando não há outra a aplicar
        val view = EquivalenceView.build(EquivalenceLedger(null), RefinementJournal(null), EquivalencePhases(null), watchWith(3), b)
        assertEquals("APPLY", view.getJSONObject("nextAction").getString("kind"))
        assertTrue(view.getJSONObject("nextAction").getBoolean("local"))
    }

    private fun watchWith(near: Int): StallWatch {
        val w = StallWatch(null)
        var t = 0L
        repeat(near) {
            // condução em GNV a ~1.400 rpm e 5,2 ms; depois o rpm cai para 450 e volta: quase apagou
            repeat(30) { w.accept(StallWatch.Frame(t, "GNV", 1_400.0, 0.45, 5.2)); t += 100 }
            w.accept(StallWatch.Frame(t, "GNV", 450.0, 0.30, 5.2)); t += 100
            repeat(5) { w.accept(StallWatch.Frame(t, "GNV", 1_300.0, 0.45, 5.2)); t += 100 }
            t += 20_000
        }
        return w
    }

    @Test
    fun `engasgo com mistura rica propoe empobrecer fora da baixa e a trava da baixa impede abaixo de 3,5 ms`() {
        val rich = StallLocalFix.enrich(stalls(3), brain(point(4, 5.0, 1.0, "RICO", -0.05), point(5, 6.0, 1.0, "RICO", -0.06)))
        val p = region(rich).getJSONObject("proposal")
        assertEquals("RICO", p.getString("direction"))
        for (i in 0 until p.getJSONArray("points").length()) assertTrue(p.getJSONArray("points").getJSONObject(i).getDouble("kAfter") < 1.0)
        val low = StallLocalFix.enrich(stalls(3, ms = 2.2).also { region(it).put("fromMs", 2.0).put("toMs", 2.5) },
            brain(point(1, 2.0, 1.0, "RICO", -0.06), point(2, 3.0, 1.0, "RICO", -0.06)))
        // pontos 1 (2,0 ms) e 2 (3,0 ms) estão abaixo de 3,5 ms: nunca empobrece
        assertFalse(region(low).has("proposal"))
        assertEquals("TRAVA_DA_BAIXA", region(low).getJSONObject("diagnosis").getString("technicalReason"))
    }

    @Test
    fun `um engasgo so ou ja gravado depois dele nao propoe e pontos que discordam tambem nao`() {
        val b = brain(point(4, 5.0, 1.0, "POBRE", 0.1), point(5, 6.0, 1.0, "POBRE", 0.1))
        assertFalse(region(StallLocalFix.enrich(stalls(1), b)).has("proposal"))
        // o dono gravou a Curva K depois dos engasgos: os antigos não contam (idempotência)
        assertFalse(region(StallLocalFix.enrich(stalls(3), b, sinceMs = 10_000_000L)).has("proposal"))
        val mixed = brain(point(4, 5.0, 1.0, "POBRE", 0.1), point(5, 6.0, 1.0, "RICO", -0.1))
        assertEquals("DIRECOES_DISCORDAM", region(StallLocalFix.enrich(stalls(3), mixed)).getJSONObject("diagnosis").getString("technicalReason"))
        val equal = brain(point(4, 5.0, 1.0, "EQUIVALENTE", 0.0), point(5, 6.0, 1.0, "EQUIVALENTE", 0.0))
        assertEquals("TODOS_EQUIVALENTES", region(StallLocalFix.enrich(stalls(3), equal)).getJSONObject("diagnosis").getString("technicalReason"))
    }

    @Test
    fun `enriquecer nunca altera a entrada e e idempotente`() {
        val input = stalls(3); val b = brain(point(4, 5.0, 1.0, "POBRE", 0.1))
        val before = input.toString()
        val a = StallLocalFix.enrich(input, b); val c = StallLocalFix.enrich(input, b)
        assertEquals(before, input.toString())
        assertEquals(a.toString(), c.toString())
    }

    @Test
    fun `sessoes reais - toda regiao tem onde e quantas e qualquer proposta respeita 8 por cento e 0,75 a 1,20`() {
        var regions = 0
        for (name in listOf(RealSessionReplaySupport.GNV_ONLY, RealSessionReplaySupport.AUTOMATCH, RealSessionReplaySupport.REFERENCE)) {
            val watch = StallWatch(null)
            val frames = RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name))
            frames.forEach { watch.accept(RealSessionReplaySupport.stallFrame(it)) }
            val brain = brainFor(name)
            val out = StallLocalFix.enrich(watch.json(), brain)
            val arr = out.getJSONArray("regions")
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i); regions++
                assertTrue(r.getDouble("mapBar") > 0 && r.getDouble("rpm") > 0 && r.getDouble("ms") > 0 && r.getInt("count") >= 1)
                assertTrue(r.getLong("firstAt") <= r.getLong("lastAt"))
                assertTrue("$name: região sem pontos da curva", r.getJSONArray("curvePoints").length() >= 1)
                assertTrue(r.getJSONObject("diagnosis").getString("text").isNotBlank())
                r.optJSONObject("proposal")?.let { p ->
                    for (j in 0 until p.getJSONArray("points").length()) {
                        val it = p.getJSONArray("points").getJSONObject(j)
                        assertTrue(Math.abs(it.getDouble("kAfter") / it.getDouble("kBefore") - 1.0) <= 0.0801)
                        assertTrue(it.getDouble("kAfter") in 0.75..1.2001)
                    }
                }
            }
        }
        assertTrue(regions > 0)
    }

    private fun brainFor(name: String, keep: (RealSessionReplaySupport.Telemetry) -> Boolean = { true }): JSONObject {
        val ledger = EquivalenceReplaySupport.ledger(name, keep)
        val seqK = when (name) { RealSessionReplaySupport.REFERENCE -> 2550; RealSessionReplaySupport.AUTOMATCH -> 2262; else -> 2336 }
        val refSeq = when (name) { RealSessionReplaySupport.REFERENCE -> 95; RealSessionReplaySupport.AUTOMATCH -> 634; else -> 642 }
        val (axis, k) = EquivalenceReplaySupport.curve(name, seqK)
        val usage = UsageMeter(null)
        EquivalenceReplaySupport.frames(name).forEach { usage.accept(it.t, it.fuel, it.rpm, it.map, 1L) }
        val r = EquivalenceEngine.evaluate(EquivalenceInput(axis, k, EquivalenceReplaySupport.reference(name, refSeq), null,
            ledger.petrolObservations(), ledger.gasObservations(), ExperienceMeter(null).reading(), usage.reading()))
        return EquivalenceJson.result(r, null, null, null)
    }

    @Test
    fun `sessoes reais curtas - sem portao de tempo a evidencia estatistica ja propoe e o indice nunca e 100 por cento falso`() {
        var proposals = 0
        for (name in listOf(RealSessionReplaySupport.GNV_ONLY, RealSessionReplaySupport.AUTOMATCH, RealSessionReplaySupport.REFERENCE)) {
            val frames = EquivalenceReplaySupport.frames(name)
            val first = frames.first { it.fuel == "GNV" }.t
            for (windowMin in listOf(3, 6, 12)) {
                val cut = first + windowMin * 60_000L
                val json = brainFor(name) { it.fuel != "GNV" || it.t <= cut }
                json.opt("index")?.let { if (it is Number) assertTrue("$name ${windowMin}min: índice $it parece o 100% falso", it.toDouble() < 0.95) }
                if (json.optJSONObject("nextAction")?.optString("kind") == "APPLY") proposals++
                // o texto da próxima ação nunca cita tempo/regra
                val text = json.optJSONObject("nextAction")?.optString("text").orEmpty().lowercase()
                for (f in listOf("minut", " min", "visita", "episód", "confiança")) assertFalse("$name: $text", text.contains(f))
            }
        }
        assertTrue("alguma janela curta deveria chegar a propor (propostas=$proposals)", proposals > 0)
    }

    // ------------------------------------------------------------------ C: pontos entre as bolinhas da ECU

    private fun drive(ledger: EquivalenceLedger, fuel: String, ms: Double, map: Double, start: Long, n: Int): Long {
        var t = start
        repeat(n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, 2000.0, map, ms)); t += 280 }
        return t
    }

    @Test
    fun `betweenPoints - um por intervalo entre as bandas da ECU, no maximo 2x e cache por revisao`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 5.2, 0.60, 0, 30)
        t = drive(ledger, "GNV", 5.5, 0.60, t + EvidenceTestSupport.VISIT_GAP, 20)
        val a = ledger.betweenPointsJson()
        val gaps = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.getString("kind") == "gap" }
        assertEquals(17, gaps.size)
        assertTrue(a.length() <= 2 * EcuAcquisitionTruth.BANDS)
        assertTrue(gaps.all { it.has("centerMapBar") && it.getJSONObject("gas").has("n") && it.getJSONObject("petrol").has("ms") && it.has("n") })
        assertTrue(gaps.zipWithNext().all { (x, y) -> x.getDouble("centerMs") < y.getDouble("centerMs") })
        val withData = gaps.filter { it.getInt("samples") > 0 }
        assertTrue(withData.isNotEmpty())
        for (g in withData) {
            assertEquals("coletado", g.getString("state"))
            assertTrue(g.getDouble("gnvMs") > 0 && g.getDouble("petrolMs") > 0)
            assertEquals(0.60, g.getDouble("mapBar"), 0.01)
        }
        assertTrue(gaps.filter { it.getInt("samples") == 0 }.all { it.getString("state") == "falta" && it.isNull("gnvMs") && it.isNull("diffPct") })
        // cache: mesma revisão = mesmo conteúdo; nova leitura muda
        assertEquals(a.toString(), ledger.betweenPointsJson().toString())
        drive(ledger, "GNV", 5.5, 0.60, t + EvidenceTestSupport.VISIT_GAP, 20)
        assertTrue(ledger.betweenPointsJson().toString() != a.toString())
    }

    @Test
    fun `betweenPoints nas sessoes reais nunca passa de 36 e so marca COLETADO com leituras`() {
        for (name in listOf(RealSessionReplaySupport.GNV_ONLY, RealSessionReplaySupport.AUTOMATCH, RealSessionReplaySupport.REFERENCE)) {
            val ledger = EquivalenceReplaySupport.ledger(name)
            val refSeq = when (name) { RealSessionReplaySupport.REFERENCE -> 95; RealSessionReplaySupport.AUTOMATCH -> 634; else -> 642 }
            ledger.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(EquivalenceReplaySupport.acquisition(name, refSeq))) // como o serviço
            val b = ledger.betweenPointsJson()
            assertTrue("$name: ${b.length()}", b.length() in 17..36)
            for (i in 0 until b.length()) {
                val o = b.getJSONObject(i)
                if (o.getString("state") == "coletado") assertTrue(o.getInt("samples") >= AutoMatchRefinedEngine.BAND_MATURE_COUNT)
            }
            if (name == RealSessionReplaySupport.GNV_ONLY) assertTrue((0 until b.length()).any { b.getJSONObject(it).getString("state") == "coletado" })
        }
    }

    // ------------------------------------------------------------------ D: refinoState só humano

    private val forbidden = listOf("minut", " min", "visita", "episód", "episod", "confiança", "confianca", "60 s", "trechos", "n efetivo")

    private fun assertHuman(s: JSONObject) {
        for (key in listOf("phase", "whatNow", "nextAction")) {
            val text = s.getString(key).lowercase()
            for (f in forbidden) assertFalse("$key cita regra interna '$f': $text", text.contains(f))
            assertFalse("$key com código cru: $text", Regex("[A-Z]{3,}_[A-Z_]+").containsMatchIn(s.getString(key)))
        }
        if (!s.isNull("whyNoProposal")) for (f in forbidden) assertFalse(s.getString("whyNoProposal").lowercase().contains(f))
        assertTrue(s.getJSONObject("technical").has("phase"))
    }

    private fun view(ledger: EquivalenceLedger, brain: JSONObject?, phases: EquivalencePhases = EquivalencePhases(null), watch: StallWatch = StallWatch(null)) =
        EquivalenceView.build(ledger, RefinementJournal(null), phases, watch, brain)

    @Test
    fun `refinoState - fases humanas, contagens e motivo quando nao propoe`() {
        // sem leitura: Lendo a ECU
        val s0 = view(EquivalenceLedger(null), null).getJSONObject("refinoState")
        assertEquals("Sem ECU", s0.getString("phase")); assertHuman(s0)
        // coletando: 17 intervalos, X coletados
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 5.0, 0.6, 0, 30)
        drive(ledger, "GNV", 5.5, 0.6, t + EvidenceTestSupport.VISIT_GAP, 20)
        val phases = EquivalencePhases(null) { 0L }
        phases.observe(true, JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1), null, ledger.index(), JSONObject().put("latest", JSONObject.NULL), 0)
        val collecting = view(ledger, brain(), phases).getJSONObject("refinoState")
        assertHuman(collecting)
        assertTrue(collecting.getString("phase"), collecting.getString("phase").startsWith("Coletando entre as faixas da ECU: "))
        val c = collecting.getJSONObject("counts")
        assertEquals(17, c.getInt("intervalsTotal"))
        assertEquals(17, c.getInt("intervalsCollected") + c.getInt("intervalsMissing"))
        assertFalse(collecting.isNull("whyNoProposal")); assertEquals(collecting.getString("whyNoProposal"), collecting.getString("reason"))
        assertTrue(collecting.getString("phase").contains("de 17 intervalos"))
        // pronto para gravar: APPLY do cérebro com 5 pontos
        val apply = brain().put("nextAction", JSONObject().put("kind", "APPLY").put("text", "5 pontos pobres entre 4,0 e 8,0 ms (5–9%) · Aplicar ajuste").put("route", "refino")
            .put("pointIndexes", JSONArray(listOf(4, 5, 6, 7, 8))))
        val ready = view(ledger, apply, phases).getJSONObject("refinoState")
        assertEquals("Pronto para gravar 5 pontos", ready.getString("phase"))
        assertTrue(ready.getBoolean("canAct")); assertEquals(5, ready.getJSONObject("counts").getInt("pointsToWrite"))
        assertEquals("Gravar 5 pontos na Curva K", ready.getString("nextAction")); assertHuman(ready)
        assertEquals("APPLY", ready.getJSONObject("technical").getString("nextActionKind"))
        // estável e verificando
        val stable = brain().put("index", 0.9).put("nextAction", JSONObject().put("kind", "NOTHING").put("text", "Equivalente. Nada a fazer.").put("pointIndexes", JSONArray()))
        assertEquals("Estável", view(ledger, stable, phases).getJSONObject("refinoState").getString("phase"))
        val proving = brain().put("nextAction", JSONObject().put("kind", "PROVING").put("text", "Rodando para provar o ajuste").put("pointIndexes", JSONArray()))
        assertEquals("Verificando", view(ledger, proving, phases).getJSONObject("refinoState").getString("phase"))
    }

    @Test
    fun `refinoState nas sessoes reais nunca cita regra interna e diz a ECU como verdade`() {
        for (name in listOf(RealSessionReplaySupport.GNV_ONLY, RealSessionReplaySupport.AUTOMATCH, RealSessionReplaySupport.REFERENCE)) {
            val ledger = EquivalenceReplaySupport.ledger(name)
            val seq = RealSessionReplaySupport.snapshots(RealSessionReplaySupport.fixture(name)).last()
            val acq = AutoCalAcquisition.fromSnapshot(seq)
            val phases = EquivalencePhases(null) { 0L }
            phases.observe(true, JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1), acq, ledger.index(), JSONObject().put("latest", JSONObject.NULL), 0)
            val v = view(ledger, brainFor(name), phases)
            val s = v.getJSONObject("refinoState")
            assertHuman(s)
            assertNotNull(v.getJSONArray("betweenPoints")); assertTrue(v.has("fluidity")); assertTrue(v.getJSONObject("autopilot").has("ecuTruth"))
        }
    }

    // ------------------------------------------------------------------ fluidez

    private fun dense(petrol: List<Double>, gas: List<Double>): JSONObject {
        fun lane(ms: List<Double>) = JSONArray(ms.mapIndexed { i, v -> JSONObject().put("mapBar", 0.3 + 0.05 * i).put("tpetMs", v).put("samples", 20) })
        return JSONObject().put("petrol", lane(petrol)).put("gas", lane(gas))
    }

    @Test
    fun `fluidez - gasolina linear e GNV com solavancos`() {
        val linear = List(12) { 3.0 + 0.5 * it }
        val jerky = List(12) { 3.0 + 0.5 * it + if (it % 2 == 0) 0.9 else -0.9 }
        val f = Fluidity.fromDense(dense(linear, jerky))
        assertEquals("LINEAR", f.getJSONObject("petrol").getString("verdict"))
        assertEquals("SOLAVANCOS", f.getJSONObject("gas").getString("verdict"))
        assertTrue(f.getJSONObject("gas").getDouble("jerkPct") > f.getJSONObject("petrol").getDouble("jerkPct"))
        assertTrue(f.getJSONObject("gas").getDouble("deviationPct") > f.getJSONObject("petrol").getDouble("deviationPct"))
        assertTrue(f.getJSONObject("gnv").getDouble("index") < f.getJSONObject("gasolina").getDouble("index"))
        assertEquals(f.getJSONObject("gas").getDouble("jerkPct"), f.getJSONObject("gnv").getDouble("jerks"), 0.0)
        assertEquals("LINEAR", Fluidity.fromDense(dense(linear, linear)).getJSONObject("gas").getString("verdict"))
        val none = Fluidity.fromDense(dense(listOf(3.0, 4.0), emptyList()))
        assertEquals("SEM_DADOS", none.getJSONObject("gas").getString("verdict"))
        assertTrue(none.getJSONObject("gas").isNull("jerkPct")) // desconhecido é null, nunca 0
        assertNull(Fluidity.fromDense(null).optJSONObject("x"))
    }
}
