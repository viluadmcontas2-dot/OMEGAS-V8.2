package com.omegas.prohub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Defeito P2-4: o tique de análise não é tudo-ou-nada e não se perde quando a faixa está ocupada. */
class AnalysisRobustnessTest {
    private fun named(name: String) = Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }

    @Test
    fun `um passo que lanca vira aviso e os demais continuam`() {
        val ran = ArrayList<String>()
        val warnings = ArrayList<String>()
        GuardedSteps.run(
            listOf(
                "diario" to { throw IllegalStateException("diário quebrou") },
                "stallWatch" to { ran += "stallWatch" },
                "veredito" to { ran += "veredito" },
                "overlay" to { ran += "overlay" },
                "notificacao" to { ran += "notificacao" },
            ),
        ) { warnings += it }
        assertEquals(listOf("stallWatch", "veredito", "overlay", "notificacao"), ran)
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("diario"))
    }

    @Test
    fun `aviso que lanca nao derruba os passos seguintes`() {
        val ran = AtomicInteger()
        GuardedSteps.run(listOf("a" to { throw RuntimeException("x") }, "b" to { ran.incrementAndGet() })) {
            throw RuntimeException("log quebrou")
        }
        assertEquals(1, ran.get())
    }

    @Test
    fun `tique recusado por faixa ocupada roda uma vez quando a faixa libera`() {
        val executor = named("test-analysis-rerun")
        try {
            val lane = AnalysisLane(executor)
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val reruns = AtomicInteger()
            val rerunDone = CountDownLatch(1)
            assertTrue(lane.submit { started.countDown(); release.await() }) // ex.: stateChanged ocupando a faixa
            assertTrue(started.await(2, TimeUnit.SECONDS))
            // healthTick cai exatamente agora: antes era perdido; agora fica marcado como sujo (3 recusas = 1 rerun)
            repeat(3) {
                assertFalse(lane.submit(rerunKey = "analysisTick") { reruns.incrementAndGet(); rerunDone.countDown() })
            }
            release.countDown()
            assertTrue(rerunDone.await(2, TimeUnit.SECONDS))
            Thread.sleep(150)
            assertEquals(1, reruns.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `sem rerunKey a recusa continua descartando (coalescente)`() {
        val executor = named("test-analysis-coalesce")
        try {
            val lane = AnalysisLane(executor)
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val ran = AtomicInteger()
            lane.submit { started.countDown(); release.await() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertFalse(lane.submit { ran.incrementAndGet() })
            release.countDown()
            Thread.sleep(200)
            assertEquals(0, ran.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `rodada travada por mais de 30 s avisa e deixa uma so entrar atras sem rodar duas juntas`() {
        val executor = named("test-analysis-hang")
        try {
            var now = 0L
            val hangWarnings = AtomicInteger()
            val lane = AnalysisLane(executor, clock = { now }, onHang = { hangWarnings.incrementAndGet() })
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val concurrent = AtomicInteger()
            val maxConcurrent = AtomicInteger()
            val secondDone = CountDownLatch(1)
            assertTrue(lane.submit { started.countDown(); concurrent.incrementAndGet(); release.await(); concurrent.decrementAndGet() })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            now = 10_000L
            assertFalse("ainda não é travamento", lane.submit { })
            now = 31_000L
            val accepted = lane.submit {
                maxConcurrent.set(maxOf(maxConcurrent.get(), concurrent.incrementAndGet()))
                concurrent.decrementAndGet(); secondDone.countDown()
            }
            assertTrue("passou de 30 s: a próxima rodada entra", accepted)
            assertFalse("só UMA entra atrás da travada", lane.submit { })
            assertEquals(1, hangWarnings.get())
            release.countDown()
            assertTrue(secondDone.await(2, TimeUnit.SECONDS))
            assertEquals("a rodada nova viu só a si mesma rodando", 1, maxConcurrent.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
