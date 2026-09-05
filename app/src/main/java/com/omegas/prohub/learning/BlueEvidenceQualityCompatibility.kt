package com.omegas.prohub.learning

import kotlin.math.exp

/**
 * Temporary source-compatibility projection for RED persistence/UI fields.
 *
 * Despite the historical symbol name, this object does NOT award confidence for
 * accumulating visits. Confidence is only a display-quality projection from
 * repeatability + directional consensus; it has no correction authority and is
 * scheduled for removal after SignalLearningStore is reduced to passive storage.
 */
object VisitConfidence {
    data class Result(
        val stage: String,
        val confidence: Double,
        val uniqueVisits: Int,
        val effectiveVisits: Double,
        val repeatability: Double,
        val consensus: Double,
    )

    data class AdaptiveTarget(
        val targetVisits: Int,
        val confidenceBandLow: Double,
        val confidenceBandHigh: Double,
    )

    fun adaptiveTarget(spread: Double, spreadLimit: Double, consensus: Double): AdaptiveTarget {
        val repeatability = exp(-spread.coerceAtLeast(0.0) / spreadLimit.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        val quality = (repeatability * consensus.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        return AdaptiveTarget(
            targetVisits = 1,
            confidenceBandLow = (quality - 0.10).coerceAtLeast(0.0),
            confidenceBandHigh = (quality + 0.10).coerceAtMost(1.0),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun evaluate(
        uniqueVisits: Int,
        effectiveVisits: Double,
        spread: Double,
        spreadLimit: Double,
        consensus: Double,
        provisionalVisits: Int,
        acceptedVisits: Int,
        confirmedVisits: Int,
    ): Result {
        val repeatability = exp(-spread.coerceAtLeast(0.0) / spreadLimit.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        val safeConsensus = consensus.coerceIn(0.0, 1.0)
        val confidence = (repeatability * safeConsensus).coerceIn(0.0, 1.0)
        return Result(
            stage = if (uniqueVisits > 0) "EVIDENCE_QUALITY_ONLY" else "OBSERVED",
            confidence = confidence,
            uniqueVisits = uniqueVisits.coerceAtLeast(0),
            effectiveVisits = effectiveVisits.coerceAtLeast(0.0),
            repeatability = repeatability,
            consensus = safeConsensus,
        )
    }
}
