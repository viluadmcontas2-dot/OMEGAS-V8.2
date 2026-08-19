package com.omegas.prohub.autocal

/**
 * Contrato observacional da calibração automática estacionária da ECU.
 * Não reproduz matemática OEM e não possui writer, timer ou transporte.
 */
object StationaryCalibrationStateMachine {
    enum class State {
        IDLE,
        PRECONDITIONS_UNKNOWN,
        READY_TO_OBSERVE,
        ECU_ACTIVE,
        COMPLETED_OBSERVED,
        FAILED_OBSERVED,
        RECOVERY_REQUIRED,
    }

    data class Preconditions(
        val engineRunningKnown: Boolean,
        val fuelStateKnown: Boolean,
        val nativeStatusKnown: Boolean,
        val calibrationIdentityKnown: Boolean,
    ) {
        fun complete(): Boolean = engineRunningKnown && fuelStateKnown && nativeStatusKnown && calibrationIdentityKnown
    }

    data class Snapshot(
        val state: State,
        val reason: String,
        val preconditions: Preconditions,
        val inputsKnown: Boolean,
        val outputObserved: Boolean,
        val mutationScope: String,
        val recovery: String?,
        val algorithmKnown: Boolean = false,
        val appAutomaticWrite: Boolean = false,
    )

    fun evaluate(
        preconditions: Preconditions,
        nativeActive: Boolean?,
        completionObserved: Boolean?,
        failureObserved: Boolean?,
        outputObserved: Boolean,
    ): Snapshot {
        if (!preconditions.complete()) {
            return Snapshot(
                state = State.PRECONDITIONS_UNKNOWN,
                reason = "STATIONARY_PRECONDITIONS_INCOMPLETE",
                preconditions = preconditions,
                inputsKnown = false,
                outputObserved = false,
                mutationScope = "UNKNOWN",
                recovery = "REOBSERVE_NATIVE_PRECONDITIONS",
            )
        }
        if (failureObserved == true) {
            return Snapshot(
                State.FAILED_OBSERVED,
                "NATIVE_FAILURE_OBSERVED",
                preconditions,
                inputsKnown = true,
                outputObserved = outputObserved,
                mutationScope = if (outputObserved) "ECU_NATIVE_CALIBRATION" else "UNKNOWN",
                recovery = "RECONCILE_CALIBRATION_IDENTITY",
            )
        }
        if (completionObserved == true) {
            return Snapshot(
                State.COMPLETED_OBSERVED,
                "NATIVE_COMPLETION_OBSERVED",
                preconditions,
                inputsKnown = true,
                outputObserved = outputObserved,
                mutationScope = if (outputObserved) "ECU_NATIVE_CALIBRATION" else "UNKNOWN",
                recovery = if (outputObserved) "RECONCILE_CALIBRATION_IDENTITY" else "READ_OUTPUT_BEFORE_PROMOTION",
            )
        }
        if (nativeActive == true) {
            return Snapshot(
                State.ECU_ACTIVE,
                "NATIVE_STATIONARY_CALIBRATION_ACTIVE",
                preconditions,
                inputsKnown = true,
                outputObserved = false,
                mutationScope = "POTENTIALLY_ECU_NATIVE_CALIBRATION",
                recovery = "OBSERVE_UNTIL_TERMINAL_STATE",
            )
        }
        if (nativeActive == false) {
            return Snapshot(
                State.READY_TO_OBSERVE,
                "NATIVE_STATIONARY_CALIBRATION_INACTIVE",
                preconditions,
                inputsKnown = true,
                outputObserved = false,
                mutationScope = "NONE_OBSERVED",
                recovery = null,
            )
        }
        return Snapshot(
            State.PRECONDITIONS_UNKNOWN,
            "NATIVE_ACTIVE_STATE_UNKNOWN",
            preconditions,
            inputsKnown = false,
            outputObserved = false,
            mutationScope = "UNKNOWN",
            recovery = "REOBSERVE_NATIVE_STATE",
        )
    }
}
