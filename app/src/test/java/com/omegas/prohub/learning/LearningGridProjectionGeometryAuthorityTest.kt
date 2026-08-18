package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningGridProjectionGeometryAuthorityTest {
    @After
    fun cleanupAuthority() {
        LearningCalibrationAuthority.endPhysicalSession()
    }

    @Test
    fun managedPhysicalSessionWithoutGeometryNeverInventsCellOrWeight() {
        LearningCalibrationAuthority.beginPhysicalSession()

        val cell = LearningGridProjection.cellFor(rpm = 2_500.0, petrolMs = 4.5, mapBar = 0.60)
        assertFalse(cell.getBoolean("geometryKnown"))
        assertEquals("MAP_GEOMETRY_UNKNOWN", cell.getString("reasonCode"))
        assertEquals(-1, cell.getInt("row"))
        assertEquals(-1, cell.getInt("column"))
        assertEquals("UNKNOWN", cell.getString("key"))
        assertEquals(0, cell.getJSONArray("continuousWeights").length())
        assertEquals(0, cell.getJSONArray("trilinearWeights").length())
        assertTrue(ContinuousLearningMath.bilinearWeights(2_500.0, 4.5).isEmpty())
        assertTrue(ContinuousLearningMath.trilinearWeights(2_500.0, 4.5, 0.60).isEmpty())

        val rawRegion = JSONObject()
            .put("id", "petrol-raw")
            .put("fuel", "PETROL")
            .put("epoch", 0)
            .put("rpm", 2_500.0)
            .put("petrol_ms", 4.5)
            .put("map_bar", 0.60)
            .put("samples", 12)
        val enriched = LearningGridProjection.enrichRegion(rawRegion)
        assertEquals("petrol-raw", enriched.getString("id"))
        assertFalse(enriched.getBoolean("cell_known"))
        assertEquals("UNKNOWN", enriched.getString("cell_key"))
        assertEquals(0, LearningGridProjection.project(JSONArray().put(rawRegion), 1).length())
    }

    @Test
    fun knownEcuGeometryControlsCellAndAllWeightCallPathsInsteadOfHistoricalFixture() {
        LearningCalibrationAuthority.beginPhysicalSession()
        LearningCalibrationAuthority.publish(
            LearningCalibrationBinding(
                calibrationFingerprint = "cal-live",
                calibrationGeneration = 7,
                geometryFingerprint = "geo-live",
                usbSessionId = 42L,
                mapHash = "map-live",
                petrolAxisMs = listOf(1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0, 21.0, 23.0),
                rpmAxis = listOf(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000, 9_000, 10_000, 11_000, 12_000),
            ),
        )

        val cell = LearningGridProjection.cellFor(rpm = 1_000.0, petrolMs = 5.0, mapBar = 0.60)
        assertTrue(cell.getBoolean("geometryKnown"))
        assertEquals("ECU_CURRENT", cell.getString("axisSource"))
        assertEquals("geo-live", cell.getString("geometryFingerprint"))
        assertEquals(2, cell.getInt("row"))
        assertEquals(0, cell.getInt("column"))
        assertEquals(1_000, cell.getInt("rpmBin"))
        assertEquals(5.0, cell.getDouble("petrolBin"), 0.0)

        val weights = cell.getJSONArray("continuousWeights")
        assertEquals(1, weights.length())
        assertEquals(2, weights.getJSONObject(0).getInt("row"))
        assertEquals(0, weights.getJSONObject(0).getInt("column"))
        assertEquals(1.0, weights.getJSONObject(0).getDouble("weight"), 0.0)

        val legacySignatureWeights = ContinuousLearningMath.bilinearWeights(1_000.0, 5.0)
        assertEquals(1, legacySignatureWeights.size)
        assertEquals(2, legacySignatureWeights.single().row)
        assertEquals(0, legacySignatureWeights.single().column)
        assertEquals(1.0, legacySignatureWeights.single().weight, 0.0)

        val grid = LearningGridProjection.gridJson()
        assertTrue(grid.getBoolean("geometryKnown"))
        assertEquals("ECU_CURRENT", grid.getString("axisSource"))
        assertEquals(1_000, grid.getJSONArray("rpmBins").getInt(0))
        assertEquals(5.0, grid.getJSONArray("petrolBins").getDouble(2), 0.0)
    }

    @Test
    fun legacyIdentityMarkerRemainsComparableButNeverGrantsCellGeometry() {
        val legacy = LearningCalibrationBinding.fromJson(
            JSONObject()
                .put("calibration_fingerprint", "cal-old")
                .put("calibration_generation", 4)
                .put("geometry_fingerprint", "geo-old")
                .put("usb_session_id", 9L)
                .put("map_hash", "map-old"),
        )
        assertNotNull(legacy)
        assertFalse(legacy!!.geometryKnown())
        assertEquals("cal-old:4:geo-old", legacy.key())

        LearningCalibrationAuthority.beginPhysicalSession()
        LearningCalibrationAuthority.publish(legacy)
        val cell = LearningGridProjection.cellFor(2_500.0, 4.5)
        assertFalse(cell.getBoolean("geometryKnown"))
        assertEquals("MAP_GEOMETRY_UNKNOWN", cell.getString("reasonCode"))
        assertTrue(ContinuousLearningMath.bilinearWeights(2_500.0, 4.5).isEmpty())
    }

    @Test
    fun historicalFixtureIsAvailableOnlyOutsideManagedPhysicalSession() {
        LearningCalibrationAuthority.beginPhysicalSession()
        assertFalse(LearningGridProjection.cellFor(2_500.0, 4.5).getBoolean("geometryKnown"))

        LearningCalibrationAuthority.endPhysicalSession()
        val legacy = LearningGridProjection.cellFor(2_500.0, 4.5)
        assertTrue(legacy.getBoolean("geometryKnown"))
        assertEquals("LEGACY_FIXTURE", legacy.getString("axisSource"))
        assertEquals(4, legacy.getInt("row"))
        assertEquals(3, legacy.getInt("column"))
    }
}
