package com.omegas.dyno.lab

import kotlin.math.abs

class DriveResponseAnalyzer {
    data class Frame(
        val tMs: Long,
        val fuel: String,
        val fuelAgeMs: Long,
        val rpm: Double,
        val tpsPct: Double?,
        val mapKpa: Double?,
        val ectC: Double?,
        val speedKmh: Double?,
        val stft: Double?,
        val ltft: Double?,
    )

    data class Result(
        val lagMs: Double?,
        val roughness: Double?,
        val confidence: Double?,
        val eventsMatched: Int,
        val comparison: String,
        val curveKAdvice: String?,
    )

    /**
     * Analyzes a chronological capture. lagMs and roughness describe matched GNV events.
     * Roughness is the mean absolute change of RPM/s between consecutive intervals.
     * Confidence (0..1) scores sample cadence and agreement across pairs.
     */
    fun analyze(frames: List<Frame>): Result {
        val pairs = collectEvents(frames).groupBy { it.condition }.values.flatMap { events ->
            val gasoline = events.filter { it.fuel == "GASOLINA" }
            val gnv = events.filter { it.fuel == "GNV" }
            if (minOf(gasoline.size, gnv.size) < MIN_PAIRS) emptyList()
            else gasoline.zip(gnv, ::Pairing)
        }
        if (pairs.isEmpty()) return Result(null, null, null, 0, "Dados insuficientes", null)

        val gnvMisses = pairs.count { it.gnv.lagMs == null }
        val gasolineMisses = pairs.count { it.gasoline.lagMs == null }
        val lagDifferences = pairs.mapNotNull { it.lagDifference() }
        val lagDifference = lagDifferences.medianOrNull()
        val roughnessDifference = pairs.map { it.gnv.roughness - it.gasoline.roughness }.medianOrNull()!!
        val comparison = when {
            gnvMisses == pairs.size && gasolineMisses == pairs.size -> "Rotação sem resposta nas duas amostras"
            gnvMisses > gasolineMisses -> "GNV com mais hesitações"
            gasolineMisses > gnvMisses -> "Gasolina com mais hesitações"
            lagDifference != null && lagDifference >= NOTICEABLE_LAG_MS -> "GNV com mais atraso"
            lagDifference != null && lagDifference <= -NOTICEABLE_LAG_MS -> "Gasolina com mais atraso"
            roughnessDifference >= NOTICEABLE_ROUGHNESS -> "GNV com resposta mais irregular"
            roughnessDifference <= -NOTICEABLE_ROUGHNESS -> "Gasolina com resposta mais irregular"
            else -> "Respostas semelhantes"
        }
        val agreement = pairs.groupingBy { it.outcome() }.eachCount().values.max().toDouble() / pairs.size
        val confidence = agreement * pairs.map {
            minOf(it.gasoline.cadenceQuality, it.gnv.cadenceQuality)
        }.average()
        return Result(
            pairs.mapNotNull { it.gnv.lagMs }.medianOrNull(),
            pairs.map { it.gnv.roughness }.medianOrNull(),
            confidence,
            pairs.size,
            comparison,
            curveAdvice(pairs, confidence),
        )
    }

    private fun collectEvents(frames: List<Frame>): List<Event> {
        val events = mutableListOf<Event>()
        var index = 0
        while (index < frames.lastIndex) {
            val baseline = frames[index]
            val onset = frames[index + 1]
            if (!baseline.isUsable() || !continues(baseline, onset, baseline) ||
                baseline.mapKpa == null || onset.tpsPct!! <= baseline.tpsPct!!
            ) {
                index++
                continue
            }
            // Measure from the first observed rise, using the last pre-rise sample as reference.
            var end = index + 1
            var step: Double? = null
            while (end < frames.size && continues(frames[end - 1], frames[end], baseline)) {
                val frame = frames[end]
                val sinceBaseline = frame.tMs - baseline.tMs
                if (step == null && sinceBaseline > MAX_STEP_MS) break
                val pedalRise = frame.tpsPct!! - baseline.tpsPct
                if (step == null && pedalRise >= STEP_POINTS) step = pedalRise
                if (step != null && frame.tMs - onset.tMs >= RESPONSE_WINDOW_MS) {
                    events += measure(frames.subList(index, end + 1), baseline.mapKpa, step)
                    index = end
                    break
                }
                end++
            }
            index++
        }
        return events
    }

    private fun Frame.isUsable(): Boolean =
        tMs >= 0 && (fuel == "GNV" || fuel == "GASOLINA") && fuelAgeMs in 0..1500 &&
            rpm.isFinite() && rpm > 0 && tpsPct != null && tpsPct in 0.0..100.0 &&
            ectC != null && ectC.isFinite() && ectC >= 70 &&
            speedKmh != null && speedKmh.isFinite() && speedKmh >= MIN_SPEED_KMH &&
            (mapKpa == null || (mapKpa.isFinite() && mapKpa > 0))

    private fun continues(previous: Frame, current: Frame, baseline: Frame): Boolean {
        val dt = current.tMs - previous.tMs
        if (dt !in 1..MAX_GAP_MS || current.fuel != baseline.fuel || !current.isUsable()) return false

        val ratio = current.rpm / current.speedKmh!!
        val previousRatio = previous.rpm / previous.speedKmh!!
        val baselineRatio = baseline.rpm / baseline.speedKmh!!
        return abs(ratio - previousRatio) <= previousRatio * MAX_GEAR_CHANGE &&
            abs(ratio - baselineRatio) <= baselineRatio * MAX_GEAR_CHANGE
    }

    private fun measure(window: List<Frame>, initialMap: Double, step: Double): Event {
        val baseline = window.first()
        val onset = window[1]
        val lag = window.firstOrNull { it.rpm - baseline.rpm > RESPONSE_RPM }
            ?.let { (it.tMs - onset.tMs).toDouble() }
        val intervals = window.zipWithNext { previous, current ->
            val gap = current.tMs - previous.tMs
            Interval(gap, (current.rpm - previous.rpm) * 1000 / gap)
        }
        val roughness = intervals.zipWithNext { previous, current ->
            abs(current.rpmPerSecond - previous.rpmPerSecond)
        }.average()
        val worstGap = intervals.maxOf { it.gapMs }
        val cadenceQuality = 1.0 - (worstGap - GOOD_GAP_MS).coerceAtLeast(0) / (2.0 * (MAX_GAP_MS - GOOD_GAP_MS))
        return Event(
            baseline.fuel,
            Condition((baseline.rpm / 500).toInt(), (initialMap / 15).toInt(), (step / STEP_POINTS).toInt()),
            lag,
            roughness,
            cadenceQuality,
            baseline.stft,
            baseline.ltft,
        )
    }

    private fun curveAdvice(pairs: List<Pairing>, confidence: Double): String? {
        if (confidence < MIN_ADVICE_CONFIDENCE) return null
        val corroborated = pairs.filter {
            it.outcome() == Outcome.GNV_WORSE &&
                it.gasoline.stft?.let { trim -> abs(trim) < 5 } == true &&
                it.gasoline.ltft?.let { trim -> abs(trim) < 5 } == true
        }
        val required = pairs.size * MIN_ADVICE_CONFIDENCE
        val positive = corroborated.count { it.gnv.trimDirection() > 0 }
        val negative = corroborated.count { it.gnv.trimDirection() < 0 }
        return if (maxOf(positive, negative) >= required) {
            "Revisar a Curva K nas faixas em que o GNV respondeu pior e a correção de combustível confirmou o desvio."
        } else null
    }

    private data class Condition(val rpmBand: Int, val mapBand: Int, val pedalBand: Int)

    private data class Interval(val gapMs: Long, val rpmPerSecond: Double)

    private data class Event(
        val fuel: String,
        val condition: Condition,
        val lagMs: Double?,
        val roughness: Double,
        val cadenceQuality: Double,
        val stft: Double?,
        val ltft: Double?,
    ) {
        fun trimDirection(): Int {
            if (stft == null || ltft == null || stft !in -100.0..100.0 || ltft !in -100.0..100.0) return 0
            return when {
                stft >= 5 && ltft >= 3 -> 1
                stft <= -5 && ltft <= -3 -> -1
                else -> 0
            }
        }
    }

    private enum class Outcome { GNV_WORSE, GASOLINE_WORSE, SIMILAR }

    private data class Pairing(val gasoline: Event, val gnv: Event) {
        fun lagDifference(): Double? {
            val gnvLag = gnv.lagMs ?: return null
            val gasolineLag = gasoline.lagMs ?: return null
            return gnvLag - gasolineLag
        }

        fun outcome(): Outcome {
            if (gnv.lagMs == null && gasoline.lagMs != null) return Outcome.GNV_WORSE
            if (gasoline.lagMs == null && gnv.lagMs != null) return Outcome.GASOLINE_WORSE
            val lag = lagDifference() ?: 0.0
            val roughness = gnv.roughness - gasoline.roughness
            return when {
                lag >= NOTICEABLE_LAG_MS -> Outcome.GNV_WORSE
                lag <= -NOTICEABLE_LAG_MS -> Outcome.GASOLINE_WORSE
                roughness >= NOTICEABLE_ROUGHNESS -> Outcome.GNV_WORSE
                roughness <= -NOTICEABLE_ROUGHNESS -> Outcome.GASOLINE_WORSE
                else -> Outcome.SIMILAR
            }
        }
    }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val ordered = sorted()
        return (ordered[(size - 1) / 2] + ordered[size / 2]) / 2
    }

    private companion object {
        const val MIN_PAIRS = 5
        const val STEP_POINTS = 5.0
        const val MAX_STEP_MS = 500L
        const val MAX_GAP_MS = 600L
        const val GOOD_GAP_MS = 200L
        const val RESPONSE_WINDOW_MS = 1500L
        const val RESPONSE_RPM = 120.0
        const val MIN_SPEED_KMH = 5.0
        const val MAX_GEAR_CHANGE = 0.10
        const val NOTICEABLE_LAG_MS = 100.0
        const val NOTICEABLE_ROUGHNESS = 100.0
        const val MIN_ADVICE_CONFIDENCE = 0.8
    }
}
