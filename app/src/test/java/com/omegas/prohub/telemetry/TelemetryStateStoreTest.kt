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
    fun `partial telemetry cannot inherit map from previous physical revision`() {
        val store = TelemetryStateStore()
        store.beginSession(21L)
        assertTrue(store.updateFromEngineEvent(fullEvent(21L, 1_000L, 2_000, 4.5, 0.45)) != null)

        val partial = JSONObject()
            .put("event", "telemetry")
            .put("session_id", 21L)
            .put("live", JSONObject()
                .put("session_id", 21L)
                .put("captured_elapsed_ms", 1_100L)
                .put("rpm", 2_100)
                .put("petrol_ms", 5.0))
            .toString()

        assertNull(store.updateFromEngineEvent(partial))
        val current = store.telemetryCopy()
        assertEquals(1_000L, current.getLong("captured_elapsed_ms"))
        assertEquals(2_000, current.getInt("rpm"))
        assertEquals(4.5, current.getDouble("petrol_ms"), 0.0001)
        assertEquals(0.45, current.getDouble("load_bar"), 0.0001)
    }

    @Test
    fun `older frame from same usb generation cannot replace newer physical revision`() {
        val store = TelemetryStateStore()
        store.beginSession(22L)
        assertTrue(store.updateFromEngineEvent(fullEvent(22L, 2_000L, 3_000, 6.0, 0.70)) != null)
        assertNull(store.updateFromEngineEvent(fullEvent(22L, 1_900L, 9_000, 9.0, 1.20)))

        val current = store.telemetryCopy()
        assertEquals(2_000L, current.getLong("captured_elapsed_ms"))
        assertEquals(3_000, current.getInt("rpm"))
        assertEquals(6.0, current.getDouble("petrol_ms"), 0.0001)
        assertEquals(0.70, current.getDouble("load_bar"), 0.0001)
    }

    private fun fullEvent(
        sessionId: Long,
        capturedElapsedMs: Long,
        rpm: Int,
        petrolMs: Double,
        mapBar: Double,
    ): JSONObject = JSONObject()
        .put("event", "telemetry")
        .put("session_id", sessionId)
        .put("live", JSONObject()
            .put("session_id", sessionId)
            .put("captured_elapsed_ms", capturedElapsedMs)
            .put("rpm", rpm)
            .put("petrol_ms", petrolMs)
            .put("load_bar", mapBar)
            .put("fuel", "GNV"))

    private fun event(sessionId: Long, rpm: Int): String = fullEvent(
        sessionId = sessionId,
        capturedElapsedMs = rpm.toLong(),
        rpm = rpm,
        petrolMs = 4.0,
        mapBar = 0.5,
    ).toString()
}
