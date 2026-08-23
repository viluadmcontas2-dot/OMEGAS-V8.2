package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalUncertaintyBudgetTest {
    @Test fun `environmental diagnostic widens uncertainty without forcing infinite collection`() {
        val classification = ResidualMechanismClassifier.classify(
            ResidualEvidence(
                comparableSamples = 16,
                localizedRepeatability = 0.35,
                broadCoherence = 0.40,
                environmentalCorrelation = 0.85,
                contradiction = 0.10,
                mapMechanismSupported = false,
                curveMechanismSupported = false,
                direction = EffectDirection.INCREASE,
                localizedStructureSupported = false,
                broadStructureSupported = false,
                environmentalContextVerified = true,
                environmentalExplanationSupported = true,
                contradictionObserved = false,
                localResidualCleared = true,
            ),
        )
        val budget = EnvironmentalUncertaintyBudget.adjust(baseUncertainty = 0.04, classification = classification)
        assertTrue(budget.adjustedUncertainty > 0.04)
        assertEquals("CONDITION_MODEL_OR_MATCH_CONTEXT", budget.nextAction)
        assertFalse(budget.requiresUnboundedCollection)
    }

    @Test fun `supported local mechanism does not inflate environmental uncertainty`() {
        val classification = ResidualMechanismClassifier.classify(
            ResidualEvidence(
                comparableSamples = 18,
                localizedRepeatability = 0.90,
                broadCoherence = 0.30,
                environmentalCorrelation = 0.10,
                contradiction = 0.05,
                mapMechanismSupported = true,
                curveMechanismSupported = true,
                direction = EffectDirection.DECREASE,
                localizedStructureSupported = true,
                broadStructureSupported = false,
                environmentalContextVerified = false,
                environmentalExplanationSupported = false,
                contradictionObserved = false,
                localResidualCleared = false,
            ),
        )
        val budget = EnvironmentalUncertaintyBudget.adjust(0.05, classification)
        assertEquals(0.05, budget.adjustedUncertainty, 1e-12)
        assertEquals("PROCEED_WITH_SUPPORTED_MODEL", budget.nextAction)
    }
}
