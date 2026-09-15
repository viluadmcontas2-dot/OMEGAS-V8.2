package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePetrolReferenceRefreshTest {
    @Test
    fun `later physical gasoline evidence replaces persisted adaptive reference for same visit`() {
        val snapshot = JSONObject()
            .put("epoch", 1)
            .put("regions", baseRegions())
            .put("comparisons", JSONArray())

        val first = LearningSnapshotReconciler.reconcile(snapshot)
        val firstComparisons = first.getJSONArray("comparisons")
        assertEquals(1, firstComparisons.length())
        assertEquals("PRIOR_PLUS_RESIDUAL", firstComparisons.getJSONObject(0).getString("reference_stage"))

        first.getJSONArray("regions").put(
            region(
                id = "p-direct",
                fuel = "PETROL",
                epoch = 0,
                rpm = 2_500.0,
                map = 0.70,
                petrolMs = 6.60,
                quality = 0.98,
                samples = 80,
            ),
        )

        val refreshed = LearningSnapshotReconciler.reconcile(first)
        val comparisons = refreshed.getJSONArray("comparisons")

        assertEquals("A mesma visita não pode ganhar voto duplicado", 1, comparisons.length())
        val comparison = comparisons.getJSONObject(0)
        assertEquals("LOCAL_REFERENCE_AVAILABLE", comparison.getString("reference_reason_code"))
        assertTrue(comparison.getString("reference_stage") != "PRIOR_PLUS_RESIDUAL")
        assertEquals("p-direct", comparison.getString("reference_region_id"))
        assertEquals(6.60, comparison.getDouble("petrol_target_ms"), 0.000001)
        assertEquals(false, comparison.getBoolean("reference_extrapolated"))
        assertEquals(true, comparison.getBoolean("reference_is_direct_evidence"))
    }

    private fun baseRegions(): JSONArray = JSONArray()
        .put(region("p-1", "PETROL", 0, 1_000.0, 0.30, 3.8, 0.92, 18))
        .put(region("p-2", "PETROL", 0, 1_200.0, 0.35, 4.2, 0.91, 20))
        .put(region("p-3", "PETROL", 0, 1_400.0, 0.40, 4.6, 0.93, 22))
        .put(region("p-4", "PETROL", 0, 1_600.0, 0.45, 5.0, 0.90, 24))
        .put(region("p-5", "PETROL", 0, 1_800.0, 0.50, 5.4, 0.89, 26))
        .put(region("p-6", "PETROL", 0, 2_000.0, 0.55, 5.8, 0.90, 28))
        .put(
            region("g-gap", "CNG", 1, 2_500.0, 0.70, 8.0, 0.86, 16)
                .put("visits", JSONArray().put("gap-visit")),
        )

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
}
