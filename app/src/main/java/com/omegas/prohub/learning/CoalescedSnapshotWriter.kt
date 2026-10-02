package com.omegas.prohub.learning

import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistidor assíncrono para arquivos que representam apenas o estado mais recente.
 *
 * Vários pedidos podem chegar enquanto o armazenamento ainda grava o anterior. Nesse
 * caso, estados intermediários são coalescidos e somente a fotografia mais nova precisa
 * chegar ao disco. Isto NÃO deve ser usado para logs/eventos que precisem preservar cada
 * entrada individual.
 */
internal class CoalescedSnapshotWriter(
    private val target: File,
    threadName: String,
    private val beforeWrite: () -> Unit = {},
) : AutoCloseable {
    private val accepting = AtomicBoolean(true)
    private val scheduled = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)
    private val requests = AtomicLong(0L)
    private val writes = AtomicLong(0L)
    private val failures = AtomicLong(0L)

    @Volatile private var latestPayloadProvider: (() -> String)? = null
    @Volatile private var lastError = ""

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply { isDaemon = true }
    }

    fun submit(payload: String): Boolean {
        return request { payload }
    }

    /**
     * Coalesce antes de construir o payload caro. O provider mais recente só é
     * executado na thread de persistência quando ela realmente for gravar.
     */
    fun request(payloadProvider: () -> String): Boolean {
        if (!accepting.get()) return false
        latestPayloadProvider = payloadProvider
        requests.incrementAndGet()
        dirty.set(true)
        scheduleDrain()
        return true
    }

    private fun scheduleDrain() {
        if (!scheduled.compareAndSet(false, true)) return
        try {
            executor.execute { drain() }
        } catch (_: RejectedExecutionException) {
            scheduled.set(false)
        }
    }

    private fun drain() {
        try {
            while (dirty.getAndSet(false)) {
                try {
                    val payload = latestPayloadProvider?.invoke() ?: continue
                    beforeWrite()
                    writeAtomically(payload)
                    writes.incrementAndGet()
                    lastError = ""
                } catch (error: Throwable) {
                    failures.incrementAndGet()
                    lastError = error.message ?: error.javaClass.simpleName
                }
            }
        } finally {
            scheduled.set(false)
            if (dirty.get()) scheduleDrain()
        }
    }

    fun flush(timeoutMs: Long = 5_000L): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
        while (dirty.get() || scheduled.get()) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) return false
            val barrier = CompletableFuture<Boolean>()
            try {
                executor.execute { barrier.complete(true) }
                barrier.get(remaining, TimeUnit.NANOSECONDS)
            } catch (_: Exception) {
                return false
            }
        }
        return true
    }

    fun metricsJson(): JSONObject {
        val requested = requests.get()
        val written = writes.get()
        return JSONObject()
            .put("requests", requested)
            .put("writes", written)
            .put("coalesced", (requested - written).coerceAtLeast(0L))
            .put("failures", failures.get())
            .put("dirty", dirty.get())
            .put("scheduled", scheduled.get())
            .put("lastError", lastError)
    }

    override fun close() {
        if (!accepting.compareAndSet(true, false)) return
        if (dirty.get()) scheduleDrain()
        flush(10_000L)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) executor.shutdownNow()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            executor.shutdownNow()
        }
    }

    private fun writeAtomically(payload: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(payload, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(payload, Charsets.UTF_8)
            temporary.delete()
        }
    }
}
