package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefinementJournalTest {
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }

    private fun index(vararg bands: Pair<Double?, Int>): JSONObject = JSONObject().put("ratio", 1.0).put("bands",
        JSONArray(bands.mapIndexed { i, (ratio, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL)
        }))

    private fun write(journal: RefinementJournal, before: IntArray, after: IntArray, idx: JSONObject) =
        journal.recordCurveWrite(before, after, axisRaw, idx, "teste")

    @Test
    fun `faixa que chega perto da gasolina e confirmada e as outras esperam dados`() {
        val journal = RefinementJournal(null)
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..23) it[i] = 17000 }
        write(journal, before, after, index(1.06 to 20, 1.05 to 20, 1.04 to 20, 1.08 to 20, 1.07 to 20))
        assertTrue(journal.evaluate(index(1.005 to 12, null to 0, null to 0, null to 0, null to 0)))
        assertEquals("VERIFICANDO", journal.json().getJSONObject("latest").getString("status"))
        journal.evaluate(index(1.005 to 12, 1.0 to 10, 1.01 to 9, 0.995 to 8, 1.004 to 15))
        val latest = journal.json().getJSONObject("latest")
        assertEquals("VERIFICADO", latest.getString("status"))
        assertEquals("CONFIRMADA", latest.getJSONArray("bands").getJSONObject(0).getString("verdict"))
    }

    @Test
    fun `passou do ponto suaviza a proxima correcao e piorou oferece restaurar so o trecho`() {
        val journal = RefinementJournal(null)
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..29) it[i] = 18000 }
        write(journal, before, after, index(1.05 to 20, 1.06 to 20, 1.05 to 20, 1.02 to 20, 1.03 to 20))
        journal.evaluate(index(0.96 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 10, 1.0 to 10))
        val latest = journal.json().getJSONObject("latest")
        assertEquals("PIOROU_EM_PARTE", latest.getString("status"))
        val verdicts = latest.getJSONArray("bands")
        assertEquals("PASSOU", verdicts.getJSONObject(0).getString("verdict"))
        assertEquals("PIOROU", verdicts.getJSONObject(3).getString("verdict"))
        val scale = journal.json().getJSONArray("bandScale")
        assertTrue(scale.getDouble(0) < 1.0)
        assertTrue(scale.getDouble(3) < scale.getDouble(0))
        val restore = journal.restorePoints()
        assertTrue(restore.length() > 0)
        for (i in 0 until restore.length()) {
            val p = restore.getJSONObject(i)
            val ms = axisRaw[p.getInt("index")] / 512.0
            assertTrue("ponto $ms ms fora da faixa que piorou", ms >= 7.0 && ms < 9.5)
            assertEquals(16384, p.getInt("targetRaw"))
        }
    }

    @Test
    fun `mapa K ou AutoMatch nativo no meio interrompe a verificacao`() {
        val journal = RefinementJournal(null)
        write(journal, IntArray(30) { 16384 }, IntArray(30) { 17000 }, index(1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20))
        journal.interrupt("MAPA_K_GRAVADO")
        assertEquals("INTERROMPIDO", journal.json().getJSONObject("latest").getString("status"))
        assertFalse(journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20)))
    }

    @Test
    fun `ganho aprendido por faixa alimenta o motor refinado`() {
        val journal = RefinementJournal(null)
        val scale = journal.pointGainScale(axisRaw.map { it / 512.0 })
        assertEquals(30, scale.size)
        assertTrue(scale.all { it == 1.0 })
    }
}
