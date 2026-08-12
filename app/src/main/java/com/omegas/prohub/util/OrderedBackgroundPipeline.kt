package com.omegas.prohub.util

import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Fila assíncrona ordenada para retirar trabalho secundário do caminho crítico.
 *
 * A chamada [submit] apenas enfileira e retorna. Um único worker preserva a ordem
 * de chegada. [flush] cria uma barreira para checkpoints, troca de sessão e
 * encerramento, sem descartar tarefas anteriores.
 */
class OrderedBackgroundPipeline(
    threadName: String,
    threadPriority: Int = Thread.NORM_PRIORITY,
    private val onFailure: (sequence: Long, error: Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val accepting = AtomicBoolean(true)
    private val submitted = AtomicLong(0L)
    private val completed = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    private val lastCompletedSequence = AtomicLong(0L)
    private val lastFailedSequence = AtomicLong(0L)
    private val lastQueueDelayMs = AtomicLong(0L)
    private val maxQueueDelayMs = AtomicLong(0L)
    private val lastProcessingMs = AtomicLong(0L)
    private val maxProcessingMs = AtomicLong(0L)

    @Volatile private var workerThread: Thread? = null

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply {
            isDaemon = true
            priority = threadPriority.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
            workerThread = this
        }
    }

    fun submit(sequence: Long, work: () -> Unit): Boolean {
        if (!accepting.get()) return false
        val enqueuedAtNanos = System.nanoTime()
        submitted.incrementAndGet()
        return try {
            executor.execute {
                val startedAtNanos = System.nanoTime()
                val queueDelayMs = nanosToMillis(startedAtNanos - enqueuedAtNanos)
                lastQueueDelayMs.set(queueDelayMs)
                updateMaximum(maxQueueDelayMs, queueDelayMs)
                try {
                    work()
                    lastCompletedSequence.set(sequence)
                } catch (error: Throwable) {
                    failed.incrementAndGet()
                    lastFailedSequence.set(sequence)
                    onFailure(sequence, error)
                } finally {
                    val processingMs = nanosToMillis(System.nanoTime() - startedAtNanos)
                    lastProcessingMs.set(processingMs)
                    updateMaximum(maxProcessingMs, processingMs)
                    completed.incrementAndGet()
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            submitted.decrementAndGet()
            false
        }
    }

    /** Aguarda todas as tarefas aceitas antes da barreira. */
    fun flush(timeoutMs: Long = 5_000L): Boolean {
        if (Thread.currentThread() === workerThread) return true
        val barrier = CompletableFuture<Boolean>()
        return try {
            executor.execute { barrier.complete(true) }
            barrier.get(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        }
    }

    fun metricsJson(): JSONObject = JSONObject()
        .put("accepting", accepting.get())
        .put("submitted", submitted.get())
        .put("completed", completed.get())
        .put("pending", (submitted.get() - completed.get()).coerceAtLeast(0L))
        .put("failed", failed.get())
        .put("lastCompletedSequence", lastCompletedSequence.get())
        .put("lastFailedSequence", lastFailedSequence.get())
        .put("lastQueueDelayMs", lastQueueDelayMs.get())
        .put("maxQueueDelayMs", maxQueueDelayMs.get())
        .put("lastProcessingMs", lastProcessingMs.get())
        .put("maxProcessingMs", maxProcessingMs.get())

    override fun close() {
        if (!accepting.compareAndSet(true, false)) return
        flush(10_000L)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) executor.shutdownNow()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            executor.shutdownNow()
        }
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
