package com.omegas.prohub.learning

/**
 * Transporta uma visão read-only e transitória das anchors PETROL do owner real
 * (`SignalLearningStore`) até o GasolineOracle durante a mesma chamada síncrona.
 *
 * Não armazena ciência, não persiste dados e não escolhe authority. O ThreadLocal
 * só evita que stores/threads paralelos contaminem a consulta; fora de `withAnchors`
 * a visão é sempre vazia.
 */
internal object NativePetrolPriorScope {
    private val active = ThreadLocal<List<NativeLearningAnchor>?>()

    fun <T> withAnchors(anchors: List<NativeLearningAnchor>, block: () -> T): T {
        if (anchors.isEmpty()) return block()
        val previous = active.get()
        active.set(anchors)
        return try {
            block()
        } finally {
            if (previous == null) active.remove() else active.set(previous)
        }
    }

    fun snapshot(): List<NativeLearningAnchor> = active.get().orEmpty()
}
