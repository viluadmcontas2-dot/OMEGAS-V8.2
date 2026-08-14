package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalAnchorCorrelatorTest {
    @Test
    fun `correlates only compatible real GNV frames and returns best physical frame`() {
        val frames = listOf(
            frame(1, 1_000, 2_400, 0.40, 4.00, "GASOLINA", sessionId = 9),
            frame(2, 1_100, 2_480, 0.41, 4.05, "GNV", sessionId = 9, gasMs = 7.1),
            frame(3, 1_200, 2_500, 0.405, 4.02, "GNV", sessionId = 9, gasMs = 7.2),
            frame(4, 1_300, 2_520, 0.41, 4.06, "GNV", sessionId = 9, gasMs = 7.3),
        )

        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames = frames,
            nativePetrolMs = 4.04,
            nativeMapBar = 0.41,
            observedAtElapsedMs = 1_500,
            policy = LearningTolerancePolicy(requiredFrames = 3),
            sessionId = 9,
        )

        assertEquals("CORRELATED", result.state)
        assertEquals("STABLE_SINGLE_PHYSICAL_CLUSTER", result.reason)
        assertEquals(3, result.matchedFrames)
        assertEquals(2_520, result.rpm)
        assertEquals(1_300L, result.correlatedFrameElapsedMs)
        assertEquals(200L, result.lagMs)
        assertEquals("GNV", result.fuel)
        assertEquals(7.3, result.gasMsDiagnostic!!, 0.0001)
        assertTrue(result.confidence > 0.5)
        assertTrue(result.rpmConfidence > 0.0)
    }

    @Test
    fun `petrol frames never satisfy a gnv native anchor`() {
        val frames = listOf(
            frame(1, 1_000, 2_500, 0.41, 4.04, "GASOLINA"),
            frame(2, 1_100, 2_500, 0.41, 4.04, "GASOLINA"),
        )
        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_500, LearningTolerancePolicy(),
        )
        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertEquals("FUEL_MISMATCH", result.reason)
        assertNull(result.rpm)
    }

    @Test
    fun `wrong session and implausible frames cannot create anchor position`() {
        val frames = listOf(
            frame(1, 1_000, 2_500, 0.41, 4.04, "GNV", sessionId = 8),
            frame(2, 1_100, 2_500, 0.41, 4.04, "GNV", sessionId = 9, plausible = false),
            frame(3, 1_200, 2_500, 0.41, 4.04, "GNV", sessionId = 9, plausible = false),
        )
        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_500, LearningTolerancePolicy(), sessionId = 9,
        )
        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertEquals("IMPLAUSIBLE_TELEMETRY", result.reason)
        assertNull(result.rpm)
    }

    @Test
    fun `unstable rpm does not become a fake position`() {
        val frames = listOf(
            frame(1, 1_000, 1_500, 0.41, 4.04, "GNV"),
            frame(2, 1_100, 3_500, 0.41, 4.04, "GNV"),
        )
        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_500, LearningTolerancePolicy(),
        )
        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertEquals("RPM_AMBIGUITY", result.reason)
        assertNull(result.rpm)
        assertEquals(0.0, result.rpmConfidence, 0.0001)
    }

    @Test
    fun `continuity gap blocks correlation`() {
        val frames = listOf(
            frame(1, 100, 2_500, 0.41, 4.04, "GNV"),
            frame(2, 1_200, 2_510, 0.41, 4.04, "GNV"),
        )
        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, 4.04, 0.41, 1_300,
            LearningTolerancePolicy(breakingGapMs = 900L),
        )
        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertEquals("CONTINUITY_GAP", result.reason)
        assertNull(result.rpm)
    }

    @Test
    fun `missing native context is explicit unreliable correlation`() {
        val frames = listOf(frame(1, 1_000, 2_500, 0.41, 4.04, "GNV"))
        val result = NativeAutoCalAnchorCorrelator.correlate(
            frames, null, 0.41, 1_500, LearningTolerancePolicy(),
        )
        assertEquals("NO_RELIABLE_CORRELATION", result.state)
        assertEquals("NO_NATIVE_CONTEXT", result.reason)
        assertNull(result.rpm)
        assertEquals(0, result.matchedFrames)
    }

    @Test
    fun `duplicating the same physical support does not manufacture confidence`() {
        val base = listOf(
            frame(1, 1_100, 2_500, 0.41, 4.04, "GNV"),
            frame(2, 1_200, 2_505, 0.408, 4.03, "GNV"),
        )
        val doubled = base + listOf(
            frame(3, 1_100, 2_500, 0.41, 4.04, "GNV"),
            frame(4, 1_200, 2_505, 0.408, 4.03, "GNV"),
        )
        val policy = LearningTolerancePolicy(requiredFrames = 30)
        val first = NativeAutoCalAnchorCorrelator.correlate(base, 4.04, 0.41, 1_500, policy)
        val second = NativeAutoCalAnchorCorrelator.correlate(doubled, 4.04, 0.41, 1_500, policy)
        assertEquals("CORRELATED", first.state)
        assertEquals("CORRELATED", second.state)
        assertEquals(first.confidence, second.confidence, 1e-12)
        assertEquals(first.rpmConfidence, second.rpmConfidence, 1e-12)
    }

    private fun frame(
        sequence: Long,
        elapsedMs: Long,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
        fuel: String,
        sessionId: Long = 0L,
        gasMs: Double? = null,
        plausible: Boolean = true,
    ) = NativeAnchorTelemetryWindow.Frame(
        sequence = sequence,
        elapsedMs = elapsedMs,
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        fuel = fuel,
        sessionId = sessionId,
        gasMsDiagnostic = gasMs,
        plausible = plausible,
    )
}
