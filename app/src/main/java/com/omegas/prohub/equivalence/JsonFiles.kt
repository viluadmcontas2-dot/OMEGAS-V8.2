package com.omegas.prohub.equivalence

import org.json.JSONObject
import java.io.File

/** Escrita atômica (.tmp + renameTo) e leitura tolerante dos arquivos do cérebro. */
internal object JsonFiles {
    /** Verdadeiro se gravou; falha de disco nunca sobe como exceção. */
    fun write(target: File?, text: String): Boolean {
        if (target == null) return true
        return try {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** JSON do arquivo, ou nulo se não existe, está corrompido ou tem outro formato. */
    fun read(source: File?, format: String): JSONObject? {
        val file = source?.takeIf { it.isFile } ?: return null
        return try {
            JSONObject(file.readText()).takeIf { it.optString("format") == format }
        } catch (_: Exception) {
            null
        }
    }
}
