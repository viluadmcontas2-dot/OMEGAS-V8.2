package com.omegas.prohub.learning

enum class PredictorCoordinateCandidate {
    CURRENT_PETROL_ON_GAS,
    MIDPOINT,
    PETROL_REFERENCE,
}

data class PredictorCoordinateHoldout(
    val epochId: String,
    val candidate: PredictorCoordinateCandidate,
    val absoluteLogError: Double,
    val riskLoss: Double,
) {
    init {
        require(epochId.isNotBlank())
        require(absoluteLogError.isFinite() && absoluteLogError >= 0.0)
        require(riskLoss.isFinite() && riskLoss >= 0.0)
    }
}

data class PredictorCoordinateValidationReport(
    val preferred: PredictorCoordinateCandidate?,
    val petrolReferenceValidated: Boolean,
    val reason: String,
    val p90ByCandidate: Map<PredictorCoordinateCandidate, Double>,
    val meanRiskByCandidate: Map<PredictorCoordinateCandidate, Double>,
)

data class PredictorContextAblationOutcome(
    val epochId: String,
    val base2dAbsoluteLogError: Double,
    val base2dRiskLoss: Double,
    val contextualAbsoluteLogError: Double?,
    val contextualRiskLoss: Double?,
    val contextAvailable: Boolean,
) {
    init {
        require(epochId.isNotBlank())
        require(base2dAbsoluteLogError.isFinite() && base2dAbsoluteLogError >= 0.0)
        require(base2dRiskLoss.isFinite() && base2dRiskLoss >= 0.0)
        require(contextualAbsoluteLogError == null || contextualAbsoluteLogError.isFinite() && contextualAbsoluteLogError >= 0.0)
        require(contextualRiskLoss == null || contextualRiskLoss.isFinite() && contextualRiskLoss >= 0.0)
    }
}

data class PredictorContextAblationReport(
    val promoteContextualDimension: Boolean,
    val reason: String,
)

/**
 * Offline validation only. It compares already-produced holdout outcomes and
 * cannot mutate Predictor state, runtime geometry or actionability.
 */
object PredictorGeometryAblationValidator {
    fun validateCoordinate(
        outcomes: List<PredictorCoordinateHoldout>,
    ): PredictorCoordinateValidationReport {
        val grouped = outcomes.groupBy { it.candidate }
        val expectedCandidates = PredictorCoordinateCandidate.entries.toSet()
        if (grouped.keys != expectedCandidates) {
            return coordinateUnavailable("INCOMPLETE_COORDINATE_HOLDOUTS")
        }
        val epochSets = expectedCandidates.associateWith { candidate ->
            grouped.getValue(candidate).map { it.epochId }.toSet()
        }
        val commonEpochs = epochSets.values.first()
        if (commonEpochs.size < 2 || epochSets.values.any { it != commonEpochs }) {
            return coordinateUnavailable("INCOMPARABLE_COORDINATE_EPOCHS")
        }

        val p90 = expectedCandidates.associateWith { candidate ->
            quantile(grouped.getValue(candidate).map { it.absoluteLogError }, 0.90)
        }
        val meanRisk = expectedCandidates.associateWith { candidate ->
            grouped.getValue(candidate).map { it.riskLoss }.average()
        }
        val dominators = expectedCandidates.filter { candidate ->
            expectedCandidates.filter { it != candidate }.all { other ->
                val noWorse = p90.getValue(candidate) <= p90.getValue(other) &&
                    meanRisk.getValue(candidate) <= meanRisk.getValue(other)
                val strictlyBetter = p90.getValue(candidate) < p90.getValue(other) ||
                    meanRisk.getValue(candidate) < meanRisk.getValue(other)
                noWorse && strictlyBetter
            }
        }
        val preferred = dominators.singleOrNull()
        val petrolReferenceValidated = preferred == PredictorCoordinateCandidate.PETROL_REFERENCE
        val reason = when {
            petrolReferenceValidated -> "PETROL_REFERENCE_DOMINATES_HOLDOUT"
            preferred != null -> "ALTERNATIVE_COORDINATE_DOMINATES_HOLDOUT"
            else -> "NO_COORDINATE_DOMINATES_ERROR_AND_RISK"
        }
        return PredictorCoordinateValidationReport(
            preferred = preferred,
            petrolReferenceValidated = petrolReferenceValidated,
            reason = reason,
            p90ByCandidate = p90,
            meanRiskByCandidate = meanRisk,
        )
    }

    fun validateContextual(
        outcomes: List<PredictorContextAblationOutcome>,
    ): PredictorContextAblationReport {
        if (outcomes.map { it.epochId }.toSet().size < 2) {
            return PredictorContextAblationReport(false, "INSUFFICIENT_CONTEXT_HOLDOUT_EPOCHS")
        }
        if (outcomes.any {
                !it.contextAvailable ||
                    it.contextualAbsoluteLogError == null ||
                    it.contextualRiskLoss == null
            }
        ) {
            return PredictorContextAblationReport(false, "CONTEXT_UNAVAILABLE_BASE_2D_RETAINED")
        }
        val strictlyBetterEverywhere = outcomes.all { outcome ->
            requireNotNull(outcome.contextualAbsoluteLogError) < outcome.base2dAbsoluteLogError &&
                requireNotNull(outcome.contextualRiskLoss) < outcome.base2dRiskLoss
        }
        return if (strictlyBetterEverywhere) {
            PredictorContextAblationReport(true, "CONTEXTUAL_HOLDOUT_IMPROVES_ERROR_AND_RISK")
        } else {
            PredictorContextAblationReport(false, "CONTEXTUAL_MODEL_NOT_STRICTLY_BETTER")
        }
    }

    private fun coordinateUnavailable(reason: String): PredictorCoordinateValidationReport =
        PredictorCoordinateValidationReport(
            preferred = null,
            petrolReferenceValidated = false,
            reason = reason,
            p90ByCandidate = emptyMap(),
            meanRiskByCandidate = emptyMap(),
        )

    private fun quantile(values: List<Double>, probability: Double): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lower = position.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction
    }
}
