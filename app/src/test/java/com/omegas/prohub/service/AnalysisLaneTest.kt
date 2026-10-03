package com.omegas.prohub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AnalysisLaneTest {
    private fun named(name: String) = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    @Test
    fun `autoCalTick keeps running on time on the shared scheduler while analysis is blocked`() {
        val serviceThread = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "test-service").apply { isDaemon = true }
        }
        val analysisThread = named("test-analysis")
        try {
            val lane = AnalysisLane(analysisThread)
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val ranOn = AtomicReference("")

            // healthTick: entrega o trabalho pesado à faixa de análise e volta na hora.
            serviceThread.execute {
                lane.submit {
                    ranOn.set(Thread.currentThread().name)
                    started.countDown()
                    release.await() // análise TRAVADA (refino/diário/full_snapshot lentos)
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))

            // autoCalTick na MESMA thread do serviço, com atraso fixo curto, enquanto a análise segue travada.
            val ticks = AtomicInteger(0)
            val fiveTicks = CountDownLatch(5)
            val startedAt = System.nanoTime()
            val task = serviceThread.scheduleWithFixedDelay({
                ticks.incrementAndGet()
                fiveTicks.countDown()
            }, 0L, 20L, TimeUnit.MILLISECONDS)
            assertTrue("o autoCalTick não pode esperar pela análise", fiveTicks.await(2, TimeUnit.SECONDS))
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            assertTrue("5 ticks de 20 ms em tempo (levou ${elapsedMs} ms)", elapsedMs < 1_500L)
            assertTrue(lane.isBusy())
            assertEquals("test-analysis", ranOn.get())
            assertNotEquals("test-service", ranOn.get())
            task.cancel(false)
            release.countDown()
        } finally {
            serviceThread.shutdownNow()
            analysisThread.shutdownNow()
        }
    }

    @Test
    fun `a new analysis round while one is running is dropped, not queued`() {
        val executor = named("test-analysis-2")
        try {
            val lane = AnalysisLane(executor)
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val secondRan = AtomicInteger(0)
            assertTrue(lane.submit { started.countDown(); release.await() })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertFalse(lane.submit { secondRan.incrementAndGet() })
            assertFalse(lane.submit { secondRan.incrementAndGet() })
            release.countDown()
            val deadline = System.nanoTime() + 2_000_000_000L
            while (lane.isBusy() && System.nanoTime() < deadline) Thread.sleep(5)
            assertFalse(lane.isBusy())
            assertEquals(0, secondRan.get())
            assertEquals(2L, lane.json().getLong("skipped"))
            assertEquals(1L, lane.json().getLong("completed"))
            // Terminou: a próxima rodada volta a ser aceita.
            assertTrue(lane.submit { secondRan.incrementAndGet() })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `an analysis failure is counted and never stops the lane`() {
        val executor = named("test-analysis-3")
        try {
            val seen = AtomicReference<Throwable?>(null)
            val lane = AnalysisLane(executor, onFailure = { seen.set(it) })
            assertTrue(lane.submit { error("falha de análise") })
            val deadline = System.nanoTime() + 2_000_000_000L
            while (lane.isBusy() && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(1L, lane.json().getLong("failed"))
            assertTrue(seen.get() is IllegalStateException)
            assertTrue(lane.submit { })
        } finally {
            executor.shutdownNow()
        }
    }
}
