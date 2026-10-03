package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefinementAutopilotTest {
    private var now = 0L
    private fun pilot() = RefinementAutopilot(null) { now }

    private fun monitor(count: Int, max: Int? = 3, enabled: Int? = 1) = JSONObject()
        .put("autoMatchCount", count).put("maxAutomatch", max ?: JSONObject.NULL).put("autoCalEnabled", enabled ?: JSONObject.NULL)

    private fun zone(index: Int) = when (index) { in 0..5 -> 0; in 6..9 -> 1; in 10..13 -> 2; else -> 3 }

    /** Formato da Platina (AutoCalAcquisition): ZONA_ADQUIRIDA/ATIVIDADE/AGUARDANDO + zoneAcquired por zona. */
    private fun acquisition(petrolAcquired: Int, gasAcquired: Int) = JSONObject().put("points", JSONArray().apply {
        listOf("GASOLINA" to petrolAcquired, "GNV" to gasAcquired).forEach { (fuel, acquired) ->
            repeat(18) { i ->
                val zoneDone = (0..17).filter { zone(it) == zone(i) }.all { it < acquired }
                put(JSONObject().put("fuel", fuel).put("zone", zone(i)).put("zoneAcquired", zoneDone)
                    .put("state", if (i < acquired && zoneDone) "ZONA_ADQUIRIDA" else if (i < acquired) "ATIVIDADE" else "AGUARDANDO"))
            }
        }
    })

    private fun index(vararg bands: Pair<Double?, Int>) = JSONObject().put("samples", bands.sumOf { it.second })
        .put("petrolObservations", 500).put("gasObservations", 200)
        .put("bands", JSONArray(bands.mapIndexed { i, (r, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", r ?: JSONObject.NULL)
        }))

    private val noJournal = JSONObject().put("latest", JSONObject.NULL)
    private val offIndex = index(1.08 to 20, 1.06 to 20, 1.0 to 20, null to 0, null to 0)

    @Test
    fun `enquanto a ECU esta no automatico o OMEGAS so observa`() {
        val p = pilot()
        val r = p.observe(true, monitor(1), acquisition(10, 8), offIndex, noJournal, 0)
        assertEquals("ECU_TRABALHANDO", r.getString("phase"))
        assertFalse(r.getBoolean("canDisconnect"))
        assertNull(p.takeAlert())
    }

    @Test
    fun `ECU no maximo de automaticos libera o refino e avisa uma vez`() {
        val p = pilot()
        val r = p.observe(true, monitor(3), acquisition(18, 15), offIndex, noJournal, 0)
        assertEquals("MAX_AUTOMATCH", r.getString("ecuDoneReason"))
        assertEquals("PROPOSTA_PRONTA", r.getString("phase"))
        assertTrue(p.takeAlert() != null)
        now += 3_000
        p.observe(true, monitor(3), acquisition(18, 15), offIndex, noJournal, 0)
        assertNull(p.takeAlert())
    }

    @Test
    fun `aquisicao completa sem confirmacao nativa continua esperando e acompanha novo contador`() {
        val p = pilot()
        p.observe(true, monitor(1, max = null), acquisition(18, 18), offIndex, noJournal, 0)
        repeat(220) { now += 3_000; p.observe(true, monitor(1, max = null), acquisition(18, 18), offIndex, noJournal, 0) }
        assertEquals("ECU_TRABALHANDO", p.json().getString("phase"))
        assertFalse(p.json().getBoolean("ecuDone"))
        now += 3_000
        val r = p.observe(true, monitor(2, max = null), acquisition(18, 18), offIndex, noJournal, 0)
        assertEquals("ECU_TRABALHANDO", r.getString("phase"))
    }

    @Test
    fun `todas as faixas dentro de 3 por cento pode desconectar`() {
        val p = pilot()
        val r = p.observe(true, monitor(0, enabled = 0), acquisition(0, 0), index(1.01 to 20, 0.99 to 20, 1.02 to 12, null to 0, null to 0), noJournal, 0)
        assertEquals("ESTAVEL", r.getString("phase"))
        assertTrue(r.getBoolean("canDisconnect"))
    }

    @Test
    fun `curva gravada em verificacao e trecho que piorou`() {
        val p = pilot()
        val verifying = JSONObject().put("latest", JSONObject().put("status", "VERIFICANDO"))
        assertEquals("VERIFICANDO", p.observe(true, monitor(3), acquisition(18, 18), offIndex, verifying, 0).getString("phase"))
        val worse = JSONObject().put("latest", JSONObject().put("status", "PIOROU_EM_PARTE"))
        assertEquals("RESTAURAR_TRECHO", p.observe(true, monitor(3), acquisition(18, 18), offIndex, worse, 4).getString("phase"))
    }

    @Test
    fun `sem pares suficientes pede os pontos que faltam`() {
        val p = pilot()
        val r = p.observe(true, monitor(3), acquisition(18, 18), index(null to 2, 1.1 to 3, null to 0, null to 0, null to 0), noJournal, 0)
        assertEquals("COLETANDO_NOSSOS", r.getString("phase"))
        assertTrue(r.getString("next").contains("ms"))
    }

    @Test
    fun `conta os pontos da ECU pelos estados da Platina e zonas adquiridas`() {
        val p = pilot()
        val r = p.observe(true, monitor(2), acquisition(8, 6), offIndex, noJournal, 0)
        assertEquals(8, r.getInt("petrolValid"))
        assertEquals(6, r.getInt("gasValid"))
        assertEquals(1, r.getInt("petrolZones"))
        assertEquals(1, r.getInt("gasZones"))
        assertEquals("ECU_TRABALHANDO", r.getString("phase"))
    }
}
