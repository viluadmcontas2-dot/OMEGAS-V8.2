package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalUiProjectionTest {
    @Test
    fun `manual valid reference wins when monitor is only partial`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = partialSnapshot("native-partial"),
            manualStatus = status("READY", 42L),
            manualSnapshot = usableReference("manual-valid"),
        )
        assertEquals("MANUAL_READER", projection.getString("source"))
        assertTrue(projection.getBoolean("referenceUsable"))
        assertEquals("manual-valid", projection.getString("snapshotHash"))
    }

    @Test
    fun `native monitor wins when both references are usable in current session`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = usableReference("native-valid"),
            manualStatus = status("READY", 42L),
            manualSnapshot = usableReference("manual-valid"),
        )
        assertEquals("NATIVE_MONITOR", projection.getString("source"))
        assertTrue(projection.getBoolean("referenceAvailable"))
        assertEquals("native-valid", projection.getString("snapshotHash"))
    }

    @Test
    fun `unknown manual generation is rejected when current usb generation is known`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = unavailableSnapshot(),
            manualStatus = JSONObject().put("state", "READY").put("updatedAt", 3_000L),
            manualSnapshot = usableReference("unknown-session"),
        )
        assertEquals("NONE", projection.getString("source"))
        assertFalse(projection.getBoolean("referenceAvailable"))
        assertEquals("STALE_SESSION", projection.getString("freshness"))
    }

    @Test
    fun `stale manual generation cannot leak into new usb session`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 43L),
            nativeSnapshot = unavailableSnapshot(),
            manualStatus = status("READY", 42L),
            manualSnapshot = usableReference("stale-manual"),
        )
        assertEquals("NONE", projection.getString("source"))
        assertFalse(projection.getBoolean("referenceUsable"))
    }

    @Test
    fun `partial current monitor stays visible but never claims reference`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 7L),
            nativeSnapshot = partialSnapshot("partial"),
            manualStatus = status("IDLE", 7L),
            manualSnapshot = unavailableSnapshot(),
        )
        assertEquals("NATIVE_MONITOR", projection.getString("source"))
        assertTrue(projection.getBoolean("snapshotAvailable"))
        assertFalse(projection.getBoolean("referenceAvailable"))
        assertFalse(projection.getBoolean("referenceUsable"))
    }

    private fun status(state: String, sessionId: Long) = JSONObject()
        .put("state", state)
        .put("sessionId", sessionId)
        .put("updatedAt", 2_000L)

    private fun unavailableSnapshot() = JSONObject()
        .put("available", false)
        .put("fields", JSONArray())

    private fun partialSnapshot(hash: String) = JSONObject()
        .put("available", true)
        .put("snapshotHash", hash)
        .put("fields", JSONArray().put(vectorField("PETR_INJ_TBP", 30, 2.0)))

    private fun usableReference(hash: String) = JSONObject()
        .put("available", true)
        .put("snapshotHash", hash)
        .put("fields", JSONArray()
            .put(vectorField("PETR_INJ_TBP", 30, 2.0))
            .put(vectorField("PETR_MNFLD_PRESS_RV", 30, 0.3))
            .put(vectorField("GAS_MNFLD_PRESS_RV", 30, 0.32)))

    private fun vectorField(key: String, count: Int, base: Double): JSONObject {
        val raw = JSONArray()
        val physical = JSONArray()
        repeat(count) { index ->
            raw.put(100 + index)
            physical.put(base + index * 0.01)
        }
        return JSONObject()
            .put("key", key)
            .put("status", "VALID")
            .put("rawValues", raw)
            .put("physicalValues", physical)
    }
}
