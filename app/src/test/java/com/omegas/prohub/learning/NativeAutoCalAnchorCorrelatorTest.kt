package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalAnchorCorrelatorTest {
    @Test
    fun `correlates only compatible cng frames and returns lag`() {
        val frames = listOf(
            frame(1, 1_000, 2_400, 0.40, 4.00, "PETROL"),
            frame(2, 1_100, 2_480, 0.41, 4.05, "CNG"),
            frame(3, 1_200, 2_500, 0.405, 4.02, "CNG"),
            frame(4, 1_300, 2_520, 0.41, 4.06, "CNG"),
        )

        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames = frames,
            nativePetrolMs = 4.04,
            nativeMapBar = 0.41,
            observedAtElapsedMs = 1_500,
            policy = LearningTolerancePolicy(requiredFrames = 3),
        )

        assertEquals("CORRELATED", result.state)
        assertEquals(3, result.matchedFrames)
        assertEquals(2_500, result.rpm)
        assertEquals(300L, result.lagMs)
        assertTrue(result.confidence > 0.5)
        assertTrue(result.rpmConfidence > 0.0)
    }

    @Test
    fun `petrol frames never satisfy a cng native anchor`() {
        val frames = listOf(
            frame(1, 1_000, 2_500, 0.41, 4.04, "PETROL"),
            frame(2, 1_100, 2_500, 0.41, 4.04, "PETROL"),
        )

        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_500, LearningTolerancePolicy(),
        )

        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertNull(result.rpm)
    }

    @Test
    fun `unstable rpm does not become a fake position`() {
        val frames = listOf(
            frame(1, 1_000, 1_500, 0.41, 4.04, "CNG"),
            frame(2, 1_100, 3_500, 0.41, 4.04, "CNG"),
        )

        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_500, LearningTolerancePolicy(),
        )

        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertNull(result.rpm)
        assertEquals(0.0, result.rpmConfidence, 0.0001)
    }

    @Test
    fun `missing native petrol or map context cannot invent correlation`() {
        val frames = listOf(frame(1, 1_000, 2_500, 0.41, 4.04, "CNG"))

        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, null, 0.41, 1_500, LearningTolerancePolicy(),
        )

        assertEquals("NO_NATIVE_CONTEXT", result.state)
        assertNull(result.rpm)
        assertEquals(0, result.matchedFrames)
    }

    private fun frame(
        sequence: Long,
        elapsedMs: Long,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
        fuel: String,
    ) = NativeAnchorTelemetryWindow.Frame(sequence, elapsedMs, rpm, mapBar, petrolMs, fuel)
}
