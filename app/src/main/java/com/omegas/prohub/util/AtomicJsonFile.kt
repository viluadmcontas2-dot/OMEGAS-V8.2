package com.omegas.prohub.util

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Arquivo JSON à prova de queda de energia (a ignição corta a multimídia a qualquer momento); mesmo desenho do
 * MotorLearningMemory.writePersistedState. Escrita: temporário com fsync, reserva opcional `.bak` do arquivo atual (só se parece
 * JSON inteiro: um arquivo quebrado nunca toma o lugar de uma reserva boa; ele vai para `.corrupt`) e o `.tmp` entra
 * por renomeação atômica. Leitura: o arquivo; se faltar, estiver vazio ou inválido, o `.bak`. Nunca apaga nada sozinho.
 */
object AtomicJsonFile {
    fun backupOf(target: File): File = File(target.parentFile, target.name + ".bak")

    /** Grava [text] em [target] com fsync e substituição atômica. Falhas de E/S sobem ao chamador. */
    fun write(target: File, text: String, keepBackup: Boolean = true) {
        val directory = target.absoluteFile.parentFile
        directory.mkdirs()
        val tmp = File.createTempFile(".atomic-", ".tmp", directory)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (keepBackup) {
                when {
                    looksWhole(target) -> preserve(target, backupOf(target), directory)
                    target.isFile && target.length() > 0L ->
                        preserve(target, File(directory, target.name + ".corrupt"), directory)
                }
            }
            move(tmp, target)
        } finally {
            tmp.delete()
        }
    }

    private fun preserve(target: File, destination: File, directory: File) {
        val tmp = File.createTempFile(".atomic-", ".tmp", directory)
        try {
            FileOutputStream(tmp).use { out ->
                target.inputStream().use { it.copyTo(out) }
                out.fd.sync()
            }
            move(tmp, destination)
        } finally {
            tmp.delete()
        }
    }

    /** JSON do arquivo; se ele faltar, estiver vazio ou inválido, o do `.bak`; null quando nenhum dos dois serve. */
    fun readJson(target: File): JSONObject? = (load(target) ?: load(backupOf(target)))?.second

    /** O mesmo que [readJson], como o texto gravado. */
    fun read(target: File): String? = (load(target) ?: load(backupOf(target)))?.first

    private fun load(file: File): Pair<String, JSONObject>? = try {
        val text = file.takeIf { it.isFile && it.length() > 0L }?.readText(Charsets.UTF_8) ?: return null
        text to JSONObject(text)
    } catch (_: Exception) { null }

    /** Começa com `{` e termina com `}`: inteiro o bastante para servir de reserva, sem reler o arquivo todo. */
    private fun looksWhole(file: File): Boolean = try {
        file.isFile && file.length() >= 2L && RandomAccessFile(file, "r").use { raf ->
            val first = raf.read()
            raf.seek(raf.length() - 1)
            first == '{'.code && raf.read() == '}'.code
        }
    } catch (_: Exception) { false }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
