package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/**
 * Curva Própria: T(MAP) por célula de 0,02 bar, aprendida das leituras estáveis do livro, com a Referência
 * como prior que cai com o número de leituras (n / (n + N0)). Função pura; não toca a ECU.
 *
 * Passos: mediana de ln(ms) por célula → isotônica entre células (peso n) → Whittaker robusto do resíduo
 * sobre o prior (só onde há prior) → isotônica final em ln T (peso n + N0). Sem prior: só as células com
 * leitura e a interpolação entre elas. O oráculo Python (tools/equivalence_oracle) repete cada passo.
 */
object OwnCurveFitter {
    const val GRID_MIN_BAR = 0.10
    /** 0,10..2,50 bar: todo o domínio físico aceito pelo livro, sem o antigo teto visual de 1,10 bar. */
    const val GRID_CELLS = 120
    /** Pseudo-contagem do prior = faixa nativa madura do AutoMatch. */
    const val PRIOR_N0 = 3.0

    /** Célula só vira OWN com este mínimo de leituras (e dispersão ≤ [EquivalenceTolerances.MIN]). */
    const val OWN_MIN_SAMPLES = AutoMatchRefinedEngine.BAND_MATURE_COUNT

    fun cellOf(mapBar: Double): Int? {
        if (!mapBar.isFinite()) return null
        val j = floor((mapBar - GRID_MIN_BAR) / EquivalenceTolerances.CELL_BAR + 1e-9)
        return if (j >= 0.0 && j < GRID_CELLS) j.toInt() else null
    }

    fun center(cell: Int): Double = GRID_MIN_BAR + (cell + 0.5) * EquivalenceTolerances.CELL_BAR

    /**
     * ms da gasolina da Referência no MAP: isotônica em ln (peso = maturidade) + interpolação linear; plana
     * até ±[EquivalenceLedger.ECU_REF_MARGIN_BAR] além das pontas; nulo além disso.
     */
    fun priorAt(reference: Reference, mapBar: Double): Double? {
        val pts = reference.points.sortedBy { it.mapBar }
        if (pts.isEmpty()) return null
        val maps = pts.map { it.mapBar }
        if (mapBar < maps.first() - EquivalenceLedger.ECU_REF_MARGIN_BAR || mapBar > maps.last() + EquivalenceLedger.ECU_REF_MARGIN_BAR) return null
        val fit = AutoMatchRefinedEngine.pava(pts.map { ln(it.petrolMs) }, pts.map { max(1, it.maturity).toDouble() }).map { exp(it) }
        if (mapBar <= maps.first()) return fit.first()
        if (mapBar >= maps.last()) return fit.last()
        return AutoMatchRefinedEngine.interp(mapBar, maps, fit)
    }

    /** Mediana verdadeira (média dos dois do meio com n par): a do índice n/2 enviesa para cima em amostras pequenas. */
    internal fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    fun fit(
        observations: List<EquivalenceLedger.Obs>,
        fuel: Fuel,
        prior: Reference?,
        regime: OperatingRegime = OperatingRegime.DRIVING,
    ): OwnCurve {
        val lns = Array(GRID_CELLS) { ArrayList<Double>() }
        for (o in observations) {
            val j = cellOf(o.map) ?: continue
            if (regime.accepts(o.rpm) && o.petrolMs > 0.0) lns[j].add(ln(o.petrolMs))
        }
        val n = IntArray(GRID_CELLS) { lns[it].size }
        val med = DoubleArray(GRID_CELLS) { if (lns[it].isEmpty()) Double.NaN else median(lns[it]) }
        val disp = DoubleArray(GRID_CELLS)
        for (j in 0 until GRID_CELLS) {
            if (n[j] >= 2) disp[j] = 1.4826 * median(lns[j].map { abs(it - med[j]) })
        }
        val data = (0 until GRID_CELLS).filter { n[it] > 0 }
        val mono = DoubleArray(GRID_CELLS) { Double.NaN }
        if (data.isNotEmpty()) {
            val isotonic = AutoMatchRefinedEngine.pava(data.map { med[it] }, data.map { n[it].toDouble() })
            data.forEachIndexed { k, j -> mono[j] = isotonic[k] }
        }
        val priorMs = Array<Double?>(GRID_CELLS) { j -> prior?.let { priorAt(it, center(j)) } }
        val lnT = arrayOfNulls<Double>(GRID_CELLS)
        val domain = (0 until GRID_CELLS).filter { priorMs[it] != null }
        if (domain.isNotEmpty()) {
            val p = domain.map { ln(priorMs[it]!!) }
            val obs = ArrayList<AutoMatchRefinedEngine.Observation>()
            domain.forEachIndexed { k, j ->
                if (n[j] > 0) {
                    obs += AutoMatchRefinedEngine.Observation(listOf(k to 1.0), mono[j] - p[k], n[j].toDouble() / (n[j] + PRIOR_N0))
                }
            }
            val priorWeights = domain.map { PRIOR_N0 / (n[it] + PRIOR_N0) }
            val (r, _) = AutoMatchRefinedEngine.whittaker(
                domain.map { center(it) }, obs, List(domain.size) { 0.0 }, priorWeights, AutoMatchRefinedEngine.LAMBDA,
            )
            domain.forEachIndexed { k, j -> lnT[j] = p[k] + r[k] }
            for (j in data) if (priorMs[j] == null) lnT[j] = mono[j]
        } else if (data.isNotEmpty()) {
            for (j in data) lnT[j] = mono[j]
            if (data.size >= 2) {
                val xs = data.map { center(it) }
                val ys = data.map { mono[it] }
                for (j in data.first()..data.last()) {
                    if (lnT[j] == null) lnT[j] = AutoMatchRefinedEngine.interp(center(j), xs, ys)
                }
            }
        }
        val live = (0 until GRID_CELLS).filter { lnT[it] != null }
        if (live.isNotEmpty()) {
            val isotonic = AutoMatchRefinedEngine.pava(live.map { lnT[it]!! }, live.map { n[it] + PRIOR_N0 })
            live.forEachIndexed { k, j -> lnT[j] = isotonic[k] }
        }
        val hasPrior = prior != null && prior.points.isNotEmpty()
        val cells = (0 until GRID_CELLS).map { j ->
            val t = lnT[j]?.let { exp(it) }
            val source = when {
                n[j] == 0 -> if (hasPrior && t != null) CellSource.REFERENCE else CellSource.BLENDED
                n[j] >= OWN_MIN_SAMPLES && disp[j] <= EquivalenceTolerances.MIN -> CellSource.OWN
                else -> CellSource.BLENDED
            }
            val pj = priorMs[j]
            val divergence = if (t != null && pj != null && n[j] > 0) t / pj - 1.0 else null
            OwnCell(center(j), t, n[j], disp[j], source, divergence)
        }
        return OwnCurve(fuel, cells)
    }

    /** MAP da primeira travessia do ms pela curva (segmentos entre células com valor). Nulo se nunca cruza. */
    fun mapFor(curve: OwnCurve, petrolMs: Double): Double? {
        val known = curve.cells.filter { it.petrolMs != null }
        for (i in 0 until known.size - 1) {
            val m0 = known[i].mapBar
            val m1 = known[i + 1].mapBar
            val t0 = known[i].petrolMs ?: continue
            val t1 = known[i + 1].petrolMs ?: continue
            if (t0 == t1) continue
            if (petrolMs >= minOf(t0, t1) && petrolMs <= maxOf(t0, t1)) return m0 + (m1 - m0) * (petrolMs - t0) / (t1 - t0)
        }
        return null
    }
}
