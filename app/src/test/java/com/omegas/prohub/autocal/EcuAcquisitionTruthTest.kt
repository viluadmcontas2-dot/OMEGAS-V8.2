package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A ECU é a verdade: zona/banda/contador lidos da ECU mandam; só falta o que ela realmente não tem. */
class EcuAcquisitionTruthTest {
    private fun zone(i: Int) = when (i) { in 0..5 -> 0; in 6..9 -> 1; in 10..13 -> 2; else -> 3 }

    /** Aquisição sintética no formato do AutoCalAcquisition: flags por zona e contadores por banda (limiar 3). */
    private fun acq(petrolFlags: List<Boolean>?, gasFlags: List<Boolean>?, petrolCount: Int, gasCount: Int): JSONObject {
        val points = JSONArray()
        for ((fuel, flags, count) in listOf(Triple("GASOLINA", petrolFlags, petrolCount), Triple("GNV", gasFlags, gasCount))) {
            repeat(18) { i ->
                val flag = flags?.get(zone(i)) == true
                points.put(JSONObject().put("fuel", fuel).put("zone", zone(i)).put("index", i).put("counter", count)
                    .put("threshold", 3).put("zoneAcquired", flag)
                    .put("state", if (count > 0 && flag) "ZONA_ADQUIRIDA" else if (count > 0) "ATIVIDADE" else "AGUARDANDO"))
            }
        }
        return JSONObject().put("points", points).put("zoneFlags", JSONObject()
            .put("petrol", petrolFlags?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("gas", gasFlags?.let { JSONArray(it) } ?: JSONObject.NULL))
    }

    private val all = listOf(true, true, true, true)
    private val none = listOf(false, false, false, false)

    @Test
    fun `ECU com 4 de 4 zonas nos dois combustiveis nunca falta zona`() {
        val t = EcuAcquisitionTruth.fromAcquisition(acq(all, all, 10, 10), 1, 3, 1)
        assertEquals(0, t.getJSONArray("missing").length())
        assertTrue(t.getBoolean("allZonesCovered"))
        assertFalse(t.getString("summary").lowercase().contains("falta"))
        // mesmo com contador 0 (AutoMatch ainda nao rodou): 4/4 nunca vira "faltam"
        val t0 = EcuAcquisitionTruth.fromAcquisition(acq(all, all, 10, 10), 0, 3, 1)
        assertEquals(0, t0.getJSONArray("missing").length())
    }

    @Test
    fun `bandas lidas maduras valem mesmo com a flag da ECU zerada`() {
        val t = EcuAcquisitionTruth.fromAcquisition(acq(none, none, 10, 10), 0, 3, 1)
        assertEquals(4, t.getJSONObject("gas").getInt("zonesCovered"))
        assertEquals(4, t.getJSONObject("petrol").getInt("zonesCovered"))
        assertEquals("BANDAS_LIDAS", t.getJSONObject("gas").getJSONArray("basis").getString(0))
        assertEquals(0, t.getJSONArray("missing").length())
    }

    @Test
    fun `so falta o que a ECU realmente nao tem`() {
        val t = EcuAcquisitionTruth.fromAcquisition(acq(all, listOf(true, true, false, true), 10, 0), 0, 3, 1)
        val missing = t.getJSONArray("missing")
        assertEquals(1, missing.length())
        assertEquals("GNV", missing.getJSONObject(0).getString("fuel"))
        assertEquals(3, missing.getJSONObject(0).getJSONArray("zones").getInt(0))
        // gasolina: 4/4 pelas flags, nunca listada
        assertEquals(4, t.getJSONObject("petrol").getInt("zonesCovered"))
    }

    @Test
    fun `AutoMatch ja entregue pela ECU nao e falta de aquisicao`() {
        val t = EcuAcquisitionTruth.fromAcquisition(acq(all, listOf(false, false, false, true), 0, 0), 2, 3, 1)
        assertTrue(t.getBoolean("delivered"))
        assertEquals(0, t.getJSONArray("missing").length())
        assertFalse(t.getString("summary").lowercase().contains("falta"))
    }

    @Test
    fun `sem leitura da ECU e desconhecido nunca zero`() {
        val t = EcuAcquisitionTruth.fromAcquisition(null, null, null, null)
        assertTrue(t.getJSONObject("gas").isNull("zonesCovered"))
        assertEquals(0, t.getJSONArray("missing").length())
        assertFalse(t.getBoolean("read"))
    }

    @Test
    fun `campo de zonas ausente nao vira faltam todas`() {
        val t = EcuAcquisitionTruth.fromAcquisition(acq(null, null, 0, 0), null, null, null)
        assertTrue(t.getJSONObject("gas").isNull("zoneFlags"))
    }

    @Test
    fun `piloto com 4 de 4 zonas e ECU no automatico nao pede para adquirir`() {
        val p = EquivalencePhases(null) { 0L }
        val index = JSONObject().put("samples", 0).put("petrolObservations", 0).put("gasObservations", 0).put("bands", JSONArray())
        val r = p.observe(true, JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1),
            acq(all, all, 10, 10), index, JSONObject().put("latest", JSONObject.NULL), 0)
        assertEquals("ECU_TRABALHANDO", r.getString("phase"))
        val text = (r.getString("headline") + " " + r.getString("next")).lowercase()
        assertFalse(text, text.contains("dirija normalmente nos dois"))
        assertEquals(4, r.getInt("gasZones"))
        assertEquals(4, r.getInt("petrolZones"))
    }

    @Test
    fun `COLETANDO nao pede gasolina quando a ECU ja entrega bandas de gasolina`() {
        val p = EquivalencePhases(null) { 0L }
        val index = JSONObject().put("samples", 0).put("petrolObservations", 0).put("gasObservations", 0).put("petrolReference", "NENHUMA")
            .put("bands", JSONArray())
        val r = p.observe(true, JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1),
            acq(none, all, 10, 10), index, JSONObject().put("latest", JSONObject.NULL), 0)
        assertEquals("COLETANDO_NOSSOS", r.getString("phase"))
        assertFalse(r.getString("next"), r.getString("next").contains("Rode alguns minutos na gasolina"))
    }

    @Test
    fun `fixtures reais - toda zona marcada pela ECU esta coberta e so falta o que a ECU nao tem`() {
        var checked = 0
        var bandsOverFlag = 0
        for (name in listOf(RealSessionReplaySupport.AUTOMATCH, RealSessionReplaySupport.GNV_ONLY, RealSessionReplaySupport.REFERENCE)) {
            val root = RealSessionReplaySupport.fixture(name)
            for (snap in RealSessionReplaySupport.snapshots(root)) {
                val a = RealSessionReplaySupport.acquisition(snap)
                val count = RealSessionReplaySupport.rawValues(snap, "NUM_AUTOMATCH_EXECUTED")?.firstOrNull()
                val t = EcuAcquisitionTruth.fromAcquisition(a, count, 3, 1)
                for ((key, fuel) in listOf("petrol" to "ACQUIRED_ZONES_PETROL", "gas" to "ACQUIRED_ZONES_GAS")) {
                    val flags = RealSessionReplaySupport.rawValues(snap, fuel) ?: continue
                    val f = t.getJSONObject(key)
                    for (z in 0 until 4) {
                        if (flags[z] > 0) assertTrue("${name} ${snap.getInt("sequence")} $key z$z", f.getJSONArray("zoneCovered").getBoolean(z))
                        else if (f.getJSONArray("zoneCovered").getBoolean(z)) bandsOverFlag++
                    }
                    checked++
                }
                // 4/4 nos dois combustiveis lidos da ECU => nunca falta
                val pf = RealSessionReplaySupport.rawValues(snap, "ACQUIRED_ZONES_PETROL")
                val gf = RealSessionReplaySupport.rawValues(snap, "ACQUIRED_ZONES_GAS")
                if (pf != null && gf != null && pf.all { it > 0 } && gf.all { it > 0 }) {
                    assertEquals(0, t.getJSONArray("missing").length())
                    assertTrue(t.getBoolean("allZonesCovered"))
                }
                // delivered => nada "falta"
                if (t.getBoolean("delivered")) assertEquals(0, t.getJSONArray("missing").length())
            }
        }
        assertTrue(checked > 20)
        // as sessoes reais tem bandas maduras com flag zerada: a leitura da ECU vale mais que a flag
        assertTrue("bandas lidas deveriam cobrir zonas com flag 0 nas sessoes reais", bandsOverFlag > 0)
    }

    @Test
    fun `projecao do AutoCal usa a verdade da ECU`() {
        val snap = RealSessionReplaySupport.snapshots(RealSessionReplaySupport.fixture(RealSessionReplaySupport.GNV_ONLY))
            .first { it.getInt("sequence") == 37 } // flags 0000, bandas maduras
        val a = AutoCalAcquisition.fromSnapshot(snap)
        val gas = EcuAcquisitionTruth.fuel(a, "GNV")
        assertNotNull(gas.covered)
        assertTrue(gas.zonesCovered!! >= 3)
        assertEquals(0, gas.zonesFlagged)
    }
}
