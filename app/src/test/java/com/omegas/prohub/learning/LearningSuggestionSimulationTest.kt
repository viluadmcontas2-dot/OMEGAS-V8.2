package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max

class LearningSuggestionSimulationTest {
    private val policy = LearningTolerancePolicy()

    @Test
    fun `ten thousand local gasoline neighborhoods produce a usable reference`() {
        val random = Random(0x0A11CE)
        var accepted = 0
        var maxError = 0.0
        repeat(10_000) { scenario ->
            val rpm = 900.0 + random.nextDouble() * 3_600.0
            val map = 0.22 + random.nextDouble() * 0.70
            val water = 72.0 + random.nextDouble() * 24.0
            val petrol = 2.2 + random.nextDouble() * 8.5
            val rpmLimit = max(policy.historicalRpmMinimum, rpm * policy.historicalRpmPercent / 100.0)
            val local = PetrolReferenceSelector.Region(
                id = "local-$scenario",
                rpm = rpm + signed(random, rpmLimit * 0.35),
                mapBar = map + signed(random, policy.historicalMapBar * 0.35),
                waterC = water + signed(random, policy.historicalTemperatureC * 0.35),
                petrolMs = petrol + signed(random, 0.08),
                confidence = 0.70 + random.nextDouble() * 0.30,
                sampleCount = 3 + random.nextInt(8),
            )
            val distractors = (0 until 7).map { index ->
                PetrolReferenceSelector.Region(
                    id = "far-$scenario-$index",
                    rpm = rpm + (3.2 + random.nextDouble() * 5.0) * rpmLimit * if (index % 2 == 0) 1 else -1,
                    mapBar = (map + (3.2 + random.nextDouble() * 4.0) * policy.historicalMapBar * if (index % 3 == 0) 1 else -1).coerceAtLeast(0.05),
                    waterC = water + signed(random, 24.0),
                    petrolMs = (petrol + signed(random, 4.0)).coerceAtLeast(0.20),
                    confidence = 0.50 + random.nextDouble() * 0.50,
                    sampleCount = 1 + random.nextInt(12),
                )
            }
            val result = PetrolReferenceSelector.estimate(
                regions = listOf(local) + distractors,
                request = PetrolReferenceSelector.Request(rpm, map, water),
                policy = policy,
            )
            assertTrue("scenario=$scenario diagnostic=${result.toJson()}", result.available)
            assertEquals("LOCAL_REFERENCE_AVAILABLE", result.reasonCode)
            val error = abs((result.petrolTargetMs ?: error("missing target")) - petrol)
            maxError = max(maxError, error)
            assertTrue("scenario=$scenario error=$error diagnostic=${result.toJson()}", error <= 0.20)
            accepted++
        }
        println("SIM_REFERENCE_LOCAL scenarios=10000 accepted=$accepted maxErrorMs=$maxError")
        assertEquals(10_000, accepted)
    }

    @Test
    fun `five thousand distant gasoline sets cannot create a false equivalence`() {
        val random = Random(0xFACADE)
        var rejected = 0
        repeat(5_000) { scenario ->
            val rpm = 1_200.0 + random.nextDouble() * 3_000.0
            val map = 0.25 + random.nextDouble() * 0.60
            val water = 75.0 + random.nextDouble() * 18.0
            val rpmLimit = max(policy.historicalRpmMinimum, rpm * policy.historicalRpmPercent / 100.0)
            val regions = (0 until 8).map { index ->
                PetrolReferenceSelector.Region(
                    id = "distant-$scenario-$index",
                    rpm = rpm + (3.0 + random.nextDouble() * 6.0) * rpmLimit * if (index % 2 == 0) 1 else -1,
                    mapBar = (map + (3.0 + random.nextDouble() * 5.0) * policy.historicalMapBar * if (index % 3 == 0) 1 else -1).coerceAtLeast(0.05),
                    waterC = water + signed(random, 30.0),
                    petrolMs = 1.5 + random.nextDouble() * 10.0,
                    confidence = 0.4 + random.nextDouble() * 0.6,
                    sampleCount = 1 + random.nextInt(10),
                )
            }
            val result = PetrolReferenceSelector.estimate(
                regions,
                PetrolReferenceSelector.Request(rpm, map, water),
                policy,
            )
            assertFalse("scenario=$scenario diagnostic=${result.toJson()}", result.available)
            assertEquals("NO_LOCAL_PETROL_REFERENCE", result.reasonCode)
            rejected++
        }
        println("SIM_REFERENCE_FAR scenarios=5000 rejected=$rejected")
        assertEquals(5_000, rejected)
    }

    @Test
    fun `two thousand stable useful errors generate at least one manual suggestion`() {
        val random = Random(0x51A11)
        var scenariosWithSuggestion = 0
        var totalActionable = 0
        repeat(2_000) { scenario ->
            val target = 2.5 + random.nextDouble() * 7.0
            val errorRatio = (0.075 + random.nextDouble() * 0.065) * if (scenario % 2 == 0) 1.0 else -1.0
            val comparisons = JSONArray()
            val visits = 7 + random.nextInt(5)
            repeat(visits) { visit ->
                val jitter = signed(random, 0.004)
                comparisons.put(comparison(
                    id = "stable-$scenario-$visit",
                    visit = "visit-$scenario-$visit",
                    target = target + signed(random, 0.03),
                    observedRatio = errorRatio + jitter,
                    rpm = 1_500.0 + random.nextDouble() * 1_800.0,
                    map = 0.35 + random.nextDouble() * 0.30,
                    quality = 0.82 + random.nextDouble() * 0.17,
                ))
            }
            val result = AssistedCalibrationAdvisor.analyze(export(comparisons))
            val actionable = actionableCount(result.optJSONArray("kFactorSuggestions")) +
                actionableCount(result.optJSONArray("mapResidualSuggestions"))
            if (actionable > 0) scenariosWithSuggestion++
            totalActionable += actionable
            assertTrue(
                "scenario=$scenario ratio=$errorRatio visits=$visits result=$result",
                actionable > 0,
            )
        }
        println("SIM_ADVISOR_USEFUL scenarios=2000 withSuggestion=$scenariosWithSuggestion actionable=$totalActionable")
        assertEquals(2_000, scenariosWithSuggestion)
    }

    @Test
    fun `five thousand deadband scenarios never invent a correction`() {
        val random = Random(0xDEADBA)
        var falseSuggestions = 0
        repeat(5_000) { scenario ->
            val target = 2.2 + random.nextDouble() * 8.0
            val comparisons = JSONArray()
            repeat(8) { visit ->
                val ratio = signed(random, 0.018)
                comparisons.put(comparison(
                    id = "neutral-$scenario-$visit",
                    visit = "neutral-visit-$scenario-$visit",
                    target = target,
                    observedRatio = ratio,
                    rpm = 1_200.0 + random.nextDouble() * 2_000.0,
                    map = 0.30 + random.nextDouble() * 0.40,
                    quality = 0.85 + random.nextDouble() * 0.14,
                ))
            }
            val result = AssistedCalibrationAdvisor.analyze(export(comparisons))
            val actionable = actionableCount(result.optJSONArray("kFactorSuggestions")) +
                actionableCount(result.optJSONArray("mapResidualSuggestions"))
            falseSuggestions += actionable
            assertEquals("scenario=$scenario result=$result", 0, actionable)
        }
        println("SIM_ADVISOR_DEADBAND scenarios=5000 falseSuggestions=$falseSuggestions")
        assertEquals(0, falseSuggestions)
    }

    private fun export(comparisons: JSONArray): JSONObject = JSONObject()
        .put("epoch", 1)
        .put("comparisons", comparisons)

    private fun comparison(
        id: String,
        visit: String,
        target: Double,
        observedRatio: Double,
        rpm: Double,
        map: Double,
        quality: Double,
    ): JSONObject {
        val observed = target * (1.0 + observedRatio)
        return JSONObject()
            .put("id", id)
            .put("visit_id", visit)
            .put("epoch", 1)
            .put("petrol_target_ms", target)
            .put("petrol_on_cng_ms", observed)
            .put("rpm", rpm)
            .put("map_bar", map)
            .put("quality", quality)
    }

    private fun actionableCount(array: JSONArray?): Int {
        if (array == null) return 0
        var count = 0
        repeat(array.length()) { index ->
            if (array.optJSONObject(index)?.optBoolean("actionable", false) == true) count++
        }
        return count
    }

    private fun signed(random: Random, magnitude: Double): Double =
        (random.nextDouble() * 2.0 - 1.0) * magnitude
}