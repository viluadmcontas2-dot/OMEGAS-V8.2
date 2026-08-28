package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorGeometryAblationValidatorTest {
    @Test
    fun `Tpet reference wins only when holdout P90 and risk both improve`() {
        val outcomes = mutableListOf<PredictorCoordinateHoldout>()
        listOf("e1", "e2", "e3", "e4").forEachIndexed { index, epoch ->
            outcomes += PredictorCoordinateHoldout(epoch, PredictorCoordinateCandidate.CURRENT_PETROL_ON_GAS, 0.10 + index * 0.01, 0.15 + index * 0.01)
            outcomes += PredictorCoordinateHoldout(epoch, PredictorCoordinateCandidate.MIDPOINT, 0.07 + index * 0.008, 0.11 + index * 0.008)
            outcomes += PredictorCoordinateHoldout(epoch, PredictorCoordinateCandidate.PETROL_REFERENCE, 0.03 + index * 0.004, 0.05 + index * 0.004)
        }

        val report = PredictorGeometryAblationValidator.validateCoordinate(outcomes)

        assertEquals(PredictorCoordinateCandidate.PETROL_REFERENCE, report.preferred)
        assertTrue(report.petrolReferenceValidated)
        assertEquals("PETROL_REFERENCE_DOMINATES_HOLDOUT", report.reason)
    }

    @Test
    fun `tradeoff between P90 and risk cannot silently select a coordinate`() {
        val outcomes = listOf(
            PredictorCoordinateHoldout("e1", PredictorCoordinateCandidate.CURRENT_PETROL_ON_GAS, 0.04, 0.10),
            PredictorCoordinateHoldout("e2", PredictorCoordinateCandidate.CURRENT_PETROL_ON_GAS, 0.05, 0.10),
            PredictorCoordinateHoldout("e1", PredictorCoordinateCandidate.MIDPOINT, 0.05, 0.07),
            PredictorCoordinateHoldout("e2", PredictorCoordinateCandidate.MIDPOINT, 0.06, 0.07),
            PredictorCoordinateHoldout("e1", PredictorCoordinateCandidate.PETROL_REFERENCE, 0.03, 0.12),
            PredictorCoordinateHoldout("e2", PredictorCoordinateCandidate.PETROL_REFERENCE, 0.04, 0.12),
        )

        val report = PredictorGeometryAblationValidator.validateCoordinate(outcomes)

        assertEquals(null, report.preferred)
        assertFalse(report.petrolReferenceValidated)
        assertEquals("NO_COORDINATE_DOMINATES_ERROR_AND_RISK", report.reason)
    }

    @Test
    fun `contextual dimension promotes only when every comparable epoch improves error and risk`() {
        val outcomes = listOf(
            PredictorContextAblationOutcome("e1", 0.08, 0.10, 0.05, 0.07, true),
            PredictorContextAblationOutcome("e2", 0.07, 0.09, 0.04, 0.06, true),
            PredictorContextAblationOutcome("e3", 0.09, 0.11, 0.06, 0.08, true),
        )

        val report = PredictorGeometryAblationValidator.validateContextual(outcomes)

        assertTrue(report.promoteContextualDimension)
        assertEquals("CONTEXTUAL_HOLDOUT_IMPROVES_ERROR_AND_RISK", report.reason)
    }

    @Test
    fun `one contextual regression keeps canonical Map K two dimensional`() {
        val outcomes = listOf(
            PredictorContextAblationOutcome("e1", 0.08, 0.10, 0.05, 0.07, true),
            PredictorContextAblationOutcome("e2", 0.07, 0.09, 0.08, 0.06, true),
        )

        val report = PredictorGeometryAblationValidator.validateContextual(outcomes)

        assertFalse(report.promoteContextualDimension)
        assertEquals("CONTEXTUAL_MODEL_NOT_STRICTLY_BETTER", report.reason)
    }

    @Test
    fun `missing MAP or context retains valid base 2D without promotion`() {
        val outcomes = listOf(
            PredictorContextAblationOutcome("e1", 0.08, 0.10, null, null, false),
            PredictorContextAblationOutcome("e2", 0.07, 0.09, 0.04, 0.06, true),
        )

        val report = PredictorGeometryAblationValidator.validateContextual(outcomes)

        assertFalse(report.promoteContextualDimension)
        assertEquals("CONTEXT_UNAVAILABLE_BASE_2D_RETAINED", report.reason)
    }
}
