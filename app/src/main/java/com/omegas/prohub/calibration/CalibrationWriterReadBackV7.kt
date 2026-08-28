package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import org.json.JSONArray
import org.json.JSONObject

/** Converte somente payloads já marcados como readback válido pelos managers. */
internal object CalibrationWriterReadBackV7 {
    fun map(desired: CalibrationStateV7, status: JSONObject): CalibrationStateV7 {
        val details = status.optJSONObject("details")
            ?: error("Writer do Mapa K confirmou sem detalhes de readback")
        require(details.optBoolean("readbackValid", false)) {
            "Writer do Mapa K não confirmou readback válido"
        }
        val visible = details.optJSONArray("rows")
            ?: error("Readback do Mapa K não trouxe as 12 linhas editáveis")
        val extra = details.optJSONArray("extraRow")
            ?: error("Readback do Mapa K não trouxe a linha técnica preservada")
        require(visible.length() == CalibrationShapeV7.MAP_K_EDITABLE_ROWS)
        require(extra.length() == CalibrationShapeV7.MAP_K_COLUMNS)
        val map = MutableList(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 0 }
        }
        repeat(CalibrationShapeV7.MAP_K_EDITABLE_ROWS) { row ->
            map[row] = intRow(visible.getJSONArray(row), row)
        }
        map[CalibrationShapeV7.MAP_K_EDITABLE_ROWS] = intRow(extra, CalibrationShapeV7.MAP_K_EDITABLE_ROWS)
        return desired.copy(mapK = map)
    }

    fun curve(desired: CalibrationStateV7, status: JSONObject): CalibrationStateV7 {
        val details = status.optJSONObject("details")
            ?: error("Writer da Curva K confirmou sem detalhes de readback")
        require(details.optBoolean("readbackValid", false)) {
            "Writer da Curva K não confirmou readback válido"
        }
        val curve = details.optJSONObject("curve")
            ?: error("Readback da Curva K não trouxe a curva confirmada")
        val raw = curve.optJSONArray("factorsRaw")
            ?: error("Readback da Curva K não trouxe os 30 valores Q14")
        require(raw.length() == CalibrationShapeV7.CURVE_K_POINTS)
        val factors = List(raw.length()) { index ->
            KFactorProtocol.factorFromRaw(raw.getInt(index))
        }
        return desired.copy(curveK = factors)
    }

    private fun intRow(values: JSONArray, row: Int): List<Int> {
        require(values.length() == CalibrationShapeV7.MAP_K_COLUMNS) {
            "Linha de readback $row não possui ${CalibrationShapeV7.MAP_K_COLUMNS} colunas"
        }
        return List(values.length()) { column ->
            values.getInt(column).also { value -> require(value in 0..0xFF) }
        }
    }
}
