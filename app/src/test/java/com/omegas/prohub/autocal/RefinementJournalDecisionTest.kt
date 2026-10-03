package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

/** Classe 2: motivos e operandos saem do Journal real que julgou a gravação. */
class RefinementJournalDecisionTest {
    private val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private fun index(ratio: Double) = JSONObject().put("ratio", ratio)
        .put("bands", JSONArray(EquivalenceLedger.BANDS.map {
            JSONObject().put("fromMs", it.first).put("toMs", it.second).put("samples", 20).put("ratio", ratio)
        }))
    @Test fun realWorseningCarriesItsOperandsAndFunctionalCause() {
        val j = RefinementJournal(null) { 1_000L }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.02), "manual-test")
        j.evaluate(index(1.09))
        val latest = j.json().getJSONObject("latest")
        assertEquals("PIOROU_EM_PARTE", latest.getString("status"))
        assertEquals("WORSE_BANDS_DETECTED", latest.getString("reasonCode"))
        assertEquals("FUNCTIONAL", latest.getString("failureDomain"))
        val band = latest.getJSONArray("bands").getJSONObject(0)
        assertEquals("ERROR_INCREASE_EXCEEDS_MARGIN", band.getString("reasonCode"))
        val numbers = band.getJSONObject("decision")
        assertEquals(ln(1.02), numbers.getDouble("errorBeforeLog"), 1e-12)
        assertEquals(ln(1.09), numbers.getDouble("errorAfterLog"), 1e-12)
        assertTrue(numbers.getDouble("worseMarginLog") > 0.0)
        assertEquals(20, numbers.getInt("samplesBefore"))
        assertEquals(20, numbers.getInt("samplesAfter"))
    }
    @Test fun nativeChangeIsAnExplicitInvalidationNotTransportFailure() {
        val j = RefinementJournal(null) { 1_000L }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.12), "manual-test")
        j.interrupt("NATIVE_AUTOMATCH")
        val latest = j.json().getJSONObject("latest")
        assertEquals("INTERROMPIDO", latest.getString("status"))
        assertEquals("EXPERIMENT_INVALIDATED", latest.getString("reasonCode"))
        assertEquals("FUNCTIONAL", latest.getString("failureDomain"))
        assertEquals("NATIVE_AUTOMATCH", latest.getString("interruptReason"))
    }
    @Test fun elapsedTimeAloneDoesNotRepublishVisibleDecision() {
        var now = 1_000L
        val j = RefinementJournal(null) { now }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.12), "manual-test")
        val after = index(1.0)
        for (i in 1 until EquivalenceLedger.BANDS.size) {
            after.getJSONArray("bands").getJSONObject(i).put("samples", 0).put("ratio", JSONObject.NULL)
        }
        assertTrue(j.evaluate(after))
        assertEquals("VERIFICANDO", j.json().getJSONObject("latest").getString("status"))
        now += 3_000L
        assertFalse("sem amostra/veredito novo não há mudança visual", j.evaluate(after))
        assertEquals("tempo diagnóstico continua medido", 3_000L, j.json().getJSONObject("latest").getLong("onlineMs"))
    }

    @Test fun confirmedWritesHaveDistinctIdentityEvenWhenClockIsFrozen() {
        val j = RefinementJournal(null) { 1_000L }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.12), "first-confirmed")
        val first = j.json().getJSONObject("latest").getString("id")
        j.interrupt("NATIVE_AUTOMATCH")
        j.recordCurveWrite(IntArray(30) { 17000 }, IntArray(30) { 18000 }, axis, index(1.12), "second-confirmed")
        val second = j.json().getJSONObject("latest").getString("id")
        assertNotEquals("relógio repetido não pode confundir experimentos", first, second)
    }

    @Test fun transitionsAreDeliveredInOrderWithoutPollingAndSnapshotsAreIndependent() {
        val j = RefinementJournal(null) { 1_000L }
        val events = ArrayList<JSONObject>()
        j.setDecisionListener { events.add(it) }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.12), "first")
        j.interrupt("NATIVE_AUTOMATCH")
        j.recordCurveWrite(IntArray(30) { 17000 }, IntArray(30) { 18000 }, axis, index(1.12), "second")
        j.evaluate(index(1.0))
        assertEquals(listOf("MANUAL_WRITE_CONFIRMED", "EXPERIMENT_INVALIDATED",
            "MANUAL_WRITE_CONFIRMED", "BAND_VERIFICATION_COMPLETE"), events.map { it.getString("reasonCode") })
        assertEquals("VERIFICANDO", events.first().getString("status"))
        events.last().put("status", "CONSUMER_CHANGED_COPY")
        assertEquals("VERIFICADO", j.json().getJSONObject("latest").getString("status"))
        j.setDecisionListener(null)
        j.recordCurveWrite(IntArray(30) { 18000 }, IntArray(30) { 19000 }, axis, index(1.12), "detached")
        assertEquals(4, events.size)
    }

    @Test fun supersededVerificationIsDeliveredBeforeReplacement() {
        val j = RefinementJournal(null) { 1_000L }
        val events = ArrayList<JSONObject>()
        j.setDecisionListener { events.add(it) }
        j.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis, index(1.12), "first")
        j.recordCurveWrite(IntArray(30) { 17000 }, IntArray(30) { 18000 }, axis, index(1.12), "second")
        assertEquals(listOf("MANUAL_WRITE_CONFIRMED", "SUPERSEDED_BY_CONFIRMED_WRITE",
            "MANUAL_WRITE_CONFIRMED"), events.map { it.getString("reasonCode") })
        assertEquals("INTERROMPIDO", events[1].getString("status"))
        assertEquals(events[0].getString("id"), events[1].getString("id"))
        assertNotEquals(events[1].getString("id"), events[2].getString("id"))
    }
}
