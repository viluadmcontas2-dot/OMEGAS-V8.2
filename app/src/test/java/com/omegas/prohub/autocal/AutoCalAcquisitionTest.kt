package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalAcquisitionTest {
    @Test
    fun `ponto cru usa escalas e contador igual ao limiar fica valido`() {
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(6)),
            field("PETR_INJ_TBUF", intArrayOf(2048) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(512) + IntArray(17)),
            field("NUM_BUF_UPD_PETR", intArrayOf(6) + IntArray(17)),
        )
        val point = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points").getJSONObject(0)
        assertEquals(4.0, point.getDouble("timeMs"), 0.0001)
        assertEquals(0.5, point.getDouble("mapBar"), 0.0001)
        assertEquals("VALIDO", point.getString("state"))
        assertTrue(point.getBoolean("draw"))
    }

    @Test
    fun `ponto abaixo do limiar aparece como coletando e nao e desenhado`() {
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(6)),
            field("PETR_INJ_TBUF", intArrayOf(2048) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(512) + IntArray(17)),
            field("NUM_BUF_UPD_PETR", intArrayOf(3) + IntArray(17)),
        )
        val point = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points").getJSONObject(0)
        assertEquals("COLETANDO", point.getString("state"))
        assertFalse(point.getBoolean("draw"))
    }

    @Test
    fun `limiares distinguem baixa e normal por combustivel sem usar MaxAutomatch`() {
        val calibration = IntArray(10).also {
            it[2] = 7
            it[5] = 4
            it[8] = 9
        }
        val petrolCounts = IntArray(18).also {
            it[0] = 2
            it[6] = 6
        }
        val gasCounts = IntArray(18).also {
            it[0] = 4
            it[6] = 8
        }
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(2)),
            field("VECT_AUTOCAL_U8_2", intArrayOf(1)),
            field("CALIBRATION_VAL_1", calibration),
            field("PETR_INJ_TBUF", IntArray(18) { 2048 }),
            field("MNFLD_PRESS_BUF", IntArray(18) { 512 }),
            field("NUM_BUF_UPD_PETR", petrolCounts),
            field("PETR_INJ_TBUF_GAS", IntArray(18) { 2048 }),
            field("MNFLD_PRESS_BUF_GAS", IntArray(18) { 512 }),
            field("NUM_BUF_UPD_GAS", gasCounts),
        )
        val result = AutoCalAcquisition.fromSnapshot(snapshot)
        val points = result.getJSONArray("points")
        val petrolLow = points.getJSONObject(0)
        val petrolNormal = points.getJSONObject(6)
        val gasLow = points.getJSONObject(18)
        val gasNormal = points.getJSONObject(24)

        assertEquals(2, petrolLow.getInt("threshold"))
        assertEquals("VALIDO", petrolLow.getString("state"))
        assertEquals(7, petrolNormal.getInt("threshold"))
        assertEquals("COLETANDO", petrolNormal.getString("state"))
        assertFalse(petrolNormal.getBoolean("draw"))

        assertEquals(4, gasLow.getInt("threshold"))
        assertEquals("VALIDO", gasLow.getString("state"))
        assertTrue(gasLow.getBoolean("draw"))
        assertEquals(9, gasNormal.getInt("threshold"))
        assertEquals("COLETANDO", gasNormal.getString("state"))
        assertFalse(gasNormal.getBoolean("draw"))

        val thresholds = result.getJSONObject("thresholds")
        assertEquals(1, thresholds.getInt("maxAutomatch"))
        assertEquals(4, thresholds.getInt("gasLow"))
        assertEquals(9, thresholds.getInt("gasNormal"))
    }

    private fun snapshot(vararg fields: JSONObject) = JSONObject().put("fields", JSONArray(fields.toList()))

    private fun field(key: String, raw: IntArray) = JSONObject()
        .put("key", key)
        .put("rawValues", JSONArray(raw.toList()))
        .put("status", AutoCalFieldStatus.VALID.name)
}
