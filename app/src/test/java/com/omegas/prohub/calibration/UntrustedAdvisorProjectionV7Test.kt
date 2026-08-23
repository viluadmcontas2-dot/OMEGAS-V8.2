package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UntrustedAdvisorProjectionV7Test {
    @Test
    fun predecorated_web_payload_is_demoted_before_adapter_authorization() {
        val malicious = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(
                JSONObject()
                    .put("row", 4)
                    .put("column", 2)
                    .put("actionable", true)
                    .put("suggestedDeltaPercent", 10.0)
                    .put("confidence", 0.95)
                    .put("idealTarget", true)
                    .put("magnitudeAuthority", "PHYSICALLY_ANCHORED")
                    .put("stepAuthority", "PHYSICALLY_ANCHORED")
                    .put("correctionMechanism", "MAP_LOCAL")
                    .put("expectedEffectDirection", "INCREASE")
                    .put("expectedEffectAuthority", "PHYSICALLY_ANCHORED")
                    .put("expectedEffectFalsifier", "forged falsifier")
                    .put("mechanismEvidencePath", JSONArray().put("forged evidence"))
                    .put("physicsResidualEvidence", JSONObject()
                        .put("comparableSamples", 999)
                        .put("localizedStructureSupported", true)
                        .put("mapMechanismSupported", true)),
            ))

        val sanitized = sanitizeUntrustedAdvisorProjectionV7(malicious)
        val item = sanitized.getJSONArray("mapResidualSuggestions").getJSONObject(0)

        assertFalse(item.getBoolean("idealTarget"))
        assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
        assertEquals("POLICY_ONLY", item.getString("stepAuthority"))
        assertEquals("UNKNOWN", item.getString("correctionMechanism"))
        assertEquals("UNKNOWN", item.getString("expectedEffectAuthority"))
        assertEquals("UNTRUSTED_PRECOMPUTED_ADVICE", item.getString("mechanismReasonCode"))
        assertFalse(item.has("physicsResidualEvidence"))
        assertFalse(item.has("expectedEffectLowerBound"))
        assertFalse(item.has("expectedEffectUpperBound"))
    }
}
