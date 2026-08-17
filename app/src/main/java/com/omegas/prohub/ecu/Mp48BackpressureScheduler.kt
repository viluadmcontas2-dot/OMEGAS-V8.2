package com.omegas.prohub.ecu

import com.omegas.prohub.learning.NativeAnchorTelemetryWindow
import com.omegas.prohub.usb.UsbProtocolReply
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Mp48BackpressureRejectedException(message: String) : IllegalStateException(message)

data class Mp48BackpressureMetrics(
    val readOnlyCapacity: Int,
    val criticalCapacity: Int,
    val readOnlyInFlight: Int,
    val criticalInFlight: Int,
    val readOnlyAccepted: Long,
    val readOnlyRejected: Long,
    val criticalAccepted: Long,
    val criticalRejected: Long,
)

/**
 * Admission controller sobre a única authority serial MP48.
 * Não cria thread, fila ou transporte: apenas limita quantos callers externos
 * podem aguardar a engine simultaneamente.
 */
class Mp48BackpressureScheduler(
    private val delegate: Mp48SerialScheduler,
    readOnlyCapacity: Int = 32,
    criticalCapacity: Int = 8,
) : Mp48SerialScheduler {
    private val readOnlyCap = readOnlyCapacity.coerceAtLeast(1)
    private val criticalCap = criticalCapacity.coerceAtLeast(1)
    private val readOnlyPermits = Semaphore(readOnlyCap, true)
    private val criticalPermits = Semaphore(criticalCap, true)
    private val readOnlyInFlight = AtomicInteger(0)
    private val criticalInFlight = AtomicInteger(0)
    private val readOnlyAccepted = AtomicLong(0L)
    private val readOnlyRejected = AtomicLong(0L)
    private val criticalAccepted = AtomicLong(0L)
    private val criticalRejected = AtomicLong(0L)

    override fun isConnected(): Boolean = delegate.isConnected()
    override fun currentSessionId(): Long = delegate.currentSessionId()

    override fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        purgeBefore: Boolean,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
    ): UsbProtocolReply = withAdmission(workClass, timeoutMs.toLong().coerceAtLeast(1L)) {
        delegate.transaction(
            request = request,
            reason = reason,
            timeoutMs = timeoutMs,
            purgeBefore = purgeBefore,
            expectedSessionId = expectedSessionId,
            workClass = workClass,
            telemetryAfter = telemetryAfter,
        )
    }

    override fun <T> unit(
        reason: String,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
        waitTimeoutMs: Long,
        block: (Mp48SerialUnit) -> T,
    ): T = withAdmission(workClass, waitTimeoutMs.coerceAtLeast(1L)) {
        delegate.unit(
            reason = reason,
            expectedSessionId = expectedSessionId,
            workClass = workClass,
            telemetryAfter = telemetryAfter,
            waitTimeoutMs = waitTimeoutMs,
            block = block,
        )
    }

    override fun recentTelemetryFrames(
        fromElapsedMs: Long,
        toElapsedMs: Long,
    ): List<NativeAnchorTelemetryWindow.Frame> = delegate.recentTelemetryFrames(fromElapsedMs, toElapsedMs)

    fun metricsSnapshot(): Mp48BackpressureMetrics = Mp48BackpressureMetrics(
        readOnlyCapacity = readOnlyCap,
        criticalCapacity = criticalCap,
        readOnlyInFlight = readOnlyInFlight.get(),
        criticalInFlight = criticalInFlight.get(),
        readOnlyAccepted = readOnlyAccepted.get(),
        readOnlyRejected = readOnlyRejected.get(),
        criticalAccepted = criticalAccepted.get(),
        criticalRejected = criticalRejected.get(),
    )

    private fun <T> withAdmission(workClass: Mp48WorkClass, waitTimeoutMs: Long, block: () -> T): T {
        val critical = workClass == Mp48WorkClass.MANUAL_WRITE || workClass == Mp48WorkClass.SAFETY
        val semaphore = if (critical) criticalPermits else readOnlyPermits
        val inFlight = if (critical) criticalInFlight else readOnlyInFlight
        val accepted = if (critical) criticalAccepted else readOnlyAccepted
        val rejected = if (critical) criticalRejected else readOnlyRejected
        val acquired = if (critical) {
            try {
                semaphore.tryAcquire(waitTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        } else {
            semaphore.tryAcquire()
        }
        if (!acquired) {
            rejected.incrementAndGet()
            val lane = if (critical) "MANUAL_WRITE/SAFETY" else "READ_ONLY"
            throw Mp48BackpressureRejectedException("Backpressure MP48: lane $lane saturada")
        }
        accepted.incrementAndGet()
        inFlight.incrementAndGet()
        return try {
            block()
        } finally {
            inFlight.decrementAndGet()
            semaphore.release()
        }
    }
}
