package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchResidualPlannerTest {
    @Test
    fun `residual usa somente comparacoes da epoca atual`() {
        val learning = learning(5.0, 5.5, listOf("old", "current"))
            .put("epoch", 2)
        learning.getJSONArray("comparisons").getJSONObject(0).put("epoch", 1).put("petrol_on_cng_ms", 6.0)
        learning.getJSONArray("comparisons").getJSONObject(1).put("epoch", 2).put("petrol_on_cng_ms", 5.0)

        val result = AutoMatchResidualPlanner.analyze(analysis(0.0), learning)

        assertEquals(1, result.getInt("comparisonCount"))
        result.getJSONArray("cells").let { cells ->
            repeat(cells.length()) { index ->
                assertEquals(0.0, cells.getJSONObject(index).getDouble("residualPercent"), 1e-9)
            }
        }
    }

    @Test
    fun `erro igual a tendencia global produz residual zero`() {
        val result = AutoMatchResidualPlanner.analyze(
            autoMatch = analysis(globalCorrection = 0.10),
            learningExport = learning(target = 5.0, observed = 5.5, visits = listOf("visit-1")),
        )
        assertTrue(result.getBoolean("available"))
        assertTrue(result.getBoolean("globalTrendRemoved"))
        assertFalse(result.getBoolean("dampingApplied"))
        val cells = result.getJSONArray("cells")
        assertTrue(cells.length() > 0)
        repeat(cells.length()) { index ->
            assertEquals(0.0, cells.getJSONObject(index).getDouble("residualPercent"), 1e-9)
            assertEquals("EQUIVALENT_AFTER_GLOBAL", cells.getJSONObject(index).getString("direction"))
        }
    }

    @Test
    fun `diferenca adicional de dois por cento permanece como residual local`() {
        val result = AutoMatchResidualPlanner.analyze(
            autoMatch = analysis(globalCorrection = 0.10),
            learningExport = learning(target = 5.0, observed = 5.6, visits = listOf("visit-1", "visit-2")),
        )
        val cells = result.getJSONArray("cells")
        assertTrue(cells.length() > 0)
        repeat(cells.length()) { index ->
            val cell = cells.getJSONObject(index)
            assertEquals(2.0, cell.getDouble("residualPercent"), 1e-9)
            assertEquals("INCREASE_LOCAL_CNG_DELIVERY", cell.getString("direction"))
            assertFalse(cell.getBoolean("automatic"))
            assertTrue(cell.getBoolean("requiresReview"))
        }
    }

    @Test
    fun `janelas da mesma visita continuam uma visita unica`() {
        val export = learning(target = 5.0, observed = 5.6, visits = listOf("same", "same", "same"))
        val result = AutoMatchResidualPlanner.analyze(analysis(0.10), export)
        val cells = result.getJSONArray("cells")
        repeat(cells.length()) { index ->
            assertEquals(1, cells.getJSONObject(index).getInt("uniqueVisits"))
            assertEquals("OBSERVED", cells.getJSONObject(index).getString("confidenceStage"))
        }
    }

    @Test
    fun `reconstrucao sem fator atual nao libera residual`() {
        val analysis = analysis(0.10)
        analysis.getJSONArray("points").getJSONObject(0).put("currentFactor", JSONObject.NULL)
        val result = AutoMatchResidualPlanner.analyze(analysis, learning(5.0, 5.5, listOf("v")))
        assertFalse(result.getBoolean("available"))
        assertFalse(result.getBoolean("automatic"))
        assertEquals(0, result.getJSONArray("cells").length())
    }

    @Test
    fun `sem comparacoes retorna tendencia disponivel e nenhuma celula`() {
        val result = AutoMatchResidualPlanner.analyze(
            analysis(0.10),
            JSONObject().put("comparisons", JSONArray()),
        )
        assertTrue(result.getBoolean("available"))
        assertEquals(0, result.getInt("cellCount"))
        assertTrue(result.getString("message").contains("Ainda não existem"))
    }

    private fun analysis(globalCorrection: Double): JSONObject {
        val points = JSONArray()
        repeat(30) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("referenceTimeMs", 0.5 + index * 0.5)
                .put("currentFactor", 1.0)
                .put("calculatedFactor", 1.0 + globalCorrection))
        }
        return JSONObject()
            .put("ok", true)
            .put("available", true)
            .put("points", points)
    }

    private fun learning(target: Double, observed: Double, visits: List<String>): JSONObject {
        val comparisons = JSONArray()
        visits.forEachIndexed { index, visit ->
            comparisons.put(JSONObject()
                .put("id", "comparison-$index")
                .put("visit_id", visit)
                .put("petrol_target_ms", target)
                .put("petrol_on_cng_ms", observed)
                .put("rpm", 2_500.0)
                .put("map_bar", 0.60)
                .put("quality", 1.0))
        }
        return JSONObject().put("comparisons", comparisons)
    }
}
