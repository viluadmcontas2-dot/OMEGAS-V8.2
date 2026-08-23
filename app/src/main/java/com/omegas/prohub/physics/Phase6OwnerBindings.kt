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
 * Adds Phase 06 authority metadata to the Advisor projection.
 *
 * The real Advisor statistics are first projected into typed causal evidence.
 * Legacy numerical deltas remain StepPolicy outputs and never become ideal
 * targets here. Missing primary support or incomplete causal ordering stays
 * UNKNOWN; optional environmental metadata never gates RPM+MAP+Tinj physics.
 */
fun AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice: JSONObject): JSONObject {
    val output = JSONObject(advice.toString())
    PhysicsResidualEvidenceProducer.populate(output)
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
            .put("stepAuthority", MagnitudeAuthority.POLICY_ONLY.name)
            .put("magnitudeRole", "STEP_POLICY_BASELINE")
            .put("correctionMechanism", CorrectionMechanism.UNKNOWN.name)
            .put("mechanismCandidateLane", candidateLane.name)
            .put("idealTarget", false)
            .put("expectedEffectDirection", legacyDirection(item).name)

        val typedEvidence = item.optJSONObject("physicsResidualEvidence")
            ?.toResidualEvidenceOrNull()
            ?: return@repeat
        val classification = ResidualMechanismClassifier.classify(typedEvidence)
        val classifiedMechanism = classification.decision.mechanism
        val exportedMechanism = when (classifiedMechanism) {
            candidateLane,
            CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
            CorrectionMechanism.NO_ACTION,
            CorrectionMechanism.UNKNOWN -> classifiedMechanism
            else -> CorrectionMechanism.UNKNOWN
        }
        val reasonCode = if (exportedMechanism == classifiedMechanism) {
            classification.reasonCode
        } else {
            "CANDIDATE_LANE_MISMATCH:${classification.reasonCode}"
        }
        val effect = classification.decision.effect

        item.put("correctionMechanism", exportedMechanism.name)
            .put("mechanismReasonCode", reasonCode)
            .put("mechanismEvidencePath", JSONArray(classification.decision.evidencePath))
            .put("mechanismUncertaintyInflation", classification.uncertaintyInflation)
            .put("mechanismNextEvidence", classification.nextEvidence)
            .put("expectedEffectDirection", effect.direction.name)
            .put("expectedEffectAuthority", effect.authority.name)
            .put("expectedEffectAssumptions", JSONArray(effect.assumptions))
            .put("expectedEffectFalsifier", effect.falsifier)
        effect.lowerBound?.let { item.put("expectedEffectLowerBound", it) }
        effect.upperBound?.let { item.put("expectedEffectUpperBound", it) }
    }
}

private fun legacyDirection(item: JSONObject): EffectDirection = when (item.optString("direction")) {
    "INCREASE_CNG_DELIVERY" -> EffectDirection.INCREASE
    "DECREASE_CNG_DELIVERY" -> EffectDirection.DECREASE
    else -> EffectDirection.UNKNOWN
}

private fun JSONObject.toResidualEvidenceOrNull(): ResidualEvidence? = runCatching {
    ResidualEvidence(
        comparableSamples = getInt("comparableSamples"),
        localizedRepeatability = getDouble("localizedRepeatability"),
        broadCoherence = getDouble("broadCoherence"),
        environmentalCorrelation = getDouble("environmentalCorrelation"),
        contradiction = getDouble("contradiction"),
        mapMechanismSupported = getBoolean("mapMechanismSupported"),
        curveMechanismSupported = getBoolean("curveMechanismSupported"),
        direction = EffectDirection.valueOf(getString("direction")),
        localizedStructureSupported = getBoolean("localizedStructureSupported"),
        broadStructureSupported = getBoolean("broadStructureSupported"),
        environmentalContextVerified = getBoolean("environmentalContextVerified"),
        environmentalExplanationSupported = getBoolean("environmentalExplanationSupported"),
        contradictionObserved = getBoolean("contradictionObserved"),
        localResidualCleared = getBoolean("localResidualCleared"),
    )
}.getOrNull()
