package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousWindowNoveltyTest {
    @Test
    fun `primeira janela e integralmente nova`() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 0L,
            endedAtElapsedMs = 250L,
            frameCount = 6,
            medianIntervalMs = 50L,
            previouslyRepresentedThroughElapsedMs = null,
        )
        assertEquals(6, result.newFrames)
        assertEquals(1.0, result.fraction, 0.0)
        assertTrue(result.fullyNew)
        assertFalse(result.duplicate)
        assertEquals(250L, result.representedThroughElapsedMs)
    }

    @Test
    fun `janela deslizante absorve somente o quadro novo`() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 50L,
            endedAtElapsedMs = 300L,
            frameCount = 6,
            medianIntervalMs = 50L,
            previouslyRepresentedThroughElapsedMs = 250L,
        )
        assertEquals(1, result.newFrames)
        assertEquals(1.0 / 6.0, result.fraction, 1e-12)
        assertEquals(300L, result.representedThroughElapsedMs)
    }

    @Test
    fun `fronteira representada avanca em toda janela sobreposta`() {
        val second = ContinuousWindowNovelty.calculate(50L, 300L, 6, 50L, 250L)
        val third = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 100L,
            endedAtElapsedMs = 350L,
            frameCount = 6,
            medianIntervalMs = 50L,
            previouslyRepresentedThroughElapsedMs = second.representedThroughElapsedMs,
        )
        assertEquals(1, third.newFrames)
        assertEquals(350L, third.representedThroughElapsedMs)
    }

    @Test
    fun `reavaliacao identica nao cria outro voto`() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 0L,
            endedAtElapsedMs = 250L,
            frameCount = 6,
            medianIntervalMs = 50L,
            previouslyRepresentedThroughElapsedMs = 250L,
        )
        assertEquals(0, result.newFrames)
        assertEquals(0.0, result.fraction, 0.0)
        assertTrue(result.duplicate)
    }

    @Test
    fun `dez mil repeticoes da mesma janela continuam com ganho marginal zero`() {
        var representedThrough = 250L
        var accumulatedNewFrames = 0
        repeat(10_000) {
            val duplicate = ContinuousWindowNovelty.calculate(
                startedAtElapsedMs = 0L,
                endedAtElapsedMs = 250L,
                frameCount = 6,
                medianIntervalMs = 50L,
                previouslyRepresentedThroughElapsedMs = representedThrough,
            )
            accumulatedNewFrames += duplicate.newFrames
            representedThrough = duplicate.representedThroughElapsedMs
            assertTrue(duplicate.duplicate)
            assertEquals(0.0, duplicate.fraction, 0.0)
        }
        assertEquals(0, accumulatedNewFrames)
        assertEquals(250L, representedThrough)
    }

    @Test
    fun `janela posterior sem sobreposicao volta a ter peso integral`() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 400L,
            endedAtElapsedMs = 650L,
            frameCount = 6,
            medianIntervalMs = 50L,
            previouslyRepresentedThroughElapsedMs = 250L,
        )
        assertEquals(6, result.newFrames)
        assertTrue(result.fullyNew)
    }

    @Test
    fun `cadencia parcial ainda conta ao menos o quadro final`() {
        val result = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = 100L,
            endedAtElapsedMs = 400L,
            frameCount = 6,
            medianIntervalMs = 60L,
            previouslyRepresentedThroughElapsedMs = 371L,
        )
        assertEquals(1, result.newFrames)
        assertEquals(1.0 / 6.0, result.fraction, 1e-12)
    }
}
