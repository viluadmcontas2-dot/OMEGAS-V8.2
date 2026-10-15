package com.omegas.prohub.settings

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/** Configurações exclusivamente funcionais da arquitetura Android nativa. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("omegas_settings_native", Context.MODE_PRIVATE)

    init {
        // Migra somente os antigos valores-padrão excessivos. Valores personalizados
        // permanecem intocados.
        if (!prefs.getBoolean("recorderSafeDefaultsV52", false)) {
            val edit = prefs.edit().putBoolean("recorderSafeDefaultsV52", true)
            if (!prefs.contains("sessionTelemetryEveryMs") || prefs.getLong("sessionTelemetryEveryMs", 0L) < 250L) {
                edit.putLong("sessionTelemetryEveryMs", 250L)
            }
            if (!prefs.contains("sessionLogMaxMb") || prefs.getInt("sessionLogMaxMb", 1_024) == 1_024) {
                edit.putInt("sessionLogMaxMb", 256)
            }
            if (!prefs.contains("sessionKeepCount") || prefs.getInt("sessionKeepCount", 5) == 5) {
                edit.putInt("sessionKeepCount", 20)
            }
            edit.apply()
        }
        if (!prefs.getBoolean("recorderRetentionFloor20V1", false)) {
            val edit = prefs.edit().putBoolean("recorderRetentionFloor20V1", true)
            if (!prefs.contains("sessionKeepCount") || prefs.getInt("sessionKeepCount", 20) < 20) {
                edit.putInt("sessionKeepCount", 20)
            }
            edit.apply()
        }
    }

    var autoStartEngine: Boolean
        get() = prefs.getBoolean("autoStartEngine", true)
        set(value) = prefs.edit().putBoolean("autoStartEngine", value).apply()
    var autoConnectUsb: Boolean
        get() = prefs.getBoolean("autoConnectUsb", true)
        set(value) = prefs.edit().putBoolean("autoConnectUsb", value).apply()
    var autoReconnectUsb: Boolean
        get() = prefs.getBoolean("autoReconnectUsb", true)
        set(value) = prefs.edit().putBoolean("autoReconnectUsb", value).apply()
    var keepCpuAwake: Boolean
        get() = prefs.getBoolean("keepCpuAwake", true)
        set(value) = prefs.edit().putBoolean("keepCpuAwake", value).apply()

    /** MP48 oficial: não editável e imune a preferências antigas. */
    val baudRate: Int get() = 9_600
    val dataBits: Int get() = 8
    val stopBits: Int get() = 1
    val parity: String get() = "NONE"
    val dtr: Boolean get() = false
    val rts: Boolean get() = false

    var preferredDeviceName: String
        get() = prefs.getString("preferredDeviceName", "") ?: ""
        set(value) = prefs.edit().putString("preferredDeviceName", value).apply()

    var gpsEnabled: Boolean
        get() = prefs.getBoolean("gpsEnabled", false)
        set(value) = prefs.edit().putBoolean("gpsEnabled", value).apply()
    var gpsIntervalMs: Long
        get() = prefs.getLong("gpsIntervalMs", 1_000L)
        set(value) = prefs.edit().putLong("gpsIntervalMs", value.coerceIn(250L, 60_000L)).apply()


    var sessionRecorderEnabled: Boolean
        get() = prefs.getBoolean("sessionRecorderEnabled", true)
        set(value) = prefs.edit().putBoolean("sessionRecorderEnabled", value).apply()
    var sessionRecorderAutoStartOnUsb: Boolean
        get() = prefs.getBoolean("sessionRecorderAutoStartOnUsb", true)
        set(value) = prefs.edit().putBoolean("sessionRecorderAutoStartOnUsb", value).apply()
    var sessionTelemetryEveryMs: Long
        get() = prefs.getLong("sessionTelemetryEveryMs", 250L)
        set(value) = prefs.edit().putLong("sessionTelemetryEveryMs", value.coerceIn(250L, 5_000L)).apply()
    var sessionFullSnapshotEveryMs: Long
        get() = prefs.getLong("sessionFullSnapshotEveryMs", 5_000L)
        set(value) = prefs.edit().putLong("sessionFullSnapshotEveryMs", value.coerceIn(0L, 60_000L)).apply()
    var sessionCaptureRawUsb: Boolean
        get() = prefs.getBoolean("sessionCaptureRawUsb", false)
        set(value) = prefs.edit().putBoolean("sessionCaptureRawUsb", value).apply()
    var sessionLogMaxMb: Int
        get() = prefs.getInt("sessionLogMaxMb", 256)
        set(value) = prefs.edit().putInt("sessionLogMaxMb", value.coerceIn(64, 1_024)).apply()
    var sessionKeepCount: Int
        get() = prefs.getInt("sessionKeepCount", 20)
        set(value) = prefs.edit().putInt("sessionKeepCount", value.coerceIn(20, 100)).apply()

    var deviceId: String
        get() {
            val existing = prefs.getString("deviceId", "") ?: ""
            if (existing.isNotBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", created).apply()
            return created
        }
        set(value) = prefs.edit().putString("deviceId", value.trim()).apply()
    var deviceName: String
        get() = prefs.getString("deviceName", "Android ${android.os.Build.MODEL}") ?: "Android"
        set(value) = prefs.edit().putString("deviceName", value.trim().take(48)).apply()


    var gnvCylinderCapacityM3: Float
        get() = prefs.getFloat("gnvCylinderCapacityM3", 15.0f)
        set(value) = prefs.edit().putFloat("gnvCylinderCapacityM3", value).apply()

    var lastEngineOffPressure: Int
        get() = prefs.getInt("lastEngineOffPressure", -1)
        set(value) = prefs.edit().putInt("lastEngineOffPressure", value).apply()

    fun toJson(): JSONObject = JSONObject()
        .put("native", true)
        .put("autoStartEngine", autoStartEngine)
        .put("autoConnectUsb", autoConnectUsb)
        .put("autoReconnectUsb", autoReconnectUsb)
        .put("keepCpuAwake", keepCpuAwake)
        .put("baudRate", baudRate)
        .put("dataBits", dataBits)
        .put("stopBits", stopBits)
        .put("parity", parity)
        .put("dtr", dtr)
        .put("rts", rts)
        .put("preferredDeviceName", preferredDeviceName)
        .put("gpsEnabled", gpsEnabled)
        .put("gpsIntervalMs", gpsIntervalMs)
        .put("sessionRecorderEnabled", sessionRecorderEnabled)
        .put("sessionRecorderAutoStartOnUsb", sessionRecorderAutoStartOnUsb)
        .put("sessionTelemetryEveryMs", sessionTelemetryEveryMs)
        .put("sessionFullSnapshotEveryMs", sessionFullSnapshotEveryMs)
        .put("sessionCaptureRawUsb", sessionCaptureRawUsb)
        .put("sessionLogMaxMb", sessionLogMaxMb)
        .put("sessionKeepCount", sessionKeepCount)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("gnvCylinderCapacityM3", gnvCylinderCapacityM3.toDouble())
        .put("lastEngineOffPressure", lastEngineOffPressure)

    private fun getDouble(key: String, fallback: Double): Double =
        java.lang.Double.longBitsToDouble(
            prefs.getLong("${key}Bits", java.lang.Double.doubleToRawLongBits(fallback)),
        )

}
