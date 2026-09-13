package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueScientificRegionRecoveryTest {
    @Test
    fun `evidence uuid is independent from deterministic scientific region`() {
        val a = BlueScientificRegion.from(rpm = 870.0, mapBar = 0.44)
        val b = BlueScientificRegion.from(rpm = 872.0, mapBar = 0.441)
        assertEquals(a.id, b.id)
        assertTrue(a.physicallyMatches(rpm = 872.0, mapBar = 0.441))
        assertNotEquals("visit-a", a.id)
    }

    @Test
    fun `continuous physical match survives adjacent quantization boundary`() {
        val left = BlueScientificRegion.from(rpm = 899.0, mapBar = 0.449)
        val right = BlueScientificRegion.from(rpm = 901.0, mapBar = 0.451)
        assertTrue(left.physicallyMatches(right.rpmCenter, right.mapCenterBar))
    }
}
