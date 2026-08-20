package com.omegas.prohub.physics

import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import com.omegas.prohub.learning.ContinuousLearningMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6BindingIntegrationTest {
    @Test fun adaptiveScientificAuthoritiesStayTypedAndCannotDoubleCountSameEvidence() {
        val inputs = listOf(
            PhysicsScientificInput(ScientificAuthority.OEM_NATIVE, "frame-42", 1.0),
            PhysicsScientificInput(ScientificAuthority.CLASSIC_ASSISTED, "frame-42", 0.8),
            PhysicsScientificInput(ScientificAuthority.ADAPTIVE_SHADOW, "frame-77", 0.6),
        )
        val accepted = PhysicsScientificInput.deduplicateByPhysicalEvidence(inputs)
        assertEquals(2, accepted.size)
        assertTrue(accepted.any { it.authority == ScientificAuthority.OEM_NATIVE })
        assertTrue(accepted.any { it.authority == ScientificAuthority.ADAPTIVE_SHADOW })
        assertFalse(accepted.any { it.authority == ScientificAuthority.CLASSIC_ASSISTED && it.physicalEvidenceId == "frame-42" })
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
}
