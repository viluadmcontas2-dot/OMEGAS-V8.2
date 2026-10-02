package com.omegas.prohub.learning

import kotlin.math.ceil

/**
 * Controls when an accepted rolling sample is allowed to mutate scientific state.
 *
 * Live/UI decisions may still update every frame. Scientific publication advances
 * only after enough new physical frames accumulated since the last publication,
 * preventing a full rolling window from becoming a new scientific event per frame.
 */
class SciencePublicationGate(
    private val minimumNoveltyFraction: Double = 0.75,
) {
    init {
        require(minimumNoveltyFraction in 0.50..1.0) { "Novelty fraction must be in 0.50..1.0" }
    }

    data class Decision(
        val publish: Boolean,
        val novelty: ContinuousWindowNovelty.Result,
    )

    private val representedThroughByKey = linkedMapOf<String, Long>()

    @Synchronized
    fun evaluate(
        key: String,
        startedAtElapsedMs: Long,
        endedAtElapsedMs: Long,
        frameCount: Int,
        medianIntervalMs: Long,
        forceBoundary: Boolean = false,
    ): Decision {
        val previous = representedThroughByKey[key]
        val novelty = ContinuousWindowNovelty.calculate(
            startedAtElapsedMs = startedAtElapsedMs,
            endedAtElapsedMs = endedAtElapsedMs,
            frameCount = frameCount,
            medianIntervalMs = medianIntervalMs,
            previouslyRepresentedThroughElapsedMs = previous,
        )
        val minimumNewFrames = ceil(frameCount * minimumNoveltyFraction).toInt().coerceIn(1, frameCount)
        val publish = !novelty.duplicate && (
            previous == null ||
                forceBoundary ||
                novelty.newFrames >= minimumNewFrames
        )
        if (publish) representedThroughByKey[key] = novelty.representedThroughElapsedMs
        return Decision(publish, novelty)
    }

    @Synchronized
    fun reset(key: String? = null) {
        if (key == null) representedThroughByKey.clear() else representedThroughByKey.remove(key)
    }
}
