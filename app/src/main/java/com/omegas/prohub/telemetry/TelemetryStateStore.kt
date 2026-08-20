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
    private data class TelemetryHistorySample(
        val sequence: Long,
        val timestamp: Long,
        val rpm: Int,
        val petrolMs: Double,
        val gasMs: Double,
        val mapBar: Double,
        val gpsSpeedKmh: Double,
        val gpsLatitude: Double?,
        val gpsLongitude: Double?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("sequence", sequence)
            .put("timestamp", timestamp)
            .put("rpm", rpm)
            .put("petrol_ms", petrolMs)
            .put("gas_ms", gasMs)
            .put("map_bar", mapBar)
            .put("gps_speed_kmh", gpsSpeedKmh)
            .put("gps_latitude", gpsLatitude ?: JSONObject.NULL)
            .put("gps_longitude", gpsLongitude ?: JSONObject.NULL)
    }

    private val lock = Any()
    private val sequence = AtomicLong(0)
    private var stateUpdatedAt = 0L
    private var telemetryUpdatedAt = 0L
    private var telemetry = JSONObject()
    private var runtime = JSONObject()
    private var fullSnapshot = JSONObject()
    private var gps = JSONObject()
    private var sessionId = 0L
    private var valid = false
    private var acceptingTelemetry = false
    private val history = ArrayDeque<TelemetryHistorySample>()

    /** Adapter legado. O caminho runtime atual deve entregar o objeto já projetado. */
    fun updateFromEngineEvent(raw: String): JSONObject? = try {
        updateFromEngineEvent(JSONObject(raw))
    } catch (_: Exception) {
        null
    }

    /**
     * Consome a única projeção downstream do frame, sem serializar e parsear de
     * novo entre NativeRuntimeManager e o serviço. O objeto é somente leitura.
     */
    fun updateFromEngineEvent(root: JSONObject): JSONObject? {
        val event = root.optString("event", "telemetry")
        val payload = root.optJSONObject("data") ?: root.optJSONObject("live") ?: root
        return synchronized(lock) {
            val eventSessionId = root.optLong("session_id", payload.optLong("session_id", 0L))
            if (!acceptingTelemetry || (eventSessionId > 0L && eventSessionId != sessionId)) {
                return@synchronized null
            }
            merge(telemetry, payload)
            root.optJSONObject("runtime")?.let { merge(runtime, it) }
            val now = System.currentTimeMillis()
            stateUpdatedAt = now
            val isTelemetry = event == "telemetry" || payload.has("rpm")
            if (isTelemetry) {
                telemetryUpdatedAt = now
                valid = true
            }
            val seq = sequence.incrementAndGet()
            if (isTelemetry) {
                history.addLast(
                    TelemetryHistorySample(
                        sequence = seq,
                        timestamp = telemetryUpdatedAt,
                        rpm = payload.optInt("rpm", telemetry.optInt("rpm", 0)),
                        petrolMs = payload.optDouble("petrol_ms", telemetry.optDouble("petrol_ms", 0.0)),
                        gasMs = payload.optDouble("gas_ms_diagnostic", payload.optDouble("gas_ms", telemetry.optDouble("gas_ms", 0.0))),
                        mapBar = payload.optDouble("load_bar", payload.optDouble("map_bar", telemetry.optDouble("map_bar", 0.0))),
                        gpsSpeedKmh = gps.optDouble("speedKmh", 0.0),
                        gpsLatitude = gps.opt("latitude")?.let { if (it == JSONObject.NULL) null else (it as? Number)?.toDouble() },
                        gpsLongitude = gps.opt("longitude")?.let { if (it == JSONObject.NULL) null else (it as? Number)?.toDouble() },
                    ),
                )
                while (history.size > historyLimit) history.removeFirst()
            }
            JSONObject().put("event", event).put("sequence", seq).put("timestamp", stateUpdatedAt)
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
        telemetry = JSONObject()
        runtime = JSONObject().put("link", "OFFLINE").put("reason", reason).put("session_id", sessionId)
        fullSnapshot = JSONObject()
        history.clear()
        sequence.incrementAndGet()
    }

    fun updateFullSnapshot(root: JSONObject) {
        synchronized(lock) {
            val snapshotSessionId = root.optLong("session_id", root.optLong("native_session_id", 0L))
            if (!acceptingTelemetry || (snapshotSessionId > 0L && snapshotSessionId != sessionId)) return@synchronized
            fullSnapshot = JSONObject(root.toString())
            val now = System.currentTimeMillis()
            root.optJSONObject("live")?.let { live ->
                merge(telemetry, live)
                val frameAtMs = when {
                    live.optDouble("last_frame_at", 0.0) > 0.0 -> (live.optDouble("last_frame_at") * 1000.0).toLong()
                    live.has("last_frame_age_ms") -> now - live.optLong("last_frame_age_ms", Long.MAX_VALUE).coerceAtLeast(0L)
                    else -> 0L
                }
                if (frameAtMs in 1..now && frameAtMs > telemetryUpdatedAt) telemetryUpdatedAt = frameAtMs
            }
            root.optJSONObject("runtime")?.let { merge(runtime, it) }
            stateUpdatedAt = now
            sequence.incrementAndGet()
        }
    }

    private fun historyJson(): JSONArray = JSONArray(history.map { it.toJson() })

    fun lightweightJson(): String = synchronized(lock) {
        JSONObject()
            .put("sequence", sequence.get()).put("updatedAt", telemetryUpdatedAt).put("stateUpdatedAt", stateUpdatedAt)
            .put("ageMs", if (telemetryUpdatedAt == 0L) -1 else System.currentTimeMillis() - telemetryUpdatedAt)
            .put("valid", valid).put("sessionId", sessionId)
            .put("telemetry", JSONObject(telemetry.toString())).put("runtime", JSONObject(runtime.toString()))
            .put("gps", JSONObject(gps.toString())).put("history", historyJson()).toString()
    }

    /** Snapshot mínimo para a WebView; não serializa o histórico. */
    fun liveJson(): String = synchronized(lock) {
        JSONObject().put("sequence", sequence.get()).put("updatedAt", telemetryUpdatedAt)
            .put("ageMs", if (telemetryUpdatedAt == 0L) -1 else System.currentTimeMillis() - telemetryUpdatedAt)
            .put("valid", valid).put("sessionId", sessionId).put("live", JSONObject(telemetry.toString()))
            .put("runtime", JSONObject(runtime.toString())).toString()
    }

    fun fullJson(): String = synchronized(lock) {
        val result = if (fullSnapshot.length() > 0) JSONObject(fullSnapshot.toString()) else JSONObject()
        result.put("native_sequence", sequence.get()).put("native_session_id", sessionId).put("telemetry_valid", valid)
            .put("native_updated_at", telemetryUpdatedAt).put("native_state_updated_at", stateUpdatedAt)
            .put("native_history", historyJson())
        if (!result.has("live")) result.put("live", JSONObject(telemetry.toString()))
        if (!result.has("runtime")) result.put("runtime", JSONObject(runtime.toString()))
        result.put("gps", JSONObject(gps.toString()))
        result.toString()
    }

    fun telemetryCopy(): JSONObject = synchronized(lock) { if (valid) JSONObject(telemetry.toString()) else JSONObject() }
    fun sessionId(): Long = synchronized(lock) { sessionId }
    fun isValid(): Boolean = synchronized(lock) { valid }
    fun ageMs(): Long = synchronized(lock) { if (telemetryUpdatedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - telemetryUpdatedAt }

    private fun merge(target: JSONObject, source: JSONObject) {
        source.keys().forEach { key -> target.put(key, source.opt(key)) }
    }
}
