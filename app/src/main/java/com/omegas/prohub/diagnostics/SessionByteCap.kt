package com.omegas.prohub.diagnostics

/**
 * Teto de espaço das sessões gravadas. Função pura: decide quais pastas apagar para caber no teto.
 * Sem o espelho em Documentos nenhuma outra regra poda, e o armazenamento do app encheria.
 */
internal object SessionByteCap {
    const val TOTAL_BYTES_CAP = 1_536L * 1024L * 1024L

    data class Info(
        val name: String,
        val bytes: Long,
        val modifiedAtMs: Long,
        /** A sessão que está gravando agora nunca é apagada. */
        val active: Boolean,
    )

    /** Do mais antigo ao mais novo, só sessões fechadas, até o total caber em [capBytes]. */
    fun select(entries: List<Info>, capBytes: Long): List<String> {
        var total = entries.sumOf { it.bytes }
        if (total <= capBytes) return emptyList()
        val doomed = mutableListOf<String>()
        for (entry in entries.filter { !it.active }.sortedBy { it.modifiedAtMs }) {
            if (total <= capBytes) break
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
