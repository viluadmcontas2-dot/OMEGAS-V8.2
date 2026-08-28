package com.omegas.prohub.physics

enum class ActuatorFreedom {
    FROZEN,
    FREE,
}

data class ActuatorIdentificationInput(
    val kStar: KStarEstimate,
    val context: CalibrationPhysicsContext,
    val mapFreedom: ActuatorFreedom,
    val curveFreedom: ActuatorFreedom,
    val localResidualRemoved: Boolean,
)

data class IdentifiedActuatorTarget(
    val fStar: Double?,
    val mapTarget: ConditionalActuatorTarget?,
    val curveTarget: ConditionalActuatorTarget?,
    val reason: String,
    val authority: MagnitudeAuthority,
) {
    init {
        require(mapTarget == null || curveTarget == null) {
            "One residual cannot identify simultaneous Map and Curve targets"
        }
    }
}

/**
 * Identification gate around the existing physical target equations.
 * F* is primary. Actuator decomposition is allowed only when the complementary
 * actuator is conditioned/frozen; both-free requires a later explicit allocator.
 */
object ActuatorTargetIdentification {
    fun resolve(input: ActuatorIdentificationInput): IdentifiedActuatorTarget {
        val fStar = input.kStar.targetFactor
        if (input.kStar.abstained || fStar == null || !fStar.isFinite() || fStar <= 0.0) {
            return IdentifiedActuatorTarget(
                fStar = null,
                mapTarget = null,
                curveTarget = null,
                reason = "KSTAR_UNAVAILABLE",
                authority = MagnitudeAuthority.UNKNOWN,
            )
        }

        return when (input.mapFreedom) {
            ActuatorFreedom.FREE -> when (input.curveFreedom) {
                ActuatorFreedom.FROZEN -> resolveMapConditioned(input, fStar)
                ActuatorFreedom.FREE ->
                    identifiedFStarOnly(fStar, input.kStar.authority, "FSTAR_PRIMARY_BOTH_ACTUATORS_FREE")
            }

            ActuatorFreedom.FROZEN -> when (input.curveFreedom) {
                ActuatorFreedom.FREE -> resolveCurveConditioned(input, fStar)
                ActuatorFreedom.FROZEN ->
                    identifiedFStarOnly(fStar, input.kStar.authority, "NO_FREE_ACTUATOR")
            }
        }
    }

    private fun resolveMapConditioned(
        input: ActuatorIdentificationInput,
        fStar: Double,
    ): IdentifiedActuatorTarget {
        val target = ConditionalActuatorTargets.mapLocal(input.kStar, input.context)
        return if (target.factor == null) {
            identifiedFStarOnly(fStar, input.kStar.authority, target.reason)
        } else {
            IdentifiedActuatorTarget(
                fStar = fStar,
                mapTarget = target,
                curveTarget = null,
                reason = "MAP_CONDITIONED_ON_FROZEN_CURVE",
                authority = input.kStar.authority,
            )
        }
    }

    private fun resolveCurveConditioned(
        input: ActuatorIdentificationInput,
        fStar: Double,
    ): IdentifiedActuatorTarget {
        val target = ConditionalActuatorTargets.curveGlobal(
            input.kStar,
            input.context,
            input.localResidualRemoved,
        )
        return if (target.factor == null) {
            identifiedFStarOnly(fStar, input.kStar.authority, target.reason)
        } else {
            IdentifiedActuatorTarget(
                fStar = fStar,
                mapTarget = null,
                curveTarget = target,
                reason = "CURVE_CONDITIONED_ON_FROZEN_MAP",
                authority = input.kStar.authority,
            )
        }
    }

    private fun identifiedFStarOnly(
        fStar: Double,
        authority: MagnitudeAuthority,
        reason: String,
    ): IdentifiedActuatorTarget = IdentifiedActuatorTarget(
        fStar = fStar,
        mapTarget = null,
        curveTarget = null,
        reason = reason,
        authority = authority,
    )
}
