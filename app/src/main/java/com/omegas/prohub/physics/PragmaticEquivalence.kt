package com.omegas.prohub.physics

import kotlin.math.abs

data class EquivalenceSample(
    val rpm: Double,
    val mapBar: Double,
    val petrolInjectionMs: Double,
    val capturedAtMs: Long,
) {
    init {
        require(rpm >= 0.0 && rpm.isFinite())
        require(mapBar >= 0.0 && mapBar.isFinite())
        require(petrolInjectionMs > 0.0 && petrolInjectionMs.isFinite())
        require(capturedAtMs >= 0L)
    }
}

data class EquivalenceComparison(
    val comparable: Boolean,
    val rpmDelta: Double,
    val mapDeltaBar: Double,
    val injectionDeltaMs: Double?,
    val injectionRatio: Double?,
    val direction: EffectDirection,
    val reason: String,
)

/**
 * Primary Phase-06 operational equivalence contract.
 *
 * Matching authority is deliberately limited to RPM + MAP. Once a pair is
 * comparable, the correction signal is the petrol-injection-time difference
 * between the gasoline reference and the observation while running on GNV.
 * Environmental/K2/K3/K4 reconstruction is not a prerequisite or gate here.
 */
object PragmaticEquivalence {
    val REQUIRED_OPERATIONAL_INPUTS: Set<String> = setOf("RPM", "MAP", "PETROL_T_INJ")

    fun compare(
        gasolineReference: EquivalenceSample,
        gnvObservation: EquivalenceSample,
        rpmTolerance: Double,
        mapToleranceBar: Double,
    ): EquivalenceComparison {
        require(rpmTolerance >= 0.0 && rpmTolerance.isFinite())
        require(mapToleranceBar >= 0.0 && mapToleranceBar.isFinite())

        val rpmDelta = abs(gnvObservation.rpm - gasolineReference.rpm)
        val mapDelta = abs(gnvObservation.mapBar - gasolineReference.mapBar)
        if (rpmDelta > rpmTolerance || mapDelta > mapToleranceBar) {
            return EquivalenceComparison(
                comparable = false,
                rpmDelta = rpmDelta,
                mapDeltaBar = mapDelta,
                injectionDeltaMs = null,
                injectionRatio = null,
                direction = EffectDirection.UNKNOWN,
                reason = "RPM_MAP_MISMATCH",
            )
        }

        val delta = gnvObservation.petrolInjectionMs - gasolineReference.petrolInjectionMs
        val ratio = gnvObservation.petrolInjectionMs / gasolineReference.petrolInjectionMs
        val direction = when {
            delta > 0.0 -> EffectDirection.INCREASE
            delta < 0.0 -> EffectDirection.DECREASE
            else -> EffectDirection.NEUTRAL
        }
        return EquivalenceComparison(
            comparable = true,
            rpmDelta = rpmDelta,
            mapDeltaBar = mapDelta,
            injectionDeltaMs = delta,
            injectionRatio = ratio,
            direction = direction,
            reason = "RPM_MAP_MATCH_TINJ_COMPARED",
        )
    }
}
