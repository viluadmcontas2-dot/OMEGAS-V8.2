package com.omegas.prohub.autocal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Equivalência Refinada OMEGAS da Curva K (MUL_ACT).
 *
 * Evidência que motivou o motor (docs/workunits/OMEGAS-WU-006.md):
 *  - o AutoMatch nativo aplica ganho total ponto a ponto sobre curvas RV montadas
 *    com bandas de 1–2 amostras, sem suavização, limitado a [0,75; 1,20] — gera
 *    dentes de serra;
 *  - degraus de K (|d ln K / d ln t| ≈ 1,9 entre 8 e 9 ms na curva de referência) fazem
 *    o GNV entregar gás de forma não linear ao pedido da gasolina (puxada com trancos);
 *  - teste cego com a telemetria em gasolina no mesmo RPM×MAP (não usada pelo motor)
 *    prefere a curva refinada e a trava de inclinação [E_MAX] (blind_telemetry_test.py).
 *
 * Pipeline: evidência por banda (buffers + contagem) → ajuste isotônico robusto de
 * T(MAP) por combustível → equivalência exata K_alvo(T_p) = K(T_g)·T_g/T_p →
 * Whittaker robusto em ln K sobre u = ln t → trava de coerência (passo ≤ ±15% e
 * |Δ ln K/Δ ln t| ≤ [E_MAX]).
 *
 * Puro: não acessa USB nem grava na ECU. O oráculo de referência é
 * `tools/autocal_refine/refined_oracle.py`; a paridade é testada por
 * `tests/test_refined_autocal_contract.py`.
 */
object AutoMatchRefinedEngine {
    const val ALGORITHM = "OMEGAS_REFINED_EQUIVALENCE_V1"
    const val POINT_COUNT = 30
    const val BAND_COUNT = 18
    const val AXIS_COUNTS_PER_MS = 512.0
    const val MAP_COUNTS_PER_BAR = 1024.0
    const val Q14 = 16384.0
    const val MAX_RAW = 65535
    const val MIN_FACTOR = 0.60
    const val MAX_FACTOR = MAX_RAW / Q14

    const val BAND_FULL_COUNT = 6
    const val BAND_MATURE_COUNT = 3
    const val MIN_COMMON_MATURE = 4
    const val OUTLIER_MIN_LOG = 0.05
    const val OUTLIER_MAD_K = 3.0
    /** Rigidez escolhida por validação cruzada nas sessões reais (prever faixa omitida), não por estética. */
    const val LAMBDA = 0.3
    const val PRIOR_SUPPORTED = 0.05
    const val PRIOR_UNSUPPORTED = 1.0
    const val EVIDENCE_REF = 0.5
    val MAX_STEP_LOG = ln(1.15)
    const val E_MAX = 0.35
    const val IRLS_ITERATIONS = 6
    const val TUKEY_C = 4.685
    const val SMOOTH_TOLERANCE_LOG = 0.0025
    /** Peso de cada par GNV×gasolina da telemetria (validado em metade escondida da volta). */
    const val TELEMETRY_WEIGHT = 0.4
    /** Abaixo disso a telemetria é dominada por transiente/corte (erro ~15%). */
    const val TELEMETRY_MIN_MS = 3.0

    data class Input(
        val axisRaw: IntArray,
        val mulActRaw: IntArray,
        val petrolTimeRaw: IntArray?,
        val petrolMapRaw: IntArray?,
        val petrolCounts: IntArray?,
        val gasTimeRaw: IntArray?,
        val gasMapRaw: IntArray?,
        val gasCounts: IntArray?,
        /** Pares (t_gasolina de referência, t_no_GNV) da condução, medidos com a curva vigente. */
        val telemetryPairs: List<kotlin.Pair<Double, Double>> = emptyList(),
        /** Ganho aprendido por ponto (RefinementJournal): <1 suaviza, >1 firma a correção. */
        val pointGainScale: DoubleArray? = null,
    )

    enum class Mode { EQUIVALENCE, POLISH, UNAVAILABLE }

    /** MEASURED: evidência forte; BLENDED: parcial; SMOOTHED: só coerência; HELD: inalterado. */
    enum class Origin { MEASURED, BLENDED, SMOOTHED, HELD }

    data class BandPoint(
        val band: Int,
        val mapBar: Double,
        val timeMs: Double,
        val weight: Double,
        val count: Int,
        val fitTimeMs: Double = timeMs,
    )

    data class Target(
        val mapBar: Double,
        val petrolMs: Double,
        val gasMs: Double,
        val weight: Double,
        val ratio: Double,
        val logTarget: Double,
        val robustWeight: Double = 1.0,
    )

    data class RejectedBand(val fuel: String, val band: Int, val mapBar: Double, val timeMs: Double)

    data class Metrics(
        val maxNeighborStep: Double,
        val maxElasticity: Double,
        val roughness: Double,
        val slopeSignChanges: Int,
    )

    data class Result(
        val mode: Mode,
        val reason: String?,
        val matureCommonPoints: Int,
        val axisMs: List<Double>,
        val currentRaw: List<Int>,
        val refinedRaw: List<Int>,
        val origins: List<Origin>,
        val gain: List<Double>,
        val elasticityLimit: Double,
        val needsAnotherPass: Boolean,
        val targets: List<Target>,
        val rejectedBands: List<RejectedBand>,
        /** Quantos alvos vieram da telemetria da condução (além das faixas nativas). */
        val telemetryTargetCount: Int = 0,
        val metricsBefore: Metrics?,
        val metricsAfter: Metrics?,
        /** Erro médio ponderado entre o K pedido pela medição e a curva (fração); null sem equivalência. */
        val evidenceErrorBefore: Double? = null,
        val evidenceErrorAfter: Double? = null,
    ) {
        val equivalenceAvailable: Boolean get() = mode == Mode.EQUIVALENCE
        val available: Boolean get() = mode != Mode.UNAVAILABLE
    }

    fun refine(input: Input): Result {
        val axisRaw = input.axisRaw
        val kRaw = input.mulActRaw
        if (axisRaw.size != POINT_COUNT || kRaw.size != POINT_COUNT) {
            return unavailable("EIXO_OU_MUL_ACT_INDISPONIVEL")
        }
        val axisMs = axisRaw.map { it / AXIS_COUNTS_PER_MS }
        if (axisMs[0] <= 0.0 || (0 until POINT_COUNT - 1).any { axisMs[it + 1] <= axisMs[it] }) {
            return unavailable("EIXO_NAO_CRESCENTE")
        }
        val kOld = kRaw.map { it / Q14 }
        if (kOld.any { it <= 0.0 }) return unavailable("MUL_ACT_INVALIDO")
        val u = axisMs.map { ln(it) }
        val x0 = kOld.map { ln(it) }

        var targets: List<Target> = emptyList()
        val rejected = mutableListOf<RejectedBand>()
        if (input.petrolTimeRaw != null && input.petrolMapRaw != null && input.petrolCounts != null &&
            input.gasTimeRaw != null && input.gasMapRaw != null && input.gasCounts != null &&
            listOf(input.petrolTimeRaw, input.petrolMapRaw, input.petrolCounts, input.gasTimeRaw, input.gasMapRaw, input.gasCounts)
                .all { it.size == BAND_COUNT }
        ) {
            val (petrol, rp) = monotoneFit(bandPoints(input.petrolTimeRaw, input.petrolMapRaw, input.petrolCounts))
            val (gas, rg) = monotoneFit(bandPoints(input.gasTimeRaw, input.gasMapRaw, input.gasCounts))
            rp.forEach { rejected += RejectedBand("GASOLINA", it.band, it.mapBar, it.timeMs) }
            rg.forEach { rejected += RejectedBand("GNV", it.band, it.mapBar, it.timeMs) }
            if (petrol.size >= 2 && gas.size >= 2) targets = equivalenceTargets(petrol, gas, axisMs, kOld)
        }
        val matureWeight = BAND_MATURE_COUNT.toDouble() / BAND_FULL_COUNT
        val mature = targets.count { it.weight >= matureWeight }
        val equivalence = mature >= MIN_COMMON_MATURE
        // A telemetria complementa as faixas nativas; nunca habilita a equivalência sozinha.
        val bandTargetCount = targets.size
        if (equivalence && input.telemetryPairs.isNotEmpty()) {
            targets = targets + input.telemetryPairs.mapNotNull { (tp, tg) ->
                if (tp < TELEMETRY_MIN_MS || tg <= 0.0 || tp > axisMs.last()) return@mapNotNull null
                Target(Double.NaN, tp, tg, TELEMETRY_WEIGHT, tg / tp, ln(interp(tg, axisMs, kOld) * tg / tp))
            }
        }

        val observations = if (equivalence) {
            targets.map { Observation(axisWeights(it.petrolMs, axisMs), it.logTarget, it.weight) }
        } else emptyList()

        val evidence = DoubleArray(POINT_COUNT)
        observations.forEach { o -> o.a.forEach { (j, a) -> evidence[j] += o.w * a } }
        val gain = List(POINT_COUNT) { j ->
            var spread = 0.5 * evidence[j]
            if (j > 0) spread += 0.25 * evidence[j - 1]
            if (j < POINT_COUNT - 1) spread += 0.25 * evidence[j + 1]
            min(1.0, spread / EVIDENCE_REF)
        }
        val priorWeights = gain.map { g -> PRIOR_UNSUPPORTED * (1.0 - g) + PRIOR_SUPPORTED * g }
        val (fitted, robust) = whittaker(u, observations, x0, priorWeights, LAMBDA)
        if (equivalence) targets = targets.mapIndexed { i, t -> t.copy(robustWeight = robust[i]) }
        val scale = input.pointGainScale?.takeIf { it.size == POINT_COUNT }
        val scaled = if (scale == null) fitted else fitted.mapIndexed { j, z -> x0[j] + scale[j] * (z - x0[j]) }
        val eEff = effectiveElasticity(x0, u)
        val final = enforceCoherence(scaled, x0, u, eEff)

        val origins = List(POINT_COUNT) { j ->
            when {
                gain[j] >= 0.5 -> Origin.MEASURED
                gain[j] > 0.0 -> Origin.BLENDED
                abs(final[j] - x0[j]) > SMOOTH_TOLERANCE_LOG -> Origin.SMOOTHED
                else -> Origin.HELD
            }
        }
        val outRaw = final.mapIndexed { j, z ->
            // Sem evidência e sem anomalia: preserva exatamente o valor gravado.
            if (origins[j] == Origin.HELD) return@mapIndexed kRaw[j]
            val factor = exp(z).coerceIn(MIN_FACTOR, MAX_FACTOR)
            (factor * Q14).roundToInt().coerceIn(0, MAX_RAW)
        }
        return Result(
            mode = if (equivalence) Mode.EQUIVALENCE else Mode.POLISH,
            reason = if (equivalence) null else "BANDAS_COMUNS_MADURAS_INSUFICIENTES",
            matureCommonPoints = mature,
            axisMs = axisMs,
            currentRaw = kRaw.toList(),
            refinedRaw = outRaw,
            origins = origins,
            gain = gain,
            elasticityLimit = eEff,
            needsAnotherPass = eEff > E_MAX + 1e-9,
            targets = targets,
            rejectedBands = rejected,
            telemetryTargetCount = targets.size - bandTargetCount,
            metricsBefore = metrics(kOld, axisMs),
            metricsAfter = metrics(outRaw.map { it / Q14 }, axisMs),
            evidenceErrorBefore = evidenceError(if (equivalence) targets.filter { it.weight >= matureWeight } else emptyList(), axisMs, kOld),
            evidenceErrorAfter = evidenceError(if (equivalence) targets.filter { it.weight >= matureWeight } else emptyList(), axisMs, outRaw.map { it / Q14 }),
        )
    }

    private fun evidenceError(targets: List<Target>, axisMs: List<Double>, factors: List<Double>): Double? {
        if (targets.isEmpty()) return null
        val total = targets.sumOf { it.weight }
        val sq = targets.sumOf { t -> val d = ln(interp(t.petrolMs, axisMs, factors)) - t.logTarget; t.weight * d * d }
        return exp(kotlin.math.sqrt(sq / total)) - 1.0
    }

    fun metrics(factors: List<Double>, axisMs: List<Double>, lo: Int = 2, hi: Int = 22): Metrics {
        val logs = factors.map { ln(it) }
        val u = axisMs.map { ln(it) }
        var maxStep = 0.0
        var maxElastic = 0.0
        for (j in lo until hi) {
            maxStep = max(maxStep, abs(factors[j + 1] / factors[j] - 1.0))
            maxElastic = max(maxElastic, abs((logs[j + 1] - logs[j]) / (u[j + 1] - u[j])))
        }
        var rough = 0.0
        for (j in lo + 1 until hi) {
            val d = logs[j + 1] - 2 * logs[j] + logs[j - 1]
            rough += d * d
        }
        var changes = 0
        var last = 0
        for (j in lo until hi) {
            val d = logs[j + 1] - logs[j]
            val s = if (abs(d) < 0.005) 0 else if (d > 0) 1 else -1
            if (s != 0 && last != 0 && s != last) changes++
            if (s != 0) last = s
        }
        return Metrics(maxStep, maxElastic, rough, changes)
    }

    // ------------------------------------------------------------- evidência

    internal fun bandPoints(timeRaw: IntArray, mapRaw: IntArray, counts: IntArray): List<BandPoint> =
        (0 until BAND_COUNT).mapNotNull { band ->
            val n = counts[band]
            if (n <= 0 || timeRaw[band] <= 0 || mapRaw[band] <= 0) return@mapNotNull null
            BandPoint(
                band = band,
                mapBar = mapRaw[band] / MAP_COUNTS_PER_BAR,
                timeMs = timeRaw[band] / AXIS_COUNTS_PER_MS,
                weight = min(n, BAND_FULL_COUNT).toDouble() / BAND_FULL_COUNT,
                count = n,
            )
        }.sortedBy { it.mapBar }

    internal fun pava(ys: List<Double>, ws: List<Double>): List<Double> {
        val sums = ArrayList<DoubleArray>() // [Σwy, Σw, quantidade]
        ys.forEachIndexed { i, y ->
            sums += doubleArrayOf(y * ws[i], ws[i], 1.0)
            while (sums.size > 1 && sums[sums.size - 2][0] / sums[sums.size - 2][1] > sums.last()[0] / sums.last()[1]) {
                val b = sums.removeAt(sums.size - 1)
                val a = sums.last()
                a[0] += b[0]; a[1] += b[1]; a[2] += b[2]
            }
        }
        val out = ArrayList<Double>(ys.size)
        sums.forEach { b -> repeat(b[2].toInt()) { out += b[0] / b[1] } }
        return out
    }

    internal fun monotoneFit(points: List<BandPoint>): Pair<List<BandPoint>, List<BandPoint>> {
        val accepted = points.toMutableList()
        val rejected = mutableListOf<BandPoint>()
        repeat(3) {
            if (accepted.size < 2) return@repeat
            val logs = accepted.map { ln(it.timeMs) }
            val fit = pava(logs, accepted.map { it.weight })
            val residuals = logs.indices.map { logs[it] - fit[it] }
            val loo = accepted.indices.map { i ->
                if (i > 0 && i < accepted.size - 1) {
                    val a = accepted[i - 1]
                    val b = accepted[i + 1]
                    val expected = interp(accepted[i].mapBar, listOf(a.mapBar, b.mapBar), listOf(ln(a.timeMs), ln(b.timeMs)))
                    ln(accepted[i].timeMs) - expected
                } else residuals[i]
            }
            val mad = weightedMedian(loo.map { abs(it) }, accepted.map { it.weight }) * 1.4826
            val limit = max(OUTLIER_MIN_LOG, OUTLIER_MAD_K * mad)
            val worst = accepted.indices
                .filter { abs(loo[it]) > limit && abs(residuals[it]) > 1e-9 }
                .maxByOrNull { abs(loo[it]) }
                ?: return finishFit(accepted, rejected)
            rejected += accepted.removeAt(worst)
        }
        return finishFit(accepted, rejected)
    }

    private fun finishFit(accepted: List<BandPoint>, rejected: List<BandPoint>): Pair<List<BandPoint>, List<BandPoint>> {
        if (accepted.isEmpty()) return accepted to rejected
        val fit = pava(accepted.map { ln(it.timeMs) }, accepted.map { it.weight })
        return accepted.mapIndexed { i, p -> p.copy(fitTimeMs = exp(fit[i])) } to rejected
    }

    private fun equivalenceTargets(
        petrol: List<BandPoint>,
        gas: List<BandPoint>,
        axisMs: List<Double>,
        kOld: List<Double>,
    ): List<Target> {
        val pm = petrol.map { it.mapBar }
        val pt = petrol.map { it.fitTimeMs }
        val pw = petrol.map { it.weight }
        val gm = gas.map { it.mapBar }
        val gt = gas.map { it.fitTimeMs }
        val gw = gas.map { it.weight }
        val lo = max(pm.first(), gm.first())
        val hi = min(pm.last(), gm.last())
        val grid = (pm + gm).filter { it in lo..hi }.distinct().sorted()
        return grid.mapNotNull { m ->
            val tp = interp(m, pm, pt)
            val tg = interp(m, gm, gt)
            val w = min(localWeight(m, pm, pw), localWeight(m, gm, gw))
            if (w <= 0.0 || tp <= 0.0 || tg <= 0.0) return@mapNotNull null
            val kAtGas = interp(tg, axisMs, kOld)
            Target(m, tp, tg, w, tg / tp, ln(kAtGas * tg / tp))
        }
    }

    private fun localWeight(m: Double, maps: List<Double>, weights: List<Double>): Double {
        if (m <= maps.first()) return if (abs(m - maps.first()) < 1e-9) weights.first() else 0.0
        if (m >= maps.last()) return if (abs(m - maps.last()) < 1e-9) weights.last() else 0.0
        for (i in 1 until maps.size) {
            if (m <= maps[i]) {
                if (abs(m - maps[i]) < 1e-9) return weights[i]
                return min(weights[i - 1], weights[i])
            }
        }
        return 0.0
    }

    // ---------------------------------------------------------------- ajuste

    private class Observation(val a: List<Pair<Int, Double>>, val y: Double, val w: Double)

    private fun secondDifferenceRows(u: List<Double>): List<DoubleArray> = (1 until u.size - 1).map { j ->
        val h0 = u[j] - u[j - 1]
        val h1 = u[j + 1] - u[j]
        val scale = 2.0 / (h0 + h1)
        val meanH = (h0 + h1) / 2.0
        val norm = meanH * meanH
        DoubleArray(u.size).also { row ->
            row[j - 1] = scale / h0 * norm
            row[j] = -scale * (1.0 / h0 + 1.0 / h1) * norm
            row[j + 1] = scale / h1 * norm
        }
    }

    private fun whittaker(
        u: List<Double>,
        observations: List<Observation>,
        prior: List<Double>,
        priorWeights: List<Double>,
        lambda: Double,
    ): Pair<List<Double>, List<Double>> {
        val n = u.size
        val d2 = secondDifferenceRows(u)
        var obsRobust = List(observations.size) { 1.0 }
        var priorRobust = List(n) { 1.0 }
        var x = prior
        repeat(IRLS_ITERATIONS) {
            val m = Array(n) { DoubleArray(n) }
            val v = DoubleArray(n)
            d2.forEach { row ->
                val nz = row.indices.filter { row[it] != 0.0 }
                nz.forEach { j -> nz.forEach { k -> m[j][k] += lambda * row[j] * row[k] } }
            }
            for (j in 0 until n) {
                val w = priorWeights[j] * priorRobust[j]
                m[j][j] += w
                v[j] += w * prior[j]
            }
            observations.forEachIndexed { i, o ->
                val w = o.w * obsRobust[i]
                o.a.forEach { (j, aj) ->
                    v[j] += w * aj * o.y
                    o.a.forEach { (k, ak) -> m[j][k] += w * aj * ak }
                }
            }
            x = solve(m, v)
            val residuals = observations.map { o -> o.y - o.a.sumOf { (j, a) -> a * x[j] } }
            val priorRes = (0 until n).map { prior[it] - x[it] }
            val pool = (residuals.map { abs(it) } + priorRes.map { abs(it) }).sorted()
            val scale = max(pool[pool.size / 2] * 1.4826, 0.01)
            obsRobust = residuals.map { tukey(it / (TUKEY_C * scale)) }
            priorRobust = priorRes.map { max(tukey(it / (TUKEY_C * scale)), 0.05) }
        }
        return x to obsRobust
    }

    private fun tukey(z: Double): Double = if (abs(z) >= 1.0) 0.0 else (1.0 - z * z).let { it * it }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): List<Double> {
        val n = vector.size
        val a = Array(n) { r -> DoubleArray(n + 1).also { row -> matrix[r].copyInto(row); row[n] = vector[r] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            require(abs(p) >= 1e-15) { "sistema singular" }
            for (r in col + 1 until n) {
                val factor = a[r][col] / p
                if (factor != 0.0) for (c in col..n) a[r][c] -= factor * a[col][c]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var sum = 0.0
            for (c in r + 1 until n) sum += a[r][c] * x[c]
            x[r] = (a[r][n] - sum) / a[r][r]
        }
        return x.toList()
    }

    // ---------------------------------------------------- trava de coerência

    internal fun coherenceFeasible(x0: List<Double>, u: List<Double>, e: Double): Boolean {
        val n = x0.size
        for (j in 0 until n) {
            var hi = Double.MAX_VALUE
            var lo = -Double.MAX_VALUE
            for (k in 0 until n) {
                hi = min(hi, x0[k] + MAX_STEP_LOG + e * abs(u[j] - u[k]))
                lo = max(lo, x0[k] - MAX_STEP_LOG - e * abs(u[j] - u[k]))
            }
            if (lo > hi + 1e-12) return false
        }
        return true
    }

    internal fun effectiveElasticity(x0: List<Double>, u: List<Double>): Double {
        if (coherenceFeasible(x0, u, E_MAX)) return E_MAX
        var lo = E_MAX
        var hi = 8.0
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (coherenceFeasible(x0, u, mid)) hi = mid else lo = mid
        }
        return hi
    }

    internal fun enforceCoherence(z0: List<Double>, x0: List<Double>, u: List<Double>, e: Double): List<Double> {
        val z = z0.toDoubleArray()
        val n = z.size
        for (iteration in 0 until 20000) {
            var changed = false
            for (j in 0 until n) {
                val lo = x0[j] - MAX_STEP_LOG
                val hi = x0[j] + MAX_STEP_LOG
                if (z[j] < lo - 1e-12 || z[j] > hi + 1e-12) {
                    z[j] = min(max(z[j], lo), hi)
                    changed = true
                }
            }
            for (j in 0 until n - 1) {
                val limit = e * (u[j + 1] - u[j])
                val diff = z[j + 1] - z[j]
                if (abs(diff) > limit + 1e-9) {
                    val excess = (abs(diff) - limit) / 2.0
                    val sign = if (diff > 0) 1.0 else -1.0
                    z[j] += sign * excess
                    z[j + 1] -= sign * excess
                    changed = true
                }
            }
            if (!changed) break
        }
        return z.toList()
    }

    // ------------------------------------------------------------ utilidades

    internal fun interp(x: Double, xs: List<Double>, ys: List<Double>): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        for (i in 1 until xs.size) {
            if (x <= xs[i]) {
                val span = xs[i] - xs[i - 1]
                if (span <= 0.0) return ys[i - 1]
                return ys[i - 1] + (ys[i] - ys[i - 1]) * ((x - xs[i - 1]) / span)
            }
        }
        return ys.last()
    }

    private fun axisWeights(x: Double, xs: List<Double>): List<Pair<Int, Double>> {
        if (x <= xs.first()) return listOf(0 to 1.0)
        if (x >= xs.last()) return listOf(xs.lastIndex to 1.0)
        for (i in 1 until xs.size) {
            if (x <= xs[i]) {
                val f = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
                return listOf(i - 1 to 1.0 - f, i to f)
            }
        }
        return listOf(xs.lastIndex to 1.0)
    }

    private fun weightedMedian(values: List<Double>, weights: List<Double>): Double {
        val pairs = values.zip(weights).sortedWith(compareBy({ it.first }, { it.second }))
        val total = weights.sum()
        var acc = 0.0
        for ((value, weight) in pairs) {
            acc += weight
            if (acc >= total / 2.0) return value
        }
        return pairs.last().first
    }

    private fun unavailable(reason: String) = Result(
        mode = Mode.UNAVAILABLE,
        reason = reason,
        matureCommonPoints = 0,
        axisMs = emptyList(),
        currentRaw = emptyList(),
        refinedRaw = emptyList(),
        origins = emptyList(),
        gain = emptyList(),
        elasticityLimit = E_MAX,
        needsAnotherPass = false,
        targets = emptyList(),
        rejectedBands = emptyList(),
        metricsBefore = null,
        metricsAfter = null,
    )
}
