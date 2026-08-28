package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTimeToZeroMetricsTest {
    @Test
    fun `first occurrence timings are monotonic and never overwritten`() {
        val tracker = PredictorTimeToZeroTracker(startedAtMs = 1_000L)
        tracker.recordFirstKstar(1_120L)
        tracker.recordFirstKstar(1_500L)
        tracker.recordConfirmedKstar(1_240L)
        tracker.recordFirstActionableMap(1_400L)
        tracker.recordZeroBand(1_900L)
        tracker.recordZeroBand(2_300L)
        val metrics = tracker.snapshot()
        assertEquals(120L, metrics.timeToFirstKstarMs)
        assertEquals(240L, metrics.timeToConfirmedKstarMs)
        assertEquals(400L, metrics.timeToFirstActionableMapMs)
        assertEquals(900L, metrics.timeToZeroBandMs)
    }

    @Test
    fun `predicted cells heldout error corrections abstention and regression are measured explicitly`() {
        val tracker = PredictorTimeToZeroTracker(startedAtMs = 0L)
        tracker.recordPredictedCellsBeforeDirectObservation(3)
        tracker.recordPredictedCellsBeforeDirectObservation(2)
        tracker.recordHeldoutPredictionError(0.10)
        tracker.recordHeldoutPredictionError(-0.20)
        repeat(3) { tracker.recordCorrection() }
        tracker.recordDecision(abstained = true)
        tracker.recordDecision(abstained = false)
        tracker.recordDecision(abstained = false)
        tracker.recordRevalidation(regressed = false)
        tracker.recordRevalidation(regressed = true)
        val metrics = tracker.snapshot()
        assertEquals(5, metrics.predictedCellsBeforeDirectObservation)
        assertEquals(0.15, metrics.heldoutPredictionError!!, 1e-12)
        assertEquals(3, metrics.correctionsToConverge)
        assertEquals(1.0 / 3.0, metrics.abstentionRate, 1e-12)
        assertEquals(0.5, metrics.regressionRate, 1e-12)
    }

    @Test
    fun `no samples produce null heldout error and zero rates`() {
        val metrics = PredictorTimeToZeroTracker(startedAtMs = 10L).snapshot()
        assertNull(metrics.heldoutPredictionError)
        assertTrue(metrics.abstentionRate == 0.0)
        assertTrue(metrics.regressionRate == 0.0)
    }
}
