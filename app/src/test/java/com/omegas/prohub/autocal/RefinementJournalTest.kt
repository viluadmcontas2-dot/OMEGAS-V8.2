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
                // Fixture explícita de medições independentes e precisas para testar a resposta do ganho.
                .put("evidenceStats", JSONObject().put("model", "overlap-lag1-mad-v1")
                    .put("effectiveSamples", n.toDouble()).put("dispersionLog", 0.002))
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
    fun `faixa que piorou fecha na hora mesmo com as outras ainda sem leitura`() {
        val journal = RefinementJournal(null)
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..29) it[i] = 18000 }
        write(journal, before, after, index(1.03 to 20, 1.03 to 20, 1.03 to 20, 1.03 to 20, 1.03 to 20))
        // Só a faixa 3 tem leitura depois da gravação e saiu da margem (8,6%, pior que os 3% de antes).
        journal.evaluate(index(null to 0, null to 0, null to 0, 1.09 to 10, null to 0))
        val latest = journal.json().getJSONObject("latest")
        assertEquals("PIOROU_EM_PARTE", latest.getString("status"))
        assertEquals("PIOROU", latest.getJSONArray("bands").getJSONObject(3).getString("verdict"))
        assertEquals("COLETANDO", latest.getJSONArray("bands").getJSONObject(0).getString("verdict"))
        assertTrue(journal.restorePoints().length() > 0)
    }

    @Test
    fun `piorou tem criterio - fora da margem aceita e pior que antes alem do ruido`() {
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..29) it[i] = 17000 }
        fun verdict(b: Double, a: Double): String {
            val j = RefinementJournal(null)
            write(j, before, after, index(b to 20, b to 20, b to 20, b to 20, b to 20))
            j.evaluate(index(a to 10, a to 10, a to 10, a to 10, a to 10))
            return j.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2).getString("verdict")
        }
        assertEquals("dentro dos 5% aceitos nunca é piorou", "CONFIRMADA", verdict(1.02, 1.045))
        assertEquals("fora da margem, mas pior só pelo ruído", "CURTA", verdict(1.06, 1.07))
        assertEquals("fora da margem e pior além do ruído", "PIOROU", verdict(1.03, 1.07))
        assertEquals("fora da margem e piorou a partir de dentro", "PIOROU", verdict(1.01, 1.06))
        assertEquals("trocou de lado dentro da margem: passou do ponto, não piorou", "PASSOU", verdict(1.05, 0.96))
    }

    @Test
    fun `diferenca de poucos por cento nao e piorou`() {
        val journal = RefinementJournal(null)
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..29) it[i] = 17000 }
        write(journal, before, after, index(1.02 to 20, 1.02 to 20, 1.02 to 20, 1.02 to 20, 1.02 to 20))
        journal.evaluate(index(1.045 to 12, 1.04 to 12, 1.045 to 12, 1.05 to 12, 1.04 to 12))
        val latest = journal.json().getJSONObject("latest")
        assertEquals("VERIFICADO", latest.getString("status"))
        for (i in 0 until latest.getJSONArray("bands").length()) {
            assertTrue(latest.getJSONArray("bands").getJSONObject(i).getString("verdict") != "PIOROU")
        }
        assertEquals(0, journal.restorePoints().length())
    }

    @Test
    fun `oferta de restaurar nao expira por relogio e so vale para o ultimo experimento`() {
        var now = 1_000L
        val journal = RefinementJournal(null) { now }
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..29) it[i] = 18000 }
        write(journal, before, after, index(1.05 to 20, 1.06 to 20, 1.05 to 20, 1.02 to 20, 1.03 to 20))
        journal.evaluate(index(0.96 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 10, 1.0 to 10))
        assertTrue(journal.restorePoints().length() > 0)
        now += 3 * 24 * 60 * 60_000L // três dias desconectado: a oferta continua (sem métrica de tempo)
        assertTrue(journal.restorePoints().length() > 0)
        now = 2_000L
        val journal2 = RefinementJournal(null) { now }
        write(journal2, before, after, index(1.05 to 20, 1.06 to 20, 1.05 to 20, 1.02 to 20, 1.03 to 20))
        journal2.evaluate(index(0.96 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 10, 1.0 to 10))
        write(journal2, after, after.copyOf().also { it[6] = 18100 }, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20))
        assertEquals(0, journal2.restorePoints().length())
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
