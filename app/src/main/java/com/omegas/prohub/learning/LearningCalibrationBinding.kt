package com.omegas.prohub.learning

import com.omegas.prohub.calibration.CalibrationIdentity
import com.omegas.prohub.calibration.MapGeometryCompleteness
import com.omegas.prohub.calibration.MapGeometrySnapshot
import org.json.JSONArray
import org.json.JSONObject

/** Identidade mínima e imutável que acompanha ciência GNV e sua geometria KNOWN. */
data class LearningCalibrationBinding(
    val calibrationFingerprint: String,
    val calibrationGeneration: Int,
    val geometryFingerprint: String,
    val usbSessionId: Long,
    val mapHash: String,
    val petrolAxisMs: List<Double>,
    val rpmAxis: List<Int>,
) {
    init {
        require(calibrationFingerprint.isNotBlank())
        require(calibrationGeneration >= 0)
        require(geometryFingerprint.isNotBlank())
        require(usbSessionId > 0L)
        require(mapHash.isNotBlank())
        require(petrolAxisMs.size == 12 && petrolAxisMs.all { it.isFinite() })
        require(rpmAxis.size == 12 && rpmAxis.all { it >= 0 })
    }

    /**
     * A sessão USB é provenance de aquisição, não identidade material da calibração.
     * Reabrir o mesmo MP48 não deve fabricar uma calibração nova.
     */
    fun key(): String = "$calibrationFingerprint:$calibrationGeneration:$geometryFingerprint"

    fun geometryKnown(): Boolean = petrolAxisMs.size == 12 && rpmAxis.size == 12

    fun toJson(): JSONObject = JSONObject()
        .put("calibration_fingerprint", calibrationFingerprint)
        .put("calibration_generation", calibrationGeneration)
        .put("geometry_fingerprint", geometryFingerprint)
        .put("usb_session_id", usbSessionId)
        .put("map_hash", mapHash)
        .put("petrol_axis_ms", JSONArray(petrolAxisMs))
        .put("rpm_axis", JSONArray(rpmAxis))
        .put("geometry_known", geometryKnown())

    companion object {
        fun fromIdentity(
            identity: CalibrationIdentity,
            geometry: MapGeometrySnapshot,
        ): LearningCalibrationBinding {
            require(identity.materiallyUsable()) { "CalibrationIdentity não está pronta para ciência GNV" }
            require(geometry.completeness == MapGeometryCompleteness.KNOWN) { "Geometria Map K não está KNOWN" }
            require(geometry.usbSessionId == identity.usbSessionId) { "Identidade e geometria pertencem a sessões diferentes" }
            require(geometry.fingerprint() == identity.geometryFingerprint) { "Fingerprint da geometria diverge da CalibrationIdentity" }
            return LearningCalibrationBinding(
                calibrationFingerprint = identity.functionFingerprint,
                calibrationGeneration = identity.generation,
                geometryFingerprint = identity.geometryFingerprint,
                usbSessionId = identity.usbSessionId,
                mapHash = identity.mapHash,
                petrolAxisMs = geometry.timeAxisMs.toList(),
                rpmAxis = geometry.rpmAxisRaw.toList(),
            )
        }

        fun fromJson(raw: JSONObject?): LearningCalibrationBinding? {
            if (raw == null) return null
            val fingerprint = raw.optString("calibration_fingerprint")
            val generation = raw.optInt("calibration_generation", -1)
            val geometry = raw.optString("geometry_fingerprint")
            val session = raw.optLong("usb_session_id", 0L)
            val mapHash = raw.optString("map_hash")
            val petrol = raw.optJSONArray("petrol_axis_ms") ?: return null
            val rpm = raw.optJSONArray("rpm_axis") ?: return null
            if (fingerprint.isBlank() || generation < 0 || geometry.isBlank() || session <= 0L || mapHash.isBlank()) return null
            if (petrol.length() != 12 || rpm.length() != 12) return null
            val petrolAxis = List(12) { petrol.optDouble(it, Double.NaN) }
            val rpmAxis = List(12) { rpm.optInt(it, -1) }
            if (petrolAxis.any { !it.isFinite() } || rpmAxis.any { it < 0 }) return null
            return LearningCalibrationBinding(fingerprint, generation, geometry, session, mapHash, petrolAxis, rpmAxis)
        }
    }
}
