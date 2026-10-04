package com.omegas.prohub.autocal

import kotlin.math.abs

/**
 * Fonte única dos pares (gasolina de referência, GNV) e das regras de independência da evidência.
 *
 * O livro ([EquivalenceLedger]) e o cérebro (`EquivalenceEngine`) usam estas mesmas funções: o veredito
 * "equivalente" e a proposta de K saem do mesmo conjunto de pares (casamento por RPM×MAP).
 *
 * Independência: leituras estáveis consecutivas se sobrepõem (janela de 3 quadros), então o número de
 * leituras NÃO é evidência. Evidência = "visitas": trechos de condução da mesma faixa separados por pelo
 * menos [VISIT_GAP_MS] (60 s). O peso de uma faixa conta por visita, nunca por quadro.
 *
 * Espelho Python: tools/equivalence_oracle (build_pairs, visit_ids) e tools/autocal_refine/blind_telemetry_test.py.
 */
object EvidencePairs {
    const val VISIT_GAP_MS = 60_000L
    /** Visitas distintas que uma faixa (ou o entorno de um ponto) precisa ter para valer como evidência. */
    const val MIN_VISITS = 3
    /** Uma faixa grossa só vale com leituras em pelo menos este número de terços internos... */
    const val INTERIOR_SLICES = 3
    const val INTERIOR_MIN_SLICES = 2
    /** ...cada um com pelo menos este número de pares. */
    const val INTERIOR_MIN_PAIRS = 2
    /** Id de episódio de um par = faixa × este fator + índice da visita (único entre faixas). */
    const val EPISODE_BAND_FACTOR = 100_000

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
     * Um par por leitura de GNV: gasolina mediana das leituras de gasolina no mesmo RPM±150/MAP±0,02 (≥ 2) ou,
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
                    if (abs(it.rpm - g.rpm) <= mr && abs(it.map - g.map) <= mm) matches += it.petrolMs
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
