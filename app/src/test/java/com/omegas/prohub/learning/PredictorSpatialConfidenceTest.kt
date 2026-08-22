package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorSpatialConfidenceTest {
    @Test
    fun `target inside physically distributed independent support is supported`() {
        val support = listOf(
            point("a", 1350.0, 3.0, 120.0, 0.90, "trip-a"),
            point("b", 4500.0, 3.0, 122.0, 0.85, "trip-b"),
            point("c", 3000.0, 10.0, 121.0, 0.88, "trip-c"),
        )
        val result = PredictorSpatialConfidence.evaluate(3000.0, 5.0, support)

        assertTrue(result.supported)
        assertEquals("SUPPORTED_INSIDE_PHYSICAL_HULL", result.reason)
        assertEquals(3, result.distinctTrajectories)
        assertTrue(result.confidence in 0.0..1.0)
        assertTrue(result.confidence > 0.0)
    }

    @Test
    fun `outside physical hull is explicit extrapolation with no prediction support`() {
        val support = listOf(
            point("a", 1350.0, 3.0, 120.0, 0.90, "trip-a"),
            point("b", 2500.0, 3.0, 121.0, 0.90, "trip-b"),
            point("c", 1850.0, 4.5, 122.0, 0.90, "trip-c"),
        )
        val result = PredictorSpatialConfidence.evaluate(6000.0, 14.0, support)

        assertFalse(result.supported)
        assertEquals("EXTRAPOLATION_OUTSIDE_SUPPORT_HULL", result.reason)
        assertEquals(0.0, result.confidence, 1e-12)
        assertEquals(0.0, result.extrapolationPenalty, 1e-12)
    }

    @Test
    fun `same trajectory repeated cannot manufacture independent support`() {
        val support = listOf(
            point("a", 1350.0, 3.0, 120.0, 0.95, "same-trip"),
            point("b", 4500.0, 3.0, 121.0, 0.95, "same-trip"),
            point("c", 3000.0, 10.0, 122.0, 0.95, "same-trip"),
        )
        val result = PredictorSpatialConfidence.evaluate(3000.0, 5.0, support)

        assertFalse(result.supported)
        assertEquals("INSUFFICIENT_TRAJECTORY_INDEPENDENCE", result.reason)
        assertEquals(1, result.distinctTrajectories)
    }

    @Test
    fun `contradictory target k lowers coherence and confidence`() {
        val coherent = listOf(
            point("a", 1350.0, 3.0, 120.0, 0.9, "a"),
            point("b", 4500.0, 3.0, 121.0, 0.9, "b"),
            point("c", 3000.0, 10.0, 120.5, 0.9, "c"),
        )
        val contradictory = listOf(
            point("a", 1350.0, 3.0, 100.0, 0.9, "a"),
            point("b", 4500.0, 3.0, 180.0, 0.9, "b"),
            point("c", 3000.0, 10.0, 120.0, 0.9, "c"),
        )

        val good = PredictorSpatialConfidence.evaluate(3000.0, 5.0, coherent)
        val bad = PredictorSpatialConfidence.evaluate(3000.0, 5.0, contradictory)

        assertTrue(good.supported && bad.supported)
        assertTrue(good.coherenceScore > bad.coherenceScore)
        assertTrue(good.confidence > bad.confidence)
    }

    @Test
    fun `distance follows physical axis spacing not one cell equals one unit`() {
        val shortPhysicalStep = PredictorSpatialConfidence.physicalDistance(850.0, 2.0, 1350.0, 2.0)
        val longPetrolStep = PredictorSpatialConfidence.physicalDistance(850.0, 16.0, 850.0, 18.0)
        val shortPetrolStep = PredictorSpatialConfidence.physicalDistance(850.0, 2.0, 850.0, 2.5)

        assertTrue(shortPetrolStep < longPetrolStep)
        assertTrue(shortPhysicalStep > 0.0)
        assertTrue(longPetrolStep > 0.0)
    }

    @Test
    fun `predictor spatial confidence never upgrades upstream support quality`() {
        val support = listOf(
            point("a", 1350.0, 3.0, 120.0, 0.20, "trip-a"),
            point("b", 4500.0, 3.0, 121.0, 0.20, "trip-b"),
            point("c", 3000.0, 10.0, 120.5, 0.20, "trip-c"),
        )

        val result = PredictorSpatialConfidence.evaluate(3000.0, 5.0, support)

        assertTrue(result.supported)
        assertTrue(result.qualityScore <= 0.20 + 1e-12)
        assertTrue(result.confidence <= result.qualityScore + 1e-12)
    }

    private fun point(
        id: String,
        rpm: Double,
        petrolMs: Double,
        targetK: Double,
        quality: Double,
        trajectory: String,
    ) = PredictorSpatialConfidence.SupportPoint(id, rpm, petrolMs, targetK, quality, trajectory)
}
