package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Lote H: evidência em 54 bins finos (3 × as 18 faixas da ECU), exibição/consumo em 18.
 * Espelho de tools/autocal_refine/fine_bins.py (aggregate, band_summary, between_json, bands18_json).
 *
 * Grade: 54 bins em ln(ms) de 3,0 a 12,0 ms (razão 4^(1/54) = 1,026 por bin): mais fina que o passo mais fino do
 * eixo K de 30 pontos e 3× mais fina que a ECU. As bordas são literais (arredondadas a 4 casas) para não depender
 * de pow() na fronteira. Estrutura limitada: 54 bins × reservatório de [RESERVOIR] pares; derivada dos pares do
 * livro (que já persistem), portanto nada novo no arquivo e o arquivo antigo continua carregando.
 */
object FineBins {
    const val BAND_COUNT = 18
    const val FINE_PER_BAND = 3
    const val FINE_COUNT = BAND_COUNT * FINE_PER_BAND
    /** Pares recentes guardados por bin: memória limitada, estatística robusta. */
    const val RESERVOIR = 32
    const val CONF_EPISODES_FULL = 6.0
    const val CONF_SAMPLES_FULL = 24.0
    const val CONF_DISPERSION_REF = 0.08
    const val CONF_EPISODES_UNKNOWN = 0.3
    const val DISPERSION_UNKNOWN = 0.10
    const val GRID_LO_MS = 3.0
    const val GRID_HI_MS = 12.0

    val EDGES = doubleArrayOf(
        3.0, 3.078, 3.1581, 3.2402, 3.3244, 3.4109, 3.4996, 3.5906, 3.684, 3.7798, 3.8781, 3.9789, 4.0824, 4.1885, 4.2975,
        4.4092, 4.5239, 4.6415, 4.7622, 4.886, 5.0131, 5.1435, 5.2772, 5.4144, 5.5552, 5.6997, 5.8479, 6.0, 6.156, 6.3161,
        6.4804, 6.6489, 6.8218, 6.9992, 7.1812, 7.3679, 7.5595, 7.7561, 7.9578, 8.1647, 8.3771, 8.5949, 8.8184, 9.0477,
        9.283, 9.5244, 9.7721, 10.0262, 10.2869, 10.5544, 10.8289, 11.1105, 11.3994, 11.6959, 12.0,
    )

    /** n = total de pares do bin; as estatísticas vêm dos últimos [RESERVOIR]. [medianLn] nulo = sem pares. */
    data class Bin(
        val index: Int,
        val fromMs: Double,
        val toMs: Double,
        val n: Int,
        val medianLn: Double?,
        val dispersion: Double?,
        /** Episódios distintos do reservatório; nulo = algum desconhecido (ou sem pares). */
        val episodes: Int?,
        val episodeIds: List<Int>,
        val ecuShare: Double,
        val petrolMs: Double?,
    ) {
        val ratio: Double? get() = medianLn?.let { exp(it) }
        /** Centro geométrico do bin em ln ms. */
        val centerLn: Double get() = 0.5 * (ln(fromMs) + ln(toMs))
        val confidence: Double get() = confidence(n, episodes, dispersion)
    }

    /** Resumo de um conjunto de bins (faixa de 18 ou intervalo entre pontos da ECU). */
    data class Summary(
        val fromMs: Double,
        val toMs: Double,
        val samples: Int,
        val ratio: Double?,
        val episodes: Int?,
        val dispersion: Double?,
        val ecuShare: Double,
        val confidence: Double,
    )

    fun fineIndex(tp: Double): Int? {
        if (!(tp >= GRID_LO_MS && tp < GRID_HI_MS)) return null
        var lo = 0
        var hi = FINE_COUNT - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (EDGES[mid] <= tp) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun bandOfBin(index: Int): Int = index / FINE_PER_BAND

    private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

    private class Entry(val ln: Double, val tp: Double, val episode: Int, val ecu: Boolean)

    /** Pares em ordem de chegada → 54 bins. Mediana = elemento n/2 do ordenado (convenção do livro). */
    fun aggregate(pairs: List<EquivalenceLedger.EvidencePair>): List<Bin> {
        val reservoirs = Array(FINE_COUNT) { ArrayDeque<Entry>() }
        val counts = IntArray(FINE_COUNT)
        for (p in pairs) {
            val idx = fineIndex(p.petrolRefMs) ?: continue
            if (p.gasPetrolMs <= 0.0 || p.petrolRefMs <= 0.0) continue
            counts[idx]++
            val queue = reservoirs[idx]
            queue.addLast(Entry(ln(p.gasPetrolMs / p.petrolRefMs), p.petrolRefMs, p.episode, p.ecuRef))
            if (queue.size > RESERVOIR) queue.removeFirst()
        }
        return List(FINE_COUNT) { i ->
            val res = reservoirs[i]
            if (res.isEmpty()) Bin(i, EDGES[i], EDGES[i + 1], counts[i], null, null, null, emptyList(), 0.0, null)
            else {
                val med = median(res.map { it.ln })
                val disp = if (res.size >= 2) median(res.map { kotlin.math.abs(it.ln - med) }) * 1.4826 else null
                val known = res.all { it.episode >= 0 }
                val ids = if (known) res.map { it.episode }.toSortedSet().toList() else emptyList()
                Bin(i, EDGES[i], EDGES[i + 1], counts[i], med, disp, if (known) ids.size else null, ids,
                    res.count { it.ecu }.toDouble() / res.size, median(res.map { it.tp }))
            }
        }
    }

    /** 0..1: trechos × dispersão × quantidade. Sem episódios conhecidos vale pouco. */
    fun confidence(samples: Int, episodes: Int?, dispersion: Double?): Double {
        if (samples <= 0) return 0.0
        val confE = if (episodes == null) CONF_EPISODES_UNKNOWN else min(1.0, episodes / CONF_EPISODES_FULL)
        val confS = min(1.0, samples / CONF_SAMPLES_FULL)
        val d = dispersion ?: DISPERSION_UNKNOWN
        val confD = 1.0 / (1.0 + (d / CONF_DISPERSION_REF) * (d / CONF_DISPERSION_REF))
        return confE * confD * (0.5 + 0.5 * confS)
    }

    private fun summarize(bins: List<Bin>, members: List<Bin>, fromMs: Double, toMs: Double, weightByConfidence: Boolean): Summary {
        val filled = members.filter { it.n > 0 && it.medianLn != null }
        val samples = filled.sumOf { it.n }
        if (samples == 0) return Summary(fromMs, toMs, 0, null, null, null, 0.0, 0.0)
        var weights = filled.map { if (weightByConfidence) it.n * it.confidence else it.n.toDouble() }
        var total = weights.sum()
        if (total <= 0.0) { weights = filled.map { it.n.toDouble() }; total = samples.toDouble() }
        val ratio = exp(filled.indices.sumOf { weights[it] * filled[it].medianLn!! } / total)
        val episodes = if (filled.all { it.episodes != null }) filled.flatMap { it.episodeIds }.toSet().size else null
        val dispBins = filled.filter { it.dispersion != null }
        val dispersion = if (dispBins.isEmpty()) null else dispBins.sumOf { it.n * it.dispersion!! } / dispBins.sumOf { it.n }
        val ecu = filled.sumOf { it.n * it.ecuShare } / samples
        return Summary(fromMs, toMs, samples, ratio, episodes, dispersion, ecu, confidence(samples, episodes, dispersion))
    }

    /** Faixa de 18 [band]: razão ponderada por amostras (em ln), episódios (união), dispersão e confiança. */
    fun bandSummary(bins: List<Bin>, band: Int): Summary {
        val members = bins.subList(band * FINE_PER_BAND, (band + 1) * FINE_PER_BAND)
        return summarize(bins, members, members.first().fromMs, members.last().toMs, weightByConfidence = false)
    }

    /**
     * Intervalos ENTRE os pontos da ECU: a ECU mostra 18 valores (o bin do meio de cada faixa, 3i+1). Entre dois
     * vizinhos ficam exatamente 2 bins finos (3i+2 e 3i+3); abaixo do primeiro, o bin 0; acima do último, o 53.
     * Triple(tipo, de, até-exclusivo).
     */
    fun betweenMembers(): List<Triple<String, Int, Int>> =
        listOf(Triple("open-low", 0, 1)) +
            (0 until BAND_COUNT - 1).map { Triple("gap", FINE_PER_BAND * it + 2, FINE_PER_BAND * it + 4) } +
            listOf(Triple("open-high", FINE_COUNT - 1, FINE_COUNT))

    fun betweenSummary(bins: List<Bin>, from: Int, until: Int): Summary =
        summarize(bins, bins.subList(from, until), bins[from].fromMs, bins[until - 1].toMs, weightByConfidence = true)

    private fun r5(v: Double?): Any = if (v == null) JSONObject.NULL else Math.round(v * 100_000.0) / 100_000.0
    private fun r4(v: Double): Double = Math.round(v * 10_000.0) / 10_000.0

    private fun fineJson(b: Bin): JSONObject = JSONObject()
        .put("index", b.index).put("fromMs", b.fromMs).put("toMs", b.toMs)
        .put("ratio", r5(b.ratio)).put("samples", b.n)
        .put("episodes", if (b.n > 0 && b.episodes != null) b.episodes else JSONObject.NULL)
        .put("dispersion", r5(b.dispersion)).put("ecuShare", r4(b.ecuShare))
        .put("ownShare", if (b.n > 0) r4(1.0 - b.ecuShare) else 0.0)
        .put("petrolMs", r5(b.petrolMs))

    private fun summaryJson(s: Summary, kind: String?, fine: List<Bin>): JSONObject {
        val o = JSONObject()
        if (kind != null) o.put("kind", kind)
        o.put("fromMs", s.fromMs).put("toMs", s.toMs).put("ratio", r5(s.ratio)).put("samples", s.samples)
            .put("episodes", s.episodes ?: JSONObject.NULL).put("confidence", r4(s.confidence))
            .put("dispersion", r5(s.dispersion)).put("ecuShare", r4(s.ecuShare))
        o.put("fineBins", JSONArray().apply { fine.forEach { put(fineJson(it)) } })
        return o
    }

    /** `bands18`: as 18 faixas da ECU (espaços), agregadas dos bins finos. Desconhecido = null. */
    fun bands18Json(bins: List<Bin>): JSONArray = JSONArray().apply {
        for (band in 0 until BAND_COUNT) {
            put(summaryJson(bandSummary(bins, band), null, bins.subList(band * FINE_PER_BAND, (band + 1) * FINE_PER_BAND)))
        }
    }

    /** `betweenBands`: 17 intervalos entre os pontos da ECU (+ pontas abertas só com evidência). Peso = amostras × confiança. */
    fun betweenJson(bins: List<Bin>): JSONArray = JSONArray().apply {
        for ((kind, from, until) in betweenMembers()) {
            val s = betweenSummary(bins, from, until)
            if (kind != "gap" && s.samples == 0) continue
            put(summaryJson(s, kind, bins.subList(from, until)))
        }
    }

    // ------------------------------------------------------------ portões da evidência (consumo pelo motor)

    /** Bin válido por posição; [outlierBands] = faixas de 18 com razão implausível; [fewEpisodeBands] = sem trechos suficientes. */
    class Gate(val valid: BooleanArray, val outlierBands: Int, val fewEpisodeBands: Int)

    /**
     * Bin com < [AutoMatchRefinedEngine.BAND_MATURE_COUNT] pares não é evidência. A faixa de 18 do bin precisa de
     * ≥ [AutoMatchRefinedEngine.MIN_BAND_EPISODES] episódios (união; desconhecido desliga o portão) e razão agregada plausível.
     */
    fun gate(bins: List<Bin>): Gate {
        val valid = BooleanArray(FINE_COUNT)
        var outliers = 0
        var few = 0
        for (band in 0 until BAND_COUNT) {
            val s = bandSummary(bins, band)
            val mature = (band * FINE_PER_BAND until (band + 1) * FINE_PER_BAND).filter { bins[it].n >= AutoMatchRefinedEngine.BAND_MATURE_COUNT }
            if (mature.isEmpty()) continue
            if (s.episodes != null && s.episodes < AutoMatchRefinedEngine.MIN_BAND_EPISODES) { few++; continue }
            val ratio = s.ratio ?: continue
            if (ratio < AutoMatchRefinedEngine.TELEMETRY_RATIO_MIN || ratio > AutoMatchRefinedEngine.TELEMETRY_RATIO_MAX) { outliers++; continue }
            mature.forEach { valid[it] = true }
        }
        return Gate(valid, outliers, few)
    }
}
