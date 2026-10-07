package com.omegas.prohub.diagnostics

/**
 * Teto de espaço das sessões gravadas. Função pura: decide quais pastas apagar para caber no teto.
 * Sem o espelho em Documentos nenhuma outra regra poda, e o armazenamento do app encheria.
 */
internal object SessionByteCap {
    const val TOTAL_BYTES_CAP = 1_536L * 1024L * 1024L

    /** Só acima disto uma sessão SEM cópia pública confirmada pode sair (o aparelho ficaria sem espaço). */
    const val EMERGENCY_TOTAL_BYTES_CAP = 3L * 1024L * 1024L * 1024L

    data class Info(
        val name: String,
        val bytes: Long,
        val modifiedAtMs: Long,
        /** Gravando agora ou na fila/no meio da publicação: nunca é apagada. */
        val active: Boolean,
        /** Tem `.documents_mirrored` (ou não há espelho): a cópia pública existe. */
        val published: Boolean = true,
    )

    /**
     * Do mais antigo ao mais novo, até o total caber em [capBytes], apagando só sessões publicadas e
     * inativas. Sessão sem cópia pública só sai se o total ainda passar de [emergencyCapBytes].
     */
    fun select(
        entries: List<Info>,
        capBytes: Long,
        emergencyCapBytes: Long = EMERGENCY_TOTAL_BYTES_CAP,
    ): List<String> {
        var total = entries.sumOf { it.bytes }
        if (total <= capBytes) return emptyList()
        val doomed = mutableListOf<String>()
        val candidates = entries.filter { !it.active }.sortedBy { it.modifiedAtMs }
        for (entry in candidates.filter { it.published }) {
            if (total <= capBytes) break
            doomed += entry.name
            total -= entry.bytes
        }
        for (entry in candidates.filter { !it.published }) {
            if (total <= emergencyCapBytes) break
            doomed += entry.name
            total -= entry.bytes
        }
        return doomed
    }
}

/**
 * Id único de sessão mesmo para duas sessões no mesmo segundo (queda e volta rápida do USB):
 * milissegundos (já no carimbo) + contador do processo. O id continua começando com a data
 * (yyyy-MM-dd_HH-mm...), que a exportação usa para nomear o arquivo.
 */
internal object SessionIdFormat {
    fun build(stamp: String, deviceId: String, counter: Long): String =
        "session_${stamp}_n${counter.toString().padStart(3, '0')}_${deviceId.take(8)}"
}
