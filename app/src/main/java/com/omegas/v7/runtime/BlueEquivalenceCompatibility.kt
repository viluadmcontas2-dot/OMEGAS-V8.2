package com.omegas.v7.runtime

import com.omegas.prohub.blue.BlueCausalEngine
import com.omegas.prohub.blue.BlueCausalPolicy

/**
 * Transitional source-compatibility facade only. All equivalence mathematics is
 * delegated to the single [BlueCausalEngine]; this class owns no alternate
 * predictor, confidence model or correction formula.
 */
class V7EquivalenceEngine(
    policy: EquivalencePolicyV7 = EquivalencePolicyV7(),
) {
    private val blue = BlueCausalEngine(
        BlueCausalPolicy(
            rpmMinimumWindow = policy.rpmMinimumWindow,
            rpmPercentWindow = policy.rpmPercentWindow,
            mapWindowBar = policy.mapWindowBar,
            waterWindowC = policy.waterWindowC,
            maximumNormalizedDistance = policy.maximumNormalizedDistance,
            maximumReferenceBursts = policy.maximumNeighbors,
            deadbandMs = policy.deadbandMs,
            deadbandPercent = policy.deadbandPercent,
        ),
    )

    fun reconcile(
        state: V7SessionState,
        nowMs: Long = System.currentTimeMillis(),
    ): List<FuelComparisonV7> = blue.reconcile(state, nowMs)
}
