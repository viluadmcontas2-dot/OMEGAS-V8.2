package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalInstrumentProjectionTest {
    @Test
    fun `projects proven native curves points k and live now without ui science`() {
        val reference = snapshot(
            vectorField("PETR_INJ_TBP", doubleArrayOf(2.0, 4.0, 6.0), 100L),
            vectorField("PETR_MNFLD_PRESS_RV", doubleArrayOf(0.20, 0.40, 0.60), 120L),
            vectorField("GAS_MNFLD_PRESS_RV", doubleArrayOf(0.18, 0.37, 0.56), 130L),
            vectorField("MUL_ACT", doubleArrayOf(1.0, 1.01, 1.02), 140L),
            vectorField(
                "MNFLD_PRESS_THD",
                doubleArrayOf(
                    0.154, 0.256, 0.307, 0.358, 0.410, 0.461,
                    0.512, 0.563, 0.614, 0.666, 0.717, 0.768,
                    0.819, 0.870, 0.922, 0.973, 1.024, 1.126,
                ),
                150L,
            ),
        )
        val native = snapshot(
            vectorField("PETR_INJ_TBUF", doubleArrayOf(2.1, 4.1), 200L),
            vectorField("MNFLD_PRESS_BUF", doubleArrayOf(0.21, 0.41), 201L),
            vectorField("PETR_INJ_TBUF_GAS", doubleArrayOf(2.2, 4.2), 202L),
            vectorField("MNFLD_PRESS_BUF_GAS", doubleArrayOf(0.19, 0.39), 203L),
            vectorField("PETR_INJ_TBUF_GAS_PREV", doubleArrayOf(2.3, 4.3), 204L),
            vectorField("MNFLD_PRESS_BUF_GAS_PREV", doubleArrayOf(0.17, 0.36), 205L),
            vectorField("NUM_BUF_UPD_PETR", doubleArrayOf(3.0, 4.0), 206L, raw = intArrayOf(3, 4)),
            vectorField("NUM_BUF_UPD_GAS", doubleArrayOf(5.0, 6.0), 207L, raw = intArrayOf(5, 6)),
            vectorField("PETR_INJ_TBP", doubleArrayOf(2.0, 4.0, 6.0), 208L),
            vectorField("MUL_ACT", doubleArrayOf(1.0, 1.01, 1.02), 209L),
            vectorField("NUM_AUTOMATCH_EXECUTED", doubleArrayOf(2.0), 210L, raw = intArrayOf(2)),
            vectorField("MAX_AUTOMATCH", doubleArrayOf(3.0), 211L, raw = intArrayOf(3)),
        )

        val result = AutoCalInstrumentProjection.project(
            referenceSnapshot = reference,
            nativeCurrentSnapshot = native,
            telemetryStatus = telemetry(ageMs = 75L, sessionId = 42L),
            currentSessionId = 42L,
            acquisitionZones = JSONObject()
                .put("petrol", JSONArray(listOf(true, false, true, false)))
                .put("gas", JSONArray(listOf(false, true, true, false))),
        )

        assertEquals("omegas.autocal.instrument.v1", result.getString("schema"))
        assertEquals(3, result.getJSONObject("reference").getJSONArray("petrol").length())
        assertEquals(3, result.getJSONObject("reference").getJSONArray("gas").length())
        assertEquals(2, result.getJSONObject("acquisition").getJSONArray("petrolCurrent").length())
        assertEquals(2, result.getJSONObject("acquisition").getJSONArray("gasCurrent").length())
        assertEquals(2, result.getJSONObject("acquisition").getJSONArray("gasPrevious").length())
        assertEquals(3, result.getJSONObject("kCurve").getJSONArray("points").length())

        val epoch = result.getJSONObject("epoch")
        assertEquals(2, epoch.getInt("autoMatchExecuted"))
        assertEquals(3, epoch.getInt("maxAutoMatch"))
        assertEquals("CURRENT_NATIVE_AUTOMATCH_EPOCH", epoch.getString("gasCurrentRole"))
        assertEquals("PREVIOUS_NATIVE_AUTOMATCH_EPOCH", epoch.getString("gasPreviousRole"))
        assertEquals("ECU_READ", epoch.getString("authority"))
        val roles = result.getJSONObject("acquisition").getJSONObject("roles")
        assertEquals("CURRENT_NATIVE_AUTOMATCH_EPOCH", roles.getString("gasCurrent"))
        assertEquals("PREVIOUS_NATIVE_AUTOMATCH_EPOCH", roles.getString("gasPrevious"))

        val live = result.getJSONObject("liveNow")
        assertEquals(4.84, live.getDouble("petrolMs"), 0.0001)
        assertEquals(0.44, live.getDouble("mapBar"), 0.0001)
        assertEquals(2500.0, live.getDouble("rpm"), 0.0001)

        val authority = result.getJSONObject("authority")
        assertFalse(authority.getBoolean("uiDerivesScience"))
        assertEquals("ECU_READ", authority.getString("nativePoints"))
        assertEquals(listOf(true, false, true, false), bools(result.getJSONObject("zones").getJSONArray("petrol")))

        val regions = result.getJSONArray("zoneRegions")
        assertEquals(4, regions.length())
        assertEquals(0.0, regions.getJSONObject(0).getDouble("lowMapBar"), 0.0001)
        assertEquals(0.461, regions.getJSONObject(0).getDouble("highMapBar"), 0.0001)
        assertEquals(0.461, regions.getJSONObject(1).getDouble("lowMapBar"), 0.0001)
        assertEquals(0.666, regions.getJSONObject(1).getDouble("highMapBar"), 0.0001)
        assertEquals(0.870, regions.getJSONObject(3).getDouble("lowMapBar"), 0.0001)
        assertEquals(1.126, regions.getJSONObject(3).getDouble("highMapBar"), 0.0001)
        assertTrue(regions.getJSONObject(0).getBoolean("petrolAcquired"))
        assertTrue(regions.getJSONObject(1).getBoolean("gasAcquired"))
        assertEquals("Região 4", regions.getJSONObject(3).getString("label"))
    }

    @Test
    fun `empty zero acquisition slots are not published as physical points`() {
        val reference = snapshot(
            vectorField("PETR_INJ_TBP", doubleArrayOf(2.0), 100L),
            vectorField("PETR_MNFLD_PRESS_RV", doubleArrayOf(0.2), 100L),
            vectorField("GAS_MNFLD_PRESS_RV", doubleArrayOf(0.2), 100L),
        )
        val rawX = IntArray(18)
        val rawY = IntArray(18)
        rawX[13] = 3600
        rawY[13] = 884
        val physicalX = DoubleArray(18)
        val physicalY = DoubleArray(18)
        physicalX[13] = 7.2
        physicalY[13] = 0.884
        val native = snapshot(
            vectorField("PETR_INJ_TBUF_GAS", physicalX, 200L, raw = rawX),
            vectorField("MNFLD_PRESS_BUF_GAS", physicalY, 201L, raw = rawY),
        )

        val result = AutoCalInstrumentProjection.project(reference, native, JSONObject(), null, JSONObject())
        val points = result.getJSONObject("acquisition").getJSONArray("gasCurrent")
        assertEquals(1, points.length())
        assertEquals(13, points.getJSONObject(0).getInt("index"))
        assertEquals(7.2, points.getJSONObject(0).getDouble("petrolMs"), 0.0001)
        assertEquals(0.884, points.getJSONObject(0).getDouble("mapBar"), 0.0001)
    }

    @Test
    fun `stale or wrong-session telemetry never materializes agora`() {
        val base = snapshot(
            vectorField("PETR_INJ_TBP", doubleArrayOf(2.0), 100L),
            vectorField("PETR_MNFLD_PRESS_RV", doubleArrayOf(0.2), 100L),
            vectorField("GAS_MNFLD_PRESS_RV", doubleArrayOf(0.2), 100L),
        )

        val stale = AutoCalInstrumentProjection.project(
            base,
            null,
            telemetry(AutoCalInstrumentProjection.LIVE_STALE_MS + 1L, 42L),
            42L,
            JSONObject(),
        )
        assertTrue(stale.isNull("liveNow"))

        val wrongSession = AutoCalInstrumentProjection.project(
            base,
            null,
            telemetry(20L, 41L),
            42L,
            JSONObject(),
        )
        assertTrue(wrongSession.isNull("liveNow"))
    }

    @Test
    fun `gas previous remains separate from gas current for adaptive remarking`() {
        val reference = snapshot(
            vectorField("PETR_INJ_TBP", doubleArrayOf(4.0), 100L),
            vectorField("PETR_MNFLD_PRESS_RV", doubleArrayOf(0.4), 100L),
            vectorField("GAS_MNFLD_PRESS_RV", doubleArrayOf(0.4), 100L),
        )
        val native = snapshot(
            vectorField("PETR_INJ_TBUF_GAS", doubleArrayOf(4.0), 200L),
            vectorField("MNFLD_PRESS_BUF_GAS", doubleArrayOf(0.42), 200L),
            vectorField("PETR_INJ_TBUF_GAS_PREV", doubleArrayOf(4.0), 180L),
            vectorField("MNFLD_PRESS_BUF_GAS_PREV", doubleArrayOf(0.48), 180L),
        )

        val result = AutoCalInstrumentProjection.project(reference, native, JSONObject(), null, JSONObject())
        val acquisition = result.getJSONObject("acquisition")
        assertEquals(0.42, acquisition.getJSONArray("gasCurrent").getJSONObject(0).getDouble("mapBar"), 0.0001)
        assertEquals(0.48, acquisition.getJSONArray("gasPrevious").getJSONObject(0).getDouble("mapBar"), 0.0001)
    }

    private fun telemetry(ageMs: Long, sessionId: Long) = JSONObject()
        .put("valid", true)
        .put("ageMs", ageMs)
        .put("sessionId", sessionId)
        .put(
            "live",
            JSONObject()
                .put("petrol_ms", 4.84)
                .put("load_bar", 0.44)
                .put("rpm", 2500)
                .put("fuel", "GNV")
                .put("captured_elapsed_ms", 9_999L),
        )

    private fun snapshot(vararg fields: JSONObject) = JSONObject()
        .put("available", true)
        .put("fields", JSONArray().apply { fields.forEach(::put) })

    private fun vectorField(
        key: String,
        physical: DoubleArray,
        capturedAtMs: Long,
        raw: IntArray = IntArray(physical.size) { it + 1 },
    ) = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("physicalValues", JSONArray(physical.toList()))
        .put("rawValues", JSONArray(raw.toList()))
        .put("capturedAtMs", capturedAtMs)

    private fun bools(values: JSONArray) = List(values.length()) { values.getBoolean(it) }
}
