package com.omegas.prohub.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Request for the offline uncertainty oracle. Ratios are dimensionless. */
data class OracleRequest(
    val petrolOnGasMs: Double,
    val petrolReferenceMs: Double,
    val currentFactor: Double,
    val gain: PlantGain,
    val measurementStdRatio: Double,
    val driftStdRatio: Double,
    val draws: Int,
    val seed: Long,
) {
    init {
        require(petrolOnGasMs > 0.0 && petrolReferenceMs > 0.0 && currentFactor > 0.0)
        require(measurementStdRatio >= 0.0 && driftStdRatio >= 0.0)
        require(draws >= 100) { "oracle requires enough draws to form a useful interval" }
    }
}

data class OracleSummary(
    val meanTargetFactor: Double?,
    val lower95: Double?,
    val upper95: Double?,
    val authority: MagnitudeAuthority,
    val abstained: Boolean,
    val reason: String,
    val draws: Int,
)

/**
 * Deterministic seeded bootstrap/Monte-Carlo style oracle for Phase 06 tests.
 * It is deliberately offline-oriented; runtime uses the analytic KStarEstimator.
 */
object PhysicsUncertaintyOracle {
    fun estimate(request: OracleRequest): OracleSummary {
        val meanGain = request.gain.mean
        val lowerGain = request.gain.lower
        val upperGain = request.gain.upper
        if (meanGain == null || lowerGain == null || upperGain == null ||
            meanGain <= 0.0 || lowerGain <= 0.0 || upperGain < lowerGain
        ) {
            return OracleSummary(
                meanTargetFactor = null,
                lower95 = null,
                upper95 = null,
                authority = MagnitudeAuthority.UNKNOWN,
                abstained = true,
                reason = "PLANT_GAIN_UNKNOWN",
                draws = 0,
            )
        }

        val random = Random(request.seed)
        val nominalLogRatio = ln(request.petrolOnGasMs / request.petrolReferenceMs)
        val theta = ln(request.currentFactor)
        val samples = DoubleArray(request.draws)
        for (index in samples.indices) {
            val sampledGain = if (upperGain == lowerGain) lowerGain else random.nextDouble(lowerGain, upperGain)
            val measurementNoise = gaussian(random) * request.measurementStdRatio
            val driftNoise = gaussian(random) * request.driftStdRatio
            val sampledError = nominalLogRatio + measurementNoise + driftNoise
            samples[index] = exp(theta + sampledError / sampledGain)
        }
        samples.sort()
        val mean = samples.average()
        val lower = quantile(samples, 0.025)
        val upper = quantile(samples, 0.975)
        return OracleSummary(
            meanTargetFactor = mean,
            lower95 = lower,
            upper95 = upper,
            authority = request.gain.authority,
            abstained = false,
            reason = "SEEDED_UNCERTAINTY_ORACLE",
            draws = request.draws,
        )
    }

    private fun gaussian(random: Random): Double {
        val u1 = max(random.nextDouble(), 1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    private fun quantile(sorted: DoubleArray, probability: Double): Double {
        require(sorted.isNotEmpty())
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lowerIndex = position.toInt()
        val upperIndex = minOf(lowerIndex + 1, sorted.lastIndex)
        val fraction = position - lowerIndex
        return sorted[lowerIndex] * (1.0 - fraction) + sorted[upperIndex] * fraction
    }
}

/**
 * Lightweight conjugate-style posterior for plant gain g. A prior is not
 * empirical authority by itself; only an informative intervention promotes the
 * result to EMPIRICALLY_BOUNDED.
 */
data class PlantGainPosterior private constructor(
    val mean: Double?,
    val variance: Double?,
    val informativeUpdates: Int,
) {
    companion object {
        fun unknown(): PlantGainPosterior = PlantGainPosterior(null, null, 0)

        fun prior(mean: Double, variance: Double): PlantGainPosterior {
            require(mean > 0.0 && variance > 0.0)
            return PlantGainPosterior(mean, variance, 0)
        }
    }

    fun update(
        beforeLogError: Double,
        afterLogError: Double,
        appliedLogFactorDelta: Double,
        observationVariance: Double,
    ): PlantGainPosterior {
        require(observationVariance > 0.0)
        if (!appliedLogFactorDelta.isFinite() || kotlin.math.abs(appliedLogFactorDelta) < 1e-9) return this
        val observedGain = (beforeLogError - afterLogError) / appliedLogFactorDelta
        if (!observedGain.isFinite() || observedGain <= 0.0) return this
        val observedVariance = observationVariance / (appliedLogFactorDelta * appliedLogFactorDelta)
        val priorMean = mean
        val priorVariance = variance
        return if (priorMean == null || priorVariance == null) {
            PlantGainPosterior(observedGain, observedVariance.coerceAtLeast(1e-9), 1)
        } else {
            val priorPrecision = 1.0 / priorVariance
            val observedPrecision = 1.0 / observedVariance.coerceAtLeast(1e-9)
            val posteriorVariance = 1.0 / (priorPrecision + observedPrecision)
            val posteriorMean = posteriorVariance * (priorMean * priorPrecision + observedGain * observedPrecision)
            PlantGainPosterior(posteriorMean, posteriorVariance, informativeUpdates + 1)
        }
    }

    fun toPlantGain(): PlantGain {
        val m = mean
        val v = variance
        if (m == null || v == null || informativeUpdates <= 0 || m <= 0.0) return PlantGain.unknown()
        val halfWidth = 1.96 * sqrt(v)
        val lower = (m - halfWidth).coerceAtLeast(1e-6)
        val upper = m + halfWidth
        return PlantGain.empiricallyBounded(m, lower, upper)
    }
}
