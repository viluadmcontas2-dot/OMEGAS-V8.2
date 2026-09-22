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
            field("PETR_INJ_TBUF", intArrayOf(2000) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(500) + IntArray(17)),
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
            field("PETR_INJ_TBUF", intArrayOf(2000) + IntArray(17)),
            field("MNFLD_PRESS_BUF", intArrayOf(500) + IntArray(17)),
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


    @Test
    fun `GNV anterior nao herda contador nem maturidade do epoch atual`() {
        val thresholds = intArrayOf(
            154, 256, 307, 358, 410, 461,
            512, 563, 614, 666, 717, 768,
            819, 870, 922, 973, 1024, 1126,
        )
        val calibration = IntArray(10).also {
            it[5] = 3
            it[8] = 3
        }
        val currentCounts = IntArray(18).also { it[0] = 9 }
        val previousTimes = IntArray(18).also { it[0] = 2100 }
        val previousMaps = IntArray(18).also { it[0] = 400 }
        val snapshot = snapshot(
            field("MNFLD_PRESS_THD", thresholds),
            field("CALIBRATION_VAL_1", calibration),
            field("NUM_BUF_UPD_GAS", currentCounts),
            field("PETR_INJ_TBUF_GAS_PREV", previousTimes),
            field("MNFLD_PRESS_BUF_GAS_PREV", previousMaps),
        )

        val previous = AutoCalAcquisition.fromSnapshot(snapshot)
            .getJSONArray("points")
            .getJSONObject(36)

        assertEquals("GNV_ANTERIOR", previous.getString("fuel"))
        assertTrue(previous.getBoolean("previous"))
        assertFalse(previous.getBoolean("maturityApplicable"))
        assertTrue(previous.isNull("counter"))
        assertTrue(previous.isNull("threshold"))
        assertTrue(previous.isNull("maturityGroup"))
        assertEquals("EPOCA_ANTERIOR", previous.getString("state"))
        assertFalse(previous.getBoolean("draw"))
        assertEquals(0, previous.getInt("zone"))
        assertEquals(0.4, previous.getDouble("mapBar"), 0.0001)
    }

    @Test
    fun `regiao visual vem do MAP nativo e nao do indice do buffer`() {
        val thresholds = intArrayOf(
            154, 256, 307, 358, 410, 461,
            512, 563, 614, 666, 717, 768,
            819, 870, 922, 973, 1024, 1126,
        )
        val gasTimes = IntArray(18)
        val gasMaps = IntArray(18)
        val gasCounts = IntArray(18)
        gasTimes[17] = 3600
        gasMaps[17] = 400
        gasCounts[17] = 3
        val calibration = IntArray(10).also {
            it[5] = 3
            it[8] = 3
        }
        val snapshot = snapshot(
            field("MNFLD_PRESS_THD", thresholds),
            field("CALIBRATION_VAL_1", calibration),
            field("PETR_INJ_TBUF_GAS", gasTimes),
            field("MNFLD_PRESS_BUF_GAS", gasMaps),
            field("NUM_BUF_UPD_GAS", gasCounts),
        )

        val gasPoint17 = AutoCalAcquisition.fromSnapshot(snapshot)
            .getJSONArray("points")
            .getJSONObject(18 + 17)

        assertEquals(0, gasPoint17.getInt("zone"))
        assertEquals("NORMAL_INDEX", gasPoint17.getString("maturityGroup"))
        assertEquals(0.4, gasPoint17.getDouble("mapBar"), 0.0001)
        assertEquals(3, gasPoint17.getInt("threshold"))
        assertEquals("VALIDO", gasPoint17.getString("state"))
    }

    private fun snapshot(vararg fields: JSONObject) = JSONObject().put("fields", JSONArray(fields.toList()))

    private fun field(key: String, raw: IntArray) = JSONObject()
        .put("key", key)
        .put("rawValues", JSONArray(raw.toList()))
        .put("status", AutoCalFieldStatus.VALID.name)
}
