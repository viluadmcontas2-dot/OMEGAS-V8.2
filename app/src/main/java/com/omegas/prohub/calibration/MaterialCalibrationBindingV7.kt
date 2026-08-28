package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7

/**
 * Ponte explícita entre a identidade física NEXT e o contador legado V7.
 * A revision permanece somente como projeção/compatibilidade; igualdade material
 * usa exclusivamente o fingerprint físico versionado.
 */
data class MaterialCalibrationBindingV7(
    val functionFingerprint: String,
    val revisionProjection: CalibrationRevisionV7,
) {
    init {
        require(functionFingerprint.length == 64) { "Fingerprint físico inválido" }
    }

    fun samePhysicalCalibration(other: MaterialCalibrationBindingV7): Boolean =
        functionFingerprint == other.functionFingerprint
}
