package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAnchorTelemetryWindowTest {
    @Test
    fun `window is bounded by frame count`() {
        val window = NativeAnchorTelemetryWindow(maxFrames = 4, maxAgeMs = 10_000L)
        repeat(8) { index ->
            window.record(index * 100L, 2_000 + index, 0.4, 4.0, "CNG")
        }

        val frames = window.snapshot()
        assertEquals(4, frames.size)
        assertEquals(5L, frames.first().sequence)
        assertEquals(8L, frames.last().sequence)
    }

    @Test
    fun `window evicts frames older than age budget`() {
        val window = NativeAnchorTelemetryWindow(maxFrames = 100, maxAgeMs = 1_000L)
        window.record(0L, 2_000, 0.4, 4.0, "CNG")
        window.record(500L, 2_100, 0.4, 4.1, "CNG")
        window.record(1_500L, 2_200, 0.4, 4.2, "CNG")

        val frames = window.snapshot()
        assertEquals(2, frames.size)
        assertEquals(500L, frames.first().elapsedMs)
    }

    @Test
    fun `between preserves physical order and signals`() {
        val window = NativeAnchorTelemetryWindow(maxFrames = 16, maxAgeMs = 10_000L)
        window.record(1_000L, 2_000, 0.35, 3.8, "PETROL")
        window.record(1_500L, 2_500, 0.45, 4.2, "CNG")
        window.record(2_000L, 3_000, 0.55, 4.8, "CNG")

        val frames = window.between(1_400L, 2_000L)
        assertEquals(2, frames.size)
        assertEquals("CNG", frames.first().fuel)
        assertEquals(2_500, frames.first().rpm)
        assertEquals(4.2, frames.first().petrolMs, 0.0001)
        assertEquals(0.45, frames.first().mapBar, 0.0001)
        assertTrue(frames.first().sequence < frames.last().sequence)
    }

    @Test
    fun `reset clears frames and restarts sequence for a new usb session`() {
        val window = NativeAnchorTelemetryWindow()
        window.record(1_000L, 2_000, 0.4, 4.0, "CNG")
        window.reset()
        val after = window.record(2_000L, 2_100, 0.5, 4.1, "CNG")

        assertEquals(1, window.size())
        assertEquals(1L, after.sequence)
    }
}
