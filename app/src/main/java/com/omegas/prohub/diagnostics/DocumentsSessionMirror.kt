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

/**
 * Cópia durável das sessões OMEGAS fora do sandbox do aplicativo.
 *
 * Cada sessão sai em UM ZIP imutável (`Download/Omegas/<sessão>/<sessão>.zip`, ver
 * SessionPartPlanner), publicado ao fechar a sessão ou, se o app morreu, na próxima abertura.
 * Escrito uma vez, oculto enquanto grava, nunca reescrito. Sessões antigas com partes seguem em
 * `_parte_NNNN.zip`.
 * Arquivos soltos reescritos durante a direção eram multiplicados pelo Drive em
 * "events_0001.jsonl (31).json"; um ZIP só no fim perdia a sessão num corte de energia. A captura continua pertencendo ao SessionRecorder;
 * esta classe não cria polling, captura paralela nem autoridade científica. Só o teto de espaço (MirrorRetention: aviso a 3 GB, as sessões mais antigas saem acima de 6 GB) mexe em arquivos já publicados.
 */
class DocumentsSessionMirror(private val context: Context) {
    companion object {
        const val PUBLIC_ROOT = "Download/Omegas"

        fun safeName(raw: String): String =
            raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").take(96).ifBlank { "session" }

        /** Arquivos da sessão (sem marcadores ocultos), em ordem estável. */
        fun sessionFiles(sessionDir: File): List<File> = sessionDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.relativeTo(sessionDir).path }
            .toList()
    }

    @Volatile private var lastSyncAtMs = 0L
    @Volatile private var lastSyncOk = true
    @Volatile private var lastError = ""
    @Volatile private var lastSessionId = ""
    @Volatile private var lastFileCount = 0
    @Volatile private var usageBytes = -1L
    @Volatile private var usageWarning = ""
    @Volatile private var prunedSessions = 0
    private var lastRetentionAtMs = 0L

    /**
     * Publica UMA parte imutável da sessão em `Download/Omegas/<sessão>/<sessão>_parte_NNNN.zip`.
     * O número é reservado antes de escrever (SessionPartPlanner.reserve) e um nome que já existe no
     * destino — publicado ou pendente de uma queda — nunca é reaberto: a parte sobe de número. Assim
     * uma parte publicada nunca é regravada com outra faixa e nunca nasce "(1).zip".
     */
    @Synchronized
    fun publishPart(sessionId: String, plan: SessionPartPlanner.Plan): JSONObject {
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
            SessionPartPlanner.reserve(plan) { candidate ->
                publicNameExists(safeSession, SessionPartPlanner.fileName(sessionId, candidate))
            }
            val name = SessionPartPlanner.fileName(sessionId, plan)
            val write: (OutputStream) -> Int = { SessionPartPlanner.writeZip(plan, sessionId, it) }
            val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishZipScoped("$PUBLIC_ROOT/$safeSession/", name, write)
            else publishZipLegacy(safeSession, name, write)
            lastSyncAtMs = System.currentTimeMillis()
            lastSyncOk = true
            lastError = ""
            lastSessionId = safeSession
            lastFileCount = count
            enforceRetention(safeSession)
            statusObject().put("ok", true).put("path", "$PUBLIC_ROOT/$safeSession/$name").put("part", plan.part)
        } catch (error: Exception) {
            fail(sessionId, error.message ?: error.javaClass.simpleName)
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun scopedItem(relativePath: String, name: String): android.net.Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // Inclui itens ainda pendentes (gravação interrompida): também contam como nome usado.
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
        return existing
    }

    @Suppress("DEPRECATION")
    private fun publicNameExists(safeSession: String, name: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scopedItem("$PUBLIC_ROOT/$safeSession/", name) != null
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas/$safeSession/$name").exists()
        }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishZipScoped(relativePath: String, name: String, write: (OutputStream) -> Int): Int {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // Nome reservado e conferido antes: se mesmo assim existir, não reescreve (nunca "rwt" sobre parte publicada).
        check(scopedItem(relativePath, name) == null) { "$name já existe em Download/Omegas; parte publicada não é reescrita" }
        val uri = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Não foi possível criar $name em Download/Omegas")
        val count = try {
            resolver.openOutputStream(uri, "w")?.use { write(it) } ?: error("Destino público indisponível")
        } catch (error: Exception) {
            // ZIP pela metade não fica pendente com o nome reservado; a próxima tentativa usa outro número.
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw error
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return count
    }

    @Suppress("DEPRECATION")
    private fun publishZipLegacy(safeSession: String, name: String, write: (OutputStream) -> Int): Int {
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas/$safeSession").apply { mkdirs() }
        val target = File(root, name)
        check(!target.exists()) { "$name já existe em Download/Omegas; parte publicada não é reescrita" }
        val tmp = File(root, ".$name.tmp")
        val count = FileOutputStream(tmp).use { write(it) }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = false); tmp.delete() }
        return count
    }

    @Synchronized
    fun publishRootFile(source: File, subFolder: String = ""): JSONObject {
        val folder = subFolder.trim('/')
        require(!folder.contains("..")) { "Pasta inválida" }
        val publicDir = if (folder.isEmpty()) PUBLIC_ROOT else "$PUBLIC_ROOT/$folder"
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) syncScopedAt(source, "$publicDir/")
            else syncLegacyAt(
                source,
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    if (folder.isEmpty()) "Omegas" else "Omegas/$folder",
                ).apply { mkdirs() },
            )
            JSONObject()
                .put("ok", true)
                .put("published", true)
                .put("path", "$publicDir/${source.name}")
                .put("relativeRoot", publicDir)
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
        .put("usageBytes", usageBytes)
        .put("usageWarning", usageWarning)
        .put("prunedSessions", prunedSessions)

    /**
     * Teto do espelho (MirrorRetention): mede o que o app guardou em Download/Omegas, avisa a partir de 3 GB e,
     * só acima de 6 GB, tira as sessões mais antigas (nunca as 12 mais novas nem a que acabou de sair).
     * No máximo a cada 10 min; qualquer falha é ignorada (publicar nunca depende disto).
     */
    private fun enforceRetention(justPublished: String) {
        val now = System.currentTimeMillis()
        if (lastRetentionAtMs != 0L && now - lastRetentionAtMs < 10 * 60_000L) return
        lastRetentionAtMs = now
        try {
            val entries = mirrorEntries()
            usageBytes = MirrorRetention.totalBytes(entries)
            usageWarning = MirrorRetention.warning(entries).orEmpty()
            val doomed = MirrorRetention.sessionsToDelete(entries, protect = setOf(justPublished))
            for (session in doomed) {
                if (deleteMirrorSession(session)) prunedSessions += 1
            }
            if (doomed.isNotEmpty()) {
                val after = mirrorEntries()
                usageBytes = MirrorRetention.totalBytes(after)
                usageWarning = MirrorRetention.warning(after).orEmpty()
            }
        } catch (_: Exception) {
        }
    }

    private fun mirrorEntries(): List<MirrorRetention.Entry> {
        val out = ArrayList<MirrorRetention.Entry>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATE_ADDED,
            )
            context.contentResolver.query(
                collection, projection, MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?", arrayOf("$PUBLIC_ROOT/%/"), null,
            )?.use { cursor ->
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val session = cursor.getString(pathColumn).orEmpty()
                        .removePrefix("$PUBLIC_ROOT/").trim('/').substringBefore('/')
                    if (session.isBlank()) continue
                    out += MirrorRetention.Entry(session, cursor.getLong(sizeColumn), cursor.getLong(addedColumn) * 1_000L)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas")
            root.listFiles { file -> file.isDirectory }?.forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    out += MirrorRetention.Entry(dir.name, file.length(), file.lastModified())
                }
            }
        }
        return out
    }

    private fun deleteMirrorSession(session: String): Boolean {
        if (session != safeName(session)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.delete(
                collection, MediaStore.MediaColumns.RELATIVE_PATH + "=?", arrayOf("$PUBLIC_ROOT/$session/"),
            ) > 0
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas/$session").deleteRecursively()
        }
    }

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