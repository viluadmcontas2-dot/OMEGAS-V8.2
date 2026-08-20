package com.omegas.prohub.physics

/**
 * Causal classifier for residual structure. Thresholds here are decision-policy
 * bounds over normalized evidence scores, never ECU constants or physical laws.
 * The classifier never manufactures a numeric target: target estimation remains
 * owned by TargetEstimator/KStarEstimator.
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
) {
    init {
        require(comparableSamples >= 0)
        listOf(localizedRepeatability, broadCoherence, environmentalCorrelation, contradiction).forEach {
            require(it.isFinite() && it in 0.0..1.0)
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
    private const val STRONG_SUPPORT = 0.65
    private const val DOMINANCE_MARGIN = 0.12
    private const val HIGH_CONTRADICTION = 0.60
    private const val ENVIRONMENTAL_DOMINANCE = 0.70

    fun classify(evidence: ResidualEvidence): MechanismClassification {
        if (evidence.contradiction >= HIGH_CONTRADICTION) {
            return inconclusive(
                reason = "CONTRADICTORY_EVIDENCE",
                nextEvidence = "collect comparable microstates until directional/model contradiction is resolved",
                inflation = evidence.contradiction,
            )
        }

        if (evidence.environmentalCorrelation >= ENVIRONMENTAL_DOMINANCE &&
            evidence.environmentalCorrelation >= evidence.localizedRepeatability &&
            evidence.environmentalCorrelation >= evidence.broadCoherence
        ) {
            return MechanismClassification(
                decision = CorrectionDecision(
                    mechanism = CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
                    effect = ExpectedEffect(
                        direction = evidence.direction,
                        lowerBound = null,
                        upperBound = null,
                        assumptions = listOf("environmental/context effect dominates residual structure"),
                        authority = MagnitudeAuthority.UNKNOWN,
                        falsifier = "residual remains after conditioning on pressure/temperature/water microstate",
                    ),
                    target = null,
                    evidencePath = listOf("environmentalCorrelation=${evidence.environmentalCorrelation}"),
                ),
                reasonCode = "ENVIRONMENTAL_CONFOUNDER",
                uncertaintyInflation = evidence.environmentalCorrelation,
                nextEvidence = "compare matched microstates conditioned on environmental context",
            )
        }

        val localDominates = evidence.localizedRepeatability >= STRONG_SUPPORT &&
            evidence.localizedRepeatability >= evidence.broadCoherence + DOMINANCE_MARGIN
        if (localDominates && evidence.mapMechanismSupported) {
            return supported(
                mechanism = CorrectionMechanism.MAP_LOCAL,
                direction = evidence.direction,
                reason = "LOCALIZED_REPEATABLE",
                evidencePath = listOf(
                    "localizedRepeatability=${evidence.localizedRepeatability}",
                    "mapMechanismSupported=true",
                    "comparableSamples=${evidence.comparableSamples}",
                ),
            )
        }

        val broadDominates = evidence.broadCoherence >= STRONG_SUPPORT &&
            evidence.broadCoherence >= evidence.localizedRepeatability + DOMINANCE_MARGIN
        if (broadDominates && evidence.curveMechanismSupported) {
            return supported(
                mechanism = CorrectionMechanism.CURVE_MUL_ACT,
                direction = evidence.direction,
                reason = "BROAD_COHERENT_SUPPORTED",
                evidencePath = listOf(
                    "broadCoherence=${evidence.broadCoherence}",
                    "curveMechanismSupported=true",
                    "comparableSamples=${evidence.comparableSamples}",
                ),
            )
        }

        return inconclusive(
            reason = if (broadDominates && !evidence.curveMechanismSupported) {
                "BROAD_WITHOUT_CURVE_MECHANISM_SUPPORT"
            } else if (localDominates && !evidence.mapMechanismSupported) {
                "LOCAL_WITHOUT_MAP_MECHANISM_SUPPORT"
            } else {
                "INSUFFICIENT_STRUCTURE"
            },
            nextEvidence = "collect comparable microstates that discriminate local, global, and environmental mechanisms",
            inflation = maxOf(evidence.contradiction, evidence.environmentalCorrelation * 0.5),
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
