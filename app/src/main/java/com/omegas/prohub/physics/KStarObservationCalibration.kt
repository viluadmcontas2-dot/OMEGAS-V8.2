package com.omegas.prohub.physics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Evidence stage for a real K* observation. FALLBACK_COLLECTION is deliberately
 * non-authoritative: accumulating frames does not repair weak reference/context.
 */
enum class KStarDirectStage {
    OBSERVED,
    DIRECT_PROVISIONAL,
    DIRECT_CONFIRMED,
    FALLBACK_COLLECTION,
    ABSTAIN,
}

/**
 * Standard-uncertainty components used by the bounded runtime approximation.
 * Relative timing terms are log-space first-order standard deviations. Theta
 * terms are additive standard deviations in ln(F). No field carries a writer or
 * actionability threshold.
 */
data class KStarUncertaintyComponents(
    val petrolOnGasRelativeStd: Double,
    val petrolReferenceRelativeStd: Double,
    val currentThetaStd: Double,
    val contextThetaStd: Double,
    val modelThetaStd: Double,
    val contradictionThetaStd: Double,
) {
    init {
        listOf(
            petrolOnGasRelativeStd,
            petrolReferenceRelativeStd,
            currentThetaStd,
            contextThetaStd,
            modelThetaStd,
            contradictionThetaStd,
        ).forEach { value -> require(value.isFinite() && value >= 0.0) }
    }
}

data class KStarUncertaintyEstimate(
    val meanTargetFactor: Double?,
    val lower95: Double?,
    val upper95: Double?,
    val logErrorStd: Double?,
    val targetThetaStd: Double?,
    val abstained: Boolean,
    val reason: String,
)

/**
 * Components remain separate so later reliability calibration can learn which
 * dimension is miscalibrated instead of hiding everything in one opaque score.
 */
data class KStarConfidenceComponents(
    val reference: Double,
    val observation: Double,
    val effectiveSamples: Double,
    val independentVisits: Double,
    val geometricLocality: Double,
    val contextMatch: Double,
    val modelFit: Double,
    val calibrationFreshness: Double,
) {
    init {
        values().forEach { value -> require(value.isFinite() && value in 0.0..1.0) }
    }

    internal fun values(): List<Double> = listOf(
        reference,
        observation,
        effectiveSamples,
        independentVisits,
        geometricLocality,
        contextMatch,
        modelFit,
        calibrationFreshness,
    )
}

data class KStarConfidenceAssessment(
    val components: KStarConfidenceComponents,
    val score: Double,
)

data class KStarCalibratedObservation(
    val estimate: KStarEstimate,
    val stage: KStarDirectStage,
    val directAuthority: Boolean,
    val frameCount: Int,
    val strongReference: Boolean,
    val ambiguityResolved: Boolean,
    val uncertainty: KStarUncertaintyEstimate,
    val confidence: KStarConfidenceAssessment,
)

/**
 * Calibration layer around the canonical [KStarEstimator]. It never recomputes
 * K*, F*, or plant gain. Its responsibilities are limited to evidence stage,
 * bounded uncertainty propagation, and transparent monotonic confidence.
 */
object KStarObservationCalibration {
    const val DIRECT_PROVISIONAL_FRAMES: Int = 4
    const val DIRECT_CONFIRMED_FRAMES: Int = 6
    const val AMBIGUITY_FALLBACK_FRAMES: Int = 8

    private const val NORMAL_95 = 1.96
    private const val NORMAL_95_FULL_WIDTH = 3.92

    fun evaluate(
        estimate: KStarEstimate,
        frameCount: Int,
        strongReference: Boolean,
        ambiguityResolved: Boolean,
        uncertainty: KStarUncertaintyComponents,
        confidenceComponents: KStarConfidenceComponents,
    ): KStarCalibratedObservation {
        require(frameCount >= 0)
        val stage = stage(
            estimate = estimate,
            frameCount = frameCount,
            strongReference = strongReference,
            ambiguityResolved = ambiguityResolved,
        )
        return KStarCalibratedObservation(
            estimate = estimate,
            stage = stage,
            directAuthority = stage == KStarDirectStage.DIRECT_PROVISIONAL ||
                stage == KStarDirectStage.DIRECT_CONFIRMED,
            frameCount = frameCount,
            strongReference = strongReference,
            ambiguityResolved = ambiguityResolved,
            uncertainty = propagate(estimate, uncertainty),
            confidence = confidence(confidenceComponents),
        )
    }

    fun propagate(
        estimate: KStarEstimate,
        uncertainty: KStarUncertaintyComponents,
    ): KStarUncertaintyEstimate {
        val targetTheta = estimate.targetTheta
        val targetFactor = estimate.targetFactor
        if (estimate.abstained || targetTheta == null || targetFactor == null) {
            return abstainedUncertainty("KSTAR_ESTIMATE_ABSTAINED:${estimate.reason}")
        }
        val gainMean = estimate.gain.mean
        val gainLower = estimate.gain.lower
        val gainUpper = estimate.gain.upper
        if (
            gainMean == null || gainLower == null || gainUpper == null ||
            !gainMean.isFinite() || gainMean <= 0.0 ||
            !gainLower.isFinite() || !gainUpper.isFinite() ||
            gainLower <= 0.0 || gainUpper < gainLower
        ) {
            return abstainedUncertainty("GAIN_INTERVAL_UNKNOWN")
        }

        val logErrorStd = sqrt(
            uncertainty.petrolOnGasRelativeStd * uncertainty.petrolOnGasRelativeStd +
                uncertainty.petrolReferenceRelativeStd * uncertainty.petrolReferenceRelativeStd,
        )
        val gainStd = ((gainUpper - gainLower) / NORMAL_95_FULL_WIDTH).coerceAtLeast(0.0)
        val gainContribution = abs(estimate.logError) * gainStd / (gainMean * gainMean)
        val observationContribution = logErrorStd / gainMean
        val sigmaTheta = sqrt(
            uncertainty.currentThetaStd.squared() +
                observationContribution.squared() +
                gainContribution.squared() +
                uncertainty.contextThetaStd.squared() +
                uncertainty.modelThetaStd.squared() +
                uncertainty.contradictionThetaStd.squared(),
        )
        val halfWidth = NORMAL_95 * sigmaTheta
        return KStarUncertaintyEstimate(
            meanTargetFactor = targetFactor,
            lower95 = exp(targetTheta - halfWidth),
            upper95 = exp(targetTheta + halfWidth),
            logErrorStd = logErrorStd,
            targetThetaStd = sigmaTheta,
            abstained = false,
            reason = "FIRST_ORDER_LOG_PROPAGATION",
        )
    }

    fun confidence(components: KStarConfidenceComponents): KStarConfidenceAssessment {
        val values = components.values()
        val score = if (values.any { it == 0.0 }) {
            0.0
        } else {
            values.fold(1.0) { acc, value -> acc * value }.pow(1.0 / values.size)
        }
        return KStarConfidenceAssessment(
            components = components,
            score = score.coerceIn(0.0, 1.0),
        )
    }

    private fun stage(
        estimate: KStarEstimate,
        frameCount: Int,
        strongReference: Boolean,
        ambiguityResolved: Boolean,
    ): KStarDirectStage = when {
        estimate.abstained -> KStarDirectStage.ABSTAIN
        strongReference && ambiguityResolved && frameCount >= DIRECT_CONFIRMED_FRAMES ->
            KStarDirectStage.DIRECT_CONFIRMED
        strongReference && ambiguityResolved && frameCount >= DIRECT_PROVISIONAL_FRAMES ->
            KStarDirectStage.DIRECT_PROVISIONAL
        (!strongReference || !ambiguityResolved) && frameCount >= AMBIGUITY_FALLBACK_FRAMES ->
            KStarDirectStage.FALLBACK_COLLECTION
        else -> KStarDirectStage.OBSERVED
    }

    private fun abstainedUncertainty(reason: String): KStarUncertaintyEstimate = KStarUncertaintyEstimate(
        meanTargetFactor = null,
        lower95 = null,
        upper95 = null,
        logErrorStd = null,
        targetThetaStd = null,
        abstained = true,
        reason = reason,
    )
}

private fun Double.squared(): Double = this * this
