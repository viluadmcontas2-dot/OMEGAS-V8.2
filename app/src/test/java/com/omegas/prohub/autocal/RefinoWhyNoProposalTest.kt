package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motivo humano quando o Refino não sugere (sem regra interna no texto do dono). */
class RefinoWhyNoProposalTest {
    private fun gaps(total: Int, collected: Int) = JSONArray((0 until total).map {
        JSONObject().put("kind", "gap").put("state", if (it < collected) "coletado" else "falta")
    })

    private fun equivalence(index: Double?) = JSONObject().put("available", true)
        .put("nextAction", JSONObject().put("kind", "COLLECT").put("pointIndexes", JSONArray()))
        .put("index", index ?: JSONObject.NULL)

    private fun why(phase: String, between: JSONArray, index: Double?): String? {
        val s = RefinoState.build(JSONObject().put("phase", phase), equivalence(index), between, null, "GNV")
        return if (s.isNull("whyNoProposal")) null else s.getString("whyNoProposal")
    }

    @Test
    fun `faltam leituras em 2 faixas`() {
        assertEquals("Faltam leituras em 2 faixas.", why("COLETANDO_NOSSOS", gaps(5, 3), null))
    }

    @Test
    fun `uma faixa so usa o singular`() {
        assertEquals("Faltam leituras em 1 faixa.", why("COLETANDO_NOSSOS", gaps(5, 4), 0.9))
    }

    @Test
    fun `sem leitura nenhuma diz que ainda aprende`() {
        assertEquals("Ainda aprendendo seu motor para afirmar a diferença.", why("COLETANDO_NOSSOS", gaps(0, 0), null))
    }

    @Test
    fun `ECU ainda aprendendo explica por que nao propoe`() {
        val text = why("ECU_TRABALHANDO", gaps(5, 1), null)
        assertTrue(text, text != null && text.contains("ECU ainda está aprendendo"))
    }

    @Test
    fun `tudo medido e dentro da margem diz que nao ha ajuste`() {
        val text = why("COLETANDO_NOSSOS", gaps(5, 5), 0.97)
        assertTrue(text, text != null && text.contains("dentro da margem"))
    }
}
