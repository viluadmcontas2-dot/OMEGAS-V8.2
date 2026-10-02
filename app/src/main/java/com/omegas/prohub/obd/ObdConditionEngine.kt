package com.omegas.prohub.obd

/**
 * Converte frames OBD×MP48 já qualificados em uma condição independente.
 *
 * Um frame é uma leitura instantânea. Uma condição é uma janela curta,
 * consistente e identificável que pode entrar no mapa observacional. Isso
 * impede que uma sequência de polls do ELM327 seja contada como muitas
 * evidências independentes.
 */
class ObdConditionEngine(
    private val minimumFrames: Int = 6,
    private val maxGapMs: Long = 1_500L,
    private val maxRpmSpan: Double = 120.0,
    private val maxPetrolInjectionSpanMs: Double = 0.50,
) {
    data class Frame(
        val observedAtMs: Long,
        val fuel: String,
        val cellKey: String,
        val mp48Rpm: Double,
        val petrolInjectionMs: Double,
        val obdRpm: Double,
        val stft: Double,
        val ltft: Double?,
        val speedKmh: Double?,
        val coolantC: Double?,
    )

    data class Condition(
        val id: String,
        val fuel: String,
        val cellKey: String,
        val firstObservedAtMs: Long,
        val lastObservedAtMs: Long,
        val frameCount: Int,
        val mp48Rpm: Double,
        val petrolInjectionMs: Double,
        val stft: Double,
        val ltft: Double?,
        val speedKmh: Double?,
        val coolantC: Double?,
    )

    sealed class Result {
        data class Forming(val frameCount: Int, val requiredFrames: Int) : Result()
        data class Accepted(val condition: Condition) : Result()
        data class Discarded(val reason: String) : Result()
    }

    private val frames = ArrayDeque<Frame>()

    fun reset() = frames.clear()

    fun accept(frame: Frame): Result {
        val last = frames.lastOrNull()
        if (last != null) {
            val discontinuity = when {
                frame.observedAtMs <= last.observedAtMs -> "tempo OBD fora de ordem"
                frame.observedAtMs - last.observedAtMs > maxGapMs -> "lacuna entre leituras OBD×MP48"
                frame.fuel != last.fuel -> "troca de combustível"
                frame.cellKey != last.cellKey -> "mudança de célula física"
                else -> null
            }
            if (discontinuity != null) {
                frames.clear()
                return Result.Discarded(discontinuity)
            }
        }
        frames += frame
        if (frames.size < minimumFrames) return Result.Forming(frames.size, minimumFrames)

        val window = frames.toList()
        val rpmSpan = window.maxOf { it.mp48Rpm } - window.minOf { it.mp48Rpm }
        val injectionSpan = window.maxOf { it.petrolInjectionMs } - window.minOf { it.petrolInjectionMs }
        if (rpmSpan > maxRpmSpan || injectionSpan > maxPetrolInjectionSpanMs) {
            frames.clear()
            return Result.Discarded(
                if (rpmSpan > maxRpmSpan) "RPM variando na janela" else "tempo de injeção variando na janela",
            )
        }

        val condition = Condition(
            id = "${window.first().observedAtMs}-${window.last().observedAtMs}-${frame.fuel}-${frame.cellKey}",
            fuel = frame.fuel,
            cellKey = frame.cellKey,
            firstObservedAtMs = window.first().observedAtMs,
            lastObservedAtMs = window.last().observedAtMs,
            frameCount = window.size,
            mp48Rpm = mean(window.map { it.mp48Rpm }),
            petrolInjectionMs = mean(window.map { it.petrolInjectionMs }),
            stft = mean(window.map { it.stft }),
            ltft = meanNullable(window.map { it.ltft }),
            speedKmh = meanNullable(window.map { it.speedKmh }),
            coolantC = meanNullable(window.map { it.coolantC }),
        )
        frames.clear()
        return Result.Accepted(condition)
    }

    private fun mean(values: List<Double>): Double = values.average()
    private fun meanNullable(values: List<Double?>): Double? = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
}
