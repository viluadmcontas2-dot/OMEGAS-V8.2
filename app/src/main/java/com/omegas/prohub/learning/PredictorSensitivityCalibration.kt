package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.ln

data class PredictorSensitivityPosterior(
    val gMean: Double,
    val gVariance: Double,
    val modelErrorVariance: Double,
) {
    init {
        require(gMean.isFinite())
        require(gVariance.isFinite() && gVariance > 0.0)
        require(modelErrorVariance.isFinite() && modelErrorVariance >= 0.0)
    }
}

data class PredictorSensitivityInput(
    val sameIdentity: Boolean,
    val contextComparable: Boolean,
    val beforeError: Double,
    val afterError: Double,
    val beforeFactor: Double,
    val afterFactor: Double,
    val measurementVariance: Double,
    val processVariance: Double,
    val predictedAfterError: Double?,
    val prior: PredictorSensitivityPosterior,
    val provenance: List<String>,
) {
    init {
        require(beforeError.isFinite())
        require(afterError.isFinite())
        require(beforeFactor.isFinite() && beforeFactor > 0.0)
        require(afterFactor.isFinite() && afterFactor > 0.0)
        require(measurementVariance.isFinite() && measurementVariance > 0.0)
        require(processVariance.isFinite() && processVariance >= 0.0)
        require(predictedAfterError == null || predictedAfterError.isFinite())
        require(provenance.isNotEmpty() && provenance.none { it.isBlank() })
    }
}

data class PredictorRealOutcomeEvidence(
    val beforeError: Double,
    val afterError: Double,
    val deltaLogFactor: Double,
    val gObserved: Double,
    val provenance: List<String>,
)

data class PredictorSensitivityResult(
    val accepted: Boolean,
    val reason: String,
    val gHat: Double?,
    val prior: PredictorSensitivityPosterior,
    val posterior: PredictorSensitivityPosterior,
    val processVariance: Double,
    val modelDowngraded: Boolean,
    val realEvidence: PredictorRealOutcomeEvidence?,
)

/**
 * Causal update from a confirmed real intervention. Prediction is used only to
 * score model error; it is never converted into physical evidence.
 */
object PredictorSensitivityCalibration {
    private const val MIN_ABS_DELTA_LOG_FACTOR = 1e-6

    fun update(input: PredictorSensitivityInput): PredictorSensitivityResult {
        if (!input.sameIdentity) return abstain(input, "IDENTITY_MISMATCH")
        if (!input.contextComparable) return abstain(input, "CONTEXT_NOT_COMPARABLE")

        val deltaLogFactor = ln(input.afterFactor / input.beforeFactor)
        if (!deltaLogFactor.isFinite() || abs(deltaLogFactor) < MIN_ABS_DELTA_LOG_FACTOR) {
            return abstain(input, "INTERVENTION_DENOMINATOR_TOO_SMALL")
        }

        val deltaError = input.afterError - input.beforeError
        val gHat = -deltaError / deltaLogFactor
        if (!gHat.isFinite()) return abstain(input, "NON_FINITE_SENSITIVITY")

        val driftedPriorVariance = input.prior.gVariance + input.processVariance
        val observationVariance = input.measurementVariance / (deltaLogFactor * deltaLogFactor)
        val priorPrecision = 1.0 / driftedPriorVariance
        val observationPrecision = 1.0 / observationVariance
        val posteriorVariance = 1.0 / (priorPrecision + observationPrecision)
        val posteriorMean = posteriorVariance * (
            input.prior.gMean * priorPrecision + gHat * observationPrecision
        )

        val predictionResidual = input.predictedAfterError?.let { predicted -> input.afterError - predicted }
        val predictedChange = input.predictedAfterError?.let { it - input.beforeError }
        val actualChange = input.afterError - input.beforeError
        val contradicted = predictedChange != null &&
            predictedChange != 0.0 && actualChange != 0.0 && predictedChange * actualChange < 0.0
        val nextModelErrorVariance = if (contradicted && predictionResidual != null) {
            input.prior.modelErrorVariance + predictionResidual * predictionResidual
        } else {
            input.prior.modelErrorVariance
        }
        val posterior = PredictorSensitivityPosterior(
            gMean = posteriorMean,
            gVariance = posteriorVariance,
            modelErrorVariance = nextModelErrorVariance,
        )
        return PredictorSensitivityResult(
            accepted = true,
            reason = if (contradicted) "REAL_OUTCOME_CONTRADICTED_PREDICTION" else "REAL_OUTCOME_ACCEPTED",
            gHat = gHat,
            prior = input.prior,
            posterior = posterior,
            processVariance = input.processVariance,
            modelDowngraded = contradicted,
            realEvidence = PredictorRealOutcomeEvidence(
                beforeError = input.beforeError,
                afterError = input.afterError,
                deltaLogFactor = deltaLogFactor,
                gObserved = gHat,
                provenance = input.provenance.toList(),
            ),
        )
    }

    private fun abstain(input: PredictorSensitivityInput, reason: String): PredictorSensitivityResult =
        PredictorSensitivityResult(
            accepted = false,
            reason = reason,
            gHat = null,
            prior = input.prior,
            posterior = input.prior,
            processVariance = input.processVariance,
            modelDowngraded = false,
            realEvidence = null,
        )
}
