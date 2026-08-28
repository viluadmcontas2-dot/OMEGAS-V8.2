package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorSurfaceTest {
    @Test
    fun `direct residual plus native anchor stays diagnostic without fabricating ideal target`() {
        val row = 2
        val column = 3
        val learning = learning(
            residuals = JSONArray().put(residual(row, column, 10.0, actionable = true, stage = "ACCEPTED")),
            anchors = JSONArray().put(anchor(row, column, epoch = 4)),
            epoch = 4,
        )
        val surface = PredictorSurface.build(learning, confirmedMap(current = 120))
        val cell = cell(surface, row, column)

        assertEquals(144, surface.getJSONArray("cells").length())
        assertEquals("RPM_X_PETROL_INJECTION_MS", surface.getString("physicalAxis"))
        assertEquals("VALIDADO", cell.getString("state"))
        assertEquals(120, cell.getInt("currentK"))
        assertTrue(cell.isNull("targetK"))
        assertEquals(10.0, cell.getDouble("suggestedDeltaPercent"), 1e-9)
        assertEquals(1, cell.getInt("nativeAnchorCount"))
        assertFalse(cell.getBoolean("predicted"))
        assertFalse(cell.getBoolean("automaticWrite"))
        assertTrue(cell.getJSONArray("provenance").length() >= 3)
    }

    @Test
    fun `direct learning without native validation remains observed`() {
        val row = 5
        val column = 6
        val surface = PredictorSurface.build(
            learning(
                residuals = JSONArray().put(residual(row, column, 4.0, actionable = true, stage = "ACCEPTED")),
                anchors = JSONArray(),
                epoch = 2,
            ),
            confirmedMap(current = 120),
        )
        val cell = cell(surface, row, column)

        assertEquals("OBSERVADO", cell.getString("state"))
        assertEquals(0, cell.getInt("nativeAnchorCount"))
        assertTrue(cell.isNull("targetK"))
        assertFalse(cell.getBoolean("predicted"))
    }

    @Test
    fun `native anchor without local residual is observation not invented target`() {
        val row = 1
        val column = 8
        val surface = PredictorSurface.build(
            learning(JSONArray(), JSONArray().put(anchor(row, column, epoch = 3)), epoch = 3),
            confirmedMap(current = 121),
        )
        val cell = cell(surface, row, column)

        assertEquals("OBSERVADO", cell.getString("state"))
        assertTrue(cell.isNull("targetK"))
        assertTrue(cell.isNull("suggestedDeltaPercent"))
        assertFalse(cell.getBoolean("predicted"))
    }

    @Test
    fun `no scientific support remains unknown even when current map is known`() {
        val surface = PredictorSurface.build(learning(JSONArray(), JSONArray(), epoch = 1), confirmedMap(120))
        val cell = cell(surface, 11, 11)

        assertEquals("DESCONHECIDO", cell.getString("state"))
        assertEquals(120, cell.getInt("currentK"))
        assertTrue(cell.isNull("targetK"))
        assertFalse(surface.getBoolean("automaticWrite"))
        assertTrue(surface.isNull("writer"))
    }

    @Test
    fun `anchor from previous epoch cannot validate current surface`() {
        val row = 4
        val column = 4
        val surface = PredictorSurface.build(
            learning(
                JSONArray().put(residual(row, column, 5.0, actionable = true, stage = "CONFIRMED")),
                JSONArray().put(anchor(row, column, epoch = 6)),
                epoch = 7,
            ),
            confirmedMap(120),
        )

        assertEquals("OBSERVADO", cell(surface, row, column).getString("state"))
        assertEquals(0, cell(surface, row, column).getInt("nativeAnchorCount"))
        assertTrue(cell(surface, row, column).isNull("targetK"))
    }

    @Test
    fun `primary rpm map tinj science keeps advisor delta diagnostic only`() {
        val row = 3
        val column = 4
        val learning = learning(
            residuals = JSONArray().put(residual(row, column, 6.0, actionable = true, stage = "ACCEPTED")),
            anchors = JSONArray().put(anchor(row, column, epoch = 9)),
            epoch = 9,
        ).put("primaryAuthority", "RPM_MAP_PETROL_TINJ")

        val result = PredictorSurface.build(learning, confirmedMap(120))
        val projected = cell(result, row, column)

        assertEquals("VALIDADO", projected.getString("state"))
        assertTrue(projected.isNull("targetK"))
        assertEquals(6.0, projected.getDouble("suggestedDeltaPercent"), 1e-12)
        assertFalse(result.getBoolean("automaticWrite"))
    }

    @Test
    fun `predictor surface preserves upstream confidence instead of upgrading it`() {
        val row = 6
        val column = 5
        val learning = learning(
            residuals = JSONArray().put(
                residual(row, column, 3.0, actionable = true, stage = "ACCEPTED", confidence = 0.21),
            ),
            anchors = JSONArray().put(anchor(row, column, epoch = 5)),
            epoch = 5,
        )

        val projected = cell(PredictorSurface.build(learning, confirmedMap(120)), row, column)

        assertEquals(0.21, projected.getDouble("confidence"), 1e-12)
        assertTrue(projected.isNull("targetK"))
    }

    @Test
    fun `duplicate upstream payload keeps predictor revision token stable`() {
        val learning = learning(
            residuals = JSONArray().put(residual(2, 2, 4.0, actionable = true, stage = "ACCEPTED")),
            anchors = JSONArray().put(anchor(2, 2, epoch = 3)),
            epoch = 3,
        )
        val map = confirmedMap(120)

        val first = PredictorSurface.build(JSONObject(learning.toString()), JSONObject(map.toString()))
        val second = PredictorSurface.build(JSONObject(learning.toString()), JSONObject(map.toString()))

        assertEquals(first.getString("revisionToken"), second.getString("revisionToken"))
    }

    private fun learning(residuals: JSONArray, anchors: JSONArray, epoch: Int): JSONObject = JSONObject()
        .put("epoch", epoch)
        .put("advisorRevision", 12L)
        .put("assistedCalibration", JSONObject().put("mapResidualSuggestions", residuals))
        .put("nativeLearningAnchors", anchors)

    private fun residual(
        row: Int,
        column: Int,
        delta: Double,
        actionable: Boolean,
        stage: String,
        confidence: Double = 0.82,
    ): JSONObject = JSONObject()
        .put("row", row)
        .put("column", column)
        .put("suggestedDeltaPercent", delta)
        .put("residualErrorPercent", delta)
        .put("uncertaintyPercent", 1.5)
        .put("confidence", confidence)
        .put("confidenceStage", stage)
        .put("readiness", "AVAILABLE")
        .put("actionable", actionable)

    private fun anchor(row: Int, column: Int, epoch: Int): JSONObject = JSONObject()
        .put("calibrationEpoch", epoch)
        .put("scientificRevision", 5L)
        .put("nativeValidity", true)
        .put("correlationState", "CORRELATED")
        .put("rpm", KMapPhysicalAxes.rpmBins()[column])
        .put("petrolOnCngMs", KMapPhysicalAxes.petrolBins()[row])
        .put("mapBar", 0.60)
        .put("fingerprint", "anchor-$epoch-$row-$column")
        .put("correlationConfidence", 0.90)

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
