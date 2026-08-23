package com.omegas.prohub.calibration

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compatibility/UI payloads are presentation inputs, never Physics authority.
 *
 * Preserve their statistical/delta fields so the UI may keep showing historical
 * Advisor context, but strip every field capable of authorizing a concrete target.
 * Only a fresh native Advisor -> Physics computation may promote authority again.
 */
internal fun sanitizeUntrustedAdvisorIngressV7(payload: JSONObject): JSONObject {
    val precomputed = payload.optJSONObject("assistedCalibration")
        ?: payload.optJSONObject("assisted_calibration")
        ?: payload
    return sanitizeUntrustedAdvisorProjectionV7(precomputed)
}

/**
 * A WebView may request a learning import but never supplies the scientific
 * observations. The service-owned native export is the only admissible source;
 * cached Advisor/Physics projections are stripped so authority is recomputed from
 * that native evidence inside V7CalibrationCoordinator.resolveAdvice.
 */
internal fun selectTrustedLearningSnapshotV7(
    @Suppress("UNUSED_PARAMETER") untrustedUiPayload: String,
    nativeSnapshot: JSONObject,
): JSONObject = sanitizeUntrustedLearningSnapshotV7(nativeSnapshot)

/**
 * Strip every derived Advisor/Physics projection before a fresh native recompute.
 * Raw regions/comparisons are preserved only because the caller has already bound
 * this object to the service-owned native learning export.
 */
internal fun sanitizeUntrustedLearningSnapshotV7(payload: JSONObject): JSONObject =
    JSONObject(payload.toString()).apply {
        remove("assistedCalibration")
        remove("assisted_calibration")
        remove("kFactorSuggestions")
        remove("mapResidualSuggestions")
        remove("mapCorrectionRegions")
        remove("physicsPolicy")
        remove("physicsIngress")
        remove("physicsAuthoritative")
        remove("primaryAuthority")
        remove("inputSource")
        remove("environmentGates")
    }

internal fun sanitizeUntrustedAdvisorProjectionV7(advice: JSONObject): JSONObject {
    val output = JSONObject(advice.toString())
    sanitizeUntrustedLane(
        output.optJSONArray("kFactorSuggestions") ?: JSONArray(),
        candidateLane = CorrectionMechanism.CURVE_MUL_ACT,
    )
    sanitizeUntrustedLane(
        output.optJSONArray("mapResidualSuggestions") ?: JSONArray(),
        candidateLane = CorrectionMechanism.MAP_LOCAL,
    )
    return output
        .put("physicsIngress", "UNTRUSTED_PRECOMPUTED_ADVICE")
        .put("physicsAuthoritative", false)
}

private fun sanitizeUntrustedLane(items: JSONArray, candidateLane: CorrectionMechanism) {
    repeat(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@repeat
        item.put("magnitudeAuthority", MagnitudeAuthority.POLICY_ONLY.name)
            .put("stepAuthority", MagnitudeAuthority.POLICY_ONLY.name)
            .put("magnitudeRole", "STEP_POLICY_BASELINE")
            .put("idealTarget", false)
            .put("correctionMechanism", CorrectionMechanism.UNKNOWN.name)
            .put("mechanismCandidateLane", candidateLane.name)
            .put("mechanismReasonCode", "UNTRUSTED_PRECOMPUTED_ADVICE")
            .put("expectedEffectDirection", EffectDirection.UNKNOWN.name)
            .put("expectedEffectAuthority", MagnitudeAuthority.UNKNOWN.name)
            .put("mechanismEvidencePath", JSONArray())

        item.remove("physicsResidualEvidence")
        item.remove("expectedEffectLowerBound")
        item.remove("expectedEffectUpperBound")
        item.remove("expectedEffectAssumptions")
        item.remove("expectedEffectFalsifier")
        item.remove("mechanismUncertaintyInflation")
        item.remove("mechanismNextEvidence")
    }
}
