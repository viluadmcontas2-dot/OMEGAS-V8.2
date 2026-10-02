package com.omegas.prohub.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryVisualLifecyclePolicyTest {
    @Test
    fun visualBacklogIsHardBoundedToActivePlusOneReplaceablePending() {
        assertEquals(1, TelemetryVisualLifecyclePolicy.MAX_ACTIVE_VISUAL_FRAMES)
        assertEquals(1, TelemetryVisualLifecyclePolicy.MAX_PENDING_VISUAL_FRAMES)
        assertEquals(2, TelemetryVisualLifecyclePolicy.MAX_BUFFERED_VISUAL_FRAMES)
    }

    @Test
    fun learningAndCriticalEventsStayOutsideDiscardableVisualQueue() {
        assertFalse(TelemetryVisualLifecyclePolicy.SCIENTIFIC_LEARNING_USES_VISUAL_QUEUE)
        assertFalse(TelemetryVisualLifecyclePolicy.CRITICAL_EVENTS_USE_VISUAL_QUEUE)
        assertTrue(
            TelemetryVisualLifecyclePolicy.shouldRender(
                TelemetryVisualWorkClass.CRITICAL_EVENT,
                TelemetryVisualSurfaceMode.HIDDEN,
            ),
        )
    }

    @Test
    fun hiddenAndSplitScreenHaveExplicitRenderingPolicy() {
        assertFalse(
            TelemetryVisualLifecyclePolicy.shouldRender(
                TelemetryVisualWorkClass.PRIMARY_TELEMETRY,
                TelemetryVisualSurfaceMode.HIDDEN,
            ),
        )
        assertTrue(
            TelemetryVisualLifecyclePolicy.shouldRender(
                TelemetryVisualWorkClass.PRIMARY_TELEMETRY,
                TelemetryVisualSurfaceMode.SPLIT_SCREEN,
            ),
        )
        assertFalse(
            TelemetryVisualLifecyclePolicy.shouldRender(
                TelemetryVisualWorkClass.SECONDARY_VISUAL,
                TelemetryVisualSurfaceMode.SPLIT_SCREEN,
            ),
        )
        assertTrue(TelemetryVisualLifecyclePolicy.retainLatestSnapshotWhileHidden())
        assertFalse(TelemetryVisualLifecyclePolicy.requiresHistoricalReplayOnForeground())
    }

    @Test
    fun primaryTelemetryAndFreshnessContractAreStable() {
        assertTrue("rpm" in TelemetryVisualLifecyclePolicy.primaryTelemetryFields)
        assertTrue("petrol_ms" in TelemetryVisualLifecyclePolicy.primaryTelemetryFields)
        assertTrue("fuel" in TelemetryVisualLifecyclePolicy.primaryTelemetryFields)
        assertEquals(250L, TelemetryVisualLifecyclePolicy.normalizedAgeMs(1_000L, 1_250L))
        assertEquals(-1L, TelemetryVisualLifecyclePolicy.normalizedAgeMs(0L, 1_250L))
    }
}
