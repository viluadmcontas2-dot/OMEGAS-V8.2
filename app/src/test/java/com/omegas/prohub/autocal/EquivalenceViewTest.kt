package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalenceJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalenceViewTest {
    private fun build(brain: JSONObject? = null) =
        EquivalenceView.build(EquivalenceLedger(null), RefinementJournal(null), EquivalencePhases(null), StallWatch(null), brain)

    @Test
    fun `visao do Refino carrega o resultado do cerebro unico nas chaves planas que a UI le`() {
        val brain = JSONObject().put("ok", true).put("format", EquivalenceJson.FORMAT).put("available", true)
            .put("index", 0.5).put("coverage", 7).put("provisional", false)
            .put("nextAction", JSONObject().put("kind", "NOTHING").put("text", "Equivalente. Nada a fazer.").put("route", JSONObject.NULL))
            .put("points", JSONArray().put(JSONObject().put("index", 12).put("axisMs", 6.5).put("state", "POBRE")))
            .put("reference", JSONObject().put("frozen", true).put("canFreeze", true).put("frozenAt", 1L))
        val view = build(brain)
        assertEquals(0.5, view.getDouble("index"), 0.0)
        assertEquals(7, view.getInt("coverage"))
        assertFalse(view.getBoolean("provisional"))
        assertEquals("NOTHING", view.getJSONObject("nextAction").getString("kind"))
        assertEquals(12, view.getJSONArray("points").getJSONObject(0).getInt("index"))
        assertTrue(view.getJSONObject("reference").getBoolean("frozen"))
        assertEquals(0.5, view.getJSONObject("equivalence").getDouble("index"), 0.0)
        // as chaves antigas continuam
        assertTrue(view.has("autopilot"))
        assertTrue(view.getJSONObject("autopilot").has("phase"))
        assertTrue(view.has("bands"))
        // "available" nunca vaza para a raiz (a UI trata available=false como sem dados)
        assertFalse(view.has("available"))
    }

    @Test
    fun `sem resultado do cerebro a visao e a de sempre e sem curva lida so a Referencia aparece`() {
        val plain = build()
        assertTrue(plain.isNull("equivalence"))
        assertFalse(plain.has("index"))
        assertTrue(plain.has("autopilot"))

        val unread = JSONObject().put("ok", true).put("available", false).put("reason", "CURVA_K_NAO_LIDA")
            .put("reference", JSONObject().put("frozen", false).put("canFreeze", true))
        val view = build(unread)
        assertFalse(view.has("index"))
        assertFalse(view.has("available"))
        assertTrue(view.getJSONObject("reference").getBoolean("canFreeze"))
    }
}
