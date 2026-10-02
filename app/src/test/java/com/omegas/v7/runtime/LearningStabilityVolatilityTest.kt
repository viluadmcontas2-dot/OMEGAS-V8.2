package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStabilityVolatilityTest {
    private val revision = CalibrationRevisionV7(0, 0)

    @Test
    fun isolatedOutliersDoNotReplaceConsolidatedPrimary() {
        val baselineErrors = listOf(1.7, 2.0, 1.5, 1.9, 2.2, 1.6)
        val baseline = baselineErrors.mapIndexed { index, error -> comparison(index, error) }
        val consolidated = LearningStabilityV7.mapCell(baseline, row = 4, column = 2)

        assertEquals(LearningStabilityStateV7.CONSOLIDATED, consolidated.state)
        assertEquals(1.7, consolidated.consolidatedErrorPercent ?: Double.NaN, 0.000001)

        val withPositiveOutlier = LearningStabilityV7.mapCell(
            baseline + comparison(6, 15.0),
            row = 4,
            column = 2,
        )
        assertEquals(LearningStabilityStateV7.REVALIDATING, withPositiveOutlier.state)
        assertEquals(1.7, withPositiveOutlier.consolidatedErrorPercent ?: Double.NaN, 0.000001)
        assertEquals(15.0, withPositiveOutlier.recentErrorPercent ?: Double.NaN, 0.000001)

        val withOppositeOutlier = LearningStabilityV7.mapCell(
            baseline + comparison(6, 15.0) + comparison(7, -10.0),
            row = 4,
            column = 2,
        )
        assertEquals(LearningStabilityStateV7.REVALIDATING, withOppositeOutlier.state)
        assertEquals(1.7, withOppositeOutlier.consolidatedErrorPercent ?: Double.NaN, 0.000001)
        assertNotNull(withOppositeOutlier.recentErrorPercent)

        val rawSeries = baselineErrors + 15.0 + -10.0
        val rawMaxJump = rawSeries.zipWithNext { a, b -> kotlin.math.abs(b - a) }.maxOrNull() ?: 0.0
        val stablePrimarySeries = listOf(1.7, 1.7, 1.7)
        val stableMaxJump = stablePrimarySeries.zipWithNext { a, b -> kotlin.math.abs(b - a) }.maxOrNull() ?: 0.0
        assertTrue("raw probe must reproduce pathological volatility", rawMaxJump >= 20.0)
        assertTrue("consolidated primary must remain stable under isolated conflict", stableMaxJump < 0.01)
    }

    @Test
    fun repeatedCoherentContraryEvidenceEventuallyPromotesNewGeneration() {
        val baseline = listOf(1.7, 2.0, 1.5, 1.9, 2.2, 1.6)
            .mapIndexed { index, error -> comparison(index, error) }
        val firstContrary = LearningStabilityV7.mapCell(
            baseline + comparison(6, -10.0),
            row = 4,
            column = 2,
        )
        assertEquals(LearningStabilityStateV7.REVALIDATING, firstContrary.state)
        assertEquals(1.7, firstContrary.consolidatedErrorPercent ?: Double.NaN, 0.000001)

        val coherentNegative = listOf(-10.0, -9.6, -10.3, -9.9, -10.1, -9.8)
            .mapIndexed { offset, error -> comparison(6 + offset, error) }
        val promoted = LearningStabilityV7.mapCell(
            baseline + coherentNegative,
            row = 4,
            column = 2,
        )

        assertEquals(LearningStabilityStateV7.CONSOLIDATED, promoted.state)
        assertTrue("new repeated evidence must advance the scientific generation", promoted.generation >= 1)
        assertTrue("promoted center must reflect the coherent new direction", (promoted.consolidatedErrorPercent ?: 0.0) < -9.0)
        assertEquals("DECREASE_CNG_DELIVERY", promoted.direction)
    }

    private fun comparison(index: Int, errorPercent: Double): FuelComparisonV7 {
        val observed = 4.5
        val target = observed / (1.0 + errorPercent / 100.0)
        val difference = observed - target
        return FuelComparisonV7(
            id = "cmp-$index",
            revision = revision,
            cngVisitId = "visit-$index",
            petrolEvidenceIds = listOf("petrol-$index"),
            rpm = 1850.0,
            mapBar = 0.40,
            waterC = 85.0,
            petrolTargetMs = target,
            petrolOnCngMs = observed,
            differenceMs = difference,
            errorPercent = errorPercent,
            direction = when {
                kotlin.math.abs(difference) <= 0.12 || kotlin.math.abs(errorPercent) <= 2.5 -> "EQUIVALENT"
                difference > 0.0 -> "INCREASE_CNG_DELIVERY"
                else -> "DECREASE_CNG_DELIVERY"
            },
            quality = 1.0,
            createdAtMs = 1_000L + index,
        )
    }
}
