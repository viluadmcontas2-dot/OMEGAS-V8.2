package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Detecta, por combustível, os pontos FORA DA CURVA dos buffers nativos (spec 2026-10-07-autocal-apagar-lenta,
 * revisão 2): a curva ms × MAP das bandas 0..15 com contador > 0 e MAP > 0 passa pelo mesmo ajuste robusto do
 * Refino ([AutoMatchRefinedEngine.monotoneFit]: resíduo leave-one-out em ln ms, limiar max(5%, 3·MAD)); banda
 * rejeitada = fora da curva. Só recalcula quando o buffer daquele combustível muda.
 *
 * Sem "forma real" (decisão do dono, 2026-10-07): parado o carro injeta mais, então o ponto contaminado volta
 * sempre no mesmo lugar; repetição não prova nada. Banda fora da curva é candidata toda vez que estiver fora.
 *
 * Sem I/O; não é thread-safe (o coordenador o usa a partir de um único executor).
 */
class OutlierCurveTracker {
    class Reading(
        val counters: IntArray,
        val timeRaw: IntArray,
        val mapRaw: IntArray,
        val observedAtElapsedMs: Long,
    )

    data class Outlier(
        val fuel: Fuel,
        val band: Int,
        val counter: Int,
        val timeRaw: Int,
        val mapRaw: Int,
        val timeMs: Double,
        val mapBar: Double,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("fuel", fuel.wireName)
            .put("band", band)
            .put("point", band + 1)
            // Mesmas chaves que o gerenciador confere na releitura de antes (banda mudou desde a marca → pula).
            .put("counterAfter", counter)
            .put("timeRaw", timeRaw)
            .put("mapRaw", mapRaw)
            .put("timeMs", timeMs)
            .put("mapBar", mapBar)
    }

    private val last = HashMap<Fuel, Reading>()
    private val outliers = HashMap<Fuel, List<Outlier>>()

    /** Registra uma leitura confirmada dos buffers de [fuel]; devolve true se o buffer mudou (e reavaliou). */
    fun observe(fuel: Fuel, reading: Reading): Boolean {
        val previous = last[fuel]
        if (previous != null && reading.observedAtElapsedMs <= previous.observedAtElapsedMs) return false
        last[fuel] = Reading(
            reading.counters.copyOf(), reading.timeRaw.copyOf(), reading.mapRaw.copyOf(), reading.observedAtElapsedMs,
        )
        val changed = previous == null ||
            !previous.counters.contentEquals(reading.counters) ||
            !previous.timeRaw.contentEquals(reading.timeRaw) ||
            !previous.mapRaw.contentEquals(reading.mapRaw)
        if (!changed) return false
        outliers[fuel] = detect(fuel, reading)
        return true
    }

    private fun detect(fuel: Fuel, reading: Reading): List<Outlier> {
        if (reading.counters.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT ||
            reading.timeRaw.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT ||
            reading.mapRaw.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT
        ) return emptyList()
        val points = AutoMatchRefinedEngine.bandPoints(reading.timeRaw, reading.mapRaw, reading.counters)
        val rejected = AutoMatchRefinedEngine.monotoneFit(points).second
        return rejected.map { point ->
            Outlier(
                fuel = fuel,
                band = point.band,
                counter = reading.counters[point.band],
                timeRaw = reading.timeRaw[point.band],
                mapRaw = reading.mapRaw[point.band],
                timeMs = point.timeMs,
                mapBar = point.mapBar,
            )
        }.sortedBy { it.band }
    }

    /** Bandas fora da curva com contador > 0 na última leitura. */
    fun candidates(fuel: Fuel): List<Outlier> = outliers[fuel].orEmpty().filter { it.counter > 0 }

    fun lastCounters(fuel: Fuel): IntArray? = last[fuel]?.counters?.copyOf()

    /** O app apagou [band] de [fuel] (readback confirmou): sai dos candidatos até a próxima leitura. */
    fun onDeleted(fuel: Fuel, band: Int) {
        outliers[fuel] = outliers[fuel].orEmpty().filter { it.band != band }
        last[fuel]?.counters?.let { if (band in it.indices) it[band] = 0 }
    }

    /** A banda mudou desde a marca (ou o readback foi ambíguo): sai dos candidatos até o próximo cálculo. */
    fun forget(fuel: Fuel, bands: Collection<Int>) {
        outliers[fuel] = outliers[fuel].orEmpty().filter { it.band !in bands }
    }

    /** invalidateRound / ação manual / sessão nova: as leituras anteriores não valem mais. */
    fun reset() {
        last.clear()
        outliers.clear()
    }

    fun json(): JSONObject = JSONObject().also { root ->
        Fuel.entries.forEach { fuel ->
            root.put(
                fuel.wireName,
                JSONObject()
                    .put("baseline", last[fuel] != null)
                    .put("outliers", JSONArray().also { array -> outliers[fuel].orEmpty().forEach { array.put(it.toJson()) } }),
            )
        }
    }


}
