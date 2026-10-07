package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Detecta, por banda do GNV, se a ÚLTIMA aquisição nativa aconteceu na marcha lenta
 * (spec 2026-10-07-autocal-apagar-lenta). Sem I/O: recebe leituras confirmadas dos buffers
 * GNV (NUM_BUF_UPD_GAS, PETR_INJ_TBUF_GAS, MNFLD_PRESS_BUF_GAS) e uma janela de telemetria
 * (mesmo relógio elapsed do monitor), e mantém só o estado por banda.
 *
 * - Aquisição da banda i: o contador subiu, OU o contador ficou igual (> 0, inclusive saturado
 *   em 10) e o tempo ou a MAP da banda mudou. Contador caiu = rebaseline (reset/apagamento), não aquisição.
 * - Banda com contador 0 ou MAP raw 0 é ignorada; só bandas 0..15.
 * - Classificação: quadros GNV plausíveis com |MAP − MAP_banda| < 0,03 bar; precisa de >= 3.
 *   >= 80% com rpm < 1000 → LENTA (marca); <= 20% → ANDANDO; entre os dois → INDEFINIDO.
 *   Qualquer aquisição que não seja LENTA tira a marca: a regra olha só a última aquisição.
 *
 * Não é thread-safe: o coordenador o usa a partir de um único executor.
 */
class IdleAcquisitionTracker(
    private val idleRpm: Int = IDLE_RPM,
    private val mapToleranceBar: Double = MAP_TOLERANCE_BAR,
    private val minFrames: Int = MIN_FRAMES,
    private val idleFractionToMark: Double = IDLE_FRACTION_TO_MARK,
    private val drivingFractionToClear: Double = DRIVING_FRACTION_MAX_IDLE,
    private val bandCount: Int = BAND_COUNT,
) {
    enum class Regime { LENTA, ANDANDO, INDEFINIDO }

    class Reading(
        val counters: IntArray,
        val timeRaw: IntArray?,
        val mapRaw: IntArray?,
        val observedAtElapsedMs: Long,
    )

    data class Evidence(
        val band: Int,
        val frames: Int,
        val idleFrames: Int,
        val idleFraction: Double,
        val mapBar: Double,
        val mapRaw: Int,
        val counterBefore: Int,
        val counterAfter: Int,
        val valueChangedAtSameCounter: Boolean,
        val fromElapsedMs: Long,
        val toElapsedMs: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("band", band)
            .put("point", band + 1)
            .put("frames", frames)
            .put("idleFrames", idleFrames)
            .put("idleFraction", idleFraction)
            .put("mapBar", mapBar)
            .put("mapRaw", mapRaw)
            .put("counterBefore", counterBefore)
            .put("counterAfter", counterAfter)
            .put("valueChangedAtSameCounter", valueChangedAtSameCounter)
            .put("fromElapsedMs", fromElapsedMs)
            .put("toElapsedMs", toElapsedMs)
    }

    data class Acquisition(val band: Int, val regime: Regime, val evidence: Evidence)

    private var previous: Reading? = null
    private val marks = linkedMapOf<Int, Evidence>()

    /** Bandas marcadas como "última aquisição na lenta", com a evidência. */
    fun idleBands(): Map<Int, Evidence> = LinkedHashMap(marks)

    /** Contadores da última leitura confirmada (cópia), ou nulo antes do baseline. */
    fun lastCounters(): IntArray? = previous?.counters?.copyOf()

    fun lastObservedAtElapsedMs(): Long? = previous?.observedAtElapsedMs

    fun observe(
        reading: Reading,
        frames: (fromElapsedMs: Long, toElapsedMs: Long) -> List<NativeAnchorTelemetryWindow.Frame>,
    ): List<Acquisition> {
        val before = previous
        previous = Reading(
            counters = reading.counters.copyOf(),
            timeRaw = reading.timeRaw?.copyOf(),
            mapRaw = reading.mapRaw?.copyOf(),
            observedAtElapsedMs = reading.observedAtElapsedMs,
        )
        if (before == null || reading.observedAtElapsedMs <= before.observedAtElapsedMs) return emptyList()

        val acquired = ArrayList<Pair<Int, Boolean>>()
        for (band in 0 until minOf(bandCount, reading.counters.size)) {
            val now = reading.counters[band]
            val old = before.counters.getOrElse(band) { 0 }
            val mapRaw = reading.mapRaw?.getOrNull(band) ?: 0
            if (now <= 0 || mapRaw == 0) {
                marks.remove(band) // banda vazia (ou sem MAP): nada a apagar
                continue
            }
            if (now < old) {
                marks.remove(band) // rebaseline: reset/apagamento no meio
                continue
            }
            val valueChanged = now == old && (
                differs(reading.timeRaw, before.timeRaw, band) || differs(reading.mapRaw, before.mapRaw, band)
                )
            if (now > old || valueChanged) acquired += band to valueChanged
        }
        if (acquired.isEmpty()) return emptyList()

        val window = frames(before.observedAtElapsedMs, reading.observedAtElapsedMs)
        return acquired.map { (band, valueChanged) ->
            val mapRaw = reading.mapRaw?.getOrNull(band) ?: 0
            val mapBar = AutoCalScale.mapBar(mapRaw)
            val matching = window.filter { frame ->
                frame.plausible && frame.fuel.uppercase() in GNV_NAMES && abs(frame.mapBar - mapBar) < mapToleranceBar
            }
            val idle = matching.count { it.rpm < idleRpm }
            val fraction = if (matching.isEmpty()) 0.0 else idle.toDouble() / matching.size
            val regime = when {
                matching.size < minFrames -> Regime.INDEFINIDO
                fraction >= idleFractionToMark -> Regime.LENTA
                fraction <= drivingFractionToClear -> Regime.ANDANDO
                else -> Regime.INDEFINIDO
            }
            val evidence = Evidence(
                band = band,
                frames = matching.size,
                idleFrames = idle,
                idleFraction = fraction,
                mapBar = mapBar,
                mapRaw = mapRaw,
                counterBefore = before.counters.getOrElse(band) { 0 },
                counterAfter = reading.counters[band],
                valueChangedAtSameCounter = valueChanged,
                fromElapsedMs = before.observedAtElapsedMs,
                toElapsedMs = reading.observedAtElapsedMs,
            )
            if (regime == Regime.LENTA) marks[band] = evidence else marks.remove(band)
            Acquisition(band, regime, evidence)
        }
    }

    /** O app apagou estas bandas (readback confirmado): contador conhecido vai a 0 e a marca sai. */
    fun markDeleted(bands: Collection<Int>) {
        bands.forEach { marks.remove(it) }
        val last = previous ?: return
        val counters = last.counters.copyOf()
        bands.forEach { if (it in counters.indices) counters[it] = 0 }
        previous = Reading(counters, last.timeRaw, last.mapRaw, last.observedAtElapsedMs)
    }

    fun reset() {
        previous = null
        marks.clear()
    }

    fun json(): JSONObject = JSONObject()
        .put("baseline", previous != null)
        .put("idleBands", JSONArray().also { array -> marks.values.forEach { array.put(it.toJson()) } })

    private fun differs(now: IntArray?, old: IntArray?, band: Int): Boolean {
        val a = now?.getOrNull(band) ?: return false
        val b = old?.getOrNull(band) ?: return false
        return a != b
    }

    companion object {
        const val IDLE_RPM = 1_000
        const val MAP_TOLERANCE_BAR = 0.03
        const val MIN_FRAMES = 3
        const val IDLE_FRACTION_TO_MARK = 0.80
        const val DRIVING_FRACTION_MAX_IDLE = 0.20
        const val BAND_COUNT = 16
        val GNV_NAMES = setOf("GNV", "CNG")
    }
}
