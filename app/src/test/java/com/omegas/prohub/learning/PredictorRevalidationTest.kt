package com.omegas.prohub.learning

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorRevalidationTest {
    @Test
    fun `strong reference follows four provisional to six confirmed without changing outcome direction`() {
        val at4 = PredictorRevalidation.evaluate(input(afterFrameCount = 4, beforeError = 0.20, afterError = 0.10))
        assertEquals(PredictorRevalidationEvidenceState.DIRECT_PROVISIONAL, at4.evidenceState)
        assertEquals(PredictorSuggestionState.REVALIDATING, at4.lifecycleState)
        assertEquals(PredictorSuggestionState.IMPROVED, at4.preliminaryOutcome)
        assertFalse(at4.adaptationAllowed)

        val at6 = PredictorRevalidation.evaluate(input(afterFrameCount = 6, beforeError = 0.20, afterError = 0.10))
        assertEquals(PredictorRevalidationEvidenceState.DIRECT_CONFIRMED, at6.evidenceState)
        assertEquals(PredictorSuggestionState.IMPROVED, at6.lifecycleState)
        assertEquals(PredictorSuggestionState.IMPROVED, at6.preliminaryOutcome)
        assertTrue(at6.adaptationAllowed)
        assertNotNull(at6.sensitivityResult)
    }

    @Test
    fun `prediction source cannot adapt even with six frames and a sensitivity payload`() {
        val result = PredictorRevalidation.evaluate(
            input(
                afterFrameCount = 6,
                afterSourceType = PredictorScientificSourceType.PREDICTION,
            ),
        )
        assertEquals(PredictorSuggestionState.REVALIDATING, result.lifecycleState)
        assertFalse(result.adaptationAllowed)
        assertNull(result.sensitivityResult)
        assertEquals("AFTER_SOURCE_NOT_REAL", result.reason)
    }

    @Test
    fun `worse real outcome becomes regressed and downgrades model`() {
        val result = PredictorRevalidation.evaluate(
            input(afterFrameCount = 6, beforeError = 0.10, afterError = 0.22),
        )
        assertEquals(PredictorSuggestionState.REGRESSED, result.lifecycleState)
        assertTrue(result.modelDowngraded)
        assertTrue(result.adaptationAllowed)
    }

    @Test
    fun `sign reversal may converge and is never clamped to a plateau`() {
        val result = PredictorRevalidation.evaluate(
            input(afterFrameCount = 6, beforeError = 0.12, afterError = -0.015, zeroBand = 0.02),
        )
        assertEquals(PredictorSuggestionState.CONVERGED, result.lifecycleState)
        assertTrue(result.adaptationAllowed)
    }

    @Test
    fun `weak reference uses eight frame fallback instead of pretending fast confirmation`() {
        val at7 = PredictorRevalidation.evaluate(input(afterFrameCount = 7, referenceStrong = false))
        assertEquals(PredictorRevalidationEvidenceState.WAITING, at7.evidenceState)
        assertEquals(PredictorSuggestionState.REVALIDATING, at7.lifecycleState)
        val at8 = PredictorRevalidation.evaluate(input(afterFrameCount = 8, referenceStrong = false))
        assertEquals(PredictorRevalidationEvidenceState.FALLBACK_CONFIRMED, at8.evidenceState)
        assertTrue(at8.adaptationAllowed)
    }

    @Test
    fun `non comparable context keeps suggestion revalidating`() {
        val result = PredictorRevalidation.evaluate(input(afterFrameCount = 8, contextComparable = false))
        assertEquals(PredictorRevalidationEvidenceState.WAITING, result.evidenceState)
        assertEquals(PredictorSuggestionState.REVALIDATING, result.lifecycleState)
        assertFalse(result.adaptationAllowed)
    }

    @Test
    fun `synthetic repeated corrections demonstrate convergence rather than plateau`() {
        var before = 0.24
        val afters = listOf(0.12, 0.055, -0.012)
        val states = afters.map { after ->
            val result = PredictorRevalidation.evaluate(
                input(afterFrameCount = 6, beforeError = before, afterError = after, zeroBand = 0.02),
            )
            before = after
            result.lifecycleState
        }
        assertEquals(
            listOf(
                PredictorSuggestionState.IMPROVED,
                PredictorSuggestionState.IMPROVED,
                PredictorSuggestionState.CONVERGED,
            ),
            states,
        )
    }

    private fun input(
        afterFrameCount: Int,
        beforeError: Double = 0.20,
        afterError: Double = 0.10,
        referenceStrong: Boolean = true,
        contextComparable: Boolean = true,
        afterSourceType: PredictorScientificSourceType = PredictorScientificSourceType.POST_WRITE_OUTCOME,
        zeroBand: Double = 0.02,
    ): PredictorRevalidationInput = PredictorRevalidationInput(
        suggestionState = PredictorSuggestionState.REVALIDATING,
        referenceStrong = referenceStrong,
        contextComparable = contextComparable,
        afterSourceType = afterSourceType,
        afterFrameCount = afterFrameCount,
        beforeError = beforeError,
        afterError = afterError,
        zeroBand = zeroBand,
        noChangeTolerance = 0.005,
        sensitivityInput = PredictorSensitivityInput(
            sameIdentity = true,
            contextComparable = contextComparable,
            beforeError = beforeError,
            afterError = afterError,
            beforeFactor = 1.0,
            afterFactor = exp(0.08),
            measurementVariance = 0.01,
            processVariance = 0.01,
            predictedAfterError = beforeError - 0.05,
            prior = PredictorSensitivityPosterior(
                gMean = 1.0,
                gVariance = 0.25,
                modelErrorVariance = 0.01,
            ),
            provenance = listOf("synthetic-real-after"),
        ),
    )
}
