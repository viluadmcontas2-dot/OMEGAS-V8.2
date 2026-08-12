package com.omegas.v7.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Armazenamento explícito e atômico para arquivos de sessão nomeados. */
class V7SessionFileStore(
    private val directory: File,
) {
    init {
        if (!directory.exists()) require(directory.mkdirs()) { "Não foi possível criar diretório de sessões V7" }
        require(directory.isDirectory)
    }

    fun list(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension.equals("omegas7", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

    fun save(fileName: String, state: V7SessionState): File {
        val target = resolve(fileName)
        val temporary = File(directory, ".${target.name}.tmp")
        temporary.writeText(V7SessionSnapshotCodec.encode(state), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        return target
    }

    fun load(fileName: String): V7SessionState {
        val file = resolve(fileName)
        require(file.isFile) { "Sessão V7 não encontrada: ${file.name}" }
        return V7SessionSnapshotCodec.decode(file.readText(Charsets.UTF_8))
    }

    fun delete(fileName: String): Boolean = resolve(fileName).delete()

    private fun resolve(fileName: String): File {
        val clean = fileName.trim()
            .removeSuffix(".omegas7")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
        require(clean.isNotBlank()) { "Nome de sessão vazio" }
        val file = File(directory, "$clean.omegas7").canonicalFile
        require(file.parentFile == directory.canonicalFile) { "Nome de sessão inválido" }
        return file
    }
}
