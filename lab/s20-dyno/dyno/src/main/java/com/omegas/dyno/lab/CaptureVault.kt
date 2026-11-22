package com.omegas.dyno.lab

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

internal class CaptureVault(private val context: Context, private val hub: TelemetryHub) {
    private val db = VaultStore(context)
    private val exportWorker = Executors.newSingleThreadExecutor()
    private val sampleSlots = Semaphore(1024)
    private var closed = false
    private var accepting = false
    private var acceptFromNs = 0L
    private var overflow = false
    private var exporting = false
    private var session: Long? = null
    @Volatile var listing: String = "[]"
        private set

    init {
        worker.execute { guarded {
            db.recoverInterrupted()
            refresh()
        } }
    }

    @Synchronized fun start(): Boolean {
        if (closed || accepting) return false
        accepting = true
        overflow = false
        val stamp = receiptStamp()
        acceptFromNs = stamp.monoNs
        hub.vaultError(null)
        worker.execute {
            try {
                if (session != null) finish(stamp)
                session = db.start(stamp)
            } catch (exception: Exception) {
                synchronized(this) {
                    if (acceptFromNs == stamp.monoNs) accepting = false
                }
                report(exception)
                return@execute
            }
            hub.capture(true)
            guarded { refresh() }
        }
        return true
    }

    @Synchronized fun stop(): Boolean {
        if (closed || !accepting) return false
        accepting = false
        val stamp = receiptStamp()
        worker.execute { guarded { finish(stamp) } }
        return true
    }

    @Synchronized fun sample(observation: Observation) {
        if (closed || !accepting || observation.stamp.monoNs < acceptFromNs) return
        if (!sampleSlots.tryAcquire()) {
            if (!overflow) {
                overflow = true
                hub.vaultError("Armazenamento ocupado. Leituras da sessão foram perdidas.")
            }
            return
        }
        worker.execute {
            try {
                guarded { session?.let { db.observe(it, observation) } }
            } finally {
                sampleSlots.release()
            }
        }
    }

    @Synchronized fun export(uri: Uri) {
        if (closed) return
        if (exporting) {
            hub.vaultError("Uma exportação já está em andamento")
            return
        }
        exporting = true
        worker.execute {
            var temp: File? = null
            try {
                val archive = File.createTempFile("dyno-export-", ".zip", context.cacheDir)
                temp = archive
                db.exportZip(archive.outputStream())
                exportWorker.execute {
                    try {
                        guarded {
                            val stream = context.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("Arquivo de exportação indisponível")
                            stream.use { output -> archive.inputStream().use { it.copyTo(output) } }
                        }
                    } finally {
                        archive.delete()
                        synchronized(this) { exporting = false }
                    }
                }
            } catch (exception: Exception) {
                temp?.delete()
                synchronized(this) { exporting = false }
                report(exception)
            }
        }
    }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        val stamp = receiptStamp()
        worker.execute {
            guarded { finish(stamp) }
            db.close()
            exportWorker.shutdown()
        }
    }

    private fun finish(stamp: Stamp) {
        session?.let { db.stop(it, stamp) }
        session = null
        hub.capture(false)
        refresh()
    }

    private fun refresh() { listing = db.sessions().toString() }
    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (exception: Exception) {
            report(exception)
        }
    }

    private companion object {
        // Serialize database work across successive Activity instances.
        val worker = Executors.newSingleThreadExecutor()
    }

    private fun report(exception: Exception) {
        hub.vaultError(exception.message ?: "Falha no armazenamento do dinamômetro")
    }
}
