package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class EquivalenceInput(
    val axisRaw: IntArray,
    val mulActRaw: IntArray,
    /** ReferenceStore.current(). */
    val reference: Reference?,
    /** ReferenceStore.provisional(acquisition): usada só se [reference] é nula. */
    val provisional: Reference?,
    val petrolObs: List<EquivalenceLedger.Obs>,
    val gasObs: List<EquivalenceLedger.Obs>,
    val experience: ExperienceMeter.Reading,
    val usage: UsageMeter.Reading,
    /** Texto da operação em andamento (a fila de operações preenche; F4 sempre nulo). */
    val operation: String? = null,
    val pointGainScale: DoubleArray? = null,
    /** Histerese da proposta (0 = sem); a produção passa [AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG]. */
    val holdMinStepLog: Double = 0.0,
)

/**
 * O cérebro: do ms de cada ponto da Curva K (eixo PETR_INJ_TBP) ao MAP em que a gasolina o pede (Curva
 * Própria da gasolina), à mistura do GNV nesse MAP (Curva Própria do GNV), ao estado, ao índice e à única
 * próxima ação. A proposta de K sai só do [AutoMatchRefinedEngine] (com todos os portões dele), alimentado por
 * pares das Curvas Próprias. Função pura: observa, nunca grava.
 */
object EquivalenceEngine {
    const val CONFIDENCE_MAX = EquivalenceTolerances.MIN
    const val COLLECT_MIN_USAGE = 0.02
    /** Evidência de um ponto = a célula do MAP equivalente e as vizinhas (a leitura do MAP já tremeria > 0,02 bar). */
    const val POOL_CELLS = 1
    /** Dispersão assumida quando nenhuma célula vizinha tem duas leituras: conservadora. */
    const val DISPERSION_UNKNOWN = 0.10
    private const val Z95 = 1.96
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    private fun f1(value: Double): String = String.format(PT_BR, "%.1f", value)

    fun curveFromSnapshot(snapshot: JSONObject?): Pair<IntArray, IntArray>? {
        val fields = snapshot?.optJSONArray("fields") ?: return null
        var axis: IntArray? = null
        var factors: IntArray? = null
        for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            if (field.optString("status") != "VALID") continue
            val raw = field.optJSONArray("rawValues") ?: continue
            if (raw.length() != AutoMatchRefinedEngine.POINT_COUNT) continue
            when (field.optString("key")) {
                "PETR_INJ_TBP" -> axis = IntArray(raw.length()) { raw.optInt(it) }
                "MUL_ACT" -> factors = IntArray(raw.length()) { raw.optInt(it) }
            }
        }
        val a = axis
        val k = factors
        return if (a != null && k != null) a to k else null
    }

    private fun span(cell: Int): IntRange = max(0, cell - POOL_CELLS)..min(OwnCurveFitter.GRID_CELLS - 1, cell + POOL_CELLS)

    private fun pooledSamples(curve: OwnCurve, cell: Int): Int = span(cell).sumOf { curve.cells[it].samples }

    /** Dispersão combinada (variância ponderada por n−1) das células vizinhas; sem par de leituras = [DISPERSION_UNKNOWN]. */
    private fun pooledDispersion(curve: OwnCurve, cell: Int): Double {
        var num = 0.0
        var den = 0
        for (j in span(cell)) {
            val c = curve.cells[j]
            if (c.samples >= 2) {
                num += (c.samples - 1) * c.dispersion * c.dispersion
                den += c.samples - 1
            }
        }
        return if (den > 0) sqrt(num / den) else DISPERSION_UNKNOWN
    }

    fun evaluate(
        input: EquivalenceInput,
        judge: (List<EquivalencePoint>) -> ProofOutcome = { ProofOutcome.NONE },
    ): EquivalenceResult {
        val prior = input.reference ?: input.provisional
        val ownP = OwnCurveFitter.fit(input.petrolObs, Fuel.GASOLINA, prior)
        val ownG = OwnCurveFitter.fit(input.gasObs, Fuel.GNV, prior)
        val provisional = input.reference == null
        val n = AutoMatchRefinedEngine.POINT_COUNT
        val axis = input.axisRaw.map { it / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS }
        val k = input.mulActRaw.map { it / AutoMatchRefinedEngine.Q14 }
        val valid = input.axisRaw.size == n && input.mulActRaw.size == n && axis[0] > 0.0 &&
            (0 until n - 1).all { axis[it + 1] > axis[it] } && k.all { it > 0.0 }
        if (!valid) {
            return EquivalenceResult(
                emptyList(), null, 0, provisional,
                NextAction(NextActionKind.NOTHING, "Curva K indisponível.", null, null, emptyList()),
                null, ownP, ownG,
            )
        }
        val u = axis.map { ln(it) }
        val x = k.map { ln(it) }
        val usage = input.usage.byPoint(axis, ownP)
        val maps = ArrayList<Double?>(n)
        val base = ArrayList<EquivalencePoint>(n)
        for (i in 0 until n) {
            val tp = axis[i]
            val map = OwnCurveFitter.mapFor(ownP, tp)
            maps += map
            val cell = map?.let { OwnCurveFitter.cellOf(it) }
            val tg = map?.let { ownG.at(it) }
            val kTarget = tg?.let { AutoMatchRefinedEngine.interp(it, axis, k) * it / tp }
            val mixture = tg?.let { AutoMatchRefinedEngine.interp(it, axis, k) * it / tp / k[i] - 1.0 }
            val samples = if (cell != null) pooledSamples(ownG, cell) else 0
            val dispersion = if (cell != null) max(pooledDispersion(ownP, cell), pooledDispersion(ownG, cell)) else 0.0
            val tolerance = EquivalenceTolerances.tolerance(dispersion)
            val state = when {
                tp < AutoMatchRefinedEngine.TELEMETRY_MIN_MS || map == null -> PointState.SEM_DADOS
                samples < AutoMatchRefinedEngine.BAND_MATURE_COUNT || Z95 * dispersion / sqrt(samples.toDouble()) > CONFIDENCE_MAX -> PointState.APRENDENDO
                mixture == null -> PointState.MEDIDO
                abs(mixture) <= tolerance -> PointState.EQUIVALENTE
                mixture > tolerance -> PointState.POBRE
                else -> PointState.RICO
            }
            val sources = LinkedHashSet<String>()
            if (cell != null) {
                val petrolCell = ownP.cells[cell]
                if (samples > 0 || petrolCell.samples > 0) sources += "TELEMETRIA"
                if (petrolCell.source != CellSource.OWN) {
                    if (input.reference != null) sources += "ECU_REF" else if (input.provisional != null) sources += "AUTOCAL"
                }
            }
            var slope: Double? = null
            if (i > 0) slope = abs((x[i] - x[i - 1]) / (u[i] - u[i - 1]))
            if (i < n - 1) {
                val next = abs((x[i + 1] - x[i]) / (u[i + 1] - u[i]))
                slope = if (slope == null) next else max(slope, next)
            }
            base += EquivalencePoint(
                index = i, axisMs = tp, kCurrent = k[i], kTarget = kTarget, mixture = mixture, tolerance = tolerance,
                roughnessRatio = map?.let { input.experience.roughnessRatio(it) },
                nearStallRatio = map?.let { input.experience.nearStallRatio(it) },
                slope = slope, usage = usage[i], samples = samples, sources = sources, state = state,
            )
        }
        val outcome = judge(base)
        val points = base.map { p -> outcome.states[p.index]?.let { p.copy(state = it) } ?: p }
        val (index, coverage) = indexOf(points)
        val proposal = proposalOf(input, ownP, ownG)
        val action = nextAction(input, points, maps, proposal, outcome)
        return EquivalenceResult(points, index, coverage, provisional, action, proposal, ownP, ownG)
    }

    /** Índice = Σ uso·equivalente / Σ uso nos pontos com estado julgável (nem SEM_DADOS, APRENDENDO nem MEDIDO). */
    private fun indexOf(points: List<EquivalencePoint>): Pair<Double?, Int> {
        val counted = points.filter {
            it.state != PointState.SEM_DADOS && it.state != PointState.APRENDENDO && it.state != PointState.MEDIDO && it.mixture != null
        }
        val total = counted.sumOf { it.usage }
        if (total <= 0.0) return null to counted.size
        val equivalent = counted.filter { abs(it.mixture!!) <= it.tolerance }.sumOf { it.usage }
        return equivalent / total to counted.size
    }

    /** Um par (gasolina, GNV) por leitura GNV de condução, lidos nas Curvas Próprias. Sai só do motor refinado, com os portões dele. */
    private fun proposalOf(input: EquivalenceInput, ownP: OwnCurve, ownG: OwnCurve): AutoMatchRefinedEngine.Result? {
        return try {
            val pairs = ArrayList<Pair<Double, Double>>()
            val episodes = ArrayList<Int>()
            for (o in input.gasObs) {
                if (o.rpm < EquivalenceLedger.DRIVING_MIN_RPM) continue
                val tp = ownP.at(o.map) ?: continue
                val tg = ownG.at(o.map) ?: continue
                if (tp >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS) { pairs += tp to tg; episodes += o.episode }
            }
            AutoMatchRefinedEngine.refine(
                AutoMatchRefinedEngine.Input(
                    axisRaw = input.axisRaw, mulActRaw = input.mulActRaw,
                    petrolTimeRaw = null, petrolMapRaw = null, petrolCounts = null,
                    gasTimeRaw = null, gasMapRaw = null, gasCounts = null,
                    telemetryPairs = pairs, pointGainScale = input.pointGainScale,
                    telemetryEpisodes = episodes, holdMinStepLog = input.holdMinStepLog,
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun terrain(map: Double): String = when {
        map < 0.45 -> "em plano"
        map <= 0.75 -> "em subida leve"
        else -> "em subida forte"
    }

    private fun plural(count: Int, singular: String, plural: String) = if (count == 1) singular else plural

    private fun nextAction(
        input: EquivalenceInput,
        points: List<EquivalencePoint>,
        maps: List<Double?>,
        proposal: AutoMatchRefinedEngine.Result?,
        outcome: ProofOutcome,
    ): NextAction {
        input.operation?.let { return NextAction(NextActionKind.OPERATION, it, null, null, emptyList()) }
        if (input.reference == null && input.provisional != null) {
            return NextAction(NextActionKind.FREEZE_REFERENCE, "Congelar esta curva como referência", "autocal", "referencia", emptyList())
        }
        val contested = points.filter { it.state == PointState.CONTESTADO }
        if (contested.isNotEmpty()) {
            val text = "Ajuste em ${f1(contested.minOf { it.axisMs })}–${f1(contested.maxOf { it.axisMs })} ms piorou a suavidade · Desfazer"
            return NextAction(NextActionKind.CONTESTED, text, "curve", "equivalencia", contested.map { it.index })
        }
        val poor = points.filter { it.state == PointState.POBRE }
        val rich = points.filter { it.state == PointState.RICO }
        val off = poor + rich
        if (off.isNotEmpty() && proposal != null && proposal.mode == AutoMatchRefinedEngine.Mode.EQUIVALENCE &&
            proposal.refinedRaw != proposal.currentRaw
        ) {
            val head = when {
                poor.isNotEmpty() && rich.isNotEmpty() ->
                    "${poor.size} ${plural(poor.size, "ponto pobre", "pontos pobres")} e ${rich.size} ${plural(rich.size, "rico", "ricos")}"
                poor.isNotEmpty() -> "${poor.size} ${plural(poor.size, "ponto pobre", "pontos pobres")}"
                else -> "${rich.size} ${plural(rich.size, "ponto rico", "pontos ricos")}"
            }
            val lo = (off.minOf { abs(it.mixture!!) } * 100.0).roundToInt()
            val hi = (off.maxOf { abs(it.mixture!!) } * 100.0).roundToInt()
            val suffix = if (input.reference == null) " · sem referência da ECU" else ""
            val text = "$head entre ${f1(off.minOf { it.axisMs })} e ${f1(off.maxOf { it.axisMs })} ms ($lo–$hi%) · Aplicar ajuste$suffix"
            return NextAction(NextActionKind.APPLY, text, "curve", "equivalencia", off.sortedByDescending { it.usage }.map { it.index })
        }
        if (points.any { it.state == PointState.EM_PROVA }) {
            val remaining = outcome.remainingMinutes
            val text = if (remaining != null) "Rodando para provar o ajuste · faltam ~$remaining min de condução nessa faixa"
            else "Rodando para provar o ajuste"
            return NextAction(NextActionKind.PROVING, text, "refino", "pontos", points.filter { it.state == PointState.EM_PROVA }.map { it.index })
        }
        val candidates = points.filter {
            (it.state == PointState.SEM_DADOS || it.state == PointState.APRENDENDO) && it.usage >= COLLECT_MIN_USAGE && maps[it.index] != null
        }.sortedByDescending { it.usage }
        if (candidates.isNotEmpty()) {
            val top = candidates.first()
            val map = maps[top.index]!!
            val text = "Rode ${terrain(map)} (~${f1(map)} bar) para eu medir entre " +
                "${f1(candidates.minOf { it.axisMs })} e ${f1(candidates.maxOf { it.axisMs })} ms"
            return NextAction(NextActionKind.COLLECT, text, "refino", "pontos", candidates.map { it.index })
        }
        val judged = points.count {
            it.state != PointState.SEM_DADOS && it.state != PointState.APRENDENDO && it.state != PointState.MEDIDO && it.mixture != null
        }
        if (judged == 0) {
            return NextAction(NextActionKind.COLLECT, "Rode no GNV para eu começar a medir", "refino", "pontos", emptyList())
        }
        if (off.isNotEmpty()) {
            // Há pontos fora, mas o motor refinado não tem evidência para propor: pede mais leitura em vez de dizer "nada a fazer".
            return NextAction(
                NextActionKind.COLLECT, "Rode mais no GNV: ainda faltam leituras para propor o ajuste",
                "refino", "pontos", off.sortedByDescending { it.usage }.map { it.index },
            )
        }
        return NextAction(NextActionKind.NOTHING, "Equivalente. Nada a fazer.", null, null, emptyList())
    }
}
