package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estatísticas descritivas do residual espacial.
 *
 * Não escolhe Mapa K nem Curva K e não contém threshold de actionability. O objetivo
 * do owner 092 é preservar informação suficiente para consumidores posteriores
 * distinguirem residual localizado, tendência ampla e padrão contraditório/ruidoso.
 */
internal object ResidualSpatialStats {
    private data class Cell(val row: Int, val column: Int, val residualPct: Double) {
        val sign: Int get() = when {
            residualPct > 0.0 -> 1
            residualPct < 0.0 -> -1
            else -> 0
        }
        val key: String get() = "$row:$column"
    }

    fun from(residual: JSONArray): JSONObject {
        val cells = buildList {
            repeat(residual.length()) { index ->
                val raw = residual.optJSONObject(index) ?: return@repeat
                val row = raw.optInt("row", -1)
                val column = raw.optInt("column", -1)
                val value = raw.optDouble("residualErrorPercent", Double.NaN)
                if (row in 0..11 && column in 0..11 && value.isFinite()) add(Cell(row, column, value))
            }
        }
        if (cells.isEmpty()) {
            return JSONObject()
                .put("available", false)
                .put("cell_count", 0)
                .put("reason", "NO_RESIDUAL_CELLS")
                .put("classification_policy_applied", false)
        }

        val positive = cells.count { it.sign > 0 }
        val negative = cells.count { it.sign < 0 }
        val neutral = cells.size - positive - negative
        val meanAbs = cells.sumOf { abs(it.residualPct) } / cells.size
        val rms = sqrt(cells.sumOf { it.residualPct * it.residualPct } / cells.size)
        val signedMean = cells.sumOf { it.residualPct } / cells.size
        val rowMin = cells.minOf { it.row }
        val rowMax = cells.maxOf { it.row }
        val columnMin = cells.minOf { it.column }
        val columnMax = cells.maxOf { it.column }
        val largestSameSign = largestSameSignComponent(cells)
        val dominantSignCount = maxOf(positive, negative, neutral)

        return JSONObject()
            .put("available", true)
            .put("cell_count", cells.size)
            .put("positive_cells", positive)
            .put("negative_cells", negative)
            .put("neutral_cells", neutral)
            .put("signed_mean_error_pct", signedMean)
            .put("mean_abs_error_pct", meanAbs)
            .put("rms_error_pct", rms)
            .put("row_start", rowMin)
            .put("row_end", rowMax)
            .put("row_span", rowMax - rowMin + 1)
            .put("column_start", columnMin)
            .put("column_end", columnMax)
            .put("column_span", columnMax - columnMin + 1)
            .put("dominant_sign_fraction", dominantSignCount.toDouble() / cells.size.toDouble())
            .put("largest_same_sign_component_cells", largestSameSign)
            .put("largest_same_sign_component_fraction", largestSameSign.toDouble() / cells.size.toDouble())
            .put("classification_policy_applied", false)
            .put("chooses_map_or_curve", false)
    }

    private fun largestSameSignComponent(cells: List<Cell>): Int {
        val byKey = cells.associateBy { it.key }
        val pending = byKey.keys.toMutableSet()
        var largest = 0
        while (pending.isNotEmpty()) {
            val startKey = pending.first()
            pending.remove(startKey)
            val start = byKey.getValue(startKey)
            val queue = ArrayDeque<Cell>()
            queue.add(start)
            var size = 0
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                size += 1
                listOf(
                    cell.row - 1 to cell.column,
                    cell.row + 1 to cell.column,
                    cell.row to cell.column - 1,
                    cell.row to cell.column + 1,
                ).forEach { (row, column) ->
                    val key = "$row:$column"
                    val neighbor = byKey[key] ?: return@forEach
                    if (key in pending && neighbor.sign == start.sign) {
                        pending.remove(key)
                        queue.add(neighbor)
                    }
                }
            }
            largest = maxOf(largest, size)
        }
        return largest
    }
}
