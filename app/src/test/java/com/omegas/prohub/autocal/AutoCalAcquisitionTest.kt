package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalAcquisitionTest {
    @Test
    fun `ponto cru usa escalas e atividade nao inventa maturidade`() {
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(6)),
            field("PETR_INJ_TBUF", intArrayOf(2048) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(512) + IntArray(17)),
            field("NUM_BUF_UPD_PETR", intArrayOf(6) + IntArray(17)),
        )
        val point = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points").getJSONObject(0)
        assertEquals(4.0, point.getDouble("timeMs"), 0.0001)
        assertEquals(0.5, point.getDouble("mapBar"), 0.0001)
        assertEquals("ATIVIDADE", point.getString("state"))
        assertTrue(point.getBoolean("draw"))
        assertEquals(6, point.getInt("threshold"))
        assertEquals("PROGBASE_DUMP_RUNTIME_SELECTOR", point.getString("thresholdSemantics"))
    }

    @Test
    fun `atividade parcial continua visivel sem classificar maturidade`() {
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(6)),
            field("PETR_INJ_TBUF", intArrayOf(2048) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(512) + IntArray(17)),
            field("NUM_BUF_UPD_PETR", intArrayOf(3) + IntArray(17)),
        )
        val point = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points").getJSONObject(0)
        assertEquals("ATIVIDADE", point.getString("state"))
        assertTrue(point.getBoolean("draw"))
        assertFalse(point.getBoolean("zoneAcquired"))
    }

    @Test
    fun `dump promove seletores de maturidade sem substituir zonas nativas`() {
        val calibration = intArrayOf(1, 3, 3, 1, 3, 3, 1, 3, 3, 1)
        val petrolCounts = IntArray(18).also {
            it[0] = 2
            it[6] = 6
        }
        val gasCounts = IntArray(18).also {
            it[0] = 4
            it[6] = 8
        }
        val snapshot = snapshot(
            field("VECT_AUTOCAL_U8_1", intArrayOf(6)),
            field("VECT_AUTOCAL_U8_2", intArrayOf(1)),
            field("CALIBRATION_VAL_1", calibration),
            field("ACQUIRED_ZONES_PETROL", intArrayOf(1, 0, 0, 0)),
            field("ACQUIRED_ZONES_GAS", intArrayOf(1, 0, 0, 0)),
            field("PETR_INJ_TBUF", IntArray(18) { 2000 }),
            field("MNFLD_PRESS_BUF", IntArray(18) { 500 }),
            field("NUM_BUF_UPD_PETR", petrolCounts),
            field("PETR_INJ_TBUF_GAS", IntArray(18) { 2000 }),
            field("MNFLD_PRESS_BUF_GAS", IntArray(18) { 500 }),
            field("NUM_BUF_UPD_GAS", gasCounts),
        )
        val result = AutoCalAcquisition.fromSnapshot(snapshot)
        val points = result.getJSONArray("points")
        val petrolLow = points.getJSONObject(0)
        val petrolNormal = points.getJSONObject(6)
        val gasLow = points.getJSONObject(18)
        val gasNormal = points.getJSONObject(24)

        assertEquals("ZONA_ADQUIRIDA", petrolLow.getString("state"))
        assertTrue(petrolLow.getBoolean("zoneAcquired"))
        assertEquals("ATIVIDADE", petrolNormal.getString("state"))
        assertFalse(petrolNormal.getBoolean("zoneAcquired"))

        assertEquals("ZONA_ADQUIRIDA", gasLow.getString("state"))
        assertTrue(gasLow.getBoolean("zoneAcquired"))
        assertEquals("ATIVIDADE", gasNormal.getString("state"))
        assertFalse(gasNormal.getBoolean("zoneAcquired"))

        val thresholds = result.getJSONObject("thresholds")
        assertEquals(1, thresholds.getInt("maxAutomatch"))
        assertEquals(6, thresholds.getInt("petrolIdleMinUpdate"))
        assertEquals(6, thresholds.getInt("petrolLow"))
        assertEquals(3, thresholds.getInt("petrolNormal"))
        assertEquals(3, thresholds.getInt("gasLow"))
        assertEquals(3, thresholds.getInt("gasNormal"))
        assertEquals("PROGBASE_DUMP_GRID_PROVEN", thresholds.getString("calibrationValueMapping"))
        assertEquals(5, thresholds.getInt("runtimeSelectorBoundaryInclusive"))
        assertEquals(listOf(5, 9, 13), buildList {
            val values = thresholds.getJSONArray("zoneBoundariesInclusive")
            repeat(values.length()) { add(values.getInt(it)) }
        })
        assertTrue(thresholds.getBoolean("maturityThresholdsPromoted"))
        assertEquals(6, petrolLow.getInt("threshold"))
        assertEquals(3, petrolNormal.getInt("threshold"))
        assertEquals(3, gasLow.getInt("threshold"))
        assertEquals(3, gasNormal.getInt("threshold"))
    }

    private fun snapshot(vararg fields: JSONObject) = JSONObject().put("fields", JSONArray(fields.toList()))

    private fun field(key: String, raw: IntArray) = JSONObject()
        .put("key", key)
        .put("rawValues", JSONArray(raw.toList()))
        .put("status", AutoCalFieldStatus.VALID.name)
}
