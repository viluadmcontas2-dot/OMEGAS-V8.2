package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Software-journey regressions for issue #25.
 *
 * Exercises the real BlueCalibrationCoordinator from confirmed calibration
 * snapshot -> Learning evidence ingestion -> equivalent pair -> measured
 * comparison -> presentation payload. Hardware managers are inert placeholders;
 * these paths perform no Android/USB I/O.
 */
class BlueEvidenceJourneyTest {
    @Test
    fun `gasoline 4_00 and gnv 4_40 becomes visible ten percent measured deviation`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol-a", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p1"),
            region("gnv-a", "GNV", 1500.0, 0.50, 4.40, 0.95, "g1"),
        ))
        assertEquals(1, result.getInt("activeComparisons"))
        val comparison = result.getJSONArray("comparisons").getJSONObject(0)
        assertEquals(4.00, comparison.getDouble("petrolReferenceMs"), 1e-9)
        assertEquals(4.40, comparison.getDouble("petrolOnCngMs"), 1e-9)
        assertEquals(10.0, comparison.getDouble("errorPercent"), 1e-9)
        assertTrue(comparison.getString("cellKey").isNotBlank())
        // Measurement stays continuous at 4.40 ms; physical Map K addressing
        // correctly snaps that observation to the immutable nearest bin, 4.5 ms.
        assertEquals(4.50, comparison.getJSONObject("mapKCell").getDouble("petrolBin"), 1e-9)
        val proposal = result.getJSONObject("proposal")
        assertTrue(proposal.getBoolean("evidenceAvailable"))
        assertFalse(proposal.getBoolean("available"))
        assertEquals("MEASURE_ACTUATOR_GAIN", proposal.getString("state"))
        assertFalse(result.getBoolean("automaticWrite"))
    }

    @Test
    fun `negative ten percent deviation survives full journey`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 3.60, 0.95, "g"),
        ))
        assertEquals(-10.0, latest(result).getDouble("errorPercent"), 1e-9)
    }

    @Test
    fun `zero deviation is preserved as measured evidence`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 4.00, 0.95, "g"),
        ))
        assertEquals(1, result.getInt("activeComparisons"))
        assertEquals(0.0, latest(result).getDouble("errorPercent"), 1e-9)
        assertEquals("MEASURED_WITHIN_ACTION_DEADBAND", result.getJSONObject("proposal").getString("state"))
    }

    @Test
    fun `one percent deviation remains visible but is not actionable`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 4.04, 0.95, "g"),
        ))
        assertEquals(1.0, latest(result).getDouble("errorPercent"), 1e-9)
        val proposal = result.getJSONObject("proposal")
        assertFalse(proposal.getBoolean("actionableDeviation"))
        assertFalse(proposal.getBoolean("available"))
        assertEquals("MEASURED_WITHIN_ACTION_DEADBAND", proposal.getString("state"))
    }

    @Test
    fun `three percent deviation is measured while causal target stays unavailable`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 4.12, 0.95, "g"),
        ))
        assertEquals(3.0, latest(result).getDouble("errorPercent"), 1e-9)
        val proposal = result.getJSONObject("proposal")
        assertTrue(proposal.getBoolean("actionableDeviation"))
        assertFalse(proposal.getBoolean("available"))
        assertFalse(proposal.has("correctionMultiplier"))
        assertEquals("MEASURE_ACTUATOR_GAIN", proposal.getString("state"))
    }

    @Test
    fun `petrol without gnv never fabricates a comparison`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
        ))
        assertEquals(0, result.getInt("activeComparisons"))
        assertEquals(1, result.getInt("petrolEvidence"))
        assertEquals(0, result.getInt("activeCngEvidence"))
    }

    @Test
    fun `gnv without petrol never fabricates a reference`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("gnv", "GNV", 1500.0, 0.50, 4.40, 0.95, "g"),
        ))
        assertEquals(0, result.getInt("activeComparisons"))
        assertEquals(0, result.getInt("petrolEvidence"))
        assertEquals(1, result.getInt("activeCngEvidence"))
    }

    @Test
    fun `far rpm evidence does not become a false equivalent pair`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 900.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 2200.0, 0.50, 4.40, 0.95, "g"),
        ))
        assertEquals(0, result.getInt("activeComparisons"))
    }

    @Test
    fun `far map evidence does not become a false equivalent pair`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.30, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.80, 4.40, 0.95, "g"),
        ))
        assertEquals(0, result.getInt("activeComparisons"))
    }

    @Test
    fun `low quality gasoline cannot become reference`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.20, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 4.40, 0.95, "g"),
        ))
        assertEquals(0, result.getInt("activeComparisons"))
    }

    @Test
    fun `same region visit refreshes evidence and measured deviation instead of freezing first sample`() {
        val coordinator = readyCoordinator()
        val first = coordinator.ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv", "GNV", 1500.0, 0.50, 4.40, 0.70, "g", updatedAt = 1_000L),
        ))
        assertEquals(10.0, latest(first).getDouble("errorPercent"), 1e-9)
        val second = coordinator.ingestLearningSnapshot(snapshot(
            region("gnv", "GNV", 1500.0, 0.50, 4.20, 0.98, "g", updatedAt = 2_000L),
        ))
        assertEquals(1, second.getInt("activeCngEvidence"))
        assertEquals(5.0, latest(second).getDouble("errorPercent"), 1e-9)
    }

    @Test
    fun `gnv evidence from another epoch is ignored`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("petrol", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p"),
            region("gnv-old", "GNV", 1500.0, 0.50, 4.40, 0.95, "g", epoch = 2),
            epoch = 3,
        ))
        assertEquals(0, result.getInt("activeCngEvidence"))
        assertEquals(0, result.getInt("activeComparisons"))
    }

    @Test
    fun `calibration snapshots must be confirmed and from the same session`() {
        val coordinator = newCoordinator()
        val unconfirmedMap = mapSnapshot(7L).put("sessionConfirmed", false)
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.synchronizeFromConfirmedSnapshot(unconfirmedMap, curveSnapshot(7L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.synchronizeFromConfirmedSnapshot(mapSnapshot(7L), curveSnapshot(8L))
        }
        val accepted = coordinator.synchronizeFromConfirmedSnapshot(mapSnapshot(9L), curveSnapshot(9L))
        assertTrue(accepted.getBoolean("ready"))
        assertEquals("CONFIRMED_SESSION_CACHE", accepted.getString("source"))
    }

    @Test
    fun `multiple equivalent gasoline visits produce deterministic median reference`() {
        val result = readyCoordinator().ingestLearningSnapshot(snapshot(
            region("p1", "PETROL", 1500.0, 0.50, 3.90, 0.95, "p1"),
            region("p2", "PETROL", 1500.0, 0.50, 4.00, 0.95, "p2"),
            region("p3", "PETROL", 1500.0, 0.50, 4.10, 0.95, "p3"),
            region("g", "GNV", 1500.0, 0.50, 4.40, 0.95, "g"),
        ))
        assertEquals(4.00, latest(result).getDouble("petrolReferenceMs"), 1e-9)
        assertEquals(10.0, latest(result).getDouble("errorPercent"), 1e-9)
    }

    private fun latest(result: JSONObject): JSONObject {
        val comparison = result.optJSONObject("latestComparison")
        assertNotNull("journey must publish latestComparison", comparison)
        return comparison!!
    }

    private fun readyCoordinator(sessionId: Long = 42L): BlueCalibrationCoordinator =
        newCoordinator().also { it.synchronizeFromConfirmedSnapshot(mapSnapshot(sessionId), curveSnapshot(sessionId)) }

    private fun newCoordinator(): BlueCalibrationCoordinator {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val mapManager = allocateInstance.invoke(unsafe, KWriteManager::class.java) as KWriteManager
        val factorManager = allocateInstance.invoke(unsafe, KFactorManager::class.java) as KFactorManager
        return BlueCalibrationCoordinator(mapManager, factorManager)
    }

    private fun mapSnapshot(sessionId: Long): JSONObject {
        val rows = JSONArray()
        repeat(CalibrationShape.MAP_K_STORAGE_ROWS) {
            val row = JSONArray()
            repeat(CalibrationShape.MAP_K_COLUMNS) { row.put(100) }
            rows.put(row)
        }
        return JSONObject()
            .put("complete", true)
            .put("sessionConfirmed", true)
            .put("sessionId", sessionId)
            .put("allRows", rows)
    }

    private fun curveSnapshot(sessionId: Long): JSONObject {
        val raw = JSONArray()
        repeat(CalibrationShape.CURVE_K_POINTS) { raw.put(16_384) }
        return JSONObject()
            .put("complete", true)
            .put("sessionConfirmed", true)
            .put("sessionId", sessionId)
            .put("factorsRaw", raw)
    }

    private fun snapshot(vararg regions: JSONObject, epoch: Int = 1): JSONObject = JSONObject()
        .put("epoch", epoch)
        .put("regions", JSONArray().apply { regions.forEach(::put) })

    private fun region(
        id: String,
        fuel: String,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
        quality: Double,
        visitId: String,
        epoch: Int = 1,
        updatedAt: Long = 1_000L,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("epoch", epoch)
        .put("visits", JSONArray().put(visitId))
        .put("rpm", rpm)
        .put("map_bar", mapBar)
        .put("petrol_ms", petrolMs)
        .put("quality", quality)
        .put("updated_at", updatedAt)
}