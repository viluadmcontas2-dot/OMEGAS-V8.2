package com.omegas.prohub.physics

/**
 * Metadata contract crossing Physics -> Suggestion -> Draft -> UI without
 * acquiring serial/write authority. The physical ideal and the operational step
 * are carried separately all the way through the projection.
 */
data class PhysicsSuggestionContract(
    val mechanism: CorrectionMechanism,
    val expectedEffect: ExpectedEffect,
    val idealTargetFactor: Double?,
    val idealTargetAuthority: MagnitudeAuthority,
    val appliedStepFactor: Double?,
    val appliedStepFraction: Double?,
    val stepAuthority: MagnitudeAuthority,
    val evidencePath: List<String>,
) {
    init {
        require(evidencePath.isNotEmpty())
        require(appliedStepFraction == null || appliedStepFraction in 0.0..1.0)
        if (idealTargetFactor == null) require(idealTargetAuthority == MagnitudeAuthority.UNKNOWN)
    }
}

data class PhysicsDraftContract(
    val suggestion: PhysicsSuggestionContract,
    val requiresHumanConfirmation: Boolean = true,
    val writerAuthority: Boolean = false,
) {
    init {
        require(requiresHumanConfirmation) { "Phase 06 cannot create an auto-write draft" }
        require(!writerAuthority) { "Physics metadata never acquires writer authority" }
    }
}

data class PhysicsUiContract(
    val mechanism: String,
    val magnitudeAuthority: String,
    val stepAuthority: String,
    val direction: String,
    val idealTargetFactor: Double?,
    val appliedStepFactor: Double?,
    val appliedStepIsIdealTarget: Boolean,
    val assumptions: List<String>,
    val falsifier: String,
    val requiresHumanConfirmation: Boolean,
)

object PhysicsContractPropagation {
    fun toSuggestion(decision: CorrectionDecision, appliedStep: AppliedStep?): PhysicsSuggestionContract {
        val target = decision.target
        return PhysicsSuggestionContract(
            mechanism = decision.mechanism,
            expectedEffect = decision.effect,
            idealTargetFactor = target?.factor,
            idealTargetAuthority = target?.authority ?: MagnitudeAuthority.UNKNOWN,
            appliedStepFactor = appliedStep?.factor,
            appliedStepFraction = appliedStep?.fraction,
            stepAuthority = appliedStep?.authority ?: MagnitudeAuthority.UNKNOWN,
            evidencePath = decision.evidencePath,
        )
    }

    fun toDraft(suggestion: PhysicsSuggestionContract): PhysicsDraftContract =
        PhysicsDraftContract(suggestion = suggestion)

    fun toUi(draft: PhysicsDraftContract): PhysicsUiContract {
        val suggestion = draft.suggestion
        val ideal = suggestion.idealTargetFactor
        val step = suggestion.appliedStepFactor
        return PhysicsUiContract(
            mechanism = suggestion.mechanism.name,
            magnitudeAuthority = suggestion.idealTargetAuthority.name,
            stepAuthority = suggestion.stepAuthority.name,
            direction = suggestion.expectedEffect.direction.name,
            idealTargetFactor = ideal,
            appliedStepFactor = step,
            appliedStepIsIdealTarget = ideal != null && step != null && kotlin.math.abs(ideal - step) < 1e-12,
            assumptions = suggestion.expectedEffect.assumptions,
            falsifier = suggestion.expectedEffect.falsifier,
            requiresHumanConfirmation = draft.requiresHumanConfirmation,
        )
    }
}
