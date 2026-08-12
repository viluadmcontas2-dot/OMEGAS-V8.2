package com.omegas.v7.runtime

/**
 * Dimensões confirmadas por três fontes independentes:
 *
 * - descritor Delphi MAP_K: armazenamento físico com 13 linhas e 12 colunas;
 * - tráfego serial: linhas indexadas 0..12, cada uma com 12 bytes;
 * - arquivos .lec: MappaK0..MappaK12, colunas 0..11.
 *
 * A operação útil permanece deliberadamente limitada às 12 linhas conhecidas da
 * grade de calibração. A linha física 12 continua preservada em leitura,
 * snapshot e readback, porém não pode receber sugestão nem escrita por célula.
 */
object CalibrationShapeV7 {
    const val CURVE_K_POINTS = 30
    const val MAP_K_COLUMNS = 12
    const val MAP_K_TIME_THRESHOLDS = 12

    /** Doze linhas efetivamente exibidas e editáveis. */
    const val MAP_K_EDITABLE_ROWS = 12

    /** Treze linhas físicas preservadas no protocolo, arquivo e readback. */
    const val MAP_K_STORAGE_ROWS = 13

    /** Alias histórico representa o armazenamento físico completo. */
    const val MAP_K_ROWS = MAP_K_STORAGE_ROWS

    fun requireCurve(curve: List<Double>) {
        require(curve.size == CURVE_K_POINTS) {
            "Curva K V7 exige $CURVE_K_POINTS pontos"
        }
    }

    fun requireMap(map: List<List<Int>>) {
        require(map.size == MAP_K_STORAGE_ROWS && map.all { it.size == MAP_K_COLUMNS }) {
            "Mapa K V7 exige ${MAP_K_STORAGE_ROWS}x${MAP_K_COLUMNS} no armazenamento"
        }
        require(map.flatten().all { it in 0..0xFF }) {
            "Mapa K V7 aceita somente valores U8"
        }
    }

    fun requireEditableCell(row: Int, column: Int) {
        require(row in 0 until MAP_K_EDITABLE_ROWS) {
            "Linha MAP_K não editável: $row; intervalo operacional 0..${MAP_K_EDITABLE_ROWS - 1}"
        }
        require(column in 0 until MAP_K_COLUMNS) {
            "Coluna MAP_K inexistente: $column; intervalo físico 0..${MAP_K_COLUMNS - 1}"
        }
    }
}
