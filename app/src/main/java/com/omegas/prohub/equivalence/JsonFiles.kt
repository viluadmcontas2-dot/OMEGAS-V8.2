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
            writeAtomic(target, text)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val tmpCounter = java.util.concurrent.atomic.AtomicLong()

    /**
     * .tmp único por escrita (duas escritas nunca se intercalam no mesmo arquivo) + renameTo. Se o rename falha,
     * guarda o último arquivo bom em `.bak` antes da escrita direta; a leitura cai no `.bak` se o principal
     * estiver corrompido. Lança em falha de disco (o chamador decide).
     */
    fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + "." + System.nanoTime() + "." + tmpCounter.incrementAndGet() + ".tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                if (target.isFile) {
                    try { target.copyTo(File(target.parentFile, target.name + ".bak"), overwrite = true) } catch (_: Exception) {}
                }
                target.writeText(text)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Texto do arquivo principal e, se faltar ou não for JSON válido, o do `.bak`. */
    fun readJsonWithBak(source: File?, accept: (JSONObject) -> Boolean = { true }): JSONObject? {
        if (source == null) return null
        for (candidate in listOf(source, File(source.parentFile, source.name + ".bak"))) {
            if (!candidate.isFile) continue
            try {
                val root = JSONObject(candidate.readText())
                if (accept(root)) return root
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** JSON do arquivo, ou nulo se não existe, está corrompido ou tem outro formato. */
    fun read(source: File?, format: String): JSONObject? {
        return readJsonWithBak(source) { it.optString("format") == format }
    }
}
