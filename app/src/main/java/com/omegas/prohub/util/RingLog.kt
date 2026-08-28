package com.omegas.prohub.util

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingLog(
    private val maxEntries: Int = 1000,
    private val persistentFile: File? = null,
) {
    private val items = ArrayDeque<JSONObject>()
    private val clockFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val lineFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var listener: ((JSONObject) -> Unit)? = null

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
        try {
            persistentFile?.appendText(
                "${lineFormatter.format(now)}\t${level.uppercase()}\t${category.uppercase()}\t${clean.replace('\n', ' ')}\n",
                Charsets.UTF_8,
            )
            rotateIfNeeded()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun json(limit: Int = 400): String {
        val arr = JSONArray()
        items.takeLast(limit.coerceAtMost(maxEntries)).forEach(arr::put)
        return arr.toString()
    }

    @Synchronized
    fun clear() {
        items.clear()
        try { persistentFile?.writeText("") } catch (_: Exception) {}
    }

    @Synchronized
    fun text(): String = persistentFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
        ?: items.joinToString("\n") {
            "${it.optString("time")}\t${it.optString("level")}\t${it.optString("category")}\t${it.optString("message")}"
        }

    private fun rotateIfNeeded() {
        val file = persistentFile ?: return
        if (!file.exists() || file.length() <= 2_000_000) return
        val old = File(file.parentFile, file.nameWithoutExtension + ".1.log")
        old.delete()
        file.copyTo(old, overwrite = true)
        file.writeText("")
    }
}

