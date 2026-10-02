package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Projeção semântica da mesma sessão gravada pelo SessionRecorder.
 *
 * Não captura telemetria, não faz polling e não escreve na ECU. Resume eventos já
 * aceitos pelo recorder para que UI e análises futuras não precisem reler todos os
 * JSONL. O JSONL continua sendo a evidência bruta/canônica.
 */
class SessionSemanticLedger(
    private val sessionDir: File,
    private val sessionId: String,
    private val physicalUsbSessionId: Long,
    private val startedAtMs: Long,
    private val startReason: String,
) {
    companion object {
        const val FILE_NAME = "session_summary.json"
        const val SCHEMA = "omegas-session-semantic-v1"

        @JvmStatic
        fun loadOrRebuild(sessionDir: File): JSONObject {
            val summaryFile = File(sessionDir, FILE_NAME)
            val parsed = try {
                if (summaryFile.isFile) JSONObject(summaryFile.readText(Charsets.UTF_8)) else null
            } catch (_: Exception) {
                null
            }
            if (parsed?.optString("schema") == SCHEMA && parsed.optBoolean("recording", false).not()) {
                return JSONObject(parsed.toString())
            }

            val manifest = try {
                JSONObject(File(sessionDir, "manifest.json").readText(Charsets.UTF_8))
            } catch (_: Exception) {
                JSONObject()
            }
            val id = manifest.optString("sessionId", sessionDir.name)
            val startedAt = manifest.optLong("createdAtMs", manifest.optLong("startedAtMs", sessionDir.lastModified()))
            val metadata = manifest.optJSONObject("metadata") ?: JSONObject()
            val ledger = SessionSemanticLedger(
                sessionDir = sessionDir,
                sessionId = id,
                physicalUsbSessionId = metadata.optLong("usbSessionId", 0L),
                startedAtMs = startedAt,
                startReason = manifest.optString("reason", "Sessão"),
            )
            sessionDir.listFiles { file -> file.isFile && file.name.startsWith("events_") && file.name.endsWith(".jsonl") }
                ?.sortedBy { it.name }
                ?.forEach { events ->
                    events.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            try {
                                val item = JSONObject(line)
                                ledger.observe(
                                    sequence = item.optLong("sequence", 0L),
                                    type = item.optString("type"),
                                    source = item.optString("source"),
                                    data = item.optJSONObject("data") ?: JSONObject(),
                                    recordedAtMs = item.optLong("recordedAtMs", 0L),
                                    persist = false,
                                )
                            } catch (_: Exception) {
                                ledger.rebuildParseErrors += 1
                            }
                        }
                    }
                }

            val manifestStoppedAt = manifest.optLong("stoppedAtMs", 0L)
            val stoppedAt = manifestStoppedAt.takeIf { it > 0L } ?: ledger.lastEventAtMs.takeIf { it > 0L } ?: startedAt
            val stopReason = manifest.optString("stopReason").ifBlank {
                if (manifest.optBoolean("recording", false)) "RECOVERED_AFTER_UNCLEAN_STOP" else ledger.observedStopReason
            }
            val rebuilt = ledger.buildSnapshot(
                recording = false,
                stoppedAtMs = stoppedAt,
                stopReason = stopReason,
                recovered = true,
            )
            ledger.writeAtomic(rebuilt)
            return JSONObject(rebuilt.toString())
        }
    }

    private var lastSequence = 0L
    private var lastEventAtMs = 0L
    private var eventCount = 0L
    private var telemetrySamples = 0L
    private var petrolTicks = 0L
    private var cngTicks = 0L
    private var snapshotCount = 0
    private var nativeSnapshotCount = 0
    private var manualSnapshotCount = 0
    private var validSnapshotCount = 0
    private var partialSnapshotCount = 0
    private var actionReceipts = 0
    private var calibrationEpochs = 0
    private var lastSnapshotAtMs = 0L
    private var lastSnapshotHash = ""
    private var autoCalEnabled: Int? = null
    private var moduleVersion: Int? = null
    private var autoMatchExecuted: Int? = null
    private var petrolZones = 0
    private var gasZones = 0
    private var observedStopReason = ""
    private var rebuildParseErrors = 0
    private val matureRegions = linkedSetOf<Int>()
    private val correlatedRegions = linkedSetOf<Int>()

    @Synchronized
    fun observe(
        sequence: Long,
        type: String,
        source: String,
        data: JSONObject,
        recordedAtMs: Long,
        persist: Boolean = true,
    ) {
        lastSequence = maxOf(lastSequence, sequence)
        if (recordedAtMs > 0L) lastEventAtMs = maxOf(lastEventAtMs, recordedAtMs)
        eventCount += 1L

        when (type) {
            "telemetry" -> observeTelemetry(data)
            "autocal_native_snapshot", "autocal_manual_snapshot" -> observeAutoCalSnapshot(type, data, recordedAtMs)
            "autocal_native_action" -> actionReceipts += 1
            "autocal_native_calibration_epoch" -> calibrationEpochs += 1
            "session_stopped" -> observedStopReason = data.optString("reason", observedStopReason)
        }

        if (persist && shouldPersist(type)) {
            persist(
                recording = type != "session_stopped",
                stoppedAtMs = if (type == "session_stopped") recordedAtMs else 0L,
                stopReason = if (type == "session_stopped") observedStopReason else "",
            )
        }
    }

    @Synchronized
    fun snapshot(
        recording: Boolean,
        stoppedAtMs: Long = 0L,
        stopReason: String = "",
    ): JSONObject = buildSnapshot(recording, stoppedAtMs, stopReason, recovered = false)

    @Synchronized
    fun persist(
        recording: Boolean,
        stoppedAtMs: Long = 0L,
        stopReason: String = "",
    ): JSONObject {
        val summary = buildSnapshot(recording, stoppedAtMs, stopReason, recovered = false)
        writeAtomic(summary)
        return JSONObject(summary.toString())
    }

    @Synchronized
    fun finish(stoppedAtMs: Long, stopReason: String): JSONObject =
        persist(recording = false, stoppedAtMs = stoppedAtMs, stopReason = stopReason)

    private fun shouldPersist(type: String): Boolean =
        type == "session_started" ||
            type == "session_stopped" ||
            type == "export_boundary" ||
            type.startsWith("autocal_") ||
            type == "k_batch_confirmed" ||
            type == "k_factor_batch_confirmed"

    private fun observeTelemetry(data: JSONObject) {
        telemetrySamples += 1L
        val payload = data.optJSONObject("event")?.optJSONObject("data")
            ?: data.optJSONObject("data")
            ?: data
        when (payload.optString("fuel", payload.optString("state", "")).uppercase()) {
            "PETROL", "GASOLINA" -> petrolTicks += 1L
            "CNG", "GNV", "GAS" -> cngTicks += 1L
        }
    }

    private fun observeAutoCalSnapshot(type: String, snapshot: JSONObject, recordedAtMs: Long) {
        snapshotCount += 1
        if (type == "autocal_native_snapshot") nativeSnapshotCount += 1 else manualSnapshotCount += 1
        lastSnapshotAtMs = maxOf(lastSnapshotAtMs, recordedAtMs)
        lastSnapshotHash = snapshot.optString("snapshotHash", lastSnapshotHash)
        val validFieldCount = snapshot.optInt("validFieldCount", 0)
        val fieldCount = snapshot.optInt("fieldCount", 0)
        if (snapshot.optBoolean("partial", false)) partialSnapshotCount += 1
        else if (validFieldCount > 0 || (fieldCount > 0 && validFieldCount == fieldCount)) validSnapshotCount += 1

        autoCalEnabled = number(snapshot, "AUTO_CAL_ENABLE", snapshot.optIntOrNull("autoCalEnabled")) ?: autoCalEnabled
        moduleVersion = number(snapshot, "MODULE_VERSION", null) ?: moduleVersion
        autoMatchExecuted = number(
            snapshot,
            "NUM_AUTOMATCH_EXECUTED",
            snapshot.optJSONObject("nativeStatus")?.optIntOrNull("autoMatchCount"),
        ) ?: autoMatchExecuted
        petrolZones = maxOf(petrolZones, zoneCount(snapshot, "ACQUIRED_ZONES_PETROL"))
        gasZones = maxOf(gasZones, zoneCount(snapshot, "ACQUIRED_ZONES_GAS"))

        val events = snapshot.optJSONArray("nativeMaturityEvents") ?: JSONArray()
        repeat(events.length()) { index ->
            val event = events.optJSONObject(index) ?: return@repeat
            val band = event.optInt("bandIndex", -1)
            if (band >= 0) {
                val humanRegion = band + 1
                matureRegions += humanRegion
                if (event.optString("correlationState") == "CORRELATED") correlatedRegions += humanRegion
            }
        }
    }

    private fun buildSnapshot(
        recording: Boolean,
        stoppedAtMs: Long,
        stopReason: String,
        recovered: Boolean,
    ): JSONObject {
        val effectiveEnd = if (recording) System.currentTimeMillis() else stoppedAtMs.takeIf { it > 0L } ?: lastEventAtMs
        val autocal = JSONObject()
            .put("snapshotCount", snapshotCount)
            .put("nativeSnapshotCount", nativeSnapshotCount)
            .put("manualSnapshotCount", manualSnapshotCount)
            .put("validSnapshotCount", validSnapshotCount)
            .put("partialSnapshotCount", partialSnapshotCount)
            .put("lastSnapshotAtMs", lastSnapshotAtMs)
            .put("lastSnapshotHash", lastSnapshotHash.ifBlank { JSONObject.NULL })
            .put("autoCalEnabled", autoCalEnabled ?: JSONObject.NULL)
            .put("moduleVersion", moduleVersion ?: JSONObject.NULL)
            .put("autoMatchExecuted", autoMatchExecuted ?: JSONObject.NULL)
            .put("petrolZones", petrolZones)
            .put("gasZones", gasZones)
            .put("matureRegions", toArray(matureRegions))
            .put("correlatedRegions", toArray(correlatedRegions))
            .put("actionReceipts", actionReceipts)
            .put("calibrationEpochs", calibrationEpochs)

        return JSONObject()
            .put("schema", SCHEMA)
            .put("sessionId", sessionId)
            .put("physicalUsbSessionId", physicalUsbSessionId)
            .put("startReason", startReason)
            .put("startedAtMs", startedAtMs)
            .put("stoppedAtMs", if (recording) JSONObject.NULL else effectiveEnd)
            .put("durationMs", (effectiveEnd - startedAtMs).coerceAtLeast(0L))
            .put("recording", recording)
            .put("stopReason", if (recording) "" else stopReason.ifBlank { observedStopReason })
            .put("lastSequence", lastSequence)
            .put("eventsObserved", eventCount)
            .put("telemetrySamples", telemetrySamples)
            .put("petrolTicks", petrolTicks)
            .put("cngTicks", cngTicks)
            .put("autocal", autocal)
            .put("recovered", recovered)
            .put("rebuildParseErrors", rebuildParseErrors)
            .put("evidenceAuthority", "SESSION_RECORDER_JSONL")
            .put("automaticMapKMutation", false)
            .put("automaticEcuWrite", false)
    }

    private fun writeAtomic(summary: JSONObject) {
        sessionDir.mkdirs()
        val destination = File(sessionDir, FILE_NAME)
        val temp = File(sessionDir, ".$FILE_NAME.tmp")
        val bytes = summary.toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(temp, false).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun zoneCount(snapshot: JSONObject, key: String): Int {
        val item = validField(snapshot, key) ?: return 0
        val values = item.optJSONArray("rawValues") ?: return 0
        var count = 0
        repeat(minOf(4, values.length())) { index ->
            if (values.optDouble(index, 0.0) > 0.0) count += 1
        }
        return count
    }

    private fun number(snapshot: JSONObject, key: String, fallback: Int?): Int? {
        val item = validField(snapshot, key)
        val values = item?.optJSONArray("rawValues")
        if (values != null && values.length() > 0 && !values.isNull(0)) return values.optInt(0)
        return fallback
    }

    private fun validField(snapshot: JSONObject, key: String): JSONObject? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") == key && field.optString("status") == "VALID") return field
        }
        return null
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun toArray(values: Set<Int>): JSONArray = JSONArray().also { array ->
        values.sorted().forEach { array.put(it) }
    }
}
