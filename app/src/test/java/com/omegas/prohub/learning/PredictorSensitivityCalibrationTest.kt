package com.omegas.prohub.learning

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorSensitivityCalibrationTest {
    @Test
    fun `known g is recovered from comparable real intervention`() {
        val g = 1.8
        val dln = 0.08
        val result = PredictorSensitivityCalibration.update(
            input(beforeError = 0.20, afterError = 0.20 - g * dln, afterFactor = exp(dln)),
        )
        assertTrue(result.accepted)
        assertEquals(g, result.gHat!!, 1e-9)
        assertTrue(result.posterior.gVariance < result.prior.gVariance + result.processVariance)
        assertNotNull(result.realEvidence)
    }

    @Test
    fun `tiny intervention identity or context failure abstains`() {
        assertFalse(PredictorSensitivityCalibration.update(input(afterFactor = 1.0 + 1e-12)).accepted)
        assertFalse(PredictorSensitivityCalibration.update(input(sameIdentity = false)).accepted)
        assertFalse(PredictorSensitivityCalibration.update(input(contextComparable = false)).accepted)
    }

    @Test
    fun `sign reversal remains representable without plateau clamp`() {
        val result = PredictorSensitivityCalibration.update(
            input(beforeError = 0.10, afterError = 0.16, afterFactor = exp(0.05), measurementVariance = 1e-8),
        )
        assertTrue(result.accepted)
        assertTrue(result.gHat!! < 0.0)
        assertTrue(result.posterior.gMean < 0.0)
    }

    @Test
    fun `prediction contradiction inflates model error and downgrades`() {
        val result = PredictorSensitivityCalibration.update(
            input(beforeError = 0.15, afterError = 0.24, predictedAfterError = 0.05, afterFactor = exp(0.06)),
        )
        assertTrue(result.accepted)
        assertTrue(result.modelDowngraded)
        assertTrue(result.posterior.modelErrorVariance > result.prior.modelErrorVariance)
    }

    private fun input(
        beforeError: Double = 0.20,
        afterError: Double = 0.10,
        beforeFactor: Double = 1.0,
        afterFactor: Double = exp(0.05),
        sameIdentity: Boolean = true,
        contextComparable: Boolean = true,
        measurementVariance: Double = 0.0004,
        predictedAfterError: Double? = 0.11,
    ) = PredictorSensitivityInput(
        sameIdentity = sameIdentity,
        contextComparable = contextComparable,
        beforeError = beforeError,
        afterError = afterError,
        beforeFactor = beforeFactor,
        afterFactor = afterFactor,
        measurementVariance = measurementVariance,
        processVariance = 0.01,
        predictedAfterError = predictedAfterError,
        prior = PredictorSensitivityPosterior(gMean = 1.0, gVariance = 0.5, modelErrorVariance = 0.02),
        provenance = listOf("confirmed-write", "comparable-after-window"),
    )
}
