package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalenceTolerances
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/** Revisão adversarial P2-6/P2-7: faixa só vale com cobertura interna; o texto da tolerância é o valor real (±4%/±5%). */
class EquivalencePhasesGatesTest {
    private val monitor = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)
    private val noJournal = JSONObject()

    private fun index(ecuShare: Double = 0.0, interior: Boolean = true, ratio: Double = 1.0): JSONObject =
        JSONObject().put("ratio", ratio).put("samples", 100).put("petrolObservations", 500).put("gasObservations", 200)
            .put("bands", JSONArray((0 until 5).map { i ->
                JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                    .put("samples", 20).put("ratio", ratio).put("episodes", 3).put("ecuShare", ecuShare)
                    .put("interiorCovered", interior)
            }))

    @Test
    fun `a tolerancia das fases e a do cerebro - 4 por cento e 5 por cento na ECU`() {
        assertEquals(ln(1.0 + EquivalenceTolerances.MIN), EquivalencePhases.TOLERANCE_LOG, 1e-12)
        assertEquals(ln(1.0 + EquivalenceTolerances.MAX), EquivalencePhases.TOLERANCE_LOG_ECU_REF, 1e-12)
        assertTrue(EquivalencePhases.TOLERANCE_LEAVE_LOG < EquivalencePhases.TOLERANCE_LOG)
        assertTrue(EquivalencePhases.TOLERANCE_LEAVE_LOG_ECU_REF < EquivalencePhases.TOLERANCE_LOG_ECU_REF)
    }

    @Test
    fun `a frase de estavel diz a tolerancia que vale de verdade`() {
        val own = EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(), noJournal, 0)
        assertEquals("ESTAVEL", own.getString("phase"))
        assertTrue(own.getString("headline"), own.getString("headline").contains("±4%"))
        assertTrue(own.getString("headline"), !own.getString("headline").contains("±3%") && !own.getString("headline").contains("±6%"))
        val ecu = EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(ecuShare = 1.0), noJournal, 0)
        assertTrue(ecu.getString("headline"), ecu.getString("headline").contains("±5% onde a gasolina é a da ECU"))
        // 4,5% numa faixa de gasolina própria está fora; na referência da ECU (±5%) está dentro: o texto e a regra coincidem.
        assertEquals("PROPOSTA_PRONTA", EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(ratio = 1.045), noJournal, 0).getString("phase"))
        assertEquals("ESTAVEL", EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(ecuShare = 1.0, ratio = 1.045), noJournal, 0).getString("phase"))
        assertEquals("PROPOSTA_PRONTA", EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(ecuShare = 1.0, ratio = 1.06), noJournal, 0).getString("phase"))
    }

    @Test
    fun `faixa grossa sem leituras por dentro dela nao conta como medida`() {
        val r = EquivalencePhases(null) { 0L }.observe(true, monitor, null, index(interior = false), noJournal, 0)
        assertEquals("COLETANDO_NOSSOS", r.getString("phase"))
        assertEquals(0, r.getInt("bandsMeasured"))
        assertEquals(5, r.getJSONArray("bandsMissing").length())
    }
}
