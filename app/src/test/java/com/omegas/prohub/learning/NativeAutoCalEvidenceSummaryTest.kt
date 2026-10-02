package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class NativeAutoCalEvidenceSummaryTest {
    @Test
    fun missingFileReturnsEmptyReadOnlySummary() {
        val root = Files.createTempDirectory("omegas-autocal-summary").toFile()
        val summary = NativeAutoCalEvidenceSummary(root).snapshot()

        assertTrue(summary.getBoolean("ok"))
        assertFalse(summary.getBoolean("available"))
        assertEquals(0, summary.getInt("snapshotCount"))
        assertEquals(0, summary.getInt("bandCount"))
        assertFalse(summary.getBoolean("automaticCalibration"))
        assertTrue(summary.getBoolean("manualOnly"))
        assertFalse(summary.getBoolean("activeLearningMutation"))
        assertFalse(summary.getBoolean("writerAvailable"))
    }

    @Test
    fun existingFileProducesNativeEvidenceCountsWithoutWriterAuthority() {
        val root = Files.createTempDirectory("omegas-autocal-summary").toFile()
        val items = JSONArray()
            .put(JSONObject()
                .put("snapshotId", "snap-a")
                .put("bandIndex", 0)
                .put("count", 12)
                .put("coverageQuality", 0.75)
                .put("historicalConditionKnown", true))
            .put(JSONObject()
                .put("snapshotId", "snap-a")
                .put("bandIndex", 1)
                .put("count", 0)
                .put("coverageQuality", 0.50)
                .put("historicalConditionKnown", false))
            .put(JSONObject()
                .put("snapshotId", "snap-b")
                .put("bandIndex", 0)
                .put("count", 8)
                .put("coverageQuality", 1.0)
                .put("historicalConditionKnown", true))
        root.resolve(NativeAutoCalEvidenceSummary.FILE_NAME)
            .writeText(JSONObject().put("nativeEcuEvidence", items).toString(), Charsets.UTF_8)

        val summary = NativeAutoCalEvidenceSummary(root).snapshot()

        assertTrue(summary.getBoolean("available"))
        assertEquals(2, summary.getInt("snapshotCount"))
        assertEquals(3, summary.getInt("bandCount"))
        assertEquals(2, summary.getInt("bandsWithSamples"))
        assertEquals(20L, summary.getLong("totalNativeCounts"))
        assertEquals(2, summary.getInt("historicalConditionKnownBands"))
        assertEquals("snap-b", summary.getString("latestSnapshotId"))
        assertEquals(0.75, summary.getDouble("averageCoverageQuality"), 0.0001)
        assertFalse(summary.getBoolean("automaticCalibration"))
        assertFalse(summary.getBoolean("writerAvailable"))
    }

    @Test
    fun cacheRefreshesOnlyWhenFileSignatureChanges() {
        val root = Files.createTempDirectory("omegas-autocal-summary").toFile()
        val file = root.resolve(NativeAutoCalEvidenceSummary.FILE_NAME)
        file.writeText(
            JSONObject().put("nativeEcuEvidence", JSONArray().put(
                JSONObject().put("snapshotId", "first").put("bandIndex", 0).put("count", 1),
            )).toString(),
            Charsets.UTF_8,
        )
        val reader = NativeAutoCalEvidenceSummary(root)
        assertEquals(1L, reader.snapshot().getLong("totalNativeCounts"))

        Thread.sleep(5)
        file.writeText(
            JSONObject().put("nativeEcuEvidence", JSONArray().put(
                JSONObject().put("snapshotId", "second").put("bandIndex", 0).put("count", 9),
            )).toString(),
            Charsets.UTF_8,
        )
        file.setLastModified(System.currentTimeMillis() + 1000)

        assertEquals(9L, reader.snapshot().getLong("totalNativeCounts"))
        assertEquals("second", reader.snapshot().getString("latestSnapshotId"))
    }
}
