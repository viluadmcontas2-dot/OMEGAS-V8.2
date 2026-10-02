package com.omegas.prohub.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLifecyclePolicyTest {
    @Test
    fun `learning restore never gates telemetry`() {
        assertFalse(StartupLifecyclePolicy.LEARNING_RESTORE_ON_CRITICAL_PATH)
        assertFalse(StartupLifecyclePolicy.TELEMETRY_REQUIRES_LEARNING_READY)
        StartupLifecyclePolicy.LearningRestoreState.values().forEach { state ->
            assertTrue(StartupLifecyclePolicy.telemetryMayRemainAvailable(state))
        }
    }

    @Test
    fun `heavy persistence is forbidden from activity critical path`() {
        assertFalse(StartupLifecyclePolicy.FULL_HISTORY_READ_ON_FIRST_SCREEN)
        assertFalse(StartupLifecyclePolicy.HEAVY_CHECKPOINT_ON_FIRST_FRAME)
        assertFalse(StartupLifecyclePolicy.HEAVY_CHECKPOINT_ON_SCREEN_CHANGE)
        assertFalse(StartupLifecyclePolicy.HEAVY_CHECKPOINT_ON_ACTIVITY_DESTROY)
        assertFalse(StartupLifecyclePolicy.startupCostMayScaleWithHistoricalRecordCount())
    }

    @Test
    fun `cold warm and reopen budgets are explicit`() {
        assertTrue(
            StartupLifecyclePolicy.StartupMeasurement(
                launchKind = StartupLifecyclePolicy.LaunchKind.COLD,
                firstUsefulFrameMs = 1_400,
                firstValidTelemetryMs = 2_900,
                learningRestoreState = StartupLifecyclePolicy.LearningRestoreState.RESTORING,
            ).withinBudget(),
        )
        assertFalse(
            StartupLifecyclePolicy.StartupMeasurement(
                launchKind = StartupLifecyclePolicy.LaunchKind.WARM,
                firstUsefulFrameMs = 900,
                firstValidTelemetryMs = 2_000,
                learningRestoreState = StartupLifecyclePolicy.LearningRestoreState.READY,
            ).withinBudget(),
        )
    }

    @Test
    fun `corrupted learning restore cannot make startup policy fail closed for telemetry`() {
        assertTrue(
            StartupLifecyclePolicy.telemetryMayRemainAvailable(
                StartupLifecyclePolicy.LearningRestoreState.FAILED,
            ),
        )
    }
}
