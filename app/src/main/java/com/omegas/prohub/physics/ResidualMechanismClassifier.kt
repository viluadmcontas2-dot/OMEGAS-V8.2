package com.omegas.prohub.physics

/**
 * Causal evidence for residual structure.
 *
 * Numeric scores remain diagnostic/provenance only. Causal promotion is driven
 * by explicit structural support flags produced from real Advisor statistics,
 * not by universal score thresholds. UNKNOWN is the default whenever support,
 * environmental context, or causal ordering is incomplete.
 */
data class ResidualEvidence(
    val comparableSamples: Int,
    val localizedRepeatability: Double,
    val broadCoherence: Double,
    val environmentalCorrelation: Double,
    val contradiction: Double,
    val mapMechanismSupported: Boolean,
    val curveMechanismSupported: Boolean,
    val direction: EffectDirection,
    val localizedStructureSupported: Boolean =
        localizedRepeatability > 0.0 && localizedRepeatability > broadCoherence,
    val broadStructureSupported: Boolean =
        broadCoherence > 0.0 && broadCoherence > localizedRepeatability,
    val environmentalContextVerified: Boolean = true,
    val environmentalExplanationSupported: Boolean =
        environmentalCorrelation > 0.0 &&
            environmentalCorrelation > localizedRepeatability &&
            environmentalCorrelation > broadCoherence,
    val contradictionObserved: Boolean =
        contradiction > 0.0 &&
            contradiction > localizedRepeatability &&
            contradiction > broadCoherence &&
            contradiction > environmentalCorrelation,
    val localResidualCleared: Boolean = true,
) {
    init {
        require(comparableSamples >= 0)
        listOf(localizedRepeatability, broadCoherence, environmentalCorrelation, contradiction).forEach {
            require(it.isFinite() && it in 0.0..1.0)
        }
        require(!environmentalExplanationSupported || environmentalContextVerified) {
            "environmental explanation requires verified environmental context"
        }
    }
}

data class MechanismClassification(
    val decision: CorrectionDecision,
    val reasonCode: String,
    val uncertaintyInflation: Double,
    val nextEvidence: String,
)

object ResidualMechanismClassifier {
    fun classify(evidence: ResidualEvidence): MechanismClassification {
        if (evidence.comparableSamples == 0) {
            return inconclusive(
                reason = "NO_COMPARABLE_SUPPORT",
                nextEvidence = "collect at least one genuine comparable microstate before causal classification",
                inflation = maxOf(evidence.contradiction, evidence.environmentalCorrelation),
            )
        }

        if (evidence.contradictionObserved) {
            return inconclusive(
                reason = "CONTRADICTORY_EVIDENCE",
                nextEvidence = "collect comparable microstates until directional/model contradiction is resolved",
                inflation = evidence.contradiction,
            )
        }

        if (!evidence.environmentalContextVerified) {
            return inconclusive(
                reason = "ENVIRONMENT_CONTEXT_UNVERIFIED",
                nextEvidence = "verify matched pressure/temperature/water context before causal mechanism promotion",
                inflation = evidence.environmentalCorrelation,
            )
        }

        if (evidence.environmentalExplanationSupported) {
            return MechanismClassification(
                decision = CorrectionDecision(
                    mechanism = CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
                    effect = ExpectedEffect(
                        direction = evidence.direction,
                        lowerBound = null,
                        upperBound = null,
                        assumptions = listOf("verified environmental/context evidence explains residual structure"),
                        authority = MagnitudeAuthority.UNKNOWN,
                        falsifier = "residual remains after conditioning on pressure/temperature/water microstate",
                    ),
                    target = null,
                    evidencePath = listOf(
                        "environmentalContextVerified=true",
                        "environmentalExplanationSupported=true",
                        "environmentalCorrelation=${evidence.environmentalCorrelation}",
                    ),
                ),
                reasonCode = "ENVIRONMENTAL_CONFOUNDER",
                uncertaintyInflation = evidence.environmentalCorrelation,
                nextEvidence = "compare matched microstates conditioned on environmental context",
            )
        }

        if (evidence.localizedStructureSupported) {
            return if (evidence.mapMechanismSupported) {
                supported(
                    mechanism = CorrectionMechanism.MAP_LOCAL,
                    direction = evidence.direction,
                    reason = "LOCALIZED_REPEATABLE",
                    evidencePath = listOf(
                        "localizedStructureSupported=true",
                        "mapMechanismSupported=true",
                        "comparableSamples=${evidence.comparableSamples}",
                        "localizedRepeatability=${evidence.localizedRepeatability}",
                    ),
                )
            } else {
                inconclusive(
                    reason = "LOCAL_WITHOUT_MAP_MECHANISM_SUPPORT",
                    nextEvidence = "verify local Map mechanism authority for the observed residual",
                    inflation = evidence.contradiction,
                )
            }
        }

        if (evidence.broadStructureSupported) {
            if (!evidence.localResidualCleared) {
                return inconclusive(
                    reason = "LOCAL_RESIDUAL_NOT_CLEARED",
                    nextEvidence = "resolve or condition actionable local residual before Curve mechanism promotion",
                    inflation = evidence.contradiction,
                )
            }
            return if (evidence.curveMechanismSupported) {
                supported(
                    mechanism = CorrectionMechanism.CURVE_MUL_ACT,
                    direction = evidence.direction,
                    reason = "BROAD_COHERENT_SUPPORTED",
                    evidencePath = listOf(
                        "broadStructureSupported=true",
                        "localResidualCleared=true",
                        "curveMechanismSupported=true",
                        "comparableSamples=${evidence.comparableSamples}",
                        "broadCoherence=${evidence.broadCoherence}",
                    ),
                )
            } else {
                inconclusive(
                    reason = "BROAD_WITHOUT_CURVE_MECHANISM_SUPPORT",
                    nextEvidence = "verify Curve/MUL_ACT mechanism authority for the broad residual",
                    inflation = evidence.contradiction,
                )
            }
        }

        return inconclusive(
            reason = "INSUFFICIENT_STRUCTURE",
            nextEvidence = "collect comparable microstates that discriminate local, global, and environmental mechanisms",
            inflation = maxOf(evidence.contradiction, evidence.environmentalCorrelation),
        )
    }

    private fun supported(
        mechanism: CorrectionMechanism,
        direction: EffectDirection,
        reason: String,
        evidencePath: List<String>,
    ): MechanismClassification = MechanismClassification(
        decision = CorrectionDecision(
            mechanism = mechanism,
            effect = ExpectedEffect(
                direction = direction,
                lowerBound = null,
                upperBound = null,
                assumptions = listOf("mechanism classification only; magnitude belongs to TargetEstimator"),
                authority = MagnitudeAuthority.UNKNOWN,
                falsifier = "post-intervention residual structure contradicts selected mechanism",
            ),
            target = null,
            evidencePath = evidencePath,
        ),
        reasonCode = reason,
        uncertaintyInflation = 0.0,
        nextEvidence = "target estimation may proceed only with supported gain/context authority",
    )

    private fun inconclusive(reason: String, nextEvidence: String, inflation: Double): MechanismClassification =
        MechanismClassification(
            decision = CorrectionDecision.inconclusive(reason),
            reasonCode = reason,
            uncertaintyInflation = inflation.coerceIn(0.0, 1.0),
            nextEvidence = nextEvidence,
        )
}

data class ActuatorAllocation(
    val mechanism: CorrectionMechanism,
    val idealTarget: IdealTarget,
    val mapShare: Double,
    val curveShare: Double,
    val reason: String,
) {
    init {
        require(mapShare in 0.0..1.0)
        require(curveShare in 0.0..1.0)
        require(mapShare + curveShare <= 1.0 + 1e-12) { "same residual cannot be double-counted" }
    }
}

/**
 * Exclusive allocator: a classified residual is assigned to one actuator family
 * at a time. UNKNOWN/environmental/no-action receives no actuation allocation.
 */
object ExclusiveActuatorAllocator : ActuatorAllocator {
    fun allocate(mechanism: CorrectionMechanism, idealTarget: IdealTarget): ActuatorAllocation = when (mechanism) {
        CorrectionMechanism.MAP_LOCAL -> ActuatorAllocation(mechanism, idealTarget, 1.0, 0.0, "LOCAL_RESIDUAL_ONLY")
        CorrectionMechanism.CURVE_MUL_ACT -> ActuatorAllocation(mechanism, idealTarget, 0.0, 1.0, "GLOBAL_RESIDUAL_AFTER_LOCAL_REMOVAL")
        CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
        CorrectionMechanism.NO_ACTION,
        CorrectionMechanism.UNKNOWN -> ActuatorAllocation(mechanism, idealTarget, 0.0, 0.0, "NO_ACTUATION_WITHOUT_SUPPORTED_MECHANISM")
    }
}
