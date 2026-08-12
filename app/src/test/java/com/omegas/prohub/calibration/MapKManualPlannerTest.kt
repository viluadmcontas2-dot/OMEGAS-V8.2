package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapKManualPlannerTest {
    @Test
    fun percentAdjustmentMatchesExistingManualSemantics() {
        assertEquals(134, MapKManualPlanner.target(120, "percent", 12.0))
        assertEquals(114, MapKManualPlanner.target(120, "percent", -5.0))
    }

    @Test
    fun deltaAndAbsoluteTargetAreNormalizedInKotlin() {
        assertEquals(125, MapKManualPlanner.target(120, "delta", 5.0))
        assertEquals(150, MapKManualPlanner.target(120, "target", 150.0))
    }

    @Test
    fun safetyBoundsRemainIdentical() {
        assertEquals(100, MapKManualPlanner.target(120, "target", 10.0))
        assertEquals(255, MapKManualPlanner.target(120, "target", 999.0))
        assertEquals(100, MapKManualPlanner.target(101, "delta", -50.0))
    }

    @Test
    fun invalidModeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MapKManualPlanner.target(120, "automatic", 5.0)
        }
    }
}
