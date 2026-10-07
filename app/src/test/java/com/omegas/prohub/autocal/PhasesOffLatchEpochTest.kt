package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achado 14: zerar o GNV (época nova) limpa a histerese das faixas "fora". */
class PhasesOffLatchEpochTest {
    private var now = 0L

    private fun monitorDone() = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)

    private fun index(epochAt: Long, vararg bands: Pair<Double?, Int>) = JSONObject().put("samples", bands.sumOf { it.second })
        .put("petrolObservations", 500).put("gasObservations", 200).put("gasEpochAt", epochAt)
        .put("bands", JSONArray(bands.mapIndexed { i, (r, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", r ?: JSONObject.NULL)
        }))

    private val noJournal = JSONObject().put("latest", JSONObject.NULL)

    @Test
    fun `GNV zerado limpa a histerese das faixas fora`() {
        val p = EquivalencePhases(null) { now }
        val first = p.observe(true, monitorDone(), null, index(100L, 1.0 to 20, 1.045 to 20), noJournal, 0)
        assertEquals(1, first.getJSONArray("bandsOff").length())
        // Mesma época: 3,5% ainda está "fora" pela histerese (só sai abaixo de 3%).
        now += 3_000
        assertEquals(1, p.observe(true, monitorDone(), null, index(100L, 1.0 to 20, 1.035 to 20), noJournal, 0).getJSONArray("bandsOff").length())
        // O GNV foi zerado (época nova): a medição de agora não herda o "fora" da curva anterior.
        now += 3_000
        assertEquals(0, p.observe(true, monitorDone(), null, index(200L, 1.0 to 20, 1.035 to 20), noJournal, 0).getJSONArray("bandsOff").length())
    }
}
