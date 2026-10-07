package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

/** O índice leve (linha, coluna) tem de coincidir com o JSON completo de [LiveCellProjection.cellFor] em toda a grade. */
class LiveCellProjectionIndexTest {
    @Test
    fun `indice leve coincide com a celula completa`() {
        var checked = 0
        var rpm = 0.0
        while (rpm <= 7_000.0) {
            var ms = 0.0
            while (ms <= 25.0) {
                val full = LiveCellProjection.cellFor(rpm, ms)
                val (row, column) = LiveCellProjection.cellIndex(rpm, ms)
                assertEquals("linha rpm=$rpm ms=$ms", full.getInt("row"), row)
                assertEquals("coluna rpm=$rpm ms=$ms", full.getInt("column"), column)
                checked++
                ms += 0.37
            }
            rpm += 97.0
        }
        assertEquals("caso médio (rpm da lenta, 4,5 ms)", "${LiveCellProjection.cellFor(870.0, 4.5).getString("key")}",
            LiveCellProjection.cellIndex(870.0, 4.5).let { "${it.first}:${it.second}" })
        check(checked > 1_000)
    }
}
