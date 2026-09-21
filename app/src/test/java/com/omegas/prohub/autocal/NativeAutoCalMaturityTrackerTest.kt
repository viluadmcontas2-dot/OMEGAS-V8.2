package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalMaturityTrackerTest {
    @Test
    fun `first observation is baseline even when bands are already mature`() {
        val tracker = NativeAutoCalMaturityTracker()
        val counters = IntArray(18) { 20 }

        val events = tracker.observe(counters, 5, 10, enabled = true, observedAtElapsedMs = 1_000L)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `crossing threshold emits once and later growth does not duplicate`() {
        val tracker = NativeAutoCalMaturityTracker()
        tracker.observe(IntArray(18), 5, 10, enabled = true, observedAtElapsedMs = 1_000L)
        val crossing = IntArray(18).also { it[3] = 5 }

        val first = tracker.observe(crossing, 5, 10, enabled = true, observedAtElapsedMs = 2_000L)
        val repeated = tracker.observe(crossing.copyOf().also { it[3] = 7 }, 5, 10, enabled = true, observedAtElapsedMs = 3_000L)

        assertEquals(1, first.size)
        assertEquals(3, first.single().bandIndex)
        assertEquals(0, first.single().zone)
        assertEquals(0, first.single().previousCounter)
        assertEquals(5, first.single().counter)
        assertEquals(5, first.single().threshold)
        assertEquals(1_000L, first.single().previousObservedAtElapsedMs)
        assertEquals(2_000L, first.single().observedAtElapsedMs)
        assertTrue(repeated.isEmpty())
    }

    @Test
    fun `normal band uses normal threshold and reports its zone`() {
        val tracker = NativeAutoCalMaturityTracker()
        tracker.observe(IntArray(18), 5, 10, enabled = true, observedAtElapsedMs = 1_000L)
        val counters = IntArray(18).also { it[12] = 10 }

        val events = tracker.observe(counters, 5, 10, enabled = true, observedAtElapsedMs = 2_000L)

        assertEquals(1, events.size)
        assertEquals(12, events.single().bandIndex)
        assertEquals(2, events.single().zone)
        assertEquals(10, events.single().threshold)
    }

    @Test
    fun `paused acquisition updates baseline without creating science`() {
        val tracker = NativeAutoCalMaturityTracker()
        tracker.observe(IntArray(18), 5, 10, enabled = true, observedAtElapsedMs = 1_000L)
        val pausedCounters = IntArray(18).also { it[2] = 5 }

        val paused = tracker.observe(pausedCounters, 5, 10, enabled = false, observedAtElapsedMs = 2_000L)
        val resumed = tracker.observe(pausedCounters, 5, 10, enabled = true, observedAtElapsedMs = 3_000L)

        assertTrue(paused.isEmpty())
        assertTrue(resumed.isEmpty())
    }

    @Test
    fun `identical reread never creates event`() {
        val tracker = NativeAutoCalMaturityTracker()
        val counters = IntArray(18).also { it[8] = 4 }
        tracker.observe(counters, 5, 10, enabled = true, observedAtElapsedMs = 1_000L)

        val events = tracker.observe(counters, 5, 10, enabled = true, observedAtElapsedMs = 2_000L)

        assertTrue(events.isEmpty())
    }
    @Test
    fun `failed correlation retries only after fresh mature counter growth`() {
        val tracker = NativeAutoCalMaturityTracker()
        tracker.observe(IntArray(18), 5, 10, enabled = true, observedAtElapsedMs = 1_000L)
        val crossing = IntArray(18).also { it[3] = 5 }

        val first = tracker.observe(crossing, 5, 10, enabled = true, observedAtElapsedMs = 2_000L)
        assertEquals(1, first.size)
        tracker.recordCorrelationResult(3, correlated = false)

        val unchanged = tracker.observe(crossing, 5, 10, enabled = true, observedAtElapsedMs = 3_000L)
        val grown = crossing.copyOf().also { it[3] = 6 }
        val retry = tracker.observe(grown, 5, 10, enabled = true, observedAtElapsedMs = 4_000L)

        assertTrue(unchanged.isEmpty())
        assertEquals(1, retry.size)
        assertEquals(3, retry.single().bandIndex)
        assertTrue(retry.single().correlationRetry)

        tracker.recordCorrelationResult(3, correlated = true)
        val afterSuccess = grown.copyOf().also { it[3] = 7 }
        assertTrue(tracker.observe(afterSuccess, 5, 10, enabled = true, observedAtElapsedMs = 5_000L).isEmpty())
    }

    @Test
    fun `mature baseline can retry on later growth but reset clears retry lifecycle`() {
        val tracker = NativeAutoCalMaturityTracker()
        val mature = IntArray(18).also { it[8] = 10 }

        assertTrue(tracker.observe(mature, 5, 10, enabled = true, observedAtElapsedMs = 1_000L).isEmpty())
        val grown = mature.copyOf().also { it[8] = 11 }
        val retry = tracker.observe(grown, 5, 10, enabled = true, observedAtElapsedMs = 2_000L)

        assertEquals(1, retry.size)
        assertTrue(retry.single().correlationRetry)

        tracker.reset()
        val postReset = grown.copyOf().also { it[8] = 12 }
        assertTrue(tracker.observe(postReset, 5, 10, enabled = true, observedAtElapsedMs = 3_000L).isEmpty())
    }

}
