package com.omegas.prohub.learning

/**
 * Orçamento explícito do sidecar derivado do Learning.
 *
 * O estado científico principal continua em MotorLearningMemory. Este orçamento
 * limita apenas evidência derivada/diagnóstica usada para confiança adaptativa,
 * contexto nativo e proveniência curta, impedindo crescimento indefinido do hot path.
 */
internal object LearningEvidenceBudget {
    const val MAX_NATIVE_SNAPSHOTS = 16
    const val MAX_NATIVE_ANCHORS = 256
    const val MAX_VISIT_ACCUMULATORS = 256
    const val MAX_PROVENANCE_ENTRIES = 64
    const val MAX_PERSISTED_BYTES = 256 * 1024

    fun <T> retainNewestSnapshotGroups(
        items: List<T>,
        snapshotId: (T) -> String,
        maxSnapshots: Int = MAX_NATIVE_SNAPSHOTS,
    ): List<T> {
        if (items.isEmpty() || maxSnapshots <= 0) return emptyList()
        val ids = linkedSetOf<String>()
        items.forEach { item -> snapshotId(item).takeIf { it.isNotBlank() }?.let(ids::add) }
        if (ids.size <= maxSnapshots) return items
        val keep = ids.toList().takeLast(maxSnapshots).toHashSet()
        return items.filter { snapshotId(it) in keep }
    }

    fun <T> retainNewestVisits(
        items: List<T>,
        lastSeenAt: (T) -> Long,
        maxEntries: Int = MAX_VISIT_ACCUMULATORS,
    ): List<T> {
        if (items.size <= maxEntries) return items
        if (maxEntries <= 0) return emptyList()
        return items.withIndex()
            .sortedWith(compareBy<IndexedValue<T>> { lastSeenAt(it.value) }.thenBy { it.index })
            .takeLast(maxEntries)
            .sortedBy { it.index }
            .map { it.value }
    }

    fun <T> retainNewestEntries(items: List<T>, maxEntries: Int = MAX_PROVENANCE_ENTRIES): List<T> {
        if (maxEntries <= 0) return emptyList()
        return if (items.size <= maxEntries) items else items.takeLast(maxEntries)
    }
}
