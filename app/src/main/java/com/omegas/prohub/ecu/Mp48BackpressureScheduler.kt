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
    val readOnlyAdmissionSamples: Long,
    val readOnlyAdmissionWaitNanos: Long,
    val readOnlyMaxAdmissionWaitNanos: Long,
    val criticalAdmissionSamples: Long,
    val criticalAdmissionWaitNanos: Long,
    val criticalMaxAdmissionWaitNanos: Long,
    val readOnlySchedulerDelaySamples: Long,
    val readOnlySchedulerDelayUpperBoundNanos: Long,
    val readOnlyMaxSchedulerDelayUpperBoundNanos: Long,
    val criticalSchedulerDelaySamples: Long,
    val criticalSchedulerDelayUpperBoundNanos: Long,
    val criticalMaxSchedulerDelayUpperBoundNanos: Long,
) {
    val readOnlyAverageAdmissionWaitMs: Double?
        get() = readOnlyAdmissionSamples.takeIf { it > 0L }
            ?.let { readOnlyAdmissionWaitNanos.toDouble() / it.toDouble() / 1_000_000.0 }

    val criticalAverageAdmissionWaitMs: Double?
        get() = criticalAdmissionSamples.takeIf { it > 0L }
            ?.let { criticalAdmissionWaitNanos.toDouble() / it.toDouble() / 1_000_000.0 }

    val readOnlyAverageSchedulerDelayUpperBoundMs: Double?
        get() = readOnlySchedulerDelaySamples.takeIf { it > 0L }
            ?.let { readOnlySchedulerDelayUpperBoundNanos.toDouble() / it.toDouble() / 1_000_000.0 }

    val criticalAverageSchedulerDelayUpperBoundMs: Double?
        get() = criticalSchedulerDelaySamples.takeIf { it > 0L }
            ?.let { criticalSchedulerDelayUpperBoundNanos.toDouble() / it.toDouble() / 1_000_000.0 }
}

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
    private val readOnlyAdmissionSamples = AtomicLong(0L)
    private val readOnlyAdmissionWaitNanos = AtomicLong(0L)
    private val readOnlyMaxAdmissionWaitNanos = AtomicLong(0L)
    private val criticalAdmissionSamples = AtomicLong(0L)
    private val criticalAdmissionWaitNanos = AtomicLong(0L)
    private val criticalMaxAdmissionWaitNanos = AtomicLong(0L)
    private val readOnlySchedulerDelaySamples = AtomicLong(0L)
    private val readOnlySchedulerDelayUpperBoundNanos = AtomicLong(0L)
    private val readOnlyMaxSchedulerDelayUpperBoundNanos = AtomicLong(0L)
    private val criticalSchedulerDelaySamples = AtomicLong(0L)
    private val criticalSchedulerDelayUpperBoundNanos = AtomicLong(0L)
    private val criticalMaxSchedulerDelayUpperBoundNanos = AtomicLong(0L)

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
            val delegateStartedNs = System.nanoTime()
            val reply = delegate.transaction(
                request = request,
                reason = reason,
                timeoutMs = timeoutMs,
                purgeBefore = purgeBefore,
                expectedSessionId = expectedSessionId,
                workClass = workClass,
                telemetryAfter = telemetryAfter,
            )
            recordSchedulerDelayUpperBound(
                workClass = workClass,
                delegateElapsedNanos = (System.nanoTime() - delegateStartedNs).coerceAtLeast(0L),
                serialElapsedMs = reply.elapsedMs,
            )
            reply
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
        readOnlyAdmissionSamples = readOnlyAdmissionSamples.get(),
        readOnlyAdmissionWaitNanos = readOnlyAdmissionWaitNanos.get(),
        readOnlyMaxAdmissionWaitNanos = readOnlyMaxAdmissionWaitNanos.get(),
        criticalAdmissionSamples = criticalAdmissionSamples.get(),
        criticalAdmissionWaitNanos = criticalAdmissionWaitNanos.get(),
        criticalMaxAdmissionWaitNanos = criticalMaxAdmissionWaitNanos.get(),
        readOnlySchedulerDelaySamples = readOnlySchedulerDelaySamples.get(),
        readOnlySchedulerDelayUpperBoundNanos = readOnlySchedulerDelayUpperBoundNanos.get(),
        readOnlyMaxSchedulerDelayUpperBoundNanos = readOnlyMaxSchedulerDelayUpperBoundNanos.get(),
        criticalSchedulerDelaySamples = criticalSchedulerDelaySamples.get(),
        criticalSchedulerDelayUpperBoundNanos = criticalSchedulerDelayUpperBoundNanos.get(),
        criticalMaxSchedulerDelayUpperBoundNanos = criticalMaxSchedulerDelayUpperBoundNanos.get(),
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
            .put("readOnlyAdmissionSamples", metrics.readOnlyAdmissionSamples)
            .put("readOnlyAverageAdmissionWaitMs", metrics.readOnlyAverageAdmissionWaitMs ?: JSONObject.NULL)
            .put("readOnlyMaxAdmissionWaitMs", metrics.readOnlyMaxAdmissionWaitNanos.toDouble() / 1_000_000.0)
            .put("criticalAdmissionSamples", metrics.criticalAdmissionSamples)
            .put("criticalAverageAdmissionWaitMs", metrics.criticalAverageAdmissionWaitMs ?: JSONObject.NULL)
            .put("criticalMaxAdmissionWaitMs", metrics.criticalMaxAdmissionWaitNanos.toDouble() / 1_000_000.0)
            .put("readOnlySchedulerDelaySamples", metrics.readOnlySchedulerDelaySamples)
            .put("readOnlyAverageSchedulerDelayUpperBoundMs", metrics.readOnlyAverageSchedulerDelayUpperBoundMs ?: JSONObject.NULL)
            .put("readOnlyMaxSchedulerDelayUpperBoundMs", metrics.readOnlyMaxSchedulerDelayUpperBoundNanos.toDouble() / 1_000_000.0)
            .put("criticalSchedulerDelaySamples", metrics.criticalSchedulerDelaySamples)
            .put("criticalAverageSchedulerDelayUpperBoundMs", metrics.criticalAverageSchedulerDelayUpperBoundMs ?: JSONObject.NULL)
            .put("criticalMaxSchedulerDelayUpperBoundMs", metrics.criticalMaxSchedulerDelayUpperBoundNanos.toDouble() / 1_000_000.0)
            .put("schedulerDelaySemantics", "UPPER_BOUND_QUEUE_PLUS_ENGINE_OVERHEAD")
            .put("learningMutation", LearningMutationAuthority.current().toJson())
    }

    private fun recordSchedulerDelayUpperBound(
        workClass: Mp48WorkClass,
        delegateElapsedNanos: Long,
        serialElapsedMs: Long,
    ) {
        val critical = workClass == Mp48WorkClass.MANUAL_WRITE || workClass == Mp48WorkClass.SAFETY
        val samples = if (critical) criticalSchedulerDelaySamples else readOnlySchedulerDelaySamples
        val total = if (critical) criticalSchedulerDelayUpperBoundNanos else readOnlySchedulerDelayUpperBoundNanos
        val maximum = if (critical) criticalMaxSchedulerDelayUpperBoundNanos else readOnlyMaxSchedulerDelayUpperBoundNanos
        val serialNanos = serialElapsedMs.coerceAtLeast(0L) * 1_000_000L
        val upperBoundNanos = (delegateElapsedNanos - serialNanos).coerceAtLeast(0L)
        samples.incrementAndGet()
        total.addAndGet(upperBoundNanos)
        maximum.updateAndGet { previous -> maxOf(previous, upperBoundNanos) }
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
        val admissionSamples = if (critical) criticalAdmissionSamples else readOnlyAdmissionSamples
        val admissionWaitNanos = if (critical) criticalAdmissionWaitNanos else readOnlyAdmissionWaitNanos
        val maxAdmissionWaitNanos = if (critical) criticalMaxAdmissionWaitNanos else readOnlyMaxAdmissionWaitNanos
        val admissionStartedNs = System.nanoTime()
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
        val admissionElapsedNs = (System.nanoTime() - admissionStartedNs).coerceAtLeast(0L)
        admissionSamples.incrementAndGet()
        admissionWaitNanos.addAndGet(admissionElapsedNs)
        maxAdmissionWaitNanos.updateAndGet { previous -> maxOf(previous, admissionElapsedNs) }
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
