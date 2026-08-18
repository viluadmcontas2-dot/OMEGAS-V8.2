package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsefulCoverageMetricTest {
    @Test
    fun usefulCoverageCountsSupportedRegionsPerTimeNotRawFrames() {
        val root = JSONObject()
            .put("regions", JSONArray()
                .put(region("r1", samples = 1))
                .put(region("r2", samples = 10_000))
                .put(region("invalid", samples = 10_000).put("visit_count", 0)))

        val metric = UsefulCoverageMetric.fromLearningExport(root, elapsedMsOverride = 30_000L)
        assertEquals(3, metric.getInt("raw_region_count"))
        assertEquals(2, metric.getInt("useful_region_count"))
        assertEquals(1, metric.getInt("ignored_region_count"))
        assertEquals(2, metric.getInt("environment_context_complete_regions"))
        assertEquals(4.0, metric.getDouble("useful_regions_per_minute"), 1e-9)
        assertFalse(metric.getBoolean("raw_frame_count_used"))
    }

    @Test
    fun repeatingFramesInsideSameRegionDoesNotIncreaseCoverage() {
        val oneFrame = JSONObject().put("regions", JSONArray().put(region("same", samples = 1)))
        val tenThousand = JSONObject().put("regions", JSONArray().put(region("same", samples = 10_000)))

        val a = UsefulCoverageMetric.fromLearningExport(oneFrame, 60_000L)
        val b = UsefulCoverageMetric.fromLearningExport(tenThousand, 60_000L)
        assertEquals(1, a.getInt("useful_region_count"))
        assertEquals(a.getInt("useful_region_count"), b.getInt("useful_region_count"))
        assertEquals(a.getDouble("useful_regions_per_minute"), b.getDouble("useful_regions_per_minute"), 0.0)
    }

    @Test
    fun unavailableOptionalEnvironmentDoesNotEraseOtherwiseUsefulCoverage() {
        val region = region("optional-env", samples = 5)
            .put("water_c", JSONObject.NULL)
            .put("pressure_diff_bar", JSONObject.NULL)
        val metric = UsefulCoverageMetric.fromLearningExport(
            JSONObject().put("regions", JSONArray().put(region)),
            60_000L,
        )

        assertEquals(1, metric.getInt("useful_region_count"))
        assertEquals(0, metric.getInt("environment_context_complete_regions"))
        assertTrue(metric.getBoolean("environment_context_optional"))
        assertEquals("USEFUL_COVERAGE_AVAILABLE", metric.getString("reason"))
    }

    @Test
    fun missingRequiredOperatingContextReportsNoUsefulCoverage() {
        val region = region("bad", samples = 5).put("map_bar", JSONObject.NULL)
        val metric = UsefulCoverageMetric.fromLearningExport(
            JSONObject().put("regions", JSONArray().put(region)),
            60_000L,
        )
        assertEquals(0, metric.getInt("useful_region_count"))
        assertEquals(0.0, metric.getDouble("useful_regions_per_minute"), 0.0)
        assertEquals("NO_VALID_SUPPORTED_REGION", metric.getString("reason"))
    }

    private fun region(id: String, samples: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("rpm", 2_500.0)
        .put("map_bar", 0.60)
        .put("petrol_ms", 4.0)
        .put("water_c", 80.0)
        .put("pressure_diff_bar", 1.4)
        .put("confidence", 0.8)
        .put("samples", samples)
        .put("visit_count", 1)
}
