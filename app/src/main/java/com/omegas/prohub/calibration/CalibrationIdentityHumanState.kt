package com.omegas.prohub.calibration

data class CalibrationIdentityHumanProjection(
    val label: String,
    val reasonCode: String,
    val actionable: Boolean,
)

/** Linguagem humana derivada; não decide ciência nem expõe hash como mensagem principal. */
object CalibrationIdentityHumanState {
    fun project(
        completeness: CalibrationCompleteness,
        freshness: CalibrationFreshness,
    ): CalibrationIdentityHumanProjection = when {
        freshness == CalibrationFreshness.STALE -> CalibrationIdentityHumanProjection(
            label = "Calibração mudou",
            reasonCode = "CALIBRATION_CHANGED",
            actionable = false,
        )
        completeness == CalibrationCompleteness.KNOWN && freshness == CalibrationFreshness.CURRENT_SESSION ->
            CalibrationIdentityHumanProjection("Pronto", "CALIBRATION_READY", true)
        completeness == CalibrationCompleteness.PARTIAL ->
            CalibrationIdentityHumanProjection("Dados incompletos", "CALIBRATION_PARTIAL", false)
        else -> CalibrationIdentityHumanProjection("Revalidar calibração", "CALIBRATION_REVALIDATE", false)
    }
}
