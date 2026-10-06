package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.JsonFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Ciclo fechado da Curva K: cada gravação confirmada vira um experimento.
 *
 * Antes: o índice de equivalência por faixa (t_no_GNV / t_gasolina, condução ≥1000 rpm)
 * medido com a curva antiga. Depois: o mesmo índice medido com a curva nova, conforme o
 * motorista roda. Por faixa o resultado é CONFIRMADO (chegou perto da gasolina),
 * PASSOU (inverteu o sinal além do ruído), CURTO (mesmo sinal, pouca melhora) ou
 * PIOROU (ficou mais longe). Isso ajusta o ganho de cada faixa para a próxima proposta
 * (aprendizado pela própria correção) e, se piorou, permite restaurar só aquele trecho.
 *
 * Nunca grava na ECU: só registra, avalia e informa a UI/motor.
 */
class RefinementJournal(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-refinement-journal-v1"
        /** Amostras por faixa (antes e depois) para julgar uma faixa. */
        const val MIN_BAND_SAMPLES = 8
        /** Diferença abaixo disso é ruído de medição (~2%). */
        const val NOISE_LOG = 0.02
        /**
         * Critério único da margem (decisão do dono, 2026-10-06): GNV dentro de ±4% é o alvo e até ±5% é aceito
         * ([EquivalenceTolerances.MAX]). Depois da correção, uma faixa dentro disso é CONFIRMADA.
         */
        val OK_AFTER_LOG = ln(1.0 + com.omegas.prohub.equivalence.EquivalenceTolerances.MAX)
        /**
         * "Piorou" tem critério e mínimo: a faixa ficou FORA da margem aceita ([WORSE_MIN_ERROR_LOG]) E pior que antes além
         * do ruído de medição ([WORSE_MARGIN_LOG] = [NOISE_LOG]). Dentro da margem nunca é "piorou"; piora menor que o ruído
         * também não.
         */
        const val WORSE_MARGIN_LOG = NOISE_LOG
        val WORSE_MIN_ERROR_LOG = ln(1.0 + com.omegas.prohub.equivalence.EquivalenceTolerances.WORSE_ERROR)

        /** Uma faixa só é julgada com leituras em ≥ este número de episódios (visitas separadas por ≥ 60 s), não só 8 quadros. */
        const val MIN_BAND_EPISODES = EvidencePairs.MIN_VISITS
        const val MIN_SCALE = 0.4
        const val MAX_SCALE = 1.3
        /** A cada experimento fechado o ganho aprendido volta esta fração do caminho até 1,0 (nunca multiplica para sempre). */
        const val LEARN_DECAY = 0.8
        const val MAX_EXPERIMENTS = 40
        const val POINT_COUNT = 30
        /** Abaixo disso o motor tem estratégia própria (lenta): não conta como condução da verificação. */
        const val DRIVING_MIN_RPM = EquivalenceLedger.DRIVING_MIN_RPM
        /** Estado de um experimento cuja gravação falhou com a ECU possivelmente alterada (só a foto permite voltar). */
        const val STATUS_FAILED_PARTIAL = "FALHA_PARCIAL"
        val BANDS = EquivalenceLedger.BANDS
        /**
         * Tempo de CONDUÇÃO (rpm ≥ 1000 numa faixa alterada) depois da gravação. Passado isso a verificação fecha com o
         * que já deu para julgar: faixa que o motorista não visita não pode segurar o refino para sempre.
         */
        const val VERIFY_PARTIAL_ONLINE_MS = 15 * 60_000L
        /** Sem nenhuma faixa julgável depois disso: INCONCLUSIVO, e o refino segue medindo do zero. */
        const val VERIFY_GIVE_UP_ONLINE_MS = 40 * 60_000L
        private const val MAX_TICK_MS = 10_000L
        private const val SAVE_EVERY_MS = 60_000L
        /** Estados em que o experimento ainda espera dados. */
        const val STATUS_VERIFYING = "VERIFICANDO"

        private val UNDO_REASON = Regex("(restaurar|desfazer|reset|neutralizar)", RegexOption.IGNORE_CASE)

        /**
         * A gravação de Curva K era um Desfazer/Restaurar/Reset (o motivo que o escritor da ECU registra)? Voltar a uma
         * foto, ou zerar para 1,0, não é uma passada de ganho. Motivo desconhecido = gravação normal.
         */
        fun isUndoReason(reason: String): Boolean = UNDO_REASON.containsMatchIn(reason)
    }

    private val lock = Any()
    private val experiments = ArrayList<JSONObject>()
    private val bandScale = DoubleArray(BANDS.size) { 1.0 }
    /** Quantas vezes cada ponto da Curva K já foi alterado por gravação confirmada (ganho decrescente, E1). */
    private val pointPasses = IntArray(POINT_COUNT)
    private var experimentSequence = 0L
    private var lastEvaluateAt = 0L
    private var lastSaveAt = 0L

    init { load() }

    private var decisionListener: ((JSONObject) -> Unit)? = null

    /** Entrega a transição no momento em que ocorre; o consumidor apenas enfileira o registro. */
    fun setDecisionListener(listener: ((JSONObject) -> Unit)?) = synchronized(lock) {
        decisionListener = listener
    }

    private fun publishDecision(exp: JSONObject) {
        decisionListener?.invoke(JSONObject(exp.toString()))
    }

    /** Registra uma gravação de Curva K confirmada (antes/depois + índice medido com a curva antiga). */
    fun recordCurveWrite(
        beforeRaw: IntArray,
        afterRaw: IntArray,
        axisRaw: IntArray,
        indexBefore: JSONObject,
        source: String,
        /** Foto da curva tirada ANTES desta gravação: o Desfazer restaura exatamente ela. */
        photoFile: String = "",
        /**
         * Desfazer/Restaurar: voltar não é uma passada de ganho. Não conta passada (e devolve a que a gravação desfeita
         * tinha contado) e a verificação dele não ensina o ganho das faixas.
         */
        restore: Boolean = false,
    ) {
        synchronized(lock) {
            for (i in 0 until min(POINT_COUNT, min(beforeRaw.size, afterRaw.size))) {
                if (beforeRaw[i] != afterRaw[i]) pointPasses[i] = if (restore) max(0, pointPasses[i] - 1) else pointPasses[i] + 1
            }
            experiments.lastOrNull()?.takeIf { it.optString("status") == "VERIFICANDO" }?.let {
                it.put("status", "INTERROMPIDO").put("reasonCode", "SUPERSEDED_BY_CONFIRMED_WRITE")
                    .put("failureDomain", "FUNCTIONAL").put("closedAt", clock())
                publishDecision(it)
            }
            experimentSequence += 1L
            experiments += JSONObject()
                .put("id", "EXP-${clock()}-$experimentSequence")
                .put("appliedAt", clock())
                .put("source", source)
                .put("axisRaw", JSONArray(axisRaw.toList()))
                .put("beforeRaw", JSONArray(beforeRaw.toList()))
                .put("afterRaw", JSONArray(afterRaw.toList()))
                .put("indexBefore", indexBefore)
                .put("photoFile", photoFile)
                .put("restore", restore)
                .put("onlineMs", 0L)
                .put("status", "VERIFICANDO").put("reasonCode", "MANUAL_WRITE_CONFIRMED").put("failureDomain", "NONE")
            while (experiments.size > MAX_EXPERIMENTS) experiments.removeAt(0)
            publishDecision(experiments.last())
        }
        save()
    }

    /**
     * A gravação falhou com a ECU possivelmente alterada (falha parcial): registra o experimento só com a foto,
     * para o Desfazer aparecer. Não há antes/depois conhecido: o único caminho de volta é a foto.
     */
    fun recordFailedWrite(photoFile: String, source: String, partial: Boolean) {
        synchronized(lock) {
            experiments.lastOrNull()?.takeIf { it.optString("status") == STATUS_VERIFYING }?.let {
                it.put("status", "INTERROMPIDO").put("reasonCode", "SUPERSEDED_BY_FAILED_WRITE")
                    .put("failureDomain", "FUNCTIONAL").put("closedAt", clock())
                publishDecision(it)
            }
            experimentSequence += 1L
            experiments += JSONObject()
                .put("id", "EXP-${clock()}-$experimentSequence")
                .put("appliedAt", clock())
                .put("closedAt", clock())
                .put("source", source)
                .put("photoFile", photoFile)
                .put("partial", partial)
                .put("onlineMs", 0L)
                .put("status", STATUS_FAILED_PARTIAL).put("reasonCode", "WRITE_FAILED_ECU_MAY_HAVE_CHANGED").put("failureDomain", "FUNCTIONAL")
            while (experiments.size > MAX_EXPERIMENTS) experiments.removeAt(0)
            publishDecision(experiments.last())
        }
        save()
    }

    /** Algo mudou o motor por fora (Mapa K, AutoMatch nativo): a verificação perde validade. */
    fun interrupt(reason: String) {
        synchronized(lock) {
            // O AutoMatch nativo reescreveu a Curva K inteira: ganho aprendido e passadas eram da curva antiga.
            if (reason == "AUTOMATCH_NATIVO") { bandScale.fill(1.0); pointPasses.fill(0) }
            experiments.lastOrNull()?.takeIf { it.optString("status") == "VERIFICANDO" }?.let {
                it.put("status", "INTERROMPIDO").put("reasonCode", "EXPERIMENT_INVALIDATED")
                    .put("failureDomain", "FUNCTIONAL").put("interruptReason", reason).put("closedAt", clock())
                publishDecision(it)
            }
        }
        save()
    }

    /**
     * Avalia o experimento em verificação com o índice atual (curva nova). Retorna true se algo
     * visível mudou (veredito, estado). [ecuOnline] conta o tempo de condução da verificação.
     *
     * Fecha de três jeitos, nunca fica eterno:
     *  - todas as faixas tocadas julgadas → VERIFICADO / PIOROU_EM_PARTE;
     *  - nenhuma faixa tocada tem medição de antes → SEM_BASE (a curva nova vira a base e o refino segue);
     *  - passou [VERIFY_PARTIAL_ONLINE_MS] de condução: fecha com o que foi julgado (faixas sem dado
     *    ficam SEM_DADOS); sem nenhuma julgada em [VERIFY_GIVE_UP_ONLINE_MS] → INCONCLUSIVO.
     */
    fun evaluate(indexNow: JSONObject, ecuOnline: Boolean = true, rpm: Double? = null, petrolMs: Double? = null): Boolean {
        var needsSave = false
        val changed = synchronized(lock) {
            val now = clock()
            val dt = if (lastEvaluateAt == 0L) 0L else (now - lastEvaluateAt).coerceIn(0L, MAX_TICK_MS)
            lastEvaluateAt = now
            val exp = experiments.lastOrNull()?.takeIf { it.optString("status") == STATUS_VERIFYING } ?: return false
            // O orçamento conta CONDUÇÃO, não tempo conectado: rpm ≥ 1000 numa faixa que a gravação alterou.
            // Sem leitura de rpm (chamador antigo/teste) cai no tempo online.
            val driving = rpm == null || (rpm >= DRIVING_MIN_RPM && petrolMs != null && bandVisited(exp, petrolMs))
            if (ecuOnline && driving) exp.put("onlineMs", exp.optLong("onlineMs", 0L) + dt)
            val onlineMs = exp.optLong("onlineMs", 0L)
            val before = exp.optJSONObject("indexBefore")?.optJSONArray("bands") ?: JSONArray()
            val after = indexNow.optJSONArray("bands") ?: JSONArray()
            val verdicts = JSONArray()
            var touchedBands = 0
            var judged = 0
            var worse = 0
            var pending = 0
            var withoutBase = 0
            // Sem relógio (decisão do dono, 2026-10-05): a verificação fecha quando as faixas tocadas forem julgadas, nunca por
            // minutos de condução. `onlineMs` continua só como diagnóstico.
            val timeboxed = false
            for (i in BANDS.indices) {
                val b = before.optJSONObject(i) ?: JSONObject()
                val a = after.optJSONObject(i) ?: JSONObject()
                val nb = b.optInt("samples", 0)
                val na = a.optInt("samples", 0)
                // Evidência independente: episódios (visitas ≥ 60 s) e leituras espalhadas por dentro da faixa. Desconhecido = legado.
                val eb = if (b.has("episodes") && !b.isNull("episodes")) b.optInt("episodes") else null
                val ea = if (a.has("episodes") && !a.isNull("episodes")) a.optInt("episodes") else null
                val solidBefore = nb >= MIN_BAND_SAMPLES && (eb == null || eb >= MIN_BAND_EPISODES) && b.optBoolean("interiorCovered", true)
                val solidAfter = na >= MIN_BAND_SAMPLES && (ea == null || ea >= MIN_BAND_EPISODES) && a.optBoolean("interiorCovered", true)
                val rb = b.optDouble("ratio", Double.NaN)
                val ra = a.optDouble("ratio", Double.NaN)
                val verdict = JSONObject().put("fromMs", BANDS[i].first).put("toMs", BANDS[i].second)
                    .put("samplesBefore", nb).put("samplesAfter", na)
                    .put("ratioBefore", if (rb.isFinite()) rb else JSONObject.NULL)
                    .put("ratioAfter", if (ra.isFinite()) ra else JSONObject.NULL)
                val touched = bandTouched(exp, BANDS[i])
                if (touched) touchedBands++
                when {
                    !touched -> verdict.put("verdict", "NAO_ALTERADA")
                    !solidBefore || !rb.isFinite() -> { verdict.put("verdict", "SEM_ANTES"); withoutBase++ }
                    !solidAfter || !ra.isFinite() -> {
                        if (timeboxed) verdict.put("verdict", "SEM_DADOS") else { verdict.put("verdict", "COLETANDO"); pending++ }
                    }
                    else -> {
                        val e0 = ln(rb)
                        val e1 = ln(ra)
                        // Ordem: piorou (fora da margem e pior além do ruído) → passou do ponto (trocou de lado além do
                        // ruído: ensina o ganho) → confirmada (dentro da margem aceita) → curta (mesmo lado, ainda fora).
                        val v = when {
                            abs(e1) > WORSE_MIN_ERROR_LOG && abs(e1) > abs(e0) + WORSE_MARGIN_LOG -> "PIOROU"
                            e0 * e1 < 0 && abs(e1) > NOISE_LOG -> "PASSOU"
                            abs(e1) <= OK_AFTER_LOG -> "CONFIRMADA"
                            else -> "CURTA"
                        }
                        verdict.put("verdict", v)
                        judged++
                        if (v == "PIOROU") worse++
                    }
                }
                val code = when (verdict.optString("verdict")) {
                    "NAO_ALTERADA" -> "UNCHANGED_BAND"
                    "SEM_ANTES" -> "BASELINE_MISSING"
                    "SEM_DADOS" -> "COVERAGE_TIMEOUT"
                    "COLETANDO" -> "POST_WRITE_SAMPLES_PENDING"
                    "CONFIRMADA" -> "WITHIN_NOISE_OR_RELATIVE_IMPROVEMENT"
                    "PIOROU" -> "ERROR_INCREASE_EXCEEDS_MARGIN"
                    "PASSOU" -> "ERROR_SIGN_REVERSED"
                    else -> "REMAINING_ERROR_SAME_DIRECTION"
                }
                verdict.put("reasonCode", code).put("decision", JSONObject()
                    .put("errorBeforeLog", if (rb.isFinite() && rb > 0) ln(rb) else JSONObject.NULL)
                    .put("errorAfterLog", if (ra.isFinite() && ra > 0) ln(ra) else JSONObject.NULL)
                    .put("worseMarginLog", WORSE_MARGIN_LOG).put("okAfterLog", OK_AFTER_LOG)
                    .put("relativeImprovementFactor", 0.35)
                    .put("samplesBefore", nb).put("samplesAfter", na)
                    .put("episodesBefore", eb ?: JSONObject.NULL).put("episodesAfter", ea ?: JSONObject.NULL)
                    .put("minSamples", MIN_BAND_SAMPLES).put("minEpisodes", MIN_BAND_EPISODES).put("onlineMs", onlineMs))
                verdicts.put(verdict)
            }
            // Tempo segue no diagnóstico, mas não é uma mudança da decisão visível.
            val signature = JSONArray((0 until verdicts.length()).map { i ->
                JSONObject(verdicts.getJSONObject(i).toString()).also {
                    it.optJSONObject("decision")?.remove("onlineMs")
                }
            }).toString()
            val visibleChange = signature != exp.optString("verdictSignature")
            exp.put("verdictSignature", signature)
            exp.put("bands", verdicts).put("indexAfter", indexNow).put("evaluatedAt", now)
            val closedStatus: String? = when {
                // Responsivo: uma faixa que piorou (com evidência) fecha na hora e oferece restaurar só aquele trecho;
                // não espera as outras faixas terem leitura. Nenhuma faixa tocada é ignorada: as demais seguem julgadas
                // pela prova por ponto do cérebro.
                worse > 0 -> "PIOROU_EM_PARTE"
                pending == 0 && judged > 0 -> "VERIFICADO"
                // Nada a esperar e nada julgável: não há medição de antes nas faixas tocadas.
                pending == 0 && touchedBands > 0 && withoutBase == touchedBands -> "SEM_BASE"
                pending == 0 && touchedBands == 0 -> "SEM_BASE"
                timeboxed && judged > 0 -> if (worse > 0) "PIOROU_EM_PARTE" else "VERIFICADO"
                else -> null
            }
            if (closedStatus != null) {
                exp.put("status", closedStatus).put("closedAt", now)
                    .put("reasonCode", when (closedStatus) {
                        "PIOROU_EM_PARTE" -> "WORSE_BANDS_DETECTED"
                        "VERIFICADO" -> "BAND_VERIFICATION_COMPLETE"
                        "SEM_BASE" -> "BASELINE_MISSING"
                        else -> "VERIFICATION_COVERAGE_TIMEOUT"
                    })
                    .put("failureDomain", if (closedStatus in setOf("PIOROU_EM_PARTE", "INCONCLUSIVO")) "FUNCTIONAL" else "NONE")
                // Desfazer/Restaurar não ensina o ganho: voltar não é medir a correção.
                // O ganho aprendido decai a cada experimento fechado (mesmo sem faixa julgada); só o veredito ensina.
                if (!exp.optBoolean("restore", false)) learn(verdicts)
                needsSave = true
            } else if (now - lastSaveAt >= SAVE_EVERY_MS) {
                needsSave = true
            }
            (visibleChange || closedStatus != null).also { if (it) publishDecision(exp) }
        }
        if (needsSave) save()
        return changed
    }

    /** O ponto de operação atual (Petrol Inj. em ms) está numa faixa que este experimento alterou? */
    private fun bandVisited(exp: JSONObject, petrolMs: Double): Boolean {
        val band = BANDS.firstOrNull { petrolMs >= it.first && petrolMs < it.second } ?: return false
        return bandTouched(exp, band)
    }

    private fun bandTouched(exp: JSONObject, band: kotlin.Pair<Double, Double>): Boolean {
        val axis = exp.optJSONArray("axisRaw") ?: return true
        val before = exp.optJSONArray("beforeRaw") ?: return true
        val after = exp.optJSONArray("afterRaw") ?: return true
        for (i in 0 until axis.length()) {
            val ms = axis.optInt(i) / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS
            if (ms >= band.first - 0.5 && ms < band.second + 0.5 && before.optInt(i) != after.optInt(i)) return true
        }
        return false
    }

    /**
     * Ganho por faixa para a próxima proposta: passou → mais suave; piorou → bem mais suave; curta E melhorou → um pouco
     * mais firme (curta que NÃO melhorou não firma ganho). Antes de aplicar o veredito o ganho aprendido volta uma fração
     * do caminho até 1,0 em TODAS as faixas ([LEARN_DECAY]): o aprendizado esquece, não se acumula para sempre.
     */
    private fun learn(verdicts: JSONArray) {
        for (i in 0 until min(verdicts.length(), bandScale.size)) {
            bandScale[i] = 1.0 + (bandScale[i] - 1.0) * LEARN_DECAY
            val v = verdicts.optJSONObject(i)
            val improved = v?.optJSONObject("decision")?.let { d ->
                val e0 = d.optDouble("errorBeforeLog", Double.NaN)
                val e1 = d.optDouble("errorAfterLog", Double.NaN)
                e0.isFinite() && e1.isFinite() && abs(e1) < abs(e0)
            } ?: false
            bandScale[i] = when (v?.optString("verdict")) {
                "PASSOU" -> max(MIN_SCALE, bandScale[i] * 0.7)
                "PIOROU" -> max(MIN_SCALE, bandScale[i] * 0.5)
                "CURTA" -> if (improved) min(MAX_SCALE, bandScale[i] * 1.15) else bandScale[i]
                "CONFIRMADA" -> bandScale[i] + (1.0 - bandScale[i]) * 0.2
                else -> bandScale[i]
            }
        }
    }

    /** Escala de ganho por ponto do eixo (1,0 fora das faixas de condução). */
    fun pointGainScale(axisMs: List<Double>): DoubleArray = synchronized(lock) {
        DoubleArray(axisMs.size) { j ->
            val band = BANDS.indexOfFirst { axisMs[j] >= it.first && axisMs[j] < it.second }
            val learned = if (band < 0) 1.0 else bandScale[band]
            // Ganho decrescente por ponto, independente do veredito: 1,0 → 0,7 → 0,5 a cada gravação que o altera.
            learned * AutoMatchRefinedEngine.passGain(if (j < POINT_COUNT) pointPasses[j] else 0)
        }
    }

    /**
     * Pontos a restaurar do último experimento: só os das faixas que pioraram. Sem relógio: a oferta vale enquanto este for
     * o último experimento (uma gravação nova, Desfazer ou interrupção a substitui) e o piloto só a mostra enquanto a faixa
     * seguir fora da margem.
     */
    fun restorePoints(): JSONArray = synchronized(lock) {
        val exp = experiments.lastOrNull()?.takeIf { it.optString("status") == "PIOROU_EM_PARTE" } ?: return JSONArray()
        val axis = exp.optJSONArray("axisRaw") ?: return JSONArray()
        val before = exp.optJSONArray("beforeRaw") ?: return JSONArray()
        val after = exp.optJSONArray("afterRaw") ?: return JSONArray()
        val bad = (0 until (exp.optJSONArray("bands")?.length() ?: 0))
            .mapNotNull { exp.optJSONArray("bands")?.optJSONObject(it) }
            .filter { it.optString("verdict") == "PIOROU" }
            .map { it.optDouble("fromMs") to it.optDouble("toMs") }
        val out = JSONArray()
        for (i in 0 until axis.length()) {
            val ms = axis.optInt(i) / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS
            if (bad.any { ms >= it.first - 0.5 && ms < it.second + 0.5 } && before.optInt(i) != after.optInt(i)) {
                out.put(JSONObject().put("index", i).put("currentRaw", after.optInt(i)).put("targetRaw", before.optInt(i)))
            }
        }
        out
    }

    fun json(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("ok", true)
            .put("format", FORMAT)
            .put("bandScale", JSONArray(bandScale.toList()))
            .put("pointPasses", JSONArray(pointPasses.toList()))
            .put("bands", JSONArray(BANDS.map { JSONObject().put("fromMs", it.first).put("toMs", it.second) }))
            .put("verifyBudgetMs", VERIFY_PARTIAL_ONLINE_MS)
            .put("giveUpBudgetMs", VERIFY_GIVE_UP_ONLINE_MS)
            .put("latest", experiments.lastOrNull()?.let { JSONObject(it.toString()).apply { remove("axisRaw"); remove("verdictSignature") } } ?: JSONObject.NULL)
            .put("count", experiments.size)
            .put("history", JSONArray(experiments.takeLast(10).map { e ->
                JSONObject().put("id", e.optString("id")).put("appliedAt", e.optLong("appliedAt"))
                    .put("status", e.optString("status")).put("source", e.optString("source"))
                    .put("ratioBefore", e.optJSONObject("indexBefore")?.opt("ratio") ?: JSONObject.NULL)
                    .put("ratioAfter", e.optJSONObject("indexAfter")?.opt("ratio") ?: JSONObject.NULL)
            }))
            .put("automatic", false)
    }

    /** Um só escritor por vez; o payload é montado e gravado sob `saveLock` (o disco nunca volta atrás). */
    private val saveLock = Any()
    private var buildSeq = 0L
    private var writtenSeq = 0L

    /** Grava já (fim do serviço): o Desfazer depende de `photoFile`. */
    fun flush() = save()

    private fun save() {
        val target = file ?: return
        synchronized(saveLock) {
            val payload = synchronized(lock) {
                lastSaveAt = clock()
                JSONObject().put("format", FORMAT)
                    .put("bandScale", JSONArray(bandScale.toList()))
                    .put("pointPasses", JSONArray(pointPasses.toList()))
                    .put("experimentSequence", experimentSequence)
                    .put("experiments", JSONArray(experiments.map { JSONObject(it.toString()) }))
            }
            val seq = ++buildSeq
            if (seq <= writtenSeq) return
            try {
                JsonFiles.writeAtomic(target, payload.toString())
                writtenSeq = seq
            } catch (_: Exception) {
            }
        }
    }

    private fun load() {
        val source = file ?: return
        try {
            val root = JsonFiles.readJsonWithBak(source) { it.optString("format") == FORMAT } ?: return
            root.optJSONArray("bandScale")?.let { a ->
                for (i in 0 until min(a.length(), bandScale.size)) bandScale[i] = a.optDouble(i, 1.0).coerceIn(MIN_SCALE, MAX_SCALE)
            }
            root.optJSONArray("pointPasses")?.let { a ->
                for (i in 0 until min(a.length(), POINT_COUNT)) pointPasses[i] = a.optInt(i, 0).coerceIn(0, 1000)
            }
            root.optJSONArray("experiments")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(experiments::add) }
            val loadedSequence = experiments.mapNotNull {
                it.optString("id").takeIf { id -> id.count { c -> c == '-' } >= 2 }
                    ?.substringAfterLast('-')?.toLongOrNull()
            }.maxOrNull() ?: 0L
            experimentSequence = max(root.optLong("experimentSequence", 0L), loadedSequence).coerceAtLeast(0L)
        } catch (_: Exception) {
            experiments.clear()
        }
    }
}
