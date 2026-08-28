package com.omegas.prohub.learning

import com.omegas.prohub.telemetry.RuntimeFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificSignalTest {
    @Test
    fun freshnessBoundaryIsInclusiveAndExplicit() {
        val signal = ScientificSignal(
            value = 4.2,
            capturedAtElapsedMs = 1_000L,
            freshness = RuntimeFreshness.CURRENT,
            available = true,
            plausible = true,
            source = "MP48",
        )
        val atBoundary = signal.evaluate(nowElapsedMs = 1_500L, maxAgeMs = 500L)
        assertTrue(atBoundary.usable)
        assertEquals(500L, atBoundary.ageMs)

        val stale = signal.evaluate(nowElapsedMs = 1_501L, maxAgeMs = 500L)
        assertFalse(stale.usable)
        assertEquals("SIGNAL_STALE", stale.reasonCode)
        assertEquals(null, stale.value)
    }

    @Test
    fun unavailableStaleImplausibleAndUnknownTimestampFailClosed() {
        val unavailable = ScientificSignal<Double>(null, null, RuntimeFreshness.UNKNOWN, false, false, "MISSING")
        assertEquals("SIGNAL_UNAVAILABLE", unavailable.evaluate(1_000L, 500L).reasonCode)

        val implausible = ScientificSignal(4.0, 900L, RuntimeFreshness.CURRENT, true, false, "MP48")
        assertEquals("SIGNAL_IMPLAUSIBLE", implausible.evaluate(1_000L, 500L).reasonCode)

        val stale = ScientificSignal(4.0, 900L, RuntimeFreshness.STALE, true, true, "MP48")
        assertEquals("SIGNAL_STALE", stale.evaluate(1_000L, 500L).reasonCode)

        val noTimestamp = ScientificSignal(4.0, null, RuntimeFreshness.CURRENT, true, true, "RESTORED")
        assertEquals("SIGNAL_TIMESTAMP_UNKNOWN", noTimestamp.evaluate(1_000L, 500L).reasonCode)
    }

    @Test
    fun futureTimestampCannotBeTreatedAsFresh() {
        val signal = ScientificSignal(4.0, 1_001L, RuntimeFreshness.CURRENT, true, true, "MP48")
        val result = signal.evaluate(1_000L, 500L)
        assertFalse(result.usable)
        assertEquals("SIGNAL_TIMESTAMP_IN_FUTURE", result.reasonCode)
    }
}
