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

        /**
         * Revalidation alone does not create empirical authority. Promotion
         * requires an informative, directionally coherent paired outcome.
         */
        fun gainAuthority(
            hasIntervention: Boolean,
            hasRevalidation: Boolean,
            beforeLogError: Double?,
            afterLogError: Double?,
            appliedLogFactorDelta: Double?,
        ): MagnitudeAuthority {
            if (!hasIntervention || !hasRevalidation) return MagnitudeAuthority.POLICY_ONLY
            val before = beforeLogError ?: return MagnitudeAuthority.POLICY_ONLY
            val after = afterLogError ?: return MagnitudeAuthority.POLICY_ONLY
            val delta = appliedLogFactorDelta ?: return MagnitudeAuthority.POLICY_ONLY
            if (!before.isFinite() || !after.isFinite() || !delta.isFinite() || kotlin.math.abs(delta) < 1e-9) {
                return MagnitudeAuthority.POLICY_ONLY
            }
            val observedGain = (before - after) / delta
            return if (observedGain.isFinite() && observedGain > 0.0) {
                MagnitudeAuthority.EMPIRICALLY_BOUNDED
            } else {
                MagnitudeAuthority.POLICY_ONLY
            }
        }
    }
}
