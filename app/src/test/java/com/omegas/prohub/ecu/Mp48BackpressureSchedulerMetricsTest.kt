package com.omegas.prohub.ecu

import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class Mp48BackpressureSchedulerMetricsTest {
    @Test
    fun readOnlyTransactionRecordsConservativeDelayWithoutChangingReply() {
        val scheduler = Mp48BackpressureScheduler(
            delegate = SleepingScheduler(sleepMs = 25L, reportedSerialMs = 5L),
            readOnlyCapacity = 1,
            criticalCapacity = 1,
        )

        val reply = scheduler.transaction(
            request = byteArrayOf(0x48, 0x0B, 0x53),
            reason = "122A metric test",
            timeoutMs = 200,
            purgeBefore = false,
            expectedSessionId = 7L,
            workClass = Mp48WorkClass.READ_ONLY,
            telemetryAfter = true,
        )

        assertTrue(reply.ok)
        val metrics = scheduler.metricsSnapshot()
        assertEquals(1L, metrics.readOnlyAccepted)
        assertEquals(0L, metrics.readOnlyRejected)
        assertEquals(1L, metrics.readOnlyAdmissionSamples)
        assertEquals(1L, metrics.readOnlySchedulerDelaySamples)
        assertTrue(metrics.readOnlyMaxSchedulerDelayUpperBoundNanos >= 10_000_000L)
        assertTrue((metrics.readOnlyAverageSchedulerDelayUpperBoundMs ?: 0.0) >= 10.0)
    }

    @Test
    fun readOnlyLaneRemainsImmediateRejectWhenCapacityIsOccupied() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val scheduler = Mp48BackpressureScheduler(
            delegate = BlockingScheduler(entered, release),
            readOnlyCapacity = 1,
            criticalCapacity = 1,
        )
        val first = Thread {
            try {
                scheduler.transaction(
                    request = byteArrayOf(1),
                    reason = "first",
                    timeoutMs = 500,
                    purgeBefore = false,
                    expectedSessionId = 7L,
                    workClass = Mp48WorkClass.READ_ONLY,
                    telemetryAfter = true,
                )
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        first.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        try {
            scheduler.transaction(
                request = byteArrayOf(2),
                reason = "second",
                timeoutMs = 500,
                purgeBefore = false,
                expectedSessionId = 7L,
                workClass = Mp48WorkClass.READ_ONLY,
                telemetryAfter = true,
            )
            throw AssertionError("second READ_ONLY should be rejected immediately")
        } catch (_: Mp48BackpressureRejectedException) {
            // expected
        } finally {
            release.countDown()
            first.join(1_000L)
        }

        assertEquals(null, failure.get())
        val metrics = scheduler.metricsSnapshot()
        assertEquals(1L, metrics.readOnlyAccepted)
        assertEquals(1L, metrics.readOnlyRejected)
        assertEquals(2L, metrics.readOnlyAdmissionSamples)
    }

    private class SleepingScheduler(
        private val sleepMs: Long,
        private val reportedSerialMs: Long,
    ) : Mp48SerialScheduler {
        override fun isConnected(): Boolean = true
        override fun currentSessionId(): Long = 7L

        override fun transaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
        ): UsbProtocolReply {
            Thread.sleep(sleepMs)
            return UsbProtocolReply(true, status = 0x53, request = request, elapsedMs = reportedSerialMs)
        }

        override fun <T> unit(
            reason: String,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
            waitTimeoutMs: Long,
            block: (Mp48SerialUnit) -> T,
        ): T = block(fakeUnit(expectedSessionId, reportedSerialMs))
    }

    private class BlockingScheduler(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : Mp48SerialScheduler {
        override fun isConnected(): Boolean = true
        override fun currentSessionId(): Long = 7L

        override fun transaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
        ): UsbProtocolReply {
            entered.countDown()
            release.await(1, TimeUnit.SECONDS)
            return UsbProtocolReply(true, status = 0x53, request = request, elapsedMs = 1L)
        }

        override fun <T> unit(
            reason: String,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
            waitTimeoutMs: Long,
            block: (Mp48SerialUnit) -> T,
        ): T = block(fakeUnit(expectedSessionId, 1L))
    }

    companion object {
        private fun fakeUnit(sessionId: Long, elapsedMs: Long): Mp48SerialUnit = object : Mp48SerialUnit {
            override val sessionId: Long = sessionId
            override fun transaction(
                request: ByteArray,
                reason: String,
                timeoutMs: Int,
                purgeBefore: Boolean,
            ): UsbProtocolReply = UsbProtocolReply(true, status = 0x53, request = request, elapsedMs = elapsedMs)
        }
    }
}
