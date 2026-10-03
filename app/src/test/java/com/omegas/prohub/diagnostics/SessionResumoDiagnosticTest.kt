package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

/** Classe 2: o mesmo evento do serviço precisa virar texto de diagnóstico. */
class SessionResumoDiagnosticTest {
    @Test fun verifiedWithoutConfirmedBandsDoesNotInventImprovement() {
        val resumo = SessionResumo("s", 1_000L, TimeZone.getTimeZone("UTC"))
        resumo.observe("refinement_verdict", JSONObject()
            .put("id", "e").put("status", "VERIFICADO")
            .put("ratioBefore", 1.12).put("ratioAfter", 1.10)
            .put("bands", org.json.JSONArray(listOf(JSONObject()
                .put("fromMs", 3.0).put("toMs", 4.5).put("verdict", "CURTA")))), 2_000L)
        val md = resumo.markdown()
        assertFalse("VERIFICADO só significa comparação concluída, não curva equivalente", md.contains("melhorou e foi confirmada"))
        assertTrue(md, md.contains("verificação concluída"))
    }

    @Test fun watchdogIsVisibleAndDoesNotBecomeSuccess() {
        val resumo = SessionResumo("s", 1_000L, TimeZone.getTimeZone("UTC"))
        val changed = resumo.observe("refinement_diagnostic", JSONObject()
            .put("reasonCode", "ECU_READ_TIMEOUT").put("failureDomain", "TRANSPORT")
            .put("headline", "A ECU não respondeu a tempo. A leitura continua sem gravar.")
            .put("diagnostic", JSONObject().put("elapsedMs", 30_000).put("budgetMs", 30_000)
                .put("bandsMeasured", 0).put("bandsOff", 0)), 31_000L)
        assertTrue(changed)
        val md = resumo.markdown()
        assertTrue(md, md.contains("O que aconteceu de estranho"))
        assertTrue(md, md.contains("ECU_READ_TIMEOUT"))
        assertTrue(md, md.contains("transporte"))
        assertTrue(md, md.contains("30000"))
        assertTrue(md, md.contains("A ECU não respondeu"))
        assertFalse(md, md.contains("gravação confirmada por diagnóstico"))
    }
}
