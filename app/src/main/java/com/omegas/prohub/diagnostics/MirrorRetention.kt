package com.omegas.prohub.diagnostics

/**
 * Teto do espelho `Download/Omegas`. Puro: decide só QUAIS sessões saem; quem apaga é o DocumentsSessionMirror.
 *
 * O espelho guarda cada sessão num ZIP imutável e nunca era podado: depois de meses enchia o armazenamento do
 * aparelho. Regras (conservadoras, a evidência real é insubstituível):
 *  - aviso a partir de [WARN_BYTES];
 *  - só se passar de [MAX_BYTES] apaga, da sessão MAIS ANTIGA para a mais nova;
 *  - nunca apaga as [MIN_KEEP_SESSIONS] mais novas nem a sessão que acabou de ser publicada.
 */
internal object MirrorRetention {
    const val WARN_BYTES = 3L * 1024L * 1024L * 1024L
    const val MAX_BYTES = 6L * 1024L * 1024L * 1024L
    const val MIN_KEEP_SESSIONS = 12

    data class Entry(val session: String, val bytes: Long, val addedAtMs: Long)

    fun totalBytes(entries: List<Entry>): Long = entries.sumOf { it.bytes.coerceAtLeast(0L) }

    fun warning(entries: List<Entry>, warnBytes: Long = WARN_BYTES): String? {
        val total = totalBytes(entries)
        if (total < warnBytes) return null
        val mb = total / (1024L * 1024L)
        return "Download/Omegas ocupa $mb MB. As sessões mais antigas saem sozinhas acima de ${MAX_BYTES / (1024L * 1024L)} MB; " +
            "copie o que quiser guardar."
    }

    /** Nomes das sessões a apagar (mais antigas primeiro) para voltar ao teto, respeitando as proteções. */
    fun sessionsToDelete(
        entries: List<Entry>,
        protect: Set<String> = emptySet(),
        maxBytes: Long = MAX_BYTES,
        keepNewest: Int = MIN_KEEP_SESSIONS,
    ): List<String> {
        var total = totalBytes(entries)
        if (total <= maxBytes) return emptyList()
        val sessions = entries.groupBy { it.session }
            .map { (name, list) -> Triple(name, list.sumOf { it.bytes.coerceAtLeast(0L) }, list.maxOf { it.addedAtMs }) }
            .sortedBy { it.third }
        val newest = sessions.takeLast(keepNewest.coerceAtLeast(0)).map { it.first }.toSet()
        val out = ArrayList<String>()
        for ((name, bytes, _) in sessions) {
            if (total <= maxBytes) break
            if (name in newest || name in protect) continue
            out += name
            total -= bytes
        }
        return out
    }
}
