package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidualMechanismClassifierTest {
    @Test
    fun `localized repeatable residual selects map local candidate`() {
        val result = ResidualMechanismClassifier.classify(
            ResidualEvidence(
                comparableSamples = 12,
                localizedRepeatability = 0.88,
                broadCoherence = 0.22,
                environmentalCorrelation = 0.10,
                contradiction = 0.08,
                mapMechanismSupported = true,
                curveMechanismSupported = true,
                direction = EffectDirection.INCREASE,
            ),
        )
        assertEquals(CorrectionMechanism.MAP_LOCAL, result.decision.mechanism)
        assertEquals("LOCALIZED_REPEATABLE", result.reasonCode)
        assertTrue(result.decision.evidencePath.isNotEmpty())
    }

    @Test
    fun `broad residual needs curve mechanism support`() {
        val supported = ResidualMechanismClassifier.classify(
            ResidualEvidence(20, 0.20, 0.91, 0.12, 0.05, true, true, EffectDirection.DECREASE),
        )
        val unsupported = ResidualMechanismClassifier.classify(
            ResidualEvidence(20, 0.20, 0.91, 0.12, 0.05, true, false, EffectDirection.DECREASE),
        )
        assertEquals(CorrectionMechanism.CURVE_MUL_ACT, supported.decision.mechanism)
        assertEquals(CorrectionMechanism.UNKNOWN, unsupported.decision.mechanism)
        assertNull(unsupported.decision.target)
    }

    @Test
    fun `environmental confounder never defaults to k correction`() {
        val result = ResidualMechanismClassifier.classify(
            ResidualEvidence(14, 0.55, 0.60, 0.86, 0.12, true, true, EffectDirection.INCREASE),
        )
        assertEquals(CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC, result.decision.mechanism)
        assertNull(result.decision.target)
        assertTrue(result.uncertaintyInflation > 0.0)
    }

    @Test
    fun `zero comparable support always abstains regardless of scores`() {
        val result = ResidualMechanismClassifier.classify(
            ResidualEvidence(0, 0.92, 0.08, 0.02, 0.01, true, true, EffectDirection.INCREASE),
        )
        assertEquals(CorrectionMechanism.UNKNOWN, result.decision.mechanism)
        assertEquals("NO_COMPARABLE_SUPPORT", result.reasonCode)
        assertNull(result.decision.target)
        assertTrue(result.nextEvidence.isNotBlank())
    }

    @Test
    fun `insufficient or contradictory evidence abstains with next evidence`() {
        val result = ResidualMechanismClassifier.classify(
            ResidualEvidence(2, 0.92, 0.80, 0.10, 0.78, true, true, EffectDirection.INCREASE),
        )
        assertEquals(CorrectionMechanism.UNKNOWN, result.decision.mechanism)
        assertNull(result.decision.target)
        assertTrue(result.nextEvidence.isNotBlank())
    }

    @Test
    fun `allocator never double counts same residual into map and curve`() {
        val allocation = ExclusiveActuatorAllocator.allocate(
            mechanism = CorrectionMechanism.MAP_LOCAL,
            idealTarget = IdealTarget(1.12, MagnitudeAuthority.EMPIRICALLY_BOUNDED),
        )
        assertEquals(1.0, allocation.mapShare, 0.0)
        assertEquals(0.0, allocation.curveShare, 0.0)
        assertTrue(allocation.mapShare + allocation.curveShare <= 1.0)
    }
}
