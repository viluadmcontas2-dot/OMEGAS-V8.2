package com.omegas.prohub.learning

/**
 * Orçamento do estado científico principal do Learning.
 *
 * As médias, pesos, contagens e comparações são a memória científica e nunca são
 * descartadas apenas para caber em bytes. O que pode ser compactado é a lista
 * textual de IDs históricos já resumidos por essas estatísticas.
 */
internal object LearningMemoryBudget {
    const val MAX_REGION_VISIT_IDS = 16
    const val MAX_REGION_SESSION_IDS = 8
    const val TARGET_PERSISTED_BYTES = 5 * 1024 * 1024
    const val POLICY = "BOUNDED_PROVENANCE_EXACT_COUNTS_V1"

    val provenanceLevels: List<Pair<Int, Int>> = listOf(
        MAX_REGION_VISIT_IDS to MAX_REGION_SESSION_IDS,
        8 to 4,
        4 to 2,
        0 to 0,
    )

    fun retainNewestIds(values: Iterable<String>, maxEntries: Int): LinkedHashSet<String> {
        if (maxEntries <= 0) return linkedSetOf()
        val normalized = linkedSetOf<String>()
        values.forEach { value -> value.takeIf { it.isNotBlank() }?.let(normalized::add) }
        if (normalized.size <= maxEntries) return normalized
        return normalized.toList().takeLast(maxEntries).toCollection(linkedSetOf())
    }

    fun trimNewestIds(values: MutableSet<String>, maxEntries: Int) {
        if (maxEntries <= 0) {
            values.clear()
            return
        }
        while (values.size > maxEntries) {
            val iterator = values.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}
