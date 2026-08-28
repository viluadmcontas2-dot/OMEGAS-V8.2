package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorAuthorityMetadataTest {
    @Test
    fun `existing ideal target candidate carries estimate range authority provenance model and error stats`() {
        val stats = PredictorPredictionErrorStats(
            sampleCount = 12,
            intervalHitCount = 9,
            intervalMissCount = 3,
            meanAbsoluteLogError = 0.031,
            calibrationError = 0.281,
        )
        val result = PredictorContract.evaluate(
            input(
                authority = MagnitudeAuthority.PHYSICALLY_ANCHORED,
                uncertaintyPercent = 10.0,
                model = PredictorModelDescriptor(
                    modelFamily = "DIRECT_KSTAR_CONTRACT",
                    modelVersion = "step155-v1",
                    confidenceCalibrationVersion = "risk-cal-v3",
                ),
                stats = stats,
            ),
        )

        val target = result.candidates.single()
        assertEquals(132.0, target.estimateK, 1e-12)
        assertEquals(118.8, target.range.lowerK, 1e-12)
        assertEquals(145.2, target.range.upperK, 1e-12)
        assertEquals("OBSERVATION_DECLARED_UNCERTAINTY_PERCENT", target.range.basis)
        assertEquals(MagnitudeAuthority.PHYSICALLY_ANCHORED, target.authority)
        assertEquals(listOf("same calibration identity", "current source revisions"), target.assumptions)
        assertEquals(listOf("evidence:direct-visit-42"), target.evidenceRefs)
        assertEquals("DIRECT_KSTAR_CONTRACT", target.model.modelFamily)
        assertEquals("step155-v1", target.model.modelVersion)
        assertEquals("risk-cal-v3", target.model.confidenceCalibrationVersion)
        assertEquals(stats, target.predictionErrorStats)
        assertEquals(revisions(), target.sourceRevisions)
        assertTrue(target.industrialIdealAuthorityEligible())
    }

    @Test
    fun `unknown and policy only authority can never self declare industrial ideal eligibility`() {
        listOf(MagnitudeAuthority.UNKNOWN, MagnitudeAuthority.POLICY_ONLY).forEach { authority ->
            val target = PredictorContract.evaluate(input(authority = authority)).candidates.single()
            assertFalse(target.industrialIdealAuthorityEligible())
        }
    }

    @Test
    fun `empirically bounded remains authority eligible but not a writer or actionability handle`() {
        val target = PredictorContract.evaluate(
            input(authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED),
        ).candidates.single()

        assertTrue(target.industrialIdealAuthorityEligible())
        val fields = target::class.java.declaredFields.map { it.name.lowercase() }
        listOf("actionable", "writer", "usb", "serial", "router", "scheduler").forEach { token ->
            assertTrue(fields.none { it.contains(token) })
        }
    }

    private fun input(
        authority: MagnitudeAuthority,
        uncertaintyPercent: Double = 2.0,
        model: PredictorModelDescriptor = PredictorModelDescriptor(
            modelFamily = "DIRECT_KSTAR_CONTRACT",
            modelVersion = "step155-v1",
            confidenceCalibrationVersion = "UNVERIFIED",
        ),
        stats: PredictorPredictionErrorStats = PredictorPredictionErrorStats.empty(),
    ): PredictorInputSnapshot = PredictorInputSnapshot(
        calibration = binding(),
        curveHash = "curve-A",
        sourceRevisions = revisions(),
        epoch = 4,
        sessionId = "science-session-4",
        observations = listOf(
            PredictorObservation(
                cell = PredictorCell(row = 2, column = 3),
                kStar = 132.0,
                currentK = 120,
                uncertaintyPercent = uncertaintyPercent,
                support = 0.82,
                knownness = PredictorKnownness.KNOWN,
                operatingPoint = PredictorOperatingPoint(
                    rpm = 2400.0,
                    petrolInjectionMs = 4.5,
                    mapBar = 0.60,
                    effectiveMass = 1.0,
                    effectiveCapacity = 1.0,
                ),
                stamp = PredictorEvidenceStamp(
                    calibrationFingerprint = "calibration-A",
                    calibrationGeneration = 7,
                    geometryFingerprint = "geometry-A",
                    mapHash = "map-A",
                    curveHash = "curve-A",
                    sourceRevisions = revisions(),
                    epoch = 4,
                    sessionId = "science-session-4",
                    freshness = PredictorSourceFreshness.CURRENT,
                ),
                provenance = "OMEGAS_DIRECT_OBSERVATION",
                magnitudeAuthority = authority,
                assumptions = listOf("same calibration identity", "current source revisions"),
                evidenceRefs = listOf("evidence:direct-visit-42"),
            ),
        ),
        model = model,
        predictionErrorStats = stats,
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
