package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidualMechanismClassifierTest {
    @Test
    fun `localized supported structure selects map local candidate`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(local = true, mapSupported = true),
        )
        assertEquals(CorrectionMechanism.MAP_LOCAL, result.decision.mechanism)
        assertEquals("LOCALIZED_REPEATABLE", result.reasonCode)
        assertTrue(result.decision.evidencePath.isNotEmpty())
    }

    @Test
    fun `broad residual requires curve support and cleared local residual`() {
        val supported = ResidualMechanismClassifier.classify(
            evidence(broad = true, curveSupported = true, localResidualCleared = true),
        )
        val unsupported = ResidualMechanismClassifier.classify(
            evidence(broad = true, curveSupported = false, localResidualCleared = true),
        )
        val uncleared = ResidualMechanismClassifier.classify(
            evidence(broad = true, curveSupported = true, localResidualCleared = false),
        )
        assertEquals(CorrectionMechanism.CURVE_MUL_ACT, supported.decision.mechanism)
        assertEquals(CorrectionMechanism.UNKNOWN, unsupported.decision.mechanism)
        assertEquals("BROAD_WITHOUT_CURVE_MECHANISM_SUPPORT", unsupported.reasonCode)
        assertEquals(CorrectionMechanism.UNKNOWN, uncleared.decision.mechanism)
        assertEquals("LOCAL_RESIDUAL_NOT_CLEARED", uncleared.reasonCode)
    }

    @Test
    fun `environmental confounder remains diagnostic when no primary mechanism is supported`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(environmental = true),
        )
        assertEquals(CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC, result.decision.mechanism)
        assertNull(result.decision.target)
        assertTrue(result.uncertaintyInflation > 0.0)
    }

    @Test
    fun `missing optional environmental context does not block supported primary mechanism`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(local = true, environmentalContextVerified = false),
        )
        assertEquals(CorrectionMechanism.MAP_LOCAL, result.decision.mechanism)
        assertEquals("LOCALIZED_REPEATABLE", result.reasonCode)
        assertNull(result.decision.target)
    }

    @Test
    fun `primary mechanism wins over diagnostic environmental signal`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(local = true, environmental = true),
        )
        assertEquals(CorrectionMechanism.MAP_LOCAL, result.decision.mechanism)
        assertEquals("LOCALIZED_REPEATABLE", result.reasonCode)
        assertNull(result.decision.target)
    }

    @Test
    fun `zero comparable support always abstains regardless of structure`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(samples = 0, local = true, broad = true),
        )
        assertEquals(CorrectionMechanism.UNKNOWN, result.decision.mechanism)
        assertEquals("NO_COMPARABLE_SUPPORT", result.reasonCode)
        assertNull(result.decision.target)
        assertTrue(result.nextEvidence.isNotBlank())
    }

    @Test
    fun `explicit contradiction abstains with next evidence`() {
        val result = ResidualMechanismClassifier.classify(
            evidence(local = true, contradiction = true),
        )
        assertEquals(CorrectionMechanism.UNKNOWN, result.decision.mechanism)
        assertEquals("CONTRADICTORY_EVIDENCE", result.reasonCode)
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

    private fun evidence(
        samples: Int = 4,
        local: Boolean = false,
        broad: Boolean = false,
        environmental: Boolean = false,
        environmentalContextVerified: Boolean = true,
        contradiction: Boolean = false,
        localResidualCleared: Boolean = true,
        mapSupported: Boolean = true,
        curveSupported: Boolean = true,
    ) = ResidualEvidence(
        comparableSamples = samples,
        localizedRepeatability = if (local) 1.0 else 0.0,
        broadCoherence = if (broad) 1.0 else 0.0,
        environmentalCorrelation = if (environmental) 1.0 else 0.0,
        contradiction = if (contradiction) 1.0 else 0.0,
        mapMechanismSupported = mapSupported,
        curveMechanismSupported = curveSupported,
        direction = EffectDirection.INCREASE,
        localizedStructureSupported = local,
        broadStructureSupported = broad,
        environmentalContextVerified = environmentalContextVerified,
        environmentalExplanationSupported = environmental,
        contradictionObserved = contradiction,
        localResidualCleared = localResidualCleared,
    )
}
