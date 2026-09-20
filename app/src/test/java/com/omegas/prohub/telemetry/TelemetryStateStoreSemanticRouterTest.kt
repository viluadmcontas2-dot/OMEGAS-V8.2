package com.omegas.prohub.telemetry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryStateStoreSemanticRouterTest {
    @Test
    fun `object delivery updates current session without string reparse`() {
        val store = TelemetryStateStore(historyLimit = 4)
        store.beginSession(7L)
        val event = JSONObject()
            .put("event", "telemetry")
            .put("session_id", 7L)
            .put("live", JSONObject()
                .put("session_id", 7L)
                .put("captured_elapsed_ms", 1_000L)
                .put("rpm", 2500)
                .put("petrol_ms", 4.25)
                .put("load_bar", 0.62)
                .put("fuel", "GNV"))
            .put("runtime", JSONObject().put("link", "ONLINE"))

        val accepted = store.updateFromEngineEvent(event)
        assertNotNull(accepted)
        assertTrue(store.isValid())
        assertEquals(2500, store.telemetryCopy().getInt("rpm"))
        assertEquals(4.25, store.telemetryCopy().getDouble("petrol_ms"), 0.0001)
    }

    @Test
    fun `object delivery rejects stale usb generation`() {
        val store = TelemetryStateStore(historyLimit = 4)
        store.beginSession(8L)
        val stale = JSONObject()
            .put("event", "telemetry")
            .put("session_id", 7L)
            .put("live", JSONObject()
                .put("session_id", 7L)
                .put("captured_elapsed_ms", 2_000L)
                .put("rpm", 1800)
                .put("petrol_ms", 3.5)
                .put("load_bar", 0.40))

        assertNull(store.updateFromEngineEvent(stale))
        assertTrue(store.telemetryCopy().length() == 0)
    }
}
