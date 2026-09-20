package com.omegas.prohub.telemetry

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Estado central e thread-safe da telemetria. A interface nativa e o painel
 * LAN opcional recebem apenas cópias deste estado.
 */
class TelemetryStateStore(private val historyLimit: Int = 720) {
    private val lock = Any()
    private val sequence = AtomicLong(0)
    private var stateUpdatedAt = 0L
    private var telemetryUpdatedAt = 0L
    private var lastTelemetryCapturedElapsedMs = -1L
    private var telemetry = JSONObject()
    private var runtime = JSONObject()
    private var fullSnapshot = JSONObject()
    private var gps = JSONObject()
    private var sessionId = 0L
    private var valid = false
    private var acceptingTelemetry = false
    private val history = ArrayDeque<JSONObject>()

    /** Adapter legado; o runtime atual entrega o objeto já projetado. */
    fun updateFromEngineEvent(raw: String): JSONObject? = try {
        updateFromEngineEvent(JSONObject(raw))
    } catch (_: Exception) {
        null
    }

    /**
     * Consome a projeção downstream do frame sem serializar e parsear novamente
     * entre NativeRuntimeManager e o serviço. O objeto é tratado como somente leitura.
     */
    fun updateFromEngineEvent(root: JSONObject): JSONObject? {
        val event = root.optString("event", "telemetry")
        val payload = root.optJSONObject("data") ?: root.optJSONObject("live") ?: root
        val isTelemetry = event == "telemetry" || payload.has("rpm")
        return synchronized(lock) {
            val eventSessionId = root.optLong("session_id", payload.optLong("session_id", 0L))
            if (!acceptingTelemetry || (eventSessionId > 0L && eventSessionId != sessionId)) {
                return@synchronized null
            }

            if (isTelemetry) {
                val capturedElapsedMs = physicalFrameRevision(payload) ?: return@synchronized null
                if (capturedElapsedMs <= lastTelemetryCapturedElapsedMs) return@synchronized null
                telemetry = copyObject(payload)
                lastTelemetryCapturedElapsedMs = capturedElapsedMs
            }
            // Eventos que não são telemetria não podem completar nem alterar o frame físico.
            root.optJSONObject("runtime")?.let { merge(runtime, it) }

            val now = System.currentTimeMillis()
            stateUpdatedAt = now
            if (isTelemetry) {
                telemetryUpdatedAt = now
                valid = true
            }
            val seq = sequence.incrementAndGet()
            if (isTelemetry) {
                history.addLast(
                    JSONObject()
                        .put("sequence", seq)
                        .put("timestamp", telemetryUpdatedAt)
                        .put("captured_elapsed_ms", payload.getLong("captured_elapsed_ms"))
                        .put("rpm", payload.getInt("rpm"))
                        .put("petrol_ms", payload.getDouble("petrol_ms"))
                        .put("gas_ms", payload.optDouble("gas_ms_diagnostic", payload.optDouble("gas_ms", 0.0)))
                        .put("map_bar", payload.getDouble("load_bar"))
                        .put("gps_speed_kmh", gps.optDouble("speedKmh", 0.0))
                        .put("gps_latitude", gps.opt("latitude") ?: JSONObject.NULL)
                        .put("gps_longitude", gps.opt("longitude") ?: JSONObject.NULL),
                )
                while (history.size > historyLimit) history.removeFirst()
            }
            JSONObject()
                .put("event", event)
                .put("sequence", seq)
                .put("timestamp", stateUpdatedAt)
                .put("captured_elapsed_ms", if (isTelemetry) payload.getLong("captured_elapsed_ms") else JSONObject.NULL)
        }
    }

    fun updateGps(value: JSONObject) {
        synchronized(lock) {
            gps = JSONObject(value.toString())
            sequence.incrementAndGet()
        }
    }

    fun beginSession(id: Long) = synchronized(lock) {
        sessionId = id
        valid = false
        acceptingTelemetry = true
        stateUpdatedAt = System.currentTimeMillis()
        telemetryUpdatedAt = 0L
        lastTelemetryCapturedElapsedMs = -1L
        telemetry = JSONObject()
        runtime = JSONObject().put("link", "INITIALIZING").put("session_id", id)
        fullSnapshot = JSONObject()
        history.clear()
        sequence.incrementAndGet()
    }

    fun invalidate(reason: String) = synchronized(lock) {
        valid = false
        acceptingTelemetry = false
        stateUpdatedAt = System.currentTimeMillis()
        telemetryUpdatedAt = 0L
        lastTelemetryCapturedElapsedMs = -1L
        telemetry = JSONObject()
        runtime = JSONObject().put("link", "OFFLINE").put("reason", reason).put("session_id", sessionId)
        fullSnapshot = JSONObject()
        history.clear()
        sequence.incrementAndGet()
    }

    fun updateFullSnapshot(root: JSONObject) {
        synchronized(lock) {
            val snapshotSessionId = root.optLong("session_id", root.optLong("native_session_id", 0L))
            if (!acceptingTelemetry || (snapshotSessionId > 0L && snapshotSessionId != sessionId)) {
                return@synchronized
            }
            fullSnapshot = JSONObject(root.toString())
            val now = System.currentTimeMillis()
            root.optJSONObject("live")?.let { live ->
                val capturedElapsedMs = physicalFrameRevision(live)
                if (capturedElapsedMs != null && capturedElapsedMs > lastTelemetryCapturedElapsedMs) {
                    telemetry = copyObject(live)
                    lastTelemetryCapturedElapsedMs = capturedElapsedMs
                    // Caminho legado: só um frame físico completo e mais novo pode
                    // substituir a projeção atual. Nunca complete campos entre revisões.
                    val frameAtMs = when {
                        live.optDouble("last_frame_at", 0.0) > 0.0 ->
                            (live.optDouble("last_frame_at") * 1000.0).toLong()
                        live.has("last_frame_age_ms") ->
                            now - live.optLong("last_frame_age_ms", Long.MAX_VALUE).coerceAtLeast(0L)
                        else -> 0L
                    }
                    if (frameAtMs in 1..now && frameAtMs > telemetryUpdatedAt) {
                        telemetryUpdatedAt = frameAtMs
                    }
                }
            }
            root.optJSONObject("runtime")?.let { merge(runtime, it) }
            stateUpdatedAt = now
            sequence.incrementAndGet()
        }
    }

    fun lightweightJson(): String = synchronized(lock) {
        JSONObject()
            .put("sequence", sequence.get())
            .put("updatedAt", telemetryUpdatedAt)
            .put("stateUpdatedAt", stateUpdatedAt)
            .put("ageMs", if (telemetryUpdatedAt == 0L) -1 else System.currentTimeMillis() - telemetryUpdatedAt)
            .put("valid", valid)
            .put("sessionId", sessionId)
            .put("telemetry", JSONObject(telemetry.toString()))
            .put("runtime", JSONObject(runtime.toString()))
            .put("gps", JSONObject(gps.toString()))
            .put("history", JSONArray(history.map { JSONObject(it.toString()) }))
            .toString()
    }

    /** Snapshot mínimo para a WebView; não serializa o histórico. */
    fun liveJson(): String = synchronized(lock) {
        JSONObject()
            .put("sequence", sequence.get())
            .put("updatedAt", telemetryUpdatedAt)
            .put("ageMs", if (telemetryUpdatedAt == 0L) -1 else System.currentTimeMillis() - telemetryUpdatedAt)
            .put("valid", valid)
            .put("sessionId", sessionId)
            .put("live", JSONObject(telemetry.toString()))
            .put("runtime", JSONObject(runtime.toString()))
            .toString()
    }

    fun fullJson(): String = synchronized(lock) {
        val result = if (fullSnapshot.length() > 0) JSONObject(fullSnapshot.toString()) else JSONObject()
        result.put("native_sequence", sequence.get())
        result.put("native_session_id", sessionId)
        result.put("telemetry_valid", valid)
        result.put("native_updated_at", telemetryUpdatedAt)
        result.put("native_state_updated_at", stateUpdatedAt)
        result.put("native_history", JSONArray(history.map { JSONObject(it.toString()) }))
        if (!result.has("live")) result.put("live", JSONObject(telemetry.toString()))
        if (!result.has("runtime")) result.put("runtime", JSONObject(runtime.toString()))
        result.put("gps", JSONObject(gps.toString()))
        result.toString()
    }

    fun telemetryCopy(): JSONObject = synchronized(lock) {
        if (valid) JSONObject(telemetry.toString()) else JSONObject()
    }
    fun sessionId(): Long = synchronized(lock) { sessionId }
    fun isValid(): Boolean = synchronized(lock) { valid }
    fun ageMs(): Long = synchronized(lock) {
        if (telemetryUpdatedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - telemetryUpdatedAt
    }

    private fun physicalFrameRevision(payload: JSONObject): Long? {
        if (!payload.has("captured_elapsed_ms") ||
            !payload.has("rpm") ||
            !payload.has("petrol_ms") ||
            !payload.has("load_bar")
        ) return null
        val capturedElapsedMs = payload.optLong("captured_elapsed_ms", -1L)
        val rpm = payload.optDouble("rpm", Double.NaN)
        val petrolMs = payload.optDouble("petrol_ms", Double.NaN)
        val mapBar = payload.optDouble("load_bar", Double.NaN)
        if (capturedElapsedMs <= 0L ||
            !rpm.isFinite() || rpm < 0.0 ||
            !petrolMs.isFinite() || petrolMs < 0.0 ||
            !mapBar.isFinite() || mapBar < 0.0
        ) return null
        return capturedElapsedMs
    }

    private fun copyObject(source: JSONObject): JSONObject = JSONObject().also { target ->
        source.keys().forEach { key -> target.put(key, source.opt(key)) }
    }

    private fun merge(target: JSONObject, source: JSONObject) {
        source.keys().forEach { key -> target.put(key, source.opt(key)) }
    }
}
