package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SessionEventDropsTest {
    @Test
    fun `evento que chega depois da parada e contado como descartado`() {
        val dropped = AtomicLong(0L)
        var written = 0
        assertFalse(SessionEventDrops.writeOrCount(recording = false, dropped = dropped) { written += 1 })
        assertTrue(SessionEventDrops.writeOrCount(recording = true, dropped = dropped) { written += 1 })
        assertEquals(1L, dropped.get())
        assertEquals(1, written)
    }

    @Test
    fun `fila descartada no encerramento entra na contagem`() {
        val dropped = AtomicLong(0L)
        val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16))
        val release = CountDownLatch(1)
        worker.execute { release.await(5, TimeUnit.SECONDS) }
        repeat(3) { worker.execute { } }
        try {
            assertEquals(3, SessionEventDrops.shutdownCounting(worker, dropped))
            assertEquals(3L, dropped.get())
            assertTrue(worker.isShutdown)
        } finally {
            release.countDown()
        }
    }
}
