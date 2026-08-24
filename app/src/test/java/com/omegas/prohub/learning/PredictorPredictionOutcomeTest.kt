package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorPredictionOutcomeTest {
    @Test
    fun `prediction outcome remains structurally distinct from direct observation`() {
        assertFalse(PredictorObservation::class.java.isAssignableFrom(PredictionOutcome::class.java))
        assertFalse(PredictionOutcome::class.java.isAssignableFrom(PredictorObservation::class.java))
    }

    @Test
    fun `completed in interval outcome updates coverage without fabricating downgrade`() {
        val result = PredictorPredictionCalibration.reduce(
            prior = PredictorPredictionErrorStats.empty(),
            outcomes = listOf(outcome(actualKStar = 132.5, lowerK = 130.0, upperK = 134.0)),
        )

        assertEquals(1, result.stats.sampleCount)
        assertEquals(1, result.stats.intervalHitCount)
        assertEquals(0, result.stats.intervalMissCount)
        assertEquals(1.0, result.stats.intervalCoverage, 1e-12)
        assertFalse(result.actionabilityDowngraded)
        assertEquals("NO_INTERVAL_MISS", result.reason)
    }

    @Test
    fun `out of interval real outcome increases calibration error and emits downgrade`() {
        val baseline = PredictorPredictionCalibration.reduce(
            PredictorPredictionErrorStats.empty(),
            listOf(outcome(actualKStar = 132.0, lowerK = 130.0, upperK = 134.0)),
        )
        val missed = PredictorPredictionCalibration.reduce(
            baseline.stats,
            listOf(outcome(actualKStar = 145.0, lowerK = 130.0, upperK = 134.0)),
        )

        assertEquals(2, missed.stats.sampleCount)
        assertEquals(1, missed.stats.intervalMissCount)
        assertTrue(missed.stats.calibrationError > baseline.stats.calibrationError)
        assertTrue(missed.actionabilityDowngraded)
        assertEquals("OBSERVED_OUTSIDE_PREDICTION_INTERVAL", missed.reason)
    }

    @Test
    fun `pending outcome without applied target or later observation cannot enter calibration`() {
        val pending = outcome(actualKStar = null, appliedTargetK = null)
        val result = PredictorPredictionCalibration.reduce(
            PredictorPredictionErrorStats.empty(),
            listOf(pending),
        )

        assertEquals(PredictorPredictionErrorStats.empty(), result.stats)
        assertFalse(result.actionabilityDowngraded)
        assertEquals("NO_COMPLETE_POST_WRITE_OUTCOME", result.reason)
    }

    @Test
    fun `batch reduction is order invariant`() {
        val outcomes = listOf(
            outcome("p1", actualKStar = 131.0, lowerK = 128.0, upperK = 134.0),
            outcome("p2", actualKStar = 145.0, lowerK = 129.0, upperK = 135.0),
            outcome("p3", actualKStar = 133.0, lowerK = 130.0, upperK = 134.0),
        )
        val forward = PredictorPredictionCalibration.reduce(PredictorPredictionErrorStats.empty(), outcomes)
        val reverse = PredictorPredictionCalibration.reduce(PredictorPredictionErrorStats.empty(), outcomes.reversed())

        assertEquals(forward.stats, reverse.stats)
        assertEquals(forward.actionabilityDowngraded, reverse.actionabilityDowngraded)
    }

    private fun outcome(
        predictionId: String = "prediction-1",
        actualKStar: Double?,
        lowerK: Double = 130.0,
        upperK: Double = 134.0,
        appliedTargetK: Double? = 132.0,
    ): PredictionOutcome = PredictionOutcome(
        predictionId = predictionId,
        predictionRevisionToken = "revision-155",
        cell = PredictorCell(2, 3),
        predictedEstimateK = 132.0,
        lowerK = lowerK,
        upperK = upperK,
        pImprove = null,
        context = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = 0.60,
            effectiveMass = 1.0,
            effectiveCapacity = 1.0,
        ),
        appliedTargetK = appliedTargetK,
        actualKStar = actualKStar,
        authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
        model = PredictorModelDescriptor("RELATIVE_KSTAR_FIELD", "step155-v1", "risk-cal-v3"),
        evidenceRefs = listOf("evidence:visit-1"),
    )
}
