package com.omegas.prohub.diagnostics

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Arquivo das sessões e cópias da Curva K no armazenamento PRIVADO do app (outros apps não leem).
 * JVM puro (testável sem Android). Quem quer o arquivo fora do app usa "Exportar" (um toque, o dono
 * escolhe o destino). Toda escrita é atômica (temporário oculto + renomear) e nenhum caminho sai da raiz.
 */
class PrivateSessionArchive(root: File) {
    companion object {
        fun safeName(raw: String): String =
            raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").take(96).ifBlank { "session" }

        /** Arquivos da sessão (sem marcadores ocultos), em ordem estável. */
        fun sessionFiles(sessionDir: File): List<File> = sessionDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.relativeTo(sessionDir).path }
            .toList()
    }

    data class MigrationReport(val moved: Int, val failed: Int, val lastError: String)

    private val root: File = root.canonicalFile

    /** Caminho relativo → arquivo dentro da raiz, ou null se for vazio, absoluto ou tentar sair dela. */
    fun resolveInside(relativePath: String): File? {
        val clean = relativePath.trim()
        if (clean.isEmpty() || clean.startsWith("/") || clean.startsWith("\\")) return null
        if (clean.split('/', '\\').any { it == ".." }) return null
        val target = File(root, clean).canonicalFile
        return if (target.path.startsWith(root.path + File.separator)) target else null
    }

    /** Grava uma parte inteira (ex.: ZIP da sessão). Mesmo nome = mesmo arquivo; falha no meio mantém a versão anterior. */
    fun writePart(safeSession: String, name: String, write: (OutputStream) -> Int): Int {
        val target = requireNotNull(resolveInside("$safeSession/$name")) { "Nome de arquivo inválido" }
        return atomicWrite(target) { write(it) }
    }

    /** Copia um arquivo solto para a raiz. `events_*.jsonl` só cresce: acrescenta o que falta; o resto é regravado. */
    fun copyRootFile(source: File) {
        require(source.isFile) { "Arquivo local não encontrado" }
        val target = requireNotNull(resolveInside(source.name)) { "Nome de arquivo inválido" }
        require(target.parentFile == root) { "Nome de arquivo inválido" }
        if (isAppendable(source) && target.isFile && target.length() <= source.length()) {
            val offset = target.length()
            if (offset == source.length()) return
            FileInputStream(source).use { input ->
                skipFully(input, offset)
                FileOutputStream(target, true).use { output -> input.copyTo(output, 64 * 1024) }
            }
            return
        }
        atomicWrite(target) { out -> source.inputStream().use { it.copyTo(out, 64 * 1024) }.toInt() }
    }

    /**
     * Traz uma cópia antiga (pasta pública) para dentro. Só vale se o tamanho bater; já importada com o
     * mesmo tamanho = confirmada sem reabrir. Nada parcial fica para trás.
     */
    fun importVerified(relativePath: String, expectedSize: Long, open: () -> InputStream?): Boolean {
        val target = resolveInside(relativePath) ?: return false
        if (expectedSize >= 0 && target.isFile && target.length() == expectedSize) return true
        return try {
            val written = atomicWrite(target, verify = { tmp -> expectedSize < 0 || tmp.length() == expectedSize }) { out ->
                (open() ?: error("Origem indisponível")).use { it.copyTo(out, 64 * 1024) }.toInt()
            }
            written >= 0
        } catch (_: Exception) {
            false // origem ilegível ou truncada: a antiga fica onde está e tenta de novo na próxima abertura
        }
    }

    /** Move a pasta antiga (Android ≤ 9: Download/Omegas) para dentro: copia, confere e só então apaga a origem. */
    fun migrateDirectory(legacyRoot: File): MigrationReport {
        if (!legacyRoot.isDirectory) return MigrationReport(0, 0, "")
        var moved = 0
        var failed = 0
        var lastError = ""
        legacyRoot.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.path }
            .forEach { file ->
                val relative = file.relativeTo(legacyRoot).invariantSeparatorsPath
                val ok = importVerified(relative, file.length()) { FileInputStream(file) }
                if (ok && file.delete()) moved += 1
                else { failed += 1; lastError = "Não consegui mover $relative" }
            }
        // Pastas que ficaram vazias saem; as com arquivo que falhou ficam.
        legacyRoot.walkBottomUp().filter { it.isDirectory && it != legacyRoot }.forEach { dir ->
            if (dir.list()?.isEmpty() == true) dir.delete()
        }
        return MigrationReport(moved, failed, lastError)
    }

    private fun atomicWrite(
        target: File,
        verify: (File) -> Boolean = { true },
        write: (OutputStream) -> Int,
    ): Int {
        val parent = requireNotNull(target.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) error("Não consegui criar a pasta do arquivo")
        val tmp = File(parent, ".${target.name}.tmp")
        try {
            val count = FileOutputStream(tmp).use { out ->
                val n = write(out)
                out.fd.sync()
                n
            }
            check(verify(tmp)) { "Cópia incompleta" }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return count
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun isAppendable(file: File): Boolean =
        file.name.startsWith("events_") && file.name.endsWith(".jsonl")

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
