package com.omegas.prohub.model

/**
 * Contrato central do Bloco 04 (031-040).
 *
 * Estes limites governam instrumentação e testes de startup/lifecycle. Eles não
 * autorizam trabalho pesado na Activity nem tornam a restauração do Learning
 * dependência para telemetria.
 */
object StartupLifecyclePolicy {
    const val COLD_START_FIRST_USEFUL_FRAME_BUDGET_MS = 1_500L
    const val WARM_START_FIRST_USEFUL_FRAME_BUDGET_MS = 750L
    const val REOPEN_FIRST_USEFUL_FRAME_BUDGET_MS = 1_000L
    const val FIRST_VALID_TELEMETRY_BUDGET_MS = 3_000L

    const val LEARNING_RESTORE_ON_CRITICAL_PATH = false
    const val TELEMETRY_REQUIRES_LEARNING_READY = false
    const val FULL_HISTORY_READ_ON_FIRST_SCREEN = false
    const val HEAVY_CHECKPOINT_ON_FIRST_FRAME = false
    const val HEAVY_CHECKPOINT_ON_SCREEN_CHANGE = false
    const val HEAVY_CHECKPOINT_ON_ACTIVITY_DESTROY = false

    enum class LearningRestoreState {
        RESTORING,
        READY,
        FAILED,
    }

    enum class LaunchKind {
        COLD,
        WARM,
        REOPEN,
    }

    data class StartupMeasurement(
        val launchKind: LaunchKind,
        val firstUsefulFrameMs: Long,
        val firstValidTelemetryMs: Long?,
        val learningRestoreState: LearningRestoreState,
    ) {
        fun withinBudget(): Boolean {
            val frameBudget = when (launchKind) {
                LaunchKind.COLD -> COLD_START_FIRST_USEFUL_FRAME_BUDGET_MS
                LaunchKind.WARM -> WARM_START_FIRST_USEFUL_FRAME_BUDGET_MS
                LaunchKind.REOPEN -> REOPEN_FIRST_USEFUL_FRAME_BUDGET_MS
            }
            val telemetryOk = firstValidTelemetryMs == null ||
                firstValidTelemetryMs <= FIRST_VALID_TELEMETRY_BUDGET_MS
            return firstUsefulFrameMs <= frameBudget && telemetryOk
        }
    }

    fun telemetryMayRemainAvailable(state: LearningRestoreState): Boolean = when (state) {
        LearningRestoreState.RESTORING,
        LearningRestoreState.READY,
        LearningRestoreState.FAILED -> true
    }

    fun startupCostMayScaleWithHistoricalRecordCount(): Boolean = false
}
