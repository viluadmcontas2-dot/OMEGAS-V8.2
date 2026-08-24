package com.omegas.prohub.learning

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorIdealTargetStepPolicyTest {
    @Test
    fun `same physical evidence with different support keeps same ideal target and delta star`() {
        val lowSupport = PredictorContract.evaluate(input(support = 0.55))
        val highSupport = PredictorContract.evaluate(input(support = 0.95))

        val low = lowSupport.candidates.single()
        val high = highSupport.candidates.single()
        assertEquals(132, low.targetK)
        assertEquals(low.targetK, high.targetK)
        assertEquals(ln(132.0 / 120.0), low.deltaStar!!, 1e-12)
        assertEquals(low.deltaStar!!, high.deltaStar!!, 1e-12)
    }

    @Test
    fun `beta changes only k next while ideal target stays fixed`() {
        val ideal = PredictorContract.evaluate(input()).candidates.single()
        val conservative = PredictorStepPolicy.apply(
            StepPolicyInput(currentK = ideal.currentKObserved, idealKStar = ideal.kStarObserved, beta = 0.25),
        )
        val assertive = PredictorStepPolicy.apply(
            StepPolicyInput(currentK = ideal.currentKObserved, idealKStar = ideal.kStarObserved, beta = 0.75),
        )

        assertTrue(conservative.available)
        assertTrue(assertive.available)
        assertEquals(132, ideal.targetK)
        assertTrue(conservative.kNext!! < assertive.kNext!!)
        assertTrue(assertive.kNext!! <= ideal.targetK)
        assertEquals(ideal.deltaStar!!, conservative.deltaStar!!, 1e-12)
        assertEquals(ideal.deltaStar!!, assertive.deltaStar!!, 1e-12)
    }

    @Test
    fun `beta endpoints and correction sign obey physical direction`() {
        val noStep = PredictorStepPolicy.apply(StepPolicyInput(120, 132.0, 0.0))
        val fullStep = PredictorStepPolicy.apply(StepPolicyInput(120, 132.0, 1.0))
        val decrease = PredictorStepPolicy.apply(StepPolicyInput(120, 108.0, 0.5))

        assertEquals(120, noStep.kNext)
        assertEquals(132, fullStep.kNext)
        assertTrue(decrease.kNext!! < 120)
        assertTrue(decrease.deltaStar!! < 0.0)
    }

    @Test
    fun `invalid policy inputs fail closed`() {
        listOf(
            StepPolicyInput(120, 132.0, Double.NaN),
            StepPolicyInput(120, 132.0, -0.1),
            StepPolicyInput(120, 132.0, 1.1),
            StepPolicyInput(0, 132.0, 0.5),
            StepPolicyInput(120, 0.0, 0.5),
            StepPolicyInput(120, Double.NaN, 0.5),
        ).forEach { input ->
            val decision = PredictorStepPolicy.apply(input)
            assertFalse(decision.available)
            assertEquals(null, decision.kNext)
        }
    }

    private fun input(support: Double = 0.82): PredictorInputSnapshot {
        val revisions = PredictorSourceRevisions(11L, 12L, 13L, 14L, 15L)
        val calibration = LearningCalibrationBinding(
            calibrationFingerprint = "calibration-A",
            calibrationGeneration = 7,
            geometryFingerprint = "geometry-A",
            usbSessionId = 21L,
            mapHash = "map-A",
            petrolAxisMs = List(12) { index -> 1.0 + index },
            rpmAxis = List(12) { index -> 800 + index * 300 },
        )
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
            cell = PredictorCell(2, 3),
            kStar = 132.0,
            currentK = 120,
            uncertaintyPercent = 1.5,
            support = support,
            knownness = PredictorKnownness.KNOWN,
            operatingPoint = PredictorOperatingPoint(2400.0, 4.5, 0.60),
            stamp = stamp,
            provenance = "DIRECT",
            contextState = PredictorContextState.SUFFICIENT,
            supportState = PredictorSupportState.SUFFICIENT,
        )
        return PredictorInputSnapshot(
            calibration = calibration,
            curveHash = "curve-A",
            sourceRevisions = revisions,
            epoch = 4,
            sessionId = "science-session-4",
            observations = listOf(observation),
        )
    }
}
