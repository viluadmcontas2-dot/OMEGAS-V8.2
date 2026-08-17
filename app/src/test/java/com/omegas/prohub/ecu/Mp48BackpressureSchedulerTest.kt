package com.omegas.prohub.ecu

import com.omegas.prohub.learning.NativeAnchorTelemetryWindow
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Mp48BackpressureSchedulerTest {
    @Test
    fun `READ ONLY rejeita excedente e conta sem bloquear acquisition`() {
        val delegate = BlockingScheduler()
        val scheduler = Mp48BackpressureScheduler(delegate, readOnlyCapacity = 1, criticalCapacity = 1)
        val pool = Executors.newSingleThreadExecutor()
        val first = pool.submit<UsbProtocolReply> {
            scheduler.transaction(byteArrayOf(1), "first", workClass = Mp48WorkClass.READ_ONLY)
        }
        assertTrue(delegate.started.await(1, TimeUnit.SECONDS))

        assertThrows(Mp48BackpressureRejectedException::class.java) {
            scheduler.transaction(byteArrayOf(2), "overflow", workClass = Mp48WorkClass.READ_ONLY)
        }
        val metrics = scheduler.metricsSnapshot()
        assertEquals(1L, metrics.readOnlyRejected)
        assertEquals(1, metrics.readOnlyInFlight)

        delegate.release.countDown()
        first.get(1, TimeUnit.SECONDS)
        pool.shutdownNow()
    }

    @Test
    fun `MANUAL WRITE e SAFETY usam lane reservada`() {
        val delegate = ImmediateScheduler()
        val scheduler = Mp48BackpressureScheduler(delegate, readOnlyCapacity = 1, criticalCapacity = 1)
        scheduler.transaction(byteArrayOf(1), "write", workClass = Mp48WorkClass.MANUAL_WRITE)
        scheduler.unit(reason = "safety", workClass = Mp48WorkClass.SAFETY) { unit ->
            unit.transaction(byteArrayOf(2), "safe")
        }
        val metrics = scheduler.metricsSnapshot()
        assertEquals(2L, metrics.criticalAccepted)
        assertEquals(0L, metrics.criticalRejected)
    }

    private open class ImmediateScheduler : Mp48SerialScheduler {
        override fun isConnected() = true
        override fun currentSessionId() = 77L
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean) =
            UsbProtocolReply(ok = true, status = 0x53, payload = byteArrayOf())
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T =
            block(object : Mp48SerialUnit {
                override val sessionId: Long = 77L
                override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) =
                    UsbProtocolReply(ok = true, status = 0x53, payload = byteArrayOf())
            })
        override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long): List<NativeAnchorTelemetryWindow.Frame> = emptyList()
    }

    private class BlockingScheduler : ImmediateScheduler() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean): UsbProtocolReply {
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            return UsbProtocolReply(ok = true, status = 0x53)
        }
    }
}
