package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorHumanStateProjectionTest {
    private val range = PredictorTargetRange(118.0, 126.0, "CALIBRATED_INTERVAL")

    @Test
    fun `same number is worded differently by authority`() {
        val anchored = project(MagnitudeAuthority.PHYSICALLY_ANCHORED, PredictorHumanRiskState.REVIEW_ONLY)
        val policy = project(MagnitudeAuthority.POLICY_ONLY, PredictorHumanRiskState.REVIEW_ONLY)
        val unknown = project(MagnitudeAuthority.UNKNOWN, PredictorHumanRiskState.REVIEW_ONLY)

        assertEquals(122.0, anchored.targetEstimateK!!, 0.0)
        assertEquals(122.0, policy.targetEstimateK!!, 0.0)
        assertEquals(122.0, unknown.targetEstimateK!!, 0.0)
        assertTrue(anchored.targetLabel.contains("ALVO"))
        assertTrue(policy.targetLabel.contains("PROPOSTA CONSERVADORA"))
        assertTrue(unknown.targetLabel.contains("ESTIMATIVA"))
        assertTrue(unknown.reason.contains("confirmação", ignoreCase = true))
        assertTrue(anchored.requiresHumanReview)
        assertTrue(policy.requiresHumanReview)
        assertFalse(unknown.requiresHumanReview)
        assertEquals(PredictorHumanActionState.REVIEWABLE, anchored.actionState)
        assertEquals(PredictorHumanActionState.REVIEWABLE, policy.actionState)
        assertEquals(PredictorHumanActionState.ABSTAIN, unknown.actionState)
    }

    @Test
    fun `unknown or policy wording never claims ideal or correct`() {
        listOf(MagnitudeAuthority.UNKNOWN, MagnitudeAuthority.POLICY_ONLY).forEach { authority ->
            PredictorHumanRiskState.entries.forEach { risk ->
                val state = project(authority, risk)
                val copy = listOf(state.stateLabel, state.targetLabel, state.reason, state.disclosure.orEmpty())
                    .joinToString(" ").lowercase()
                assertFalse(copy.contains("ideal"))
                assertFalse(copy.contains("correto"))
            }
        }
    }

    @Test
    fun `predicted shrunk discloses distance and uncertainty reduction`() {
        val state = PredictorHumanStateProjector.project(
            PredictorHumanProjectionInput(
                currentK = 120,
                targetEstimateK = 121.0,
                targetRange = range,
                authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
                scientificState = PredictorHumanScientificState.PREDICTED_SHRUNK,
                riskState = PredictorHumanRiskState.REVIEW_ONLY,
                confidence = 0.64,
                reasonCode = "PREDICTED_SHRUNK",
            ),
        )

        val disclosure = state.disclosure.orEmpty().lowercase()
        assertTrue(disclosure.contains("distância"))
        assertTrue(disclosure.contains("incerteza"))
        assertTrue(disclosure.contains("reduz"))
    }

    @Test
    fun `inconsistent actionable upstream is downgraded by authority and calibration`() {
        val unknown = project(MagnitudeAuthority.UNKNOWN, PredictorHumanRiskState.CALIBRATED_ACTIONABLE)
        val uncalibrated = project(MagnitudeAuthority.PHYSICALLY_ANCHORED, PredictorHumanRiskState.RISK_NOT_CALIBRATED)
        val actionable = project(MagnitudeAuthority.PHYSICALLY_ANCHORED, PredictorHumanRiskState.CALIBRATED_ACTIONABLE)

        assertEquals(PredictorHumanActionState.ABSTAIN, unknown.actionState)
        assertFalse(unknown.requiresHumanReview)
        assertEquals(PredictorHumanActionState.ABSTAIN, uncalibrated.actionState)
        assertFalse(uncalibrated.requiresHumanReview)
        assertEquals(PredictorHumanActionState.ACTIONABLE, actionable.actionState)
        assertTrue(actionable.requiresHumanReview)
    }

    @Test
    fun `missing range blocks anchored target review`() {
        val state = PredictorHumanStateProjector.project(
            PredictorHumanProjectionInput(
                currentK = 120,
                targetEstimateK = 122.0,
                targetRange = null,
                authority = MagnitudeAuthority.PHYSICALLY_ANCHORED,
                scientificState = PredictorHumanScientificState.DIRECT_CONFIRMED,
                riskState = PredictorHumanRiskState.CALIBRATED_ACTIONABLE,
                confidence = 0.9,
                reasonCode = "DIRECT_CONFIRMED",
            ),
        )

        assertEquals(PredictorHumanActionState.ABSTAIN, state.actionState)
        assertFalse(state.requiresHumanReview)
        assertNull(state.intervalLowerK)
        assertTrue(state.reason.contains("intervalo", ignoreCase = true))
    }

    private fun project(
        authority: MagnitudeAuthority,
        risk: PredictorHumanRiskState,
    ): PredictorHumanState = PredictorHumanStateProjector.project(
        PredictorHumanProjectionInput(
            currentK = 120,
            targetEstimateK = 122.0,
            targetRange = range,
            authority = authority,
            scientificState = PredictorHumanScientificState.DIRECT_CONFIRMED,
            riskState = risk,
            confidence = 0.82,
            reasonCode = "DIRECT_CONFIRMED",
        ),
    )
}
