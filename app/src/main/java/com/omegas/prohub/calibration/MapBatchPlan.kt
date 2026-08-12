package com.omegas.prohub.calibration

import org.json.JSONArray

/**
 * Planejamento puro da intenção manual de escrita do Mapa K.
 *
 * A UX pode preparar qualquer quantidade de células da grade física 12x12.
 * O writer legado continua recebendo blocos internos pequenos, preservando o
 * comportamento de ACK/readback já exercitado enquanto a capacidade maior
 * aguarda validação física.
 */
object MapBatchPlan {
    const val MAX_USER_CELLS = KMapPhysicalAxes.WRITABLE_ROWS * KMapPhysicalAxes.COLUMNS
    const val INTERNAL_CHUNK_CELLS = 16

    data class Plan(
        val totalCells: Int,
        val chunks: List<JSONArray>,
    )

    fun build(cells: JSONArray): Plan {
        require(cells.length() in 1..MAX_USER_CELLS) {
            "Selecione entre 1 e $MAX_USER_CELLS células graváveis"
        }
        val chunks = mutableListOf<JSONArray>()
        var index = 0
        while (index < cells.length()) {
            val chunk = JSONArray()
            val end = minOf(index + INTERNAL_CHUNK_CELLS, cells.length())
            while (index < end) {
                chunk.put(cells.getJSONObject(index))
                index += 1
            }
            chunks += chunk
        }
        return Plan(cells.length(), chunks)
    }
}
