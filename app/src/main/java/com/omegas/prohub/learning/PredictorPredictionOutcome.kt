package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import kotlin.math.abs
import kotlin.math.ln

data class PredictionOutcome(
    val predictionId: String,
    val predictionRevisionToken: String,
    val cell: PredictorCell,
    val predictedEstimateK: Double,
    val lowerK: Double,
    val upperK: Double,
    val pImprove: Double?,
    val context: PredictorOperatingPoint,
    val appliedTargetK: Double?,
    val actualKStar: Double?,
    val authority: MagnitudeAuthority,
    val model: PredictorModelDescriptor,
    val evidenceRefs: List<String>,
) {
    init {
        require(predictionId.isNotBlank())
        require(predictionRevisionToken.isNotBlank())
        require(cell.row >= 0 && cell.column >= 0)
        require(predictedEstimateK.isFinite() && predictedEstimateK > 0.0)
        require(lowerK.isFinite() && lowerK > 0.0)
        require(upperK.isFinite() && upperK >= lowerK)
        require(predictedEstimateK in lowerK..upperK) { "Prediction estimate must lie inside its interval" }
        require(pImprove == null || pImprove.isFinite() && pImprove in 0.0..1.0)
        require(context.valid())
        require(appliedTargetK == null || appliedTargetK.isFinite() && appliedTargetK > 0.0)
        require(actualKStar == null || actualKStar.isFinite() && actualKStar > 0.0)
        require(evidenceRefs.isNotEmpty() && evidenceRefs.none { it.isBlank() })
    }

    fun completeForCalibration(): Boolean = appliedTargetK != null && actualKStar != null

    fun actualInsideInterval(): Boolean = actualKStar?.let { it in lowerK..upperK } ?: false

    fun absoluteLogError(): Double? = actualKStar?.let { actual ->
        abs(ln(actual / predictedEstimateK))
    }
}

data class PredictorCalibrationAssessment(
    val stats: PredictorPredictionErrorStats,
    val actionabilityDowngraded: Boolean,
    val reason: String,
)

/**
 * Pure reducer over real post-write outcomes. It owns no persistence and cannot
 * promote actionability; an interval miss can only emit a downgrade signal.
 */
object PredictorPredictionCalibration {
    fun reduce(
        prior: PredictorPredictionErrorStats,
        outcomes: List<PredictionOutcome>,
    ): PredictorCalibrationAssessment {
        val complete = outcomes
            .filter { it.completeForCalibration() }
            .sortedBy { it.predictionId }
        if (complete.isEmpty()) {
            return PredictorCalibrationAssessment(
                stats = prior,
                actionabilityDowngraded = false,
                reason = "NO_COMPLETE_POST_WRITE_OUTCOME",
            )
        }
        require(complete.map { it.predictionId }.distinct().size == complete.size) {
            "Duplicate predictionId in calibration batch"
        }

        val newHits = complete.count { it.actualInsideInterval() }
        val newMisses = complete.size - newHits
        val errorSum = complete.sumOf { requireNotNull(it.absoluteLogError()) }
        val totalSamples = prior.sampleCount + complete.size
        val totalHits = prior.intervalHitCount + newHits
        val totalMisses = prior.intervalMissCount + newMisses
        val meanError = (
            prior.meanAbsoluteLogError * prior.sampleCount.toDouble() + errorSum
        ) / totalSamples.toDouble()
        val missRate = totalMisses.toDouble() / totalSamples.toDouble()
        val stats = PredictorPredictionErrorStats(
            sampleCount = totalSamples,
            intervalHitCount = totalHits,
            intervalMissCount = totalMisses,
            meanAbsoluteLogError = meanError,
            calibrationError = meanError + missRate,
        )
        val downgraded = newMisses > 0
        return PredictorCalibrationAssessment(
            stats = stats,
            actionabilityDowngraded = downgraded,
            reason = if (downgraded) {
                "OBSERVED_OUTSIDE_PREDICTION_INTERVAL"
            } else {
                "NO_INTERVAL_MISS"
            },
        )
    }
}
