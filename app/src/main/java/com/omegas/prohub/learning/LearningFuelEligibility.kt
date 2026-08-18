package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.telemetry.RuntimeFreshness

enum class LearningEligibilityMode {
    PETROL_REFERENCE,
    CNG_COMPARISON,
    INELIGIBLE,
}

data class LearningEligibility(
    val eligible: Boolean,
    val mode: LearningEligibilityMode,
    val reasonCode: String,
)

/** Única matriz semântica de combustível/freshness/plausibility para Learning. */
object LearningFuelEligibility {
    fun evaluate(
        fuel: Mp48Fuel,
        freshness: RuntimeFreshness,
        plausible: Boolean,
    ): LearningEligibility {
        if (freshness != RuntimeFreshness.CURRENT) {
            return LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, when (freshness) {
                RuntimeFreshness.STALE -> "TELEMETRY_STALE"
                RuntimeFreshness.UNKNOWN -> "TELEMETRY_FRESHNESS_UNKNOWN"
                RuntimeFreshness.CURRENT -> error("unreachable")
            })
        }
        if (!plausible) {
            return LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, "TELEMETRY_IMPLAUSIBLE")
        }
        return when (fuel) {
            Mp48Fuel.PETROL -> LearningEligibility(true, LearningEligibilityMode.PETROL_REFERENCE, "PETROL_ELIGIBLE")
            Mp48Fuel.CNG -> LearningEligibility(true, LearningEligibilityMode.CNG_COMPARISON, "CNG_ELIGIBLE")
            Mp48Fuel.TRANSITION -> LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, "FUEL_TRANSITION")
            Mp48Fuel.CUTOFF -> LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, "FUEL_CUTOFF")
            Mp48Fuel.ENGINE_OFF -> LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, "ENGINE_OFF")
            Mp48Fuel.UNKNOWN -> LearningEligibility(false, LearningEligibilityMode.INELIGIBLE, "FUEL_UNKNOWN")
        }
    }
}
