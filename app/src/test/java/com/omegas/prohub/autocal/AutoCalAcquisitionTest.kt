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
    fun `gas anterior nao herda contador do ciclo gnv atual`() {
        val currentCounts = IntArray(18).also { it[3] = 9 }
        val previousTimes = IntArray(18).also { it[3] = 2560 }
        val previousMaps = IntArray(18).also { it[3] = 614 }
        val snapshot = snapshot(
            field("NUM_BUF_UPD_GAS", currentCounts),
            field("PETR_INJ_TBUF_GAS_PREV", previousTimes),
            field("MNFLD_PRESS_BUF_GAS_PREV", previousMaps),
        )

        val points = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points")
        val previous = points.getJSONObject(36 + 3)
        assertEquals("GNV_ANTERIOR", previous.getString("fuel"))
        assertEquals("ANTERIOR", previous.getString("state"))
        assertTrue(previous.getBoolean("draw"))
        assertTrue(previous.isNull("counter"))
        assertTrue(previous.isNull("threshold"))
        assertEquals("HISTORICAL_BUFFER_NO_MATURITY_GATE", previous.getString("thresholdSemantics"))
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
        assertEquals(listOf(4, 8, 12), buildList {
            val values = thresholds.getJSONArray("zoneBoundariesInclusive")
            repeat(values.length()) { add(values.getInt(it)) }
        })
        assertTrue(thresholds.getBoolean("maturityThresholdsPromoted"))
        assertEquals(6, petrolLow.getInt("threshold"))
        assertEquals(3, petrolNormal.getInt("threshold"))
        assertEquals(3, gasLow.getInt("threshold"))
        assertEquals(3, gasNormal.getInt("threshold"))
    }

    @Test
    fun `zonas da ECU cortam em THD 5 9 13 - banda 5 pertence a zona 2`() {
        // Prova no fio (PortmonLOGNOVO): a bandeira de zona vira 1 com MAP 0,668-0,698 (zona 3) e 0,756-0,874
        // (zona 4); logo as fronteiras sao THD[5]=0,45, THD[9]=0,65, THD[13]=0,85 -> bandas 0-4 | 5-8 | 9-12 | 13-16.
        val gasCounts = IntArray(18).also { it[4] = 8; it[5] = 8; it[9] = 8; it[13] = 8 }
        val snapshot = snapshot(
            field("ACQUIRED_ZONES_GAS", intArrayOf(0, 1, 0, 1)),
            field("PETR_INJ_TBUF_GAS", IntArray(18) { 2000 }),
            field("MNFLD_PRESS_BUF_GAS", IntArray(18) { 500 }),
            field("NUM_BUF_UPD_GAS", gasCounts),
        )
        val points = AutoCalAcquisition.fromSnapshot(snapshot).getJSONArray("points")
        assertFalse("banda 4 (0,40-0,45) e zona 1", points.getJSONObject(18 + 4).getBoolean("zoneAcquired"))
        assertTrue("banda 5 (0,45-0,50) e zona 2", points.getJSONObject(18 + 5).getBoolean("zoneAcquired"))
        assertFalse("banda 9 (0,65-0,70) e zona 3", points.getJSONObject(18 + 9).getBoolean("zoneAcquired"))
        assertTrue("banda 13 (0,85-0,90) e zona 4", points.getJSONObject(18 + 13).getBoolean("zoneAcquired"))
    }

    private fun snapshot(vararg fields: JSONObject) = JSONObject().put("fields", JSONArray(fields.toList()))

    private fun field(key: String, raw: IntArray) = JSONObject()
        .put("key", key)
        .put("rawValues", JSONArray(raw.toList()))
        .put("status", AutoCalFieldStatus.VALID.name)
}
