package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import org.json.JSONObject
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

    /**
     * Projeta o witness sobre uma saída Blue já calculada.
     *
     * O JSON de entrada é clonado antes da projeção; correctionMultiplier,
     * target K, actuator gain e qualquer outro campo causal permanecem intactos.
     * Um witness de outra revisão é tratado como indisponível.
     */
    fun project(
        baseJson: JSONObject,
        blueErrorPercent: Double,
        baseQuality: Double,
        witness: JSONObject?,
        expectedCalibrationState: String,
    ): JSONObject {
        val projected = JSONObject(baseJson.toString())
        val expected = expectedCalibrationState.trim()
        val sourceCalibrationState = witness?.optString("calibrationState", "")?.trim().orEmpty()
        val sameCalibration = witness != null && expected.isNotBlank() && sourceCalibrationState == expected
        val sourceState = witness?.optString("state", ObdWitnessState.UNAVAILABLE.name)
            ?.trim()?.uppercase().orEmpty()
        val witnessQuality = if (sameCalibration) {
            witness?.optDouble("quality", 0.0)?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        } else {
            0.0
        }
        val residual = if (sameCalibration && witness != null && witness.has("residualPp") && !witness.isNull("residualPp")) {
            witness.optDouble("residualPp", Double.NaN).takeIf(Double::isFinite)
        } else {
            null
        }
        val base = baseQuality.coerceIn(0.0, 1.0)
        val assessment = when {
            !sameCalibration || sourceState == ObdWitnessState.UNAVAILABLE.name ->
                BlueWitnessAssessment(ObdWitnessState.UNAVAILABLE, base, base, witnessQuality)
            sourceState == ObdWitnessState.INSUFFICIENT.name && residual == null ->
                BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, witnessQuality)
            else -> assess(blueErrorPercent, base, residual, witnessQuality)
        }

        return projected
            .put("baseConfidence", assessment.baseConfidence)
            .put("effectiveConfidence", assessment.effectiveConfidence)
            .put(
                "obdWitness",
                JSONObject()
                    .put("state", assessment.state.name)
                    .put("sourceState", sourceState.ifBlank { ObdWitnessState.UNAVAILABLE.name })
                    .put("quality", assessment.obdQuality)
                    .put("residualPp", residual ?: JSONObject.NULL)
                    .put("calibrationState", sourceCalibrationState.ifBlank { JSONObject.NULL })
                    .put("expectedCalibrationState", expected.ifBlank { JSONObject.NULL }),
            )
    }
}

data class BlueWitnessAssessment(
    val state: ObdWitnessState,
    val baseConfidence: Double,
    val effectiveConfidence: Double,
    val obdQuality: Double,
)
