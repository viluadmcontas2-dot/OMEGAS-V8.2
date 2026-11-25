package com.omegas.v7.runtime

import com.omegas.prohub.util.AtomicJsonFile
import java.io.File

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
        AtomicJsonFile.write(target, V7SessionSnapshotCodec.encode(state), keepBackup = false)
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
