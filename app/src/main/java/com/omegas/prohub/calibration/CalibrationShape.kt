package com.omegas.prohub.calibration

/** Physical dimensions proven for this MP48 calibration. */
object CalibrationShape {
    const val CURVE_K_POINTS = 30
    const val MAP_K_COLUMNS = 12
    const val MAP_K_TIME_THRESHOLDS = 12
    const val MAP_K_EDITABLE_ROWS = 12
    const val MAP_K_STORAGE_ROWS = 13

    fun requireCurve(curve: List<Double>) {
        require(curve.size == CURVE_K_POINTS) {
            "Curva K exige $CURVE_K_POINTS pontos"
        }
        require(curve.all { it.isFinite() && it > 0.0 }) {
            "Curva K contém fator inválido"
        }
    }

    fun requireMap(map: List<List<Int>>) {
        require(map.size == MAP_K_STORAGE_ROWS && map.all { it.size == MAP_K_COLUMNS }) {
            "Mapa K exige ${MAP_K_STORAGE_ROWS}x${MAP_K_COLUMNS} no armazenamento físico"
        }
        require(map.flatten().all { it in 0..0xFF }) {
            "Mapa K aceita somente valores U8"
        }
    }

    fun requireEditableCell(row: Int, column: Int) {
        require(row in 0 until MAP_K_EDITABLE_ROWS) {
            "Linha do Mapa K não editável: $row"
        }
        require(column in 0 until MAP_K_COLUMNS) {
            "Coluna do Mapa K inexistente: $column"
        }
    }
}
