package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedRobustPetrolSummaryTest {
    @Test
    fun seededFirstSampleIsNotDoubleCountedByImmediateRegionUpdate() {
        val summary = BoundedRobustPetrolSummary.seed(4.20)
        assertEquals(1L, summary.totalObserved)
        assertEquals(1, summary.retainedCount())

        summary.observe(4.20)
        assertEquals(1L, summary.totalObserved)
        assertEquals(1, summary.retainedCount())

        summary.observe(4.30)
        assertEquals(2L, summary.totalObserved)
        assertEquals(2, summary.retainedCount())
    }

    @Test
    fun singleLargeOutlierDoesNotMoveMedianLikeMeanWould() {
        val summary = BoundedRobustPetrolSummary.empty()
        repeat(30) { summary.observe(4.0) }
        summary.observe(99.0)

        assertEquals(31, summary.retainedCount())
        assertEquals(4.0, summary.median(), 1e-9)
        assertEquals(0.0, summary.mad(), 1e-9)
        assertTrue((30 * 4.0 + 99.0) / 31.0 > 7.0)
    }

    @Test
    fun longRunRemainsBoundedAndPreservesTotalObservationCount() {
        val summary = BoundedRobustPetrolSummary.empty()
        repeat(10_000) { index -> summary.observe(3.0 + (index % 7) * 0.01) }

        assertEquals(BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES, summary.retainedCount())
        assertEquals(10_000L, summary.totalObserved)
        assertTrue(summary.median() in 3.0..3.06)
    }

    @Test
    fun serializationRestoresBoundedSummaryAndNonFiniteInputIsIgnored() {
        val summary = BoundedRobustPetrolSummary.empty()
        summary.observe(4.1)
        summary.observe(Double.NaN)
        summary.observe(4.3)
        val restored = BoundedRobustPetrolSummary.fromJson(summary.toJson())

        assertEquals(2L, restored.totalObserved)
        assertEquals(2, restored.retainedCount())
        assertEquals(4.2, restored.median(), 1e-9)
        assertEquals(summary.toJson().getString("policy"), restored.toJson().getString("policy"))
    }

    @Test
    fun oldRegionWithoutRobustPayloadFallsBackToLegacyMeanOnce() {
        val restored = BoundedRobustPetrolSummary.fromJson(raw = null, fallback = 4.25)
        assertEquals(1L, restored.totalObserved)
        assertEquals(1, restored.retainedCount())
        assertEquals(4.25, restored.median(), 1e-9)
    }
}
