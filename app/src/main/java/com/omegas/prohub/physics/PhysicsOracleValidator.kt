package com.omegas.prohub.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

    /**
     * Offline seeded oracle for Step 150. Timing uncertainties are sampled in
     * log space so positive injection times remain positive. Gain is sampled as
     * a truncated normal derived from the typed 95% interval. Context/model/
     * contradiction terms are independent zero-mean theta perturbations.
     *
     * This is validation infrastructure, not a per-frame runtime path.
     */
    fun monteCarloPropagation(
        petrolOnGasMs: Double,
        petrolReferenceMs: Double,
        currentFactor: Double,
        gain: PlantGain,
        uncertainty: KStarUncertaintyComponents,
        draws: Int,
        seed: Long,
    ): OracleSummary {
        require(petrolOnGasMs > 0.0 && petrolReferenceMs > 0.0 && currentFactor > 0.0)
        require(draws >= 100)
        val g = gain.mean
        val lowerGain = gain.lower
        val upperGain = gain.upper
        if (
            g == null || lowerGain == null || upperGain == null ||
            !g.isFinite() || g <= 0.0 || lowerGain <= 0.0 || upperGain < lowerGain
        ) {
            return OracleSummary(null, null, null, MagnitudeAuthority.UNKNOWN, true, "PLANT_GAIN_UNKNOWN", 0)
        }

        val random = Random(seed)
        val gainStd = ((upperGain - lowerGain) / 3.92).coerceAtLeast(0.0)
        val baseTheta = ln(currentFactor)
        val gasLog = ln(petrolOnGasMs)
        val referenceLog = ln(petrolReferenceMs)
        val targets = DoubleArray(draws)
        repeat(draws) { index ->
            val sampledGasLog = gasLog + normal(random) * uncertainty.petrolOnGasRelativeStd
            val sampledReferenceLog = referenceLog + normal(random) * uncertainty.petrolReferenceRelativeStd
            val sampledGain = positiveGainSample(random, g, gainStd)
            val sampledTheta =
                baseTheta + normal(random) * uncertainty.currentThetaStd +
                    (sampledGasLog - sampledReferenceLog) / sampledGain +
                    normal(random) * uncertainty.contextThetaStd +
                    normal(random) * uncertainty.modelThetaStd +
                    normal(random) * uncertainty.contradictionThetaStd
            targets[index] = exp(sampledTheta)
        }
        targets.sort()
        return OracleSummary(
            meanTargetFactor = targets.average(),
            lower95 = quantile(targets, 0.025),
            upper95 = quantile(targets, 0.975),
            authority = gain.authority,
            abstained = false,
            reason = "SEEDED_MONTE_CARLO_LOG_PROPAGATION",
            draws = draws,
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

    private fun positiveGainSample(random: Random, mean: Double, std: Double): Double {
        if (std == 0.0) return mean
        while (true) {
            val candidate = mean + normal(random) * std
            if (candidate > 0.0 && candidate.isFinite()) return candidate
        }
    }

    private fun normal(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    private fun quantile(sorted: DoubleArray, probability: Double): Double {
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lowerIndex = position.toInt()
        val upperIndex = minOf(lowerIndex + 1, sorted.lastIndex)
        val fraction = position - lowerIndex
        return sorted[lowerIndex] * (1.0 - fraction) + sorted[upperIndex] * fraction
    }
}
