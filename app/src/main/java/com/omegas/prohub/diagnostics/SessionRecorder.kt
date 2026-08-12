package com.omegas.prohub.diagnostics

import android.content.ContentResolver
import android.net.Uri
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gravador estruturado de sessões para diagnóstico e análise independente.
 *
 * Cada linha é um JSON completo. A telemetria nativa é registrada uma única vez.
 * Ao exportar uma sessão ativa, o segmento corrente é fechado e um novo segmento
 * recebe os eventos seguintes; o ZIP usa somente arquivos imutáveis.
 */
class SessionRecorder(
    private val paths: AppPaths,
    private val settings: AppSettings,
) {
    companion object {
        private const val FORMAT = "omegas-session-log-v1"
        private const val SEGMENT_LIMIT_BYTES = 64L * 1024L * 1024L
        private const val PREVIEW_LIMIT = 120
    }

    private val droppedEvents = AtomicLong(0L)
    private val worker = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(8192),
        { runnable -> Thread(runnable, "omegas-session-recorder").apply { isDaemon = true } },
        { _, _ -> droppedEvents.incrementAndGet() },
    )
    private val sequence = AtomicLong(0L)
    private val previewLock = Any()
    private val preview = ArrayDeque<JSONObject>()
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val fileStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    @Volatile private var recording = false
    @Volatile private var sessionId = ""
    @Volatile private var startedAt = 0L
    @Volatile private var stoppedAt = 0L
    @Volatile private var eventCount = 0L
    @Volatile private var byteCount = 0L
    @Volatile private var currentSegment = 0
    @Volatile private var lastTelemetryAt = 0L
    @Volatile private var lastSnapshotAt = 0L
    @Volatile private var stopReason = ""
    @Volatile private var lastError = ""

    private var sessionDir: File? = null
    private var writer: BufferedWriter? = null
    private var segmentFile: File? = null
    private var segmentBytes = 0L

    @Synchronized
    fun start(reason: String, metadata: JSONObject = JSONObject()): JSONObject {
        if (recording) return statusObject().put("ok", true).put("alreadyRecording", true)
        return try {
            pruneOldSessions()
            val now = System.currentTimeMillis()
            val id = "session_${fileStamp.format(Date(now))}_${settings.deviceId.take(8)}"
            val dir = File(paths.sessionLogsRoot, id).apply { mkdirs() }
            sessionId = id
            sessionDir = dir
            startedAt = now
            stoppedAt = 0L
            eventCount = 0L
            byteCount = 0L
            currentSegment = 0
            segmentBytes = 0L
            lastTelemetryAt = 0L
            lastSnapshotAt = 0L
            stopReason = ""
            lastError = ""
            sequence.set(0L)
            droppedEvents.set(0L)
            synchronized(previewLock) { preview.clear() }
            openNextSegment()
            recording = true
            writeManifestBase(dir, now, reason, metadata)
            File(dir, "README_PARA_IA.txt").writeText(aiReadme(), Charsets.UTF_8)
            recordNow(
                "session_started",
                "native",
                JSONObject().put("reason", reason).put("metadata", metadata),
            )
            statusObject().put("ok", true)
        } catch (error: Exception) {
            recording = false
            lastError = error.message ?: error.javaClass.simpleName
            JSONObject().put("ok", false).put("error", lastError)
        }
    }

    fun stop(reason: String = "manual"): JSONObject {
        awaitPendingWrites()
        return synchronized(this) {
            if (!recording) return@synchronized statusObject().put("ok", true).put("alreadyStopped", true)
            recordNow("session_stopped", "native", JSONObject().put("reason", reason))
            recording = false
            stoppedAt = System.currentTimeMillis()
            stopReason = reason
            closeWriter()
            updateManifest()
            statusObject().put("ok", true)
        }
    }

    fun record(type: String, source: String, data: JSONObject, force: Boolean = false) {
        if (!recording) return

        // O evento nativo completo contém a mesma telemetria já gravada como
        // `telemetry`. Estados não telemétricos continuam podendo ser registrados.
        if (type == "engine_event" && data.optString("event") == "telemetry") return

        val now = System.currentTimeMillis()
        if (!force && type == "telemetry") {
            val every = settings.sessionTelemetryEveryMs
            if (every > 0L && now - lastTelemetryAt < every) return
            lastTelemetryAt = now
        }
        if (!force && type == "full_snapshot") {
            val every = settings.sessionFullSnapshotEveryMs
            if (every <= 0L || now - lastSnapshotAt < every) return
            lastSnapshotAt = now
        }
        val copy = try {
            JSONObject(data.toString())
        } catch (_: Exception) {
            JSONObject().put("raw", data.toString())
        }
        worker.execute {
            synchronized(this) {
                if (!recording) return@synchronized
                recordNow(type, source, copy)
            }
        }
    }

    fun recordRawUsb(direction: String, bytes: ByteArray) {
        if (!recording || !settings.sessionCaptureRawUsb || bytes.isEmpty()) return
        val copy = bytes.copyOf()
        worker.execute {
            val hex = copy.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            synchronized(this) {
                if (!recording || !settings.sessionCaptureRawUsb) return@synchronized
                recordNow(
                    "usb_raw",
                    "usb",
                    JSONObject().put("direction", direction).put("size", copy.size).put("hex", hex),
                )
            }
        }
    }

    fun statusJson(): String = statusObject().toString()

    @Synchronized
    fun statusObject(): JSONObject {
        val end = if (recording) {
            System.currentTimeMillis()
        } else {
            stoppedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("recording", recording)
            .put("sessionId", sessionId)
            .put("startedAt", startedAt)
            .put("durationMs", if (startedAt > 0L) (end - startedAt).coerceAtLeast(0L) else 0L)
            .put("events", eventCount)
            .put("droppedEvents", droppedEvents.get())
            .put("bytes", byteCount)
            .put("megabytes", byteCount / (1024.0 * 1024.0))
            .put("limitMb", settings.sessionLogMaxMb)
            .put("segment", currentSegment)
            .put("stopReason", stopReason)
            .put("lastError", lastError)
            .put("directory", sessionDir?.absolutePath ?: "")
            .put(
                "settings",
                JSONObject()
                    .put("autoStartOnUsb", settings.sessionRecorderAutoStartOnUsb)
                    .put("telemetryEveryMs", settings.sessionTelemetryEveryMs)
                    .put("fullSnapshotEveryMs", settings.sessionFullSnapshotEveryMs)
                    .put("captureRawUsb", settings.sessionCaptureRawUsb)
                    .put("maxSessionMb", settings.sessionLogMaxMb)
                    .put("keepSessions", settings.sessionKeepCount),
            )
    }

    fun previewJson(): String = synchronized(previewLock) {
        JSONArray(preview.map { JSONObject(it.toString()) }).toString()
    }

    @Synchronized
    fun listSessionsJson(): String {
        val array = JSONArray()
        paths.sessionLogsRoot.listFiles { file -> file.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { dir ->
                val manifest = try {
                    JSONObject(File(dir, "manifest.json").readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    JSONObject()
                }
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val createdAt = manifest.optLong("createdAtMs", dir.lastModified())
                val active = dir.absolutePath == sessionDir?.absolutePath && recording
                val stoppedAt = manifest.optLong("stoppedAtMs", 0L)
                val durationEnd = if (active) System.currentTimeMillis() else stoppedAt.takeIf { it > 0L } ?: dir.lastModified()
                array.put(
                    JSONObject()
                        .put("id", dir.name)
                        .put("createdAt", createdAt)
                        .put("stoppedAt", stoppedAt)
                        .put("durationMs", (durationEnd - createdAt).coerceAtLeast(0L))
                        .put("reason", manifest.optString("reason", "Sessão"))
                        .put("bytes", size)
                        .put("active", active),
                )
            }
        return array.toString()
    }

    @Synchronized
    fun clearStoppedSessions(): JSONObject {
        var deleted = 0
        paths.sessionLogsRoot.listFiles { file -> file.isDirectory }?.forEach { dir ->
            if (recording && dir.absolutePath == sessionDir?.absolutePath) return@forEach
            if (dir.deleteRecursively()) deleted += 1
        }
        return JSONObject().put("ok", true).put("deleted", deleted)
    }

    fun exportSession(
        resolver: ContentResolver,
        uri: Uri,
        requestedSessionId: String = "",
    ): JSONObject {
        awaitPendingWrites()
        val root = paths.sessionLogsRoot.canonicalFile
        val requested = if (requestedSessionId.isBlank()) sessionDir else File(root, requestedSessionId).canonicalFile
        if (requested == null || !requested.isDirectory || requested.parentFile?.canonicalFile != root) {
            return JSONObject().put("ok", false).put("error", "Sessão não encontrada")
        }

        val snapshot = synchronized(this) {
            if (recording && sessionDir?.canonicalFile == requested) {
                createActiveExportSnapshot(requested)
            } else {
                createStoppedExportSnapshot(requested)
            }
        }

        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    val files = JSONArray()
                    snapshot.entries.forEach { entry ->
                        zip.putNextEntry(ZipEntry(entry.path))
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        if (entry.bytes != null) {
                            digest.update(entry.bytes)
                            zip.write(entry.bytes)
                            copied = entry.bytes.size.toLong()
                        } else {
                            entry.file?.inputStream()?.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    digest.update(buffer, 0, count)
                                    zip.write(buffer, 0, count)
                                    copied += count
                                }
                            }
                        }
                        zip.closeEntry()
                        files.put(
                            JSONObject()
                                .put("path", entry.path)
                                .put("bytes", copied)
                                .put("sha256", digest.digest().joinToString("") { "%02x".format(it) }),
                        )
                    }
                    val now = System.currentTimeMillis()
                    val summary = JSONObject()
                        .put("exportedAtMs", now)
                        .put("exportedAtUtc", iso(now))
                        .put("session", snapshot.sessionStatus)
                        .put("appSettings", settings.toJson())
                        .put("files", files)
                        .put("immutableBoundary", snapshot.immutableBoundary)
                        .put("integrity", "SHA-256 calculado sobre os bytes exatos incluídos neste ZIP")
                    zip.putNextEntry(ZipEntry("export_summary.json"))
                    zip.write(summary.toString(2).toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: return JSONObject().put("ok", false).put("error", "Destino de exportação indisponível")
            JSONObject()
                .put("ok", true)
                .put("sessionId", requested.name)
                .put("events", snapshot.sessionStatus.optLong("events", 0L))
                .put("immutableBoundary", snapshot.immutableBoundary)
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Falha ao exportar sessão")
        }
    }

    @Synchronized
    fun close() {
        if (recording) stop("serviço encerrado")
        worker.shutdownNow()
    }

    private fun createActiveExportSnapshot(dir: File): ExportSnapshot {
        recordNow(
            "export_boundary",
            "native",
            JSONObject().put("reason", "Corte imutável para exportação ativa"),
        )
        writer?.flush()
        writer?.close()
        writer = null

        val closedFiles = dir.walkTopDown()
            .filter { it.isFile && it.name != "manifest.json" }
            .toList()
        val manifestBytes = manifestObject(recordingOverride = true)
            .put("exportBoundarySequence", sequence.get())
            .put("exportBoundaryAtMs", System.currentTimeMillis())
            .toString(2)
            .toByteArray(Charsets.UTF_8)

        // Eventos novos seguem em outro arquivo; os arquivos acima não mudam mais.
        openNextSegment()
        updateManifest()

        val entries = mutableListOf(ExportEntry("manifest.json", bytes = manifestBytes))
        closedFiles.forEach { file ->
            entries += ExportEntry(file.relativeTo(dir).invariantSeparatorsPath, file = file)
        }
        return ExportSnapshot(entries, JSONObject(String(manifestBytes, Charsets.UTF_8)), true)
    }

    private fun createStoppedExportSnapshot(dir: File): ExportSnapshot {
        val entries = dir.walkTopDown()
            .filter { it.isFile }
            .map { file -> ExportEntry(file.relativeTo(dir).invariantSeparatorsPath, file = file) }
            .toList()
        val manifest = try {
            JSONObject(File(dir, "manifest.json").readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject().put("sessionId", dir.name)
        }
        return ExportSnapshot(entries, manifest, true)
    }

    private fun recordNow(type: String, source: String, data: JSONObject) {
        if (!recording && type != "session_stopped") return
        try {
            if (writer == null) openNextSegment()
            val now = System.currentTimeMillis()
            val item = JSONObject()
                .put("format", FORMAT)
                .put("sequence", sequence.incrementAndGet())
                .put("recordedAtMs", now)
                .put("recordedAtUtc", iso(now))
                .put("type", type)
                .put("source", source)
                .put("data", data)
            val line = item.toString() + "\n"
            val bytes = line.toByteArray(StandardCharsets.UTF_8).size.toLong()
            val maxBytes = settings.sessionLogMaxMb.toLong() * 1024L * 1024L
            if (byteCount + bytes > maxBytes && type != "session_stopped") {
                stopReason = "limite de ${settings.sessionLogMaxMb} MB atingido"
                lastError = stopReason
                recording = false
                stoppedAt = now
                closeWriter()
                updateManifest()
                return
            }
            if (segmentBytes + bytes > SEGMENT_LIMIT_BYTES) openNextSegment()
            writer?.write(line)
            val critical = type in setOf(
                "session_started",
                "session_stopped",
                "export_boundary",
                "settings_changed",
                "manual_marker",
                "k_read_map",
                "k_write_requested",
                "k_batch_confirmed",
            )
            if (eventCount % 32L == 0L || critical) writer?.flush()
            eventCount += 1L
            byteCount += bytes
            segmentBytes += bytes
            synchronized(previewLock) {
                preview.addLast(
                    JSONObject()
                        .put("sequence", item.optLong("sequence"))
                        .put("recordedAtMs", now)
                        .put("type", type)
                        .put("source", source)
                        .put("summary", summarize(type, data)),
                )
                while (preview.size > PREVIEW_LIMIT) preview.removeFirst()
            }
        } catch (error: Exception) {
            lastError = error.message ?: error.javaClass.simpleName
        }
    }

    private fun openNextSegment() {
        closeWriter()
        currentSegment += 1
        segmentBytes = 0L
        val dir = sessionDir ?: error("Sessão não inicializada")
        val file = File(dir, "events_${currentSegment.toString().padStart(4, '0')}.jsonl")
        segmentFile = file
        writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8),
            64 * 1024,
        )
    }

    private fun closeWriter() {
        try { writer?.flush() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        writer = null
    }

    private fun writeManifestBase(dir: File, now: Long, reason: String, metadata: JSONObject) {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", 2)
            .put("sessionId", sessionId)
            .put("createdAtMs", now)
            .put("createdAtUtc", iso(now))
            .put("reason", reason)
            .put("deviceId", settings.deviceId)
            .put("deviceName", settings.deviceName)
            .put(
                "capture",
                JSONObject()
                    .put("telemetryEveryMs", settings.sessionTelemetryEveryMs)
                    .put("fullSnapshotEveryMs", settings.sessionFullSnapshotEveryMs)
                    .put("rawUsb", settings.sessionCaptureRawUsb)
                    .put("deduplicatedNativeTelemetry", true)
                    .put("maxSessionMb", settings.sessionLogMaxMb),
            )
            .put("metadata", metadata)
        File(dir, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)
    }

    private fun manifestObject(recordingOverride: Boolean = recording): JSONObject {
        val dir = sessionDir
        val file = dir?.let { File(it, "manifest.json") }
        val original = try {
            if (file?.isFile == true) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        return original
            .put("format", FORMAT)
            .put("sessionId", sessionId)
            .put("startedAtMs", startedAt)
            .put("stoppedAtMs", stoppedAt)
            .put("stoppedAtUtc", if (stoppedAt > 0L) iso(stoppedAt) else JSONObject.NULL)
            .put("recording", recordingOverride)
            .put("events", eventCount)
            .put("droppedEvents", droppedEvents.get())
            .put("bytes", byteCount)
            .put("segments", currentSegment)
            .put("stopReason", stopReason)
            .put("lastError", lastError)
    }

    private fun updateManifest() {
        val dir = sessionDir ?: return
        try {
            File(dir, "manifest.json").writeText(manifestObject().toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun pruneOldSessions() {
        val keep = settings.sessionKeepCount.coerceIn(1, 20)
        val dirs = paths.sessionLogsRoot.listFiles { file -> file.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        dirs.drop((keep - 1).coerceAtLeast(0)).forEach { old -> old.deleteRecursively() }
    }

    private fun awaitPendingWrites(timeoutMs: Long = 5_000L) {
        if (worker.isShutdown) return
        try { worker.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Exception) {}
    }

    private fun summarize(type: String, data: JSONObject): String = when (type) {
        "telemetry" -> {
            val event = data.optJSONObject("event") ?: data
            val payload = event.optJSONObject("data") ?: event.optJSONObject("live") ?: event
            "${payload.optInt("rpm", 0)} rpm • ${payload.optString("fuel", payload.optString("state", "--"))} • " +
                "${payload.optDouble("petrol_ms", 0.0)} ms • MAP ${payload.optDouble("load_bar", payload.optDouble("map_bar", 0.0))}"
        }
        "obd" -> "RPM ${data.opt("rpm")} • STFT ${data.opt("stft")} • ${data.optString("quality", "--")}"
        "app_log" -> "${data.optString("category", "LOG")}: ${data.optString("message", "").take(140)}"
        "settings_changed" -> "Configurações alteradas"
        "usb_raw" -> "${data.optString("direction")} • ${data.optInt("size", 0)} bytes"
        else -> type.replace('_', ' ')
    }

    private fun aiReadme(): String = """
OMEGAS PRO — PACOTE DE SESSÃO ANDROID NATIVO

Formato principal: JSON Lines, um objeto completo por linha, em events_XXXX.jsonl.
Cada registro possui sequence, recordedAtMs, recordedAtUtc, type, source e data.

Tipos principais:
- telemetry: uma única cópia de cada telemetria publicada pelo núcleo Android;
- full_snapshot: estado aprofundado periódico, incluindo aprendizado e runtime;
- app_log: conexão, recuperação serial, mapa K, readback e erros;
- obd: fonte opcional e isolada;
- usb_raw: somente quando habilitado explicitamente;
- k_*: leitura, escrita, ACK e confirmação do mapa K;
- export_boundary: ponto imutável usado quando a sessão foi exportada ainda ativa.

O aprendizado deve ser avaliado por sample.state, sample.reason, learning.live,
learning.session_summary e learning.memory. Transição, cutoff e verificação do novo
combustível são observados, mas não alimentam a memória.

O export_summary.json lista os bytes e SHA-256 exatos de cada arquivo incluído.
droppedEvents maior que zero indica lacuna de gravação para proteger a comunicação.

Unidades: RPM em rpm; tempos em ms; MAP/pressões em bar; temperaturas em °C.
""".trimIndent()

    private fun iso(value: Long): String = synchronized(isoFormatter) {
        isoFormatter.format(Date(value))
    }

    private data class ExportEntry(
        val path: String,
        val file: File? = null,
        val bytes: ByteArray? = null,
    )

    private data class ExportSnapshot(
        val entries: List<ExportEntry>,
        val sessionStatus: JSONObject,
        val immutableBoundary: Boolean,
    )
}


