package com.omegas.prohub.util

import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Entrega de estado vivo com backlog máximo de um quadro pendente.
 *
 * O quadro em processamento termina normalmente. Se novos quadros chegarem
 * enquanto o consumidor está ocupado, somente o mais recente permanece
 * pendente. Estado visual antigo nunca vira histórico em RAM.
 */
class LatestOnlyBackgroundPipeline(
    threadName: String,
    threadPriority: Int = Thread.NORM_PRIORITY,
    private val consumerName: String = threadName,
    private val onFailure: (sequence: Long, error: Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    companion object {
        const val DEFAULT_RETAINED_TASK_BYTES = 512
    }

    private data class Task(
        val sequence: Long,
        val estimatedBytes: Int,
        val enqueuedAtNanos: Long,
        val work: () -> Unit,
    )

    private val monitor = Object()
    private val accepting = AtomicBoolean(true)
    private var pending: Task? = null
    private var active: Task? = null

    private val submitted = AtomicLong(0L)
    private val executed = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    private val coalesced = AtomicLong(0L)
    private val lastCompletedSequence = AtomicLong(0L)
    private val lastFailedSequence = AtomicLong(0L)
    private val lastCoalescedSequence = AtomicLong(0L)
    private val lastQueueDelayMs = AtomicLong(0L)
    private val maxQueueDelayMs = AtomicLong(0L)
    private val lastProcessingMs = AtomicLong(0L)
    private val maxProcessingMs = AtomicLong(0L)
    private val lastThreadCpuMs = AtomicLong(-1L)
    private val maxThreadCpuMs = AtomicLong(-1L)

    private val worker = Thread({ runLoop() }, threadName).apply {
        isDaemon = true
        priority = threadPriority.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
        start()
    }

    fun submit(
        sequence: Long,
        estimatedBytes: Int = DEFAULT_RETAINED_TASK_BYTES,
        work: () -> Unit,
    ): Boolean {
        submitted.incrementAndGet()
        synchronized(monitor) {
            if (!accepting.get()) return false
            val task = Task(sequence, estimatedBytes.coerceAtLeast(0), System.nanoTime(), work)
            pending?.let {
                coalesced.incrementAndGet()
                lastCoalescedSequence.set(it.sequence)
            }
            pending = task
            monitor.notifyAll()
            return true
        }
    }

    /** Aguarda somente o quadro ativo e o último pendente; não existe fila histórica. */
    fun flush(timeoutMs: Long = 2_000L): Boolean {
        if (Thread.currentThread() === worker) return true
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
        synchronized(monitor) {
            while (pending != null || active != null) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                try {
                    monitor.wait(TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return true
        }
    }

    fun metricsJson(): JSONObject = synchronized(monitor) {
        JSONObject()
            .put("consumer", consumerName)
            .put("trigger", "EVENT_DRIVEN_ACQUIRED_FRAME")
            .put("cadence", "EVENT_DRIVEN_NO_TIMER")
            .put("mode", "LATEST_ONLY_LIVE_STATE")
            .put("queueBound", 1)
            .put("overloadPolicy", "COALESCE_PENDING_TO_LATEST")
            .put("dropAffectsAcquisition", false)
            .put("pendingBytesKind", "DECLARED_ESTIMATE_NOT_HEAP_MEASUREMENT")
            .put("defaultRetainedTaskBytes", DEFAULT_RETAINED_TASK_BYTES)
            .put("cpuAccounting", "ANDROID_THREAD_CPU_TIME_WHEN_AVAILABLE")
            .put("accepting", accepting.get())
            .put("submitted", submitted.get())
            .put("executed", executed.get())
            .put("pending", if (pending == null) 0 else 1)
            .put("active", if (active == null) 0 else 1)
            .put("pendingEstimatedBytes", pending?.estimatedBytes ?: 0)
            .put("activeEstimatedBytes", active?.estimatedBytes ?: 0)
            .put("coalesced", coalesced.get())
            .put("failed", failed.get())
            .put("lastCompletedSequence", lastCompletedSequence.get())
            .put("lastFailedSequence", lastFailedSequence.get())
            .put("lastCoalescedSequence", lastCoalescedSequence.get())
            .put("lastQueueDelayMs", lastQueueDelayMs.get())
            .put("maxQueueDelayMs", maxQueueDelayMs.get())
            .put("lastProcessingMs", lastProcessingMs.get())
            .put("maxProcessingMs", maxProcessingMs.get())
            .put("lastThreadCpuMs", lastThreadCpuMs.get())
            .put("maxThreadCpuMs", maxThreadCpuMs.get())
    }

    override fun close() {
        if (!accepting.compareAndSet(true, false)) return
        synchronized(monitor) { monitor.notifyAll() }
        try {
            worker.join(2_000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (worker.isAlive) worker.interrupt()
        synchronized(monitor) {
            pending = null
            active = null
            monitor.notifyAll()
        }
    }

    private fun runLoop() {
        while (accepting.get() || hasPending()) {
            val task = synchronized(monitor) {
                while (accepting.get() && pending == null) {
                    try {
                        monitor.wait(250L)
                    } catch (_: InterruptedException) {
                        if (!accepting.get()) return
                    }
                }
                val next = pending
                pending = null
                active = next
                next
            } ?: continue

            val startedAt = System.nanoTime()
            val cpuStartedAt = ThreadCpuClock.nowNanos()
            val queueDelayMs = nanosToMillis(startedAt - task.enqueuedAtNanos)
            lastQueueDelayMs.set(queueDelayMs)
            updateMaximum(maxQueueDelayMs, queueDelayMs)
            try {
                task.work()
                executed.incrementAndGet()
                lastCompletedSequence.set(task.sequence)
            } catch (error: Throwable) {
                failed.incrementAndGet()
                lastFailedSequence.set(task.sequence)
                onFailure(task.sequence, error)
            } finally {
                val processingMs = nanosToMillis(System.nanoTime() - startedAt)
                lastProcessingMs.set(processingMs)
                updateMaximum(maxProcessingMs, processingMs)
                val cpuMs = ThreadCpuClock.elapsedMillis(cpuStartedAt, ThreadCpuClock.nowNanos())
                if (cpuMs >= 0L) {
                    lastThreadCpuMs.set(cpuMs)
                    updateMaximum(maxThreadCpuMs, cpuMs)
                }
                synchronized(monitor) {
                    active = null
                    monitor.notifyAll()
                }
            }
        }
    }

    private fun hasPending(): Boolean = synchronized(monitor) { pending != null || active != null }

    private fun nanosToMillis(value: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(value.coerceAtLeast(0L))

    private fun updateMaximum(target: AtomicLong, candidate: Long) {
        var current = target.get()
        while (candidate > current && !target.compareAndSet(current, candidate)) {
            current = target.get()
        }
    }
}
