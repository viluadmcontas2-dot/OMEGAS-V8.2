package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Monta a alteração manual sem escrever nem acessar a porta USB. */
object KFactorManualPlanner {
    private const val MINIMUM_SAFE_FACTOR = 0.60
    private const val MAXIMUM_FACTOR = KFactorProtocol.MAX_FACTOR
    private const val MAXIMUM_INPUT_FACTOR = 4.0

    fun preview(runtimeRoot: File, index: Int, targetFactor: Double): JSONObject = try {
        require(index in 0 until KFactorProtocol.POINT_COUNT) { "Ponto K factor inválido" }
        require(targetFactor.isFinite() && targetFactor in MINIMUM_SAFE_FACTOR..MAXIMUM_INPUT_FACTOR) {
            "Informe um fator entre 0,60 e 4,00"
        }
        val cache = File(runtimeRoot, "k_factor_cache.json")
        require(cache.isFile) { "Leia a curva K factor nesta conexão" }
        val root = JSONObject(cache.readText(Charsets.UTF_8))
        require(root.optBoolean("complete") && root.optBoolean("sessionConfirmed")) {
            "Leia a curva K factor nesta conexão"
        }
        val axis = root.optJSONArray("axisRaw") ?: JSONArray()
        val factors = root.optJSONArray("factorsRaw") ?: JSONArray()
        require(axis.length() == KFactorProtocol.POINT_COUNT && factors.length() == KFactorProtocol.POINT_COUNT) {
            "Cache da curva incompleto"
        }
        val currentRaw = factors.getInt(index)
        val targetRaw = KFactorProtocol.rawFromFactor(targetFactor)
        val normalizedTarget = KFactorProtocol.factorFromRaw(targetRaw)
        val currentFactor = KFactorProtocol.factorFromRaw(currentRaw)
        JSONObject()
            .put("ok", true)
            .put("index", index)
            .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(axis.getInt(index)))
            .put("currentRaw", currentRaw)
            .put("targetRaw", targetRaw)
            .put("currentFactor", currentFactor)
            .put("targetFactor", normalizedTarget)
            .put("requestedFactor", targetFactor)
            .put("saturatedAtMaximum", targetFactor > MAXIMUM_FACTOR)
            .put("minimumFactor", MINIMUM_SAFE_FACTOR)
            .put("maximumFactor", MAXIMUM_FACTOR)
            .put("maximumInputFactor", MAXIMUM_INPUT_FACTOR)
            .put("minimumRaw", KFactorProtocol.rawFromFactor(MINIMUM_SAFE_FACTOR))
            .put("maximumRaw", KFactorProtocol.MAX_RAW)
            .put("delta", normalizedTarget - currentFactor)
            .put("deltaPercent", if (currentFactor < 0.001) 0.0 else (normalizedTarget / currentFactor - 1.0) * 100.0)
            .put("changed", currentRaw != targetRaw)
            .put("automatic", false)
            .put("requiresReview", true)
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("error", error.message ?: "Prévia K factor inválida")
    }
}
