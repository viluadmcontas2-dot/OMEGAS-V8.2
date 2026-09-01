package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Prova a amarração completa do aprendizado persistido:
 * regiões -> reconciliação -> comparações -> equivalência/tendência -> sugestões manuais.
 *
 * Não acessa USB, ECU ou writer real.
 */
class LearningEndToEndPipelineTest {
    @Test
    fun `coleta persistida gera comparacoes e sugestao global coerente`() {
        val snapshot = snapshot(
            epoch = 7,
            petrolMs = 5.0,
            cngObservedPetrolMs = 6.0,
            currentVisits = 24,
        )

        val result = AssistedCalibrationAdvisor.analyze(snapshot)

        assertEquals(24, result.getInt("comparisonCount"))
        assertEquals(24, result.getInt("uniqueVisitCount"))
        assertEquals(24, result.getJSONObject("reconciliation").getInt("reconciled_comparisons"))

        val point = closestKPoint(result, 5.0)
        assertTrue(point.getBoolean("actionable"))
        assertEquals("INCREASE_CNG_DELIVERY", point.getString("direction"))
        assertTrue(point.getDouble("suggestedDeltaPercent") > 0.0)
        assertFalse(point.getBoolean("automatic"))
        assertTrue(point.getBoolean("humanConfirmationRequired"))
        assertFalse(result.getJSONObject("method").getBoolean("automaticWrite"))
    }

    @Test
    fun `coleta equivalente preenche comparacoes sem inventar correcao`() {
        val result = AssistedCalibrationAdvisor.analyze(
            snapshot(
                epoch = 4,
                petrolMs = 5.0,
                cngObservedPetrolMs = 5.0,
                currentVisits = 24,
            ),
        )

        assertEquals(24, result.getInt("comparisonCount"))
        assertFalse(anyActionable(result.getJSONArray("kFactorSuggestions")))
        assertFalse(anyActionable(result.getJSONArray("mapResidualSuggestions")))
        assertEquals(0, result.getJSONArray("mapCorrectionRegions").length())
    }

    @Test
    fun `reconciliacao repetida nao duplica comparacoes nem muda sugestao`() {
        val original = snapshot(
            epoch = 5,
            petrolMs = 4.5,
            cngObservedPetrolMs = 5.2,
            currentVisits = 18,
        )
        val once = LearningSnapshotReconciler.reconcile(original)
        val twice = LearningSnapshotReconciler.reconcile(once)

        assertEquals(18, once.getJSONArray("comparisons").length())
        assertEquals(18, twice.getJSONArray("comparisons").length())
        assertEquals(0, twice.getJSONObject("reconciliation").getInt("reconciled_comparisons"))
        assertEquals(18, twice.getJSONObject("reconciliation").getInt("preserved_existing_comparisons"))

        val firstDecision = closestKPoint(AssistedCalibrationAdvisor.analyze(once), 4.5)
        val secondDecision = closestKPoint(AssistedCalibrationAdvisor.analyze(twice), 4.5)
        assertEquals(firstDecision.getString("direction"), secondDecision.getString("direction"))
        assertEquals(firstDecision.getBoolean("actionable"), secondDecision.getBoolean("actionable"))
        assertEquals(
            firstDecision.getDouble("suggestedDeltaPercent"),
            secondDecision.getDouble("suggestedDeltaPercent"),
            1e-9,
        )
    }

    @Test
    fun `epoca antiga permanece historica e nao contamina sugestao atual`() {
        val regions = JSONArray()
            .put(region("petrol", "PETROL", 2_200.0, 0.55, 5.0, epoch = 1, visits = 1))
            .put(region("old-cng", "CNG", 2_200.0, 0.55, 8.0, epoch = 2, visits = 30))
            .put(region("current-cng", "CNG", 2_200.0, 0.55, 5.5, epoch = 3, visits = 12))
        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("epoch", 3).put("regions", regions),
        )

        assertEquals(12, result.getInt("comparisonCount"))
        assertEquals(12, result.getInt("uniqueVisitCount"))
        val reconciliation = result.getJSONObject("reconciliation")
        assertEquals(1, reconciliation.getInt("active_cng_regions"))
        assertEquals(12, reconciliation.getInt("reconciled_comparisons"))

        val point = closestKPoint(result, 5.0)
        assertTrue(point.getDouble("errorPercent") < 20.0)
        assertEquals("INCREASE_CNG_DELIVERY", point.getString("direction"))
    }

    @Test
    fun `preenchimento continuo conserva peso e amarra celulas da comparacao`() {
        val reconciled = LearningSnapshotReconciler.reconcile(
            snapshot(
                epoch = 9,
                petrolMs = 5.0,
                cngObservedPetrolMs = 5.7,
                currentVisits = 1,
                rpm = 2_350.0,
                mapBar = 0.575,
            ),
        )
        val comparison = reconciled.getJSONArray("comparisons").getJSONObject(0)
        val weights = comparison.getJSONArray("continuous_cell_weights")

        assertTrue(weights.length() in 1..4)
        var totalWeight = 0.0
        repeat(weights.length()) { index ->
            val item = weights.getJSONObject(index)
            assertTrue(item.getInt("row") in 0..11)
            assertTrue(item.getInt("column") in 0..11)
            assertTrue(item.getDouble("weight") > 0.0)
            totalWeight += item.getDouble("weight")
        }
        assertEquals(1.0, totalWeight, 1e-9)
        assertTrue(comparison.getInt("cng_cell_row") in 0..11)
        assertTrue(comparison.getInt("cng_cell_column") in 0..11)
        assertTrue(comparison.getInt("reference_cell_row") in 0..11)
        assertTrue(comparison.getInt("reference_cell_column") in 0..11)
    }

    @Test
    fun `snapshot cru e snapshot reconciliado produzem a mesma decisao`() {
        val raw = snapshot(
            epoch = 6,
            petrolMs = 6.0,
            cngObservedPetrolMs = 5.25,
            currentVisits = 20,
        )
        val reconciled = LearningSnapshotReconciler.reconcile(raw)
        val fromRaw = AssistedCalibrationAdvisor.analyze(raw)
        val fromReconciled = AssistedCalibrationAdvisor.analyze(reconciled)

        assertEquals(fromRaw.getInt("comparisonCount"), fromReconciled.getInt("comparisonCount"))
        val rawPoint = closestKPoint(fromRaw, 6.0)
        val reconciledPoint = closestKPoint(fromReconciled, 6.0)
        assertEquals("DECREASE_CNG_DELIVERY", rawPoint.getString("direction"))
        assertEquals(rawPoint.getString("direction"), reconciledPoint.getString("direction"))
        assertEquals(rawPoint.getBoolean("actionable"), reconciledPoint.getBoolean("actionable"))
        assertEquals(
            rawPoint.getDouble("suggestedDeltaPercent"),
            reconciledPoint.getDouble("suggestedDeltaPercent"),
            1e-9,
        )
    }

    private fun snapshot(
        epoch: Int,
        petrolMs: Double,
        cngObservedPetrolMs: Double,
        currentVisits: Int,
        rpm: Double = 2_200.0,
        mapBar: Double = 0.55,
    ): JSONObject {
        val firstVisits = (currentVisits + 1) / 2
        val secondVisits = currentVisits - firstVisits
        val regions = JSONArray()
            .put(region("petrol-reference-a", "PETROL", rpm, mapBar, petrolMs, epoch = 1, visits = 1))
            .put(region("current-cng-a", "CNG", rpm, mapBar, cngObservedPetrolMs, epoch, firstVisits))
        if (secondVisits > 0) {
            val secondRpm = rpm + 700.0
            val secondMap = if (mapBar <= 0.75) mapBar + 0.18 else mapBar - 0.18
            regions
                .put(region("petrol-reference-b", "PETROL", secondRpm, secondMap, petrolMs, epoch = 1, visits = 1))
                .put(region("current-cng-b", "CNG", secondRpm, secondMap, cngObservedPetrolMs, epoch, secondVisits))
        }
        return JSONObject().put("epoch", epoch).put("regions", regions)
    }

    private fun region(
        id: String,
        fuel: String,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
        epoch: Int,
        visits: Int,
    ): JSONObject {
        val visitIds = JSONArray()
        repeat(visits) { visitIds.put("$id-visit-$it") }
        return JSONObject()
            .put("id", id)
            .put("fuel", fuel)
            .put("rpm", rpm)
            .put("map_bar", mapBar)
            .put("petrol_ms", petrolMs)
            .put("water_c", 90.0)
            .put("gas_c", 35.0)
            .put("pressure_diff_bar", 1.2)
            .put("confidence", 1.0)
            .put("quality", 1.0)
            .put("samples", 120)
            .put("epoch", epoch)
            .put("visits", visitIds)
    }

    private fun closestKPoint(result: JSONObject, petrolMs: Double): JSONObject {
        val suggestions = result.getJSONArray("kFactorSuggestions")
        return (0 until suggestions.length())
            .map { suggestions.getJSONObject(it) }
            .minBy { abs(it.getDouble("petrolMs") - petrolMs) }
    }

    private fun anyActionable(items: JSONArray): Boolean =
        (0 until items.length()).any { items.getJSONObject(it).optBoolean("actionable") }
}
