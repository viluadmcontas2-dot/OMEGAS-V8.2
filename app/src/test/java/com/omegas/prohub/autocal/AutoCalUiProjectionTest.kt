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
    fun `temporally incoherent native curves stay visible but are not a usable reference`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = usableReference("native-incoherent", longArrayOf(100L, 200L, 2_201L)),
            manualStatus = status("IDLE", 42L),
            manualSnapshot = unavailableSnapshot(),
        )
        assertEquals("NATIVE_MONITOR", projection.getString("source"))
        assertTrue(projection.getBoolean("snapshotAvailable"))
        assertFalse(projection.getBoolean("referenceUsable"))
        assertFalse(projection.getBoolean("referenceTimingCoherent"))
        assertEquals(2_101L, projection.getLong("referenceTimingSpanMs"))
        assertEquals(AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS, projection.getLong("referenceTimingLimitMs"))
    }

    @Test
    fun `manual coherent reference wins over temporally incoherent monitor`() {
        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = usableReference("native-incoherent", longArrayOf(100L, 200L, 2_201L)),
            manualStatus = status("READY", 42L),
            manualSnapshot = usableReference("manual-coherent", longArrayOf(100L, 200L, 300L)),
        )
        assertEquals("MANUAL_READER", projection.getString("source"))
        assertTrue(projection.getBoolean("referenceUsable"))
        assertTrue(projection.getBoolean("referenceTimingCoherent"))
        assertEquals(200L, projection.getLong("referenceTimingSpanMs"))
    }

    @Test
    fun `projection keeps persistent correlation state separate from current events`() {
        val nativeSnapshot = usableReference("native-correlation")
            .put(
                "nativeCorrelationState",
                JSONObject()
                    .put("correlatedBands", JSONArray().put(4))
                    .put("retryableBands", JSONArray().put(8)),
            )
            .put(
                "nativeMaturityEvents",
                JSONArray().put(
                    JSONObject()
                        .put("bandIndex", 8)
                        .put("correlationState", "NO_RELIABLE_CORRELATION"),
                ),
            )

        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = nativeSnapshot,
            manualStatus = status("IDLE", 42L),
            manualSnapshot = unavailableSnapshot(),
        )

        val correlationState = projection.getJSONObject("correlationState")
        assertEquals(4, correlationState.getJSONArray("correlatedBands").getInt(0))
        assertEquals(8, correlationState.getJSONArray("retryableBands").getInt(0))
        assertEquals(1, projection.getJSONArray("correlation").length())
    }

    @Test
    fun `manual reference keeps current native correlation state and events`() {
        val nativeSnapshot = partialSnapshot("native-partial-correlation")
            .put(
                "nativeCorrelationState",
                JSONObject()
                    .put("correlatedBands", JSONArray().put(5))
                    .put("retryableBands", JSONArray().put(9)),
            )
            .put(
                "nativeMaturityEvents",
                JSONArray().put(
                    JSONObject()
                        .put("bandIndex", 9)
                        .put("correlationState", "NO_RELIABLE_CORRELATION"),
                ),
            )

        val projection = AutoCalUiProjection.project(
            nativeStatus = status("MONITORING", 42L),
            nativeSnapshot = nativeSnapshot,
            manualStatus = status("READY", 42L),
            manualSnapshot = usableReference("manual-reference"),
        )

        assertEquals("MANUAL_READER", projection.getString("source"))
        assertEquals(5, projection.getJSONObject("correlationState").getJSONArray("correlatedBands").getInt(0))
        assertEquals(9, projection.getJSONObject("correlationState").getJSONArray("retryableBands").getInt(0))
        assertEquals(9, projection.getJSONArray("correlation").getJSONObject(0).getInt("bandIndex"))
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

    private fun usableReference(hash: String, capturedAtMs: LongArray? = null) = JSONObject()
        .put("available", true)
        .put("snapshotHash", hash)
        .put("fields", JSONArray()
            .put(vectorField("PETR_INJ_TBP", 30, 2.0, capturedAtMs?.getOrNull(0)))
            .put(vectorField("PETR_MNFLD_PRESS_RV", 30, 0.3, capturedAtMs?.getOrNull(1)))
            .put(vectorField("GAS_MNFLD_PRESS_RV", 30, 0.32, capturedAtMs?.getOrNull(2))))

    private fun vectorField(key: String, count: Int, base: Double, capturedAtMs: Long? = null): JSONObject {
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
            .also { field -> capturedAtMs?.let { field.put("capturedAtMs", it) } }
    }
}
