package com.omegas.v7.runtime

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority

/**
 * Durable Phase 06 authority carried by a V7 suggestion.
 *
 * This is metadata only. It never creates writer authority and never upgrades
 * UNKNOWN/POLICY_ONLY into a concrete target. Advisor-origin suggestions are
 * executable only when the persisted metadata still proves the exact mechanism,
 * expected effect and a physical/empirical ideal target.
 */
data class PhysicsSuggestionMetadataV7(
    val magnitudeAuthority: MagnitudeAuthority = MagnitudeAuthority.UNKNOWN,
    val stepAuthority: MagnitudeAuthority = MagnitudeAuthority.UNKNOWN,
    val correctionMechanism: CorrectionMechanism = CorrectionMechanism.UNKNOWN,
    val effectDirection: EffectDirection = EffectDirection.UNKNOWN,
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    val assumptions: List<String> = emptyList(),
    val falsifier: String = "",
    val evidencePath: List<String> = emptyList(),
    val idealTarget: Boolean = false,
) {
    init {
        require(lowerBound == null || lowerBound.isFinite())
        require(upperBound == null || upperBound.isFinite())
        require(lowerBound == null || upperBound == null || lowerBound <= upperBound)
    }

    fun authorizes(target: SuggestionTargetV7): Boolean {
        if (!idealTarget) return false
        if (magnitudeAuthority !in setOf(
                MagnitudeAuthority.PHYSICALLY_ANCHORED,
                MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            )
        ) return false
        if (effectDirection == EffectDirection.UNKNOWN) return false
        if (falsifier.isBlank()) return false
        if (evidencePath.isEmpty()) return false
        return when (target) {
            SuggestionTargetV7.MAP_K -> correctionMechanism == CorrectionMechanism.MAP_LOCAL
            SuggestionTargetV7.CURVE_K -> correctionMechanism == CorrectionMechanism.CURVE_MUL_ACT
        }
    }
}
