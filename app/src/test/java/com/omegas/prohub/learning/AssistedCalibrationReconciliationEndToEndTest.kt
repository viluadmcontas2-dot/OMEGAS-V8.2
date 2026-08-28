package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistedCalibrationReconciliationEndToEndTest {
    private val unknownTemperature = -273.15

    @Test
    fun `raw persisted regions become comparisons and actionable suggestions`() {
        val regions = JSONArray()
        repeat(28) { index ->
            val rpm = 1_250.0 + (index % 7) * 260.0
            val map = 0.34 + (index % 4) * 0.055
            val target = 3.1 + (index % 6) * 0.48
            regions.put(region(
                id = "petrol-$index",
                fuel = "PETROL",
                rpm = rpm,
                map = map,
                petrolMs = target,
                visits = listOf("petrol-visit-$index"),
            ))
        }
        repeat(34) { index ->
            val source = index % 28
            val rpm = 1_250.0 + (source % 7) * 260.0 + if (index % 2 == 0) 20.0 else -25.0
            val map = 0.34 + (source % 4) * 0.055 + if (index % 3 == 0) 0.008 else -0.006
            val target = 3.1 + (source % 6) * 0.48
            regions.put(region(
                id = "cng-$index",
                fuel = "CNG",
                rpm = rpm,
                map = map,
                petrolMs = target * 1.12,
                visits = listOf("cng-visit-$index-a", "cng-visit-$index-b"),
                epoch = 1,
            ))
        }

        val snapshot = JSONObject()
            .put("epoch", 1)
            .put("regions", regions)
            .put("comparisons", JSONArray())

        val advice = AssistedCalibrationAdvisor.analyze(snapshot)
        val reconciliation = advice.getJSONObject("reconciliation")

        assertEquals(28, reconciliation.getInt("petrol_regions"))
        assertEquals(34, reconciliation.getInt("active_cng_regions"))
        assertEquals(68, advice.getInt("comparisonCount"))
        assertEquals(68, advice.getInt("uniqueVisitCount"))
        assertTrue(actionableCount(advice.getJSONArray("kFactorSuggestions")) > 0)
        assertTrue(
            actionableCount(advice.getJSONArray("kFactorSuggestions")) +
                actionableCount(advice.getJSONArray("mapResidualSuggestions")) > 0,
        )
    }

    @Test
    fun `fuel arrival order does not change advisor result`() {
        val petrol = region("p", "PETROL", 1_800.0, 0.48, 4.0, listOf("p-v"))
        val cng = region("g", "CNG", 1_830.0, 0.49, 4.6, listOf("g-v"), epoch = 1)

        val petrolFirst = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("epoch", 1).put("regions", JSONArray().put(petrol).put(cng)),
        )
        val cngFirst = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("epoch", 1).put("regions", JSONArray().put(cng).put(petrol)),
        )

        assertEquals(1, petrolFirst.getInt("comparisonCount"))
        assertEquals(1, cngFirst.getInt("comparisonCount"))
        assertEquals(
            petrolFirst.getJSONArray("kFactorSuggestions").toString(),
            cngFirst.getJSONArray("kFactorSuggestions").toString(),
        )
    }

    @Test
    fun `far rpm and map remain pending and never invent suggestion`() {
        val snapshot = JSONObject()
            .put("epoch", 1)
            .put("regions", JSONArray()
                .put(region("p", "PETROL", 900.0, 0.20, 4.0, listOf("p-v")))
                .put(region("g", "CNG", 3_200.0, 0.92, 5.0, listOf("g-v"), epoch = 1)))

        val advice = AssistedCalibrationAdvisor.analyze(snapshot)
        val reconciliation = advice.getJSONObject("reconciliation")

        assertEquals(0, advice.getInt("comparisonCount"))
        assertEquals(1, reconciliation.getInt("pending_cng_visits"))
        assertEquals(0, actionableCount(advice.getJSONArray("kFactorSuggestions")))
        assertEquals(0, actionableCount(advice.getJSONArray("mapResidualSuggestions")))
    }

    private fun region(
        id: String,
        fuel: String,
        rpm: Double,
        map: Double,
        petrolMs: Double,
        visits: List<String>,
        epoch: Int = if (fuel == "PETROL") 0 else 1,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("epoch", epoch)
        .put("rpm", rpm)
        .put("map_bar", map)
        .put("water_c", unknownTemperature)
        .put("gas_c", unknownTemperature)
        .put("petrol_ms", petrolMs)
        .put("quality", 0.90)
        .put("confidence", 0.90)
        .put("samples", 8)
        .put("visits", JSONArray(visits))
        .put("updated_at", 1_000L)

    private fun actionableCount(array: JSONArray): Int =
        (0 until array.length()).count { array.optJSONObject(it)?.optBoolean("actionable") == true }
}
