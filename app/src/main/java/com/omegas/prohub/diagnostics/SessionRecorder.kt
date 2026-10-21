package com.omegas.prohub.diagnostics

import android.content.ContentResolver
import android.net.Uri
import com.omegas.prohub.autocal.AntiStallSessionAnalysis
import com.omegas.prohub.util.AtomicJsonFile
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
import java.util.concurrent.Executors
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
    private val documentsMirror: DocumentsSessionMirror? = null,
) {
    companion object {
        private const val FORMAT = "omegas-session-log-v1"
        private const val SEGMENT_LIMIT_BYTES = 64L * 1024L * 1024L
        private const val PREVIEW_LIMIT = 120
        /** Garantia local (flash/manifesto) a cada 2 min; o ZIP no arquivo privado do app só sai ao fechar a sessão. */
        private const val DOCUMENTS_MIRROR_INTERVAL_MS = 120_000L
        /** Evento do AutoCal antecipa a parte, sem gerar uma parte por evento. */
        private const val DURABLE_EVENT_MIN_GAP_MS = 15_000L
        /**
         * Teto total das sessões guardadas no app (1,5 GB). Sessão sem ZIP confirmado no arquivo privado não saía nunca; sem
         * permissão ou com o MediaStore falhando, o disco enchia (até 256 MB por viagem) e depois tudo falhava em
         * silêncio. Acima do teto as mais antigas saem, espelhadas ou não; a ativa nunca.
         */
        internal const val SESSIONS_TOTAL_LIMIT_BYTES = 1_536L * 1024L * 1024L

        /**
         * Poda das pastas de sessão (puro, sem Android). Regra de sempre: pasta minúscula e [durable] (cópia arquivada
         * confirmada) sai; fora das [keep] mais recentes só sai quem é [durable]. Regra nova: acima de
         * [totalLimitBytes] as mais antigas saem mesmo sem cópia arquivada, nunca [activeDir].
         * Devolve quantas saíram pelo teto.
         */
        internal fun pruneSessionDirs(
            root: File,
            activeDir: File?,
            keep: Int,
            totalLimitBytes: Long,
            durable: (File) -> Boolean,
        ): Int {
            val dirs = root.listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            val sizes = HashMap<File, Long>()
            fun sizeOf(dir: File): Long = sizes.getOrPut(dir) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
            fun isActive(dir: File): Boolean = activeDir != null && dir.absolutePath == activeDir.absolutePath

            val validDirs = dirs.filter { dir ->
                if (sizeOf(dir) < 10 * 1024 && durable(dir) && !isActive(dir)) {
                    dir.deleteRecursively()
                    false
                } else {
                    true
                }
            }
            validDirs.drop((keep - 1).coerceAtLeast(0)).forEach { old ->
                if (durable(old) && !isActive(old)) old.deleteRecursively()
            }

            var total = validDirs.filter { it.isDirectory }.sumOf { sizeOf(it) }
            var removed = 0
            for (old in validDirs.asReversed()) {
                if (total <= totalLimitBytes) break
                if (!old.isDirectory || isActive(old)) continue
                val size = sizeOf(old)
                if (old.deleteRecursively()) {
                    total -= size
                    removed += 1
                }
            }
            return removed
        }
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
    /**
     * Publicação (ZIP no arquivo privado do app) fora do worker de eventos e de quem pediu a parada: empacotar uma sessão
     * de até 256 MB na thread de quem chamava `stop()` (a mesma do agendador que reconecta o USB) travava o app,
     * e no worker travaria a gravação da sessão seguinte.
     */
    private val publisher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-session-publisher").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    /** Uma publicação por vez (fechamento, recuperação ou limpeza manual): os offsets das partes são por pasta. */
    private val publishLock = Any()
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
    /** Painel da multimídia (car_info): no máximo 1 evento por segundo. */
    private val carInfoGate = MinIntervalGate(1_000L)
    @Volatile private var stopReason = ""
    @Volatile private var lastError = ""

    /** Falha de disco/arquivo vira o `lastError` do status (frase legível na tela); a gravação segue. */
    private fun noteFailure(what: String, error: Exception) {
        lastError = "Não consegui $what: ${error.message ?: error.javaClass.simpleName}"
    }
    @Volatile private var lastDocumentsMirrorAt = 0L
    @Volatile private var lastExportedSegment = 0
    @Volatile private var lastExportBoundarySequence = 0L

    private var sessionDir: File? = null
    private var antiStallAnalysis = AntiStallSessionAnalysis()
    private val antiStallFile = File(paths.runtimeRoot, "anti-stall-analysis.json")
    private var antiStallResult = AtomicJsonFile.readJson(antiStallFile) ?: JSONObject()

    @Synchronized
    fun antiStallSuggestion(): JSONObject = JSONObject(antiStallResult.toString())

    /** Event-driven, once per closed session; no import and no ECU write. */
    private fun finishAntiStallAnalysis() {
        val result = antiStallAnalysis.finish(sessionId)
        val oldFloor = antiStallResult.optInt("maiorAjusteComQueda", -1)
        val newFloor = result.optInt("maiorAjusteComQueda", -1)
        val floor = maxOf(oldFloor, newFloor)
        if (floor >= 0) result.put("maiorAjusteComQueda", floor)
        try {
            val text = result.toString(2)
            sessionDir?.let { dir ->
                AtomicJsonFile.write(File(dir, "anti-apagao.json"), text)
                File(dir, "RESUMO-DA-VIAGEM.html").writeText(
                    """<!doctype html><html lang="pt-BR"><meta charset="utf-8">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>Resumo da viagem — OMEGAS</title>
                    <style>body{font:20px system-ui;max-width:42em;margin:2em auto;padding:0 1em;line-height:1.5}</style>
                    <h1>Resumo da viagem</h1><p>${result.getString("motivo")}</p>
                    <p>Leituras analisadas: ${result.getInt("leituras")}.</p>
                    <p>Paradas do motor observadas: ${result.getInt("paradasDoMotor")}.</p>
                    <p>Não foi possível confirmar se o carro estava em movimento ou se a chave foi desligada.</p>
                    <p>Nenhum ajuste foi gravado pelo app. Os quatro ajustes extras ainda não têm dados para sugestão.</p>
                    <p>O arquivo anti-apagao.json guarda as quedas e o resultado da análise.
                    Os arquivos de eventos guardam as leituras completas.</p></html>""".trimIndent(),
                )
            }
            AtomicJsonFile.write(antiStallFile, result.toString())
            antiStallResult = result
            recordNow("anti_stall_analysis", "native", result)
        } catch (error: Exception) {
            lastError = "Não foi possível salvar a análise da viagem: ${error.message}"
        }
    }

    private var semanticLedger: SessionSemanticLedger? = null
    private var writer: BufferedWriter? = null
    private var segmentFile: File? = null
    private var segmentStream: FileOutputStream? = null
    private var segmentBytes = 0L

    @Synchronized
    fun start(reason: String, metadata: JSONObject = JSONObject()): JSONObject {
        if (recording) return statusObject().put("ok", true).put("alreadyRecording", true)
        return try {
            val pruned = pruneOldSessions()
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
            carInfoGate.reset()
            stopReason = ""
            lastError = ""
            lastDocumentsMirrorAt = 0L
            lastExportedSegment = 0
            lastExportBoundarySequence = 0L
            sequence.set(0L)
            droppedEvents.set(0L)
            petrolTicks = 0L
            cngTicks = 0L
            synchronized(previewLock) { preview.clear() }
            antiStallAnalysis = AntiStallSessionAnalysis()
            semanticLedger = SessionSemanticLedger(
                sessionDir = dir,
                sessionId = id,
                physicalUsbSessionId = metadata.optLong("usbSessionId", 0L),
                startedAtMs = now,
                startReason = reason,
            )
            openNextSegment()
            recording = true
            writeManifestBase(dir, now, reason, metadata)
            File(dir, "README_PARA_IA.txt").writeText(aiReadme(), Charsets.UTF_8)
            recordNow(
                "session_started",
                "native",
                JSONObject().put("reason", reason).put("metadata", metadata),
            )
            if (pruned > 0) {
                recordNow(
                    "app_log",
                    "native",
                    JSONObject().put("level", "INFO").put("category", "SESSOES")
                        .put("message", "Sessões antigas apagadas para liberar espaço: $pruned"),
                )
            }
            worker.execute {
                synchronized(this) {
                    if (recording && sessionId == id) syncDocumentsMirror(force = true)
                }
            }
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
            finishAntiStallAnalysis()
            recordNow("session_stopped", "native", JSONObject().put("reason", reason))
            recording = false
            stoppedAt = System.currentTimeMillis()
            stopReason = reason
            semanticLedger?.finish(stoppedAt, stopReason)
            closeWriter()
            updateManifest()
            // Garante no flash e agenda a publicação: o ZIP sai no publisher, nunca na thread de quem parou.
            syncDocumentsMirror(force = true)
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
        if (!force && type == "car_info" && !carInfoGate.allow(now)) return
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
            .put("documentsMirror", documentsMirror?.statusObject() ?: JSONObject()
                .put("available", false)
                .put("lastSyncOk", false)
                .put("relativeRoot", DocumentsSessionMirror.PRIVATE_ROOT))
            .put("semanticSummary", semanticLedger?.snapshot(recording, stoppedAt, stopReason) ?: JSONObject.NULL)
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
                val semantic = if (active) {
                    semanticLedger?.snapshot(true) ?: JSONObject()
                } else {
                    try { SessionSemanticLedger.loadOrRebuild(dir) } catch (_: Exception) { JSONObject() }
                }
                array.put(
                    JSONObject()
                        .put("id", dir.name)
                        .put("createdAt", createdAt)
                        .put("stoppedAt", stoppedAt)
                        .put("durationMs", (durationEnd - createdAt).coerceAtLeast(0L))
                        .put("reason", manifest.optString("reason", "Sessão"))
                        .put("bytes", size)
                        .put("active", active)
                        .put("cngTicks", manifest.optLong("cngTicks", 0L))
                        .put("petrolTicks", manifest.optLong("petrolTicks", 0L))
                        .put("semanticSummary", semantic),
                )
            }
        return array.toString()
    }

    fun clearStoppedSessions(): JSONObject {
        var deleted = 0
        var preserved = 0
        paths.sessionLogsRoot.listFiles { file -> file.isDirectory }?.forEach { dir ->
            if (recording && dir.absolutePath == sessionDir?.absolutePath) return@forEach
            if (documentsMirror != null && !documentsMirrorMarker(dir).isFile) {
                val published = try { publishParts(dir, dir.name, final = true) } catch (_: Exception) { false }
                if (!published) {
                    preserved += 1
                    return@forEach
                }
            }
            if (dir.deleteRecursively()) deleted += 1
        }
        return JSONObject().put("ok", true).put("deleted", deleted).put("preservedWithoutPublicCopy", preserved)
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
                        .put("exportMode", if (snapshot.incrementalEvents) "INCREMENTAL_ACTIVE" else "FULL_STOPPED")
                        .put("exportStartSequenceExclusive", snapshot.exportStartSequenceExclusive)
                        .put("exportEndSequenceInclusive", snapshot.exportedThroughSequence)
                        .put(
                            "exportedEventCount",
                            (snapshot.exportedThroughSequence - snapshot.exportStartSequenceExclusive).coerceAtLeast(0L),
                        )
                        .put("integrity", "SHA-256 calculado sobre os bytes exatos incluídos neste ZIP")
                    zip.putNextEntry(ZipEntry("export_summary.json"))
                    zip.write(summary.toString(2).toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: return JSONObject().put("ok", false).put("error", "Destino de exportação indisponível")
            if (snapshot.incrementalEvents) {
                synchronized(this) {
                    if (sessionId == snapshot.sessionId) {
                        lastExportedSegment = maxOf(lastExportedSegment, snapshot.exportedThroughSegment)
                        lastExportBoundarySequence = maxOf(
                            lastExportBoundarySequence,
                            snapshot.exportedThroughSequence,
                        )
                    }
                }
            }
            JSONObject()
                .put("ok", true)
                .put("sessionId", requested.name)
                .put("events", snapshot.sessionStatus.optLong("events", 0L))
                .put(
                    "exportedEvents",
                    (snapshot.exportedThroughSequence - snapshot.exportStartSequenceExclusive).coerceAtLeast(0L),
                )
                .put("exportMode", if (snapshot.incrementalEvents) "INCREMENTAL_ACTIVE" else "FULL_STOPPED")
                .put("immutableBoundary", snapshot.immutableBoundary)
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", error.message ?: "Falha ao exportar sessão")
        }
    }

    fun recoverDocumentsMirrorAsync() {
        if (documentsMirror == null) return
        if (publisher.isShutdown) return
        publisher.execute {
            // Versões antigas deixavam cópias na pasta pública Download/Omegas: traz para a pasta privada (sem perda).
            try {
                documentsMirror?.migrateLegacyPublicCopies()
            } catch (error: Exception) {
                noteFailure("trazer as cópias antigas", error)
            }
            paths.sessionLogsRoot.listFiles { file -> file.isDirectory }
                ?.sortedBy { it.lastModified() }
                ?.forEach { dir ->
                    // Já publicada (ZIP ou espelho antigo) ou ainda gravando: não toca.
                    if (documentsMirrorMarker(dir).isFile) return@forEach
                    if (synchronized(this) { recording && sessionDir?.absolutePath == dir.absolutePath }) return@forEach
                    try {
                        // Sessão interrompida (energia cortada): publica o que faltou como parte final.
                        publishParts(dir, dir.name, final = true)
                    } catch (error: Exception) {
                        noteFailure("arquivar a sessão", error)
                    }
                }
        }
    }

    fun close() {
        // Sem @Synchronized: segurar o monitor enquanto `stop()` espera o worker (que precisa do mesmo monitor)
        // travava 5 s em todo encerramento. `stop()` toma o monitor sozinho, só pelo tempo de fechar o segmento.
        if (recording) stop("serviço encerrado")
        worker.shutdownNow()
        // O ZIP em andamento termina se o processo viver; se morrer, a mesma parte é refeita na próxima abertura
        // (o offset só avança depois de publicar). Nada é apagado antes de publicado.
        publisher.shutdown()
    }

    private fun createActiveExportSnapshot(dir: File): ExportSnapshot {
        val exportStartSegment = lastExportedSegment + 1
        val exportStartSequenceExclusive = lastExportBoundarySequence

        recordNow(
            "export_boundary",
            "native",
            JSONObject().put("reason", "Corte imutável para exportação ativa"),
        )
        writer?.flush()
        writer?.close()
        writer = null

        val boundarySegment = currentSegment
        val boundarySequence = sequence.get()
        semanticLedger?.persist(recording = true)

        // Exportações repetidas da MESMA sessão ativa são incrementais:
        // cada events_XXXX.jsonl entra no máximo uma vez em exportações bem-sucedidas.
        // Metadados pequenos continuam presentes em cada pacote para manter o lote
        // autocontido e auditável.
        val closedFiles = dir.walkTopDown()
            .filter { file ->
                if (!file.isFile || file.name == "manifest.json") {
                    false
                } else {
                    val segment = eventSegmentNumber(file.name)
                    segment == null || segment in exportStartSegment..boundarySegment
                }
            }
            .toList()
        val manifestBytes = manifestObject(recordingOverride = true)
            .put("exportMode", "INCREMENTAL_ACTIVE")
            .put("exportEventSegmentStart", exportStartSegment)
            .put("exportEventSegmentEnd", boundarySegment)
            .put("exportStartSequenceExclusive", exportStartSequenceExclusive)
            .put("exportBoundarySequence", boundarySequence)
            .put("exportBoundaryAtMs", System.currentTimeMillis())
            .toString(2)
            .toByteArray(Charsets.UTF_8)

        // Eventos novos seguem em outro arquivo; os arquivos acima não mudam mais.
        openNextSegment()
        updateManifest()

        val entries = mutableListOf(ExportEntry("manifest.json", bytes = manifestBytes))
        closedFiles.forEach { file ->
            val path = file.relativeTo(dir).invariantSeparatorsPath
            if (file.name == SessionSemanticLedger.FILE_NAME) {
                entries += ExportEntry(path, bytes = file.readBytes())
            } else {
                entries += ExportEntry(path, file = file)
            }
        }
        return ExportSnapshot(
            entries = entries,
            sessionStatus = JSONObject(String(manifestBytes, Charsets.UTF_8)),
            immutableBoundary = true,
            sessionId = sessionId,
            incrementalEvents = true,
            exportStartSequenceExclusive = exportStartSequenceExclusive,
            exportedThroughSequence = boundarySequence,
            exportedThroughSegment = boundarySegment,
        )
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
        return ExportSnapshot(
            entries = entries,
            sessionStatus = manifest,
            immutableBoundary = true,
            sessionId = dir.name,
            incrementalEvents = false,
            exportStartSequenceExclusive = 0L,
            exportedThroughSequence = manifest.optLong("events", 0L),
            exportedThroughSegment = manifest.optInt("segments", 0),
        )
    }

    private fun eventSegmentNumber(name: String): Int? {
        if (!name.startsWith("events_") || !name.endsWith(".jsonl")) return null
        return name.removePrefix("events_").removeSuffix(".jsonl").toIntOrNull()
    }

    @Volatile private var petrolTicks = 0L
    @Volatile private var cngTicks = 0L

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
            
            if (type == "telemetry") {
                val evData = data.optJSONObject("event")?.optJSONObject("data") ?: data.optJSONObject("data") ?: data
                val f = evData.optString("fuel", "").uppercase()
                if (f.contains("PETROL") || f.contains("GASOLINA")) petrolTicks++
                if (f.contains("CNG") || f.contains("GNV") || f == "GAS") cngTicks++
            }

            antiStallAnalysis.observe(type, data)
            val line = item.toString() + "\n"
            val bytes = line.toByteArray(StandardCharsets.UTF_8).size.toLong()
            val effectiveLimitMb = settings.sessionLogMaxMb.coerceIn(64, 1_024)
            val maxBytes = effectiveLimitMb.toLong() * 1024L * 1024L
            if (byteCount + bytes > maxBytes && type != "session_stopped") {
                finishAntiStallAnalysis()
                stopReason = "limite de $effectiveLimitMb MB atingido"
                lastError = stopReason
                recording = false
                stoppedAt = now
                closeWriter()
                updateManifest()
                semanticLedger?.finish(stoppedAt, stopReason)
                syncDocumentsMirror(force = true)
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
                "k_read_curve",
                "k_curve_state",
                "k_map_state",
                "ecu_command",
                "k_write_requested",
                "k_batch_confirmed",
                "buffer_write_probe",
                "autocal_pre_automatch_state",
                "param_snapshot",
                "d1_decision",
            )
            if (eventCount % 32L == 0L || critical) writer?.flush()
            if (critical) syncToDisk()
            eventCount += 1L
            byteCount += bytes
            segmentBytes += bytes
            semanticLedger?.observe(
                sequence = item.optLong("sequence"),
                type = type,
                source = source,
                data = data,
                recordedAtMs = now,
            )
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
            val durableAutoCalEvent = type in setOf(
                "autocal_native_snapshot",
                "autocal_manual_snapshot",
                "autocal_native_action",
                "autocal_native_calibration_epoch",
            )
            val periodicCandidate = type == "telemetry" || type == "full_snapshot"
            val sinceLast = now - lastDocumentsMirrorAt
            if ((durableAutoCalEvent && sinceLast >= DURABLE_EVENT_MIN_GAP_MS) ||
                (periodicCandidate && sinceLast >= DOCUMENTS_MIRROR_INTERVAL_MS)
            ) {
                syncDocumentsMirror(force = true)
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
        val stream = FileOutputStream(file, true)
        segmentStream = stream
        writer = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8), 64 * 1024)
    }

    private fun closeWriter() {
        try { writer?.flush() } catch (error: Exception) { noteFailure("gravar no armazenamento", error) }
        syncToDisk()
        try { writer?.close() } catch (_: Exception) { /* fechando: o que importava já foi gravado acima */ }
        writer = null
        segmentStream = null
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
            .put("petrolTicks", petrolTicks)
            .put("cngTicks", cngTicks)
    }

    private fun updateManifest() {
        val dir = sessionDir ?: return
        try {
            File(dir, "manifest.json").writeText(manifestObject().toString(2), Charsets.UTF_8)
        } catch (error: Exception) {
            noteFailure("salvar o manifesto", error)
        }
    }

    private fun syncDocumentsMirror(force: Boolean) {
        val dir = sessionDir ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastDocumentsMirrorAt < DOCUMENTS_MIRROR_INTERVAL_MS) return
        try {
            writer?.flush()
            syncToDisk()
            updateManifest()
            semanticLedger?.persist(recording = recording, stoppedAtMs = stoppedAt, stopReason = stopReason)
            // Gravando: só garante no armazenamento do app (flash, manifesto, razões); nenhum ZIP é feito até a sessão
            // fechar (um ZIP por sessão, dono 2026-10-13: "49 partes é ridículo"). Se o app morrer, a próxima abertura publica.
            // Parada: o ZIP sai no publisher; aqui (monitor da sessão, thread de quem parou) nada pesado.
            if (!recording) publishAsync(dir, sessionId)
        } finally {
            lastDocumentsMirrorAt = now
        }
    }

    /** Garante no flash o que já foi escrito (carro pode ser desligado a qualquer instante). */
    private fun syncToDisk() {
        try { writer?.flush() } catch (error: Exception) { noteFailure("gravar no armazenamento", error) }
        try { segmentStream?.fd?.sync() } catch (error: Exception) { noteFailure("gravar no armazenamento", error) }
    }

    /** Publica em segundo plano; quem parou volta na hora. Se falhar ou o app morrer, a pasta fica sem marcador e a próxima abertura publica. */
    private fun publishAsync(dir: File, id: String) {
        if (documentsMirror == null || publisher.isShutdown) return
        try {
            publisher.execute {
                try { publishParts(dir, id, final = true) } catch (error: Exception) { noteFailure("arquivar a sessão", error) }
            }
        } catch (_: Exception) {
            // Publicador já encerrado: a pasta fica sem marcador e a próxima abertura arquiva.
        }
    }

    /**
     * Publica a próxima parte imutável com o que é novo. Os offsets só avançam depois da
     * parte publicada; se o app morrer no meio, a mesma parte é refeita com o mesmo nome.
     */
    private fun publishParts(dir: File, id: String, final: Boolean): Boolean = synchronized(publishLock) {
        val mirror = documentsMirror ?: return false
        val plan = try { SessionPartPlanner.plan(dir, final) } catch (_: Exception) { return false }
        if (plan == null) {
            if (final) markDocumentsMirrored(dir)
            return true
        }
        val result = mirror.publishPart(id, plan)
        if (!result.optBoolean("ok")) return false
        try { SessionPartPlanner.commit(dir, plan) } catch (_: Exception) { return false }
        if (final) markDocumentsMirrored(dir)
        return true
    }

    private fun documentsMirrorMarker(dir: File): File = File(dir, ".documents_mirrored")

    private fun markDocumentsMirrored(dir: File) {
        try {
            documentsMirrorMarker(dir).writeText(System.currentTimeMillis().toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // Sem marcador a sessão é arquivada de novo na próxima abertura (mesmo nome, sem perda).
        }
    }

    /** Devolve quantas sessões saíram pelo teto total (regra em [pruneSessionDirs]). */
    private fun pruneOldSessions(): Int = pruneSessionDirs(
        root = paths.sessionLogsRoot,
        activeDir = if (recording) sessionDir else null,
        keep = settings.sessionKeepCount.coerceAtLeast(25),
        totalLimitBytes = SESSIONS_TOTAL_LIMIT_BYTES,
        // Sessão sem ZIP confirmado no arquivo privado só sai pelo teto total, nunca pela contagem.
        durable = { dir -> documentsMirror == null || documentsMirrorMarker(dir).isFile },
    )

    private fun awaitPendingWrites(timeoutMs: Long = 5_000L) {
        if (worker.isShutdown) return
        try { worker.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Exception) { /* espera vencida/interrompida: segue sem bloquear quem parou */ }
    }

    private fun summarize(type: String, data: JSONObject): String = when (type) {
        "telemetry" -> {
            val event = data.optJSONObject("event") ?: data
            val payload = event.optJSONObject("data") ?: event.optJSONObject("live") ?: event
            "${payload.optInt("rpm", 0)} rpm • ${payload.optString("fuel", payload.optString("state", "--"))} • " +
                "${payload.optDouble("petrol_ms", 0.0)} ms • MAP ${payload.optDouble("load_bar", payload.optDouble("map_bar", 0.0))}"
        }
        "car_info" -> "Multimídia • ${data.opt("speed") ?: "—"} km/h • ${data.opt("rotationRate") ?: "—"} rpm • odômetro ${data.opt("odo") ?: "—"}"
        "car_info_raw" -> "Multimídia (dados crus) • ${data.opt("channelsActive") ?: "—"} de 17 canais ativos"
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
- car_info: painel lido da multimídia (velocidade, odômetro, rotação, marcha, combustível, consumo), só leitura,
  no máximo 1 por segundo; só os campos que a multimídia informou (ausente = desconhecido, nunca 0);
- car_info_raw: o que a multimídia mandou, cru, para decidir offline: campos do painel antes da sentinela, propriedades
  (propId/areaId=valor), CabinInfo, contagem de chamadas por transação (1..17) e início do payload em hex; no máximo 1 a cada
  5 s, só quando muda, com teto por ligação;
- ui_usage: contagens de uso da interface (visitas e segundos por aba, toques por controle), deltas desde o último envio; só contagens, sem valores digitados;
- usb_raw: somente quando habilitado explicitamente;
- k_*: leitura, escrita, ACK e confirmação do mapa K;
- k_read_curve / k_read_map: curva K e Mapa K LIDOS por inteiro da ECU (valores completos); campo trigger = UI (botão/tela),
  AUTO_START (12 s após conectar) ou AUTO_PERIODIC (a cada 10 min). O app lê automaticamente, mas nunca grava sozinho;
- k_curve_state / k_map_state: estado completo depois de cada gravação (origin = APP_UI_MANUAL) ou quando a curva da ECU
  mudou (origin = ECU_NATIVE_OR_EXTERNAL, com previousFactorsRaw e engineReview = curva ideal do motor de equivalência para os buffers daquele AutoMatch): reconstrói a linha do tempo de curva e mapa;
- ecu_command: toda ESCRITA enviada à ECU (pedido e resposta em hex, ACK, tempo, motivo); ecu_read_summary: resumo das leituras a cada 30 s;
- autocal_native_snapshot: as 18 bandas de gasolina e GNV (MAP, ms, contagem), MUL_ACT, vetores RV e contadores, a cada
  mudança do AutoMatch;
- autocal_pre_automatch_state: última leitura das bandas do GNV (contadores, zonas, buffers, MUL_ACT) logo antes de a ECU mudar a contagem de AutoMatch;
  responde "o que a ECU tinha quando decidiu?" (counterAgeMs = idade da leitura; campo desconhecido = nulo, nunca 0);
- param_snapshot: leitura SÓ-LEITURA de ~33 parâmetros da ECU (ANTI_STALLO, troca de combustível, corte/retorno, regras de aquisição do AutoCal) uma vez por conexão; params.NOME = {address, state, values}; desconhecido = values nulo;
- d1_decision: decisão do Refino automático depois de um AutoMatch da ECU (mode OFF/SHADOW, nunca grava; points = o que sugeriria, com as travas usadas; reason explica quando não sugere; retryOnIdle = nova tentativa na borda ocupado→livre; idealFactorsRaw e liveFactorsRaw para auditar);
- buffer_write_probe: resultado da sonda de escrita única nos pontos do GNV (só por toque do dono em Ferramentas);
- export_boundary: ponto imutável usado quando a sessão foi exportada ainda ativa.

Exportação ativa é incremental: cada events_XXXX.jsonl já exportado com sucesso não
volta a ser empacotado na exportação ativa seguinte. Sessão parada é exportada completa.

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
        val sessionId: String,
        val incrementalEvents: Boolean,
        val exportStartSequenceExclusive: Long,
        val exportedThroughSequence: Long,
        val exportedThroughSegment: Int,
    )
}