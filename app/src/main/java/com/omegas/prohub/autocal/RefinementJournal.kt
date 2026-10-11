package com.omegas.prohub.autocal

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
 * medido com a curva antiga. Depois: o mesmo índice medido só com o GNV lido depois da
 * gravação. Por faixa o resultado é CONFIRMADA (chegou perto da gasolina), PASSOU
 * (inverteu o sinal além do ruído), CURTA (mesmo sinal, pouca melhora) ou PIOROU
 * (ficou mais longe). Cada ponto alterado herda o veredito das faixas que ele toca.
 *
 * Ciclo (sem estado preso, sem "Entendi"):
 *  - cada gravação é dona das faixas que tocou; outra gravação só interrompe as faixas
 *    que ela mesma tocar (pontos independentes);
 *  - a verificação nunca é eterna: fecha com tudo julgado, sem base (SEM_BASE), com o que
 *    deu para julgar depois de [VERIFY_PARTIAL_ONLINE_MS] rodando, ou INCONCLUSIVO; parado ou em
 *    marcha lenta o prazo não anda e não há veredito (o índice só tem condução);
 *  - "piorou" só fora da margem do dono (Platina 41123729): mais de 4% pior E erro acima de 5%;
 *  - só gravação de proposta/ajuste ensina o ganho por faixa; restaurar e desfazer não;
 *  - restaurar oferece só os pontos que pioraram e cuja faixa ainda está fora de 5%, nunca de uma
 *    restauração/desfazer; desfazer só enquanto a gravação ainda está sendo medida;
 *  - depois de restaurar/desfazer, o ponto não recebe proposta na mesma direção até a faixa
 *    ser medida de novo; depois de restaurar, nunca mais o valor que piorou (nem além).
 *
 * Nunca grava na ECU: só registra, avalia e informa o piloto/UI.
 */
class RefinementJournal(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-refinement-journal-v1"
        /** Amostras por faixa (antes e depois) para julgar uma faixa. */
        const val MIN_BAND_SAMPLES = 8
        /** Diferença abaixo disso é ruído de medição (~2%). */
        const val NOISE_LOG = 0.02
        /** Dentro de ±3% depois da correção é "chegou" (Platina 41123729). */
        const val OK_AFTER_LOG = 0.0296
        /** "Piorou" só se ficou mais de ~4% pior E acima de ~5% de erro (margem do dono; Platina 41123729). */
        const val WORSE_MARGIN_LOG = 0.04
        const val WORSE_MIN_ERROR_LOG = 0.05
        const val MIN_SCALE = 0.4
        const val MAX_SCALE = 1.3
        const val MAX_EXPERIMENTS = 40
        val BANDS = EquivalenceLedger.BANDS
        /** Um ponto age na faixa até meia unidade de ms além das bordas (K interpolado entre pontos vizinhos). */
        const val BAND_MARGIN_MS = 0.5
        /**
         * Tempo rodando (ECU online, RPM de condução) depois da gravação. Passado isso a verificação fecha com
         * o que já deu para julgar: faixa que o motorista não visita não pode segurar o refino para sempre.
         */
        const val VERIFY_PARTIAL_ONLINE_MS = 15 * 60_000L
        /** Sem nenhuma faixa julgável depois disso: INCONCLUSIVO, e o refino segue. */
        const val VERIFY_GIVE_UP_ONLINE_MS = 40 * 60_000L
        private const val MAX_TICK_MS = 10_000L
        private const val SAVE_EVERY_MS = 60_000L
        const val STATUS_VERIFYING = "VERIFICANDO"

        /** Tipo da gravação, pelo motivo que a tela passa ao KFactorManager (confirmedEvents[].reason). */
        const val KIND_PROPOSTA = "PROPOSTA"
        const val KIND_RESTAURAR = "RESTAURAR"
        const val KIND_DESFAZER = "DESFAZER"
        const val KIND_MANUAL = "MANUAL"
        const val REASON_PREFIX = "Refino OMEGAS:"

        fun kindFromReason(reason: String?): String {
            val text = reason.orEmpty().trim()
            if (!text.startsWith(REASON_PREFIX)) return KIND_MANUAL
            val verb = text.removePrefix(REASON_PREFIX).trim().lowercase()
            return when {
                verb.startsWith("desfazer") -> KIND_DESFAZER
                verb.startsWith("restaurar") -> KIND_RESTAURAR
                else -> KIND_PROPOSTA
            }
        }

        /** Só o resultado de uma proposta (ou ajuste manual, como antes) ensina o ganho e pode pedir restauração. */
        private val TEACHES = setOf(KIND_PROPOSTA, KIND_MANUAL)
        private val JUDGED = setOf("CONFIRMADA", "PASSOU", "CURTA", "PIOROU")
        const val OUTSIDE_AUTOMATCH = "AUTOMATCH_NATIVO"
        const val OUTSIDE_CURVE = "CURVA_K_MUDOU_FORA_DO_APP"
        private val CURVE_CHANGED_OUTSIDE = setOf(OUTSIDE_AUTOMATCH, OUTSIDE_CURVE)

        /** Faixas em que um ponto do eixo age (com a margem de interpolação). */
        fun touchedBands(ms: Double): List<Int> =
            BANDS.indices.filter { ms >= BANDS[it].first - BAND_MARGIN_MS && ms < BANDS[it].second + BAND_MARGIN_MS }

        /**
         * Trechos de Petrol Inj. (ms) em que o K interpolado mudou: de um ponto vizinho ao outro de cada ponto
         * alterado. O GNV medido fora daqui continua valendo depois da gravação.
         */
        fun affectedRangesMs(axisRaw: IntArray, beforeRaw: IntArray, afterRaw: IntArray): List<Pair<Double, Double>> {
            val ms = axisRaw.map { it / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS }
            val raw = beforeRaw.indices.filter { it < afterRaw.size && it < ms.size && beforeRaw[it] != afterRaw[it] }.map { i ->
                (if (i == 0) 0.0 else ms[i - 1]) to (if (i == ms.lastIndex) Double.POSITIVE_INFINITY else ms[i + 1])
            }.sortedBy { it.first }
            val merged = ArrayList<Pair<Double, Double>>()
            raw.forEach { r ->
                val last = merged.lastOrNull()
                if (last != null && r.first <= last.second) merged[merged.lastIndex] = last.first to max(last.second, r.second) else merged += r
            }
            return merged
        }
    }

    private val lock = Any()
    private val experiments = ArrayList<JSONObject>()
    private val bandScale = DoubleArray(BANDS.size) { 1.0 }
    /** Bloqueios por ponto depois de restaurar/desfazer: {index, direction, badRaw, restoredRaw, at, kind, capped}. */
    private val blocks = ArrayList<JSONObject>()
    private var lastEvaluateAt = 0L
    private var lastSaveAt = Long.MIN_VALUE / 2
    private var experimentSequence = 0L
    /** Último índice avaliado (condução): restaurar só vale enquanto a faixa ainda está fora da margem. */
    private var lastIndexNow: JSONObject? = null

    init { load() }

    // ------------------------------------------------------------------ gravação

    /** Registra uma gravação de Curva K confirmada (antes/depois + índice medido com a curva antiga). */
    fun recordCurveWrite(
        beforeRaw: IntArray,
        afterRaw: IntArray,
        axisRaw: IntArray,
        indexBefore: JSONObject,
        source: String,
        kind: String = KIND_MANUAL,
    ) {
        synchronized(lock) {
            val now = clock()
            val changed = beforeRaw.indices.filter { it < afterRaw.size && beforeRaw[it] != afterRaw[it] }
            val touched = changed.filter { it < axisRaw.size }
                .flatMap { touchedBands(axisRaw[it] / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS) }.toSet()
            val prev = experiments.lastOrNull()
            if (kind == KIND_DESFAZER && prev != null) {
                // Desfazer encerra a verificação da gravação desfeita sem ensinar nada.
                prev.put("undone", true)
                if (prev.optString("status") == STATUS_VERIFYING) prev.put("status", "DESFEITO").put("closedAt", now)
            }
            if (kind == KIND_RESTAURAR) {
                restoreSource()?.let { src ->
                    val restored = src.optJSONArray("restored") ?: JSONArray()
                    changed.forEach { restored.put(it) }
                    src.put("restored", restored)
                }
            }
            // Outra gravação só interrompe as faixas que ela tocar. Faixa já julgada guarda o veredito que tinha.
            for (e in experiments) {
                if (e.optString("status") != STATUS_VERIFYING) continue
                val owned = ownedBands(e)
                val frozen = frozenBands(e)
                owned.filter { verdictOf(e, it) in JUDGED }.forEach { frozen += it }
                val lost = owned.filter { it in touched && verdictOf(e, it) !in JUDGED }
                lost.forEach { b -> e.optJSONArray("bands")?.optJSONObject(b)?.put("verdict", "INTERROMPIDA"); frozen += b }
                owned.removeAll(lost.toSet())
                e.put("owned", JSONArray(owned.toList())).put("frozen", JSONArray(frozen.toList()))
                if (owned.none { verdictOf(e, it) !in JUDGED }) close(e, now)
            }
            for (i in changed) {
                blocks.removeAll { it.optInt("index") == i }
                if (kind == KIND_RESTAURAR || kind == KIND_DESFAZER) {
                    blocks += JSONObject().put("index", i)
                        // Direção que levou ao valor ruim: do valor restaurado para o valor desfeito.
                        .put("direction", if (beforeRaw[i] > afterRaw[i]) 1 else -1)
                        .put("badRaw", beforeRaw[i]).put("restoredRaw", afterRaw[i])
                        .put("at", now).put("kind", kind)
                        // Restaurar = medido pior: nunca mais esse valor. Desfazer = escolha do dono: só espera evidência nova.
                        .put("capped", kind == KIND_RESTAURAR)
                }
            }
            experimentSequence += 1L
            experiments += JSONObject()
                .put("id", "EXP-$now-$experimentSequence")
                .put("appliedAt", now)
                .put("source", source)
                .put("kind", kind)
                .put("axisRaw", JSONArray(axisRaw.toList()))
                .put("beforeRaw", JSONArray(beforeRaw.toList()))
                .put("afterRaw", JSONArray(afterRaw.toList()))
                .put("indexBefore", indexBefore)
                .put("owned", JSONArray(touched.toList()))
                .put("onlineMs", 0L)
                .put("status", STATUS_VERIFYING)
            while (experiments.size > MAX_EXPERIMENTS) experiments.removeAt(0)
            lastSaveAt = Long.MIN_VALUE / 2
        }
        save()
    }

    /** Algo mudou o motor por fora (Mapa K, AutoMatch nativo, curva por fora do app): verificações perdem validade. */
    fun interrupt(reason: String) {
        synchronized(lock) {
            experiments.filter { it.optString("status") == STATUS_VERIFYING }.forEach {
                it.put("status", "INTERROMPIDO").put("interruptReason", reason).put("closedAt", clock())
            }
            if (reason in CURVE_CHANGED_OUTSIDE) {
                // A curva da ECU não é mais a que o app gravou: desfazer/restaurar e bloqueios perdem a referência.
                experiments.filter { !it.has("curveChangedOutside") }.forEach { it.put("curveChangedOutside", reason) }
                blocks.clear()
            }
            lastSaveAt = Long.MIN_VALUE / 2
        }
        save()
    }

    /**
     * Curva lida da ECU depois da última gravação ([mulActCapturedAtMs] > appliedAt) diferente da gravada nos
     * pontos alterados: mudou por fora do app (ProgBase, reset). Interrompe e retorna true uma vez.
     */
    fun noteEcuCurve(currentRaw: IntArray, mulActCapturedAtMs: Long): Boolean {
        val outside = synchronized(lock) {
            val latest = experiments.lastOrNull() ?: return false
            if (latest.has("curveChangedOutside") || mulActCapturedAtMs <= latest.optLong("appliedAt")) return false
            val after = latest.optJSONArray("afterRaw") ?: return false
            changedIndexes(latest).any { it < currentRaw.size && currentRaw[it] != after.optInt(it) }
        }
        if (outside) interrupt(OUTSIDE_CURVE)
        return outside
    }

    // ---------------------------------------------------------------- verificação

    /**
     * Avalia cada gravação aberta nas faixas que são dela. [indexNow] = índice atual; [indexSince] dá o índice
     * só com o GNV medido a partir de um instante (o "depois" de cada gravação). [driving] = ECU online e
     * rodando (RPM de condução): só então o prazo anda. Retorna true se algum veredito mudou ou fechou.
     */
    fun evaluate(indexNow: JSONObject, driving: Boolean = true, indexSince: ((Long) -> JSONObject)? = null): Boolean {
        var visible = false
        var structural = false
        synchronized(lock) {
            val now = clock()
            val dt = if (lastEvaluateAt == 0L) 0L else (now - lastEvaluateAt).coerceIn(0L, MAX_TICK_MS)
            lastEvaluateAt = now
            lastIndexNow = indexNow
            for (exp in experiments.filter { it.optString("status") == STATUS_VERIFYING }) {
                if (driving) exp.put("onlineMs", exp.optLong("onlineMs", 0L) + dt)
                val onlineMs = exp.optLong("onlineMs", 0L)
                val timeboxed = onlineMs >= VERIFY_PARTIAL_ONLINE_MS
                val owned = ownedBands(exp)
                val frozen = frozenBands(exp)
                val afterIndex = indexSince?.invoke(exp.optLong("appliedAt")) ?: indexNow
                val before = exp.optJSONObject("indexBefore")?.optJSONArray("bands") ?: JSONArray()
                val after = afterIndex.optJSONArray("bands") ?: JSONArray()
                val old = exp.optJSONArray("bands")
                val verdicts = JSONArray()
                for (i in BANDS.indices) {
                    val b = before.optJSONObject(i) ?: JSONObject()
                    val a = after.optJSONObject(i) ?: JSONObject()
                    val nb = b.optInt("samples", 0)
                    val na = a.optInt("samples", 0)
                    val rb = b.optDouble("ratio", Double.NaN)
                    val ra = a.optDouble("ratio", Double.NaN)
                    val verdict = JSONObject().put("fromMs", BANDS[i].first).put("toMs", BANDS[i].second)
                        .put("samplesBefore", nb).put("samplesAfter", na)
                        .put("ratioBefore", if (rb.isFinite()) rb else JSONObject.NULL)
                        .put("ratioAfter", if (ra.isFinite()) ra else JSONObject.NULL)
                    val kept = old?.optJSONObject(i)
                    when {
                        i in frozen && kept != null -> verdict.put("verdict", kept.optString("verdict"))
                            .put("ratioAfter", kept.opt("ratioAfter") ?: JSONObject.NULL).put("samplesAfter", kept.optInt("samplesAfter"))
                        i !in owned -> verdict.put("verdict", "NAO_ALTERADA")
                        nb < MIN_BAND_SAMPLES || !rb.isFinite() -> verdict.put("verdict", "SEM_ANTES")
                        na < MIN_BAND_SAMPLES || !ra.isFinite() -> verdict.put("verdict", if (timeboxed) "SEM_DADOS" else "COLETANDO")
                        else -> {
                            val e0 = ln(rb)
                            val e1 = ln(ra)
                            // Política D1 da Platina (41123729): poucos por cento nunca viram "piorou".
                            verdict.put("verdict", when {
                                abs(e1) <= max(NOISE_LOG, OK_AFTER_LOG) || abs(e1) <= abs(e0) * 0.35 -> "CONFIRMADA"
                                abs(e1) > abs(e0) + WORSE_MARGIN_LOG && abs(e1) > WORSE_MIN_ERROR_LOG -> "PIOROU"
                                e0 * e1 < 0 -> "PASSOU"
                                else -> "CURTA"
                            })
                        }
                    }
                    verdicts.put(verdict)
                }
                val names = (0 until verdicts.length()).joinToString(",") { verdicts.getJSONObject(it).optString("verdict") }
                if (names != exp.optString("verdictNames")) visible = true
                exp.put("verdictNames", names).put("bands", verdicts).put("indexAfter", afterIndex).put("evaluatedAt", now)
                // Fecha: tudo julgado; nada com base (SEM_BASE); depois do prazo parcial com o que julgou;
                // sem nada julgável no prazo final → INCONCLUSIVO. Nunca fica eterno.
                val pending = owned.count { verdictOf(exp, it) == "COLETANDO" }
                val judged = BANDS.indices.count { verdictOf(exp, it) in JUDGED }
                val noBase = owned.all { verdictOf(exp, it) in setOf("SEM_ANTES", "INTERROMPIDA") }
                if ((pending == 0 && (judged > 0 || noBase)) || onlineMs >= VERIFY_GIVE_UP_ONLINE_MS) {
                    close(exp, now)
                    structural = true
                }
            }
        }
        val now = clock()
        if (structural || visible || now - lastSaveAt >= SAVE_EVERY_MS) save()
        return visible || structural
    }

    private fun close(exp: JSONObject, now: Long) {
        val owned = ownedBands(exp)
        val all = BANDS.indices.map { verdictOf(exp, it) }
        val judged = all.count { it in JUDGED }
        val status = when {
            judged > 0 -> if ("PIOROU" in all) "PIOROU_EM_PARTE" else "VERIFICADO"
            owned.isEmpty() || owned.all { verdictOf(exp, it) == "SEM_ANTES" } -> "SEM_BASE"
            else -> "INCONCLUSIVO"
        }
        exp.put("status", status).put("closedAt", now)
        // Só proposta/ajuste ensinam o ganho; restaurar e desfazer nunca (senão a próxima proposta repete a direção).
        if (judged > 0 && exp.optString("kind", KIND_MANUAL) in TEACHES) learn(exp.optJSONArray("bands") ?: JSONArray())
        lastSaveAt = Long.MIN_VALUE / 2
    }

    /** Ganho por faixa para a próxima proposta: passou → mais suave; curta → mais firme. */
    private fun learn(verdicts: JSONArray) {
        for (i in 0 until min(verdicts.length(), bandScale.size)) {
            bandScale[i] = when (verdicts.optJSONObject(i)?.optString("verdict")) {
                "PASSOU" -> max(MIN_SCALE, bandScale[i] * 0.7)
                "PIOROU" -> max(MIN_SCALE, bandScale[i] * 0.5)
                "CURTA" -> min(MAX_SCALE, bandScale[i] * 1.15)
                "CONFIRMADA" -> bandScale[i] + (1.0 - bandScale[i]) * 0.2
                else -> bandScale[i]
            }
        }
    }

    /** Escala de ganho por ponto do eixo (1,0 fora das faixas de condução). */
    fun pointGainScale(axisMs: List<Double>): DoubleArray = synchronized(lock) {
        DoubleArray(axisMs.size) { j ->
            val band = BANDS.indexOfFirst { axisMs[j] >= it.first && axisMs[j] < it.second }
            if (band < 0) 1.0 else bandScale[band]
        }
    }

    // -------------------------------------------------------------- por ponto

    private fun changedIndexes(e: JSONObject): List<Int> {
        val b = e.optJSONArray("beforeRaw") ?: return emptyList()
        val a = e.optJSONArray("afterRaw") ?: return emptyList()
        return (0 until min(b.length(), a.length())).filter { b.optInt(it) != a.optInt(it) }
    }

    private fun msOf(e: JSONObject, i: Int): Double = (e.optJSONArray("axisRaw")?.optInt(i) ?: 0) / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS

    private fun ownedBands(e: JSONObject): MutableSet<Int> =
        e.optJSONArray("owned")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toMutableSet() }
            ?: changedIndexes(e).flatMap { touchedBands(msOf(e, it)) }.toMutableSet()

    private fun frozenBands(e: JSONObject): MutableSet<Int> =
        e.optJSONArray("frozen")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toMutableSet() } ?: mutableSetOf()

    private fun verdictOf(e: JSONObject, band: Int): String =
        e.optJSONArray("bands")?.optJSONObject(band)?.optString("verdict").orEmpty()

    /** Veredito de cada ponto alterado, pelas faixas que ele toca (a mesma regra ±0,5 ms de antes). */
    private fun pointVerdicts(exp: JSONObject): JSONArray {
        val out = JSONArray()
        val before = exp.optJSONArray("beforeRaw") ?: return out
        val after = exp.optJSONArray("afterRaw") ?: return out
        for (i in changedIndexes(exp)) {
            val ms = msOf(exp, i)
            val verdicts = touchedBands(ms).map { verdictOf(exp, it) }
            val strict = BANDS.indexOfFirst { ms >= it.first && ms < it.second }
            val verdict = when {
                verdicts.isEmpty() -> "SEM_MEDICAO"
                "PIOROU" in verdicts -> "PIOROU"
                "COLETANDO" in verdicts || verdicts.any { it.isEmpty() } ->
                    if (exp.optString("status") == STATUS_VERIFYING) "COLETANDO" else "SEM_MEDICAO"
                else -> (if (strict >= 0) verdictOf(exp, strict) else null)?.takeIf { it in JUDGED }
                    ?: verdicts.firstOrNull { it in JUDGED }
                    ?: if ("INTERROMPIDA" in verdicts) "INTERROMPIDA" else "SEM_MEDICAO"
            }
            val outcome = when (verdict) {
                "PIOROU" -> "PIOROU"
                "COLETANDO" -> "MEDINDO"
                "CONFIRMADA", "PASSOU", "CURTA" -> "MELHOROU"
                else -> "SEM_MEDICAO"
            }
            out.put(JSONObject().put("index", i).put("ms", ms)
                .put("beforeRaw", before.optInt(i)).put("afterRaw", after.optInt(i))
                .put("verdict", verdict).put("outcome", outcome))
        }
        return out
    }

    /**
     * Gravação de proposta/ajuste mais recente que ainda tem pontos que pioraram para oferecer. Nunca uma
     * restauração ou um desfazer (sem laço). Uma gravação nova em outros pontos não apaga a oferta.
     */
    private fun restoreSource(): JSONObject? {
        for (k in experiments.indices.reversed()) {
            val e = experiments[k]
            if (e.optString("kind", KIND_MANUAL) !in TEACHES) continue
            if (e.optString("status") != "PIOROU_EM_PARTE" || e.optBoolean("undone") || e.has("curveChangedOutside")) continue
            if (offerable(e, k).length() > 0) return e
        }
        return null
    }

    /**
     * Pontos que pioraram, ainda no valor gravado (nenhuma gravação depois mexeu neles), sem restaurar, e cuja
     * faixa ainda está fora da margem do dono agora (se a medição nova voltou para dentro, não há o que restaurar).
     */
    private fun offerable(src: JSONObject, position: Int): JSONArray {
        val restored = src.optJSONArray("restored")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() } ?: emptySet()
        val later = experiments.drop(position + 1).flatMap { changedIndexes(it) }.toSet()
        val now = lastIndexNow?.optJSONArray("bands")
        fun stillWorse(ms: Double): Boolean = touchedBands(ms).any { b ->
            if (verdictOf(src, b) != "PIOROU") return@any false
            val band = now?.optJSONObject(b) ?: return@any true
            val ratio = band.optDouble("ratio", Double.NaN)
            band.optInt("samples", 0) < MIN_BAND_SAMPLES || !ratio.isFinite() || ratio <= 0.0 || abs(ln(ratio)) > WORSE_MIN_ERROR_LOG
        }
        val out = JSONArray()
        val points = pointVerdicts(src)
        for (k in 0 until points.length()) {
            val p = points.getJSONObject(k)
            val i = p.optInt("index")
            if (p.optString("verdict") != "PIOROU" || i in restored || i in later || !stillWorse(p.optDouble("ms"))) continue
            out.put(JSONObject().put("index", i).put("currentRaw", p.optInt("afterRaw")).put("targetRaw", p.optInt("beforeRaw"))
                .put("referenceTimeMs", p.optDouble("ms")))
        }
        return out
    }

    /** Pontos a restaurar: só os que pioraram (fora da margem) na gravação de proposta/ajuste mais recente. */
    fun restorePoints(): JSONArray = synchronized(lock) {
        val src = restoreSource() ?: return JSONArray()
        offerable(src, experiments.indexOf(src))
    }

    /**
     * Desfazer: só da última gravação, se veio de proposta, ainda está sendo medida (o resultado fecha sozinho
     * no fim da medição), não foi desfeita e a curva não mudou por fora. Nunca de um desfazer/restauração.
     */
    private fun undoPointsOf(exp: JSONObject?): JSONArray {
        if (exp == null || exp.optString("kind") != KIND_PROPOSTA || exp.optBoolean("undone") ||
            exp.optString("status") != STATUS_VERIFYING || exp.has("curveChangedOutside")
        ) return JSONArray()
        val before = exp.optJSONArray("beforeRaw") ?: return JSONArray()
        val after = exp.optJSONArray("afterRaw") ?: return JSONArray()
        return JSONArray(changedIndexes(exp).map {
            JSONObject().put("index", it).put("currentRaw", after.optInt(it)).put("targetRaw", before.optInt(it)).put("referenceTimeMs", msOf(exp, it))
        })
    }

    /** Faixas que alguma gravação aberta ainda está medindo (passado o prazo, SEM_DADOS não segura nada). */
    fun measuringBands(): Set<Int> = synchronized(lock) {
        experiments.filter { it.optString("status") == STATUS_VERIFYING }
            .flatMap { e -> ownedBands(e).filter { verdictOf(e, it) == "COLETANDO" || verdictOf(e, it).isEmpty() } }
            .toSet()
    }

    // ---------------------------------------------------------------- proposta

    /**
     * Retenções do ciclo sobre a análise refinada. A regra de entrada de cada ponto é a de antes
     * (origem ≠ HELD e calculatedRaw ≠ currentRaw) e o valor proposto é o do motor, sem mudança.
     * Fica fora: ponto que age numa faixa em medição; ponto restaurado/desfeito na mesma direção sem
     * evidência nova; ponto restaurado que voltaria ao valor que piorou (ou além).
     * Leitura da ECU anterior à última gravação não gera proposta (a curva base é velha).
     */
    fun proposalFrom(analysis: JSONObject, indexNow: JSONObject, indexSince: ((Long) -> JSONObject)? = null): JSONObject {
        val latestAt = synchronized(lock) { experiments.lastOrNull()?.optLong("appliedAt") ?: 0L }
        val out = JSONObject().put("known", false).put("count", 0).put("points", JSONArray()).put("held", JSONArray())
        if (!analysis.optBoolean("available", false)) return out.put("reason", "ANALISE_INDISPONIVEL")
        val captured = analysis.optLong("mulActCapturedAtMs", 0L).takeIf { it > 0L } ?: analysis.optLong("capturedAtMs", 0L)
        if (latestAt > 0L && captured in 1L..latestAt) return out.put("reason", "LENDO_CURVA_NOVA")
        val measuring = measuringBands()
        // Ponto oferecido para restaurar fica fora da proposta (duas ações no mesmo ponto confundem).
        val offered = restorePoints().let { a -> (0 until a.length()).map { a.getJSONObject(it).optInt("index") }.toSet() }
        val myBlocks = synchronized(lock) { blocks.map { JSONObject(it.toString()) } }
        val sinceCache = HashMap<Long, JSONObject>()
        fun bandMeasuredSince(ms: Double, since: Long): Boolean {
            val band = BANDS.indexOfFirst { ms >= it.first && ms < it.second }.takeIf { it >= 0 }
                ?: touchedBands(ms).firstOrNull() ?: return false
            val index = indexSince?.let { provider -> sinceCache.getOrPut(since) { provider(since) } } ?: indexNow
            return (index.optJSONArray("bands")?.optJSONObject(band)?.optInt("samples", 0) ?: 0) >= MIN_BAND_SAMPLES
        }
        val kept = JSONArray()
        val held = JSONArray()
        val points = analysis.optJSONArray("points") ?: JSONArray()
        for (k in 0 until points.length()) {
            val p = points.optJSONObject(k) ?: continue
            if (p.optString("origin") == "HELD" || p.isNull("calculatedRaw") || !p.has("calculatedRaw")) continue
            val current = p.optInt("currentRaw")
            val target = p.optInt("calculatedRaw")
            if (target == current) continue
            val j = p.optInt("index")
            val ms = p.optDouble("referenceTimeMs")
            val direction = if (target > current) 1 else -1
            val block = myBlocks.firstOrNull { it.optInt("index") == j && it.optInt("direction") == direction }
            val reason = when {
                j in offered -> "OFERECIDO_PARA_RESTAURAR"
                touchedBands(ms).any { it in measuring } -> "MEDINDO"
                block != null && !bandMeasuredSince(ms, block.optLong("at")) -> "AGUARDA_EVIDENCIA_NOVA"
                block != null && block.optBoolean("capped") &&
                    (if (direction > 0) target >= block.optInt("badRaw") else target <= block.optInt("badRaw")) -> "VALOR_QUE_PIOROU"
                else -> null
            }
            if (reason != null) {
                held.put(JSONObject().put("index", j).put("referenceTimeMs", ms).put("reason", reason))
            } else {
                kept.put(JSONObject().put("index", j).put("currentRaw", current).put("targetRaw", target)
                    .put("origin", p.optString("origin")).put("referenceTimeMs", ms))
            }
        }
        val signature = (0 until kept.length()).joinToString(";") { i ->
            kept.getJSONObject(i).let { "${it.optInt("index")}:${it.optInt("currentRaw")}>${it.optInt("targetRaw")}" }
        }
        return out.put("known", true).put("count", kept.length()).put("points", kept).put("held", held)
            .put("signature", signature).put("snapshotHash", analysis.optString("snapshotHash"))
    }

    // ------------------------------------------------------------------- saída

    fun json(): JSONObject = synchronized(lock) {
        val last = experiments.lastOrNull()
        val source = restoreSource()
        val result = source ?: last
        JSONObject()
            .put("ok", true)
            .put("format", FORMAT)
            .put("bandScale", JSONArray(bandScale.toList()))
            .put("bands", JSONArray(BANDS.map { JSONObject().put("fromMs", it.first).put("toMs", it.second) }))
            .put("verifyBudgetMs", VERIFY_PARTIAL_ONLINE_MS)
            .put("giveUpBudgetMs", VERIFY_GIVE_UP_ONLINE_MS)
            .put("latest", last?.let { e ->
                JSONObject(e.toString()).apply { remove("axisRaw"); remove("verdictNames"); remove("indexAfter") }
                    .put("points", pointVerdicts(e))
                    .put("undoPoints", undoPointsOf(e))
                    .put("undoable", undoPointsOf(e).length() > 0)
            } ?: JSONObject.NULL)
            // Resultado que a tela mostra (Detalhes): o da oferta de restaurar, senão a última gravação.
            .put("result", result?.let { e ->
                JSONObject().put("id", e.optString("id")).put("kind", e.optString("kind", KIND_MANUAL))
                    .put("status", e.optString("status")).put("appliedAt", e.optLong("appliedAt"))
                    .put("closedAt", e.optLong("closedAt", 0L)).put("onlineMs", e.optLong("onlineMs", 0L))
                    .put("points", pointVerdicts(e))
                    .put("bands", e.optJSONArray("bands") ?: JSONArray())
            } ?: JSONObject.NULL)
            // Progresso da medição em curso (para a frase "faltam N faixas · X de 15 min rodando").
            .put("verifying", experiments.lastOrNull { it.optString("status") == STATUS_VERIFYING }?.let { open ->
                JSONObject().put("id", open.optString("id")).put("bandsLeft", measuringBands().size)
                    .put("drivingMs", open.optLong("onlineMs", 0L)).put("budgetMs", VERIFY_PARTIAL_ONLINE_MS)
                    .put("giveUpMs", VERIFY_GIVE_UP_ONLINE_MS)
            } ?: JSONObject.NULL)
            .put("restoreFrom", source?.optString("id") ?: JSONObject.NULL)
            .put("measuringBands", JSONArray(measuringBands().toList()))
            .put("blocks", JSONArray(blocks.map { JSONObject(it.toString()) }))
            .put("count", experiments.size)
            .put("history", JSONArray(experiments.takeLast(10).map { e ->
                JSONObject().put("id", e.optString("id")).put("appliedAt", e.optLong("appliedAt"))
                    .put("status", e.optString("status")).put("source", e.optString("source"))
                    .put("kind", e.optString("kind", KIND_MANUAL))
                    .put("ratioBefore", e.optJSONObject("indexBefore")?.opt("ratio") ?: JSONObject.NULL)
                    .put("ratioAfter", e.optJSONObject("indexAfter")?.opt("ratio") ?: JSONObject.NULL)
            }))
            .put("automatic", false)
    }

    private fun save() {
        val target = file ?: return
        val payload = synchronized(lock) {
            lastSaveAt = clock()
            JSONObject().put("format", FORMAT)
                .put("bandScale", JSONArray(bandScale.toList()))
                .put("experimentSequence", experimentSequence)
                .put("experiments", JSONArray(experiments.map { JSONObject(it.toString()) }))
                .put("blocks", JSONArray(blocks.map { JSONObject(it.toString()) }))
        }
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) { target.writeText(payload.toString()); tmp.delete() }
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.optString("format") != FORMAT) return
            root.optJSONArray("bandScale")?.let { a ->
                for (i in 0 until min(a.length(), bandScale.size)) bandScale[i] = a.optDouble(i, 1.0).coerceIn(MIN_SCALE, MAX_SCALE)
            }
            root.optJSONArray("experiments")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(experiments::add) }
            root.optJSONArray("blocks")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(blocks::add) }
            experimentSequence = root.optLong("experimentSequence", experiments.size.toLong())
        } catch (_: Exception) {
            experiments.clear()
            blocks.clear()
        }
    }
}
