package com.omegas.prohub.learning

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorStepPolicyContinuousKStarTest {
    @Test
    fun `step policy preserves continuous K star before final quantization`() {
        val kStar = 132.4
        val currentK = 120
        val expectedDeltaStar = ln(kStar / currentK.toDouble())

        val decision = PredictorStepPolicy.apply(
            StepPolicyInput(
                currentK = currentK,
                idealKStar = kStar,
                beta = 0.5,
            ),
        )

        assertTrue(decision.available)
        assertEquals(expectedDeltaStar, decision.deltaStar!!, 1e-12)
    }

    @Test
    fun `candidate physical delta and policy delta remain identical for non integer K star`() {
        val candidate = IdealTargetCandidate(
            cell = PredictorCell(2, 3),
            targetK = 132,
            kStarObserved = 132.4,
            currentKObserved = 120,
            uncertaintyPercent = 1.0,
            support = 0.9,
            provenance = "DIRECT",
            sourceRevisions = PredictorSourceRevisions(1L, 2L, 3L, 4L, 5L),
        )
        val decision = PredictorStepPolicy.apply(
            StepPolicyInput(
                currentK = candidate.currentKObserved,
                idealKStar = candidate.kStarObserved,
                beta = 0.75,
            ),
        )

        assertTrue(decision.available)
        assertEquals(candidate.deltaStar!!, decision.deltaStar!!, 1e-12)
    }
}
