package com.omegas.prohub.autocal

import kotlin.math.abs
import kotlin.math.ln

/**
 * Fonte única dos pares (gasolina de referência, GNV) e das regras de independência da evidência.
 *
 * O livro ([EquivalenceLedger]) e o cérebro (`EquivalenceEngine`) usam estas mesmas funções: o veredito
 * "equivalente" e a proposta de K saem do mesmo conjunto de pares (casamento por RPM×MAP).
 *
 * Independência vem de ESTATÍSTICA, não de relógio: leituras estáveis consecutivas se sobrepõem (janela de 3
 * quadros), então o número de leituras NÃO é o número de amostras independentes. O cérebro mede o n efetivo
 * (correção de autocorrelação, [effectiveN]) e julga pelo intervalo de confiança do erro: pouco dado = intervalo
 * largo = "ainda sem certeza", sem pedir tempo de condução. Aqui um "bloco" (episódio) é só a de-duplicação de
 * janelas sobrepostas: pares da mesma faixa separados por menos de [VISIT_GAP_MS] são o mesmo bloco, e o peso de um
 * bloco é limitado (nunca por quadro). Não existe mais portão de "visitas separadas por 60 s".
 *
 * Espelho Python: tools/equivalence_oracle (ledger_obs, build_pairs com água, visit_ids) e tools/autocal_refine/blind_telemetry_test.py.
 */
object EvidencePairs {
    const val CONFIDENCE_MODEL = "overlap-lag1-mad-v1"

    data class Confidence(val effectiveSamples: Double, val dispersionLog: Double?)

    /** A confiança da faixa usa as mesmas leituras e regras estatísticas do cérebro; não altera a razão. */
    fun confidence(pairs: List<EquivalenceLedger.EvidencePair>): Confidence {
        if (pairs.isEmpty() || pairs.any {
                it.t == Long.MIN_VALUE || !it.petrolRefMs.isFinite() || it.petrolRefMs <= 0.0 ||
                    !it.gasPetrolMs.isFinite() || it.gasPetrolMs <= 0.0
            }) return Confidence(0.0, null)
        val ordered = pairs.sortedBy { it.t }
        val errors = ordered.map { ln(it.gasPetrolMs / it.petrolRefMs) }
        if (errors.any { !it.isFinite() }) return Confidence(0.0, null)
        val sorted = errors.sorted()
        val center = sorted[sorted.size / 2]
        val dispersion = if (sorted.size >= 2) {
            val deviations = sorted.map { abs(it - center) }.sorted()
            1.4826 * deviations[deviations.size / 2]
        } else null
        return Confidence(effectiveN(ordered.map { it.t }, errors), dispersion)
    }

    /** Lacuna que separa dois blocos de leituras sobrepostas (≈ 10 quadros): de-duplicação, não exigência de tempo. */
    const val VISIT_GAP_MS = 3_000L
    /** Blocos mínimos por faixa: 1 (sem portão por contagem de trechos; a confiança vem do intervalo do n efetivo). */
    const val MIN_VISITS = 1
    /** Autocorrelação máxima aceita ao estimar o n efetivo (acima disso o n efetivo é ≈ 0 e nada é julgado). */
    const val MAX_RHO = 0.95
    /** Duas leituras estáveis a menos de 1 s partilham quadros da janela de 3 quadros (≈ 285 ms cada): são a mesma amostra. */
    const val OVERLAP_MS = 1_000L

    /**
     * n efetivo de uma série de valores (ln da razão) em ordem de tempo: n·(1−ρ)/(1+ρ), com ρ a autocorrelação de lag 1
     * limitada a [0, [MAX_RHO]]. Série sem variância ou com < 3 valores não prova independência: ρ = [MAX_RHO].
     */
    fun effectiveN(timesInOrder: List<Long>, valuesInTimeOrder: List<Double>): Double {
        val n = valuesInTimeOrder.size
        if (n == 0) return 0.0
        // Janelas sobrepostas não são amostras distintas: o n efetivo nunca passa do número de leituras sem sobreposição.
        var apart = 0
        var lastKept = Long.MIN_VALUE
        for (t in timesInOrder) if (lastKept == Long.MIN_VALUE || t - lastKept >= OVERLAP_MS) { apart++; lastKept = t }
        return minOf(autocorrelationN(valuesInTimeOrder), apart.toDouble())
    }

    private fun autocorrelationN(valuesInTimeOrder: List<Double>): Double {
        val n = valuesInTimeOrder.size
        if (n < 3) return n * (1.0 - MAX_RHO) / (1.0 + MAX_RHO)
        val mean = valuesInTimeOrder.sum() / n
        var variance = 0.0
        var cov = 0.0
        for (i in 0 until n) {
            val d = valuesInTimeOrder[i] - mean
            variance += d * d
            if (i > 0) cov += d * (valuesInTimeOrder[i - 1] - mean)
        }
        val rho = if (variance / n <= 1e-8) MAX_RHO else (cov / variance).coerceIn(0.0, MAX_RHO)
        return n * (1.0 - rho) / (1.0 + rho)
    }

    private val T975 = doubleArrayOf(12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042)

    /** Quantil t bicaudal 95% com df = ⌊n efetivo⌋ − 1 (≥ 1); acima de 30 graus vale 1,96. */
    fun tCritical(nEff: Double): Double {
        val df = kotlin.math.floor(nEff).toInt() - 1
        return if (df < 1) T975[0] else if (df > 30) 1.96 else T975[df - 1]
    }
    /** Uma faixa grossa só vale com leituras em pelo menos este número de terços internos... */
    const val INTERIOR_SLICES = 3
    const val INTERIOR_MIN_SLICES = 2
    /** ...cada um com pelo menos este número de pares. */
    const val INTERIOR_MIN_PAIRS = 2
    /** Id de episódio de um par = faixa × este fator + índice da visita (único entre faixas). */
    const val EPISODE_BAND_FACTOR = 100_000
    /**
     * Fronteira de regime: abaixo dela a ECU está na estratégia de lenta (85 sessões reais: rpm < 1200 dá +20–30% de ms
     * no mesmo MAP). Um par só junta leituras do MESMO lado desta fronteira.
     */
    const val REGIME_SPLIT_RPM = EquivalenceLedger.DRIVING_MIN_RPM

    /**
     * Diferença máxima de temperatura da água (°C) entre a leitura de GNV e a de gasolina de um par. Achado F14 (79 sessões
     * reais): gas_ms_diagnostic / petrol_ms em GNV sobe 22% com a água (1,96 a 35–40 °C → 2,39 a 75–80 °C, estável a
     * partir de ~65–70 °C) e 87% do tempo registrado é água < 70 °C; um portão fixo de temperatura apagaria quase toda
     * a evidência, então o par exige água comparável, não água quente.
     */
    const val MAX_WATER_DELTA_C = 8.0

    /** Águas comparáveis? Água desconhecida (NaN: leitura gravada antes do campo) não reprova o par. */
    fun sameWater(waterA: Double, waterB: Double): Boolean =
        !waterA.isFinite() || !waterB.isFinite() || kotlin.math.abs(waterA - waterB) <= MAX_WATER_DELTA_C

    /** As duas leituras estão no mesmo regime (lenta × condução)? */
    fun sameRegime(rpmA: Double, rpmB: Double): Boolean = (rpmA >= REGIME_SPLIT_RPM) == (rpmB >= REGIME_SPLIT_RPM)

    /** Mesma limpeza de [EquivalenceLedger.setEcuPetrolReference]: pares (MAP, ms) válidos, deduplicados a 1 mbar. */
    fun cleanReference(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val clean = points.filter { (m, t) ->
            m.isFinite() && t.isFinite() && m in 0.05..2.5 && t in EquivalenceLedger.MIN_PETROL_MS..40.0
        }
            .groupBy { Math.round(it.first * 1_000) }
            .map { (_, group) -> group.sumOf { it.first } / group.size to group.sumOf { it.second } / group.size }
            .sortedBy { it.first }
        return if (clean.size >= EquivalenceLedger.ECU_REF_MIN_POINTS &&
            clean.last().first - clean.first().first >= EquivalenceLedger.ECU_REF_MIN_SPAN_BAR
        ) clean else emptyList()
    }

    fun referenceAt(map: Double, ref: List<Pair<Double, Double>>): Double? {
        val margin = EquivalenceLedger.ECU_REF_MARGIN_BAR
        if (ref.isEmpty() || map < ref.first().first - margin || map > ref.last().first + margin) return null
        if (map <= ref.first().first) return ref.first().second
        if (map >= ref.last().first) return ref.last().second
        for (i in 0 until ref.size - 1) {
            val (m0, t0) = ref[i]
            val (m1, t1) = ref[i + 1]
            if (map in m0..m1) return if (m1 > m0) t0 + (t1 - t0) * (map - m0) / (m1 - m0) else t0
        }
        return null
    }

    /** Índice da faixa de Petrol Inj. do livro; ≥ 12 ms = faixa de cauda; < 3 ms = -1 (nunca conta cobertura). */
    fun bandOf(tp: Double): Int {
        EquivalenceLedger.BANDS.forEachIndexed { i, (lo, hi) -> if (tp >= lo && tp < hi) return i }
        return if (tp >= EquivalenceLedger.BANDS.last().second) EquivalenceLedger.BANDS.size else -1
    }

    /**
     * Visita de cada instante: ordena por tempo e abre uma visita nova quando a lacuna até o anterior é
     * ≥ [VISIT_GAP_MS]. Devolve o índice da visita, paralelo a [times] (ordem original).
     */
    fun visitIndexes(times: List<Long>): IntArray {
        val order = times.indices.sortedBy { times[it] }
        val out = IntArray(times.size)
        var visit = -1
        var last = Long.MIN_VALUE
        for (i in order) {
            if (last == Long.MIN_VALUE || times[i] - last >= VISIT_GAP_MS) visit++
            out[i] = visit
            last = times[i]
        }
        return out
    }

    fun visitCount(times: List<Long>): Int = if (times.isEmpty()) 0 else visitIndexes(times).max() + 1

    /** Cobertura interna de uma faixa grossa: pares em ≥ [INTERIOR_MIN_SLICES] de [INTERIOR_SLICES] terços (≥ [INTERIOR_MIN_PAIRS] cada). */
    fun interiorCovered(petrolRefMs: List<Double>, lo: Double, hi: Double): Boolean {
        val width = (hi - lo) / INTERIOR_SLICES
        val counts = IntArray(INTERIOR_SLICES)
        for (tp in petrolRefMs) {
            if (tp < lo || tp >= hi) continue
            counts[((tp - lo) / width).toInt().coerceIn(0, INTERIOR_SLICES - 1)]++
        }
        return counts.count { it >= INTERIOR_MIN_PAIRS } >= INTERIOR_MIN_SLICES
    }

    /**
     * Um par por leitura de GNV: gasolina mediana das leituras de gasolina no mesmo RPM±150/MAP±0,02, no mesmo regime
     * ([sameRegime]: nunca lenta × condução) e com água comparável ([sameWater], ≤ [MAX_WATER_DELTA_C] °C) (≥ 2) ou,
     * sem elas, a curva de gasolina da ECU no MAP. [EquivalenceLedger.EvidencePair.episode] = visita da faixa.
     */
    fun build(
        petrol: Collection<EquivalenceLedger.Obs>,
        gas: Collection<EquivalenceLedger.Obs>,
        ecuRef: List<Pair<Double, Double>>,
    ): List<EquivalenceLedger.EvidencePair> {
        val mr = EquivalenceLedger.MATCH_RPM
        val mm = EquivalenceLedger.MATCH_MAP
        fun rpmCell(rpm: Double) = Math.floorDiv(rpm.toLong(), mr.toLong())
        fun mapCell(map: Double) = Math.floorDiv((map * 1_000).toLong(), (mm * 1_000).toLong())
        val grid = HashMap<Long, MutableList<EquivalenceLedger.Obs>>()
        petrol.forEach { grid.getOrPut(rpmCell(it.rpm) * 1_000_003L + mapCell(it.map)) { ArrayList() }.add(it) }
        val matches = ArrayList<Double>()
        val raw = ArrayList<EquivalenceLedger.EvidencePair>()
        for (g in gas) {
            matches.clear()
            val r0 = rpmCell(g.rpm)
            val m0 = mapCell(g.map)
            for (dr in -1L..1L) for (dm in -1L..1L) {
                grid[(r0 + dr) * 1_000_003L + (m0 + dm)]?.forEach {
                    if (abs(it.rpm - g.rpm) <= mr && abs(it.map - g.map) <= mm && sameRegime(it.rpm, g.rpm) &&
                    sameWater(it.waterC, g.waterC)) matches += it.petrolMs
                }
            }
            if (matches.size >= 2) {
                matches.sort()
                raw += EquivalenceLedger.EvidencePair(matches[matches.size / 2], g.petrolMs, g.rpm, map = g.map, t = g.t)
            } else {
                referenceAt(g.map, ecuRef)?.let {
                    raw += EquivalenceLedger.EvidencePair(it, g.petrolMs, g.rpm, ecuRef = true, map = g.map, t = g.t)
                }
            }
        }
        return withVisitIds(raw)
    }

    /** Par de condução: rpm ≥ [EquivalenceLedger.DRIVING_MIN_RPM] e gasolina de referência ≥ [AutoMatchRefinedEngine.TELEMETRY_MIN_MS]. */
    fun isDriving(p: EquivalenceLedger.EvidencePair): Boolean =
        p.rpm >= EquivalenceLedger.DRIVING_MIN_RPM && p.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS

    /**
     * Troca o id de episódio de cada par de CONDUÇÃO pela visita da sua faixa (lacuna ≥ [VISIT_GAP_MS] entre pares da
     * mesma faixa). Marcha lenta não é condução: não abre nem une visitas, e fica com episódio -1.
     */
    fun withVisitIds(pairs: List<EquivalenceLedger.EvidencePair>): List<EquivalenceLedger.EvidencePair> {
        val byBand = HashMap<Int, MutableList<Int>>()
        pairs.forEachIndexed { i, p -> if (isDriving(p)) byBand.getOrPut(bandOf(p.petrolRefMs)) { ArrayList() }.add(i) }
        val ids = IntArray(pairs.size) { -1 }
        for ((band, members) in byBand) {
            val visits = visitIndexes(members.map { pairs[it].t })
            members.forEachIndexed { k, i -> ids[i] = (band + 1) * EPISODE_BAND_FACTOR + visits[k] }
        }
        return pairs.mapIndexed { i, p -> p.copy(episode = ids[i]) }
    }
}
