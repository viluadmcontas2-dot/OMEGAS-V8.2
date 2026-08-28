package com.omegas.prohub.telemetry

/**
 * Gerações independentes do runtime. Nenhuma delas substitui outra.
 * Predictor/UI são derivados; USB+Calibration+Learning definem os inputs científicos.
 */
data class RuntimeGenerationVector(
    val usbSessionId: Long,
    val calibrationFunctionFingerprint: String,
    val calibrationGeneration: Int,
    val learningGeneration: Long,
    val predictorGeneration: Long,
    val uiRevision: Long,
) {
    init {
        require(usbSessionId > 0L) { "usbSessionId inválida" }
        require(calibrationFunctionFingerprint.length == 64) { "calibrationFunctionFingerprint inválido" }
        require(calibrationGeneration >= 0) { "calibrationGeneration inválida" }
        require(learningGeneration >= 0L) { "learningGeneration inválida" }
        require(predictorGeneration >= 0L) { "predictorGeneration inválida" }
        require(uiRevision >= 0L) { "uiRevision inválida" }
    }

    fun sameScientificInputs(other: RuntimeGenerationVector): Boolean =
        usbSessionId == other.usbSessionId &&
            calibrationFunctionFingerprint == other.calibrationFunctionFingerprint &&
            calibrationGeneration == other.calibrationGeneration &&
            learningGeneration == other.learningGeneration

    fun samePresentationGeneration(other: RuntimeGenerationVector): Boolean =
        predictorGeneration == other.predictorGeneration && uiRevision == other.uiRevision
}
