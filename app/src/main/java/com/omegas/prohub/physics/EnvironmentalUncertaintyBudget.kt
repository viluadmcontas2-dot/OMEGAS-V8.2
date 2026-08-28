package com.omegas.prohub.physics

import kotlin.math.sqrt

data class EnvironmentalUncertaintyDecision(
    val adjustedUncertainty: Double,
    val nextAction: String,
    val requiresUnboundedCollection: Boolean,
)

object EnvironmentalUncertaintyBudget {
    /**
     * Environmental evidence changes uncertainty/conditioning policy, never the
     * physical target directly. There is deliberately no sample-count target.
     */
    fun adjust(
        baseUncertainty: Double,
        classification: MechanismClassification,
    ): EnvironmentalUncertaintyDecision {
        require(baseUncertainty >= 0.0)
        val inflation = classification.uncertaintyInflation.coerceIn(0.0, 1.0)
        val adjusted = sqrt(baseUncertainty * baseUncertainty + inflation * inflation)
        val environmental = classification.decision.mechanism == CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC
        return EnvironmentalUncertaintyDecision(
            adjustedUncertainty = if (inflation > 0.0) adjusted else baseUncertainty,
            nextAction = if (environmental) "CONDITION_MODEL_OR_MATCH_CONTEXT" else "PROCEED_WITH_SUPPORTED_MODEL",
            requiresUnboundedCollection = false,
        )
    }
}
