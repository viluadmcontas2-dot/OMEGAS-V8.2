package com.omegas.prohub.physics

import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import com.omegas.prohub.learning.ContinuousLearningMath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6BindingIntegrationTest {
    @Test fun adaptiveScientificAuthoritiesStayTypedAndCannotDoubleCountSameEvidence() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(
                    ScientificAuthority.OEM_NATIVE,
                    ScientificEvidenceRole.OBSERVATION,
                    "native-frame-42",
                    "frame-42",
                    0.8,
                    "native",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "classic-frame-42",
                    "frame-42",
                    0.8,
                    "classic",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.ADAPTIVE_SHADOW,
                    ScientificEvidenceRole.OBSERVATION,
                    "adaptive-frame-77",
                    "frame-77",
                    0.6,
                    "adaptive-observation",
                ),
            ),
        )

        assertTrue(resolution.conflicts.isEmpty())
        assertEquals(2, resolution.accepted.size)
        val shared = resolution.accepted.single { it.physicalEvidenceId == "frame-42" }
        assertEquals(
            setOf(ScientificAuthority.OEM_NATIVE, ScientificAuthority.CLASSIC_ASSISTED),
            shared.authorities,
        )
        assertEquals(0.8, shared.effectiveWeight, 1e-12)
        assertTrue(
            resolution.accepted.any {
                it.physicalEvidenceId == "frame-77" &&
                    it.authorities == setOf(ScientificAuthority.ADAPTIVE_SHADOW)
            },
        )
    }

    @Test fun continuousLearningBilinearProjectionDeclaresLocalModelAuthority() {
        assertEquals(PhysicsModelAuthority.LOCAL_MODEL, ContinuousLearningMath.physicsModelAuthority())
    }

    @Test fun advisorDeclaresLegacyCorrectionFractionAsPolicyOnly() {
        val policy = AssistedCalibrationAdvisor.correctionPolicyMetadata()
        assertEquals("POLICY_ONLY", policy.getString("magnitudeAuthority"))
        assertEquals(0.45, policy.getDouble("minimumFraction"), 1e-12)
        assertEquals(0.90, policy.getDouble("maximumFraction"), 1e-12)
        assertFalse(policy.getBoolean("idealTarget"))
    }

    @Test fun advisorProjectionNeverPromotesLaneToCausalMechanism() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray().put(JSONObject().put("direction", "INCREASE_CNG_DELIVERY")))
            .put("mapResidualSuggestions", JSONArray().put(JSONObject().put("direction", "DECREASE_CNG_DELIVERY")))
        val decorated = AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice)
        val curve = decorated.getJSONArray("kFactorSuggestions").getJSONObject(0)
        val map = decorated.getJSONArray("mapResidualSuggestions").getJSONObject(0)
        assertEquals("POLICY_ONLY", curve.getString("magnitudeAuthority"))
        assertEquals("STEP_POLICY_BASELINE", curve.getString("magnitudeRole"))
        assertEquals("UNKNOWN", curve.getString("correctionMechanism"))
        assertEquals("CURVE_MUL_ACT", curve.getString("mechanismCandidateLane"))
        assertFalse(curve.getBoolean("idealTarget"))
        assertEquals("UNKNOWN", map.getString("correctionMechanism"))
        assertEquals("MAP_LOCAL", map.getString("mechanismCandidateLane"))
        assertFalse(map.getBoolean("idealTarget"))
    }
}
