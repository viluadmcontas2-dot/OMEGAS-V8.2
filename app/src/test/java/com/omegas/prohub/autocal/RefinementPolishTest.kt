package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lote A (becos sem saída do Refino) e E1 (ganho decrescente por ponto) no diário e nas fases. */
class RefinementPolishTest {
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private val flat = IntArray(30) { 16384 }
    private fun touched(value: Int) = flat.copyOf().also { for (i in 5..23) it[i] = value }

    private fun index(vararg bands: Triple<Double?, Int, Int?>): JSONObject = JSONObject().put("ratio", 1.0).put("samples", bands.sumOf { it.second })
        .put("petrolObservations", 500).put("gasObservations", 200)
        .put("bands", JSONArray(bands.mapIndexed { i, (ratio, n, episodes) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL).put("episodes", episodes ?: JSONObject.NULL)
        }))

    private fun journalIndex(ratio: Double) = index(
        Triple(ratio, 20, null), Triple(ratio, 20, null), Triple(ratio, 20, null), Triple(ratio, 20, null), Triple(ratio, 20, null),
    )

    // ------------------------------------------------------------------ A4 / E1: diário

    @Test
    fun `o experimento guarda a foto de antes e o ultimo vence`() {
        val j = RefinementJournal(null)
        j.recordCurveWrite(flat, touched(17000), axisRaw, journalIndex(1.05), "A", photoFile = "foto_A.json")
        assertEquals("foto_A.json", j.json().getJSONObject("latest").getString("photoFile"))
        j.recordCurveWrite(touched(17000), touched(17500), axisRaw, journalIndex(1.05), "B", photoFile = "foto_B.json")
        assertEquals("o Desfazer é da última gravação", "foto_B.json", j.json().getJSONObject("latest").getString("photoFile"))
    }

    @Test
    fun `ganho por ponto cai 1,0 - 0,7 - 0,5 a cada gravacao que o altera, independente do veredito`() {
        val j = RefinementJournal(null)
        val axisMs = axisRaw.map { it / 512.0 }
        assertTrue(j.pointGainScale(axisMs).all { it == 1.0 })
        j.recordCurveWrite(flat, touched(17000), axisRaw, journalIndex(1.05), "1")
        var scale = j.pointGainScale(axisMs)
        for (i in 0 until 30) assertEquals("ponto $i", if (i in 5..23) 0.7 else 1.0, scale[i], 1e-9)
        j.recordCurveWrite(touched(17000), touched(17500), axisRaw, journalIndex(1.05), "2")
        scale = j.pointGainScale(axisMs)
        for (i in 0 until 30) assertEquals("ponto $i", if (i in 5..23) 0.5 else 1.0, scale[i], 1e-9)
        j.recordCurveWrite(touched(17500), touched(17800), axisRaw, journalIndex(1.05), "3")
        assertEquals(0.5, j.pointGainScale(axisMs)[10], 1e-9)
    }

    @Test
    fun `contagem de passadas sobrevive a reabrir o app`() {
        val dir = java.nio.file.Files.createTempDirectory("journal-passes").toFile()
        try {
            val file = java.io.File(dir, "journal.json")
            RefinementJournal(file).recordCurveWrite(flat, touched(17000), axisRaw, journalIndex(1.05), "1")
            val reopened = RefinementJournal(file)
            assertEquals(0.7, reopened.pointGainScale(axisRaw.map { it / 512.0 })[10], 1e-9)
        } finally { dir.deleteRecursively() }
    }

    // ------------------------------------------------------------------ A3: falha parcial

    @Test
    fun `falha parcial entra no diario com a foto e encerra a verificacao anterior`() {
        val j = RefinementJournal(null)
        j.recordCurveWrite(flat, touched(17000), axisRaw, journalIndex(1.05), "ok", photoFile = "foto_ok.json")
        j.recordFailedWrite("foto_falha.json", "KF-9", partial = true)
        val latest = j.json().getJSONObject("latest")
        assertEquals("FALHA_PARCIAL", latest.getString("status"))
        assertEquals("foto_falha.json", latest.getString("photoFile"))
        assertTrue(latest.getBoolean("partial"))
        assertFalse("não há mais verificação aberta", j.evaluate(journalIndex(1.0)))
        assertEquals(0, j.restorePoints().length())
    }

    // ------------------------------------------------------------------ A7: só condução conta

    @Test
    fun `o orcamento da verificacao conta so condução em faixa alterada`() {
        var now = 1_000L
        val j = RefinementJournal(null) { now }
        j.recordCurveWrite(flat, touched(17000), axisRaw, journalIndex(1.05), "1")
        fun online() = j.json().getJSONObject("latest").getLong("onlineMs")
        // Sem amostras novas nas faixas: a verificação segue aberta (COLETANDO) e o relógio é o que se mede.
        val idx = index(Triple(null, 0, null), Triple(null, 0, null), Triple(null, 0, null), Triple(null, 0, null), Triple(null, 0, null))
        j.evaluate(idx, true, rpm = 870.0, petrolMs = 5.0)
        repeat(10) { now += 3_000; j.evaluate(idx, true, rpm = 870.0, petrolMs = 5.0) }
        assertEquals("lenta (rpm < 1000) não conta", 0L, online())
        repeat(10) { now += 3_000; j.evaluate(idx, true, rpm = 2200.0, petrolMs = 2.0) }
        assertEquals("faixa fora do livro/não alterada não conta", 0L, online())
        repeat(10) { now += 3_000; j.evaluate(idx, true, rpm = 2200.0, petrolMs = 5.0) }
        assertEquals("condução em faixa alterada conta", 30_000L, online())
        repeat(10) { now += 3_000; j.evaluate(idx, false, rpm = 2200.0, petrolMs = 5.0) }
        assertEquals("sem ECU online não conta", 30_000L, online())
    }

    // ------------------------------------------------------------------ fases

    private var now = 0L
    private fun monitor(count: Int, max: Int? = 3, enabled: Int? = 1) = JSONObject()
        .put("autoMatchCount", count).put("maxAutomatch", max ?: JSONObject.NULL).put("autoCalEnabled", enabled ?: JSONObject.NULL)
    private val noJournal = JSONObject().put("latest", JSONObject.NULL)
    private val offIndex = index(Triple(1.08, 20, null), Triple(1.06, 20, null), Triple(1.0, 20, null), Triple(null, 0, null), Triple(null, 0, null))

    @Test
    fun `proposta que expira continua valida com texto proprio e sem repetir o aviso`() {
        val p = EquivalencePhases(null) { now }
        assertEquals("PROPOSTA_PRONTA", p.observe(true, monitor(3), null, offIndex, noJournal, 0).getString("phase"))
        assertTrue(p.takeAlert() != null)
        now += EquivalencePhases.PHASE_BUDGET_MS.getValue("PROPOSTA_PRONTA")
        val expired = p.observe(true, monitor(3), null, offIndex, noJournal, 0)
        assertEquals("TENTATIVA_ENCERRADA", expired.getString("phase"))
        assertEquals("PROPOSTA_PRONTA", expired.getString("expiredFrom"))
        assertTrue(expired.getString("headline"), expired.getString("headline").contains("Proposta ainda válida, grave quando quiser"))
        assertFalse(expired.getString("headline").contains("suficientes"))
        assertNull("o prazo não repete o aviso", p.takeAlert())
        // Evidência nova abre outra tentativa; o aviso já dado não repete (debounce).
        now += 3_000
        val again = p.observe(true, monitor(3), null, offIndex.put("revision", 7), noJournal, 0)
        assertEquals("PROPOSTA_PRONTA", again.getString("phase"))
        assertNull(p.takeAlert())
    }

    @Test
    fun `ECU trabalhando que nao termina no prazo marca de onde expirou para oferecer a revisao`() {
        val p = EquivalencePhases(null) { now }
        assertEquals("ECU_TRABALHANDO", p.observe(true, monitor(1), null, offIndex, noJournal, 0).getString("phase"))
        now += EquivalencePhases.PHASE_BUDGET_MS.getValue("ECU_TRABALHANDO")
        val expired = p.observe(true, monitor(1), null, offIndex, noJournal, 0)
        assertEquals("TENTATIVA_ENCERRADA", expired.getString("phase"))
        assertEquals("ECU_TRABALHANDO", expired.getString("expiredFrom"))
        assertTrue(expired.getString("headline").contains("revise e grave"))
    }

    @Test
    fun `histerese de faixa entra em 4 por cento e so sai abaixo de 3 por cento`() {
        fun one(ratio: Double) = index(Triple(ratio, 20, null), Triple(1.0, 20, null), Triple(1.0, 20, null), Triple(null, 0, null), Triple(null, 0, null))
        val fresh = EquivalencePhases(null) { now }
        assertEquals("3,5% sozinho está dentro", "ESTAVEL", fresh.observe(true, monitor(3), null, one(1.035), noJournal, 0).getString("phase"))
        val p = EquivalencePhases(null) { now }
        assertEquals("PROPOSTA_PRONTA", p.observe(true, monitor(3), null, one(1.08), noJournal, 0).getString("phase"))
        assertEquals("3,5% depois de fora continua fora", "PROPOSTA_PRONTA", p.observe(true, monitor(3), null, one(1.035), noJournal, 0).getString("phase"))
        assertEquals("2,5% volta para dentro", "ESTAVEL", p.observe(true, monitor(3), null, one(1.025), noJournal, 0).getString("phase"))
    }

    @Test
    fun `faixa com poucos episodios nao conta como medida mesmo com muitos pares`() {
        val p = EquivalencePhases(null) { now }
        val three = index(Triple(1.0, 20, 3), Triple(1.0, 20, 3), Triple(1.0, 20, 3), Triple(null, 0, null), Triple(null, 0, null))
        assertEquals("ESTAVEL", p.observe(true, monitor(3), null, three, noJournal, 0).getString("phase"))
        val few = index(Triple(1.0, 20, 3), Triple(1.0, 20, 3), Triple(1.0, 20, 2), Triple(null, 0, null), Triple(null, 0, null))
        val q = EquivalencePhases(null) { now }
        val r = q.observe(true, monitor(3), null, few, noJournal, 0)
        assertEquals("COLETANDO_NOSSOS", r.getString("phase"))
        val missing = r.getJSONArray("bandsMissing")
        assertTrue((0 until missing.length()).any { missing.getJSONObject(it).getDouble("fromMs") == EquivalenceLedger.BANDS[2].first })
    }

    @Test
    fun `guia de cobertura do livro vira o proximo passo`() {
        val p = EquivalencePhases(null) { now }
        val guidance = "Falta dado na faixa 6,0–7,5 ms: dirija no GNV entre 0,50 e 0,62 bar (1 de 3 trechos)."
        val idx = index(Triple(null, 2, 1), Triple(null, 3, 1), Triple(null, 0, null), Triple(null, 0, null), Triple(null, 0, null)).put("coverageGuidance", guidance)
        val r = p.observe(true, monitor(3), null, idx, noJournal, 0)
        assertEquals("COLETANDO_NOSSOS", r.getString("phase"))
        assertEquals(guidance, r.getString("next"))
    }

    @Test
    fun `a verificacao nunca mostra mais minutos que o orcamento`() {
        val p = EquivalencePhases(null) { now }
        val journal = JSONObject().put("latest", JSONObject().put("id", "e").put("status", "VERIFICANDO").put("onlineMs", 20 * 60_000L))
        val r = p.observe(true, monitor(3), null, offIndex, journal, 0)
        assertEquals("VERIFICANDO", r.getString("phase"))
        assertTrue(r.getString("headline"), r.getString("headline").contains("15 de 15 min"))
        assertFalse(r.getString("headline").contains("20 de"))
    }
}
