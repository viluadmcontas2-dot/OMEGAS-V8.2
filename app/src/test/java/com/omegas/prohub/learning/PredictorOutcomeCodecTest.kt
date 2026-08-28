package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorOutcomeCodecTest {
    @Test
    fun `ideal target metadata survives versioned ledger roundtrip`() {
        val target = IdealTargetCandidate(
            cell = PredictorCell(2, 3),
            targetK = 132,
            kStarObserved = 132.4,
            currentKObserved = 120,
            uncertaintyPercent = 2.5,
            support = 0.87,
            provenance = "DIRECT_KSTAR",
            sourceRevisions = revisions(),
            estimateK = 132.4,
            range = PredictorTargetRange(129.09, 135.71, "OBSERVATION_DECLARED_UNCERTAINTY_PERCENT"),
            authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            assumptions = listOf("same calibration identity", "current source revisions"),
            evidenceRefs = listOf("evidence:visit-a", "evidence:visit-b"),
            model = model(),
            predictionErrorStats = stats(),
        )

        val encoded = PredictorOutcomeCodec.encodeTarget(target)
        val decoded = PredictorOutcomeCodec.decodeTarget(encoded)

        assertTrue(encoded.startsWith(PredictorOutcomeCodec.TARGET_SCHEMA + ":"))
        assertEquals(target, decoded)
    }

    @Test
    fun `prediction outcome survives versioned ledger roundtrip`() {
        val outcome = PredictionOutcome(
            predictionId = "prediction-155",
            predictionRevisionToken = "revision-155",
            cell = PredictorCell(4, 5),
            predictedEstimateK = 141.25,
            lowerK = 136.0,
            upperK = 146.0,
            pImprove = 0.73,
            context = PredictorOperatingPoint(
                rpm = 3180.0,
                petrolInjectionMs = 5.2,
                mapBar = 0.72,
                deltaPressureBar = 0.91,
                petrolReferenceTemperatureC = 33.0,
                waterTemperatureC = 88.0,
                gasTemperatureC = 41.0,
                effectiveMass = 1.04,
                effectiveCapacity = 0.98,
            ),
            appliedTargetK = 138.0,
            actualKStar = 139.4,
            authority = MagnitudeAuthority.PHYSICALLY_ANCHORED,
            model = model(),
            evidenceRefs = listOf("evidence:post-write-1"),
        )

        val encoded = PredictorOutcomeCodec.encodeOutcome(outcome)
        val decoded = PredictorOutcomeCodec.decodeOutcome(encoded)

        assertTrue(encoded.startsWith(PredictorOutcomeCodec.OUTCOME_SCHEMA + ":"))
        assertEquals(outcome, decoded)
    }

    @Test
    fun `unknown or malformed schema fails closed`() {
        var failed = false
        try {
            PredictorOutcomeCodec.decodeOutcome("wrong-schema:AAAA")
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)

        failed = false
        try {
            PredictorOutcomeCodec.decodeTarget(PredictorOutcomeCodec.TARGET_SCHEMA + ":not-base64")
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun model() = PredictorModelDescriptor(
        modelFamily = "RELATIVE_KSTAR_FIELD",
        modelVersion = "step155-v1",
        confidenceCalibrationVersion = "risk-cal-v3",
    )

    private fun stats() = PredictorPredictionErrorStats(
        sampleCount = 10,
        intervalHitCount = 8,
        intervalMissCount = 2,
        meanAbsoluteLogError = 0.025,
        calibrationError = 0.225,
    )

    private fun revisions() = PredictorSourceRevisions(
        mapRevision = 11,
        curveRevision = 12,
        evidenceRevision = 13,
        referenceRevision = 14,
        physicsRevision = 15,
    )
}
