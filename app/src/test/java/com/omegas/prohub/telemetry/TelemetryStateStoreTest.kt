package com.omegas.prohub.telemetry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryStateStoreTest {
    @Test
    fun `disconnect clears values and rejects a delayed frame`() {
        val store = TelemetryStateStore()
        store.beginSession(41L)
        assertTrue(store.updateFromEngineEvent(event(41L, 2_500)) != null)
        assertEquals(2_500, store.telemetryCopy().optInt("rpm"))

        store.invalidate("USB_DISCONNECTED")
        assertFalse(store.isValid())
        assertEquals(0, store.telemetryCopy().length())
        assertNull(store.updateFromEngineEvent(event(41L, 3_000)))
        assertFalse(store.isValid())
    }

    @Test
    fun `new session rejects frames tagged with the previous connection`() {
        val store = TelemetryStateStore()
        store.beginSession(10L)
        assertTrue(store.updateFromEngineEvent(event(10L, 1_500)) != null)
        store.beginSession(11L)

        assertNull(store.updateFromEngineEvent(event(10L, 4_000)))
        assertFalse(store.isValid())
        assertTrue(store.updateFromEngineEvent(event(11L, 1_800)) != null)
        assertEquals(1_800, store.telemetryCopy().optInt("rpm"))
    }

    @Test
    fun `slow native delivery preserves physical frame age instead of pretending it is fresh`() {
        val store = TelemetryStateStore()
        store.beginSession(77L)
        val physicalAt = System.currentTimeMillis() - 700L
        val delayed = JSONObject()
            .put("event", "telemetry")
            .put("session_id", 77L)
            .put("live", JSONObject()
                .put("session_id", 77L)
                .put("rpm", 2_200)
                .put("last_frame_at", physicalAt / 1000.0)
                .put("last_frame_age_ms", 0))
            .toString()

        assertTrue(store.updateFromEngineEvent(delayed) != null)
        val live = JSONObject(store.liveJson())
        assertTrue("physical age was ${live.optLong("ageMs", -1L)} ms", live.optLong("ageMs", -1L) >= 500L)
        assertTrue("delivery delay missing", live.optLong("deliveryDelayMs", -1L) >= 500L)
        assertTrue("receipt itself should be fresh", live.optLong("deliveryAgeMs", Long.MAX_VALUE) < 250L)
    }

    private fun event(sessionId: Long, rpm: Int): String = JSONObject()
        .put("event", "telemetry")
        .put("session_id", sessionId)
        .put("live", JSONObject().put("session_id", sessionId).put("rpm", rpm))
        .toString()
}
