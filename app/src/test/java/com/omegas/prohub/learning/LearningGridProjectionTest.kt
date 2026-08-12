package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningGridProjectionTest {
    @Test
    fun `physical grid has 144 cells and protocol keeps the special thirteenth row`() {
        val grid = LearningGridProjection.gridJson()
        assertEquals(12, grid.getInt("rows"))
        assertEquals(12, grid.getInt("columns"))
        assertEquals(144, grid.getInt("physicalCells"))
        assertEquals(13, grid.getInt("protocolRows"))
        assertEquals(12, grid.getInt("protocolColumns"))
        assertEquals("0C", grid.getString("specialRow"))
        assertEquals("mp48-k-map-physical-axes-v1", grid.getString("axisSchema"))
        assertEquals("0cc7273171fbe47a8d28235be00f1af49889d0934f6fb3c73fca35ccd2fee7c7", grid.getString("lockSha256"))
        assertTrue(grid.getBoolean("immutablePhysicalContract"))
        assertEquals(156, grid.getInt("protocolRows") * grid.getInt("protocolColumns"))
    }

    @Test
    fun `cell location uses rpm and original petrol injection axes`() {
        val cell = LearningGridProjection.cellFor(rpm = 2_500.0, petrolMs = 4.0)
        assertEquals(3, cell.getInt("row"))
        assertEquals(3, cell.getInt("column"))
        assertEquals("3:3", cell.getString("key"))
        assertEquals(3.5, cell.getDouble("petrolBin"), 0.0001)
        assertEquals(2_500, cell.getInt("rpmBin"))
    }

    @Test
    fun `live tracing exposes the real four bilinear contributors without changing calibration`() {
        val trace = LearningGridProjection.liveInterpolationJson(
            rpm = 1_600.0,
            petrolMs = 4.0,
            mapBar = 0.60,
            sequence = 42L,
            updatedAt = 1_000L,
            telemetryValid = true,
        )
        val weights = trace.getJSONObject("cell").getJSONArray("continuousWeights")
        val total = (0 until weights.length()).sumOf { weights.getJSONObject(it).getDouble("weight") }

        assertTrue(trace.getBoolean("valid"))
        assertEquals("BILINEAR_RPM_X_PETROL_MS", trace.getString("method"))
        assertEquals(4, weights.length())
        assertEquals(1.0, total, 0.0000001)
        assertEquals(1.0, trace.getDouble("totalWeight"), 0.0000001)
        assertFalse(trace.getBoolean("affectsLearning"))
        assertFalse(trace.getBoolean("affectsCalibration"))
    }

    @Test
    fun `gasoline and cng evidence remain separated by fuel and k epoch`() {
        val regions = JSONArray()
            .put(region("p", "GASOLINA", 0, 3.5, 2_500.0))
            .put(region("g1", "GNV", 1, 3.5, 2_500.0))
            .put(region("g2", "GNV", 2, 3.5, 2_500.0))
        val cells = LearningGridProjection.project(regions, 2)
        val keys = (0 until cells.length()).map { index ->
            val cell = cells.getJSONObject(index)
            "${cell.getString("fuel")}:${cell.getInt("epoch")}:${cell.getString("key")}"
        }.toSet()

        assertTrue("PETROL:0:3:3" in keys)
        assertTrue("CNG:1:3:3" in keys)
        assertTrue("CNG:2:3:3" in keys)
        assertEquals(3, keys.size)
    }

    @Test
    fun `integrity detects a value divergence even when cell keys are equal`() {
        val regions = JSONArray().put(region("p", "GASOLINA", 0, 4.0, 2_500.0))
        val cells = LearningGridProjection.project(regions, 1)
        val altered = JSONArray(cells.toString())
        altered.getJSONObject(0).put("petrol_ms", 9.9)
        val report = LearningGridProjection.integrity(regions, altered, JSONArray(), 1, "map")

        assertFalse(report.getBoolean("ok"))
        assertEquals(1, report.getJSONArray("valueDivergences").length())
        assertFalse(report.getBoolean("memoryEqualsInterface"))
    }

    @Test
    fun `canonical projection reports the same hash for memory interface and export`() {
        val regions = JSONArray()
            .put(region("p", "GASOLINA", 0, 4.0, 2_500.0))
            .put(region("g", "GNV", 1, 4.2, 2_500.0))
        val cells = LearningGridProjection.project(regions, 1)
        val report = LearningGridProjection.integrity(regions, cells, JSONArray(), 1, "map")

        assertTrue(report.getBoolean("ok"))
        assertEquals(report.getString("memoryProjectionHash"), report.getString("interfaceProjectionHash"))
        assertEquals(report.getString("interfaceProjectionHash"), report.getString("exportProjectionHash"))
    }

    private fun region(id: String, fuel: String, epoch: Int, petrolMs: Double, rpm: Double) = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("epoch", epoch)
        .put("rpm", rpm)
        .put("map_bar", 0.60)
        .put("petrol_ms", petrolMs)
        .put("samples", 4)
        .put("visits", JSONArray().put("visit-$id"))
        .put("sessions", JSONArray().put("session-$id"))
        .put("confidence", 0.5)
        .put("stage", "OBSERVED")
        .put("updated_at", 1L)
}
