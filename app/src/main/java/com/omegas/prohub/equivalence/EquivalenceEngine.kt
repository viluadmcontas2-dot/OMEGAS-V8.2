package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.AutoMatchSnapshotAnalysis
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EvidencePairs
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
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
    /** Faixas nativas da ECU (a curva dela é a BASE do refino); nulo quando a leitura não as trouxe ou não são coerentes. */
    val native: NativeBands? = null,
)

/** Buffers de aquisição da ECU (18 faixas por combustível) lidos do snapshot; o motor refinado os usa como base. */
class NativeBands(
    val petrolTimeRaw: IntArray, val petrolMapRaw: IntArray, val petrolCounts: IntArray,
    val gasTimeRaw: IntArray, val gasMapRaw: IntArray, val gasCounts: IntArray,
    val pressureThresholdsRaw: IntArray?,
) {
    companion object {
        /** Extrai os buffers do snapshot como AutoMatchSnapshotAnalysis (grupo de aquisição coerente; época do GNV). */
        fun fromSnapshot(
            snapshot: JSONObject?,
            epoch: com.omegas.prohub.autocal.NativeGasEvidenceEpoch = com.omegas.prohub.autocal.NativeGasEvidenceEpoch.shared,
        ): NativeBands? {
            if (snapshot == null) return null
            if (snapshot.has("temporalCoherent") && !snapshot.optBoolean("temporalCoherent", true)) return null
            val groups = snapshot.optJSONArray("coherenceGroups")
            val group = (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }
                .firstOrNull { it.optString("key") == "ACQUISITION_CURRENT" }
            if (group != null && !group.optBoolean("coherent", false)) return null
            val fields = snapshot.optJSONArray("fields") ?: return null
            fun raw(key: String, size: Int): IntArray? {
                for (i in 0 until fields.length()) {
                    val f = fields.optJSONObject(i) ?: continue
                    if (f.optString("key") != key) continue
                    if (f.optString("status") != "VALID") return null
                    val a = f.optJSONArray("rawValues") ?: return null
                    return if (a.length() == size) IntArray(size) { a.optInt(it) } else null
                }
                return null
            }
            val n = AutoMatchRefinedEngine.BAND_COUNT
            val pt = raw("PETR_INJ_TBUF", n) ?: return null
            val pm = raw("MNFLD_PRESS_BUF", n) ?: return null
            val pc = raw("NUM_BUF_UPD_PETR", n) ?: return null
            val gt = raw("PETR_INJ_TBUF_GAS", n) ?: return null
            val gm = raw("MNFLD_PRESS_BUF_GAS", n) ?: return null
            val gcRaw = raw("NUM_BUF_UPD_GAS", n) ?: return null
            fun time(key: String): Long = (0 until fields.length()).mapNotNull { fields.optJSONObject(it) }
                .firstOrNull { it.optString("key") == key }?.optLong("capturedAtMs", 0L)?.takeIf { it > 0L }
                ?: snapshot.optLong("capturedAtMs", 0L)
            val gc = epoch.effectiveGasCounts(gcRaw, time("NUM_BUF_UPD_GAS"), time("MUL_ACT"))
            return NativeBands(pt, pm, pc, gt, gm, gc, raw("MNFLD_PRESS_THD", n))
        }
    }
}

/**
 * O cérebro: do ms de cada ponto da Curva K (eixo PETR_INJ_TBP) ao MAP em que a gasolina o pede (Curva
 * Própria da gasolina), à mistura do GNV nesse MAP (Curva Própria do GNV), ao estado, ao índice e à única
 * próxima ação. A proposta de K sai só do [AutoMatchRefinedEngine] (com todos os portões dele), alimentado por
 * pares das Curvas Próprias. Função pura: observa, nunca grava.
 */
object EquivalenceEngine {
    const val CONFIDENCE_MAX = EquivalenceTolerances.MIN
    const val COLLECT_MIN_USAGE = 0.02
    /** Leituras (pares) mínimas ao redor de um ponto para julgá-lo (= faixa nativa madura). */
    const val MIN_POINT_PAIRS = AutoMatchRefinedEngine.BAND_MATURE_COUNT
    /** n efetivo mínimo (amostras decorrelacionadas) ao redor de um ponto; a confiança em si vem do intervalo. */
    const val MIN_POINT_EPISODES = 3
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    private fun f1(value: Double): String = String.format(PT_BR, "%.1f", value)

    /**
     * (PETR_INJ_TBP, MUL_ACT) do snapshot da ECU, ambos VALID com 30 valores. Snapshot incoerente no tempo
     * (`temporalCoherent == false`, grupos lidos em instantes incompatíveis) não é base de nada: nulo, como em
     * AutoMatchSnapshotAnalysis. `partial` é true em todo snapshot real e NÃO é critério.
     */
    fun curveFromSnapshot(snapshot: JSONObject?): Pair<IntArray, IntArray>? {
        if (snapshot != null && snapshot.has("temporalCoherent") && !snapshot.optBoolean("temporalCoherent", true)) return null
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

    private fun median(values: List<Double>): Double = OwnCurveFitter.median(values)

    /**
     * Regra única de "julgável" (sem relógio): pares suficientes, n efetivo mínimo e o intervalo de confiança do erro
     * (t·dispersão/√n efetivo) dentro da margem do dono (±4%). Pouco dado = intervalo largo = ainda sem certeza.
     */
    fun isJudgeable(pairs: Int, nEff: Double, dispersion: Double?): Boolean =
        dispersion != null && pairs >= MIN_POINT_PAIRS && nEff >= MIN_POINT_EPISODES &&
            EvidencePairs.tCritical(nEff) * dispersion / sqrt(nEff) <= CONFIDENCE_MAX

    /** Evidência de um ponto: as leituras (pares GNV × gasolina por RPM×MAP, ou pela curva da ECU) ao redor dele. */
    private class PointEvidence(
        val pairs: Int,
        /** n efetivo arredondado (amostras decorrelacionadas); 0 sem pares. */
        val episodes: Int,
        val nEff: Double,
        /** Mediana por visita e depois entre visitas (peso por episódio, não por quadro); nulo sem pares. */
        val mixture: Double?,
        /** Dispersão robusta (1,4826·MAD em ln); nula com menos de 2 pares: dispersão desconhecida não julga. */
        val dispersion: Double?,
        val ecuShare: Double,
    ) {
        val judgeable: Boolean
            get() = mixture != null && isJudgeable(pairs, nEff, dispersion)
    }

    /**
     * Pares ao redor do nó [i] (entre os nós vizinhos, em ln ms). Mistura de cada par = o K que a condução pede sobre o
     * K de agora no mesmo ms da gasolina: K(t_gnv)·t_gnv / (t_gas·K(t_gas)) − 1. É o mesmo conjunto de pares de onde
     * sai a proposta de K: veredito e proposta não podem se contradizer.
     */
    private fun evidenceAt(i: Int, u: List<Double>, pairs: List<EquivalenceLedger.EvidencePair>, pairU: DoubleArray, pairLn: DoubleArray): PointEvidence {
        val n = u.size
        val lo = if (i > 0) u[i - 1] else u[0] - (u[1] - u[0])
        val hi = if (i < n - 1) u[i + 1] else u[n - 1] + (u[n - 1] - u[n - 2])
        val members = pairs.indices.filter { pairU[it] >= lo && pairU[it] <= hi }
        if (members.isEmpty()) return PointEvidence(0, 0, 0.0, null, null, 0.0)
        val ids = visitIds(members.map { pairs[it].t })
        val perVisit = HashMap<Int, MutableList<Double>>()
        members.forEachIndexed { k, m -> perVisit.getOrPut(ids[k]) { ArrayList() } += pairLn[m] }
        val center = median(perVisit.values.map { median(it) })
        val dispersion = if (members.size >= 2) {
            val all = members.map { pairLn[it] }
            val mid = median(all)
            1.4826 * median(all.map { abs(it - mid) })
        } else null
        // n efetivo: autocorrelação (lag 1) dos erros em ordem de tempo; leituras sobrepostas valem menos que uma.
        val ordered = members.sortedBy { pairs[it].t }
        val nEff = EvidencePairs.effectiveN(ordered.map { pairs[it].t }, ordered.map { pairLn[it] })
        return PointEvidence(
            members.size, Math.round(nEff).toInt(), nEff, exp(center) - 1.0, dispersion,
            members.count { pairs[it].ecuRef }.toDouble() / members.size,
        )
    }

    private fun visitIds(times: List<Long>): IntArray = EvidencePairs.visitIndexes(times)

    fun evaluate(
        input: EquivalenceInput,
        judge: (List<EquivalencePoint>) -> ProofOutcome = { ProofOutcome.NONE },
    ): EquivalenceResult {
        val prior = input.reference ?: input.provisional
        val ownP = OwnCurveFitter.fit(input.petrolObs, Fuel.GASOLINA, prior)
        // O GNV medido NÃO é puxado para a gasolina: sem prior (a Referência é de gasolina; encolher o GNV para ela esconderia o desvio).
        val ownG = OwnCurveFitter.fit(input.gasObs, Fuel.GNV, null)
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
        // Fonte única: o mesmo casamento por RPM×MAP do livro (gasolina própria, ou a curva da ECU onde não há) para veredito e proposta.
        val ecuRef = prior?.let { EvidencePairs.cleanReference(it.points.map { p -> p.mapBar to p.petrolMs }) } ?: emptyList()
        val pairs = EvidencePairs.build(input.petrolObs, input.gasObs, ecuRef)
            .filter { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }
        val pairU = DoubleArray(pairs.size) { ln(pairs[it].petrolRefMs) }
        val pairLn = DoubleArray(pairs.size) {
            val p = pairs[it]
            ln(AutoMatchRefinedEngine.interp(p.gasPetrolMs, axis, k) * p.gasPetrolMs /
                (p.petrolRefMs * AutoMatchRefinedEngine.interp(p.petrolRefMs, axis, k)))
        }
        val maps = ArrayList<Double?>(n)
        val base = ArrayList<EquivalencePoint>(n)
        for (i in 0 until n) {
            val tp = axis[i]
            val map = OwnCurveFitter.mapFor(ownP, tp)
            maps += map
            val cell = map?.let { OwnCurveFitter.cellOf(it) }
            val evidence = evidenceAt(i, u, pairs, pairU, pairLn)
            // Dispersão desconhecida (menos de 2 leituras) nunca vira tolerância larga: o ponto simplesmente não é julgado.
            val tolerance = EquivalenceTolerances.tolerance(evidence.dispersion ?: 0.0)
            val mapMixture = map?.let { m -> ownG.at(m)?.let { tg -> AutoMatchRefinedEngine.interp(tg, axis, k) * tg / tp / k[i] - 1.0 } }
            val judged = evidence.judgeable
            val mixture = if (judged) evidence.mixture else mapMixture
            val kTarget = if (judged) k[i] * (1.0 + evidence.mixture!!) else mapMixture?.let { k[i] * (1.0 + it) }
            val state = when {
                tp < AutoMatchRefinedEngine.TELEMETRY_MIN_MS || evidence.pairs == 0 -> PointState.SEM_DADOS
                !judged -> PointState.APRENDENDO
                abs(mixture!!) <= tolerance -> PointState.EQUIVALENTE
                mixture > tolerance -> PointState.POBRE
                else -> PointState.RICO
            }
            val sources = LinkedHashSet<String>()
            if (evidence.pairs > 0) sources += "TELEMETRIA"
            if (cell != null && ownP.cells[cell].source != CellSource.OWN || evidence.ecuShare > 0.0) {
                if (input.reference != null) sources += "ECU_REF" else if (input.provisional != null) sources += "AUTOCAL"
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
                slope = slope, usage = usage[i], samples = evidence.pairs, sources = sources, state = state,
                episodes = evidence.episodes,
            )
        }
        val outcome = judge(base)
        val points = base.map { p -> outcome.states[p.index]?.let { p.copy(state = it) } ?: p }
        val (index, coverage, judgedUsage) = indexOf(base)
        val proposal = proposalOf(input, pairs)
        val action = nextAction(input, points, maps, proposal, outcome, index)
        return EquivalenceResult(points, index, coverage, provisional, action, proposal, ownP, ownG, judgedUsage)
    }

    private fun isJudged(p: EquivalencePoint) =
        p.state == PointState.EQUIVALENTE || p.state == PointState.POBRE || p.state == PointState.RICO

    /**
     * Índice = Σ uso·equivalente / Σ uso nos pontos julgados (o veredito base, antes da prova). Só é número quando os
     * pontos julgados cobrem pelo menos [EquivalenceTolerances.MIN_JUDGED_USAGE] do uso: abaixo disso é nulo ("—"),
     * nunca uma porcentagem tirada de um pedaço pequeno do uso. Devolve (índice, nº de pontos julgados, fração julgada).
     */
    private fun indexOf(base: List<EquivalencePoint>): Triple<Double?, Int, Double> {
        val counted = base.filter { isJudged(it) && it.mixture != null }
        val allUsage = base.sumOf { it.usage }
        val total = counted.sumOf { it.usage }
        val share = if (allUsage > 0.0) total / allUsage else 0.0
        if (total <= 0.0 || share < EquivalenceTolerances.MIN_JUDGED_USAGE) return Triple(null, counted.size, share)
        val equivalent = counted.filter { abs(it.mixture!!) <= it.tolerance }.sumOf { it.usage }
        return Triple(equivalent / total, counted.size, share)
    }

    /** Os pares de condução (os mesmos do veredito). Sai só do motor refinado, com os portões dele. */
    private fun proposalOf(input: EquivalenceInput, pairs: List<EquivalenceLedger.EvidencePair>): AutoMatchRefinedEngine.Result? {
        return try {
            AutoMatchRefinedEngine.refine(
                AutoMatchRefinedEngine.Input(
                    axisRaw = input.axisRaw, mulActRaw = input.mulActRaw,
                    petrolTimeRaw = input.native?.petrolTimeRaw, petrolMapRaw = input.native?.petrolMapRaw,
                    petrolCounts = input.native?.petrolCounts, gasTimeRaw = input.native?.gasTimeRaw,
                    gasMapRaw = input.native?.gasMapRaw, gasCounts = input.native?.gasCounts,
                    pressureThresholdsRaw = input.native?.pressureThresholdsRaw,
                    telemetryPairs = pairs.map { it.petrolRefMs to it.gasPetrolMs }, pointGainScale = input.pointGainScale,
                    telemetryEpisodes = pairs.map { it.episode }, holdMinStepLog = input.holdMinStepLog,
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Proposta do motor com a trava da baixa (a mesma de AutoMatchSnapshotAnalysis.refinedJson): abaixo de
     * [AutoMatchSnapshotAnalysis.LOW_GUARD_MS] de Petrol Inj. o refino nunca empobrece; só mantém ou enriquece.
     */
    fun guardedRefined(proposal: AutoMatchRefinedEngine.Result): List<Int> =
        proposal.refinedRaw.mapIndexed { i, raw ->
            val current = proposal.currentRaw[i]
            if (proposal.axisMs[i] < AutoMatchSnapshotAnalysis.LOW_GUARD_MS && raw < current) current else raw
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
        index: Double?,
    ): NextAction {
        input.operation?.let { return NextAction(NextActionKind.OPERATION, it, null, null, emptyList()) }
        if (input.reference == null && input.provisional != null) {
            return NextAction(NextActionKind.FREEZE_REFERENCE, "Salvar a curva atual da ECU como referência", "refino", null, emptyList())
        }
        val contested = points.filter { it.state == PointState.CONTESTADO }
        if (contested.isNotEmpty()) {
            val text = "Ajuste em ${f1(contested.minOf { it.axisMs })}–${f1(contested.maxOf { it.axisMs })} ms piorou a suavidade · Desfazer"
            return NextAction(NextActionKind.CONTESTED, text, "refino", null, contested.map { it.index })
        }
        val poor = points.filter { it.state == PointState.POBRE }
        val rich = points.filter { it.state == PointState.RICO }
        val off = poor + rich
        // A gravação é a proposta do próprio motor (os mesmos pares do veredito), com a trava da baixa; só existe APPLY
        // quando ela muda algum ponto de fato. Assim "Pronto para gravar N pontos" e o botão falam dos mesmos N pontos.
        // Ponto em prova (gravação ainda sendo verificada) não é regravado por cima: a proposta só libera os outros.
        val guarded = proposal?.takeIf { it.mode == AutoMatchRefinedEngine.Mode.EQUIVALENCE }?.let { p ->
            guardedRefined(p).mapIndexed { i, raw -> if (points.getOrNull(i)?.state == PointState.EM_PROVA) p.currentRaw[i] else raw }
        }
        val changed = guarded?.let { refined ->
            points.indices.filter { proposal!!.origins[it] != AutoMatchRefinedEngine.Origin.HELD && refined[it] != proposal.currentRaw[it] }
        }.orEmpty()
        // Refino = suavizar a curva da ECU com os pontos nossos: o motor refinado (histerese 3,5%, passo máx. 15%, caixa de K,
        // regressão) decide o que mudar; não exige ponto já julgado "fora" quando a própria proposta muda a curva.
        if (proposal != null && guarded != null && changed.isNotEmpty()) {
            val head = if (off.isEmpty()) {
                "${changed.size} ponto${if (changed.size == 1) "" else "s"} para suavizar a curva"
            } else when {
                poor.isNotEmpty() && rich.isNotEmpty() ->
                    "${poor.size} ${plural(poor.size, "ponto pobre", "pontos pobres")} e ${rich.size} ${plural(rich.size, "rico", "ricos")}"
                poor.isNotEmpty() -> "${poor.size} ${plural(poor.size, "ponto pobre", "pontos pobres")}"
                else -> "${rich.size} ${plural(rich.size, "ponto rico", "pontos ricos")}"
            }
            val suffix = if (input.reference == null) " · sem referência da ECU" else ""
            val text = if (off.isEmpty()) {
                "$head entre ${f1(changed.minOf { points[it].axisMs })} e ${f1(changed.maxOf { points[it].axisMs })} ms · Aplicar ajuste$suffix"
            } else {
                val lo = (off.minOf { abs(it.mixture!!) } * 100.0).roundToInt()
                val hi = (off.maxOf { abs(it.mixture!!) } * 100.0).roundToInt()
                "$head entre ${f1(off.minOf { it.axisMs })} e ${f1(off.maxOf { it.axisMs })} ms ($lo–$hi%) · Aplicar ajuste$suffix"
            }
            return NextAction(
                NextActionKind.APPLY, text, "refino", null, changed.sortedByDescending { points[it].usage },
                proposal.currentRaw, guarded,
            )
        }
        if (points.any { it.state == PointState.EM_PROVA }) {
            // Sem minutos nem regras na frase do dono: o tempo restante fica em `technical` (outcome.remainingMinutes).
            val text = "Rodando para provar o ajuste"
            return NextAction(NextActionKind.PROVING, text, "refino", null, points.filter { it.state == PointState.EM_PROVA }.map { it.index })
        }
        val candidates = points.filter {
            (it.state == PointState.SEM_DADOS || it.state == PointState.APRENDENDO) && it.usage >= COLLECT_MIN_USAGE && maps[it.index] != null
        }.sortedByDescending { it.usage }
        if (candidates.isNotEmpty()) {
            val top = candidates.first()
            val map = maps[top.index]!!
            val text = "Rode ${terrain(map)} (~${f1(map)} bar) para eu medir entre " +
                "${f1(candidates.minOf { it.axisMs })} e ${f1(candidates.maxOf { it.axisMs })} ms"
            return NextAction(NextActionKind.COLLECT, text, "refino", null, candidates.map { it.index })
        }
        val judged = points.count { it.state != PointState.SEM_DADOS && it.state != PointState.APRENDENDO && it.state != PointState.MEDIDO }
        if (judged == 0) {
            return NextAction(NextActionKind.COLLECT, "Rode no GNV para eu começar a medir", "refino", null, emptyList())
        }
        if (off.isNotEmpty()) {
            // Há pontos fora, mas o motor refinado não tem evidência para propor: pede mais leitura em vez de dizer "nada a fazer".
            return NextAction(
                NextActionKind.COLLECT, "Estou aprendendo seu motor: siga dirigindo no GNV",
                "refino", null, off.sortedByDescending { it.usage }.map { it.index },
            )
        }
        // Prova que fechou sem convergir: isso NÃO é "equivalente". Com tentativas sobrando volta a medir; esgotadas, sem proposta.
        fun stillOff(p: EquivalencePoint): Boolean = p.state == PointState.INCONCLUSIVO && p.mixture != null && abs(p.mixture) > p.tolerance
        val unconverged = points.filter { stillOff(it) && outcome.reasons[it.index] == ProofOutcome.REASON_NO_CONVERGENCE }
        if (unconverged.isNotEmpty()) {
            val text = "Ajuste em ${f1(unconverged.minOf { it.axisMs })}–${f1(unconverged.maxOf { it.axisMs })} ms ainda não fechou · " +
                "sigo aprendendo antes de propor de novo"
            return NextAction(NextActionKind.COLLECT, text, "refino", null, unconverged.map { it.index })
        }
        val exhausted = points.filter { stillOff(it) && outcome.reasons[it.index] == ProofOutcome.REASON_EXHAUSTED }
        if (exhausted.isNotEmpty()) {
            val text = "Ajuste em ${f1(exhausted.minOf { it.axisMs })}–${f1(exhausted.maxOf { it.axisMs })} ms não fechou · " +
                "sem nova proposta ali; revise a curva nessa faixa"
            return NextAction(NextActionKind.NOTHING, text, "refino", null, exhausted.map { it.index })
        }
        if (index == null) {
            return NextAction(NextActionKind.COLLECT, "Estou aprendendo seu motor: siga dirigindo no GNV", "refino", null, emptyList())
        }
        return NextAction(NextActionKind.NOTHING, "Equivalente. Nada a fazer.", null, null, emptyList())
    }
}
