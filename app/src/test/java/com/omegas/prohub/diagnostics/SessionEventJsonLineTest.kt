package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEventJsonLineTest {
    @Test
    fun `encoded payload stays structural json without parse roundtrip in recorder`() {
        val data = JSONObject()
            .put("rpm", 2500)
            .put("fuel", "GNV")
            .put("note", "linha \"A\"\nB")

        val line = SessionEventJsonLine.encode(
            format = "omegas-session-log-v1",
            sequence = 9L,
            recordedAtMs = 1234L,
            recordedAtUtc = "2026-08-18T07:30:00.000Z",
            type = "telemetry",
            source = "mp48",
            dataJson = data.toString(),
        )

        val decoded = JSONObject(line)
        assertEquals(9L, decoded.getLong("sequence"))
        assertEquals("telemetry", decoded.getString("type"))
        assertTrue(decoded.get("data") is JSONObject)
        assertEquals(2500, decoded.getJSONObject("data").getInt("rpm"))
        assertEquals("linha \"A\"\nB", decoded.getJSONObject("data").getString("note"))
    }
}
