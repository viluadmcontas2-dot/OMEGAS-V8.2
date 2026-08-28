package com.omegas.prohub.learning

import kotlin.math.ceil
import kotlin.math.max

/**
 * Mede quanto de uma janela física ainda não foi representado na memória.
 *
 * O cálculo usa somente o intervalo monotônico da ECU e a cadência mediana da
 * própria amostra. Ele não altera qualidade física, tolerâncias ou visitas.
 */
object ContinuousWindowNovelty {
    /** Baseline legado explícito; não é verdade física/OEM. */
    const val FULLY_NEW_FRACTION = 0.75

    data class Result(
        val newFrames: Int,
        val totalFrames: Int,
        val fraction: Double,
        val representedThroughElapsedMs: Long,
    ) {
        /** Pelo menos 75% de quadros novos preserva a classificação legada fully-new. */
        val fullyNew: Boolean get() = newFrames >= ceil(totalFrames * FULLY_NEW_FRACTION).toInt()
        val duplicate: Boolean get() = newFrames == 0
    }

    fun calculate(
        startedAtElapsedMs: Long,
        endedAtElapsedMs: Long,
        frameCount: Int,
        medianIntervalMs: Long,
        previouslyRepresentedThroughElapsedMs: Long?,
    ): Result {
        require(frameCount > 0) { "A janela precisa conter ao menos um quadro" }
        require(endedAtElapsedMs >= startedAtElapsedMs) { "Intervalo monotônico inválido" }

        val previous = previouslyRepresentedThroughElapsedMs
        val newFrames = when {
            previous == null || previous < startedAtElapsedMs -> frameCount
            previous >= endedAtElapsedMs -> 0
            frameCount == 1 -> 1
            else -> {
                val duration = (endedAtElapsedMs - startedAtElapsedMs).coerceAtLeast(1L)
                val inferredInterval = max(1L, duration / (frameCount - 1).coerceAtLeast(1))
                val interval = medianIntervalMs.takeIf { it > 0L } ?: inferredInterval
                ceil((endedAtElapsedMs - previous).toDouble() / interval.toDouble())
                    .toInt()
                    .coerceIn(1, frameCount)
            }
        }
        return Result(
            newFrames = newFrames,
            totalFrames = frameCount,
            fraction = newFrames.toDouble() / frameCount.toDouble(),
            representedThroughElapsedMs = max(previous ?: Long.MIN_VALUE, endedAtElapsedMs),
        )
    }
}
