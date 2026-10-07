package com.omegas.prohub.ecu

import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class Mp48SerialWorkQueueTest {
    private val unit = object : Mp48SerialUnit {
        override val sessionId: Long = 7L
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) =
            UsbProtocolReply(true, request = request)
    }

    @Test
    fun `parar a engine falha todos os pendentes e libera quem espera`() {
        val queue = Mp48SerialWorkQueue(criticalWaitMs = 60_000L)
        val write = queue.submit("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) { "ok" }
        val read = queue.submit("leitura", 7L, Mp48WorkClass.READ_ONLY, true) { "ok" }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writeResult = pool.submit<Throwable?> { runCatching { write.await(10_000L) }.exceptionOrNull() }
            val readResult = pool.submit<Throwable?> { runCatching { read.await(10_000L) }.exceptionOrNull() }

            assertEquals(2, queue.failAll(IllegalStateException("Engine MP48 parada")))
            assertEquals(0, queue.size)

            val writeError = writeResult.get(2, TimeUnit.SECONDS)
            val readError = readResult.get(2, TimeUnit.SECONDS)
            assertTrue(writeError is IllegalStateException)
            assertEquals("Engine MP48 parada", writeError!!.message)
            assertFalse("falha antes de iniciar não é resultado desconhecido", writeError is Mp48CriticalWorkTimeoutException)
            assertEquals("Engine MP48 parada", readError!!.message)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `item que falhou nunca roda depois`() {
        val queue = Mp48SerialWorkQueue()
        val ran = AtomicBoolean(false)
        queue.submit("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) { ran.set(true) }
        val polled = queue.poll()!!
        assertTrue(polled.fail(IllegalStateException("x")))
        assertFalse(polled.run(unit))
        assertFalse(ran.get())
    }

    @Test
    fun `prioridade SAFETY antes de MANUAL_WRITE antes de READ_ONLY e FIFO na mesma classe`() {
        val queue = Mp48SerialWorkQueue()
        queue.submit("r1", 1L, Mp48WorkClass.READ_ONLY, true) { }
        queue.submit("w1", 1L, Mp48WorkClass.MANUAL_WRITE, true) { }
        queue.submit("s1", 1L, Mp48WorkClass.SAFETY, true) { }
        queue.submit("w2", 1L, Mp48WorkClass.MANUAL_WRITE, true) { }
        assertEquals(listOf("s1", "w1", "w2", "r1"), List(4) { queue.poll()!!.reason })
    }

    @Test
    fun `MANUAL_WRITE que nunca comecou expira com resultado sem mutacao`() {
        val queue = Mp48SerialWorkQueue(criticalWaitMs = 80L)
        val write = queue.submit("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) { "ok" }
        val started = System.nanoTime()
        try {
            write.await(10L)
            fail("deveria expirar")
        } catch (e: Mp48CriticalWorkTimeoutException) {
            assertFalse(e.mutationMayHaveStarted)
        }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 70L)
        // A unidade cancelada não roda mais, mesmo que a engine a retire da fila depois.
        assertFalse(queue.poll()!!.run(unit))
    }

    @Test
    fun `MANUAL_WRITE que comecou e nao terminou expira como resultado desconhecido`() {
        val queue = Mp48SerialWorkQueue(criticalWaitMs = 80L)
        val release = CountDownLatch(1)
        val write = queue.submit("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) {
            release.await(5, TimeUnit.SECONDS)
            "ok"
        }
        val serial = Executors.newSingleThreadExecutor()
        try {
            serial.execute { queue.poll()!!.run(unit) }
            try {
                write.await(10L)
                fail("deveria expirar")
            } catch (e: Mp48CriticalWorkTimeoutException) {
                assertTrue(e.mutationMayHaveStarted)
            }
        } finally {
            release.countDown()
            serial.shutdownNow()
        }
    }

    @Test
    fun `MANUAL_WRITE usa a espera maior entre a do chamador e o piso critico`() {
        val queue = Mp48SerialWorkQueue(criticalWaitMs = 50L)
        val write = queue.submit("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) { "ok" }
        val serial = Executors.newSingleThreadScheduledExecutor()
        try {
            serial.schedule({ queue.poll()!!.run(unit) }, 150L, TimeUnit.MILLISECONDS)
            assertEquals("ok", write.await(2_000L))
        } finally {
            serial.shutdownNow()
        }
    }

    @Test
    fun `READ_ONLY expira com TimeoutException e nao roda depois`() {
        val queue = Mp48SerialWorkQueue()
        val read = queue.submit("leitura", 7L, Mp48WorkClass.READ_ONLY, true) { "ok" }
        try {
            read.await(1L)
            fail("deveria expirar")
        } catch (_: TimeoutException) {
        }
        assertFalse(queue.poll()!!.run(unit))
    }

    @Test
    fun `excecao do bloco chega ao chamador sem embrulho`() {
        val queue = Mp48SerialWorkQueue()
        val write = queue.submit<String>("escrita", 7L, Mp48WorkClass.MANUAL_WRITE, true) {
            throw IllegalArgumentException("NACK")
        }
        assertTrue(queue.poll()!!.run(unit))
        try {
            write.await(1_000L)
            fail("deveria falhar")
        } catch (e: IllegalArgumentException) {
            assertEquals("NACK", e.message)
        }
    }
}
