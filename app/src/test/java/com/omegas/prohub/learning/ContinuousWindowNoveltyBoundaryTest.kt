package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousWindowNoveltyBoundaryTest {
    @Test
    fun fullyNewBoundaryPreservesExplicitSeventyFivePercentBaseline() {
        assertEquals(0.75, ContinuousWindowNovelty.FULLY_NEW_FRACTION, 0.0)
        assertTrue(ContinuousWindowNovelty.Result(6, 8, 0.75, 100L).fullyNew)
        assertFalse(ContinuousWindowNovelty.Result(5, 8, 0.625, 100L).fullyNew)
    }

    @Test
    fun fullyDuplicateWindowHasZeroNoveltyAndCannotBecomeIndependentEvidence() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 1_000L,
            endedAtElapsedMs = 1_700L,
            frameCount = 8,
            medianIntervalMs = 100L,
            previouslyRepresentedThroughElapsedMs = 1_700L,
        )
        assertEquals(0, result.newFrames)
        assertEquals(0.0, result.fraction, 0.0)
        assertTrue(result.duplicate)
        assertFalse(result.fullyNew)
    }

    @Test
    fun overlappingWindowCountsOnlyFramesBeyondPreviousFrontier() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 1_000L,
            endedAtElapsedMs = 1_700L,
            frameCount = 8,
            medianIntervalMs = 100L,
            previouslyRepresentedThroughElapsedMs = 1_400L,
        )
        assertEquals(3, result.newFrames)
        assertEquals(3.0 / 8.0, result.fraction, 1e-12)
        assertFalse(result.fullyNew)
        assertFalse(result.duplicate)
    }
}
