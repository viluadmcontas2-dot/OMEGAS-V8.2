package com.omegas.prohub.storage

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

/** Gravação em etapas que ficou pela metade. [plan] é texto livre do chamador (nós da curva, resumo do Mapa K). */
internal data class PendingWrite(val id: String, val steps: List<String>, val done: List<String>, val plan: String) {
    val missing: List<String> get() = steps.filter { it !in done }
}

/** Onde o diário guarda o texto. Trocável nos testes. */
internal interface JournalStore {
    fun read(): String?
    fun write(text: String)
}

/** O estado existe, mas não pode ser interpretado com segurança. */
internal class CorruptWriteJournalException(cause: Throwable? = null) : IllegalStateException(
    "Diário de gravação corrompido; não é seguro assumir que não há pendência.",
    cause,
)

/**
 * Escrita atômica: grava em arquivo temporário e troca o destino sem apagá-lo antes.
 * Se a troca atômica não for suportada, tenta substituição normal; falhas de I/O são propagadas.
 * Um temporário que sobrar após falha é ignorado pela leitura.
 */
internal class FileJournalStore(private val file: File) : JournalStore {
    override fun read(): String? {
        val path = file.absoluteFile.toPath()
        return try {
            Files.newInputStream(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override fun write(text: String) {
        val target = file.absoluteFile
        val parent = target.parentFile ?: throw IllegalStateException("Diário sem diretório pai")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IllegalStateException("Não foi possível criar o diretório do diário")
        }
        val tmp = File(parent, target.name + ".tmp")
        tmp.writeText(text)
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Diário de intenção: open ANTES da primeira etapa, mark DEPOIS de cada etapa conferida, close no fim.
 * Uma entrada aberta ao reabrir = gravação interrompida. Nunca retoma sozinho: só informa; o toque do operador
 * dispara a etapa e a central é relida antes (valor guardado nunca vale como estado). Sem relógio.
 */
internal class WriteJournal(private val store: JournalStore) {
    @Synchronized fun open(id: String, steps: List<String>, plan: String) {
        require(id.isNotBlank() && steps.isNotEmpty()) { "id e etapas são obrigatórios" }
        save(PendingWrite(id, steps, emptyList(), plan))
    }

    /** Marca a etapa como concluída. Só vale com leitura de volta igual ([readbackOk]); senão não marca. */
    @Synchronized fun mark(id: String, step: String, readbackOk: Boolean): Boolean {
        val p = load() ?: return false
        if (p.id != id || step !in p.steps || !readbackOk) return false
        if (step !in p.done) save(p.copy(done = p.done + step))
        return true
    }

    @Synchronized fun close(id: String) {
        val p = load() ?: return
        if (p.id == id) store.write("")
    }

    /** Null significa arquivo ausente/vazio ou todas as etapas concluídas; corrupção ou falha de leitura lança erro. */
    @Synchronized fun pending(): PendingWrite? {
        val p = load() ?: return null
        return if (p.missing.isEmpty()) null else p
    }

    private fun save(p: PendingWrite) {
        store.write(listOf("J1", enc(p.id), p.steps.joinToString(",") { enc(it) }, p.done.joinToString(",") { enc(it) }, enc(p.plan), "END").joinToString("|"))
    }

    private fun load(): PendingWrite? {
        val raw = store.read() ?: return null
        if (raw.isEmpty()) return null
        return try {
            val f = raw.trim().split("|")
            require(f.size == 6 && f[0] == "J1" && f[5] == "END")
            val id = dec(f[1])
            val steps = list(f[2])
            val done = list(f[3])
            require(id.isNotBlank() && steps.isNotEmpty() && done.all { it in steps })
            PendingWrite(id, steps, done, dec(f[4]))
        } catch (error: Exception) {
            throw CorruptWriteJournalException(error)
        }
    }

    private fun list(s: String) = if (s.isEmpty()) emptyList() else s.split(",").map { dec(it) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
