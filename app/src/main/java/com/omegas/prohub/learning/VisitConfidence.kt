package com.omegas.prohub.learning

import kotlin.math.exp
import kotlin.math.pow

/** Confiança explicativa baseada em visitas físicas, nunca em quantidade bruta de janelas. */
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

    /** Alvo menor para evidência repetível e maior para dados contraditórios. */
    fun adaptiveTarget(spread: Double, spreadLimit: Double, consensus: Double): AdaptiveTarget {
        val repeatability = exp(-spread.coerceAtLeast(0.0) / spreadLimit.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        val safeConsensus = consensus.coerceIn(0.0, 1.0)
        val target = when {
            safeConsensus >= 0.95 && repeatability >= 0.90 -> 3
            safeConsensus >= 0.80 && repeatability >= 0.70 -> 5
            safeConsensus >= 0.60 && repeatability >= 0.45 -> 7
            else -> 10
        }
        val uncertainty = (1.0 - (repeatability * safeConsensus)).coerceIn(0.0, 1.0)
        val center = (1.0 - uncertainty * 0.5).coerceIn(0.0, 1.0)
        val radius = (0.05 + uncertainty * 0.20).coerceIn(0.05, 0.25)
        return AdaptiveTarget(target, (center - radius).coerceAtLeast(0.0), (center + radius).coerceAtMost(1.0))
    }

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
        val safeUnique = uniqueVisits.coerceAtLeast(0)
        val safeEffective = effectiveVisits.coerceAtLeast(0.0)
        val safeSpreadLimit = spreadLimit.coerceAtLeast(1e-9)
        val safeConsensus = consensus.coerceIn(0.0, 1.0)
        val repeatability = exp(-spread.coerceAtLeast(0.0) / safeSpreadLimit).coerceIn(0.0, 1.0)
        val target = confirmedVisits.coerceAtLeast(1)
        val visitProgress = (safeUnique / target.toDouble()).coerceIn(0.0, 1.0)
        val effectiveProgress = (safeEffective / target.toDouble()).coerceIn(0.0, 1.0)
        val confidence = listOf(
            visitProgress.coerceAtLeast(if (safeUnique > 0) 0.05 else 0.0),
            effectiveProgress.coerceAtLeast(if (safeEffective > 0.0) 0.05 else 0.0),
            repeatability,
            safeConsensus.coerceAtLeast(if (safeUnique > 0) 0.05 else 0.0),
        ).let { values ->
            if (safeUnique == 0) 0.0 else values.fold(1.0) { acc, value -> acc * value.coerceIn(0.0001, 1.0) }
                .pow(1.0 / values.size)
                .coerceIn(0.0, 1.0)
        }
        val stage = when {
            safeUnique >= confirmedVisits && repeatability >= 0.60 && safeConsensus >= 0.75 -> "CONFIRMED"
            safeUnique >= acceptedVisits && repeatability >= 0.40 && safeConsensus >= 0.60 -> "ACCEPTED"
            safeUnique >= provisionalVisits -> "PROVISIONAL"
            else -> "OBSERVED"
        }
        return Result(
            stage = stage,
            confidence = confidence,
            uniqueVisits = safeUnique,
            effectiveVisits = safeEffective,
            repeatability = repeatability,
            consensus = safeConsensus,
        )
    }
}
