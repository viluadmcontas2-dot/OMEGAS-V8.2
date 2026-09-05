package com.omegas.prohub.diagnostics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fail-safe promotion from the fast private session spool to a human-owned vault.
 * The private spool is never deleted by this class, even after a successful copy.
 */
class SessionVault(
    private val context: Context,
    private val paths: AppPaths,
    private val settings: AppSettings,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-session-vault").apply { isDaemon = true }
    }

    fun persistTreePermission(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.sessionVaultTreeUri = uri.toString()
        true
    } catch (_: Exception) {
        false
    }

    fun promoteAsync(sessionDir: File) {
        if (!sessionDir.isDirectory) return
        executor.execute { promote(sessionDir) }
    }

    fun close() = executor.shutdown()

    private fun promote(sessionDir: File) {
        val statusFile = File(sessionDir, "vault_status.json")
        val temporaryZip = File(paths.tempRoot, "${sessionDir.name}.zip.tmp")
        try {
            zipDirectory(sessionDir, temporaryZip)
            val destination = promoteToPersistedTree(temporaryZip, sessionDir.name)
                ?: promoteToDocuments(temporaryZip, sessionDir.name)
                ?: error("Nenhum destino público de sessão disponível")
            statusFile.writeText(
                JSONObject()
                    .put("ok", true)
                    .put("state", "PROMOTED")
                    .put("destination", destination.toString())
                    .put("promotedAtMs", System.currentTimeMillis())
                    .put("spoolPreserved", true)
                    .toString(2),
                Charsets.UTF_8,
            )
        } catch (error: Exception) {
            statusFile.writeText(
                JSONObject()
                    .put("ok", false)
                    .put("state", "PENDING")
                    .put("error", error.message ?: error.javaClass.simpleName)
                    .put("spoolPreserved", true)
                    .toString(2),
                Charsets.UTF_8,
            )
        } finally {
            temporaryZip.delete()
        }
    }

    private fun promoteToPersistedTree(zip: File, sessionName: String): Uri? {
        val raw = settings.sessionVaultTreeUri
        if (raw.isBlank()) return null
        return try {
            val tree = Uri.parse(raw)
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            var parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            parent = findOrCreateDirectory(tree, parent, "OMEGAS") ?: return null
            parent = findOrCreateDirectory(tree, parent, "Sessions") ?: return null
            val target = DocumentsContract.createDocument(
                resolver,
                parent,
                "application/zip",
                "$sessionName.zip",
            ) ?: return null
            resolver.openOutputStream(target, "w")?.use { output ->
                FileInputStream(zip).use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun findOrCreateDirectory(tree: Uri, parent: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )
    }

    private fun promoteToDocuments(zip: File, sessionName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "OMEGAS/Sessions")
            if (!root.exists() && !root.mkdirs()) return null
            val target = File(root, "$sessionName.zip")
            FileInputStream(zip).use { input -> FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) } }
            return Uri.fromFile(target)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$sessionName.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/OMEGAS/Sessions")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(target, "w")?.use { output ->
                FileInputStream(zip).use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            target
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }

    private fun zipDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            source.walkTopDown()
                .filter { it.isFile && it.name != "vault_status.json" }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    FileInputStream(file).use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
        }
    }
}
