package com.omegas.prohub.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorRiskCoverageValidatorTest {
    @Test
    fun `leave one epoch report calibrates only when high confidence error improves every held out epoch`() {
        val outcomes = listOf(
            outcome("e1", 0.95, 0.02), outcome("e1", 0.90, 0.03), outcome("e1", 0.40, 0.12),
            outcome("e2", 0.96, 0.01), outcome("e2", 0.88, 0.04), outcome("e2", 0.35, 0.10),
            outcome("e3", 0.92, 0.03), outcome("e3", 0.86, 0.04), outcome("e3", 0.30, 0.14),
        )

        val report = PredictorRiskCoverageValidator.leaveOneEpoch(
            outcomes = outcomes,
            highConfidenceCutoff = 0.80,
        )

        assertTrue(report.calibrated)
        assertTrue(report.epochs.all { it.highConfidenceMeanAbsoluteLogError < it.overallMeanAbsoluteLogError })
    }

    @Test
    fun `high diagnostic confidence that does not improve error fails calibration`() {
        val outcomes = listOf(
            outcome("e1", 0.95, 0.15), outcome("e1", 0.30, 0.02),
            outcome("e2", 0.90, 0.12), outcome("e2", 0.25, 0.03),
        )

        val report = PredictorRiskCoverageValidator.leaveOneEpoch(outcomes, 0.80)

        assertFalse(report.calibrated)
        assertTrue(report.reason.contains("HIGH_CONFIDENCE_NOT_BETTER"))
    }

    @Test
    fun `single epoch cannot claim leave one epoch calibration`() {
        val report = PredictorRiskCoverageValidator.leaveOneEpoch(
            outcomes = listOf(outcome("only", 0.95, 0.02), outcome("only", 0.30, 0.10)),
            highConfidenceCutoff = 0.80,
        )

        assertFalse(report.calibrated)
        assertTrue(report.reason.contains("INSUFFICIENT_EPOCHS"))
    }

    private fun outcome(epoch: String, confidence: Double, error: Double) = PredictorRiskOutcome(
        epochId = epoch,
        diagnosticConfidence = confidence,
        absoluteLogError = error,
    )
}
