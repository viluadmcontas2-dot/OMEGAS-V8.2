package com.omegas.prohub.telemetry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryNearestFrameTest {
    private fun frame(
        sessionId: Long,
        observedAtMs: Long,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
        fuel: String,
    ): String = JSONObject()
        .put("event", "telemetry")
        .put("session_id", sessionId)
        .put("observed_at_ms", observedAtMs)
        .put(
            "data",
            JSONObject()
                .put("session_id", sessionId)
                .put("observed_at_ms", observedAtMs)
                .put("rpm", rpm)
                .put("load_bar", mapBar)
                .put("petrol_ms", petrolMs)
                .put("fuel", fuel),
        )
        .toString()

    @Test
    fun `nearest frame returns historical MP48 context not current snapshot`() {
        val store = TelemetryStateStore(historyLimit = 10)
        store.beginSession(7)
        store.updateFromEngineEvent(frame(7, 1_000L, rpm = 1_500, mapBar = 0.40, petrolMs = 3.0, fuel = "PETROL"))
        store.updateFromEngineEvent(frame(7, 1_200L, rpm = 1_800, mapBar = 0.55, petrolMs = 4.5, fuel = "CNG"))

        val matched = store.nearestFrame(observedAtMs = 1_160L, maxSkewMs = 100L)!!

        assertEquals(1_800, matched.getInt("rpm"))
        assertEquals(0.55, matched.getDouble("map_bar"), 0.0001)
        assertEquals(4.5, matched.getDouble("petrol_ms"), 0.0001)
        assertEquals("CNG", matched.getString("fuel"))
        assertEquals(1_200L, matched.getLong("timestamp"))
        assertEquals(40L, matched.getLong("skew_ms"))
    }

    @Test
    fun `nearest frame rejects excessive temporal skew`() {
        val store = TelemetryStateStore(historyLimit = 10)
        store.beginSession(7)
        store.updateFromEngineEvent(frame(7, 1_200L, rpm = 1_800, mapBar = 0.55, petrolMs = 4.5, fuel = "CNG"))

        assertNull(store.nearestFrame(observedAtMs = 5_000L, maxSkewMs = 120L))
    }
}
