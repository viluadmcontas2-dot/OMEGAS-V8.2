package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorActiveLearningTest {
    @Test
    fun `duplicate high dwell loses to useful novel region`() {
        val duplicate = region("duplicate", usage = 1.0, novelty = 0.01)
        val novel = region("novel", usage = 0.45, novelty = 0.90)
        val ranked = PredictorActiveLearning.rank(listOf(duplicate, novel))
        assertEquals("novel", ranked.first().regionId)
        assertEquals("NEXT_USEFUL_REGION", ranked.first().diagnosticCode)
    }

    @Test
    fun `ineligible region is never selected even with perfect factors`() {
        val ineligible = region("blocked", usage = 1.0, novelty = 1.0, eligible = false)
        val eligible = region("natural", usage = 0.2, novelty = 0.2, eligible = true)
        assertEquals(listOf("natural"), PredictorActiveLearning.rank(listOf(ineligible, eligible)).map { it.regionId })
    }

    @Test
    fun `ten thousand same dwell updates cannot beat zero novelty penalty`() {
        val duplicate = region("same", usage = 1.0, novelty = 0.0)
        val useful = region("new", usage = 0.1, novelty = 0.5)
        repeat(10_000) {
            assertEquals("new", PredictorActiveLearning.rank(listOf(duplicate, useful)).first().regionId)
        }
    }

    @Test
    fun `diagnostic surface has no driver instruction field`() {
        val fields = PredictorLearningDiagnostic::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { it.contains("driver") || it.contains("drive") || it.contains("route") || it.contains("instruction") })
    }

    private fun region(
        id: String,
        usage: Double,
        novelty: Double,
        eligible: Boolean = true,
    ) = PredictorLearningRegion(
        regionId = id,
        naturallyEligible = eligible,
        usage = usage,
        geometricNovelty = novelty,
        modelUncertainty = 0.8,
        referenceQuality = 0.9,
        calibrationFreshness = 0.9,
        independence = 0.8,
        expectedErrorImpact = 0.9,
        acquisitionCost = 1.0,
    )
}
