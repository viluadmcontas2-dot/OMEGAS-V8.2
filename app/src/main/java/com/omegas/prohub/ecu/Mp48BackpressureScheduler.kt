package com.omegas.prohub.ecu

import com.omegas.prohub.learning.LearningMutationAuthority
import com.omegas.prohub.learning.LearningMutationState
import com.omegas.prohub.learning.NativeAnchorTelemetryWindow
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.util.RuntimeBackpressurePolicy
import org.json.JSONObject
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
 *
 * O mesmo ponto de admissão abre a quarentena científica antes de qualquer
 * MANUAL_WRITE. Assim nenhum writer atual ou futuro pode esquecer a fronteira
 * de mutação enquanto continuar obedecendo ao árbitro serial único.
 */
class Mp48BackpressureScheduler(
    private val delegate: Mp48SerialScheduler,
    readOnlyCapacity: Int = RuntimeBackpressurePolicy.SECONDARY_READ_PENDING_CAPACITY,
    criticalCapacity: Int = RuntimeBackpressurePolicy.CRITICAL_SERIAL_RESERVED_CAPACITY,
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
        mutationAware(workClass, expectedSessionId, reason) {
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
    }

    override fun <T> unit(
        reason: String,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
        waitTimeoutMs: Long,
        block: (Mp48SerialUnit) -> T,
    ): T = withAdmission(workClass, waitTimeoutMs.coerceAtLeast(1L)) {
        mutationAware(workClass, expectedSessionId, reason) {
            delegate.unit(
                reason = reason,
                expectedSessionId = expectedSessionId,
                workClass = workClass,
                telemetryAfter = telemetryAfter,
                waitTimeoutMs = waitTimeoutMs,
                block = block,
            )
        }
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

    fun metricsJson(): JSONObject = metricsSnapshot().let { metrics ->
        JSONObject()
            .put("readOnlyCapacity", metrics.readOnlyCapacity)
            .put("criticalCapacity", metrics.criticalCapacity)
            .put("readOnlyInFlight", metrics.readOnlyInFlight)
            .put("criticalInFlight", metrics.criticalInFlight)
            .put("readOnlyAccepted", metrics.readOnlyAccepted)
            .put("readOnlyRejected", metrics.readOnlyRejected)
            .put("criticalAccepted", metrics.criticalAccepted)
            .put("criticalRejected", metrics.criticalRejected)
            .put("learningMutation", LearningMutationAuthority.current().toJson())
    }

    private fun <T> mutationAware(
        workClass: Mp48WorkClass,
        expectedSessionId: Long,
        reason: String,
        block: () -> T,
    ): T {
        if (workClass == Mp48WorkClass.MANUAL_WRITE) {
            LearningMutationAuthority.beginManualWrite(expectedSessionId, reason)
        }
        return try {
            block()
        } catch (error: Throwable) {
            val mutation = LearningMutationAuthority.current()
            if (mutation.usbSessionId == expectedSessionId && mutation.state != LearningMutationState.STABLE) {
                LearningMutationAuthority.markUnknown(
                    expectedSessionId,
                    "${workClass.name} failed during mutation: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            throw error
        }
    }

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
