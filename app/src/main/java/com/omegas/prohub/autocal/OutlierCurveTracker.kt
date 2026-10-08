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
 * Estabilidade (desenho aprovado, 2026-10-07): uma banda só vira candidata depois de DUAS leituras confirmadas e
 * consecutivas do mesmo combustível, em INSTANTES distintos. Conteúdo idêntico num poll novo conta (anomalia
 * estática continua anomalia); o mesmo instante repetido (cache/poll duplicado) nunca conta.
 *
 * Anti-cascata (revisão 2026-10-07): depois de um apagamento do próprio app, enquanto alguma banda apagada ainda
 * está vazia, a base coerente anterior (bandas ACEITAS no último ajuste antes do apagamento, com seus dados) é
 * preservada: uma banda aceita cujo dado não mudou não vira fora da curva só porque a base encolheu. Dado novo da
 * ECU (tempo/MAP diferentes) é julgado pelo dado atual e pode ser anomalia. Banda apagada readquirida (contador > 0)
 * substitui o dado atual; quando todas foram readquiridas, a base preservada é esquecida.
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

    /** Base coerente de antes do apagamento nosso: banda aceita → (timeRaw, mapRaw) naquele ajuste. */
    private class PreservedBase(val accepted: Map<Int, Pair<Int, Int>>, val emptyBands: MutableSet<Int>)

    private val last = HashMap<Fuel, Reading>()
    private val outliers = HashMap<Fuel, List<Outlier>>()
    /** Bandas fora da curva na leitura ANTERIOR, de instante distinto (a confirmação exige presença nas duas). */
    private val previouslyDetected = HashMap<Fuel, Set<Int>>()
    /** Bandas aceitas no último ajuste de cada combustível (vira a base preservada quando o app apaga). */
    private val lastAccepted = HashMap<Fuel, Map<Int, Pair<Int, Int>>>()
    /** Base aceita no instante da decisão de apagar (leituras durante o voo não a substituem). */
    private val decidedBase = HashMap<Fuel, Map<Int, Pair<Int, Int>>>()
    private val preserved = HashMap<Fuel, PreservedBase>()

    /** Registra uma leitura confirmada dos buffers de [fuel]; devolve true se foi leitura nova (instante novo). */
    fun observe(fuel: Fuel, reading: Reading): Boolean {
        val previous = last[fuel]
        if (previous != null && reading.observedAtElapsedMs <= previous.observedAtElapsedMs) return false
        last[fuel] = Reading(
            reading.counters.copyOf(), reading.timeRaw.copyOf(), reading.mapRaw.copyOf(), reading.observedAtElapsedMs,
        )
        preserved[fuel]?.let { base ->
            base.emptyBands.removeAll { band -> band in reading.counters.indices && reading.counters[band] > 0 }
            if (base.emptyBands.isEmpty()) preserved.remove(fuel)
        }
        previouslyDetected[fuel] = outliers[fuel].orEmpty().map { it.band }.toSet()
        outliers[fuel] = detect(fuel, reading)
        return true
    }

    private fun detect(fuel: Fuel, reading: Reading): List<Outlier> {
        if (reading.counters.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT ||
            reading.timeRaw.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT ||
            reading.mapRaw.size < AutoMatchRefinedEngine.USEFUL_BAND_COUNT
        ) return emptyList()
        val points = AutoMatchRefinedEngine.bandPoints(reading.timeRaw, reading.mapRaw, reading.counters)
        val (accepted, fitRejected) = AutoMatchRefinedEngine.monotoneFit(points)
        lastAccepted[fuel] = accepted.associate { it.band to (reading.timeRaw[it.band] to reading.mapRaw[it.band]) }
        val base = preserved[fuel]
        val rejected = if (base == null) fitRejected else fitRejected.filter { point ->
            // Aceita na base coerente e com o MESMO dado: só a base encolheu; não é anomalia nova.
            base.accepted[point.band] != (reading.timeRaw[point.band] to reading.mapRaw[point.band])
        }
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

    /** Bandas fora da curva com contador > 0 na última leitura E na leitura anterior de instante distinto. */
    fun candidates(fuel: Fuel): List<Outlier> {
        val confirmedBefore = previouslyDetected[fuel].orEmpty()
        return outliers[fuel].orEmpty().filter { it.counter > 0 && it.band in confirmedBefore }
    }

    fun lastCounters(fuel: Fuel): IntArray? = last[fuel]?.counters?.copyOf()

    /**
     * O app apagou [band] de [fuel] (readback confirmou): sai dos candidatos até a próxima leitura e a base coerente
     * de antes fica preservada enquanto a banda estiver vazia (ver cabeçalho).
     */
    fun onDeleted(fuel: Fuel, band: Int) {
        outliers[fuel] = outliers[fuel].orEmpty().filter { it.band != band }
        previouslyDetected[fuel] = previouslyDetected[fuel].orEmpty() - band
        last[fuel]?.counters?.let { if (band in it.indices) it[band] = 0 }
        val base = preserved[fuel]
            ?: PreservedBase(decidedBase[fuel] ?: lastAccepted[fuel].orEmpty(), HashSet()).also { preserved[fuel] = it }
        base.emptyBands += band
    }

    /** O coordenador vai apagar bandas de [fuel]: congela a base aceita desta decisão para [onDeleted]. */
    fun onDeleteStarted(fuel: Fuel) {
        if (preserved[fuel] == null) decidedBase[fuel] = lastAccepted[fuel].orEmpty()
    }

    /** A banda mudou desde a marca (ou o readback foi ambíguo): sai dos candidatos até o próximo cálculo. */
    fun forget(fuel: Fuel, bands: Collection<Int>) {
        outliers[fuel] = outliers[fuel].orEmpty().filter { it.band !in bands }
        previouslyDetected[fuel] = previouslyDetected[fuel].orEmpty() - bands.toSet()
    }

    /**
     * invalidateRound / ação manual / sessão nova: as leituras anteriores não valem mais.
     * [ownAutomaticDelete] = a invalidação veio do recibo do PRÓPRIO apagamento automático (monitor → serviço →
     * coordenador, antes de [onDeleted]): leituras, candidatos e confirmações caem do mesmo jeito (nada velho vira
     * aquisição atual), mas a base congelada na decisão e a preservada ficam, senão o buraco que o app abriu
     * transformaria um ponto bom marginal em "fora da curva" novo. Reset/escrita K manual/USB nova apagam tudo.
     */
    fun reset(ownAutomaticDelete: Boolean = false) {
        last.clear()
        outliers.clear()
        previouslyDetected.clear()
        lastAccepted.clear()
        if (!ownAutomaticDelete) {
            decidedBase.clear()
            preserved.clear()
        }
    }

    fun json(): JSONObject = JSONObject().also { root ->
        Fuel.entries.forEach { fuel ->
            root.put(
                fuel.wireName,
                JSONObject()
                    .put("baseline", last[fuel] != null)
                    .put("outliers", JSONArray().also { array -> outliers[fuel].orEmpty().forEach { array.put(it.toJson()) } })
                    .put("confirmed", JSONArray(candidates(fuel).map { it.band }))
                    .put("preservedBase", JSONArray(preserved[fuel]?.emptyBands?.sorted().orEmpty()))
                    .put("readingsRequired", 2),
            )
        }
    }
}
