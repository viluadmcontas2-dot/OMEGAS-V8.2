package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Classe 2: entrada/saída pública, relógio simulado, sem writer ou matemática fake. */
class RefinementLifecycleRegressionTest {
    private var now = 1_000L
    private fun index(ratio: Double = 1.0) = JSONObject().put("samples", 60).put("revision", 1)
        .put("bands", JSONArray((0..2).map { i -> JSONObject()
            .put("fromMs", EquivalenceLedger.BANDS[i].first)
            .put("toMs", EquivalenceLedger.BANDS[i].second)
            .put("samples", 20).put("ratio", ratio) }))
    private fun done() = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)
    private val noJournal = JSONObject().put("latest", JSONObject.NULL)

    @Test fun offlineNeverPresentsLastStableStateAsCurrent() {
        val p = RefinementAutopilot(null) { now }
        assertEquals("ESTAVEL", p.observe(true, done(), null, index(), noJournal, 0).getString("phase"))
        now += 3_000
        val out = p.observe(false, done(), null, index(), noJournal, 0)
        assertEquals("SEM_ECU", out.getString("phase"))
        assertFalse(out.getBoolean("canDisconnect"))
        assertFalse(out.getBoolean("ecuDone"))
        assertTrue(out.isNull("autoMatchCount"))
    }

    @Test fun offlineCannotReuseStaleGasAcquisition() {
        val p = RefinementAutopilot(null) { now }
        val acquisition = JSONObject().put("points", JSONArray(listOf(
            JSONObject().put("fuel", "GNV").put("state", "VALIDO").put("zone", 0).put("zoneAcquired", true),
            JSONObject().put("fuel", "GASOLINA").put("state", "VALIDO").put("zone", 0).put("zoneAcquired", true)
        )))
        p.observe(true, done(), acquisition, index(), noJournal, 0)
        now += 3_000
        val offline = p.observe(false, done(), acquisition, index(), noJournal, 0)
        assertTrue("gasValid antigo não é medição atual", offline.isNull("gasValid"))
        assertTrue("petrolValid antigo não é medição atual", offline.isNull("petrolValid"))
        assertEquals(0, offline.getInt("gasZones"))
        assertEquals(0, offline.getInt("petrolZones"))
    }

    @Test fun readingWithoutAnyEcuResponseExpiresInThirtySeconds() {
        val p = RefinementAutopilot(null) { now }
        val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
        p.observe(true, null, null, empty, noJournal, 0)
        var out = JSONObject()
        repeat(10) { now += 3_000; out = p.observe(true, null, null, empty, noJournal, 0) }
        assertEquals("TENTATIVA_ENCERRADA", out.getString("phase"))
        assertEquals("ECU_READ_TIMEOUT", out.getString("reasonCode"))
        assertEquals("TRANSPORT", out.getString("failureDomain"))
        assertFalse(out.getBoolean("ecuDone"))
        assertFalse(out.getBoolean("canDisconnect"))
        assertTrue(out.getJSONObject("diagnostic").getLong("elapsedMs") >= 30_000L)
    }

    @Test fun failureThenCureProgressesWithoutUserReset() {
        val p = RefinementAutopilot(null) { now }
        val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
        p.observe(true, null, null, empty, noJournal, 0)
        repeat(15) { now += 3_000; p.observe(true, null, null, empty, noJournal, 0) }
        now += 3_000
        assertEquals("PROPOSTA_PRONTA", p.observe(true, done(), null, index(1.12), noJournal, 0).getString("phase"))
    }

    @Test fun everyPhaseDecisionExplainsNumbersAndCause() {
        val p = RefinementAutopilot(null) { now }
        val out = p.observe(true, done(), null, index(1.12), noJournal, 0)
        assertEquals("MEASURED_BANDS_OFF", out.getString("reasonCode"))
        assertEquals("NONE", out.getString("failureDomain"))
        assertEquals(3, out.getJSONObject("diagnostic").getInt("bandsOff"))
        assertEquals(3, out.getJSONObject("diagnostic").getInt("bandsMeasured"))
        assertFalse(out.getBoolean("automatic"))
    }

    @Test fun automaticWaitHasCeilingWithoutDeclaringEcuDone() {
        val p = RefinementAutopilot(null) { now }
        val working = JSONObject().put("autoMatchCount", 0).put("maxAutomatch", 3).put("autoCalEnabled", 1)
        p.observe(true, working, null, index(), noJournal, 0)
        var out = JSONObject()
        repeat(800) { now += 3_000; out = p.observe(true, working, null, index(), noJournal, 0) }
        assertEquals("TENTATIVA_ENCERRADA", out.getString("phase"))
        assertEquals("ECU_PROGRESS_TIMEOUT", out.getString("reasonCode"))
        assertFalse(out.getBoolean("ecuDone"))
        assertFalse(out.getBoolean("canDisconnect"))
    }
}
