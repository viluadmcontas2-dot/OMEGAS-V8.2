package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceAvailabilityTrackerTest {
    @Test
    fun blockedStreakMeasuresUntilFirstAvailableReferenceWithoutChangingDecision() {
        val tracker = ReferenceAvailabilityTracker()

        val first = tracker.record(false, "NO_PETROL_REGIONS", 1_000_000_000L)
        val stillBlocked = tracker.record(false, "NO_LOCAL_PETROL_REFERENCE", 1_080_000_000L)
        val available = tracker.record(true, "LOCAL_REFERENCE_AVAILABLE", 1_150_000_000L)

        assertEquals("BLOCKED", first.state)
        assertEquals(0L, first.timeToReferenceMs)
        assertEquals("NO_PETROL_REGIONS", first.blockReason)
        assertEquals(80L, stillBlocked.timeToReferenceMs)
        assertEquals("NO_LOCAL_PETROL_REFERENCE", stillBlocked.blockReason)
        assertEquals("AVAILABLE", available.state)
        assertEquals(150L, available.timeToReferenceMs)
        assertEquals("NO_LOCAL_PETROL_REFERENCE", available.blockReason)
        assertEquals("FIRST_BLOCKED_REFERENCE_ATTEMPT", available.measurementOrigin)
    }

    @Test
    fun immediatelyAvailableReferenceReportsZeroAndNextBlockStartsNewStreak() {
        val tracker = ReferenceAvailabilityTracker()
        val immediate = tracker.record(true, "LOCAL_REFERENCE_AVAILABLE", 10L)
        val blocked = tracker.record(false, "NO_LOCAL_PETROL_REFERENCE", 20L)
        val available = tracker.record(true, "LOCAL_REFERENCE_AVAILABLE", 50_000_020L)

        assertEquals(0L, immediate.timeToReferenceMs)
        assertEquals(null, immediate.blockReason)
        assertEquals(0L, blocked.timeToReferenceMs)
        assertEquals(50L, available.timeToReferenceMs)
    }

    @Test
    fun selectorJsonPublishesTimingStateAndBlockReason() {
        val metric = ReferenceAvailabilityMetric(
            state = "BLOCKED",
            timeToReferenceMs = 321L,
            blockReason = "NO_LOCAL_PETROL_REFERENCE",
        )
        val json = PetrolReferenceSelector.Result(
            available = false,
            reasonCode = "NO_LOCAL_PETROL_REFERENCE",
            message = "blocked",
            referenceAvailability = metric,
        ).toJson()

        assertEquals("BLOCKED", json.getString("reference_wait_state"))
        assertEquals(321L, json.getLong("time_to_reference_ms"))
        assertEquals("NO_LOCAL_PETROL_REFERENCE", json.getString("reference_block_reason"))
        assertEquals("FIRST_BLOCKED_REFERENCE_ATTEMPT", json.getString("reference_timing_origin"))
    }
}
