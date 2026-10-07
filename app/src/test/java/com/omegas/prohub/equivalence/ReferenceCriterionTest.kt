package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EcuPetrolReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Um só critério de "gasolina madura da ECU" para a Referência (congelada/provisória) e para a curva de gasolina da ECU
 * do livro: zona marcada pela ECU OU banda com contador no limiar da própria ECU (a flag zera a cada AutoMatch).
 */
class ReferenceCriterionTest {
    private fun point(map: Double, ms: Double, state: String, counter: Int?, threshold: Int?, fuel: String = "GASOLINA") = JSONObject()
        .put("fuel", fuel).put("mapBar", map).put("timeMs", ms).put("state", state)
        .put("counter", counter ?: JSONObject.NULL).put("threshold", threshold ?: JSONObject.NULL)

    @Test
    fun `referencia e curva da ECU do livro usam o mesmo criterio de maturidade`() {
        val points = JSONArray()
        // Três zonas marcadas, quatro bandas maduras pelo contador (flag zerada pelo AutoMatch), uma imatura, uma de GNV.
        points.put(point(0.30, 3.0, "ZONA_ADQUIRIDA", 1, 3))
        points.put(point(0.40, 3.9, "ZONA_ADQUIRIDA", 10, 3))
        points.put(point(0.50, 4.8, "ZONA_ADQUIRIDA", null, null))
        points.put(point(0.60, 5.6, "ATIVIDADE", 10, 3))
        points.put(point(0.70, 6.5, "ATIVIDADE", 3, 3))
        points.put(point(0.80, 7.4, "COLETANDO", 5, 3))
        points.put(point(0.90, 8.4, "ATIVIDADE", 4, 3))
        points.put(point(1.00, 9.3, "ATIVIDADE", 2, 3))
        points.put(point(0.65, 6.0, "ZONA_ADQUIRIDA", 10, 3, fuel = "GNV"))
        val acquisition = JSONObject().put("points", points)

        val ledgerCurve = EcuPetrolReference.fromAcquisition(acquisition).sortedBy { it.first }
        val reference = ReferenceStore.pointsFrom(acquisition).map { it.mapBar to it.petrolMs }
        assertEquals(7, ledgerCurve.size)
        assertEquals(ledgerCurve, reference)
    }
}
