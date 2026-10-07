package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2: a resposta pronta da bridge não bloqueia a WebView com o cálculo pesado. */
class BackgroundMemoTest {
    private var now = 0L
    private var runs = 0
    private val queued = mutableListOf<Runnable>()
    private fun memo(refresh: Long = 1_000L, stale: Long = 3_000L, watch: Long = 15_000L, fail: Boolean = false) =
        BackgroundMemo(refresh, stale, watch, { now }, background = { queued += it }) {
            runs++
            if (fail) error("falhou") else "v$runs"
        }

    private fun runQueued() {
        val pending = queued.toList()
        queued.clear()
        pending.forEach { it.run() }
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

    /**
     * ECU#2 (travada na troca de zona): valor só VELHO volta na hora e o recálculo vai para o segundo
     * plano, uma vez só. Revisão 2026-10-07: valor INVALIDADO (depois de gravar/resetar) não volta — a
     * próxima leitura calcula na hora, para a tela nunca mostrar o estado de antes da ação.
     */
    @Test
    fun `valor velho volta na hora e o recalculo vai para o segundo plano`() {
        val memo = memo()
        memo.get()
        now += 4_000
        assertEquals("passou do prazo: devolve o velho sem calcular", "v1", memo.get())
        assertEquals("v1", memo.get())
        assertEquals("um único recálculo agendado", 1, queued.size)
        runQueued()
        assertEquals("v2", memo.get())
        memo.invalidate()
        assertEquals("invalidado: calcula na hora", "v3", memo.get())
        assertTrue(queued.isEmpty())
    }

    /**
     * Revisão 2026-10-07 (gráfico do reset): a ponte não bloqueante devolvia a projeção VELHA depois de invalidate
     * e esperava o relógio de fundo (até 1 s) para recalcular. Agora devolve o velho (nunca bloqueia a WebView)
     * mas agenda UM recálculo imediato; a chamada seguinte, depois dele, já traz a projeção nova.
     */
    @Test
    fun `nao bloqueante depois de invalidate devolve o velho e agenda um recalculo imediato`() {
        val memo = memo()
        assertEquals("v1", memo.get())
        memo.invalidate()
        assertEquals("v1", memo.getNonBlocking("fallback"))
        assertEquals("v1", memo.getNonBlocking("fallback"))
        assertEquals("um único recálculo agendado", 1, queued.size)
        runQueued()
        assertEquals("v2", memo.getNonBlocking("fallback"))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `calculo que comecou antes do invalidate nao fica marcado como valido`() {
        lateinit var memo: BackgroundMemo
        var invalidateDuring = true
        memo = BackgroundMemo(1_000L, 3_000L, 15_000L, { now }, background = { queued += it }) {
            runs++
            if (invalidateDuring) { invalidateDuring = false; memo.invalidate() } // gravação no meio do cálculo
            "v$runs"
        }
        assertEquals("v1", memo.get())
        assertEquals("o resultado de antes da gravação não vale: recalcula", "v2", memo.get())
        assertEquals("v2", memo.get())
    }

    @Test
    fun `so a primeira chamada calcula na hora e getFresh calcula sempre`() {
        val memo = memo()
        assertEquals("v1", memo.get())
        assertTrue(queued.isEmpty())
        memo.invalidate()
        assertEquals("pedido explícito de valor fresco", "v2", memo.getFresh())
    }

    @Test
    fun `erro no calculo vira resposta de falha, nao derruba a tela`() {
        val memo = memo(fail = true)
        val answer = memo.get()
        assertTrue(answer.contains("\"ok\":false"))
        assertTrue(answer.contains("falhou"))
    }
}
