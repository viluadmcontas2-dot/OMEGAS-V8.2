package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2: a resposta pronta da bridge não bloqueia a WebView com o cálculo pesado. */
class BackgroundMemoTest {
    private var now = 0L
    private var runs = 0
    private fun memo(refresh: Long = 1_000L, stale: Long = 3_000L, watch: Long = 15_000L, fail: Boolean = false) =
        BackgroundMemo(refresh, stale, watch, { now }) {
            runs++
            if (fail) error("falhou") else "v$runs"
        }

    @Test
    fun `a chamada da tela devolve o valor pronto sem recalcular`() {
        val memo = memo()
        assertEquals("v1", memo.get())
        repeat(1_000) { now += 1; assertEquals("v1", memo.get()) } // 1 s inteiro de chamadas
        assertEquals(1, runs)
        assertEquals(1, memo.computations())
    }

    @Test
    fun `segundo plano recalcula so enquanto alguem esta olhando`() {
        val memo = memo()
        assertFalse("ninguém pediu ainda", memo.refreshIfWatched())
        memo.get()
        now += 1_000
        assertTrue(memo.refreshIfWatched())
        assertEquals("v2", memo.get())
        now += 500
        assertFalse("ainda novo", memo.refreshIfWatched())
        // A tela sai: 20 s sem pedir, o segundo plano para de gastar a multimídia.
        now += 20_000
        assertFalse(memo.refreshIfWatched())
        assertEquals(2, runs)
    }

    @Test
    fun `valor velho demais e recalculado na hora e invalidar forca valor fresco`() {
        val memo = memo()
        memo.get()
        now += 4_000
        assertEquals("passou do prazo: recalcula", "v2", memo.get())
        assertEquals("v2", memo.get())
        memo.invalidate()
        assertEquals("depois de gravar a leitura é fresca", "v3", memo.get())
    }

    @Test
    fun `erro no calculo vira resposta de falha, nao derruba a tela`() {
        val memo = memo(fail = true)
        val answer = memo.get()
        assertTrue(answer.contains("\"ok\":false"))
        assertTrue(answer.contains("falhou"))
    }
}
