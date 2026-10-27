package com.omegas.prohub.calibration

import com.omegas.prohub.session.CalibrationRevision
import com.omegas.prohub.session.CalibrationShape
import com.omegas.prohub.session.CalibrationState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationWriterReadBackTest {
    private fun desired() = CalibrationState(
        revision = CalibrationRevision(1, 2),
        curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 110 }
        },
    )

    @Test
    fun map_state_is_decoded_from_verified_rows_and_preserves_technical_row() {
        val rows = JSONArray()
        repeat(CalibrationShape.MAP_K_EDITABLE_ROWS) { row ->
            rows.put(JSONArray(List(CalibrationShape.MAP_K_COLUMNS) { column ->
                if (row == 3 && column == 4) 127 else 110
            }))
        }
        val extra = JSONArray(List(CalibrationShape.MAP_K_COLUMNS) { 119 })
        val status = JSONObject().put("details", JSONObject()
            .put("readbackValid", true)
            .put("rows", rows)
            .put("extraRow", extra))

        val readBack = CalibrationWriterReadBack.map(desired(), status)

        assertEquals(127, readBack.mapK[3][4])
        assertEquals(119, readBack.mapK[12][11])
        assertEquals(CalibrationRevision(1, 2), readBack.revision)
    }

    @Test
    fun curve_state_is_decoded_from_verified_q14_values() {
        val raw = JSONArray(List(CalibrationShape.CURVE_K_POINTS) { index ->
            if (index == 5) 18022 else 16384
        })
        val status = JSONObject().put("details", JSONObject()
            .put("readbackValid", true)
            .put("curve", JSONObject().put("factorsRaw", raw)))

        val readBack = CalibrationWriterReadBack.curve(desired(), status)

        assertEquals(18022.0 / 16384.0, readBack.curveK[5], 1e-12)
        assertEquals(1.0, readBack.curveK[0], 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unverified_payload_is_rejected() {
        CalibrationWriterReadBack.map(
            desired(),
            JSONObject().put("details", JSONObject().put("readbackValid", false)),
        )
    }
}
