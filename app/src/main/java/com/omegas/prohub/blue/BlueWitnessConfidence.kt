package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Optional, asymmetric confidence adapter.
 *
 * OBD may reward an agreeing MP48 conclusion. It never changes MP48 error/target
 * and absence or disagreement never blocks a valid MP48 result.
 */
object BlueWitnessConfidence {
    private const val BLUE_DEADBAND = 0.50
    private const val OBD_DEADBAND = 0.50
    private const val MAX_SUPPORT_BOOST = 0.25

    fun assess(
        blueErrorPercent: Double,
        baseQuality: Double,
        obdGnvStftPct: Double?,
        obdQuality: Double,
    ): BlueWitnessAssessment {
        val base = baseQuality.coerceIn(0.0, 1.0)
        val quality = obdQuality.coerceIn(0.0, 1.0)
        if (!blueErrorPercent.isFinite() || obdGnvStftPct == null || !obdGnvStftPct.isFinite()) {
            return BlueWitnessAssessment(ObdWitnessState.UNAVAILABLE, base, base, quality)
        }
        if (abs(blueErrorPercent) <= BLUE_DEADBAND || abs(obdGnvStftPct) <= OBD_DEADBAND || quality <= 0.0) {
            return BlueWitnessAssessment(ObdWitnessState.INSUFFICIENT, base, base, quality)
        }
        val supports = (blueErrorPercent > 0.0) == (obdGnvStftPct > 0.0)
        if (!supports) return BlueWitnessAssessment(ObdWitnessState.CONFLICTS, base, base, quality)
        val boosted = (base + (1.0 - base) * MAX_SUPPORT_BOOST * quality).coerceIn(base, 1.0)
        return BlueWitnessAssessment(ObdWitnessState.SUPPORTS, base, boosted, quality)
    }

    fun project(
        baseJson: JSONObject,
        blueErrorPercent: Double,
        baseQuality: Double,
        witness: JSONObject?,
        @Suppress("UNUSED_PARAMETER") expectedCalibrationState: String,
        expectedRpm: Double,
        expectedMapBar: Double,
        @Suppress("UNUSED_PARAMETER") expectedPetrolOnCngMs: Double,
    ): JSONObject {
        val projected = JSONObject(baseJson.toString())
        val rpm = witness?.optDouble("rpm", Double.NaN) ?: Double.NaN
        val map = witness?.optDouble("map_bar", witness.optDouble("mapBar", Double.NaN)) ?: Double.NaN
        val rpmWindow = max(125.0, expectedRpm * 0.05)
        val regionMatched = witness != null && rpm.isFinite() && map.isFinite() &&
            abs(rpm - expectedRpm) <= rpmWindow && abs(map - expectedMapBar) <= 0.05
        val sourceState = witness?.optString("state", ObdWitnessState.UNAVAILABLE.name)?.uppercase()
            ?: ObdWitnessState.UNAVAILABLE.name
        val stft = if (regionMatched && witness != null) {
            witness.optDouble("stftMedianPct", witness.optDouble("gnvStftPct", Double.NaN)).takeIf(Double::isFinite)
        } else null
        val quality = if (regionMatched) witness?.optDouble("quality", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0 else 0.0
        val base = baseQuality.coerceIn(0.0, 1.0)
        val assessment = if (!regionMatched) {
            BlueWitnessAssessment(
                if (witness == null) ObdWitnessState.UNAVAILABLE else ObdWitnessState.INSUFFICIENT,
                base, base, quality,
            )
        } else assess(blueErrorPercent, base, stft, quality)
        return projected
            .put("baseConfidence", assessment.baseConfidence)
            .put("effectiveConfidence", assessment.effectiveConfidence)
            .put("obdWitness", JSONObject()
                .put("role", "OPTIONAL_NON_BLOCKING_CONFIDENCE")
                .put("state", assessment.state.name)
                .put("sourceState", sourceState)
                .put("quality", assessment.obdQuality)
                .put("gnvStftPct", stft ?: JSONObject.NULL)
                .put("regionMatched", regionMatched))
    }
}

data class BlueWitnessAssessment(
    val state: ObdWitnessState,
    val baseConfidence: Double,
    val effectiveConfidence: Double,
    val obdQuality: Double,
)
