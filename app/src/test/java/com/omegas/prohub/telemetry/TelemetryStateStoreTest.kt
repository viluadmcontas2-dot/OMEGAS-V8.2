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

    private fun event(sessionId: Long, rpm: Int): String = JSONObject()
        .put("event", "telemetry")
        .put("session_id", sessionId)
        .put("live", JSONObject().put("session_id", sessionId).put("rpm", rpm))
        .toString()
}
