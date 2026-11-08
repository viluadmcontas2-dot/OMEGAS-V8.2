package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.session.CalibrationShape
import com.omegas.prohub.session.CalibrationState
import org.json.JSONArray
import org.json.JSONObject

/** Converte somente payloads já marcados como readback válido pelos managers. */
internal object CalibrationWriterReadBack {
    fun map(desired: CalibrationState, status: JSONObject): CalibrationState {
        val details = status.optJSONObject("details")
            ?: error("Writer do Mapa K confirmou sem detalhes de readback")
        require(details.optBoolean("readbackValid", false)) {
            "Writer do Mapa K não confirmou readback válido"
        }
        val visible = details.optJSONArray("rows")
            ?: error("Readback do Mapa K não trouxe as 12 linhas editáveis")
        val extra = details.optJSONArray("extraRow")
            ?: error("Readback do Mapa K não trouxe a linha técnica preservada")
        require(visible.length() == CalibrationShape.MAP_K_EDITABLE_ROWS)
        require(extra.length() == CalibrationShape.MAP_K_COLUMNS)
        val map = MutableList(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 0 }
        }
        repeat(CalibrationShape.MAP_K_EDITABLE_ROWS) { row ->
            map[row] = intRow(visible.getJSONArray(row), row)
        }
        map[CalibrationShape.MAP_K_EDITABLE_ROWS] = intRow(extra, CalibrationShape.MAP_K_EDITABLE_ROWS)
        return desired.copy(mapK = map)
    }

    fun curve(desired: CalibrationState, status: JSONObject): CalibrationState {
        val details = status.optJSONObject("details")
            ?: error("Writer da Curva K confirmou sem detalhes de readback")
        require(details.optBoolean("readbackValid", false)) {
            "Writer da Curva K não confirmou readback válido"
        }
        val curve = details.optJSONObject("curve")
            ?: error("Readback da Curva K não trouxe a curva confirmada")
        val raw = curve.optJSONArray("factorsRaw")
            ?: error("Readback da Curva K não trouxe os 30 valores Q14")
        require(raw.length() == CalibrationShape.CURVE_K_POINTS)
        val factors = List(raw.length()) { index ->
            KFactorProtocol.factorFromRaw(raw.getInt(index))
        }
        return desired.copy(curveK = factors)
    }

    private fun intRow(values: JSONArray, row: Int): List<Int> {
        require(values.length() == CalibrationShape.MAP_K_COLUMNS) {
            "Linha de readback $row não possui ${CalibrationShape.MAP_K_COLUMNS} colunas"
        }
        return List(values.length()) { column ->
            values.getInt(column).also { value -> require(value in 0..0xFF) }
        }
    }
}
