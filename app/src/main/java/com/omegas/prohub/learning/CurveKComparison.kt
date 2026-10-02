package com.omegas.prohub.learning

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Leitura comparável da Curva K: ECU real de um lado, estimativa OMEGAS do outro.
 * Não lê porta, não escreve e não converte ausência de suporte em proposta.
 */
object CurveKComparison {
    const val SCHEMA = "omegas-curve-k-comparison-v1"

    fun build(ecuCurve: JSONObject?, learningSnapshot: JSONObject): JSONObject {
        val ecuPoints = ecuCurve?.optJSONArray("points") ?: JSONArray()
        val advisor = learningSnapshot.optJSONObject("assistedCalibration")
            ?: learningSnapshot.optJSONObject("assisted_calibration")
            ?: JSONObject()
        val suggestions = advisor.optJSONArray("kFactorSuggestions") ?: JSONArray()
        val adviceByIndex = linkedMapOf<Int, JSONObject>()
        repeat(suggestions.length()) { index ->
            val item = suggestions.optJSONObject(index) ?: return@repeat
            val pointIndex = item.optInt("index", -1)
            if (pointIndex in 0 until KFactorProtocol.POINT_COUNT) adviceByIndex[pointIndex] = item
        }
        val ecuByIndex = linkedMapOf<Int, JSONObject>()
        repeat(ecuPoints.length()) { index ->
            val item = ecuPoints.optJSONObject(index) ?: return@repeat
            val pointIndex = item.optInt("index", -1)
            if (pointIndex in 0 until KFactorProtocol.POINT_COUNT) ecuByIndex[pointIndex] = item
        }

        val points = JSONArray()
        var predicted = 0
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            val ecu = ecuByIndex[index]
            val advice = adviceByIndex[index]
            val currentFactor = ecu?.nullableDouble("factor")
            val deltaPercent = advice?.nullableDouble("suggestedDeltaPercent")
            val actionable = advice?.optBoolean("actionable", false) == true
            val targetFactor = if (currentFactor != null && deltaPercent != null && actionable) {
                normalizeFactor(currentFactor * (1.0 + deltaPercent / 100.0))
            } else null
            val state = when {
                currentFactor == null -> "ECU_NAO_CONFIRMADA"
                targetFactor != null -> "PREVISAO_OMEGAS"
                advice != null -> "OBSERVADO_SEM_PREVISAO"
                else -> "SEM_PREVISAO"
            }
            if (targetFactor != null) predicted += 1
            points.put(JSONObject()
                .put("index", index)
                .put("petrolMs", ecu?.nullableDouble("petrolMs") ?: advice?.nullableDouble("petrolMs") ?: JSONObject.NULL)
                .put("state", state)
                .put("ecuCurrentFactor", currentFactor ?: JSONObject.NULL)
                .put("omegasTargetFactor", targetFactor ?: JSONObject.NULL)
                .put("suggestedDeltaPercent", deltaPercent ?: JSONObject.NULL)
                .put("errorPercent", advice?.nullableDouble("errorPercent") ?: JSONObject.NULL)
                .put("uncertaintyPercent", advice?.nullableDouble("uncertaintyPercent") ?: JSONObject.NULL)
                .put("confidence", advice?.optDouble("confidence", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0)
                .put("confidenceStage", advice?.optString("confidenceStage", "OBSERVED") ?: "OBSERVED")
                .put("readiness", advice?.optString("readiness", "NO_EVIDENCE") ?: "NO_EVIDENCE")
                .put("sourceCurrent", if (currentFactor != null) "ECU_CONFIRMED_K_FACTOR" else JSONObject.NULL)
                .put("sourcePrediction", if (targetFactor != null) "OMEGAS_GLOBAL_ADVISOR" else JSONObject.NULL)
                .put("automaticWrite", false)
                .put("requiresHumanReview", targetFactor != null))
        }
        return JSONObject()
            .put("ok", true)
            .put("schema", SCHEMA)
            .put("pointCount", KFactorProtocol.POINT_COUNT)
            .put("ecuConfirmed", ecuPoints.length() == KFactorProtocol.POINT_COUNT)
            .put("predictedPoints", predicted)
            .put("points", points)
            .put("automaticWrite", false)
            .put("humanReviewRequired", true)
    }

    private fun normalizeFactor(value: Double): Double {
        val safe = value.coerceIn(0.60, KFactorProtocol.MAX_FACTOR)
        return KFactorProtocol.factorFromRaw(KFactorProtocol.rawFromFactor(safe))
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
}
