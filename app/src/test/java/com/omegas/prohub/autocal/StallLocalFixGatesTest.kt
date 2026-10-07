package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * O ajuste local de engasgo passa pelos MESMOS portões do motor refinado: trava de inclinação [AutoMatchRefinedEngine.E_MAX],
 * histerese ([AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG]) e regressão; e só conta APAGOU/QUASE_APAGOU vindos de condução
 * (rpm ≥ 1200), nunca a lenta.
 */
class StallLocalFixGatesTest {
    private fun point(i: Int, ms: Double, k: Double, state: String, mixture: Double?) = JSONObject()
        .put("index", i).put("axisMs", ms).put("kCurrent", k).put("kTarget", JSONObject.NULL)
        .put("mixture", mixture ?: JSONObject.NULL).put("tolerance", 0.02).put("state", state).put("samples", 20)

    private fun brain(vararg override: JSONObject): JSONObject {
        val pts = JSONArray()
        for (i in 0 until 30) pts.put(override.firstOrNull { it.getInt("index") == i } ?: point(i, 1.0 + i, 1.0, "APRENDENDO", null))
        return JSONObject().put("available", true).put("points", pts)
    }

    private fun region(drivingAts: List<Long>?, ats: List<Long> = listOf(1_000L, 91_000L, 181_000L), rpm: Double = 1_400.0) =
        JSONObject().put("regions", JSONArray().put(JSONObject()
            .put("fromMs", 5.0).put("toMs", 5.5).put("count", ats.size).put("stallCount", 0).put("nearCount", ats.size)
            .put("mapBar", 0.45).put("rpmBefore", rpm).put("rpm", rpm).put("ms", 5.2)
            .put("firstAt", ats.first()).put("lastAt", ats.last()).put("ats", JSONArray(ats))
            .also { r -> drivingAts?.let { r.put("drivingAts", JSONArray(it)) } }))

    private fun first(j: JSONObject) = j.getJSONArray("regions").getJSONObject(0)

    @Test
    fun `engasgos da lenta nao contam para propor`() {
        val b = brain(point(4, 5.0, 1.0, "POBRE", 0.06), point(5, 6.0, 1.0, "POBRE", 0.06))
        // Três eventos, nenhum vindo de condução (rpm < 1200): não há repetição que justifique mexer.
        val idle = StallLocalFix.enrich(region(drivingAts = emptyList()), b)
        assertFalse(first(idle).has("proposal"))
        assertEquals(0, first(idle).getInt("recentCount"))
        assertEquals("POUCOS_ENGASGOS", first(idle).getJSONObject("diagnosis").getString("technicalReason"))
        // Sem a lista de condução (JSON antigo), a região inteira com rpm de lenta também não conta.
        assertEquals(0, first(StallLocalFix.enrich(region(drivingAts = null, rpm = 1_100.0), b)).getInt("recentCount"))
        assertTrue(first(StallLocalFix.enrich(region(drivingAts = listOf(1_000L, 91_000L)), b)).has("proposal"))
    }

    @Test
    fun `StallWatch separa os eventos de conducao dos da lenta`() {
        fun watch(rpm: Double): StallWatch {
            val w = StallWatch(null)
            var t = 0L
            repeat(3) {
                repeat(30) { w.accept(StallWatch.Frame(t, "GNV", rpm, 0.45, 5.2)); t += 100 }
                w.accept(StallWatch.Frame(t, "GNV", 450.0, 0.30, 5.2)); t += 100
                repeat(5) { w.accept(StallWatch.Frame(t, "GNV", rpm, 0.45, 5.2)); t += 100 }
                t += 20_000
            }
            return w
        }
        val driving = watch(1_400.0).json().getJSONArray("regions").getJSONObject(0)
        assertEquals(3, driving.getJSONArray("drivingAts").length())
        val idle = watch(1_100.0).json().getJSONArray("regions").getJSONObject(0)
        assertEquals(3, idle.getInt("count"))
        assertEquals(0, idle.getJSONArray("drivingAts").length())
    }

    @Test
    fun `ajuste local respeita a trava de inclinacao do motor`() {
        // Um ponto isolado pedindo +15% com os vizinhos parados: sem a trava viraria um dente de +8%.
        val out = StallLocalFix.enrich(region(drivingAts = listOf(1_000L, 91_000L)), brain(point(5, 6.0, 1.0, "POBRE", 0.15)))
        val lp = out.getJSONObject("localProposal")
        val refined = lp.getJSONArray("refinedRaw")
        assertTrue(refined.getInt(5) > lp.getJSONArray("currentRaw").getInt(5))
        for (j in 0 until 29) {
            val e = abs(ln(refined.getInt(j + 1).toDouble() / refined.getInt(j)) / ln((2.0 + j) / (1.0 + j)))
            assertTrue("inclinação $e entre ${1 + j} e ${2 + j} ms", e <= AutoMatchRefinedEngine.E_MAX + 0.01)
        }
    }

    @Test
    fun `passo abaixo da histerese do motor nao vira proposta`() {
        // +3% pedido: abaixo da histerese de 3,5% do motor refinado, é ruído.
        val out = StallLocalFix.enrich(region(drivingAts = listOf(1_000L, 91_000L)),
            brain(point(5, 6.0, 1.0, "POBRE", 0.03), point(6, 7.0, 1.0, "POBRE", 0.03)))
        assertFalse(first(out).has("proposal"))
        assertEquals("PASSO_PEQUENO", first(out).getJSONObject("diagnosis").getString("technicalReason"))
    }
}
