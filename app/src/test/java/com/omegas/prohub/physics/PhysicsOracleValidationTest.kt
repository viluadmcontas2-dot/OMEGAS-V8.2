package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsOracleValidationTest {
    @Test fun `bootstrap is deterministic and centered near analytic target`() {
        val observations = listOf(
            RatioObservation(5.30, 5.00),
            RatioObservation(5.28, 5.00),
            RatioObservation(5.34, 5.02),
            RatioObservation(5.31, 4.99),
            RatioObservation(5.29, 5.01),
        )
        val gain = PlantGain.empiricallyBounded(1.0, 0.9, 1.1)
        val first = PhysicsOracleValidator.bootstrap(observations, currentFactor = 1.0, gain = gain, resamples = 800, seed = 77L)
        val second = PhysicsOracleValidator.bootstrap(observations, currentFactor = 1.0, gain = gain, resamples = 800, seed = 77L)
        assertEquals(first, second)
        val analytic = KStarEstimator.estimate(5.304, 5.004, 1.0, gain)
        assertTrue(requireNotNull(analytic.targetFactor) in requireNotNull(first.lower95)..requireNotNull(first.upper95))
    }

    @Test fun `GUM equivalent expands with uncertainty and contains nominal target`() {
        val gain = PlantGain.empiricallyBounded(1.0, 0.9, 1.1)
        val low = PhysicsOracleValidator.gumEquivalent(5.30, 5.00, 1.0, gain, relativeStd = 0.005)
        val high = PhysicsOracleValidator.gumEquivalent(5.30, 5.00, 1.0, gain, relativeStd = 0.030)
        val nominal = requireNotNull(KStarEstimator.estimate(5.30, 5.00, 1.0, gain).targetFactor)
        assertTrue(nominal in requireNotNull(low.lower95)..requireNotNull(low.upper95))
        assertTrue(requireNotNull(high.upper95) - requireNotNull(high.lower95) > requireNotNull(low.upper95) - requireNotNull(low.lower95))
    }

    @Test fun `ratio target is invariant to common time scaling`() {
        val gain = PlantGain.empiricallyBounded(1.0, 1.0, 1.0)
        val a = KStarEstimator.estimate(5.5, 5.0, 1.0, gain)
        val b = KStarEstimator.estimate(11.0, 10.0, 1.0, gain)
        assertEquals(requireNotNull(a.targetFactor), requireNotNull(b.targetFactor), 1e-12)
    }

    @Test fun `held out oracle reports interval coverage without fabricating precision`() {
        val gain = PlantGain.empiricallyBounded(1.0, 0.85, 1.15)
        val holdouts = listOf(
            HoldoutCase(5.20, 5.00, 1.0, expectedTargetFactor = 1.04),
            HoldoutCase(5.40, 5.00, 1.0, expectedTargetFactor = 1.08),
            HoldoutCase(4.80, 5.00, 1.0, expectedTargetFactor = 0.96),
        )
        val report = PhysicsOracleValidator.evaluateHoldouts(holdouts, gain, relativeStd = 0.03)
        assertTrue(report.coverage in 0.0..1.0)
        assertEquals(holdouts.size, report.total)
        assertTrue(report.covered > 0)
    }
}
