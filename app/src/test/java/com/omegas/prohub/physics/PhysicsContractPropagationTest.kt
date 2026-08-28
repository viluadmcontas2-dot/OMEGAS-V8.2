package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsContractPropagationTest {
    @Test
    fun `policy step stays separate from ideal target across suggestion draft and ui contracts`() {
        val decision = CorrectionDecision(
            mechanism = CorrectionMechanism.MAP_LOCAL,
            effect = ExpectedEffect(
                EffectDirection.INCREASE,
                1.08,
                1.12,
                listOf("gain empirically bounded"),
                MagnitudeAuthority.EMPIRICALLY_BOUNDED,
                "residual fails to improve",
            ),
            target = IdealTarget(1.10, MagnitudeAuthority.EMPIRICALLY_BOUNDED),
            evidencePath = listOf("localized residual", "gain posterior"),
        )
        val step = LegacyAdvisorStepPolicy().selectStep(
            currentFactor = 1.0,
            target = requireNotNull(decision.target),
            uncertainty = 0.5,
        )
        val suggestion = PhysicsContractPropagation.toSuggestion(decision, step)
        val draft = PhysicsContractPropagation.toDraft(suggestion)
        val ui = PhysicsContractPropagation.toUi(draft)

        assertEquals(1.10, suggestion.idealTargetFactor!!, 0.0)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, suggestion.idealTargetAuthority)
        assertEquals(MagnitudeAuthority.POLICY_ONLY, suggestion.stepAuthority)
        assertTrue(suggestion.appliedStepFactor!! < suggestion.idealTargetFactor!!)
        assertEquals(suggestion, draft.suggestion)
        assertEquals("EMPIRICALLY_BOUNDED", ui.magnitudeAuthority)
        assertEquals("POLICY_ONLY", ui.stepAuthority)
        assertEquals("EMPIRICALLY_BOUNDED", ui.effectAuthority)
        assertFalse(ui.appliedStepIsIdealTarget)
    }

    @Test
    fun `unknown magnitude can preserve direction without numeric target`() {
        val decision = CorrectionDecision(
            mechanism = CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
            effect = ExpectedEffect(
                EffectDirection.DECREASE,
                null,
                null,
                listOf("environment dominates"),
                MagnitudeAuthority.UNKNOWN,
                "matched context removes correlation",
            ),
            target = null,
            evidencePath = listOf("environmental correlation"),
        )
        val suggestion = PhysicsContractPropagation.toSuggestion(decision, null)
        val ui = PhysicsContractPropagation.toUi(PhysicsContractPropagation.toDraft(suggestion))
        assertNull(suggestion.idealTargetFactor)
        assertEquals(EffectDirection.DECREASE, suggestion.expectedEffect.direction)
        assertEquals(MagnitudeAuthority.UNKNOWN, suggestion.idealTargetAuthority)
        assertEquals("UNKNOWN", ui.effectAuthority)
        assertEquals("DECREASE", ui.direction)
    }
}
