package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSnapshotReconcilerTest {
    @Test
    fun `screenshot scale snapshot produces retroactive comparisons and suggestions`() {
        val regions = JSONArray()
        repeat(28) { index ->
            val rpm = 1_350.0 + (index % 4) * 550.0
            val map = 0.34 + (index % 5) * 0.055
            val petrolMs = 3.5 + (index % 7) * 0.48
            regions.put(region("p-$index", "PETROL", 0, rpm, map, petrolMs, 0.88, 12 + index))
        }
        repeat(34) { index ->
            val rpm = 1_370.0 + (index % 4) * 550.0
            val map = 0.35 + (index % 5) * 0.055
            val petrolMs = 3.85 + (index % 7) * 0.48
            val visits = JSONArray().apply {
                repeat(2) { visit -> put("g-$index-v$visit") }
            }
            regions.put(
                region("g-$index", "CNG", 1, rpm, map, petrolMs, 0.84, 10 + index)
                    .put("visits", visits),
            )
        }

        val reconciled = LearningSnapshotReconciler.reconcile(
            JSONObject()
                .put("epoch", 1)
                .put("regions", regions)
                .put("comparisons", JSONArray()),
        )

        val comparisons = reconciled.getJSONArray("comparisons")
        val diagnostic = reconciled.getJSONObject("reconciliation")
        assertEquals(28, diagnostic.getInt("petrol_regions"))
        assertEquals(34, diagnostic.getInt("active_cng_regions"))
        assertTrue("diagnostic=$diagnostic", comparisons.length() > 0)
        assertEquals(0, diagnostic.getInt("pending_cng_visits"))

        val advice = AssistedCalibrationAdvisor.analyze(reconciled)
        val global = advice.optJSONArray("kFactorSuggestions") ?: JSONArray()
        val local = advice.optJSONArray("mapResidualSuggestions") ?: JSONArray()
        assertTrue(
            "comparisons=${comparisons.length()} advice=$advice",
            actionable(global) + actionable(local) > 0,
        )
    }

    @Test
    fun `unknown temperature stays neutral but far rpm and map remain rejected`() {
        val near = JSONObject()
            .put("epoch", 1)
            .put("regions", JSONArray()
                .put(region("p", "PETROL", 0, 1_500.0, 0.50, 4.0, 0.9, 20))
                .put(region("g", "CNG", 1, 1_610.0, 0.56, 4.4, 0.9, 20)))
            .put("comparisons", JSONArray())
        val nearResult = LearningSnapshotReconciler.reconcile(near)
        assertEquals(1, nearResult.getJSONArray("comparisons").length())

        val far = JSONObject()
            .put("epoch", 1)
            .put("regions", JSONArray()
                .put(region("p", "PETROL", 0, 900.0, 0.20, 4.0, 0.9, 20))
                .put(region("g", "CNG", 1, 3_000.0, 0.90, 4.4, 0.9, 20)))
            .put("comparisons", JSONArray())
        val farResult = LearningSnapshotReconciler.reconcile(far)
        assertEquals(0, farResult.getJSONArray("comparisons").length())
        assertEquals(1, farResult.getJSONObject("reconciliation").getInt("pending_cng_visits"))
    }

    @Test
    fun `adaptive reference closes a spatial gap without manufacturing petrol evidence`() {
        val regions = JSONArray()
            .put(region("p-1", "PETROL", 0, 1_000.0, 0.30, 3.8, 0.92, 18))
            .put(region("p-2", "PETROL", 0, 1_200.0, 0.35, 4.2, 0.91, 20))
            .put(region("p-3", "PETROL", 0, 1_400.0, 0.40, 4.6, 0.93, 22))
            .put(region("p-4", "PETROL", 0, 1_600.0, 0.45, 5.0, 0.90, 24))
            .put(region("p-5", "PETROL", 0, 1_800.0, 0.50, 5.4, 0.89, 26))
            .put(region("p-6", "PETROL", 0, 2_000.0, 0.55, 5.8, 0.90, 28))
            .put(region("g-gap", "CNG", 1, 2_500.0, 0.70, 8.0, 0.86, 16)
                .put("visits", JSONArray().put("gap-visit")))
        val beforeSamples = (0 until 6).map { regions.getJSONObject(it).getInt("samples") }

        val reconciled = LearningSnapshotReconciler.reconcile(
            JSONObject()
                .put("epoch", 1)
                .put("regions", regions)
                .put("comparisons", JSONArray()),
        )

        val comparisons = reconciled.getJSONArray("comparisons")
        assertEquals("A superfície adaptativa deve produzir referência mesmo sem par discreto", 1, comparisons.length())
        val comparison = comparisons.getJSONObject(0)
        assertEquals("PRIOR_PLUS_RESIDUAL", comparison.getString("reference_stage"))
        assertTrue(comparison.getDouble("petrol_target_ms") > 0.05)
        assertTrue(comparison.getDouble("quality") in 0.0..1.0)
        assertEquals(1, reconciled.getJSONObject("reconciliation").getInt("adaptive_references"))

        val afterRegions = reconciled.getJSONArray("regions")
        repeat(6) { index ->
            assertEquals(beforeSamples[index], afterRegions.getJSONObject(index).getInt("samples"))
        }
    }

    @Test
    fun `reconciliation is idempotent and does not duplicate visits`() {
        val snapshot = JSONObject()
            .put("epoch", 1)
            .put("regions", JSONArray()
                .put(region("p", "PETROL", 0, 1_500.0, 0.50, 4.0, 0.9, 20))
                .put(region("g", "CNG", 1, 1_520.0, 0.51, 4.5, 0.9, 20)
                    .put("visits", JSONArray().put("visit-a").put("visit-b"))))
            .put("comparisons", JSONArray())

        val first = LearningSnapshotReconciler.reconcile(snapshot)
        val second = LearningSnapshotReconciler.reconcile(first)
        assertEquals(2, first.getJSONArray("comparisons").length())
        assertEquals(2, second.getJSONArray("comparisons").length())
    }

    private fun region(
        id: String,
        fuel: String,
        epoch: Int,
        rpm: Double,
        map: Double,
        petrolMs: Double,
        quality: Double,
        samples: Int,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("epoch", epoch)
        .put("rpm", rpm)
        .put("map_bar", map)
        .put("petrol_ms", petrolMs)
        .put("water_c", -273.15)
        .put("gas_c", -273.15)
        .put("quality", quality)
        .put("confidence", quality)
        .put("samples", samples)
        .put("visits", JSONArray().put("$id-visit"))

    private fun actionable(array: JSONArray): Int {
        var count = 0
        repeat(array.length()) { index ->
            if (array.optJSONObject(index)?.optBoolean("actionable", false) == true) count++
        }
        return count
    }
}
