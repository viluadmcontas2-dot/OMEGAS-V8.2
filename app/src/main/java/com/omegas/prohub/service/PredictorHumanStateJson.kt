package com.omegas.prohub.service

import com.omegas.prohub.learning.PredictorHumanProjectionInput
import com.omegas.prohub.learning.PredictorHumanRiskState
import com.omegas.prohub.learning.PredictorHumanScientificState
import com.omegas.prohub.learning.PredictorHumanState
import com.omegas.prohub.learning.PredictorHumanStateProjector
import com.omegas.prohub.learning.PredictorTargetRange
import com.omegas.prohub.physics.MagnitudeAuthority
import org.json.JSONObject

/** Native projection boundary consumed by the single V7 calibration Store. */
internal fun JSONObject.withPredictorHumanState(): JSONObject {
    val projected = JSONObject(toString())
    val cells = projected.optJSONArray("cells") ?: return projected
    repeat(cells.length()) { index ->
        val cell = cells.optJSONObject(index) ?: return@repeat
        val human = try {
            PredictorHumanStateProjector.project(cell.toHumanProjectionInput())
        } catch (_: Exception) {
            PredictorHumanStateProjector.project(
                PredictorHumanProjectionInput(
                    currentK = cell.validKInt("currentK"),
                    targetEstimateK = null,
                    targetRange = null,
                    authority = MagnitudeAuthority.UNKNOWN,
                    scientificState = PredictorHumanScientificState.UNKNOWN_ABSTAIN,
                    riskState = PredictorHumanRiskState.BLOCKED,
                    confidence = null,
                    reasonCode = "HUMAN_STATE_INPUT_INVALID",
                ),
            )
        }
        cell.put("humanState", human.toJson())
    }
    return projected
}

private fun JSONObject.toHumanProjectionInput(): PredictorHumanProjectionInput = PredictorHumanProjectionInput(
    currentK = validKInt("currentK"),
    targetEstimateK = nullableKDouble("targetEstimateK") ?: nullableKDouble("targetK"),
    targetRange = explicitTargetRange(),
    authority = explicitAuthority(),
    scientificState = explicitScientificState(),
    riskState = explicitRiskState(),
    confidence = nullableUnitDouble("predictionConfidence") ?: nullableUnitDouble("confidence"),
    reasonCode = optString("stateReason", optString("predictionReason", "UPSTREAM_REASON_UNAVAILABLE"))
        .ifBlank { "UPSTREAM_REASON_UNAVAILABLE" },
)

private fun JSONObject.explicitAuthority(): MagnitudeAuthority {
    val raw = optString("magnitudeAuthority", optString("authority", ""))
    return MagnitudeAuthority.entries.firstOrNull { it.name == raw } ?: MagnitudeAuthority.UNKNOWN
}

private fun JSONObject.explicitRiskState(): PredictorHumanRiskState {
    val raw = optString("humanRiskState", optString("riskState", ""))
    return PredictorHumanRiskState.entries.firstOrNull { it.name == raw }
        ?: PredictorHumanRiskState.RISK_NOT_CALIBRATED
}

private fun JSONObject.explicitScientificState(): PredictorHumanScientificState {
    val explicit = optString(
        "scientificState",
        optString("relativePredictionState", optString("predictionState", "")),
    )
    PredictorHumanScientificState.entries.firstOrNull { it.name == explicit }?.let { return it }
    return when (optString("state")) {
        "VALIDADO" -> PredictorHumanScientificState.DIRECT_CONFIRMED
        "OBSERVADO" -> PredictorHumanScientificState.DIRECT_PROVISIONAL
        "PREVISTO" -> PredictorHumanScientificState.PREDICTED_INTERPOLATED
        else -> PredictorHumanScientificState.UNKNOWN_ABSTAIN
    }
}

private fun JSONObject.explicitTargetRange(): PredictorTargetRange? {
    val objectRange = optJSONObject("targetRange")
    if (objectRange != null) {
        val lower = objectRange.nullableFiniteDouble("lowerK") ?: return null
        val upper = objectRange.nullableFiniteDouble("upperK") ?: return null
        val basis = objectRange.optString("basis").takeIf { it.isNotBlank() } ?: return null
        return PredictorTargetRange(lower, upper, basis)
    }
    val lower = nullableFiniteDouble("targetLowerK") ?: return null
    val upper = nullableFiniteDouble("targetUpperK") ?: return null
    val basis = optString("targetRangeBasis").takeIf { it.isNotBlank() } ?: return null
    return PredictorTargetRange(lower, upper, basis)
}

private fun PredictorHumanState.toJson(): JSONObject = JSONObject()
    .put("visualState", visualState.name)
    .put("scientificState", scientificState.name)
    .put("authority", authority.name)
    .put("riskState", riskState.name)
    .put("actionState", actionState.name)
    .put("stateLabel", stateLabel)
    .put("targetLabel", targetLabel)
    .put("reason", reason)
    .put("disclosure", disclosure)
    .put("confidence", confidence)
    .put("currentK", currentK)
    .put("targetEstimateK", targetEstimateK)
    .put("intervalLowerK", intervalLowerK)
    .put("intervalUpperK", intervalUpperK)
    .put("intervalBasis", intervalBasis)
    .put("requiresHumanReview", requiresHumanReview)
    .put("predicted", predicted)
    .put("directObservation", directObservation)

private fun JSONObject.validKInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key).takeIf { it in 0..255 } else null

private fun JSONObject.nullableKDouble(key: String): Double? =
    nullableFiniteDouble(key)?.takeIf { it in 0.0..255.0 }

private fun JSONObject.nullableUnitDouble(key: String): Double? =
    nullableFiniteDouble(key)?.takeIf { it in 0.0..1.0 }

private fun JSONObject.nullableFiniteDouble(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
