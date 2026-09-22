package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalEpochConfirmationTest {
    @Test
    fun `delayed MUL_ACT change confirms pending epoch on a later snapshot`() {
        val tracker = NativeAutoCalEpochConfirmation(maxSnapshots = 3)
        val transition = AutoCalEpochTransition(before = 2, after = 3)

        assertEquals(
            NativeAutoCalEpochConfirmation.StartResult.STARTED,
            tracker.start(transition, oldMulHash = "OLD"),
        )

        val first = tracker.observeMul("OLD")
        assertTrue(first is NativeAutoCalEpochConfirmation.Observation.Awaiting)
        assertTrue(tracker.hasPending())

        val second = tracker.observeMul("NEW")
        assertTrue(second is NativeAutoCalEpochConfirmation.Observation.Confirmed)
        val confirmed = (second as NativeAutoCalEpochConfirmation.Observation.Confirmed).value
        assertEquals(2, confirmed.transition.before)
        assertEquals(3, confirmed.transition.after)
        assertEquals("OLD", confirmed.oldMulHash)
        assertEquals("NEW", confirmed.newMulHash)
        assertEquals(2, confirmed.snapshotsObserved)
        assertFalse(tracker.hasPending())
    }

    @Test
    fun `unchanged K expires without inventing epoch`() {
        val tracker = NativeAutoCalEpochConfirmation(maxSnapshots = 3)
        tracker.start(AutoCalEpochTransition(3, 0), oldMulHash = "OLD")

        assertTrue(tracker.observeMul("OLD") is NativeAutoCalEpochConfirmation.Observation.Awaiting)
        assertTrue(tracker.observeMul("OLD") is NativeAutoCalEpochConfirmation.Observation.Awaiting)

        val third = tracker.observeMul("OLD")
        assertTrue(third is NativeAutoCalEpochConfirmation.Observation.Expired)
        val expired = third as NativeAutoCalEpochConfirmation.Observation.Expired
        assertEquals(3, expired.transition.before)
        assertEquals(0, expired.transition.after)
        assertEquals(3, expired.snapshotsObserved)
        assertFalse(tracker.hasPending())
    }

    @Test
    fun `overlapping counter transitions fail closed`() {
        val tracker = NativeAutoCalEpochConfirmation(maxSnapshots = 3)
        assertEquals(
            NativeAutoCalEpochConfirmation.StartResult.STARTED,
            tracker.start(AutoCalEpochTransition(1, 2), oldMulHash = "A"),
        )
        assertEquals(
            NativeAutoCalEpochConfirmation.StartResult.OVERLAP_DROPPED,
            tracker.start(AutoCalEpochTransition(2, 3), oldMulHash = "A"),
        )
        assertFalse(tracker.hasPending())
        assertTrue(tracker.observeMul("B") is NativeAutoCalEpochConfirmation.Observation.None)
    }

    @Test
    fun `session reset clears pending epoch`() {
        val tracker = NativeAutoCalEpochConfirmation(maxSnapshots = 3)
        tracker.start(AutoCalEpochTransition(0, 1), oldMulHash = "A")
        assertTrue(tracker.hasPending())
        tracker.reset()
        assertFalse(tracker.hasPending())
    }
}
