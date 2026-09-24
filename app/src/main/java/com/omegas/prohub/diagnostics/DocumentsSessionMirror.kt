package com.omegas.prohub.diagnostics

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Espelho durável das sessões OMEGAS fora do sandbox do aplicativo.
 *
 * A captura continua pertencendo ao SessionRecorder. Esta classe só replica os
 * arquivos já produzidos para Download/Omegas e nunca cria polling,
 * captura paralela, pruning ou autoridade científica.
 */
class DocumentsSessionMirror(private val context: Context) {
    companion object {
        const val PUBLIC_ROOT = "Download/Omegas"
    }

    @Volatile private var lastSyncAtMs = 0L
    @Volatile private var lastSyncOk = true
    @Volatile private var lastError = ""
    @Volatile private var lastSessionId = ""
    @Volatile private var lastFileCount = 0

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
            val files = sessionDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") }
                .sortedBy { it.name }
                .toList()
            files.forEach { source ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) syncScoped(source, safeSession)
                else syncLegacy(source, safeSession)
            }
            lastSyncAtMs = System.currentTimeMillis()
            lastSyncOk = true
            lastError = ""
            lastSessionId = safeSession
            lastFileCount = files.size
            statusObject().put("ok", true)
        } catch (error: Exception) {
            fail(sessionId, error.message ?: error.javaClass.simpleName)
        }
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

    private fun syncScoped(source: File, safeSession: String) =
        syncScopedAt(source, "$PUBLIC_ROOT/$safeSession/")

    private fun syncScopedAt(source: File, relativePath: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID, OpenableColumns.SIZE)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
            MediaStore.MediaColumns.RELATIVE_PATH + "=?"
        val args = arrayOf(source.name, relativePath)
        var targetUri: android.net.Uri? = null
        var targetSize = 0L
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                targetSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)).coerceAtLeast(0L)
                targetUri = ContentUris.withAppendedId(collection, id)
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
        if (isAppendable(source) && targetSize in 0..source.length()) {
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

    @Suppress("DEPRECATION")
    private fun syncLegacy(source: File, safeSession: String) {
        val root = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Omegas/$safeSession",
        ).apply { mkdirs() }
        syncLegacyAt(source, root)
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
        "json", "jsonl" -> "application/json"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun safeName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").take(96).ifBlank { "session" }

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