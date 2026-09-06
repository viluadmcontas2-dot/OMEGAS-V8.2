package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import kotlin.math.abs

/**
 * Adapta o residual OBD para a camada de confiança do Blue.
 *
 * Não conhece Map K, Curve K, writers, actuator gain nem correction target.
 * Seu único efeito possível é acelerar confiança quando a direção física
 * independente concorda com o erro já calculado pelo Blue.
 */
object BlueWitnessConfidence {
    private const val BLUE_ERROR_DEADBAND_PERCENT = 0.50
    private const val OBD_RESIDUAL_DEADBAND_PP = 0.50
    private const val MAX_SUPPORT_BOOST = 0.25

    fun assess(
        blueErrorPercent: Double,
        baseQuality: Double,
        obdResidualPp: Double?,
        obdQuality: Double,
    ): BlueWitnessAssessment {
        val base = baseQuality.coerceIn(0.0, 1.0)
        val witnessQuality = obdQuality.coerceIn(0.0, 1.0)
        if (!blueErrorPercent.isFinite() || obdResidualPp == null || !obdResidualPp.isFinite()) {
            return BlueWitnessAssessment(ObdWitnessState.UNAVAILABLE, base, base, witnessQuality)
        }
        if (
            abs(blueErrorPercent) <= BLUE_ERROR_DEADBAND_PERCENT ||
            abs(obdResidualPp) <= OBD_RESIDUAL_DEADBAND_PP ||
            witnessQuality <= 0.0
        ) {
            return BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, witnessQuality)
        }

        val supports = blueErrorPercent > 0.0 == (obdResidualPp > 0.0)
        if (!supports) {
            return BlueWitnessAssessment(ObdWitnessState.CONFLICTS, base, base, witnessQuality)
        }

        val effective = (base + (1.0 - base) * MAX_SUPPORT_BOOST * witnessQuality).coerceIn(base, 1.0)
        return BlueWitnessAssessment(ObdWitnessState.SUPPORTS, base, effective, witnessQuality)
    }
}

data class BlueWitnessAssessment(
    val state: ObdWitnessState,
    val baseConfidence: Double,
    val effectiveConfidence: Double,
    val obdQuality: Double,
)
