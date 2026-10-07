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
 * Evidência que motivou o motor (Work Unit OMEGAS-WU-006, removida; ver histórico git e docs/evidence/OMEGAS-WU-006.json):
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
 * `tests/test_refined_autocal_kotlin_parity.py`.
 *
 * Segurança da proposta (Fatia H-evidência): sem evidência suficiente NÃO há proposta (a curva fica
 * como está); faixa nativa fina (< 3 amostras) não é evidência; a condução sozinha exige ≥ 3 faixas
 * com ≥ 8 pares e descarta faixa com razão GNV/gasolina fora de [0,80; 1,25]; K novo limitado ao
 * intervalo do AutoMatch nativo [0,75; 1,20]; MUL_ACT fora de [0,5; 2,0] é rejeitado.
 */
object AutoMatchRefinedEngine {
    const val ALGORITHM = "OMEGAS_REFINED_EQUIVALENCE_V1"
    const val POINT_COUNT = 30
    const val BAND_COUNT = 18
    const val AXIS_COUNTS_PER_MS = 512.0
    const val MAP_COUNTS_PER_BAR = 1024.0
    const val Q14 = 16384.0
    const val MAX_RAW = 65535
    /**
     * Faixa de PROPOSTA = faixa do AutoMatch nativo (clamp(T_g/T, 0,75..1,20) observado na ECU).
     * O K novo de todo ponto alterado fica em [MIN_FACTOR, MAX_FACTOR]; ponto cujo K atual já está fora
     * da faixa e não entra nela dentro do passo de ±15% é mantido (e contado em outOfRangePoints).
     */
    const val MIN_RAW_PROPOSAL = 12288
    const val MAX_RAW_PROPOSAL = 19661
    const val MIN_FACTOR = MIN_RAW_PROPOSAL / Q14
    const val MAX_FACTOR = MAX_RAW_PROPOSAL / Q14
    /** Entrada sã: MUL_ACT fora disto é lixo de leitura/valor de fábrica (ex.: 4,0), não curva a refinar. */
    const val INPUT_MIN_FACTOR = 0.50
    const val INPUT_MAX_FACTOR = 2.00
    const val REASON_NO_EVIDENCE = "SEM_EVIDENCIA_SUFICIENTE"
    const val MESSAGE_NO_EVIDENCE = "sem evidência suficiente"

    const val BAND_FULL_COUNT = 6
    /** Faixa nativa com menos amostras que isto NÃO é evidência (entra no desenho de T(MAP), peso de evidência 0). */
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
    /**
     * A ECU pode não ter faixas maduras (AutoMatch acabou de zerar os buffers, ou o motorista
     * não passa por elas). Então a condução sozinha pode propor, mas só com cobertura de verdade:
     * pelo menos [TELEMETRY_ONLY_MIN_BANDS] faixas de Petrol Inj. com [TELEMETRY_ONLY_BAND_PAIRS]
     * pares cada. Sem isso falha fechado (POLISH, nada muda).
     */
    const val TELEMETRY_ONLY_BAND_PAIRS = 8
    const val TELEMETRY_ONLY_MIN_BANDS = 3
    /**
     * Uma faixa só puxa proposta com pares de ao menos este número de episódios = visitas à faixa separadas por
     * ≥ 60 s de condução ([EvidencePairs.VISIT_GAP_MS]); leituras estáveis seguidas NÃO são episódios distintos.
     */
    const val MIN_BAND_EPISODES = EvidencePairs.MIN_VISITS
    /** Peso por episódio: um episódio de uma faixa vale no máximo este número de pares (o resto é a mesma leitura repetida). */
    const val EPISODE_PAIR_CAP = 4
    /** Teto de peso da telemetria por faixa do livro (= uma faixa nativa plena: [BAND_FULL_COUNT] pares × [TELEMETRY_WEIGHT]). */
    const val TELEMETRY_BAND_WEIGHT_CAP = BAND_FULL_COUNT * TELEMETRY_WEIGHT
    /** A telemetria não move um ponto que a evidência nativa madura já cobre (ganho nativo ≥ isto). */
    const val NATIVE_COVERED_GAIN = 0.5
    /**
     * Erro de evidência abaixo disto num ponto = já está bom: o ponto não se move (= EquivalenceTolerances.MIN, ±4%).
     * Só vale com evidência de pelo menos [DEAD_BAND_MIN_EVIDENCE] no nó.
     */
    val DEAD_BAND_LOG = ln(1.04)
    const val DEAD_BAND_MIN_EVIDENCE = 0.1
    /** Tolerância numérica para "a proposta piorou o critério do próprio motor". */
    const val REGRESSION_EPS = 1e-4
    /**
     * Histerese de proposta: ponto com evidência cujo passo proposto fica abaixo disto é MANTIDO (ruído).
     * O motor só aplica quando [Input.holdMinStepLog] pede; a produção passa esta constante.
     */
    val HOLD_MIN_STEP_LOG = ln(1.035)
    /** Ganho decrescente por ponto já alterado: 1ª passada 1,0 · 2ª 0,7 · 3ª em diante 0,5 (independe do veredito). */
    val PASS_GAIN = doubleArrayOf(1.0, 0.7, 0.5)
    fun passGain(passes: Int): Double = PASS_GAIN[passes.coerceIn(0, PASS_GAIN.size - 1)]
    /**
     * Razão mediana GNV/gasolina de uma faixa fora disto é erro de medida: a faixa inteira é descartada. A própria ECU
     * mede a equivalência (PETR_INJ_TBUF_GAS/PETR_INJ_TBUF no mesmo MAP, 85 sessões) em 1,013 com IQR [0,975; 1,062] e
     * o AutoMatch nativo corrige em [0,75; 1,20]: [0,80; 1,25] já folga ~4 IQR; o antigo [0,6; 1,6] deixava passar lixo.
     */
    const val TELEMETRY_RATIO_MIN = 0.80
    const val TELEMETRY_RATIO_MAX = 1.25

    /**
     * Lote H: a condução como evidência em 54 bins finos (FineBins), em vez de um alvo por par. A produção só passa
     * `fineBins` ao motor quando isto é verdadeiro; o motor usa o caminho fino sempre que `Input.fineBins` vier.
     * Valor decidido pela validação cruzada nas sessões reais (tools/autocal_refine/fine_bins_cv.py).
     */
    const val FINE_BINS_ENABLED = false
    /** Rigidez do perfil ln(razão) × ln(ms) sobre os 54 bins (escolhida por validação cruzada). */
    const val FINE_LAMBDA = 0.3
    /** Pares de um bin que contam para o peso (como [BAND_FULL_COUNT]). */
    const val FINE_WEIGHT_CAP = 6
    /** Massa de evidência máxima de um nó do eixo (em "pares de bin"). */
    const val FINE_NODE_CAP = 8.0
    /** Âncora fraca do perfil: só impede o sistema de ficar singular. */
    const val FINE_PRIOR = 1e-3

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
        /** Episódio de cada par de [telemetryPairs] (paralelo); vazio ou com id < 0 = desconhecido, sem portão. */
        val telemetryEpisodes: List<Int> = emptyList(),
        /** 0 = sem histerese. */
        val holdMinStepLog: Double = 0.0,
        /** Lote H: bins finos da condução (54). Quando presente, substitui [telemetryPairs] como evidência da condução. */
        val fineBins: List<FineBins.Bin>? = null,
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
        /** Peso de EVIDÊNCIA: 0 em faixa fina (< [BAND_MATURE_COUNT] amostras). O peso de ajuste é [weight]. */
        val evidenceWeight: Double = weight,
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
        /** A equivalência veio só da condução (a ECU não tinha faixas maduras em comum). */
        val telemetryOnly: Boolean = false,
        val metricsBefore: Metrics?,
        val metricsAfter: Metrics?,
        /** Erro médio ponderado entre o K pedido pela medição e a curva (fração); null sem equivalência. */
        val evidenceErrorBefore: Double? = null,
        val evidenceErrorAfter: Double? = null,
        /** Faixas nativas com dado mas MAP/tempo inválido (bit 0x8000, ≤ 0): contadas, nunca silenciosas. */
        val invalidEvidenceBands: Int = 0,
        val thinBandsIgnored: Int = 0,
        /** Faixas de condução descartadas por razão GNV/gasolina implausível. */
        val telemetryOutlierBands: Int = 0,
        val telemetryPairsUsed: Int = 0,
        /** Alvos da telemetria descartados porque a evidência nativa madura já cobre o ponto. */
        val telemetryDroppedByNative: Int = 0,
        /** Nós que ficaram parados porque a evidência já os mostra dentro da tolerância. */
        val deadBandPoints: Int = 0,
        /** A proposta calculada piorava o critério do motor: nada é proposto (a curva fica como está). */
        val regressionBlocked: Boolean = false,
        /** Pontos com K atual fora de [MIN_FACTOR, MAX_FACTOR] (mantidos se não entram na faixa). */
        val outOfRangePoints: Int = 0,
        val message: String? = null,
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
        // K fora da faixa sã (ex.: 4,0 de fábrica/lixo): rejeita e sinaliza em vez de "manter".
        val insane = kOld.count { it < INPUT_MIN_FACTOR || it > INPUT_MAX_FACTOR }
        if (insane > 0) return unavailable("MUL_ACT_FORA_DA_FAIXA", outOfRangePoints = insane)
        val u = axisMs.map { ln(it) }
        val x0 = kOld.map { ln(it) }

        var targets: List<Target> = emptyList()
        val rejected = mutableListOf<RejectedBand>()
        val stats = BandStats()
        if (input.petrolTimeRaw != null && input.petrolMapRaw != null && input.petrolCounts != null &&
            input.gasTimeRaw != null && input.gasMapRaw != null && input.gasCounts != null &&
            listOf(input.petrolTimeRaw, input.petrolMapRaw, input.petrolCounts, input.gasTimeRaw, input.gasMapRaw, input.gasCounts)
                .all { it.size == BAND_COUNT }
        ) {
            val (petrol, rp) = monotoneFit(bandPoints(input.petrolTimeRaw, input.petrolMapRaw, input.petrolCounts, stats))
            val (gas, rg) = monotoneFit(bandPoints(input.gasTimeRaw, input.gasMapRaw, input.gasCounts, stats))
            rp.forEach { rejected += RejectedBand("GASOLINA", it.band, it.mapBar, it.timeMs) }
            rg.forEach { rejected += RejectedBand("GNV", it.band, it.mapBar, it.timeMs) }
            if (petrol.size >= 2 && gas.size >= 2) targets = equivalenceTargets(petrol, gas, axisMs, kOld)
        }
        val matureWeight = BAND_MATURE_COUNT.toDouble() / BAND_FULL_COUNT
        val mature = targets.count { it.weight >= matureWeight }
        val nativeEquivalence = mature >= MIN_COMMON_MATURE
        val fine = input.fineBins?.takeIf { it.size == FineBins.FINE_COUNT }
        var fineValid: BooleanArray? = null
        var usablePairs: List<kotlin.Pair<Double, Double>> = emptyList()
        var usableWeights: List<Double> = emptyList()
        var outlierBands = 0
        var usedCount = 0
        if (fine != null) {
            // Lote H: bins finos. Bin fino (< 3 pares) não é evidência; a faixa de 18 precisa de ≥ 3 episódios.
            val gate = FineBins.gate(fine)
            fineValid = gate.valid
            outlierBands = gate.outlierBands
            usedCount = fine.indices.filter { gate.valid[it] }.sumOf { fine[it].n }
        } else {
            val episodesKnown = input.telemetryEpisodes.size == input.telemetryPairs.size
            val keptPairs = input.telemetryPairs.indices.filter { i ->
                val (tp, tg) = input.telemetryPairs[i]
                tp >= TELEMETRY_MIN_MS && tg > 0.0 && tp <= axisMs.last()
            }
            val candidates = keptPairs.map { input.telemetryPairs[it] }
            val episodeIds = if (episodesKnown) keptPairs.map { input.telemetryEpisodes[it] } else null
            val plausible = plausibleIndices(candidates, episodeIds)
            usablePairs = plausible.kept.map { candidates[it] }
            usableWeights = pairWeights(usablePairs, episodeIds?.let { ids -> plausible.kept.map { ids[it] } })
            outlierBands = plausible.outliers
            usedCount = usablePairs.size
        }
        // A condução sozinha só habilita a equivalência com cobertura real em ≥ 3 faixas distintas.
        val telemetryOnly = !nativeEquivalence && (if (fine != null) fineCovers(fine, fineValid!!) else telemetryCovers(usablePairs))
        val equivalence = nativeEquivalence || telemetryOnly
        val bandTargetCount = targets.size
        if (telemetryOnly) targets = emptyList() // faixas nativas imaturas não entram: só a medição própria
        var droppedByNative = 0
        if (equivalence && usedCount > 0) {
            if (fine != null) {
                targets = targets + fineTargets(fine, fineValid!!, axisMs, kOld)
            } else {
                var telemetry = usablePairs.mapIndexed { i, (tp, tg) ->
                    Target(Double.NaN, tp, tg, TELEMETRY_WEIGHT * usableWeights[i], tg / tp, ln(interp(tg, axisMs, kOld) * tg / tp))
                }
                // Teto de peso por faixa do livro: a telemetria é muita leitura repetida, a nativa é a ECU medindo.
                telemetry = capBandWeight(telemetry)
                // A nativa madura cobre o ponto: a telemetria não o move (só preenche o que a nativa não cobre).
                if (nativeEquivalence && targets.isNotEmpty()) {
                    val nativeGain = gainOf(targets.map { Observation(axisWeights(it.petrolMs, axisMs), it.logTarget, it.weight) })
                    val before = telemetry.size
                    telemetry = telemetry.filter { t ->
                        val nodes = axisWeights(t.petrolMs, axisMs)
                        val dominant = nodes.maxByOrNull { it.second }!!.first
                        nativeGain[dominant] < NATIVE_COVERED_GAIN
                    }
                    droppedByNative = before - telemetry.size
                }
                targets = targets + telemetry
            }
        }

        val observations = if (equivalence) {
            targets.map { Observation(axisWeights(it.petrolMs, axisMs), it.logTarget, it.weight) }
        } else emptyList()

        val evidence = DoubleArray(POINT_COUNT)
        observations.forEach { o -> o.a.forEach { (j, a) -> evidence[j] += o.w * a } }
        var gain = gainOf(observations)
        val lnLo = ln(MIN_FACTOR)
        val lnHi = ln(MAX_FACTOR)
        val outOfRange = x0.count { it < lnLo - 1e-12 || it > lnHi + 1e-12 }
        var eEff = E_MAX
        var deadBand = 0
        var box: List<Pair<Double, Double>> = emptyList()
        val final: List<Double>
        if (equivalence) {
            val priorWeights = gain.map { g -> PRIOR_UNSUPPORTED * (1.0 - g) + PRIOR_SUPPORTED * g }
            val (fitted, robust) = whittaker(u, observations, x0, priorWeights, LAMBDA)
            targets = targets.mapIndexed { i, t -> t.copy(robustWeight = robust[i]) }
            val scale = input.pointGainScale?.takeIf { it.size == POINT_COUNT }
            val scaled = if (scale == null) fitted else fitted.mapIndexed { j, z -> x0[j] + scale[j] * (z - x0[j]) }
            val fixedNodes = deadBandNodes(observations, evidence, axisMs, kOld)
            deadBand = fixedNodes.size
            val initialBox = proposalBox(x0, gain).mapIndexed { j, b -> if (j in fixedNodes) x0[j] to x0[j] else b }
            eEff = effectiveElasticity(initialBox, u)
            val enforced = enforceCoherence(scaled, initialBox, u, eEff)
            // Histerese: ponto cujo passo proposto é ruído fica exatamente como está, desde que a curva continue
            // coerente com os vizinhos (senão o ponto volta a se mover). Espelho de refined_oracle.hold_small_steps.
            val (finalBox, finalCurve) = if (input.holdMinStepLog > 0.0)
                holdSmallSteps(scaled, initialBox, x0, enforced, u, eEff, input.holdMinStepLog)
            else initialBox to enforced
            box = finalBox
            final = finalCurve
        } else {
            // Sem evidência suficiente NÃO existe proposta: a curva fica exatamente como está.
            box = proposalBox(x0, gain)
            final = x0
            gain = List(POINT_COUNT) { 0.0 }
        }

        val origins = ArrayList<Origin>(POINT_COUNT)
        val outRaw = ArrayList<Int>(POINT_COUNT)
        for (j in 0 until POINT_COUNT) {
            // Sem evidência, ou ponto fora da faixa nativa que não entra nela: mantido exatamente.
            if (!equivalence || box[j].first == box[j].second) {
                origins += Origin.HELD
                outRaw += kRaw[j]
                continue
            }
            val origin = when {
                gain[j] >= 0.5 -> Origin.MEASURED
                gain[j] > 0.0 -> Origin.BLENDED
                abs(final[j] - x0[j]) > SMOOTH_TOLERANCE_LOG -> Origin.SMOOTHED
                else -> Origin.HELD
            }
            origins += origin
            // Sem evidência e sem anomalia: preserva exatamente o valor gravado.
            outRaw += if (origin == Origin.HELD) kRaw[j]
            else (exp(final[j]) * Q14).roundToInt().coerceIn(MIN_RAW_PROPOSAL, MAX_RAW_PROPOSAL)
        }
        // Uma proposta que piora o critério do próprio motor (erro ponderado contra TODOS os alvos usados) nunca sai.
        var regression = false
        if (equivalence && targets.isNotEmpty()) {
            val errBefore = evidenceError(targets, axisMs, kOld)
            val errAfter = evidenceError(targets, axisMs, outRaw.map { it / Q14 })
            if (errBefore != null && errAfter != null && errAfter > errBefore + REGRESSION_EPS) {
                regression = true
                for (j in 0 until POINT_COUNT) { outRaw[j] = kRaw[j]; origins[j] = Origin.HELD }
            }
        }
        return Result(
            mode = if (equivalence) Mode.EQUIVALENCE else Mode.POLISH,
            reason = if (equivalence) null else REASON_NO_EVIDENCE,
            message = if (equivalence) null else MESSAGE_NO_EVIDENCE,
            telemetryOnly = telemetryOnly,
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
            telemetryTargetCount = if (telemetryOnly) targets.size else targets.size - bandTargetCount,
            metricsBefore = metrics(kOld, axisMs),
            metricsAfter = metrics(outRaw.map { it / Q14 }, axisMs),
            evidenceErrorBefore = evidenceError(judgedTargets(equivalence, telemetryOnly, targets, matureWeight), axisMs, kOld),
            evidenceErrorAfter = evidenceError(judgedTargets(equivalence, telemetryOnly, targets, matureWeight), axisMs, outRaw.map { it / Q14 }),
            invalidEvidenceBands = stats.invalid,
            thinBandsIgnored = stats.thin,
            telemetryOutlierBands = outlierBands,
            telemetryPairsUsed = if (equivalence) usedCount else 0,
            telemetryDroppedByNative = droppedByNative,
            deadBandPoints = deadBand,
            regressionBlocked = regression,
            outOfRangePoints = outOfRange,
        )
    }

    /** Ganho por nó (0..1) da evidência: massa espalhada 0,25/0,5/0,25 sobre [EVIDENCE_REF]. */
    internal fun gainOf(observations: List<Observation>): List<Double> {
        val evidence = DoubleArray(POINT_COUNT)
        observations.forEach { o -> o.a.forEach { (j, a) -> evidence[j] += o.w * a } }
        return List(POINT_COUNT) { j ->
            var spread = 0.5 * evidence[j]
            if (j > 0) spread += 0.25 * evidence[j - 1]
            if (j < POINT_COUNT - 1) spread += 0.25 * evidence[j + 1]
            min(1.0, spread / EVIDENCE_REF)
        }
    }

    /**
     * Nós cujo erro de evidência já está dentro da tolerância ([DEAD_BAND_LOG]): média ponderada (peso × participação
     * no nó) da diferença ln K(ms) − ln K_alvo dos alvos que o tocam. Esses nós não se movem, desde que o K atual seja
     * coerente com os vizinhos (|Δ ln K/Δ ln t| ≤ [E_MAX]): K incoerente continua podendo ser reparado.
     */
    internal fun deadBandNodes(observations: List<Observation>, evidence: DoubleArray, axisMs: List<Double>, kOld: List<Double>): Set<Int> {
        val num = DoubleArray(POINT_COUNT)
        val den = DoubleArray(POINT_COUNT)
        val coherent = BooleanArray(POINT_COUNT) { true }
        for (j in 0 until POINT_COUNT - 1) {
            if (abs(ln(kOld[j + 1]) - ln(kOld[j])) > E_MAX * (ln(axisMs[j + 1]) - ln(axisMs[j])) + 1e-9) { coherent[j] = false; coherent[j + 1] = false }
        }
        // O ms de cada observação é recuperado do próprio vetor de participação: ponto médio ponderado dos nós.
        observations.forEach { o ->
            val ms = o.a.sumOf { (j, a) -> a * axisMs[j] }
            val diff = ln(interp(ms, axisMs, kOld)) - o.y
            o.a.forEach { (j, a) -> num[j] += o.w * a * diff; den[j] += o.w * a }
        }
        return (0 until POINT_COUNT).filter { j -> coherent[j] && evidence[j] >= DEAD_BAND_MIN_EVIDENCE && den[j] > 0.0 && abs(num[j] / den[j]) <= DEAD_BAND_LOG }.toSet()
    }

    /** Peso relativo de cada par por episódio: um (faixa, episódio) vale no máximo [EPISODE_PAIR_CAP] pares. Sem episódios = 1. */
    internal fun pairWeights(pairs: List<kotlin.Pair<Double, Double>>, episodes: List<Int>?): List<Double> {
        if (episodes == null || episodes.size != pairs.size) return List(pairs.size) { 1.0 }
        val counts = HashMap<kotlin.Pair<Int, Int>, Int>()
        pairs.forEachIndexed { i, p -> counts.merge(ledgerBand(p.first)!! to episodes[i], 1, Int::plus) }
        return pairs.mapIndexed { i, p -> min(1.0, EPISODE_PAIR_CAP.toDouble() / counts[ledgerBand(p.first)!! to episodes[i]]!!) }
    }

    /** Teto de peso total da telemetria por faixa do livro: acima dele os alvos da faixa são reduzidos na mesma proporção. */
    internal fun capBandWeight(targets: List<Target>): List<Target> {
        val sums = HashMap<Int, Double>()
        targets.forEach { t -> ledgerBand(t.petrolMs)?.let { sums.merge(it, t.weight, Double::plus) } }
        return targets.map { t ->
            val band = ledgerBand(t.petrolMs)
            val sum = band?.let { sums[it] } ?: 0.0
            if (sum > TELEMETRY_BAND_WEIGHT_CAP) t.copy(weight = t.weight * TELEMETRY_BAND_WEIGHT_CAP / sum) else t
        }
    }

    /** Contadores da leitura das faixas nativas (evidência inválida/fina nunca é silenciosa). */
    internal class BandStats { var invalid = 0; var thin = 0 }

    /** MAP bruto S16 com bit 0x8000 (ou negativo) não é pressão: é evidência inválida. */
    internal fun mapRawInvalid(value: Int): Boolean = value < 0 || (value and 0x8000) != 0

    /** Índice da faixa de Petrol Inj. do livro; ≥ 12 ms cai numa faixa de cauda (nunca conta cobertura). */
    private fun ledgerBand(tp: Double): Int? {
        EquivalenceLedger.BANDS.forEachIndexed { i, (lo, hi) -> if (tp >= lo && tp < hi) return i }
        return if (tp >= EquivalenceLedger.BANDS.last().second) EquivalenceLedger.BANDS.size else null
    }

    internal class Plausible(val kept: List<Int>, val outliers: Int)

    /**
     * Descarta a faixa inteira cuja razão mediana GNV/gasolina é implausível ([TELEMETRY_RATIO_MIN]..[TELEMETRY_RATIO_MAX]),
     * a faixa fina (< [BAND_MATURE_COUNT] pares) e a faixa cujos pares não se espalham por dentro dela
     * ([EvidencePairs.interiorCovered]). Com episódios: a faixa precisa de [MIN_BAND_EPISODES] episódios distintos, e o par
     * de episódio desconhecido (< 0) não conta e sai (um único -1 não desliga o portão das outras faixas).
     * Devolve os ÍNDICES mantidos (em ordem de entrada) e o nº de faixas outlier.
     */
    internal fun plausibleIndices(pairs: List<kotlin.Pair<Double, Double>>, episodes: List<Int>? = null): Plausible {
        val gated = episodes != null && episodes.size == pairs.size
        val groups = sortedMapOf<Int, MutableList<Int>>()
        pairs.forEachIndexed { i, pair ->
            val band = ledgerBand(pair.first) ?: return@forEachIndexed
            if (gated && episodes!![i] < 0) return@forEachIndexed
            groups.getOrPut(band) { ArrayList() }.add(i)
        }
        val kept = ArrayList<Int>()
        var outliers = 0
        groups.entries.forEach { (band, group) ->
            // Poucos episódios: oito pares de um só trecho são um acaso, não cobertura.
            if (gated && group.map { episodes!![it] }.toSet().size < MIN_BAND_EPISODES) return@forEach
            val ratios = group.map { pairs[it].second / pairs[it].first }.sorted()
            val median = ratios[ratios.size / 2]
            if (median < TELEMETRY_RATIO_MIN || median > TELEMETRY_RATIO_MAX) {
                outliers++
            } else if (group.size >= BAND_MATURE_COUNT && interiorOk(band, group.map { pairs[it].first })) {
                kept += group
            }
        }
        return Plausible(kept.sorted(), outliers)
    }

    private fun interiorOk(band: Int, tps: List<Double>): Boolean {
        if (band >= EquivalenceLedger.BANDS.size) return true // faixa de cauda
        val (lo, hi) = EquivalenceLedger.BANDS[band]
        return EvidencePairs.interiorCovered(tps, lo, hi)
    }

    internal fun plausiblePairs(
        pairs: List<kotlin.Pair<Double, Double>>,
        episodes: List<Int>? = null,
    ): kotlin.Pair<List<kotlin.Pair<Double, Double>>, Int> {
        val plausible = plausibleIndices(pairs, episodes)
        return plausible.kept.map { pairs[it] } to plausible.outliers
    }


    // ------------------------------------------------------- evidência fina (Lote H)

    /**
     * Perfil robusto ln(razão) × ln(ms) nos 54 bins: Whittaker (2ª diferença) + Tukey sobre as medianas dos bins
     * válidos, peso min(n, [FINE_WEIGHT_CAP]). Sem âncora ao K: só suaviza a medição. Retorna (ajustado, tukey por bin).
     * Espelho de refined_oracle.fine_profile.
     */
    internal fun fineProfile(bins: List<FineBins.Bin>, valid: BooleanArray, lambda: Double = FINE_LAMBDA): Pair<List<Double>, DoubleArray> {
        val n = FineBins.FINE_COUNT
        val u = bins.map { it.centerLn }
        val idx = (0 until n).filter { valid[it] }
        val ys = idx.map { bins[it].medianLn!! }
        val ws = idx.map { min(bins[it].n, FINE_WEIGHT_CAP).toDouble() }
        val anchor = if (ys.isEmpty()) 0.0 else ys.sorted()[ys.size / 2]
        val d2 = secondDifferenceRows(u)
        var robust = List(idx.size) { 1.0 }
        var x: List<Double> = List(n) { anchor }
        repeat(IRLS_ITERATIONS) {
            val m = Array(n) { DoubleArray(n) }
            val v = DoubleArray(n)
            d2.forEach { row ->
                val nz = row.indices.filter { row[it] != 0.0 }
                nz.forEach { j -> nz.forEach { k -> m[j][k] += lambda * row[j] * row[k] } }
            }
            for (j in 0 until n) { m[j][j] += FINE_PRIOR; v[j] += FINE_PRIOR * anchor }
            idx.forEachIndexed { pos, i ->
                val w = ws[pos] * robust[pos]
                m[i][i] += w
                v[i] += w * ys[pos]
            }
            x = solve(m, v)
            val res = idx.mapIndexed { pos, i -> ys[pos] - x[i] }
            val scale = if (res.isEmpty()) 0.01 else max(res.map { abs(it) }.sorted()[res.size / 2] * 1.4826, 0.01)
            robust = res.map { tukey(it / (TUKEY_C * scale)) }
        }
        val tukeyAll = DoubleArray(n)
        idx.forEachIndexed { pos, i -> tukeyAll[i] = robust[pos] }
        return x to tukeyAll
    }

    /**
     * Amostra o perfil fino nos nós do eixo K. Um nó tem alvo só se houver bin válido na sua célula (entre os pontos
     * médios, em ln ms, até os nós vizinhos): fora disso não há evidência inventada. Alvo = o da equivalência por par,
     * y = ln(K(tg)·tg/tp), com tp = ms do nó e tg = tp·razão(tp). Peso = [TELEMETRY_WEIGHT]·min(massa, [FINE_NODE_CAP]).
     */
    internal fun fineTargets(bins: List<FineBins.Bin>, valid: BooleanArray, axisMs: List<Double>, kOld: List<Double>): List<Target> {
        val uAxis = axisMs.map { ln(it) }
        val u = bins.map { it.centerLn }
        val (fitted, tukeyW) = fineProfile(bins, valid)
        val n = uAxis.size
        val out = ArrayList<Target>()
        for (j in 0 until n) {
            val lo = if (j > 0) 0.5 * (uAxis[j - 1] + uAxis[j]) else uAxis[j] - 0.5 * (uAxis[1] - uAxis[0])
            val hi = if (j < n - 1) 0.5 * (uAxis[j] + uAxis[j + 1]) else uAxis[j] + 0.5 * (uAxis[j] - uAxis[j - 1])
            val members = u.indices.filter { valid[it] && u[it] >= lo && u[it] < hi }
            if (members.isEmpty()) continue
            val mass = members.sumOf { min(bins[it].n, FINE_WEIGHT_CAP) * tukeyW[it] }
            if (mass <= 0.0) continue
            val r = exp(interp(uAxis[j], u, fitted))
            val tp = axisMs[j]
            val tg = tp * r
            out += Target(Double.NaN, tp, tg, TELEMETRY_WEIGHT * min(mass, FINE_NODE_CAP), r, ln(interp(tg, axisMs, kOld) * r))
        }
        return out
    }

    /** Mesma cobertura mínima da condução, contada em pares dos bins válidos por faixa grossa do livro. */
    internal fun fineCovers(bins: List<FineBins.Bin>, valid: BooleanArray): Boolean {
        val covered = EquivalenceLedger.BANDS.count { (lo, hi) ->
            bins.indices.filter { valid[it] && kotlin.math.sqrt(bins[it].fromMs * bins[it].toMs).let { c -> c >= lo && c < hi } }
                .sumOf { bins[it].n } >= TELEMETRY_ONLY_BAND_PAIRS
        }
        return covered >= TELEMETRY_ONLY_MIN_BANDS
    }

    /**
     * Limites ln K por ponto: passo ≤ ±15% ∩ [ln 0,75; ln 1,20]. Ponto que não alcança a faixa
     * (K atual fora dela e fora do alcance do passo) ou K atual fora dela sem evidência fica fixo em x0.
     */
    internal fun proposalBox(x0: List<Double>, gain: List<Double>): List<Pair<Double, Double>> {
        val loRange = ln(MIN_FACTOR)
        val hiRange = ln(MAX_FACTOR)
        return x0.mapIndexed { j, x ->
            var lo = max(x - MAX_STEP_LOG, loRange)
            var hi = min(x + MAX_STEP_LOG, hiRange)
            val outside = x < loRange - 1e-12 || x > hiRange + 1e-12
            if (lo > hi + 1e-12 || (outside && gain[j] <= 0.0)) { lo = x; hi = x }
            lo to hi
        }
    }

    /**
     * Histerese da proposta: fixa em x0 os pontos cujo passo |final - x0| < [holdLog] e reprojeta. Se a curva
     * deixar de ser coerente (|Δ ln K/Δ ln t| > e entre vizinhos) ou a projeção ceder, os pontos presos ao lado do
     * degrau voltam a se mover. Devolve (caixa, curva); sem nada a prender, devolve as recebidas.
     */
    internal fun holdSmallSteps(
        fitted: List<Double>,
        box: List<kotlin.Pair<Double, Double>>,
        x0: List<Double>,
        final: List<Double>,
        u: List<Double>,
        e: Double,
        holdLog: Double,
    ): kotlin.Pair<List<kotlin.Pair<Double, Double>>, List<Double>> {
        val n = x0.size
        val held = (0 until n).filter { j ->
            box[j].first < box[j].second && x0[j] >= box[j].first - 1e-12 && x0[j] <= box[j].second + 1e-12 &&
                abs(final[j] - x0[j]) < holdLog
        }.toMutableSet()
        while (held.isNotEmpty()) {
            val trial = box.mapIndexed { j, b -> if (j in held) (x0[j] to x0[j]) else b }
            val z = enforceCoherence(fitted, trial, u, e)
            val bad = (0 until n - 1).filter { j -> abs(z[j + 1] - z[j]) > e * (u[j + 1] - u[j]) + 1e-6 }
            val moved = held.filter { j -> abs(z[j] - x0[j]) > 1e-6 }
            if (bad.isEmpty() && moved.isEmpty()) return trial to z
            val drop = HashSet<Int>()
            bad.forEach { j ->
                if (j in held) drop += j
                if (j + 1 in held) drop += j + 1
            }
            drop += moved
            if (drop.isEmpty()) break
            held -= drop
        }
        return box to final
    }

    /** Alvos que julgam o erro: faixas nativas maduras; na condução-só, todos os pares da medição. */
    private fun judgedTargets(equivalence: Boolean, telemetryOnly: Boolean, targets: List<Target>, matureWeight: Double): List<Target> = when {
        !equivalence -> emptyList()
        telemetryOnly -> targets
        else -> targets.filter { it.weight >= matureWeight }
    }

    /** Cobertura mínima da condução: [TELEMETRY_ONLY_MIN_BANDS] faixas distintas com [TELEMETRY_ONLY_BAND_PAIRS] pares válidos. */
    private fun telemetryCovers(pairs: List<kotlin.Pair<Double, Double>>): Boolean {
        val covered = EquivalenceLedger.BANDS.count { (lo, hi) ->
            pairs.count { (tp, _) -> tp >= lo && tp < hi } >= TELEMETRY_ONLY_BAND_PAIRS
        }
        return covered >= TELEMETRY_ONLY_MIN_BANDS
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

    internal fun bandPoints(timeRaw: IntArray, mapRaw: IntArray, counts: IntArray, stats: BandStats? = null): List<BandPoint> =
        (0 until BAND_COUNT).mapNotNull { band ->
            val n = counts[band]
            if (n <= 0) return@mapNotNull null
            if (mapRawInvalid(mapRaw[band]) || timeRaw[band] <= 0 || mapRaw[band] <= 0) {
                stats?.let { it.invalid++ }
                return@mapNotNull null
            }
            // Faixa fina (< BAND_MATURE_COUNT): ajuda a desenhar T(MAP), mas não vale como alvo (peso de evidência 0).
            val thin = n < BAND_MATURE_COUNT
            if (thin) stats?.let { it.thin++ }
            val weight = min(n, BAND_FULL_COUNT).toDouble() / BAND_FULL_COUNT
            BandPoint(
                band = band,
                mapBar = mapRaw[band] / MAP_COUNTS_PER_BAR,
                timeMs = timeRaw[band] / AXIS_COUNTS_PER_MS,
                weight = weight,
                count = n,
                evidenceWeight = if (thin) 0.0 else weight,
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
        val pw = petrol.map { it.evidenceWeight }
        val gm = gas.map { it.mapBar }
        val gt = gas.map { it.fitTimeMs }
        val gw = gas.map { it.evidenceWeight }
        val lo = max(pm.first(), gm.first())
        val hi = min(pm.last(), gm.last())
        val grid = (pm + gm).filter { it in lo..hi }.distinct().sorted()
        return grid.mapNotNull { m ->
            val tp = interp(m, pm, pt)
            val tg = interp(m, gm, gt)
            val w = min(localWeight(m, pm, pw), localWeight(m, gm, gw))
            if (w <= 0.0 || tp <= 0.0 || tg <= 0.0) return@mapNotNull null
            // K(T_g) tem de ser o K sob o qual o T_g foi ADQUIRIDO. Quem chama garante que só chegam bandas da época do
            // MUL_ACT atual (NativeGasEvidenceEpoch: contagem só do que subiu depois da última gravação de K).
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

    internal class Observation(val a: List<Pair<Int, Double>>, val y: Double, val w: Double)

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

    internal fun whittaker(
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

    internal fun coherenceFeasible(box: List<Pair<Double, Double>>, u: List<Double>, e: Double): Boolean {
        val n = box.size
        for (j in 0 until n) {
            var hi = Double.MAX_VALUE
            var lo = -Double.MAX_VALUE
            for (k in 0 until n) {
                hi = min(hi, box[k].second + e * abs(u[j] - u[k]))
                lo = max(lo, box[k].first - e * abs(u[j] - u[k]))
            }
            if (lo > hi + 1e-12) return false
        }
        return true
    }

    internal fun effectiveElasticity(box: List<Pair<Double, Double>>, u: List<Double>): Double {
        if (coherenceFeasible(box, u, E_MAX)) return E_MAX
        var lo = E_MAX
        var hi = 8.0
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (coherenceFeasible(box, u, mid)) hi = mid else lo = mid
        }
        return hi
    }

    internal fun enforceCoherence(z0: List<Double>, box: List<Pair<Double, Double>>, u: List<Double>, e: Double): List<Double> {
        val z = z0.toDoubleArray()
        val n = z.size
        for (iteration in 0 until 20000) {
            var changed = false
            for (j in 0 until n) {
                val lo = box[j].first
                val hi = box[j].second
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

    private fun unavailable(reason: String, outOfRangePoints: Int = 0) = Result(
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
        outOfRangePoints = outOfRangePoints,
    )
}
