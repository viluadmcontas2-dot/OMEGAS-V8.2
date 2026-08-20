package com.omegas.prohub.physics

/**
 * Protocol definition only. It documents the future controlled physical route
 * for learning plant gain and cannot execute any ECU action in Phase 06.
 */
data class PlantIdentificationProtocol(
    val executionGate: String,
    val replayStatus: String,
    val requiresPreWriteSnapshot: Boolean,
    val requiresHumanConfirmation: Boolean,
    val requiresAckAndReadback: Boolean,
    val requiresPostWriteRevalidation: Boolean,
    val mayExecuteInPhase6: Boolean,
    val outcomeFields: Set<String>,
) {
    companion object {
        fun default(): PlantIdentificationProtocol = PlantIdentificationProtocol(
            executionGate = "FINAL_PHYSICAL_GATE_ONLY",
            replayStatus = "HOLD",
            requiresPreWriteSnapshot = true,
            requiresHumanConfirmation = true,
            requiresAckAndReadback = true,
            requiresPostWriteRevalidation = true,
            mayExecuteInPhase6 = false,
            outcomeFields = setOf(
                "beforeResidual",
                "appliedLogFactorDelta",
                "ack",
                "readback",
                "afterResidual",
                "contextSlice",
                "calibrationIdentity",
            ),
        )

        fun gainAuthority(hasIntervention: Boolean, hasRevalidation: Boolean): MagnitudeAuthority =
            if (hasIntervention && hasRevalidation) MagnitudeAuthority.EMPIRICALLY_BOUNDED
            else MagnitudeAuthority.POLICY_ONLY
    }
}
