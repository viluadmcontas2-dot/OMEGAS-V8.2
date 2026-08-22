package com.omegas.prohub.learning

/**
 * Decides whether a previously computed local RPM×MAP equivalence may remain visible
 * while the next analyzer decision is being formed.
 *
 * This does not alter or erase bounded scientific memory. It only prevents cached
 * local action from being presented while the engine/fuel/continuity state is
 * physically incompatible with primary Tinj equivalence.
 */
internal object CurrentEquivalenceStatusPolicy {
    fun allowsCachedEstimate(decision: SampleDecision?): Boolean {
        decision ?: return false
        if (decision.classification == SampleClassification.INVALID) return false
        if (decision.continuityLost || decision.reasonCode == "REAL_TELEMETRY_LOSS") return false
        return when (decision.state) {
            "INVALID",
            "ENGINE_OFF",
            "CUTOFF",
            "FUEL_TRANSITION",
            "FUEL_VERIFYING",
            "FUEL_UNKNOWN",
            "TELEMETRY_GAP",
            -> false
            else -> true
        }
    }
}
