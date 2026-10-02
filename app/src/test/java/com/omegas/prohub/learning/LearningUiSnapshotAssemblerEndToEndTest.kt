package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningUiSnapshotAssemblerEndToEndTest {
    @Test
    fun `ui payload contains aligned cells comparisons diagnostics and suggestions`() {
        val raw = scenario()

        val first = LearningUiSnapshotAssembler.assemble(raw)
        val second = LearningUiSnapshotAssembler.assemble(first)

        val comparisons = first.getJSONArray("comparisons")
        val advice = first.getJSONObject("assistedCalibration")
        val reconciliation = first.getJSONObject("reconciliation")
        val integrity = first.getJSONObject("integrity")

        assertTrue(first.getJSONArray("cells").length() > 0)
        assertTrue(comparisons.length() > 0)
        assertEquals(comparisons.length(), first.getInt("comparisonCount"))
        assertEquals(comparisons.length(), advice.getInt("comparisonCount"))
        assertEquals(0, reconciliation.getInt("pending_cng_visits"))
        assertTrue(integrity.getBoolean("ok"))
        assertTrue(actionable(advice) > 0)
        assertEquals(comparisons.length(), second.getJSONArray("comparisons").length())
        assertEquals("PERSISTED_REGIONS_RECONCILED_ADVISOR", first.getString("uiPipeline"))
    }

    @Test
    fun `ui payload exposes why distant gnv remains pending`() {
        val raw = JSONObject()
            .put("epoch", 1)
            .put("regions", JSONArray()
                .put(region("p", "PETROL", 900.0, 0.20, 3.8, "pv", 0))
                .put(region("g", "CNG", 3_200.0, 0.95, 5.1, "gv", 1)))

        val payload = LearningUiSnapshotAssembler.assemble(raw)
        val reconciliation = payload.getJSONObject("reconciliation")

        assertEquals(0, payload.getJSONArray("comparisons").length())
        assertEquals(1, reconciliation.getInt("pending_cng_visits"))
        assertTrue(
            reconciliation.getJSONObject("rejection_reasons")
                .getInt("NO_LOCAL_PETROL_REFERENCE") > 0,
        )
        assertEquals(0, actionable(payload.getJSONObject("assistedCalibration")))
    }

    private fun scenario(): JSONObject {
        val regions = JSONArray()
        repeat(12) { index ->
            val rpm = 1_300.0 + (index % 6) * 300.0
            val map = 0.36 + (index % 3) * 0.07
            val target = 3.2 + (index % 5) * 0.55
            regions.put(region("p$index", "PETROL", rpm, map, target, "pv$index", 0))
            repeat(3) { visit ->
                regions.put(region(
                    "g$index-$visit",
                    "CNG",
                    rpm + 20.0 - visit * 10.0,
                    map + 0.006 - visit * 0.003,
                    target * 1.13,
                    "gv$index-$visit",
                    1,
                ))
            }
        }
        return JSONObject()
            .put("epoch", 1)
            .put("mapHash", "map-test")
            .put("regions", regions)
            .put("comparisons", JSONArray())
    }

    private fun region(
        id: String,
        fuel: String,
        rpm: Double,
        map: Double,
        petrolMs: Double,
        visit: String,
        epoch: Int,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("epoch", epoch)
        .put("rpm", rpm)
        .put("map_bar", map)
        .put("water_c", -273.15)
        .put("gas_c", -273.15)
        .put("petrol_ms", petrolMs)
        .put("quality", 0.92)
        .put("confidence", 0.92)
        .put("samples", 10)
        .put("visits", JSONArray().put(visit))
        .put("updated_at", 1_000L)

    private fun actionable(advice: JSONObject): Int =
        listOf("kFactorSuggestions", "mapResidualSuggestions").sumOf { key ->
            val array = advice.getJSONArray(key)
            (0 until array.length()).count { array.optJSONObject(it)?.optBoolean("actionable") == true }
        }
}
