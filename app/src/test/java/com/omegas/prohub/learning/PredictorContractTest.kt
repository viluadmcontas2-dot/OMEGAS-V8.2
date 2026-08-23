package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorContractTest {
    @Test
    fun `same identity and current revisions produce a typed ideal target`() {
        val result = PredictorContract.evaluate(input())

        assertEquals(PredictorSnapshotState.READY, result.state)
        assertTrue(result.abstentionReasons.isEmpty())
        assertEquals(1, result.candidates.size)
        assertEquals(132, result.candidates.single().targetK)
        assertEquals(revisions(), result.candidates.single().sourceRevisions)
        assertTrue(result.revisionToken.isNotBlank())
    }

    @Test
    fun `calibration fingerprint mismatch fails closed`() {
        val base = input()
        val mismatched = base.copy(
            observations = listOf(observation(stamp = stamp(calibrationFingerprint = "calibration-B"))),
        )

        assertAbstains(mismatched, PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH)
    }

    @Test
    fun `calibration generation mismatch fails closed`() {
        val base = input()
        val mismatched = base.copy(
            observations = listOf(observation(stamp = stamp(calibrationGeneration = 8))),
        )

        assertAbstains(mismatched, PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH)
    }

    @Test
    fun `geometry mismatch fails closed`() {
        val base = input()
        val mismatched = base.copy(
            observations = listOf(observation(stamp = stamp(geometryFingerprint = "geometry-B"))),
        )

        assertAbstains(mismatched, PredictorAbstentionReason.GEOMETRY_MISMATCH)
    }

    @Test
    fun `map curve hash or source revision mismatch fails closed`() {
        assertAbstains(
            input().copy(observations = listOf(observation(stamp = stamp(mapHash = "map-B")))),
            PredictorAbstentionReason.SOURCE_IDENTITY_MISMATCH,
        )
        assertAbstains(
            input().copy(observations = listOf(observation(stamp = stamp(curveHash = "curve-B")))),
            PredictorAbstentionReason.SOURCE_IDENTITY_MISMATCH,
        )
        assertAbstains(
            input().copy(
                observations = listOf(
                    observation(stamp = stamp(sourceRevisions = revisions().copy(physicsRevision = 10L))),
                ),
            ),
            PredictorAbstentionReason.SOURCE_REVISION_MISMATCH,
        )
    }

    @Test
    fun `missing source revision fails closed`() {
        val invalid = input().copy(
            sourceRevisions = revisions().copy(referenceRevision = 0L),
        )

        assertAbstains(invalid, PredictorAbstentionReason.INVALID_SOURCE_REVISION)
    }

    @Test
    fun `stale source stamp fails closed`() {
        val stale = input().copy(
            observations = listOf(
                observation(stamp = stamp(freshness = PredictorSourceFreshness.STALE)),
            ),
        )

        assertAbstains(stale, PredictorAbstentionReason.SOURCE_NOT_CURRENT)
    }

    @Test
    fun `nan or unknown scientific observation fails closed`() {
        assertAbstains(
            input().copy(observations = listOf(observation(kStar = Double.NaN))),
            PredictorAbstentionReason.INVALID_SCIENTIFIC_VALUE,
        )
        assertAbstains(
            input().copy(observations = listOf(observation(knownness = PredictorKnownness.UNKNOWN))),
            PredictorAbstentionReason.SCIENTIFIC_VALUE_UNKNOWN,
        )
    }

    @Test
    fun `calibration A plus evidence B never yields an ideal target`() {
        val calibrationA = input()
        val evidenceB = observation(stamp = stamp(calibrationFingerprint = "calibration-B"))

        val result = PredictorContract.evaluate(calibrationA.copy(observations = listOf(evidenceB)))

        assertEquals(PredictorSnapshotState.ABSTAIN, result.state)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.abstentionReasons.contains(PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH))
    }

    @Test
    fun `prediction is a distinct type and cannot become observation authority`() {
        assertFalse(PredictorObservation::class.java.isAssignableFrom(PredictorPrediction::class.java))
        assertFalse(PredictorPrediction::class.java.isAssignableFrom(PredictorObservation::class.java))
    }

    @Test
    fun `typed output exposes no writer usb serial or ui authority`() {
        val forbidden = setOf("writer", "usb", "serial", "ui", "socket", "transport")
        val fieldNames = PredictorSnapshot::class.java.declaredFields.map { it.name.lowercase() }
        val candidateFields = IdealTargetCandidate::class.java.declaredFields.map { it.name.lowercase() }

        forbidden.forEach { token ->
            assertTrue("PredictorSnapshot must not expose $token", fieldNames.none { it.contains(token) })
            assertTrue("IdealTargetCandidate must not expose $token", candidateFields.none { it.contains(token) })
        }
    }

    private fun assertAbstains(input: PredictorInputSnapshot, reason: PredictorAbstentionReason) {
        val result = PredictorContract.evaluate(input)
        assertEquals(PredictorSnapshotState.ABSTAIN, result.state)
        assertTrue(result.candidates.isEmpty())
        assertTrue("Expected $reason but got ${result.abstentionReasons}", result.abstentionReasons.contains(reason))
    }

    private fun input(): PredictorInputSnapshot = PredictorInputSnapshot(
        calibration = binding(),
        curveHash = "curve-A",
        sourceRevisions = revisions(),
        epoch = 4,
        sessionId = "science-session-4",
        observations = listOf(observation()),
    )

    private fun observation(
        stamp: PredictorEvidenceStamp = stamp(),
        kStar: Double = 1.08,
        knownness: PredictorKnownness = PredictorKnownness.KNOWN,
    ): PredictorObservation = PredictorObservation(
        cell = PredictorCell(row = 2, column = 3),
        kStar = kStar,
        currentK = 120,
        suggestedDeltaPercent = 10.0,
        uncertaintyPercent = 1.5,
        support = 0.82,
        knownness = knownness,
        operatingPoint = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = 0.60,
            petrolReferenceTemperatureC = 32.0,
            effectiveMass = 1.0,
            effectiveCapacity = 1.0,
        ),
        stamp = stamp,
        provenance = "OMEGAS_DIRECT_OBSERVATION",
    )

    private fun stamp(
        calibrationFingerprint: String = "calibration-A",
        calibrationGeneration: Int = 7,
        geometryFingerprint: String = "geometry-A",
        mapHash: String = "map-A",
        curveHash: String = "curve-A",
        sourceRevisions: PredictorSourceRevisions = revisions(),
        freshness: PredictorSourceFreshness = PredictorSourceFreshness.CURRENT,
    ): PredictorEvidenceStamp = PredictorEvidenceStamp(
        calibrationFingerprint = calibrationFingerprint,
        calibrationGeneration = calibrationGeneration,
        geometryFingerprint = geometryFingerprint,
        mapHash = mapHash,
        curveHash = curveHash,
        sourceRevisions = sourceRevisions,
        epoch = 4,
        sessionId = "science-session-4",
        freshness = freshness,
    )

    private fun revisions(): PredictorSourceRevisions = PredictorSourceRevisions(
        mapRevision = 11L,
        curveRevision = 12L,
        evidenceRevision = 13L,
        referenceRevision = 14L,
        physicsRevision = 15L,
    )

    private fun binding(): LearningCalibrationBinding = LearningCalibrationBinding(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = 7,
        geometryFingerprint = "geometry-A",
        usbSessionId = 21L,
        mapHash = "map-A",
        petrolAxisMs = List(12) { index -> 1.0 + index },
        rpmAxis = List(12) { index -> 800 + index * 300 },
    )
}
