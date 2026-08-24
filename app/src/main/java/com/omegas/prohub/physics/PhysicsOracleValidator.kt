package com.omegas.prohub.physics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

data class RatioObservation(val petrolOnGasMs: Double, val petrolReferenceMs: Double) {
    init { require(petrolOnGasMs > 0.0 && petrolReferenceMs > 0.0) }
}

data class HoldoutCase(
    val petrolOnGasMs: Double,
    val petrolReferenceMs: Double,
    val currentFactor: Double,
    val expectedTargetFactor: Double,
)

data class HoldoutCoverage(
    val covered: Int,
    val total: Int,
    val coverage: Double,
)

object PhysicsOracleValidator {
    fun bootstrap(
        observations: List<RatioObservation>,
        currentFactor: Double,
        gain: PlantGain,
        resamples: Int,
        seed: Long,
    ): OracleSummary {
        require(observations.isNotEmpty())
        require(currentFactor > 0.0)
        require(resamples >= 100)
        val g = gain.mean
        if (g == null || g <= 0.0) {
            return OracleSummary(null, null, null, MagnitudeAuthority.UNKNOWN, true, "PLANT_GAIN_UNKNOWN", 0)
        }
        val random = Random(seed)
        val targets = DoubleArray(resamples)
        repeat(resamples) { draw ->
            var logRatioSum = 0.0
            repeat(observations.size) {
                val item = observations[random.nextInt(observations.size)]
                logRatioSum += ln(item.petrolOnGasMs / item.petrolReferenceMs)
            }
            val meanError = logRatioSum / observations.size
            targets[draw] = exp(ln(currentFactor) + meanError / g)
        }
        targets.sort()
        return OracleSummary(
            meanTargetFactor = targets.average(),
            lower95 = quantile(targets, 0.025),
            upper95 = quantile(targets, 0.975),
            authority = gain.authority,
            abstained = false,
            reason = "SEEDED_BOOTSTRAP_ORACLE",
            draws = resamples,
        )
    }

    /**
     * First-order GUM-style propagation in log space. It is an independent
     * analytic cross-check for the Monte-Carlo/bootstrap oracle, not an ECU law.
     */
    fun gumEquivalent(
        petrolOnGasMs: Double,
        petrolReferenceMs: Double,
        currentFactor: Double,
        gain: PlantGain,
        relativeStd: Double,
    ): OracleSummary {
        require(petrolOnGasMs > 0.0 && petrolReferenceMs > 0.0 && currentFactor > 0.0)
        require(relativeStd >= 0.0)
        val g = gain.mean
        val lowerGain = gain.lower
        val upperGain = gain.upper
        if (g == null || lowerGain == null || upperGain == null || g <= 0.0) {
            return OracleSummary(null, null, null, MagnitudeAuthority.UNKNOWN, true, "PLANT_GAIN_UNKNOWN", 0)
        }
        val error = ln(petrolOnGasMs / petrolReferenceMs)
        val theta = ln(currentFactor) + error / g
        val gainStd = ((upperGain - lowerGain) / 3.92).coerceAtLeast(0.0)
        val ratioStdLog = sqrt(2.0) * relativeStd
        val sigmaTheta = sqrt(
            (ratioStdLog / g) * (ratioStdLog / g) +
                (abs(error) * gainStd / (g * g)) * (abs(error) * gainStd / (g * g)),
        )
        val halfWidth = 1.96 * sigmaTheta
        return OracleSummary(
            meanTargetFactor = exp(theta),
            lower95 = exp(theta - halfWidth),
            upper95 = exp(theta + halfWidth),
            authority = gain.authority,
            abstained = false,
            reason = "GUM_EQUIVALENT_LOG_PROPAGATION",
            draws = 0,
        )
    }

    fun evaluateHoldouts(
        holdouts: List<HoldoutCase>,
        gain: PlantGain,
        relativeStd: Double,
    ): HoldoutCoverage {
        require(holdouts.isNotEmpty())
        var covered = 0
        holdouts.forEach { item ->
            val interval = gumEquivalent(
                item.petrolOnGasMs,
                item.petrolReferenceMs,
                item.currentFactor,
                gain,
                relativeStd,
            )
            val lower = interval.lower95
            val upper = interval.upper95
            if (lower != null && upper != null && item.expectedTargetFactor in lower..upper) covered++
        }
        return HoldoutCoverage(
            covered = covered,
            total = holdouts.size,
            coverage = covered.toDouble() / holdouts.size,
        )
    }

    private fun quantile(sorted: DoubleArray, probability: Double): Double {
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lowerIndex = position.toInt()
        val upperIndex = minOf(lowerIndex + 1, sorted.lastIndex)
        val fraction = position - lowerIndex
        return sorted[lowerIndex] * (1.0 - fraction) + sorted[upperIndex] * fraction
    }
}
