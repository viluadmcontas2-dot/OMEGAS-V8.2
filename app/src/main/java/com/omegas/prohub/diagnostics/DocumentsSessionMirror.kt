package com.omegas.prohub.diagnostics

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.annotation.TargetApi
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cópia durável das sessões OMEGAS fora do sandbox do aplicativo.
 *
 * Cada sessão encerrada vira UM arquivo `Download/Omegas/<sessão>.zip`, escrito uma vez
 * (oculto enquanto é gravado) e reaproveitado se já existir: nada de arquivos soltos
 * reescritos durante a direção, que o sincronizador do Drive multiplicava em
 * "events_0001.jsonl (31).json". A captura continua pertencendo ao SessionRecorder;
 * esta classe não cria polling, captura paralela, pruning ou autoridade científica.
 */
class DocumentsSessionMirror(private val context: Context) {
    companion object {
        const val PUBLIC_ROOT = "Download/Omegas"

        fun zipName(sessionId: String): String = safeName(sessionId) + ".zip"

        fun safeName(raw: String): String =
            raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").take(96).ifBlank { "session" }

        /** Arquivos da sessão (sem marcadores ocultos), em ordem estável. */
        fun sessionFiles(sessionDir: File): List<File> = sessionDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.relativeTo(sessionDir).path }
            .toList()

        /** ZIP determinístico: `<sessão>/<arquivo>` para cada arquivo da pasta. */
        fun writeZip(sessionDir: File, sessionId: String, output: OutputStream): Int {
            val files = sessionFiles(sessionDir)
            val prefix = safeName(sessionId)
            ZipOutputStream(output.buffered()).use { zip ->
                files.forEach { file ->
                    val entry = ZipEntry(prefix + "/" + file.relativeTo(sessionDir).invariantSeparatorsPath)
                    entry.time = file.lastModified()
                    zip.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
            }
            return files.size
        }
    }

    @Volatile private var lastSyncAtMs = 0L
    @Volatile private var lastSyncOk = true
    @Volatile private var lastError = ""
    @Volatile private var lastSessionId = ""
    @Volatile private var lastFileCount = 0

    /** Publica a sessão ENCERRADA como um único ZIP. Chamar de novo substitui o mesmo arquivo. */
    @Synchronized
    fun sync(sessionDir: File, sessionId: String): JSONObject {
        if (!sessionDir.isDirectory) return fail(sessionId, "Sessão local não encontrada")
        if (!isAvailable()) {
            return fail(
                sessionId,
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    "Permissão de armazenamento necessária para Download/Omegas"
                } else {
                    "Armazenamento público indisponível"
                },
            )
        }
        return try {
            val safeSession = safeName(sessionId)
            val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishZipScoped(sessionDir, safeSession)
            else publishZipLegacy(sessionDir, safeSession)
            lastSyncAtMs = System.currentTimeMillis()
            lastSyncOk = true
            lastError = ""
            lastSessionId = safeSession
            lastFileCount = count
            statusObject().put("ok", true).put("path", "$PUBLIC_ROOT/${zipName(safeSession)}")
        } catch (error: Exception) {
            fail(sessionId, error.message ?: error.javaClass.simpleName)
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishZipScoped(sessionDir: File, safeSession: String): Int {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = zipName(safeSession)
        val relativePath = "$PUBLIC_ROOT/"
        // Inclui itens ainda pendentes (gravação interrompida): reaproveita em vez de criar "(1).zip".
        val selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?"
        val args = arrayOf(relativePath, name)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            resolver.query(collection, projection, Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(MediaStore.setIncludePending(collection), projection, selection, args, null)
        }
        var existing: android.net.Uri? = null
        cursor?.use {
            if (it.moveToFirst()) existing = ContentUris.withAppendedId(collection, it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
        }
        existing?.let { uri -> resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }, null, null) }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Não foi possível criar $name em Download/Omegas")
        val count = resolver.openOutputStream(uri, "rwt")?.use { writeZip(sessionDir, safeSession, it) }
            ?: error("Destino público indisponível")
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return count
    }

    @Suppress("DEPRECATION")
    private fun publishZipLegacy(sessionDir: File, safeSession: String): Int {
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas").apply { mkdirs() }
        val target = File(root, zipName(safeSession))
        val tmp = File(root, ".${target.name}.tmp")
        val count = FileOutputStream(tmp).use { writeZip(sessionDir, safeSession, it) }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        return count
    }

    @Synchronized
    fun publishRootFile(source: File): JSONObject {
        if (!source.isFile) return JSONObject().put("ok", false).put("error", "Arquivo local não encontrado")
        if (!isAvailable()) {
            return JSONObject()
                .put("ok", false)
                .put("error", if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    "Permissão de armazenamento necessária para Download/Omegas"
                } else {
                    "Armazenamento público indisponível"
                })
        }
        return try {
            require(File(source.name).name == source.name && !source.name.contains("..")) { "Nome de arquivo inválido" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) syncScopedAt(source, "$PUBLIC_ROOT/")
            else syncLegacyAt(
                source,
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Omegas",
                ).apply { mkdirs() },
            )
            JSONObject()
                .put("ok", true)
                .put("published", true)
                .put("path", "$PUBLIC_ROOT/${source.name}")
                .put("relativeRoot", PUBLIC_ROOT)
                .put("survivesAppDataClear", true)
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", error.message ?: error.javaClass.simpleName)
                .put("relativeRoot", PUBLIC_ROOT)
        }
    }

    fun statusObject(): JSONObject = JSONObject()
        .put("available", isAvailable())
        .put("lastSyncOk", lastSyncOk)
        .put("lastSyncAtMs", lastSyncAtMs)
        .put("lastError", lastError)
        .put("lastSessionId", lastSessionId)
        .put("lastFileCount", lastFileCount)
        .put("relativeRoot", PUBLIC_ROOT)
        .put("survivesAppDataClear", true)
        .put("automatic", true)

    private fun fail(sessionId: String, message: String): JSONObject {
        lastSyncAtMs = System.currentTimeMillis()
        lastSyncOk = false
        lastError = message
        lastSessionId = safeName(sessionId)
        return statusObject().put("ok", false).put("error", message)
    }

    private fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun syncScopedAt(source: File, relativePath: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val appendable = isAppendable(source)
        val selection = if (appendable) {
            MediaStore.MediaColumns.RELATIVE_PATH + "=? AND (" +
                MediaStore.MediaColumns.DISPLAY_NAME + "=? OR " +
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?)"
        } else {
            MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + "=?"
        }
        val args = if (appendable) {
            // Builds antigos publicaram application/json para .jsonl e alguns
            // providers materializaram nomes como events_0001.jsonl.json ou
            // events_0001.jsonl (N).json. Reusar o maior candidato impede que
            // cada sincronização crie outra cópia cumulativa.
            arrayOf(relativePath, source.name, source.name + "%")
        } else {
            arrayOf(relativePath, source.name)
        }
        var targetUri: android.net.Uri? = null
        var targetSize = -1L
        var exactName = false
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val candidateSize = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val candidateName = cursor.getString(nameColumn).orEmpty()
                if (appendable && candidateSize > source.length()) continue
                val candidateExact = candidateName == source.name
                if (
                    targetUri == null ||
                    candidateSize > targetSize ||
                    (candidateSize == targetSize && candidateExact && !exactName)
                ) {
                    val id = cursor.getLong(idColumn)
                    targetUri = ContentUris.withAppendedId(collection, id)
                    targetSize = candidateSize
                    exactName = candidateExact
                }
            }
        }
        if (targetUri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(source))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            targetUri = resolver.insert(collection, values)
                ?: error("Não foi possível criar " + source.name + " em Download/Omegas")
            targetSize = 0L
        }

        val uri = requireNotNull(targetUri)
        if (appendable && targetSize in 0..source.length()) {
            if (targetSize == source.length()) return
            try {
                resolver.openOutputStream(uri, if (targetSize == 0L) "wt" else "wa")?.use { output ->
                    FileInputStream(source).use { input ->
                        skipFully(input, targetSize)
                        input.copyTo(output, 64 * 1024)
                    }
                } ?: error("Destino público indisponível")
                return
            } catch (_: Exception) {
                // Alguns providers não suportam append; regravação completa é segura.
            }
        }
        resolver.openOutputStream(uri, "rwt")?.use { output ->
            source.inputStream().use { it.copyTo(output, 64 * 1024) }
        } ?: error("Destino público indisponível")
    }

    private fun syncLegacyAt(source: File, root: File) {
        val target = File(root, source.name)
        if (isAppendable(source) && target.isFile && target.length() in 0..source.length()) {
            val offset = target.length()
            if (offset == source.length()) return
            FileInputStream(source).use { input ->
                skipFully(input, offset)
                FileOutputStream(target, true).use { output -> input.copyTo(output, 64 * 1024) }
            }
        } else {
            source.copyTo(target, overwrite = true)
        }
    }

    private fun isAppendable(file: File): Boolean =
        file.name.startsWith("events_") && file.name.endsWith(".jsonl")

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "json" -> "application/json"
        // application/json pode fazer alguns providers anexarem ".json" ao
        // DISPLAY_NAME ".jsonl", quebrando a busca exata e multiplicando cópias.
        "jsonl" -> "application/octet-stream"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun skipFully(input: FileInputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) remaining -= skipped
            else if (input.read() >= 0) remaining -= 1L
            else break
        }
    }
}