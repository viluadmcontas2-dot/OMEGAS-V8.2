package com.omegas.prohub.telemetry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryStateStoreSessionRaceTest {
    @Test
    fun delayedOldSessionCannotReplaceNewSessionState() {
        val store = TelemetryStateStore(historyLimit = 8)
        store.beginSession(10L)
        val oldAccepted = store.updateFromEngineEvent(
            JSONObject()
                .put("event", "telemetry")
                .put("session_id", 10L)
                .put("live", JSONObject().put("rpm", 1111).put("petrol_ms", 3.1))
                .toString(),
        )
        assertTrue(oldAccepted != null)

        store.beginSession(11L)
        assertNull(
            store.updateFromEngineEvent(
                JSONObject()
                    .put("event", "telemetry")
                    .put("session_id", 10L)
                    .put("live", JSONObject().put("rpm", 9999).put("petrol_ms", 9.9))
                    .toString(),
            ),
        )
        store.updateFullSnapshot(
            JSONObject()
                .put("session_id", 10L)
                .put("live", JSONObject().put("rpm", 8888)),
        )

        val newAccepted = store.updateFromEngineEvent(
            JSONObject()
                .put("event", "telemetry")
                .put("session_id", 11L)
                .put("live", JSONObject().put("rpm", 2222).put("petrol_ms", 4.2))
                .toString(),
        )
        assertTrue(newAccepted != null)
        assertEquals(11L, store.sessionId())
        assertEquals(2222, store.telemetryCopy().getInt("rpm"))
    }
}
