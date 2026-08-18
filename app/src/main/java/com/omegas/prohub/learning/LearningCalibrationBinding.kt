package com.omegas.prohub.learning

import com.omegas.prohub.calibration.CalibrationIdentity
import org.json.JSONObject

/** Identidade mínima e imutável que acompanha ciência GNV. */
data class LearningCalibrationBinding(
    val calibrationFingerprint: String,
    val calibrationGeneration: Int,
    val geometryFingerprint: String,
    val usbSessionId: Long,
    val mapHash: String,
) {
    init {
        require(calibrationFingerprint.isNotBlank())
        require(calibrationGeneration >= 0)
        require(geometryFingerprint.isNotBlank())
        require(usbSessionId > 0L)
        require(mapHash.isNotBlank())
    }

    fun key(): String = "$calibrationFingerprint:$calibrationGeneration:$geometryFingerprint:$usbSessionId"

    fun toJson(): JSONObject = JSONObject()
        .put("calibration_fingerprint", calibrationFingerprint)
        .put("calibration_generation", calibrationGeneration)
        .put("geometry_fingerprint", geometryFingerprint)
        .put("usb_session_id", usbSessionId)
        .put("map_hash", mapHash)

    companion object {
        fun fromIdentity(identity: CalibrationIdentity): LearningCalibrationBinding {
            require(identity.materiallyUsable()) { "CalibrationIdentity não está pronta para ciência GNV" }
            return LearningCalibrationBinding(
                calibrationFingerprint = identity.functionFingerprint,
                calibrationGeneration = identity.generation,
                geometryFingerprint = identity.geometryFingerprint,
                usbSessionId = identity.usbSessionId,
                mapHash = identity.mapHash,
            )
        }

        fun fromJson(raw: JSONObject?): LearningCalibrationBinding? {
            if (raw == null) return null
            val fingerprint = raw.optString("calibration_fingerprint")
            val generation = raw.optInt("calibration_generation", -1)
            val geometry = raw.optString("geometry_fingerprint")
            val session = raw.optLong("usb_session_id", 0L)
            val mapHash = raw.optString("map_hash")
            if (fingerprint.isBlank() || generation < 0 || geometry.isBlank() || session <= 0L || mapHash.isBlank()) return null
            return LearningCalibrationBinding(fingerprint, generation, geometry, session, mapHash)
        }
    }
}
