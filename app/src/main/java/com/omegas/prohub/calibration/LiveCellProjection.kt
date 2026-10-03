package com.omegas.prohub.calibration

import com.omegas.prohub.learning.ContinuousLearningMath
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Célula ao vivo sobre os eixos físicos do mapa K ([KMapPhysicalAxes]).
 * Localiza o ponto de operação atual (RPM × tempo de injeção gasolina × MAP) na
 * mesma grade que o usuário lê e escreve. É estritamente observacional.
 */
object LiveCellProjection {
    val rpmBins: IntArray = KMapPhysicalAxes.rpmBins()
    val petrolBins: DoubleArray = KMapPhysicalAxes.petrolBins()
    val mapBins: DoubleArray = doubleArrayOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00)

    fun cellFor(rpm: Double, petrolMs: Double, mapBar: Double = 0.60): JSONObject {
        val row = nearest(petrolBins, petrolMs)
        val column = nearest(rpmBins.map { it.toDouble() }.toDoubleArray(), rpm)
        val mapIndex = nearest(mapBins, mapBar)
        return JSONObject()
            .put("row", row)
            .put("column", column)
            .put("key", "$row:$column")
            .put("rpmBin", rpmBins[column])
            .put("petrolBin", petrolBins[row])
            .put("mapBin", mapBins[mapIndex])
            .put("continuousWeights", JSONArray(
                ContinuousLearningMath.bilinearWeights(rpm, petrolMs).map {
                    JSONObject()
                        .put("row", it.row)
                        .put("column", it.column)
                        .put("weight", it.weight)
                },
            ))
            .put("trilinearWeights", JSONArray(
                ContinuousLearningMath.trilinearWeights(rpm, petrolMs, mapBar, mapBins).map {
                    JSONObject()
                        .put("row", it.row)
                        .put("column", it.column)
                        .put("mapIndex", it.mapIndex)
                        .put("weight", it.weight)
                },
            ))
    }

    /**
     * Pacote leve para explicar, em tempo real, a mesma interpolação bilinear
     * usada pelo aprendizado. É estritamente observacional: não altera memória,
     * sugestões nem escrita K.
     */
    fun liveInterpolationJson(
        rpm: Double,
        petrolMs: Double,
        mapBar: Double,
        sequence: Long,
        updatedAt: Long,
        telemetryValid: Boolean,
    ): JSONObject {
        val physicallyValid = telemetryValid && rpm > 0.0 && petrolMs > 0.0 &&
            rpm.isFinite() && petrolMs.isFinite() && mapBar.isFinite()
        val safeRpm = if (rpm.isFinite()) rpm.coerceAtLeast(0.0) else 0.0
        val safePetrolMs = if (petrolMs.isFinite()) petrolMs.coerceAtLeast(0.0) else 0.0
        val safeMapBar = if (mapBar.isFinite()) mapBar.coerceAtLeast(0.0) else 0.0
        val cell = cellFor(safeRpm, safePetrolMs, safeMapBar)
        val weights = cell.optJSONArray("continuousWeights") ?: JSONArray()
        val totalWeight = (0 until weights.length()).sumOf { index ->
            weights.optJSONObject(index)?.optDouble("weight", 0.0) ?: 0.0
        }
        return JSONObject()
            .put("valid", physicallyValid)
            .put("educationalOnly", true)
            .put("affectsLearning", false)
            .put("affectsCalibration", false)
            .put("method", "BILINEAR_RPM_X_PETROL_MS")
            .put("axisSchema", KMapPhysicalAxes.SCHEMA)
            .put("axisLockSha256", KMapPhysicalAxes.LOCK_SHA256)
            .put("sequence", sequence)
            .put("updatedAt", updatedAt)
            .put("rpm", rpm)
            .put("petrolMs", petrolMs)
            .put("mapBar", mapBar)
            .put("totalWeight", totalWeight)
            .put("cell", cell)
    }

    private fun nearest(values: DoubleArray, value: Double): Int {
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.forEachIndexed { index, candidate ->
            val distance = abs(candidate - value)
            if (distance < bestDistance) {
                bestIndex = index
                bestDistance = distance
            }
        }
        return bestIndex
    }
}
