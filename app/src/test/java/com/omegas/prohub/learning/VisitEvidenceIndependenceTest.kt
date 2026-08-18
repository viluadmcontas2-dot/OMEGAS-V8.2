package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitEvidenceIndependenceTest {
    @Test
    fun tenThousandWindowsFromOneVisitCannotExceedVisitWeightBudget() {
        var accumulator = VisitComparisonAccumulator(key = "visit-a:region-1")
        repeat(10_000) { index ->
            accumulator = accumulator.add(
                error = 8.0,
                sampleWeight = 1.0,
                independent = true,
                nowMs = index.toLong(),
            )
        }
        assertEquals(VisitComparisonAccumulator.MAX_VISIT_WEIGHT, accumulator.weight, 1e-12)
        assertEquals(8.0, accumulator.meanError(), 1e-12)
        assertTrue(accumulator.saturated)
    }

    @Test
    fun separatePhysicalVisitGetsItsOwnBoundedAccumulator() {
        var first = VisitComparisonAccumulator(key = "visit-a:region-1")
        var second = VisitComparisonAccumulator(key = "visit-b:region-1")
        repeat(100) { index ->
            first = first.add(5.0, 1.0, independent = false, nowMs = index.toLong())
            second = second.add(-3.0, 1.0, independent = false, nowMs = index.toLong())
        }
        assertEquals(VisitComparisonAccumulator.MAX_VISIT_WEIGHT, first.weight, 1e-12)
        assertEquals(VisitComparisonAccumulator.MAX_VISIT_WEIGHT, second.weight, 1e-12)
        assertEquals(5.0, first.meanError(), 1e-12)
        assertEquals(-3.0, second.meanError(), 1e-12)
    }
}
