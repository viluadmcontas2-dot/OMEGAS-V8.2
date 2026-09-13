package com.omegas.prohub.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Latest-only async persistence with serialized physical writes. */
class CoalescedSnapshotWriter(
    private val file: File,
    private val snapshotProvider: () -> String,
    private val onFailure: (Throwable) -> Unit = {},
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-coalesced-snapshot").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val dirty = AtomicBoolean(false)
    private val scheduled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    fun request() {
        if (closed.get()) return
        dirty.set(true)
        scheduleIfNeeded()
    }

    fun flush() {
        if (closed.get()) return
        dirty.set(false)
        writeLatest()
    }

    private fun scheduleIfNeeded() {
        if (closed.get() || !scheduled.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (!closed.get() && dirty.getAndSet(false)) writeLatest()
            } finally {
                scheduled.set(false)
                if (!closed.get() && dirty.get()) scheduleIfNeeded()
            }
        }
    }

    private fun writeLatest() = synchronized(writeLock) {
        try {
            val serialized = snapshotProvider()
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile ?: file.absoluteFile.parentFile, file.name + ".tmp")
            temp.writeText(serialized, Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Throwable) {
            onFailure(error)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (dirty.getAndSet(false)) writeLatest()
        executor.shutdown()
    }
}
