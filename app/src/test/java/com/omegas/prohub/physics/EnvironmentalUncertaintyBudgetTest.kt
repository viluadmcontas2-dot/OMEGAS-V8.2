package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalUncertaintyBudgetTest {
    @Test fun `environmental diagnostic widens uncertainty without forcing infinite collection`() {
        val classification = ResidualMechanismClassifier.classify(
            ResidualEvidence(16, 0.35, 0.40, 0.85, 0.10, true, true, EffectDirection.INCREASE),
        )
        val budget = EnvironmentalUncertaintyBudget.adjust(baseUncertainty = 0.04, classification = classification)
        assertTrue(budget.adjustedUncertainty > 0.04)
        assertEquals("CONDITION_MODEL_OR_MATCH_CONTEXT", budget.nextAction)
        assertFalse(budget.requiresUnboundedCollection)
    }

    @Test fun `supported local mechanism does not inflate environmental uncertainty`() {
        val classification = ResidualMechanismClassifier.classify(
            ResidualEvidence(18, 0.90, 0.30, 0.10, 0.05, true, true, EffectDirection.DECREASE),
        )
        val budget = EnvironmentalUncertaintyBudget.adjust(0.05, classification)
        assertEquals(0.05, budget.adjustedUncertainty, 1e-12)
        assertEquals("PROCEED_WITH_SUPPORTED_MODEL", budget.nextAction)
    }
}
