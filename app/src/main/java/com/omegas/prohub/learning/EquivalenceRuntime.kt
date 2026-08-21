package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Thin runtime seam between window diagnostics/novelty and the bounded RPM×MAP surface.
 *
 * No JSON, history scan, USB access, disk access or UI work is allowed here.
 */
internal class EquivalenceRuntime(
    internal val surface: EquivalenceSurface = EquivalenceSurface(EquivalenceSurface.Config.mp48ReplayCandidate()),
    private val deadbandFraction: Double = DEFAULT_DEADBAND_FRACTION,
    private val empiricalSingleObservationNoiseFraction: Double = EMPIRICAL_SINGLE_OBSERVATION_NOISE_FRACTION,
) {
    init {
        require(deadbandFraction.isFinite() && deadbandFraction >= 0.0)
        require(empiricalSingleObservationNoiseFraction.isFinite() && empiricalSingleObservationNoiseFraction >= 0.0)
    }

    data class ObserveOutcome(
        val scientificWeight: Double,
        val touchedNodes: Int,
        val estimate: EquivalenceEstimate?,
    )

    data class EquivalenceEstimate(
        val referenceMs: Double,
        val cngMs: Double,
        val deltaMs: Double,
        val errorFraction: Double,
        val uncertaintyFraction: Double,
        val usefulMarginFraction: Double,
        val actionable: Boolean,
        val petrolEffectiveSupport: Double,
        val cngEffectiveSupport: Double,
        val materialRevision: Long,
    )

    fun observe(
        lane: FuelLane,
        rpm: Double,
        mapBar: Double,
        petrolTinjMs: Double,
        stability: Double,
        novelty: Double,
        materialRevision: Long,
    ): ObserveOutcome {
        val scientificWeight = scientificWeight(lane, stability, novelty)
        if (scientificWeight <= 0.0) {
            return ObserveOutcome(
                scientificWeight = 0.0,
                touchedNodes = 0,
                estimate = estimate(rpm, mapBar),
            )
        }
        val observed = surface.observe(
            lane = lane,
            rpm = rpm,
            mapBar = mapBar,
            petrolTinjMs = petrolTinjMs,
            weight = scientificWeight,
            materialRevision = materialRevision,
        )
        return ObserveOutcome(
            scientificWeight = observed.acceptedWeight,
            touchedNodes = observed.touchedNodes,
            estimate = estimate(rpm, mapBar),
        )
    }

    fun query(rpm: Double, mapBar: Double): EquivalenceSurface.QueryResult = surface.query(rpm, mapBar)

    fun estimate(rpm: Double, mapBar: Double): EquivalenceEstimate? {
        val query = surface.query(rpm, mapBar)
        val petrol = query.petrol ?: return null
        val cng = query.cng ?: return null
        if (petrol.meanTinjMs <= EPSILON || cng.meanTinjMs <= EPSILON) return null

        val deltaMs = cng.meanTinjMs - petrol.meanTinjMs
        val errorFraction = deltaMs / petrol.meanTinjMs
        val uPetrol = laneMeanUncertaintyFraction(petrol)
        val uCng = laneMeanUncertaintyFraction(cng)
        val uncertainty = sqrt(uPetrol * uPetrol + uCng * uCng)
        val usefulMargin = abs(errorFraction) - uncertainty - deadbandFraction

        return EquivalenceEstimate(
            referenceMs = petrol.meanTinjMs,
            cngMs = cng.meanTinjMs,
            deltaMs = deltaMs,
            errorFraction = errorFraction,
            uncertaintyFraction = uncertainty,
            usefulMarginFraction = usefulMargin,
            actionable = usefulMargin > 0.0,
            petrolEffectiveSupport = petrol.effectiveSupport,
            cngEffectiveSupport = cng.effectiveSupport,
            materialRevision = max(petrol.materialRevision, cng.materialRevision),
        )
    }

    internal fun totalWeight(lane: FuelLane): Double = surface.debugTotalWeight(lane)

    internal fun allocatedScalarCount(): Int = surface.debugAllocatedScalarCount()

    private fun scientificWeight(lane: FuelLane, stability: Double, novelty: Double): Double {
        if (!stability.isFinite() || !novelty.isFinite()) return 0.0
        val stable = stability.coerceIn(0.0, 1.0)
        val novel = novelty.coerceIn(0.0, 1.0)
        if (stable <= 0.0 || novel <= 0.0) return 0.0
        val exponent = when (lane) {
            FuelLane.PETROL_REFERENCE -> PETROL_STABILITY_EXPONENT
            FuelLane.CNG_PETROL_OBSERVED -> CNG_STABILITY_EXPONENT
        }
        return stable.pow(exponent) * novel
    }

    private fun laneMeanUncertaintyFraction(lane: EquivalenceSurface.LaneEstimate): Double {
        val mean = abs(lane.meanTinjMs).coerceAtLeast(EPSILON)
        val ess = lane.effectiveSupport.coerceAtLeast(1.0)
        val spreadFraction = sqrt(lane.varianceMs2.coerceAtLeast(0.0)) / mean
        val spreadOfMean = spreadFraction / sqrt(ess)
        val empiricalNoiseOfMean = empiricalSingleObservationNoiseFraction / sqrt(ess)
        return sqrt(spreadOfMean * spreadOfMean + empiricalNoiseOfMean * empiricalNoiseOfMean)
    }

    companion object {
        /** Approved policy: gasoline reference authority grows more conservatively than CNG. */
        internal const val PETROL_STABILITY_EXPONENT = 2.0
        internal const val CNG_STABILITY_EXPONENT = 1.0

        /** Operational equivalence policy target, not a statistical guarantee. */
        internal const val DEFAULT_DEADBAND_FRACTION = 0.02

        /**
         * Corpus-derived single-observation noise candidate. Fresh 2026-08-21 replay over
         * 3,960 tight consecutive accepted-gasoline pairs produced P90 ≈ 1.875%.
         * Rounded up to 1.9%; divided by sqrt(ESS) for mean uncertainty.
         */
        internal const val EMPIRICAL_SINGLE_OBSERVATION_NOISE_FRACTION = 0.019
        private const val EPSILON = 1e-12
    }
}
