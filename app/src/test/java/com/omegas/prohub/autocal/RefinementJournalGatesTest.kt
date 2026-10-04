package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Revisão adversarial P2-7: o diário exige episódios, esquece o ganho e não conta Desfazer como passada. */
class RefinementJournalGatesTest {
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }

    /** (razão, amostras, episódios) por faixa do livro. */
    private fun index(vararg bands: Triple<Double?, Int, Int?>): JSONObject = JSONObject().put("ratio", 1.0).put("bands",
        JSONArray(bands.mapIndexed { i, (ratio, n, episodes) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL).put("episodes", episodes ?: JSONObject.NULL)
        }))

    private val same = { r: Double?, n: Int, e: Int? -> Triple(r, n, e) }
    private fun all(ratio: Double?, n: Int, e: Int?) = Array(5) { same(ratio, n, e) }

    private fun journal(clock: () -> Long = { 1_000L }) = RefinementJournal(null, clock)
    private val before = IntArray(30) { 16384 }
    private val after = before.copyOf().also { for (i in 5..29) it[i] = 17000 }

    @Test
    fun `faixa com amostras julga sem exigir quantidade de trechos`() {
        val j = journal()
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "teste")
        // depois: 20 amostras em 2 blocos já julgam: não há portão de "trechos"/minutos
        j.evaluate(index(*all(1.0, 20, 2)))
        assertEquals("VERIFICADO", j.json().getJSONObject("latest").getString("status"))
    }

    @Test
    fun `antes sem nenhum episodio tambem nao serve de base`() {
        val j = journal()
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 0)), "teste")
        j.evaluate(index(*all(1.0, 20, 3)))
        assertEquals("SEM_BASE", j.json().getJSONObject("latest").getString("status"))
    }

    @Test
    fun `faixa sem cobertura interna nao e julgada`() {
        val j = journal()
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "teste")
        val bad = index(*all(1.0, 20, 3))
        for (i in 0 until 5) bad.getJSONArray("bands").getJSONObject(i).put("interiorCovered", false)
        j.evaluate(bad)
        assertEquals("VERIFICANDO", j.json().getJSONObject("latest").getString("status"))
    }

    @Test
    fun `curta que nao melhorou nao firma o ganho e a curta que melhorou firma pouco`() {
        val j = journal()
        // antes 1,06 (e0 = 5,8%); depois 1,07 (piorou um pouco, mas dentro da margem de PIOROU) -> CURTA sem firmar
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "teste")
        j.evaluate(index(*all(1.07, 20, 3)))
        val v = j.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2)
        assertEquals("CURTA", v.getString("verdict"))
        assertTrue("curta que piorou não firma ganho", j.json().getJSONArray("bandScale").getDouble(2) <= 1.0 + 1e-12)
        // curta que melhorou (1,06 -> 1,045: mesmo sinal, melhora, ainda acima do ok) firma um pouco
        val k = journal()
        k.recordCurveWrite(before, after, axisRaw, index(*all(1.10, 20, 3)), "teste")
        k.evaluate(index(*all(1.06, 20, 3)))
        assertEquals("CURTA", k.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2).getString("verdict"))
        val scale = k.json().getJSONArray("bandScale").getDouble(2)
        assertTrue("curta que melhorou firma: $scale", scale > 1.0 && scale <= RefinementJournal.MAX_SCALE)
    }

    @Test
    fun `o ganho aprendido decai de volta a 1 em vez de crescer para sempre`() {
        val j = journal()
        var last = 1.0
        // um experimento "curta que melhora" firma a faixa; a cada experimento seguinte sem veredito nela ela volta a 1,0
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.10, 20, 3)), "teste")
        j.evaluate(index(*all(1.06, 20, 3)))
        last = j.json().getJSONArray("bandScale").getDouble(0)
        assertTrue(last > 1.0)
        repeat(12) {
            j.recordCurveWrite(before, after, axisRaw, index(*all(null, 0, null)), "teste")
            // faixa não alterada -> sem veredito: só o decaimento age (SEM_BASE fecha o experimento)
            j.evaluate(index(*all(null, 0, null)))
            val now = j.json().getJSONArray("bandScale").getDouble(0)
            assertTrue("decai: $now <= $last", now <= last + 1e-12)
            last = now
        }
        assertEquals(1.0, last, 0.05)
        // e quem insiste em PASSOU não afunda além do piso
        repeat(30) {
            j.recordCurveWrite(before, after, axisRaw, index(*all(1.05, 20, 3)), "teste")
            j.evaluate(index(*all(0.95, 20, 3)))
        }
        val floor = j.json().getJSONArray("bandScale").getDouble(0)
        assertTrue(floor >= RefinementJournal.MIN_SCALE - 1e-12)
    }

    @Test
    fun `Desfazer e Restaurar nao contam como passada de ganho nem ensinam o ganho`() {
        val j = journal()
        val axisMs = axisRaw.map { it / 512.0 }
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "ajuste")
        assertEquals(1, j.json().getJSONArray("pointPasses").getInt(10))
        // Desfazer: volta à foto. A passada que a gravação contou é devolvida, nunca somada.
        j.recordCurveWrite(after, before, axisRaw, index(*all(1.0, 20, 3)), "desfazer", restore = true)
        assertEquals(0, j.json().getJSONArray("pointPasses").getInt(10))
        assertTrue(j.json().getJSONObject("latest").getBoolean("restore"))
        assertEquals(1.0, j.pointGainScale(axisMs)[10], 1e-12)
        // a verificação do Desfazer fecha mas não ensina o ganho (mesmo com PASSOU/PIOROU)
        j.evaluate(index(*all(0.90, 20, 3)))
        assertTrue(j.json().getJSONArray("bandScale").let { a -> (0 until a.length()).all { a.getDouble(it) == 1.0 } })
        // várias idas e vindas não degradam o ganho para 0,5
        repeat(5) {
            j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "ajuste")
            j.recordCurveWrite(after, before, axisRaw, index(*all(1.06, 20, 3)), "desfazer", restore = true)
        }
        assertEquals(0, j.json().getJSONArray("pointPasses").getInt(10))
        assertEquals(1.0, j.pointGainScale(axisMs)[10], 1e-12)
    }

    @Test
    fun `o motivo da gravacao decide se foi um Desfazer`() {
        assertTrue(RefinementJournal.isUndoReason("Restaurar backup Curva K K-FACTOR-123.json"))
        assertTrue(RefinementJournal.isUndoReason("Reset Curva K · ProgBase MUL_ACT=1.0"))
        assertTrue(RefinementJournal.isUndoReason("Neutralizar MUL_ACT live em 1.0 · OMEGAS"))
        assertTrue(RefinementJournal.isUndoReason("desfazer ajuste"))
        assertFalse(RefinementJournal.isUndoReason("Ajuste manual Curva K"))
        assertFalse(RefinementJournal.isUndoReason(""))
    }

    @Test
    fun `AutoMatch nativo reescreve a curva e zera o que o diario aprendeu`() {
        val j = journal()
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "ajuste")
        j.evaluate(index(*all(0.95, 20, 3)))
        assertTrue(j.json().getJSONArray("bandScale").getDouble(0) < 1.0)
        assertEquals(1, j.json().getJSONArray("pointPasses").getInt(10))
        j.interrupt("AUTOMATCH_NATIVO")
        assertEquals(1.0, j.json().getJSONArray("bandScale").getDouble(0), 0.0)
        assertEquals(0, j.json().getJSONArray("pointPasses").getInt(10))
        // Mapa K gravado não reescreve a Curva K: o aprendizado fica
        j.recordCurveWrite(before, after, axisRaw, index(*all(1.06, 20, 3)), "ajuste")
        j.interrupt("MAPA_K_GRAVADO")
        assertEquals(1, j.json().getJSONArray("pointPasses").getInt(10))
    }
}
