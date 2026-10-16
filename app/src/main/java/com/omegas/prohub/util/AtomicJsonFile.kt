package com.omegas.prohub.util

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Arquivo JSON à prova de queda de energia (a ignição corta a multimídia a qualquer momento); mesmo desenho do
 * MotorLearningMemory.writePersistedState. Escrita: `.tmp` + fsync, o arquivo atual vira a reserva `.bak` (só se parece
 * JSON inteiro: um arquivo quebrado nunca toma o lugar de uma reserva boa; ele vai para `.corrupt`) e o `.tmp` entra
 * por renomeação atômica. Leitura: o arquivo; se faltar, estiver vazio ou inválido, o `.bak`. Nunca apaga nada sozinho.
 */
object AtomicJsonFile {
    fun backupOf(target: File): File = File(target.parentFile, target.name + ".bak")

    /** Grava [text] em [target] com fsync, reserva e renomeação atômica. Falha de E/S sobe para quem chama decidir. */
    fun write(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (looksWhole(target)) move(target, backupOf(target))
        else if (target.isFile && target.length() > 0L) move(target, File(target.parentFile, target.name + ".corrupt"))
        move(tmp, target)
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
