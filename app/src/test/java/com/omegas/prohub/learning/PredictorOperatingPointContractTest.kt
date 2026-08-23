package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorOperatingPointContractTest {
    @Test
    fun `operating point carries optional pressure water and gas context`() {
        val point = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = 0.60,
            deltaPressureBar = 0.42,
            petrolReferenceTemperatureC = 32.0,
            waterTemperatureC = 88.0,
            gasTemperatureC = 41.0,
            effectiveMass = 1.0,
            effectiveCapacity = 1.0,
        )

        assertEquals(0.42, point.deltaPressureBar!!, 1e-12)
        assertEquals(88.0, point.waterTemperatureC!!, 1e-12)
        assertEquals(41.0, point.gasTemperatureC!!, 1e-12)
        assertTrue(point.valid())
    }

    @Test
    fun `unknown optional environmental context remains representable`() {
        val point = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = null,
            deltaPressureBar = null,
            petrolReferenceTemperatureC = null,
            waterTemperatureC = null,
            gasTemperatureC = null,
            effectiveMass = null,
            effectiveCapacity = null,
        )

        assertTrue(point.valid())
    }

    @Test
    fun `nan contextual value is invalid`() {
        val point = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = 0.60,
            waterTemperatureC = Double.NaN,
        )

        assertTrue(!point.valid())
    }

    @Test
    fun `missing runtime geometry abstains instead of producing target`() {
        val calibration = LearningCalibrationBinding(
            calibrationFingerprint = "calibration-A",
            calibrationGeneration = 7,
            geometryFingerprint = "geometry-A",
            usbSessionId = 21L,
            mapHash = "map-A",
            petrolAxisMs = emptyList(),
            rpmAxis = emptyList(),
        )
        val revisions = PredictorSourceRevisions(11L, 12L, 13L, 14L, 15L)
        val stamp = PredictorEvidenceStamp(
            calibrationFingerprint = "calibration-A",
            calibrationGeneration = 7,
            geometryFingerprint = "geometry-A",
            mapHash = "map-A",
            curveHash = "curve-A",
            sourceRevisions = revisions,
            epoch = 4,
            sessionId = "science-session-4",
            freshness = PredictorSourceFreshness.CURRENT,
        )
        val observation = PredictorObservation(
            cell = PredictorCell(0, 0),
            kStar = 132.0,
            currentK = 120,
            uncertaintyPercent = 1.0,
            support = 0.8,
            knownness = PredictorKnownness.KNOWN,
            operatingPoint = PredictorOperatingPoint(2400.0, 4.5, 0.60),
            stamp = stamp,
            provenance = "DIRECT",
        )

        val result = PredictorContract.evaluate(
            PredictorInputSnapshot(calibration, "curve-A", revisions, 4, "science-session-4", listOf(observation)),
        )

        assertEquals(PredictorSnapshotState.ABSTAIN, result.state)
        assertTrue(result.candidates.isEmpty())
        assertTrue(PredictorAbstentionReason.INVALID_CALIBRATION_IDENTITY in result.abstentionReasons)
    }
}
