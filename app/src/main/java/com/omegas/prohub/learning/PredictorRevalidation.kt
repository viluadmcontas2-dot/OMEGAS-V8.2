package com.omegas.prohub.learning

import kotlin.math.abs

enum class PredictorRevalidationEvidenceState {
    WAITING,
    DIRECT_PROVISIONAL,
    DIRECT_CONFIRMED,
    FALLBACK_CONFIRMED,
    REJECTED,
}

data class PredictorRevalidationInput(
    val suggestionState: PredictorSuggestionState,
    val referenceStrong: Boolean,
    val contextComparable: Boolean,
    val afterSourceType: PredictorScientificSourceType,
    val afterFrameCount: Int,
    val beforeError: Double,
    val afterError: Double,
    val zeroBand: Double,
    val noChangeTolerance: Double,
    val sensitivityInput: PredictorSensitivityInput?,
) {
    init {
        require(afterFrameCount >= 0)
        require(beforeError.isFinite())
        require(afterError.isFinite())
        require(zeroBand.isFinite() && zeroBand >= 0.0)
        require(noChangeTolerance.isFinite() && noChangeTolerance >= 0.0)
    }
}

data class PredictorRevalidationResult(
    val evidenceState: PredictorRevalidationEvidenceState,
    val lifecycleState: PredictorSuggestionState,
    val preliminaryOutcome: PredictorSuggestionState?,
    val adaptationAllowed: Boolean,
    val sensitivityResult: PredictorSensitivityResult?,
    val modelDowngraded: Boolean,
    val reason: String,
)

/**
 * Pure post-write revalidation policy. Four strong/comparable real frames may
 * publish a provisional direction; six confirm it. Weak reference falls back to
 * eight frames. Only real scientific ingress can authorize adaptation.
 */
object PredictorRevalidation {
    fun evaluate(input: PredictorRevalidationInput): PredictorRevalidationResult {
        if (input.suggestionState != PredictorSuggestionState.REVALIDATING) {
            return waiting(
                input = input,
                evidenceState = PredictorRevalidationEvidenceState.REJECTED,
                reason = "SUGGESTION_NOT_REVALIDATING",
            )
        }
        val sourceAuthority = PredictorScientificIngress.classify(input.afterSourceType)
        if (!sourceAuthority.acceptedAsEvidence) {
            return waiting(input, PredictorRevalidationEvidenceState.WAITING, "AFTER_SOURCE_NOT_REAL")
        }
        if (!input.contextComparable) {
            return waiting(input, PredictorRevalidationEvidenceState.WAITING, "AFTER_CONTEXT_NOT_COMPARABLE")
        }

        val evidenceState = when {
            input.referenceStrong && input.afterFrameCount >= 6 -> PredictorRevalidationEvidenceState.DIRECT_CONFIRMED
            input.referenceStrong && input.afterFrameCount >= 4 -> PredictorRevalidationEvidenceState.DIRECT_PROVISIONAL
            !input.referenceStrong && input.afterFrameCount >= 8 -> PredictorRevalidationEvidenceState.FALLBACK_CONFIRMED
            else -> PredictorRevalidationEvidenceState.WAITING
        }
        if (evidenceState == PredictorRevalidationEvidenceState.WAITING) {
            return waiting(input, evidenceState, "AFTER_EVIDENCE_INSUFFICIENT")
        }

        val outcome = classifyOutcome(input)
        if (evidenceState == PredictorRevalidationEvidenceState.DIRECT_PROVISIONAL) {
            return PredictorRevalidationResult(
                evidenceState = evidenceState,
                lifecycleState = PredictorSuggestionState.REVALIDATING,
                preliminaryOutcome = outcome,
                adaptationAllowed = false,
                sensitivityResult = null,
                modelDowngraded = false,
                reason = "DIRECT_PROVISIONAL_AWAITING_CONFIRMATION",
            )
        }

        val sensitivity = input.sensitivityInput?.let(PredictorSensitivityCalibration::update)
        val downgraded = outcome == PredictorSuggestionState.REGRESSED || sensitivity?.modelDowngraded == true
        return PredictorRevalidationResult(
            evidenceState = evidenceState,
            lifecycleState = outcome,
            preliminaryOutcome = outcome,
            adaptationAllowed = true,
            sensitivityResult = sensitivity,
            modelDowngraded = downgraded,
            reason = if (downgraded) "REAL_OUTCOME_REQUIRES_MODEL_DOWNGRADE" else "REAL_OUTCOME_CONFIRMED",
        )
    }

    private fun classifyOutcome(input: PredictorRevalidationInput): PredictorSuggestionState {
        val before = abs(input.beforeError)
        val after = abs(input.afterError)
        if (after <= input.zeroBand) return PredictorSuggestionState.CONVERGED
        val improvement = before - after
        return when {
            improvement > input.noChangeTolerance -> PredictorSuggestionState.IMPROVED
            improvement < -input.noChangeTolerance -> PredictorSuggestionState.REGRESSED
            else -> PredictorSuggestionState.NO_CHANGE
        }
    }

    private fun waiting(
        input: PredictorRevalidationInput,
        evidenceState: PredictorRevalidationEvidenceState,
        reason: String,
    ): PredictorRevalidationResult = PredictorRevalidationResult(
        evidenceState = evidenceState,
        lifecycleState = input.suggestionState,
        preliminaryOutcome = null,
        adaptationAllowed = false,
        sensitivityResult = null,
        modelDowngraded = false,
        reason = reason,
    )
}
