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
                edit.putInt("sessionKeepCount", 3)
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

    var lanServerEnabled: Boolean
        get() = prefs.getBoolean("lanServerEnabled", false)
        set(value) = prefs.edit().putBoolean("lanServerEnabled", value).apply()
    var lanServerPort: Int
        get() = prefs.getInt("lanServerPort", 8_088)
        set(value) = prefs.edit().putInt("lanServerPort", value.coerceIn(1_024, 65_535)).apply()
    var lanAccessToken: String
        get() {
            val existing = prefs.getString("lanAccessToken", "") ?: ""
            if (existing.length >= 6) return existing
            val generated = (100_000 + SecureRandom().nextInt(900_000)).toString()
            prefs.edit().putString("lanAccessToken", generated).apply()
            return generated
        }
        set(value) = prefs.edit()
            .putString("lanAccessToken", value.filter(Char::isLetterOrDigit).take(24))
            .apply()

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
        get() = prefs.getInt("sessionKeepCount", 3)
        set(value) = prefs.edit().putInt("sessionKeepCount", value.coerceIn(1, 20)).apply()

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

    var linkEnabled: Boolean
        get() = prefs.getBoolean("linkEnabled", false)
        set(value) = prefs.edit().putBoolean("linkEnabled", value).apply()
    var linkRolePreference: String
        get() = prefs.getString("linkRolePreference", "auto") ?: "auto"
        set(value) = prefs.edit().putString("linkRolePreference", value.lowercase()).apply()
    var linkPairCode: String
        get() {
            val existing = prefs.getString("linkPairCode", "") ?: ""
            if (existing.length == 6) return existing
            val generated = (100_000 + SecureRandom().nextInt(900_000)).toString()
            prefs.edit().putString("linkPairCode", generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString("linkPairCode", value.filter(Char::isDigit).take(6)).apply()
    var linkDiscoveryPort: Int
        get() = prefs.getInt("linkDiscoveryPort", 43_144)
        set(value) = prefs.edit().putInt("linkDiscoveryPort", value.coerceIn(1_024, 65_535)).apply()
    var linkDataPort: Int
        get() = prefs.getInt("linkDataPort", 43_145)
        set(value) = prefs.edit().putInt("linkDataPort", value.coerceIn(1_024, 65_535)).apply()
    var linkAutoSyncSeconds: Long
        get() = prefs.getLong("linkAutoSyncSeconds", 15L)
        set(value) = prefs.edit().putLong("linkAutoSyncSeconds", value.coerceIn(3L, 300L)).apply()
    var linkTrustedPeerId: String
        get() = prefs.getString("linkTrustedPeerId", "") ?: ""
        set(value) = prefs.edit().putString("linkTrustedPeerId", value).apply()
    var linkControlEpoch: Long
        get() = prefs.getLong("linkControlEpoch", 0L)
        set(value) = prefs.edit().putLong("linkControlEpoch", value).apply()

    var obdMode: String
        get() = prefs.getString("obdMode", "off") ?: "off"
        set(value) = prefs.edit().putString("obdMode", value.lowercase()).apply()
    /** Declaração do operador quando a MP48 não está disponível. Nunca qualifica mapa. */
    var obdManualFuel: String
        get() = prefs.getString("obdManualFuel", "") ?: ""
        set(value) = prefs.edit().putString("obdManualFuel", value.uppercase()).apply()
    var obdDeviceAddress: String
        get() = prefs.getString("obdDeviceAddress", "") ?: ""
        set(value) = prefs.edit().putString("obdDeviceAddress", value.trim()).apply()
    var obdAutoConnect: Boolean
        get() = prefs.getBoolean("obdAutoConnect", false)
        set(value) = prefs.edit().putBoolean("obdAutoConnect", value).apply()
    var obdPollIntervalMs: Long
        get() = prefs.getLong("obdPollIntervalMs", 350L)
        set(value) = prefs.edit().putLong("obdPollIntervalMs", value.coerceIn(150L, 3_000L)).apply()
    var obdMinimumCoolantC: Double
        get() = getDouble("obdMinimumCoolantC", 70.0)
        set(value) = putDouble("obdMinimumCoolantC", value.coerceIn(30.0, 110.0))
    var obdMaxRpmDifference: Double
        get() = getDouble("obdMaxRpmDifference", 250.0)
        set(value) = putDouble("obdMaxRpmDifference", value.coerceIn(20.0, 1_500.0))
    /** Janela máxima para parear um frame OBD ao frame MP48 real. */
    var obdMaxPairSkewMs: Long
        get() = prefs.getLong("obdMaxPairSkewMs", 250L)
        set(value) = prefs.edit().putLong("obdMaxPairSkewMs", value.coerceIn(50L, 3_000L)).apply()
    /** PIDs lentos mantêm seu próprio horário e só valem enquanto estiverem frescos. */
    var obdMaxContextAgeMs: Long
        get() = prefs.getLong("obdMaxContextAgeMs", 15_000L)
        set(value) = prefs.edit().putLong("obdMaxContextAgeMs", value.coerceIn(1_000L, 60_000L)).apply()
    var obdMinimumSamplesPerCell: Long
        get() = prefs.getLong("obdMinimumSamplesPerCell", 8L)
        set(value) = prefs.edit().putLong("obdMinimumSamplesPerCell", value.coerceIn(2L, 500L)).apply()
    var obdNeutralBandPct: Double
        get() = getDouble("obdNeutralBandPct", 2.0)
        set(value) = putDouble("obdNeutralBandPct", value.coerceIn(0.2, 15.0))
    var obdDivergenceBandPct: Double
        get() = getDouble("obdDivergenceBandPct", 7.0)
        set(value) = putDouble("obdDivergenceBandPct", value.coerceIn(1.0, 30.0))
    var obdGasFlowCoefficient: Double
        get() = getDouble("obdGasFlowCoefficient", 0.000005)
        set(value) = putDouble("obdGasFlowCoefficient", value.coerceIn(0.00000001, 0.01))
    var obdCylinderCount: Int
        get() = prefs.getInt("obdCylinderCount", 4)
        set(value) = prefs.edit().putInt("obdCylinderCount", value.coerceIn(1, 12)).apply()

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
        .put("lanServerEnabled", lanServerEnabled)
        .put("lanServerPort", lanServerPort)
        .put("lanAccessToken", lanAccessToken)
        .put("sessionRecorderEnabled", sessionRecorderEnabled)
        .put("sessionRecorderAutoStartOnUsb", sessionRecorderAutoStartOnUsb)
        .put("sessionTelemetryEveryMs", sessionTelemetryEveryMs)
        .put("sessionFullSnapshotEveryMs", sessionFullSnapshotEveryMs)
        .put("sessionCaptureRawUsb", sessionCaptureRawUsb)
        .put("sessionLogMaxMb", sessionLogMaxMb)
        .put("sessionKeepCount", sessionKeepCount)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("linkEnabled", linkEnabled)
        .put("linkRolePreference", linkRolePreference)
        .put("linkPairCode", linkPairCode)
        .put("linkDiscoveryPort", linkDiscoveryPort)
        .put("linkDataPort", linkDataPort)
        .put("linkAutoSyncSeconds", linkAutoSyncSeconds)
        .put("obdMode", obdMode)
        .put("obdManualFuel", obdManualFuel)
        .put("obdDeviceAddress", obdDeviceAddress)
        .put("obdAutoConnect", obdAutoConnect)
        .put("obdPollIntervalMs", obdPollIntervalMs)
        .put("obdMinimumCoolantC", obdMinimumCoolantC)
        .put("obdMaxRpmDifference", obdMaxRpmDifference)
        .put("obdMaxPairSkewMs", obdMaxPairSkewMs)
        .put("obdMaxContextAgeMs", obdMaxContextAgeMs)
        .put("obdMinimumSamplesPerCell", obdMinimumSamplesPerCell)
        .put("obdNeutralBandPct", obdNeutralBandPct)
        .put("obdDivergenceBandPct", obdDivergenceBandPct)
        .put("obdGasFlowCoefficient", obdGasFlowCoefficient)
        .put("obdCylinderCount", obdCylinderCount)
        .put("gnvCylinderCapacityM3", gnvCylinderCapacityM3.toDouble())
        .put("lastEngineOffPressure", lastEngineOffPressure)

    private fun getDouble(key: String, fallback: Double): Double =
        java.lang.Double.longBitsToDouble(
            prefs.getLong("${key}Bits", java.lang.Double.doubleToRawLongBits(fallback)),
        )

    private fun putDouble(key: String, value: Double) {
        prefs.edit().putLong("${key}Bits", java.lang.Double.doubleToRawLongBits(value)).apply()
    }
}
