package com.omegas.prohub.physics

/** Ideal actuator-specific target, still separate from any operational StepPolicy. */
data class ConditionalActuatorTarget(
    val mechanism: CorrectionMechanism,
    val factor: Double?,
    val authority: MagnitudeAuthority,
    val context: ContextSlice,
    val reason: String,
)

object ConditionalActuatorTargets {
    /**
     * MAP_LOCAL owns the local factor after conditioning on the current Curve.
     * No damped Advisor delta is accepted as input.
     */
    fun mapLocal(kStar: KStarEstimate, context: CalibrationPhysicsContext): ConditionalActuatorTarget {
        val fStar = kStar.targetFactor
        val cEff = context.cEff
        if (kStar.abstained || fStar == null || cEff == null || cEff <= 0.0) {
            return unknown(CorrectionMechanism.MAP_LOCAL, context, "KSTAR_OR_CURVE_EFFECTIVE_UNKNOWN")
        }
        return ConditionalActuatorTarget(
            mechanism = CorrectionMechanism.MAP_LOCAL,
            factor = fStar / cEff,
            authority = kStar.authority,
            context = context.microState,
            reason = "FSTAR_DIVIDED_BY_CURRENT_CURVE_EFFECTIVE",
        )
    }

    /**
     * CURVE_MUL_ACT receives only the global residual after local/context effects
     * have been removed. This prevents applying the same error to both actuators.
     */
    fun curveGlobal(
        kStar: KStarEstimate,
        context: CalibrationPhysicsContext,
        localResidualRemoved: Boolean,
    ): ConditionalActuatorTarget {
        if (!localResidualRemoved) {
            return unknown(CorrectionMechanism.CURVE_MUL_ACT, context, "LOCAL_RESIDUAL_NOT_REMOVED")
        }
        val fStar = kStar.targetFactor
        val mEff = context.mEff
        if (kStar.abstained || fStar == null || mEff == null || mEff <= 0.0) {
            return unknown(CorrectionMechanism.CURVE_MUL_ACT, context, "KSTAR_OR_MAP_EFFECTIVE_UNKNOWN")
        }
        return ConditionalActuatorTarget(
            mechanism = CorrectionMechanism.CURVE_MUL_ACT,
            factor = fStar / mEff,
            authority = kStar.authority,
            context = context.microState,
            reason = "FSTAR_DIVIDED_BY_CURRENT_MAP_EFFECTIVE_AFTER_LOCAL_REMOVAL",
        )
    }

    private fun unknown(
        mechanism: CorrectionMechanism,
        context: CalibrationPhysicsContext,
        reason: String,
    ): ConditionalActuatorTarget = ConditionalActuatorTarget(
        mechanism = mechanism,
        factor = null,
        authority = MagnitudeAuthority.UNKNOWN,
        context = context.microState,
        reason = reason,
    )
}
