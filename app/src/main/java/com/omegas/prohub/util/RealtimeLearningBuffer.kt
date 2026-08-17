package com.omegas.prohub.util

import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Buffer quente do aprendizado para aparelhos lentos.
 *
 * O analisador produz janelas sobrepostas a cada quadro. Guardar milhares dessas
 * janelas na RAM não aumenta a verdade física: aumenta apenas a idade do cálculo.
 * Por isso:
 * - evidências usam uma fila minúscula e, sob saturação, a evidência pendente mais
 *   antiga é substituída pela mais nova;
 * - observações transitórias mantêm somente o estado mais recente;
 * - a sessão gravada continua sendo o backlog frio/durável integral para auditoria.
 *
 * O worker nunca altera critérios de RPM/MAP/temperatura. Ele só controla quanto
 * trabalho redundante pode ficar esperando na RAM.
 */
class RealtimeLearningBuffer(
    threadName: String,
    importantCapacity: Int = 3,
    threadPriority: Int = Thread.NORM_PRIORITY - 1,
    private val onFailure: (sequence: Long, error: Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    companion object {
        /** Limite duro: o backlog quente deve representar segundos, nunca minutos. */
        const val MAX_HOT_EVIDENCE = 3
    }

    private data class Task(
        val generation: Long,
        val sequence: Long,
        val important: Boolean,
        val enqueuedAtNanos: Long,
        val work: () -> Unit,
    )

    private val capacityImportant = importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)
    private val monitor = Object()
    private val accepting = AtomicBoolean(true)
    private val importantQueue = ArrayDeque<Task>()
    private var latestTransient: Task? = null
    private var active: Task? = null
    private var activeGeneration = 0L
    private var currentGeneration = 0L
    private var importantSinceTransient = 0

    private val submittedFrames = AtomicLong(0L)
    private val acceptedImportant = AtomicLong(0L)
    private val acceptedTransient = AtomicLong(0L)
    private val executed = AtomicLong(0L)
    private val executedImportant = AtomicLong(0L)
    private val executedTransient = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    private val coalescedTransient = AtomicLong(0L)
    private val supersededImportant = AtomicLong(0L)
    private val rejectedStale = AtomicLong(0L)
    private val purgedImportant = AtomicLong(0L)
    private val purgedTransient = AtomicLong(0L)
    private val lastQueueDelayMs = AtomicLong(0L)
    private val maxQueueDelayMs = AtomicLong(0L)
    private val lastImportantQueueDelayMs = AtomicLong(0L)
    private val maxImportantQueueDelayMs = AtomicLong(0L)
    private val lastProcessingMs = AtomicLong(0L)
    private val maxProcessingMs = AtomicLong(0L)
    private val lastImportantProcessingMs = AtomicLong(0L)
    private val maxImportantProcessingMs = AtomicLong(0L)
    private val lastCompletedSequence = AtomicLong(0L)

    private val worker = Thread({ runLoop() }, threadName).apply {
        isDaemon = true
        priority = threadPriority.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
        start()
    }

    /** Troca a geração ativa e elimina trabalho pendente de qualquer sessão anterior. */
    fun beginGeneration(generation: Long) {
        synchronized(monitor) {
            currentGeneration = generation
            purgeQueuedLocked()
            importantSinceTransient = 0
            monitor.notifyAll()
        }
    }

    /**
     * Tenta drenar apenas o pequeno buffer quente. Ao fim, invalida a geração e
     * remove o que restou; a sessão gravada permanece como evidência durável.
     */
    fun endGeneration(generation: Long, drainMs: Long = 750L) {
        flush(drainMs)
        synchronized(monitor) {
            if (currentGeneration == generation) currentGeneration = 0L
            purgeQueuedLocked()
            importantSinceTransient = 0
            monitor.notifyAll()
        }
    }

    fun submit(
        generation: Long,
        sequence: Long,
        important: Boolean,
        work: () -> Unit,
    ): Boolean {
        submittedFrames.incrementAndGet()
        synchronized(monitor) {
            if (!accepting.get() || generation <= 0L || generation != currentGeneration) {
                rejectedStale.incrementAndGet()
                return false
            }
            val task = Task(
                generation = generation,
                sequence = sequence,
                important = important,
                enqueuedAtNanos = System.nanoTime(),
                work = work,
            )
            if (important) {
                if (importantQueue.size >= capacityImportant) {
                    importantQueue.removeFirst()
                    supersededImportant.incrementAndGet()
                }
                importantQueue.addLast(task)
                acceptedImportant.incrementAndGet()
            } else {
                if (latestTransient != null) coalescedTransient.incrementAndGet()
                latestTransient = task
                acceptedTransient.incrementAndGet()
            }
            monitor.notifyAll()
            return true
        }
    }

    /** Aguarda o buffer atual ficar vazio, sem criar uma fila adicional. */
    fun flush(timeoutMs: Long = 2_000L): Boolean {
        if (Thread.currentThread() === worker) return true
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
        synchronized(monitor) {
            while (importantQueue.isNotEmpty() || latestTransient != null || active != null) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                val waitMs = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L)
                try {
                    monitor.wait(waitMs)
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
            .put("mode", "HOT_RECENT_BOUNDED_SESSION_DURABLE")
            .put("durableBacklog", "SESSION_RECORDER")
            .put("overloadPolicy", "SUPERSEDE_OLDEST_OVERLAPPING_PENDING_EVIDENCE")
            .put("accepting", accepting.get())
            .put("generation", currentGeneration)
            .put("capacityImportant", capacityImportant)
            .put("pendingImportant", importantQueue.size)
            .put("pendingTransient", if (latestTransient == null) 0 else 1)
            .put("active", if (active == null) 0 else 1)
            .put("activeGeneration", activeGeneration)
            .put("pending", importantQueue.size + (if (latestTransient == null) 0 else 1) + (if (active == null) 0 else 1))
            .put("submittedFrames", submittedFrames.get())
            .put("acceptedImportant", acceptedImportant.get())
            .put("acceptedTransient", acceptedTransient.get())
            .put("executed", executed.get())
            .put("executedImportant", executedImportant.get())
            .put("executedTransient", executedTransient.get())
            .put("coalescedTransient", coalescedTransient.get())
            .put("supersededImportant", supersededImportant.get())
            .put("rejectedStale", rejectedStale.get())
            .put("purgedImportant", purgedImportant.get())
            .put("purgedTransient", purgedTransient.get())
            .put("failed", failed.get())
            .put("lastCompletedSequence", lastCompletedSequence.get())
            .put("lastQueueDelayMs", lastQueueDelayMs.get())
            .put("maxQueueDelayMs", maxQueueDelayMs.get())
            .put("lastImportantQueueDelayMs", lastImportantQueueDelayMs.get())
            .put("maxImportantQueueDelayMs", maxImportantQueueDelayMs.get())
            .put("lastProcessingMs", lastProcessingMs.get())
            .put("maxProcessingMs", maxProcessingMs.get())
            .put("lastImportantProcessingMs", lastImportantProcessingMs.get())
            .put("maxImportantProcessingMs", maxImportantProcessingMs.get())
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
            purgeQueuedLocked()
            monitor.notifyAll()
        }
    }

    private fun runLoop() {
        while (accepting.get() || hasPending()) {
            val task = synchronized(monitor) {
                while (accepting.get() && importantQueue.isEmpty() && latestTransient == null) {
                    try {
                        monitor.wait(250L)
                    } catch (_: InterruptedException) {
                        if (!accepting.get()) return
                    }
                }
                val next = chooseNextLocked()
                active = next
                activeGeneration = next?.generation ?: 0L
                next
            } ?: continue

            val startedAt = System.nanoTime()
            val queueDelayMs = nanosToMillis(startedAt - task.enqueuedAtNanos)
            lastQueueDelayMs.set(queueDelayMs)
            updateMaximum(maxQueueDelayMs, queueDelayMs)
            if (task.important) {
                lastImportantQueueDelayMs.set(queueDelayMs)
                updateMaximum(maxImportantQueueDelayMs, queueDelayMs)
            }
            try {
                val generationStillCurrent = synchronized(monitor) { task.generation == currentGeneration }
                if (generationStillCurrent) {
                    task.work()
                    executed.incrementAndGet()
                    if (task.important) executedImportant.incrementAndGet() else executedTransient.incrementAndGet()
                    lastCompletedSequence.set(task.sequence)
                } else {
                    if (task.important) purgedImportant.incrementAndGet() else purgedTransient.incrementAndGet()
                }
            } catch (error: Throwable) {
                failed.incrementAndGet()
                onFailure(task.sequence, error)
            } finally {
                val processingMs = nanosToMillis(System.nanoTime() - startedAt)
                lastProcessingMs.set(processingMs)
                updateMaximum(maxProcessingMs, processingMs)
                if (task.important) {
                    lastImportantProcessingMs.set(processingMs)
                    updateMaximum(maxImportantProcessingMs, processingMs)
                }
                synchronized(monitor) {
                    active = null
                    activeGeneration = 0L
                    monitor.notifyAll()
                }
            }
        }
    }

    private fun chooseNextLocked(): Task? {
        val transient = latestTransient
        if (transient != null && (importantQueue.isEmpty() || importantSinceTransient >= 2)) {
            latestTransient = null
            importantSinceTransient = 0
            return transient
        }
        if (importantQueue.isNotEmpty()) {
            importantSinceTransient += 1
            return importantQueue.removeFirst()
        }
        if (transient != null) {
            latestTransient = null
            importantSinceTransient = 0
            return transient
        }
        return null
    }

    private fun purgeQueuedLocked() {
        if (importantQueue.isNotEmpty()) {
            purgedImportant.addAndGet(importantQueue.size.toLong())
            importantQueue.clear()
        }
        if (latestTransient != null) {
            purgedTransient.incrementAndGet()
            latestTransient = null
        }
    }

    private fun hasPending(): Boolean = synchronized(monitor) {
        importantQueue.isNotEmpty() || latestTransient != null || active != null
    }

    private fun nanosToMillis(value: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(value.coerceAtLeast(0L))

    private fun updateMaximum(target: AtomicLong, candidate: Long) {
        var current = target.get()
        while (candidate > current && !target.compareAndSet(current, candidate)) {
            current = target.get()
        }
    }
}
