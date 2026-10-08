package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2: a leitura não bloqueante nunca calcula na thread da WebView; o recálculo vai para o agendador injetado. */
class BackgroundMemoNonBlockingTest {
    private var now = 0L
    private var runs = 0
    private val queued = mutableListOf<Runnable>()

    private fun memo() = BackgroundMemo(
        refreshMs = 1_000L,
        staleMs = 2_500L,
        watchMs = 15_000L,
        clock = { now },
        background = { queued += it },
    ) {
        runs += 1
        "v$runs"
    }

    private fun runQueued() {
        val pending = queued.toList()
        queued.clear()
        pending.forEach { it.run() }
    }

    @Test
    fun `non blocking read never computes on caller and serves stale while warmer refreshes`() {
        val memo = memo()

        assertEquals("warming", memo.getNonBlocking("warming"))
        assertEquals(0, runs)
        assertTrue("sem valor anterior não há o que recalcular", queued.isEmpty())

        assertTrue(memo.refreshIfWatched())
        assertEquals("v1", memo.getNonBlocking("warming"))
        assertEquals(1, runs)

        now += 4_000L
        assertEquals("stale value must not block JS", "v1", memo.getNonBlocking("warming"))
        assertEquals(1, runs)
        assertTrue("valor só velho (válido) espera o relógio de fundo", queued.isEmpty())

        assertTrue(memo.refreshIfWatched())
        assertEquals("v2", memo.getNonBlocking("warming"))

        memo.invalidate()
        assertEquals("last safe value survives invalidation until background refresh", "v2", memo.getNonBlocking("warming"))
        assertEquals("v2", memo.getNonBlocking("warming"))
        assertEquals("nada calculado na chamada", 2, runs)
        assertEquals("um único recálculo agendado", 1, queued.size)
        runQueued()
        assertEquals("v3", memo.getNonBlocking("warming"))
        assertEquals(3, runs)
    }

    @Test
    fun `get keeps computing on caller after invalidate while getFresh stays the explicit path`() {
        val memo = memo()
        assertEquals("v1", memo.get())
        memo.invalidate()
        assertEquals("v1", memo.getNonBlocking("x"))
        assertEquals("a chamada não calcula; só agenda", 1, runs)
        assertEquals(1, queued.size)
        assertEquals("v2", memo.getFresh())
        assertEquals(2, runs)
        // A tarefa agendada já foi superada pelo getFresh: não recalcula em duplicata.
        runQueued()
        assertEquals(2, runs)
        assertEquals("v2", memo.getNonBlocking("x"))
        assertTrue(queued.isEmpty())
    }
}
