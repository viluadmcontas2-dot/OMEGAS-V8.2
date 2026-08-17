package com.omegas.prohub.telemetry

data class LatestOnlyStateMetrics(
    val generation: Long,
    val published: Long,
    val replaced: Long,
    val rejected: Long,
)

/** Um único slot tipado; nunca vira fila. */
class LatestOnlyState<T>(
    private val sequenceOf: (T) -> Long,
    private val generationOf: (T) -> Long,
) {
    private val lock = Any()
    private var generation = 0L
    private var current: T? = null
    private var currentSequence = -1L
    private var published = 0L
    private var replaced = 0L
    private var rejected = 0L

    fun beginGeneration(newGeneration: Long) = synchronized(lock) {
        require(newGeneration > 0L) { "generation inválida" }
        if (generation != newGeneration) {
            generation = newGeneration
            current = null
            currentSequence = -1L
        }
    }

    fun clear() = synchronized(lock) {
        current = null
        currentSequence = -1L
    }

    fun publish(value: T): Boolean = synchronized(lock) {
        val valueGeneration = generationOf(value)
        val sequence = sequenceOf(value)
        if (generation <= 0L || valueGeneration != generation || sequence < 0L || sequence <= currentSequence) {
            rejected += 1
            return@synchronized false
        }
        if (current != null) replaced += 1
        current = value
        currentSequence = sequence
        published += 1
        true
    }

    fun current(): T? = synchronized(lock) { current }

    fun metrics(): LatestOnlyStateMetrics = synchronized(lock) {
        LatestOnlyStateMetrics(generation, published, replaced, rejected)
    }
}
