package com.omegas.prohub.learning

/**
 * Normaliza somente namespaces criados por restaurações internas do próprio app.
 * Identidades externas/desconhecidas permanecem intactas.
 */
internal object InternalLearningNamespace {
    const val PRESERVED_PETROL_SOURCE = "local-petrol-preserved"

    private val internalSources = listOf(
        "reset-audit",
        "policy-migration",
        "local-petrol-quarantine-recovery",
        PRESERVED_PETROL_SOURCE,
    )

    fun normalize(value: String): String {
        var current = value
        while (true) {
            val source = internalSources.firstOrNull { current.startsWith("$it:") } ?: return current
            current = current.removePrefix("$source:")
        }
    }
}
