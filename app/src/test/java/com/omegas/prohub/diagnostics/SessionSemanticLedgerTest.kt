package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionSemanticLedgerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `snapshot nativo vira resumo util sem criar autoridade de calibracao`() {
        val dir = temporary.newFolder("session_case")
        val ledger = SessionSemanticLedger(
            sessionDir = dir,
            sessionId = "session_case",
            physicalUsbSessionId = 44L,
            startedAtMs = 1_000L,
            startReason = "MP48 conectado",
        )
        val snapshot = JSONObject()
            .put("available", true)
            .put("snapshotHash", "abc123")
            .put("autoCalEnabled", 1)
            .put("validFieldCount", 20)
            .put("fieldCount", 20)
            .put("partial", false)
            .put("fields", JSONArray()
                .put(field("MODULE_VERSION", 7))
                .put(field("NUM_AUTOMATCH_EXECUTED", 3))
                .put(vectorField("ACQUIRED_ZONES_PETROL", intArrayOf(1, 1, 1, 1)))
                .put(vectorField("ACQUIRED_ZONES_GAS", intArrayOf(1, 1, 1, 0))))
            .put("nativeMaturityEvents", JSONArray()
                .put(JSONObject().put("bandIndex", 4).put("correlationState", "CORRELATED"))
                .put(JSONObject().put("bandIndex", 7).put("correlationState", "NO_RELIABLE_CORRELATION")))

        ledger.observe(1L, "autocal_native_snapshot", "autocal", snapshot, 1_500L)
        val summary = ledger.snapshot(recording = true)

        assertEquals("omegas-session-semantic-v1", summary.getString("schema"))
        assertEquals(44L, summary.getLong("physicalUsbSessionId"))
        val autocal = summary.getJSONObject("autocal")
        assertEquals(1, autocal.getInt("snapshotCount"))
        assertEquals(4, autocal.getInt("petrolZones"))
        assertEquals(3, autocal.getInt("gasZones"))
        assertEquals(3, autocal.getInt("autoMatchExecuted"))
        assertEquals(7, autocal.getInt("moduleVersion"))
        assertTrue(autocal.getJSONArray("matureRegions").toString().contains("5"))
        assertTrue(autocal.getJSONArray("matureRegions").toString().contains("8"))
        assertTrue(autocal.getJSONArray("correlatedRegions").toString().contains("5"))
        assertFalse(summary.getBoolean("automaticMapKMutation"))

        ledger.persist(recording = true)
        assertTrue(File(dir, SessionSemanticLedger.FILE_NAME).isFile)
    }

    @Test
    fun `summary corrompido pode ser reconstruido somente da evidencia jsonl`() {
        val dir = temporary.newFolder("session_rebuild")
        File(dir, "manifest.json").writeText(
            JSONObject()
                .put("sessionId", "session_rebuild")
                .put("createdAtMs", 2_000L)
                .put("stoppedAtMs", 5_000L)
                .put("reason", "MP48 conectado")
                .put("stopReason", "USB_DISCONNECTED")
                .toString(),
        )
        val snapshot = JSONObject()
            .put("available", true)
            .put("snapshotHash", "rebuilt")
            .put("fields", JSONArray()
                .put(vectorField("ACQUIRED_ZONES_PETROL", intArrayOf(1, 1, 0, 0)))
                .put(vectorField("ACQUIRED_ZONES_GAS", intArrayOf(1, 0, 0, 0))))
        val lines = listOf(
            event(1L, 2_000L, "session_started", JSONObject().put("reason", "MP48 conectado")),
            event(2L, 3_000L, "autocal_native_snapshot", snapshot),
            event(3L, 5_000L, "session_stopped", JSONObject().put("reason", "USB_DISCONNECTED")),
        )
        File(dir, "events_0001.jsonl").writeText(lines.joinToString("\n", postfix = "\n"))
        File(dir, SessionSemanticLedger.FILE_NAME).writeText("{broken")

        val rebuilt = SessionSemanticLedger.loadOrRebuild(dir)

        assertTrue(rebuilt.getBoolean("recovered"))
        assertEquals(2, rebuilt.getJSONObject("autocal").getInt("petrolZones"))
        assertEquals(1, rebuilt.getJSONObject("autocal").getInt("gasZones"))
        assertEquals("USB_DISCONNECTED", rebuilt.getString("stopReason"))
        assertFalse(rebuilt.getBoolean("automaticMapKMutation"))
    }

    private fun field(key: String, value: Int) = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("rawValues", JSONArray().put(value))

    private fun vectorField(key: String, values: IntArray): JSONObject {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return JSONObject().put("key", key).put("status", "VALID").put("rawValues", array)
    }

    private fun event(sequence: Long, at: Long, type: String, data: JSONObject) = JSONObject()
        .put("format", "omegas-session-log-v1")
        .put("sequence", sequence)
        .put("recordedAtMs", at)
        .put("type", type)
        .put("source", if (type.startsWith("autocal")) "autocal" else "native")
        .put("data", data)
        .toString()
}
