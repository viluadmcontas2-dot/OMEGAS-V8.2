package com.omegas.prohub.diagnostics

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

/**
 * Cópia durável das sessões OMEGAS e das cópias manuais da Curva K, no armazenamento PRIVADO do app.
 *
 * Privacidade (revisão 2026-10-20): antes ia para `Download/Omegas`, pasta pública que qualquer app com
 * acesso a arquivos lia (dados do carro, horários, GPS). Agora só o OMEGAS lê; para levar a sessão para
 * fora, o dono toca "Exportar" e escolhe o destino (Drive, cabo, e-mail). Cada sessão continua em partes
 * ZIP imutáveis (`Omegas/<sessão>/<sessão>_parte_NNNN.zip`, ver SessionPartPlanner) e as cópias antigas
 * da pasta pública são trazidas para dentro sem perda (copia, confere o tamanho, só então apaga a pública).
 * A captura continua do SessionRecorder; esta classe não cria polling, captura paralela nem pruning.
 */
class DocumentsSessionMirror(private val context: Context) {
    companion object {
        /** Pasta dentro do armazenamento privado do app (não é Download/ nem outra pasta pública). */
        const val PRIVATE_ROOT = "Omegas"
        private const val LEGACY_DOWNLOAD_ROOT = "Download/Omegas"

        fun safeName(raw: String): String = PrivateSessionArchive.safeName(raw)

        fun sessionFiles(sessionDir: File): List<File> = PrivateSessionArchive.sessionFiles(sessionDir)
    }

    /**
     * Android 10+: pasta do app no armazenamento externo (`Android/data/<app>/files`, isolada dos outros
     * apps pelo próprio Android; cabe mais que a interna). Android ≤ 9 ali outros apps ainda leem, então
     * usa a interna (`filesDir`).
     */
    private val rootDir: File by lazy {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getExternalFilesDir(null) ?: context.filesDir
        else context.filesDir
        File(base, PRIVATE_ROOT).apply { mkdirs() }
    }
    private val archive: PrivateSessionArchive by lazy { PrivateSessionArchive(rootDir) }

    @Volatile private var lastSyncAtMs = 0L
    @Volatile private var lastSyncOk = true
    @Volatile private var lastError = ""
    @Volatile private var lastSessionId = ""
    @Volatile private var lastFileCount = 0
    @Volatile private var migratedCount = 0

    /** Grava UMA parte imutável da sessão. Mesma parte de novo (queda entre gravar e registrar) = mesmo arquivo. */
    @Synchronized
    fun publishPart(sessionId: String, plan: SessionPartPlanner.Plan): JSONObject {
        return try {
            val safeSession = safeName(sessionId)
            val name = plan.fileName(sessionId)
            val count = archive.writePart(safeSession, name) { SessionPartPlanner.writeZip(plan, sessionId, it) }
            lastSyncAtMs = System.currentTimeMillis()
            lastSyncOk = true
            lastError = ""
            lastSessionId = safeSession
            lastFileCount = count
            statusObject().put("ok", true).put("path", "$PRIVATE_ROOT/$safeSession/$name").put("part", plan.part)
        } catch (error: Exception) {
            fail(sessionId, error.message ?: error.javaClass.simpleName)
        }
    }

    /** Pacote leve (resumo + série + manifesto + livro-caixa) ao lado das partes: pequeno, baixa sem estourar o Drive. Mesmo nome = mesmo arquivo. */
    @Synchronized
    fun publishSummaryPackage(sessionId: String, sessionDir: File, ledger: File?): JSONObject = try {
        val safeSession = safeName(sessionId)
        val name = SessionSummaryFiles.packageName(sessionId)
        val count = archive.writePart(safeSession, name) { SessionSummaryFiles.packageZip(sessionDir, ledger, it).toInt() }
        JSONObject().put("ok", true).put("path", "$PRIVATE_ROOT/$safeSession/$name").put("count", count)
    } catch (error: Exception) {
        JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName)
    }

    /** Cópia manual da Curva K (e outros arquivos soltos) na pasta privada. */
    @Synchronized
    fun publishRootFile(source: File): JSONObject {
        if (!source.isFile) return JSONObject().put("ok", false).put("error", "Arquivo local não encontrado")
        return try {
            require(File(source.name).name == source.name && !source.name.contains("..")) { "Nome de arquivo inválido" }
            archive.copyRootFile(source)
            JSONObject()
                .put("ok", true)
                .put("published", true)
                .put("path", "$PRIVATE_ROOT/${source.name}")
                .put("relativeRoot", PRIVATE_ROOT)
                .put("private", true)
                .put("survivesAppDataClear", false)
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", error.message ?: error.javaClass.simpleName)
                .put("relativeRoot", PRIVATE_ROOT)
        }
    }

    /**
     * Traz para dentro o que versões antigas deixaram em `Download/Omegas`. Copia, confere o tamanho e só
     * então apaga a pública; o que falhar fica onde está e tenta de novo na próxima abertura. Sem permissão
     * (Android ≤ 9) não faz nada: nada é perdido.
     */
    @Synchronized
    fun migrateLegacyPublicCopies(): JSONObject {
        var moved = 0
        var failed = 0
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val r = migrateScoped(); moved = r.first; failed = r.second
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                val legacy = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Omegas")
                val report = archive.migrateDirectory(legacy)
                moved = report.moved; failed = report.failed
                if (report.lastError.isNotEmpty()) lastError = report.lastError
            }
        } catch (error: Exception) {
            failed += 1
            lastError = "Cópias antigas ficaram em $LEGACY_DOWNLOAD_ROOT: ${error.message ?: error.javaClass.simpleName}"
        }
        migratedCount += moved
        return JSONObject().put("ok", failed == 0).put("moved", moved).put("failed", failed)
    }

    /** Android 10+: só enxerga os arquivos que o próprio OMEGAS criou em Download/Omegas (sem permissão extra). */
    private fun migrateScoped(): Pair<Int, Int> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )
        val selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?"
        val args = arrayOf("$LEGACY_DOWNLOAD_ROOT/%")
        var moved = 0
        var failed = 0
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                val folder = cursor.getString(pathCol).orEmpty().removePrefix("$LEGACY_DOWNLOAD_ROOT/").trimEnd('/')
                val name = cursor.getString(nameCol).orEmpty()
                if (name.isEmpty() || name.startsWith(".")) continue
                val relative = if (folder.isEmpty()) name else "$folder/$name"
                val ok = archive.importVerified(relative, cursor.getLong(sizeCol)) { resolver.openInputStream(uri) }
                if (ok && resolver.delete(uri, null, null) > 0) moved += 1
                else { failed += 1; lastError = "Não consegui mover $relative de $LEGACY_DOWNLOAD_ROOT" }
            }
        }
        return moved to failed
    }

    fun statusObject(): JSONObject = JSONObject()
        .put("available", true)
        .put("lastSyncOk", lastSyncOk)
        .put("lastSyncAtMs", lastSyncAtMs)
        .put("lastError", lastError)
        .put("lastSessionId", lastSessionId)
        .put("lastFileCount", lastFileCount)
        .put("relativeRoot", PRIVATE_ROOT)
        .put("private", true)
        .put("survivesAppDataClear", false)
        .put("migratedFromPublic", migratedCount)
        .put("automatic", true)

    private fun fail(sessionId: String, message: String): JSONObject {
        lastSyncAtMs = System.currentTimeMillis()
        lastSyncOk = false
        lastError = message
        lastSessionId = safeName(sessionId)
        return statusObject().put("ok", false).put("error", message)
    }
}
