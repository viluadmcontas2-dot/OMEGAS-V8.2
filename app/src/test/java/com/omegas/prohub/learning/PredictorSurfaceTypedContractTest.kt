package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorSurfaceTypedContractTest {
    @Test
    fun `typed surface delegates to fail closed Predictor contract`() {
        val revisions = PredictorSourceRevisions(
            mapRevision = 31L,
            curveRevision = 32L,
            evidenceRevision = 33L,
            referenceRevision = 34L,
            physicsRevision = 35L,
        )
        val calibration = LearningCalibrationBinding(
            calibrationFingerprint = "typed-calibration",
            calibrationGeneration = 4,
            geometryFingerprint = "typed-geometry",
            usbSessionId = 9L,
            mapHash = "typed-map",
            petrolAxisMs = List(12) { index -> 1.0 + index },
            rpmAxis = List(12) { index -> 900 + index * 300 },
        )
        val stamp = PredictorEvidenceStamp(
            calibrationFingerprint = calibration.calibrationFingerprint,
            calibrationGeneration = calibration.calibrationGeneration,
            geometryFingerprint = calibration.geometryFingerprint,
            mapHash = calibration.mapHash,
            curveHash = "typed-curve",
            sourceRevisions = revisions,
            epoch = 5,
            sessionId = "typed-session",
            freshness = PredictorSourceFreshness.CURRENT,
        )
        val observation = PredictorObservation(
            cell = PredictorCell(row = 1, column = 2),
            kStar = 126.0,
            currentK = 120,
            uncertaintyPercent = 1.0,
            support = 0.90,
            knownness = PredictorKnownness.KNOWN,
            operatingPoint = PredictorOperatingPoint(
                rpm = 1500.0,
                petrolInjectionMs = 3.0,
                mapBar = 0.55,
            ),
            stamp = stamp,
            provenance = "TYPED_SURFACE_TEST",
        )
        val input = PredictorInputSnapshot(
            calibration = calibration,
            curveHash = "typed-curve",
            sourceRevisions = revisions,
            epoch = 5,
            sessionId = "typed-session",
            observations = listOf(observation),
        )

        val result = PredictorSurface.build(input)

        assertEquals(PredictorSnapshotState.READY, result.state)
        assertEquals(126, result.candidates.single().targetK)
        assertTrue(result.abstentionReasons.isEmpty())
    }
}
