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
    fun `inside hull with independent direct visits creates predicted target`() {
        val learning = learning(
            listOf(
                direct(2, 2, 10.0, "visit-a"),
                direct(2, 6, 12.0, "visit-b"),
                direct(6, 4, 11.0, "visit-c"),
            ),
        )
        val surface = PredictorInterpolator.build(learning, confirmedMap(120))
        val cell = cell(surface, 4, 4)

        assertEquals("PREVISTO", cell.getString("state"))
        assertTrue(cell.getBoolean("predicted"))
        assertFalse(cell.getBoolean("directObservation"))
        assertTrue(cell.getInt("targetK") > 120)
        assertTrue(cell.getDouble("predictionConfidence") > 0.0)
        assertTrue(cell.getInt("distinctTrajectories") >= 2)
        assertFalse(cell.getBoolean("automaticWrite"))
        assertFalse(surface.getJSONObject("interpolation").getBoolean("predictionsFeedConfidence"))
    }

    @Test
    fun `outside direct support hull remains unknown`() {
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
        assertEquals("EXTRAPOLATION_OUTSIDE_SUPPORT_HULL", cell.getString("predictionReason"))
    }

    @Test
    fun `repeated same physical visit cannot manufacture interpolation`() {
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
        assertEquals("INSUFFICIENT_TRAJECTORY_INDEPENDENCE", cell.getString("predictionReason"))
    }

    @Test
    fun `independent trajectories that disagree in correction direction do not predict`() {
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
        assertEquals("DIRECTION_CONFLICT", cell.getString("predictionReason"))
    }

    @Test
    fun `rebuilding same scientific snapshot does not turn prediction into support`() {
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

        assertEquals(firstCell.getInt("targetK"), secondCell.getInt("targetK"))
        assertEquals(firstCell.getInt("supportCount"), secondCell.getInt("supportCount"))
        assertEquals(firstCell.getInt("distinctTrajectories"), secondCell.getInt("distinctTrajectories"))
        assertEquals(firstCell.getDouble("predictionConfidence"), secondCell.getDouble("predictionConfidence"), 1e-12)
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
