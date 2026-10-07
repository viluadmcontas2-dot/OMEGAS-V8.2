package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achado 12: verificação que não fecha não bloqueia proposta nas faixas que a gravação não tocou. */
class PhasesVerificationReleaseTest {
    private var now = 0L

    private fun monitorDone() = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)

    private fun index(epochAt: Long, vararg bands: Pair<Double?, Int>) = JSONObject().put("samples", bands.sumOf { it.second })
        .put("petrolObservations", 500).put("gasObservations", 200).put("gasEpochAt", epochAt)
        .put("bands", JSONArray(bands.mapIndexed { i, (r, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", r ?: JSONObject.NULL)
        }))

    private val noJournal = JSONObject().put("latest", JSONObject.NULL)

    private fun verifying(vararg verdicts: String) = JSONObject().put("latest", JSONObject().put("id", "EXP-1").put("status", "VERIFICANDO")
        .put("onlineMs", 0L).put("bands", JSONArray(verdicts.mapIndexed { i, v ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second).put("verdict", v)
        })))

    @Test
    fun `verificacao que nao fecha libera proposta nas faixas que a gravacao nao tocou`() {
        val idx = index(100L, null to 0, 1.0 to 20, 1.0 to 20, 1.08 to 20, null to 0)
        // A gravação tocou só a faixa 0 (ainda coletando); a faixa 3, fora, não foi tocada.
        val free = EquivalencePhases(null) { now }
            .observe(true, monitorDone(), null, idx, verifying("COLETANDO", "NAO_ALTERADA", "NAO_ALTERADA", "NAO_ALTERADA", "NAO_ALTERADA"), 0)
        assertEquals("PROPOSTA_PRONTA", free.getString("phase"))
        assertTrue(free.has("verification"))
        // A faixa fora é a que está sendo verificada: continua verificando (nada de nova gravação por cima).
        val held = EquivalencePhases(null) { now }
            .observe(true, monitorDone(), null, idx, verifying("NAO_ALTERADA", "NAO_ALTERADA", "NAO_ALTERADA", "COLETANDO", "NAO_ALTERADA"), 0)
        assertEquals("VERIFICANDO", held.getString("phase"))
    }
}
