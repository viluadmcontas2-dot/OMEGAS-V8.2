package com.omegas.prohub.learning

/** One independent historical prediction outcome used only for offline calibration proof. */
data class PredictorRiskOutcome(
    val epochId: String,
    val diagnosticConfidence: Double,
    val absoluteLogError: Double,
) {
    init {
        require(epochId.isNotBlank())
        require(diagnosticConfidence.isFinite() && diagnosticConfidence in 0.0..1.0)
        require(absoluteLogError.isFinite() && absoluteLogError >= 0.0)
    }
}

data class PredictorEpochRiskCoverage(
    val epochId: String,
    val totalCount: Int,
    val highConfidenceCount: Int,
    val highConfidenceCoverage: Double,
    val overallMeanAbsoluteLogError: Double,
    val highConfidenceMeanAbsoluteLogError: Double,
)

data class PredictorRiskCoverageReport(
    val calibrated: Boolean,
    val highConfidenceCutoff: Double,
    val epochs: List<PredictorEpochRiskCoverage>,
    val reason: String,
)

/**
 * Offline leave-one-epoch risk/coverage validator. It does not authorize runtime
 * writes or predictions; it only proves whether diagnostic confidence actually
 * conditions error in the required direction on every held-out epoch.
 */
object PredictorRiskCoverageValidator {
    fun leaveOneEpoch(
        outcomes: List<PredictorRiskOutcome>,
        highConfidenceCutoff: Double,
    ): PredictorRiskCoverageReport {
        require(highConfidenceCutoff.isFinite() && highConfidenceCutoff in 0.0..1.0)
        val grouped = outcomes.groupBy { it.epochId }.toSortedMap()
        if (grouped.size < 2) {
            return PredictorRiskCoverageReport(
                calibrated = false,
                highConfidenceCutoff = highConfidenceCutoff,
                epochs = grouped.map { (epoch, items) -> epochSummary(epoch, items, highConfidenceCutoff) },
                reason = "INSUFFICIENT_EPOCHS_FOR_LEAVE_ONE_EPOCH",
            )
        }

        val summaries = grouped.map { (epoch, items) -> epochSummary(epoch, items, highConfidenceCutoff) }
        if (summaries.any { it.highConfidenceCount == 0 }) {
            return PredictorRiskCoverageReport(
                calibrated = false,
                highConfidenceCutoff = highConfidenceCutoff,
                epochs = summaries,
                reason = "HIGH_CONFIDENCE_COVERAGE_EMPTY",
            )
        }
        val failures = summaries.filter {
            it.highConfidenceMeanAbsoluteLogError >= it.overallMeanAbsoluteLogError
        }
        return PredictorRiskCoverageReport(
            calibrated = failures.isEmpty(),
            highConfidenceCutoff = highConfidenceCutoff,
            epochs = summaries,
            reason = if (failures.isEmpty()) {
                "LEAVE_ONE_EPOCH_HIGH_CONFIDENCE_ERROR_IMPROVES"
            } else {
                "HIGH_CONFIDENCE_NOT_BETTER:${failures.joinToString(",") { it.epochId }}"
            },
        )
    }

    private fun epochSummary(
        epochId: String,
        outcomes: List<PredictorRiskOutcome>,
        cutoff: Double,
    ): PredictorEpochRiskCoverage {
        val high = outcomes.filter { it.diagnosticConfidence >= cutoff }
        val overallMean = outcomes.map { it.absoluteLogError }.averageOrZero()
        val highMean = high.map { it.absoluteLogError }.averageOrInfinity()
        return PredictorEpochRiskCoverage(
            epochId = epochId,
            totalCount = outcomes.size,
            highConfidenceCount = high.size,
            highConfidenceCoverage = if (outcomes.isEmpty()) 0.0 else high.size.toDouble() / outcomes.size.toDouble(),
            overallMeanAbsoluteLogError = overallMean,
            highConfidenceMeanAbsoluteLogError = highMean,
        )
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun List<Double>.averageOrInfinity(): Double = if (isEmpty()) Double.POSITIVE_INFINITY else average()
