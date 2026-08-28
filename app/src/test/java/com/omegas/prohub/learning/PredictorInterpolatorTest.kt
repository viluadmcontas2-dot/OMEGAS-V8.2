package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorInterpolatorTest {
    @Test
    fun `legacy damped residuals cannot seed predicted ideal target`() {
        val learning = learning(
            listOf(
                direct(2, 2, 10.0, "visit-a"),
                direct(2, 6, 12.0, "visit-b"),
                direct(6, 4, 11.0, "visit-c"),
            ),
        )
        val surface = PredictorInterpolator.build(learning, confirmedMap(120))
        val cell = cell(surface, 4, 4)

        assertEquals("DESCONHECIDO", cell.getString("state"))
        assertFalse(cell.getBoolean("predicted"))
        assertFalse(cell.getBoolean("directObservation"))
        assertTrue(cell.isNull("targetK"))
        assertEquals(0.0, cell.getDouble("predictionConfidence"), 1e-12)
        assertEquals("NO_SUPPORT", cell.getString("predictionReason"))
        assertFalse(cell.getBoolean("automaticWrite"))
        assertFalse(surface.getJSONObject("interpolation").getBoolean("predictionsFeedConfidence"))
    }

    @Test
    fun `outside legacy residual support also remains unknown because there is no K star support`() {
        val learning = learning(
            listOf(
                direct(1, 1, 10.0, "visit-a"),
                direct(1, 2, 10.0, "visit-b"),
                direct(2, 1, 10.0, "visit-c"),
            ),
        )
        val cell = cell(PredictorInterpolator.build(learning, confirmedMap(120)), 10, 10)

        assertEquals("DESCONHECIDO", cell.getString("state"))
        assertFalse(cell.getBoolean("predicted"))
        assertTrue(cell.isNull("targetK"))
        assertEquals("NO_SUPPORT", cell.getString("predictionReason"))
    }

    @Test
    fun `repeated legacy visit cannot manufacture K star interpolation`() {
        val learning = learning(
            listOf(
                direct(2, 2, 10.0, "same-visit"),
                direct(2, 6, 10.0, "same-visit"),
                direct(6, 4, 10.0, "same-visit"),
            ),
        )
        val cell = cell(PredictorInterpolator.build(learning, confirmedMap(120)), 4, 4)

        assertEquals("DESCONHECIDO", cell.getString("state"))
        assertFalse(cell.getBoolean("predicted"))
        assertTrue(cell.isNull("targetK"))
        assertEquals("NO_SUPPORT", cell.getString("predictionReason"))
    }

    @Test
    fun `opposite legacy deltas stay diagnostic and do not become direction authority`() {
        val learning = learning(
            listOf(
                direct(2, 2, 10.0, "visit-a"),
                direct(2, 6, -10.0, "visit-b"),
                direct(6, 4, 8.0, "visit-c"),
            ),
        )
        val cell = cell(PredictorInterpolator.build(learning, confirmedMap(120)), 4, 4)

        assertEquals("DESCONHECIDO", cell.getString("state"))
        assertFalse(cell.getBoolean("predicted"))
        assertTrue(cell.isNull("targetK"))
        assertEquals("NO_SUPPORT", cell.getString("predictionReason"))
    }

    @Test
    fun `rebuilding same legacy snapshot remains stable and non predictive`() {
        val learning = learning(
            listOf(
                direct(2, 2, 10.0, "visit-a"),
                direct(2, 6, 12.0, "visit-b"),
                direct(6, 4, 11.0, "visit-c"),
            ),
        )
        val first = PredictorInterpolator.build(learning, confirmedMap(120))
        val second = PredictorInterpolator.build(JSONObject(learning.toString()), confirmedMap(120))
        val firstCell = cell(first, 4, 4)
        val secondCell = cell(second, 4, 4)

        assertTrue(firstCell.isNull("targetK"))
        assertTrue(secondCell.isNull("targetK"))
        assertEquals(firstCell.getString("predictionReason"), secondCell.getString("predictionReason"))
        assertEquals(firstCell.getDouble("predictionConfidence"), secondCell.getDouble("predictionConfidence"), 1e-12)
        assertFalse(firstCell.getBoolean("predicted"))
        assertFalse(secondCell.getBoolean("predicted"))
    }

    private data class Direct(val row: Int, val column: Int, val delta: Double, val visit: String)

    private fun direct(row: Int, column: Int, delta: Double, visit: String) = Direct(row, column, delta, visit)

    private fun learning(direct: List<Direct>): JSONObject {
        val residuals = JSONArray()
        val comparisons = JSONArray()
        direct.forEach { item ->
            residuals.put(JSONObject()
                .put("row", item.row)
                .put("column", item.column)
                .put("suggestedDeltaPercent", item.delta)
                .put("residualErrorPercent", item.delta)
                .put("uncertaintyPercent", 1.0)
                .put("confidence", 0.85)
                .put("confidenceStage", "ACCEPTED")
                .put("readiness", "AVAILABLE")
                .put("actionable", true))
            comparisons.put(JSONObject()
                .put("epoch", 1)
                .put("visit_id", item.visit)
                .put("continuous_cell_weights", JSONArray().put(JSONObject()
                    .put("row", item.row)
                    .put("column", item.column)
                    .put("weight", 1.0))))
        }
        return JSONObject()
            .put("epoch", 1)
            .put("advisorRevision", 7L)
            .put("assistedCalibration", JSONObject().put("mapResidualSuggestions", residuals))
            .put("nativeLearningAnchors", JSONArray())
            .put("comparisons", comparisons)
    }

    private fun confirmedMap(current: Int): JSONObject {
        val rows = JSONArray()
        repeat(KMapPhysicalAxes.WRITABLE_ROWS) {
            rows.put(JSONArray(List(KMapPhysicalAxes.COLUMNS) { current }))
        }
        return JSONObject()
            .put("complete", true)
            .put("sessionConfirmed", true)
            .put("hash", "map-hash")
            .put("rows", rows)
    }

    private fun cell(surface: JSONObject, row: Int, column: Int): JSONObject {
        val cells = surface.getJSONArray("cells")
        repeat(cells.length()) { index ->
            val cell = cells.getJSONObject(index)
            if (cell.getInt("row") == row && cell.getInt("column") == column) return cell
        }
        error("Cell [$row,$column] not found")
    }
}
