package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalSnapshotHumanProjectorTest {
    @Test
    fun separatesThreeAcquisitionFamiliesFromThirtyPointReferences() {
        val snapshot = snapshot()
        val projection = NativeAutoCalSnapshotHumanProjector.project(snapshot)
        val points = projection.getJSONArray("acquisitionPoints")

        assertEquals("TPET_MS", projection.getString("xAxis"))
        assertEquals("MAP_BAR", projection.getString("yAxis"))
        assertEquals(54, points.length())
        assertEquals(18, countFuel(points, "PETROL"))
        assertEquals(18, countFuel(points, "GAS"))
        assertEquals(18, countFuel(points, "GAS_PREVIOUS"))
        assertEquals(4, projection.getJSONArray("reference30Keys").length())
        assertEquals(30, projection.getInt("referencePointCount"))
        assertEquals("SEPARATE_CURVE_REFERENCE_OVERLAY_ONLY", projection.getString("reference30Role"))
        assertTrue(projection.getBoolean("curveKSeparateSurface"))
        assertFalse(projection.getBoolean("uiMayDeriveMaturity"))
        assertFalse(projection.getBoolean("uiMayDerivePhysicalCoordinates"))
    }

    @Test
    fun malformedAcquisitionShapeFailsClosedWithoutFabricatingCoordinates() {
        val snapshot = snapshot().apply {
            replaceField("PETR_INJ_TBUF", IntArray(17) { 1000 })
        }
        val projection = NativeAutoCalSnapshotHumanProjector.project(snapshot)
        val petrol = pointsFor(projection.getJSONArray("acquisitionPoints"), "PETROL")

        assertEquals("NATIVE_STATE_INSUFFICIENT", projection.getString("state"))
        assertTrue(petrol.all { !it.getBoolean("positioned") })
        assertTrue(petrol.all { it.isNull("tPetrolMs") && it.isNull("mapBar") })
    }

    @Test
    fun correlatedEventMarksOnlyItsFuelBandAndNeverCreatesRpmFromBandIndex() {
        val snapshot = snapshot().apply {
            put("nativeMaturityEvents", JSONArray().put(JSONObject()
                .put("sourceFuel", "PETROL")
                .put("bandIndex", 3)
                .put("correlationState", "CORRELATED")
                .put("rpm", 1725)))
        }
        val projection = NativeAutoCalSnapshotHumanProjector.project(snapshot)
        val points = projection.getJSONArray("acquisitionPoints")
        val anchored = (0 until points.length()).map { points.getJSONObject(it) }.filter { it.getBoolean("correlatedAnchor") }

        assertEquals(1, anchored.size)
        assertEquals("PETROL", anchored.single().getString("fuel"))
        assertEquals(3, anchored.single().getInt("bandIndex"))
        assertFalse(anchored.single().has("rpm"))
    }

    @Test
    fun automatchRevalidationOverridesAcquisitionMessageWithoutChangingPointFamilies() {
        val projection = NativeAutoCalSnapshotHumanProjector.project(snapshot(), autoMatchRevalidating = true)
        assertEquals("AUTOMATCH_REVALIDATING", projection.getString("state"))
        assertEquals("AutoMatch alterou calibração — revalidando", projection.getString("message"))
        assertEquals(54, projection.getJSONArray("acquisitionPoints").length())
    }

    @Test
    fun previousGasCoordinatesSurviveAutoMatchWithoutBorrowingCurrentGasMaturity() {
        val state = snapshot().apply {
            replaceField("NUM_BUF_UPD_GAS", IntArray(18))
            replaceField("ACQUIRED_ZONES_GAS", intArrayOf(0, 0, 0, 0))
            replaceField("PETR_INJ_TBUF_GAS", IntArray(18))
            replaceField("MNFLD_PRESS_BUF_GAS", IntArray(18))
            replaceField("PETR_INJ_TBUF_GAS_PREV", IntArray(18) { if (it == 2) 1526 else 0 })
            replaceField("MNFLD_PRESS_BUF_GAS_PREV", IntArray(18) { if (it == 2) 342 else 0 })
        }
        val points = NativeAutoCalSnapshotHumanProjector.project(state).getJSONArray("acquisitionPoints")
        val previous = pointsFor(points, "GAS_PREVIOUS").single { it.getInt("bandIndex") == 2 }
        val current = pointsFor(points, "GAS").single { it.getInt("bandIndex") == 2 }

        assertTrue(previous.getBoolean("positioned"))
        assertEquals(1526.0 / 512.0, previous.getDouble("tPetrolMs"), 0.000001)
        assertEquals(342.0 / 1024.0, previous.getDouble("mapBar"), 0.000001)
        assertEquals("NOT_ACQUIRED", current.getString("maturity"))
        assertEquals("UNKNOWN", previous.getString("maturity"))
    }

    @Test
    fun previousGasFamilyHasNoCurrentGasCountersOrZoneFlags() {
        val progression = NativeAutoCalProgression.evaluate(
            petrolCounters = IntArray(18) { 8 },
            petrolTimes = IntArray(18) { 1800 },
            petrolMaps = IntArray(18) { 600 },
            petrolZoneFlags = intArrayOf(1, 1, 1, 1),
            gasCounters = IntArray(18) { 9 },
            gasTimes = IntArray(18) { 1600 },
            gasMaps = IntArray(18) { 500 },
            gasZoneFlags = intArrayOf(1, 1, 1, 1),
            previousGasTimes = IntArray(18) { 1526 },
            previousGasMaps = IntArray(18) { 342 },
        )
        val current = progression.acquisition18.single { it.fuel == NativeAutoCalProgression.Fuel.GAS }
        val previous = progression.acquisition18.single { it.fuel == NativeAutoCalProgression.Fuel.GAS_PREVIOUS }
        assertEquals(9, current.bands[2].counter)
        assertNull(previous.bands[2].counter)
        assertEquals(NativeAutoCalProgression.ZoneState.UNKNOWN, previous.zones[0].state)
        assertEquals(NativeAutoCalProgression.ShapeState.KNOWN, previous.shapeState)
    }

    private fun snapshot(): JSONObject = JSONObject()
        .put("fields", JSONArray().apply {
            put(field("NUM_BUF_UPD_PETR", IntArray(18) { 8 }))
            put(field("PETR_INJ_TBUF", IntArray(18) { 1600 + it * 10 }))
            put(field("MNFLD_PRESS_BUF", IntArray(18) { 400 + it }))
            put(field("ACQUIRED_ZONES_PETROL", intArrayOf(1, 1, 1, 1)))
            put(field("NUM_BUF_UPD_GAS", IntArray(18) { 8 }))
            put(field("PETR_INJ_TBUF_GAS", IntArray(18) { 1610 + it * 10 }))
            put(field("MNFLD_PRESS_BUF_GAS", IntArray(18) { 405 + it }))
            put(field("ACQUIRED_ZONES_GAS", intArrayOf(1, 1, 1, 1)))
            put(field("PETR_INJ_TBUF_GAS_PREV", IntArray(18) { 1590 + it * 10 }))
            put(field("MNFLD_PRESS_BUF_GAS_PREV", IntArray(18) { 395 + it }))
            put(field("PETR_INJ_TBP", IntArray(30) { 1000 + it }))
            put(field("MUL_ACT", IntArray(30) { 16384 }))
            put(field("PETR_MNFLD_PRESS_RV", IntArray(30) { 400 }))
            put(field("GAS_MNFLD_PRESS_RV", IntArray(30) { 405 }))
        })

    private fun field(key: String, values: IntArray) = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("rawValues", JSONArray(values.toList()))

    private fun JSONObject.replaceField(key: String, values: IntArray) {
        val fields = getJSONArray("fields")
        for (index in 0 until fields.length()) {
            if (fields.getJSONObject(index).getString("key") == key) {
                fields.put(index, field(key, values))
                return
            }
        }
    }

    private fun countFuel(points: JSONArray, fuel: String): Int = pointsFor(points, fuel).size

    private fun pointsFor(points: JSONArray, fuel: String): List<JSONObject> =
        (0 until points.length()).map { points.getJSONObject(it) }.filter { it.getString("fuel") == fuel }
}
