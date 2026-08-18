package com.omegas.prohub.learning

import com.omegas.prohub.calibration.CalibrationIdentity
import org.json.JSONObject

/** Identidade mínima e imutável que acompanha ciência GNV. */
data class LearningCalibrationBinding(
    val calibrationFingerprint: String,
    val calibrationGeneration: Int,
    val geometryFingerprint: String,
    val usbSessionId: Long,
) {
    init {
        require(calibrationFingerprint.isNotBlank())
        require(calibrationGeneration >= 0)
        require(geometryFingerprint.isNotBlank())
        require(usbSessionId > 0L)
    }

    fun key(): String = "$calibrationFingerprint:$calibrationGeneration:$geometryFingerprint:$usbSessionId"

    fun toJson(): JSONObject = JSONObject()
        .put("calibration_fingerprint", calibrationFingerprint)
        .put("calibration_generation", calibrationGeneration)
        .put("geometry_fingerprint", geometryFingerprint)
        .put("usb_session_id", usbSessionId)

    companion object {
        fun fromIdentity(identity: CalibrationIdentity): LearningCalibrationBinding {
            require(identity.materiallyUsable()) { "CalibrationIdentity não está pronta para ciência GNV" }
            return LearningCalibrationBinding(
                calibrationFingerprint = identity.functionFingerprint,
                calibrationGeneration = identity.generation,
                geometryFingerprint = identity.geometryFingerprint,
                usbSessionId = identity.usbSessionId,
            )
        }

        fun fromJson(raw: JSONObject?): LearningCalibrationBinding? {
            if (raw == null) return null
            val fingerprint = raw.optString("calibration_fingerprint")
            val generation = raw.optInt("calibration_generation", -1)
            val geometry = raw.optString("geometry_fingerprint")
            val session = raw.optLong("usb_session_id", 0L)
            if (fingerprint.isBlank() || generation < 0 || geometry.isBlank() || session <= 0L) return null
            return LearningCalibrationBinding(fingerprint, generation, geometry, session)
        }
    }
}
