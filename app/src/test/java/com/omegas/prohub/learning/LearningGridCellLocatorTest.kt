package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningGridCellLocatorTest {
    @Test
    fun `typed locator matches canonical grid cell without materializing JSON weights`() {
        LearningCalibrationAuthority.endPhysicalSession()
        try {
            listOf(900.0, 1_500.0, 2_500.0, 3_700.0, 6_000.0).forEach { rpm ->
                listOf(1.2, 2.8, 4.0, 6.5, 10.0).forEach { petrolMs ->
                    val canonical = LearningGridProjection.cellFor(rpm, petrolMs)
                    val typed = LearningGridCellLocator.locate(rpm, petrolMs)
                    assertTrue(typed.geometryKnown)
                    assertEquals(canonical.getInt("row"), typed.row)
                    assertEquals(canonical.getInt("column"), typed.column)
                    assertEquals(canonical.getString("key"), typed.key)
                }
            }
        } finally {
            LearningCalibrationAuthority.endPhysicalSession()
        }
    }

    @Test
    fun `managed session without geometry stays unknown`() {
        LearningCalibrationAuthority.beginPhysicalSession()
        try {
            val typed = LearningGridCellLocator.locate(2_500.0, 4.0)
            assertFalse(typed.geometryKnown)
            assertEquals(-1, typed.row)
            assertEquals(-1, typed.column)
            assertEquals("UNKNOWN", typed.key)
        } finally {
            LearningCalibrationAuthority.endPhysicalSession()
        }
    }
}
