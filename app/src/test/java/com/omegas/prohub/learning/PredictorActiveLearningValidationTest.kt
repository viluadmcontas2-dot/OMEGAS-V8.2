package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorActiveLearningValidationTest {
    @Test
    fun `first useful anchor favors frequent high quality region`() {
        val frequentGood = region(
            id = "frequent-good",
            usage = 0.95,
            novelty = 0.65,
            referenceQuality = 0.95,
        )
        val rareWeak = region(
            id = "rare-weak",
            usage = 0.20,
            novelty = 0.90,
            referenceQuality = 0.45,
        )
        assertEquals("frequent-good", PredictorActiveLearning.rank(listOf(rareWeak, frequentGood)).first().regionId)
    }

    @Test
    fun `random region Monte Carlo agrees with independent VOI oracle`() {
        val random = Random(161_2026)
        repeat(2_000) { iteration ->
            val regions = (0 until 12).map { index ->
                region(
                    id = "r${iteration}_$index",
                    usage = random.nextDouble(),
                    novelty = random.nextDouble(),
                    uncertainty = random.nextDouble(),
                    referenceQuality = random.nextDouble(),
                    freshness = random.nextDouble(),
                    independence = random.nextDouble(),
                    expectedImpact = random.nextDouble(),
                    acquisitionCost = 0.05 + random.nextDouble() * 4.95,
                    eligible = random.nextDouble() > 0.20,
                )
            }
            val eligible = regions.filter { it.naturallyEligible }
            val ranked = PredictorActiveLearning.rank(regions)
            if (eligible.isEmpty()) {
                assertTrue(ranked.isEmpty())
            } else {
                val oracle = eligible.maxWithOrNull(
                    compareBy<PredictorLearningRegion> { oracleScore(it) }
                        .thenByDescending { it.regionId },
                )!!
                assertEquals(oracle.regionId, ranked.first().regionId)
                assertTrue(abs(oracleScore(oracle) - ranked.first().score) <= 1e-12)
            }
        }
    }

    @Test
    fun `static top k has zero exhaustive regret for k3 and k4`() {
        val candidates = listOf(
            region("a", 0.92, 0.20),
            region("b", 0.70, 0.80),
            region("c", 0.65, 0.75),
            region("d", 0.55, 0.90),
            region("e", 0.50, 0.60),
            region("f", 0.30, 0.95),
        )
        for (k in listOf(3, 4)) {
            val ranked = PredictorActiveLearning.rank(candidates).take(k)
            val rankedValue = ranked.sumOf { it.score }
            val exhaustiveBest = combinations(candidates, k)
                .maxOf { subset -> subset.sumOf(::oracleScore) }
            assertTrue(abs(exhaustiveBest - rankedValue) <= 1e-12)
        }
    }

    @Test
    fun `ineligible candidates never contribute to top k`() {
        val candidates = listOf(
            region("blocked-perfect", 1.0, 1.0, eligible = false),
            region("one", 0.4, 0.5),
            region("two", 0.3, 0.4),
        )
        assertEquals(listOf("one", "two"), PredictorActiveLearning.rank(candidates).map { it.regionId })
    }

    private fun oracleScore(region: PredictorLearningRegion): Double =
        region.usage *
            region.geometricNovelty *
            region.modelUncertainty *
            region.referenceQuality *
            region.calibrationFreshness *
            region.independence *
            region.expectedErrorImpact /
            region.acquisitionCost

    private fun combinations(
        input: List<PredictorLearningRegion>,
        k: Int,
        start: Int = 0,
        prefix: List<PredictorLearningRegion> = emptyList(),
    ): List<List<PredictorLearningRegion>> {
        if (prefix.size == k) return listOf(prefix)
        val remaining = k - prefix.size
        if (input.size - start < remaining) return emptyList()
        val out = ArrayList<List<PredictorLearningRegion>>()
        for (index in start..input.size - remaining) {
            out += combinations(input, k, index + 1, prefix + input[index])
        }
        return out
    }

    private fun region(
        id: String,
        usage: Double,
        novelty: Double,
        uncertainty: Double = 0.8,
        referenceQuality: Double = 0.9,
        freshness: Double = 0.9,
        independence: Double = 0.8,
        expectedImpact: Double = 0.9,
        acquisitionCost: Double = 1.0,
        eligible: Boolean = true,
    ) = PredictorLearningRegion(
        regionId = id,
        naturallyEligible = eligible,
        usage = usage,
        geometricNovelty = novelty,
        modelUncertainty = uncertainty,
        referenceQuality = referenceQuality,
        calibrationFreshness = freshness,
        independence = independence,
        expectedErrorImpact = expectedImpact,
        acquisitionCost = acquisitionCost,
    )
}
