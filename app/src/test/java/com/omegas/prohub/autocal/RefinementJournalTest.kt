package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        journal.evaluate(index(0.96 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 24, 1.0 to 10))
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

    /** Índice com a contagem de regiões RPM×MAP distintas por faixa (`cells`), como o acumulador emite. */
    private fun indexCells(vararg bands: Triple<Double?, Int, Int>): JSONObject = JSONObject().put("ratio", 1.0).put("bands",
        JSONArray(bands.mapIndexed { i, (ratio, n, cells) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("cells", cells).put("ratio", ratio ?: JSONObject.NULL)
        }))

    // Dono, 2026-10-16 (sessão real: RESTAURAR_TRECHO 16 s depois de gravar, parado): "piorou" só com mudança relativa
    // ≥ 3% E leituras novas em ≥ 3 regiões RPM×MAP da faixa; abaixo disso fica coletando, sem oferta de restaurar.
    @Test
    fun `piorou exige mudanca de 3 por cento e leituras novas em 3 regioes distintas`() {
        assertNotEquals("PIOROU", RefinementJournal.judge(1.03, 1.06))
        assertNotEquals("PIOROU", RefinementJournal.judge(1.0, 1.029))
        assertEquals("PIOROU", RefinementJournal.judge(1.0, 1.08))
        val journal = RefinementJournal(null)
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 14..16) it[i] = 17000 } // 7,5–8,5 ms (faixa 7,5–9)
        write(journal, before, after, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20))
        // 24 leituras paradas numa região só (semáforo): coleta, sem oferta de restaurar.
        journal.evaluate(indexCells(Triple(1.0, 12, 3), Triple(1.0, 12, 3), Triple(1.0, 12, 3), Triple(1.12, 24, 1), Triple(1.0, 12, 3)))
        var band = journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(3)
        assertEquals("COLETANDO", band.getString("verdict"))
        assertEquals("POUCAS_REGIOES", band.getString("thinWorse"))
        assertEquals(0, journal.restorePoints().length())
        // As mesmas 24 leituras em 3 regiões: piorou de verdade e a oferta de restaurar aparece.
        journal.evaluate(indexCells(Triple(1.0, 12, 3), Triple(1.0, 12, 3), Triple(1.0, 12, 3), Triple(1.12, 24, 3), Triple(1.0, 12, 3)))
        band = journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(3)
        assertEquals("PIOROU", band.getString("verdict"))
        assertEquals(3, band.getInt("cellsAfter"))
        assertTrue(journal.restorePoints().length() > 0)
    }
}
