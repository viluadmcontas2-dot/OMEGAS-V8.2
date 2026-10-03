package com.omegas.prohub.calibration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCellProjectionTest {
    @Test
    fun `celula ao vivo entre quatro pontos tem pesos bilineares exatos`() {
        val t = LiveCellProjection.liveInterpolationJson(2_000.0, 4.2, 0.62, 42L, 1_000L, true)
        val cell = t.getJSONObject("cell")
        assertTrue(t.getBoolean("valid"))
        assertEquals("BILINEAR_RPM_X_PETROL_MS", t.getString("method"))
        assertEquals("mp48-k-map-physical-axes-v1", t.getString("axisSchema"))
        assertEquals("4:2", cell.getString("key"))
        assertEquals(1_850, cell.getInt("rpmBin"))
        assertEquals(4.5, cell.getDouble("petrolBin"), 1e-12)
        assertEquals(0.60, cell.getDouble("mapBin"), 1e-12)
        val w = cell.getJSONArray("continuousWeights")
        val expected = listOf(
            Triple(3, 2, 3.0 / 13),
            Triple(3, 3, 0.9 / 13),
            Triple(4, 2, 7.0 / 13),
            Triple(4, 3, 2.1 / 13),
        )
        assertEquals(4, w.length())
        expected.forEachIndexed { i, (r, c, v) ->
            val o = w.getJSONObject(i)
            assertEquals(r, o.getInt("row"))
            assertEquals(c, o.getInt("column"))
            assertEquals(v, o.getDouble("weight"), 1e-9)
        }
        assertEquals(1.0, t.getDouble("totalWeight"), 1e-9)
        assertFalse(t.getBoolean("affectsLearning"))
        assertFalse(t.getBoolean("affectsCalibration"))
    }

    @Test
    fun `telemetria parada fica invalida e cai no canto zero`() {
        val t = LiveCellProjection.liveInterpolationJson(0.0, 0.0, 0.0, 1L, 1L, true)
        assertFalse(t.getBoolean("valid"))
        assertEquals("0:0", t.getJSONObject("cell").getString("key"))
        assertEquals(0.20, t.getJSONObject("cell").getDouble("mapBin"), 1e-12)
    }

    @Test
    fun `eixos sao os do contrato fisico`() {
        assertArrayEquals(KMapPhysicalAxes.rpmBins(), LiveCellProjection.rpmBins)
        assertArrayEquals(KMapPhysicalAxes.petrolBins(), LiveCellProjection.petrolBins, 0.0)
    }
}
