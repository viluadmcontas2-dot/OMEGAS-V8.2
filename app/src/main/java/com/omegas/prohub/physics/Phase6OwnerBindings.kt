package com.omegas.prohub.physics

import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import com.omegas.prohub.learning.ContinuousLearningMath
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly binds the existing educational interpolation to LOCAL_MODEL. */
fun ContinuousLearningMath.physicsModelAuthority(): PhysicsModelAuthority =
    PhysicsModelContract.BILINEAR_AUTHORITY

/**
 * Exposes the legacy 45–90% correction fraction as operational StepPolicy
 * metadata. It is intentionally not an ideal target and not an ECU plant gain.
 */
fun AssistedCalibrationAdvisor.correctionPolicyMetadata(): JSONObject = JSONObject()
    .put("minimumFraction", 0.45)
    .put("maximumFraction", 0.90)
    .put("magnitudeAuthority", MagnitudeAuthority.POLICY_ONLY.name)
    .put("idealTarget", false)
    .put("role", "STEP_POLICY_BASELINE")

/**
 * Adds Phase 06 authority metadata to the legacy Advisor projection without
 * promoting a lane to a causal mechanism. Legacy numerical deltas remain
 * StepPolicy outputs. `mechanismCandidateLane` is only routing provenance;
 * `correctionMechanism` stays UNKNOWN until ResidualMechanismClassifier emits
 * an evidence-backed decision.
 */
fun AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice: JSONObject): JSONObject {
    val output = JSONObject(advice.toString())
    output.put("physicsPolicy", correctionPolicyMetadata())
    decorateLegacyLane(
        output.optJSONArray("kFactorSuggestions") ?: JSONArray(),
        candidateLane = CorrectionMechanism.CURVE_MUL_ACT,
    )
    decorateLegacyLane(
        output.optJSONArray("mapResidualSuggestions") ?: JSONArray(),
        candidateLane = CorrectionMechanism.MAP_LOCAL,
    )
    return output
}

private fun decorateLegacyLane(items: JSONArray, candidateLane: CorrectionMechanism) {
    repeat(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@repeat
        item.put("magnitudeAuthority", MagnitudeAuthority.POLICY_ONLY.name)
            .put("magnitudeRole", "STEP_POLICY_BASELINE")
            .put("correctionMechanism", CorrectionMechanism.UNKNOWN.name)
            .put("mechanismCandidateLane", candidateLane.name)
            .put("idealTarget", false)
            .put("expectedEffectDirection", when (item.optString("direction")) {
                "INCREASE_CNG_DELIVERY" -> EffectDirection.INCREASE.name
                "DECREASE_CNG_DELIVERY" -> EffectDirection.DECREASE.name
                else -> EffectDirection.UNKNOWN.name
            })
    }
}
