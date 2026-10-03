package com.omegas.prohub.util

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class RingLog(
    private val maxEntries: Int = 1000,
    private val persistentFile: File? = null,
) {
    private val items = ArrayDeque<JSONObject>()
    private val clockFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val lineFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var listener: ((JSONObject) -> Unit)? = null

    // Escrita em arquivo fora do bloqueio de `add`: as linhas entram numa fila e uma thread própria
    // grava em lote (abre o arquivo uma vez por lote, não uma vez por linha) e gira o arquivo.
    private val pendingLines = ConcurrentLinkedQueue<String>()
    private val drainScheduled = AtomicBoolean(false)
    private val fileLock = Any()
    private val fileWriter = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-ring-log-file").apply { isDaemon = true }
    }

    init {
        persistentFile?.parentFile?.mkdirs()
        if (persistentFile != null && persistentFile.exists()) {
            persistentFile.readLines(Charsets.UTF_8).takeLast(maxEntries / 2).forEach { line ->
                val parts = line.split('\t', limit = 4)
                if (parts.size == 4) {
                    items.addLast(
                        JSONObject()
                            .put("time", parts[0].takeLast(12))
                            .put("level", parts[1])
                            .put("category", parts[2])
                            .put("message", parts[3]),
                    )
                }
            }
        }
    }

    fun setListener(value: ((JSONObject) -> Unit)?) {
        listener = value
    }

    @Synchronized
    fun add(level: String, category: String, message: String) {
        val now = Date()
        val clean = message.replace('\u0000', ' ').trimEnd().take(4000)
        val item = JSONObject()
            .put("time", clockFormatter.format(now))
            .put("level", level.uppercase())
            .put("category", category.uppercase())
            .put("message", clean)
        items.addLast(item)
        while (items.size > maxEntries) items.removeFirst()
        try { listener?.invoke(JSONObject(item.toString())) } catch (_: Exception) {}
        if (persistentFile != null) {
            pendingLines.add(
                "${lineFormatter.format(now)}\t${level.uppercase()}\t${category.uppercase()}\t${clean.replace('\n', ' ')}\n",
            )
            scheduleDrain()
        }
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return
        try {
            fileWriter.execute {
                drainScheduled.set(false)
                drainToFile()
            }
        } catch (_: RejectedExecutionException) {
            drainScheduled.set(false)
        }
    }

    /** Grava tudo que está na fila (chamável de qualquer thread; o arquivo só é tocado sob `fileLock`). */
    private fun drainToFile() {
        val file = persistentFile ?: return
        synchronized(fileLock) {
            if (pendingLines.isEmpty()) return
            val batch = StringBuilder()
            while (true) batch.append(pendingLines.poll() ?: break)
            try {
                file.appendText(batch.toString(), Charsets.UTF_8)
                rotateIfNeeded(file)
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun json(limit: Int = 400): String {
        val arr = JSONArray()
        items.takeLast(limit.coerceAtMost(maxEntries)).forEach(arr::put)
        return arr.toString()
    }

    fun clear() {
        synchronized(this) { items.clear() }
        synchronized(fileLock) {
            pendingLines.clear()
            try { persistentFile?.writeText("") } catch (_: Exception) {}
        }
    }

    fun text(): String {
        drainToFile()
        val fromFile = synchronized(fileLock) {
            persistentFile?.takeIf { it.exists() }?.let { file ->
                try { file.readText(Charsets.UTF_8) } catch (_: Exception) { null }
            }
        }
        if (fromFile != null) return fromFile
        return synchronized(this) {
            items.joinToString("\n") {
                "${it.optString("time")}\t${it.optString("level")}\t${it.optString("category")}\t${it.optString("message")}"
            }
        }
    }

    /** Gira o arquivo acima de 2 MB com um rename (sem copiar o conteúdo) e fora do bloqueio de `add`. */
    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= 2_000_000) return
        val old = File(file.parentFile, file.nameWithoutExtension + ".1.log")
        old.delete()
        if (!file.renameTo(old)) {
            file.copyTo(old, overwrite = true)
            file.writeText("")
        }
    }
}

