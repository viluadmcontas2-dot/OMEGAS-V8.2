package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousLearningMathTest {
    @Test
    fun `sample close to an rpm boundary mostly stays on the lower column`() {
        val contributions = ContinuousLearningMath.bilinearWeights(2501.0, 5.0)
        assertEquals(1.0, contributions.sumOf { it.weight }, 0.000001)
        val lower = contributions.single { it.row == 4 && it.column == 3 }
        assertTrue(lower.weight > 0.65)
        assertTrue(contributions.any { it.row == 5 && it.column == 3 && it.weight > 0.30 })
    }

    @Test
    fun `exact grid point produces only one contribution`() {
        val contributions = ContinuousLearningMath.bilinearWeights(2500.0, 4.5)
        assertEquals(1, contributions.size)
        assertEquals(1.0, contributions.single().weight, 0.000001)
        assertEquals(4, contributions.single().row)
        assertEquals(3, contributions.single().column)
    }

    @Test
    fun `dwell weight saturates instead of counting every frame as independent`() {
        val short = ContinuousLearningMath.dwellWeight(200)
        val long = ContinuousLearningMath.dwellWeight(20_000)
        assertTrue(short > 0.0)
        assertTrue(long < 1.0)
        assertTrue(long > short)
    }

    @Test
    fun `effective sample size exposes correlated repeated weights`() {
        assertEquals(3.0, ContinuousLearningMath.effectiveSampleSize(listOf(1.0, 1.0, 1.0)), 0.000001)
        assertEquals(1.0, ContinuousLearningMath.effectiveSampleSize(listOf(3.0)), 0.000001)
    }
}

