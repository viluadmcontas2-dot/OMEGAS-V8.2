package com.omegas.prohub.calibration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KMapPhysicalAxesTest {
    @Test
    fun `physical axes are locked to the observed MP48 map order`() {
        assertArrayEquals(
            intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500),
            KMapPhysicalAxes.rpmBins(),
        )
        assertArrayEquals(
            doubleArrayOf(2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0),
            KMapPhysicalAxes.petrolBins(),
            0.0,
        )
        assertEquals("mp48-k-map-physical-axes-v1", KMapPhysicalAxes.SCHEMA)
        assertEquals("0cc7273171fbe47a8d28235be00f1af49889d0934f6fb3c73fca35ccd2fee7c7", KMapPhysicalAxes.LOCK_SHA256)
        assertTrue(KMapPhysicalAxes.json().getBoolean("immutablePhysicalContract"))
    }

    @Test
    fun `callers cannot mutate the locked arrays`() {
        val rpm = KMapPhysicalAxes.rpmBins()
        val petrol = KMapPhysicalAxes.petrolBins()
        rpm[0] = 500
        petrol[0] = 99.0
        assertEquals(850, KMapPhysicalAxes.rpmBins()[0])
        assertEquals(2.0, KMapPhysicalAxes.petrolBins()[0], 0.0)
    }
}
