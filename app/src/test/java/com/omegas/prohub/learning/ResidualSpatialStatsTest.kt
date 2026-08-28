package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidualSpatialStatsTest {
    private fun cells(vararg triples: Triple<Int, Int, Double>): JSONArray = JSONArray().also { array ->
        triples.forEach { (row, column, residual) ->
            array.put(JSONObject().put("row", row).put("column", column).put("residualErrorPercent", residual))
        }
    }

    @Test
    fun `localized residual remains spatially concentrated`() {
        val stats = ResidualSpatialStats.from(cells(Triple(4, 5, 8.0)))
        assertTrue(stats.getBoolean("available"))
        assertEquals(1, stats.getInt("cell_count"))
        assertEquals(1, stats.getInt("row_span"))
        assertEquals(1, stats.getInt("column_span"))
        assertEquals(1.0, stats.getDouble("largest_same_sign_component_fraction"), 0.0)
        assertFalse(stats.getBoolean("chooses_map_or_curve"))
    }

    @Test
    fun `broad coherent residual exposes large connected footprint`() {
        val stats = ResidualSpatialStats.from(cells(
            Triple(2, 2, 6.0), Triple(2, 3, 5.0), Triple(3, 2, 7.0), Triple(3, 3, 6.5),
        ))
        assertEquals(4, stats.getInt("cell_count"))
        assertEquals(2, stats.getInt("row_span"))
        assertEquals(2, stats.getInt("column_span"))
        assertEquals(4, stats.getInt("largest_same_sign_component_cells"))
        assertEquals(1.0, stats.getDouble("dominant_sign_fraction"), 0.0)
    }

    @Test
    fun `contradictory checkerboard exposes low same-sign connectivity`() {
        val stats = ResidualSpatialStats.from(cells(
            Triple(2, 2, 6.0), Triple(2, 3, -6.0), Triple(3, 2, -5.0), Triple(3, 3, 5.0),
        ))
        assertEquals(4, stats.getInt("cell_count"))
        assertEquals(1, stats.getInt("largest_same_sign_component_cells"))
        assertEquals(0.5, stats.getDouble("dominant_sign_fraction"), 0.0)
        assertFalse(stats.getBoolean("classification_policy_applied"))
    }
}
