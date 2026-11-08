package com.omegas.prohub.storage

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
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

/**
 * Persistência fail-closed:
 * - só NoSuchFileException significa "ainda não existe";
 * - falhas de leitura/escrita são propagadas;
 * - substituição é feita por rename atômico no mesmo diretório, sem apagar o destino antes.
 * Se o filesystem não suportar ATOMIC_MOVE, a gravação falha e o diário anterior permanece.
 */
internal class FileJournalStore(
    private val file: File,
    private val atomicMove: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) : JournalStore {
    override fun read(): String? = try {
        String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)
    } catch (_: NoSuchFileException) {
        null
    }

    override fun write(text: String) {
        val target = file.toPath().toAbsolutePath()
        val parent = target.parent ?: throw IOException("O diário precisa de um diretório pai")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".omegas-journal-", ".tmp")
        try {
            Files.write(temporary, text.toByteArray(Charsets.UTF_8))
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            // O destino nunca é removido como fallback. Se a troca falhar, preserva-se o diário antigo.
            try { Files.deleteIfExists(temporary) } catch (_: IOException) { /* temporário órfão é ignorado */ }
        }
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
        check(load() == null) { "Há uma gravação anterior ainda não encerrada" }
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
        if (p.id == id) {
            check(p.missing.isEmpty()) { "Não é possível fechar uma gravação com etapas sem leitura de volta" }
            store.write("")
        }
    }

    /** Pendência aberta ou null somente quando o diário está realmente ausente, vazio ou concluído. */
    @Synchronized fun pending(): PendingWrite? {
        val p = load() ?: return null
        return if (p.missing.isEmpty()) null else p
    }

    private fun save(p: PendingWrite) {
        store.write(listOf("J1", enc(p.id), p.steps.joinToString(",") { enc(it) }, p.done.joinToString(",") { enc(it) }, enc(p.plan), "END").joinToString("|"))
    }

    /** Conteúdo não vazio inválido e falhas de I/O nunca são convertidos em "sem pendência". */
    private fun load(): PendingWrite? {
        val stored = store.read() ?: return null
        if (stored.isEmpty()) return null
        val raw = stored.trim()
        val fields = raw.split("|")
        check(fields.size == 6 && fields[0] == "J1" && fields[5] == "END") {
            "Diário de gravação corrompido; preservar e bloquear novas gravações"
        }
        val pending = PendingWrite(dec(fields[1]), list(fields[2]), list(fields[3]), dec(fields[4]))
        check(pending.id.isNotBlank() && pending.steps.isNotEmpty() && pending.done.all { it in pending.steps }) {
            "Diário de gravação inconsistente; preservar e bloquear novas gravações"
        }
        return pending
    }

    private fun list(s: String) = if (s.isEmpty()) emptyList() else s.split(",").map { dec(it) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
