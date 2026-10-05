package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assert.assertTrue
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
        val p = EquivalencePhases(null) { now }
        assertEquals("ESTAVEL", p.observe(true, done(), null, index(), noJournal, 0).getString("phase"))
        now += 3_000
        val out = p.observe(false, done(), null, index(), noJournal, 0)
        assertEquals("SEM_ECU", out.getString("phase"))
        assertFalse(out.getBoolean("canDisconnect"))
        assertFalse(out.getBoolean("ecuDone"))
        assertTrue(out.isNull("autoMatchCount"))
    }

    @Test fun offlineCannotReuseStaleGasAcquisition() {
        val p = EquivalencePhases(null) { now }
        val acquisition = JSONObject().put("points", JSONArray(listOf(
            JSONObject().put("fuel", "GNV").put("state", "VALIDO").put("zone", 0).put("zoneAcquired", true),
            JSONObject().put("fuel", "GASOLINA").put("state", "VALIDO").put("zone", 0).put("zoneAcquired", true)
        )))
        p.observe(true, done(), acquisition, index(), noJournal, 0)
        now += 3_000
        val offline = p.observe(false, done(), acquisition, index(), noJournal, 0)
        assertTrue("gasValid antigo não é medição atual", offline.isNull("gasValid"))
        assertTrue("petrolValid antigo não é medição atual", offline.isNull("petrolValid"))
        assertTrue(offline.isNull("gasZones")) // sem leitura da ECU: "—", nunca 0
        assertTrue(offline.isNull("petrolZones"))
    }

    @Test fun readingWithoutAnyEcuResponseExpiresInThirtySeconds() {
        val p = EquivalencePhases(null) { now }
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
        val p = EquivalencePhases(null) { now }
        val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
        p.observe(true, null, null, empty, noJournal, 0)
        repeat(15) { now += 3_000; p.observe(true, null, null, empty, noJournal, 0) }
        now += 3_000
        assertEquals("PROPOSTA_PRONTA", p.observe(true, done(), null, index(1.12), noJournal, 0).getString("phase"))
    }

    @Test fun everyPhaseDecisionExplainsNumbersAndCause() {
        val p = EquivalencePhases(null) { now }
        val out = p.observe(true, done(), null, index(1.12), noJournal, 0)
        assertEquals("MEASURED_BANDS_OFF", out.getString("reasonCode"))
        assertEquals("NONE", out.getString("failureDomain"))
        assertEquals(3, out.getJSONObject("diagnostic").getInt("bandsOff"))
        assertEquals(3, out.getJSONObject("diagnostic").getInt("bandsMeasured"))
        assertFalse(out.getBoolean("automatic"))
    }

    @Test fun acquisitionSilenceNeverConfirmsNativeCompletion() {
        val p = EquivalencePhases(null) { now }
        val acquired = JSONObject().put("points", JSONArray().apply {
            for (fuel in listOf("GNV", "GASOLINA")) for (zone in 0..3)
                put(JSONObject().put("fuel", fuel).put("zone", zone).put("zoneAcquired", true).put("state", "VALIDO"))
        })
        val working = JSONObject().put("autoMatchCount", 1).put("maxAutomatch", JSONObject.NULL).put("autoCalEnabled", 1)
        p.observe(true, working, acquired, index(1.12), noJournal, 0)
        repeat(220) { now += 3_000L; p.observe(true, working, acquired, index(1.12), noJournal, 0) }
        assertEquals("ECU_TRABALHANDO", p.json().getString("phase"))
        assertFalse(p.json().getBoolean("ecuDone"))
    }

    @Test fun nativeCompletionCannotOutliveMissingCounter() {
        val p = EquivalencePhases(null) { now }
        val acquired = JSONObject().put("points", JSONArray())
        assertEquals("ESTAVEL", p.observe(true, done(), acquired, index(), noJournal, 0).getString("phase"))
        now += 3_000L
        val missing = JSONObject().put("autoCalEnabled", 1)
        val after = p.observe(true, missing, acquired, index(), noJournal, 0)
        assertFalse("contador anterior não autoriza outra observação", after.getBoolean("ecuDone"))
        // Sem contador o app está lendo (ou a leitura falhou): nunca afirma "a ECU está no automático" (decisão do dono, 2026-10-04).
        assertEquals("LENDO_ECU", after.getString("phase"))
    }

    @Test fun reopeningAppCannotResetReadingDeadline() {
        val dir = java.nio.file.Files.createTempDirectory("refino-restart").toFile()
        try {
            val file = java.io.File(dir, "pilot.json")
            val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
            var p = EquivalencePhases(file, durationClock = { now }, clock = { 7_000L })
            p.observe(true, null, null, empty, noJournal, 0)
            repeat(3) {
                now += 10_000L
                p = EquivalencePhases(file, durationClock = { now }, clock = { 7_000L })
                p.observe(true, null, null, empty, noJournal, 0)
            }
            assertEquals("TENTATIVA_ENCERRADA", p.json().getString("phase"))
            assertEquals(30_000L, p.json().getJSONObject("diagnostic").getLong("elapsedMs"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun calendarJumpDoesNotChangeDeadlineOrDecision() {
        var wall = 1_000_000L
        val p = EquivalencePhases(null, durationClock = { now }, clock = { wall })
        val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
        p.observe(true, null, null, empty, noJournal, 0)
        repeat(9) {
            now += 3_000L
            wall += if (it % 2 == 0) 86_400_000L else -172_800_000L
            assertEquals("LENDO_ECU", p.observe(true, null, null, empty, noJournal, 0).getString("phase"))
        }
        now += 3_000L
        assertEquals("TENTATIVA_ENCERRADA", p.observe(true, null, null, empty, noJournal, 0).getString("phase"))
    }

    @Test fun everyWaitingPhaseHasAnIndependentExitAndRecoversOnNewEvidence() {
        val cases = listOf("COLETANDO_NOSSOS", "PROPOSTA_PRONTA", "RESTAURAR_TRECHO") // VERIFICANDO: sem teto (fecha só por evidência)
        for (phase in cases) {
            now = 1_000L
            val p = EquivalencePhases(null) { now }
            val idx = if (phase == "COLETANDO_NOSSOS") JSONObject().put("samples", 0).put("bands", JSONArray()) else index(1.12)
            val journal = when (phase) {
                "VERIFICANDO" -> JSONObject().put("latest", JSONObject().put("id", "e").put("status", "VERIFICANDO"))
                "RESTAURAR_TRECHO" -> JSONObject().put("latest", JSONObject().put("id", "e").put("status", "PIOROU_EM_PARTE"))
                else -> noJournal
            }
            val restore = if (phase == "RESTAURAR_TRECHO") 3 else 0
            assertEquals(phase, p.observe(true, done(), null, idx, journal, restore).getString("phase"))
            val budget = EquivalencePhases.PHASE_BUDGET_MS.getValue(phase)
            now += budget
            val expired = p.observe(true, done(), null, idx, journal, restore)
            assertEquals(phase, "TENTATIVA_ENCERRADA", expired.getString("phase"))
            assertEquals(budget, expired.getJSONObject("diagnostic").getLong("elapsedMs"))
            now += 3_000L
            val recovered = p.observe(true, done(), null, index(), noJournal, 0)
            assertEquals(phase, "ESTAVEL", recovered.getString("phase"))
        }
    }

    @Test fun automaticWaitHasCeilingWithoutDeclaringEcuDone() {
        val p = EquivalencePhases(null) { now }
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
