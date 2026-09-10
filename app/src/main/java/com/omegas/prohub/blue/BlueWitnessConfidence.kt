package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessPolicy
import com.omegas.prohub.obd.ObdWitnessState
import org.json.JSONObject
import kotlin.math.abs

/**
 * Adapta o residual OBD para a camada de confiança do Blue.
 *
 * Não conhece Map K, Curve K, writers, actuator gain nem correction target.
 * STFT nunca entra na matemática do alvo. Quando concorda, pode acelerar a
 * confiança; quando contradiz o erro primário já medido, a ação é pausada para
 * coletar mais evidência sem apagar nem recalcular o resultado do Petrol Inj.
 */
object BlueWitnessConfidence {
    private const val BLUE_ERROR_DEADBAND_PERCENT = 0.50
    private const val OBD_STFT_DEADBAND_PERCENT = 0.50
    private const val MAX_SUPPORT_BOOST = 0.25

    fun assess(
        blueErrorPercent: Double,
        baseQuality: Double,
        obdGnvStftPct: Double?,
        obdQuality: Double,
    ): BlueWitnessAssessment {
        val base = baseQuality.coerceIn(0.0, 1.0)
        val witnessQuality = obdQuality.coerceIn(0.0, 1.0)
        if (!blueErrorPercent.isFinite() || obdGnvStftPct == null || !obdGnvStftPct.isFinite()) {
            return BlueWitnessAssessment(ObdWitnessState.UNAVAILABLE, base, base, witnessQuality)
        }
        if (
            abs(blueErrorPercent) <= BLUE_ERROR_DEADBAND_PERCENT ||
            abs(obdGnvStftPct) <= OBD_STFT_DEADBAND_PERCENT ||
            witnessQuality <= 0.0
        ) {
            return BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, witnessQuality)
        }

        val supports = blueErrorPercent > 0.0 == (obdGnvStftPct > 0.0)
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
     * Um witness de outra revisão é tratado como indisponível. Um conflito não
     * altera a matemática causal, mas torna a ação indisponível até nova evidência.
     */
    fun project(
        baseJson: JSONObject,
        blueErrorPercent: Double,
        baseQuality: Double,
        witness: JSONObject?,
        expectedCalibrationState: String,
        expectedRpm: Double,
        expectedMapBar: Double,
        expectedPetrolOnCngMs: Double,
    ): JSONObject {
        val projected = JSONObject(baseJson.toString())
        val expected = expectedCalibrationState.trim()
        val sourceCalibrationState = witness?.optString("calibrationState", "")?.trim().orEmpty()
        val sameCalibration = witness != null && expected.isNotBlank() && sourceCalibrationState == expected
        val sourceState = witness?.optString("state", ObdWitnessState.UNAVAILABLE.name)
            ?.trim()?.uppercase().orEmpty()
        val regionMatched = sameCalibration && witness != null && ObdWitnessPolicy().matches(
            sampleRpm = witness.optDouble("rpm", Double.NaN),
            sampleMapBar = witness.optDouble("map_bar", witness.optDouble("mapBar", Double.NaN)),
            samplePetrolMs = witness.optDouble("petrol_ms", witness.optDouble("petrolMs", Double.NaN)),
            targetRpm = expectedRpm,
            targetMapBar = expectedMapBar,
            targetPetrolMs = expectedPetrolOnCngMs,
        )
        val witnessQuality = if (sameCalibration && regionMatched) {
            witness?.optDouble("quality", 0.0)?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        } else {
            0.0
        }
        val gnvStft = if (sameCalibration && regionMatched && witness != null && witness.has("gnvStftPct") && !witness.isNull("gnvStftPct")) {
            witness.optDouble("gnvStftPct", Double.NaN).takeIf(Double::isFinite)
        } else {
            null
        }
        val base = baseQuality.coerceIn(0.0, 1.0)
        val assessment = when {
            !sameCalibration || sourceState == ObdWitnessState.UNAVAILABLE.name ->
                BlueWitnessAssessment(ObdWitnessState.UNAVAILABLE, base, base, witnessQuality)
            !regionMatched ->
                BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, witnessQuality)
            sourceState == ObdWitnessState.INSUFFICIENT.name && gnvStft == null ->
                BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, witnessQuality)
            else -> assess(blueErrorPercent, base, gnvStft, witnessQuality)
        }

        if (assessment.state == ObdWitnessState.CONFLICTS && projected.optBoolean("available", false)) {
            projected
                .put("available", false)
                .put("state", "OBD_CONFLICT_COLLECT_MORE")
                .put("witnessActionGate", "COLLECT_MORE")
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
                    .put("gnvStftPct", gnvStft ?: JSONObject.NULL)
                    .put("residualPp", JSONObject.NULL)
                    .put("regionMatched", regionMatched)
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
