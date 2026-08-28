package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificDecisionAuthorityTest {
    @Test
    fun nativeEvidenceRequiresExplicitEvidenceLevel() {
        val pressure = ScientificAuthorityRegistry.nativeAnchored("MP48_PRESSURE_DIFF", "E4")
        assertEquals(ScientificDecisionAuthority.NATIVE_ANCHORED, pressure.authority)
        assertEquals("E4", pressure.evidenceLevel)
        assertEquals("NATIVE_ANCHORED:MP48_PRESSURE_DIFF:E4", pressure.token())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nativeEvidenceWithoutLevelFailsClosed() {
        ScientificAuthorityEvidence(
            authority = ScientificDecisionAuthority.NATIVE_ANCHORED,
            source = "MP48_PRESSURE_DIFF",
            evidenceLevel = null,
        )
    }

    @Test
    fun comparabilityPolicyNeverPretendsToBeNativeEvidence() {
        val policy = ScientificAuthorityRegistry.referenceSelectorComparability
        assertEquals(ScientificDecisionAuthority.OMEGAS_COMPARABILITY_POLICY, policy.authority)
        assertEquals(null, policy.evidenceLevel)
        assertTrue(policy.token().startsWith("OMEGAS_COMPARABILITY_POLICY:"))
    }
}
