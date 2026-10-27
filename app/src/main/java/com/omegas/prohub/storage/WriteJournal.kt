package com.omegas.prohub.storage

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/** Gravação em etapas que ficou pela metade. [plan] é texto livre do chamador (nós da curva, resumo do Mapa K). */
internal data class PendingWrite(val id: String, val steps: List<String>, val done: List<String>, val plan: String) {
    val missing: List<String> get() = steps.filter { it !in done }
}

/** Onde o diário guarda o texto. Trocável nos testes. */
internal interface JournalStore {
    fun read(): String?
    fun write(text: String)
}

/** Escrita atômica: grava em `.tmp` e renomeia. `.tmp` sobrando é ignorado. */
internal class FileJournalStore(private val file: File) : JournalStore {
    override fun read(): String? = try { if (file.exists()) file.readText() else null } catch (_: Exception) { null }
    override fun write(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        file.parentFile?.mkdirs()
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

/**
 * Diário de intenção: `open` ANTES da primeira etapa, `mark` DEPOIS de cada etapa conferida, `close` no fim.
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

    /** Pendência aberta ou null (arquivo vazio, truncado ou corrompido = sem pendência). */
    @Synchronized fun pending(): PendingWrite? {
        val p = load() ?: return null
        return if (p.missing.isEmpty()) null else p
    }

    private fun save(p: PendingWrite) {
        store.write(listOf("J1", enc(p.id), p.steps.joinToString(",") { enc(it) }, p.done.joinToString(",") { enc(it) }, enc(p.plan), "END").joinToString("|"))
    }

    private fun load(): PendingWrite? = try {
        val raw = store.read()?.trim()
        if (raw.isNullOrEmpty()) null else {
            val f = raw.split("|")
            if (f.size != 6 || f[0] != "J1" || f[5] != "END") null
            else PendingWrite(dec(f[1]), list(f[2]), list(f[3]), dec(f[4]))
        }
    } catch (_: Exception) { null }

    private fun list(s: String) = if (s.isEmpty()) emptyList() else s.split(",").map { dec(it) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
