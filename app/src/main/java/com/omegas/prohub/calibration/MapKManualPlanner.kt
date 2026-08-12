package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Autoridade pura da prévia manual do Mapa K.
 *
 * Não toca USB, não escreve ECU e não altera sessão. Recebe os valores atuais
 * já lidos e devolve alvos normalizados para a UI apenas revisar/renderizar.
 */
object MapKManualPlanner {
    private const val MINIMUM_K = KWriteManager.MIN_SAFE_K
    private const val MAXIMUM_K = 255

    fun target(current: Int, mode: String, adjustment: Double): Int {
        require(current in 0..MAXIMUM_K) { "Valor K atual inválido [$current]" }
        require(adjustment.isFinite()) { "Informe um valor numérico" }
        require(mode in setOf("percent", "delta", "target")) { "Modo de alteração inválido" }
        val rawTarget = when (mode) {
            "percent" -> current * (1.0 + adjustment / 100.0)
            "delta" -> current + adjustment
            else -> adjustment
        }
        return rawTarget.roundToInt().coerceIn(MINIMUM_K, MAXIMUM_K)
    }

    fun preview(cellsJson: String, mode: String, adjustment: Double): JSONObject = try {
        require(adjustment.isFinite()) { "Informe um valor numérico" }
        require(mode in setOf("percent", "delta", "target")) { "Modo de alteração inválido" }
        val cells = JSONArray(cellsJson)
        require(cells.length() in 1..(KWriteManager.ROW_COUNT * KWriteManager.COLUMN_COUNT)) {
            "Selecione entre 1 e ${KWriteManager.ROW_COUNT * KWriteManager.COLUMN_COUNT} células"
        }
        val items = JSONArray()
        repeat(cells.length()) { index ->
            val cell = cells.getJSONObject(index)
            val row = cell.getInt("row")
            val column = cell.getInt("column")
            val current = cell.getInt("current")
            require(row in 0 until KWriteManager.ROW_COUNT) { "Linha K inválida [$row]" }
            require(column in 0 until KWriteManager.COLUMN_COUNT) { "Coluna K inválida [$column]" }
            val target = target(current, mode, adjustment)
            items.put(
                JSONObject()
                    .put("row", row)
                    .put("column", column)
                    .put("current", current)
                    .put("target", target)
                    .put("changed", target != current),
            )
        }
        JSONObject()
            .put("ok", true)
            .put("mode", mode)
            .put("adjustment", adjustment)
            .put("minimumK", MINIMUM_K)
            .put("maximumK", MAXIMUM_K)
            .put("automatic", false)
            .put("requiresReview", true)
            .put("items", items)
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("error", error.message ?: "Prévia do Mapa K inválida")
    }
}
