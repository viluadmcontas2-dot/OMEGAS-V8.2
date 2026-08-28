package com.omegas.prohub.calibration

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Identifica exclusivamente a função física vigente da calibração. */
object CalibrationFunctionFingerprint {
    const val SCHEMA = "mp48-calibration-function-v1"

    fun from(snapshot: CompositeCalibrationSnapshot): String {
        require(snapshot.generationStable) { "Snapshot composto instável" }
        val digest = MessageDigest.getInstance("SHA-256")
        fun component(label: String, value: String) {
            digest.update(label.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
        }
        component("schema", SCHEMA)
        component("geometry", snapshot.mapGeometry.fingerprint())
        component("map", snapshot.mapHash)
        component("curveAxis", snapshot.curve.axisFingerprint())
        component("curveFactors", snapshot.curve.factorsFingerprint())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
