package com.omegas.prohub.autocal

import com.omegas.prohub.util.AtomicJsonFile
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
 * Antes: o índice de equivalência por faixa (t_no_GNV / t_gasolina, todas as rotações menos os grupos locais)
 * medido com a curva antiga. Depois: o mesmo índice medido só com o GNV lido depois da
 * gravação. Por faixa o resultado é CONFIRMADA (chegou perto da gasolina), PASSOU
 * (inverteu o sinal além do ruído), CURTA (mesmo sinal, pouca melhora) ou PIOROU
 * (ficou mais longe). Cada ponto alterado herda o veredito das faixas que ele toca.
 *
 * Ciclo (sem estado preso, sem "Entendi"):
 *  - cada gravação é dona das faixas que tocou; outra gravação só interrompe as faixas
 *    que ela mesma tocar (pontos independentes);
 *  - antes e depois só se comparam com a MESMA referência de gasolina (curva da ECU/gasolina própria);
 *    se ela trocou no meio, a faixa continua medindo, sem veredito, sem ensinar e sem oferecer restaurar;
 *  - a verificação nunca é eterna: fecha com tudo julgado, sem base (SEM_BASE, só depois de medir a faixa
 *    com a curva nova ou de juntar [VERIFY_PARTIAL_SAMPLES] leituras), com o que deu para julgar, ou INCONCLUSIVO;
 *    só evidência, nunca relógio (dono, 2026-10-14);
 *  - margem do dono: alvo ±4% ("chegou"), até 5% aceito; "piorou" só mais de 4% pior E erro acima de 5% E mudança
 *    de pelo menos 3% E leituras novas em pelo menos 3 regiões RPM×MAP da faixa (dono, 2026-10-16); senão coleta;
 *  - só gravação de proposta/ajuste ensina o ganho por faixa; restaurar, desfazer, reset da curva,
 *    backup restaurado e o diário de versões antigas não;
 *  - restaurar oferece só os pontos que pioraram, que a ECU ainda tem no valor gravado e cuja faixa está
 *    fora de 5% agora, enquanto a evidência atual disser que piorou; Mapa K ou gasolina readquirida anulam a oferta;
 *    desfazer vale para a última gravação de proposta até a próxima gravação ou a curva mudar por fora;
 *  - depois de restaurar/desfazer, o ponto não recebe proposta na mesma direção até a faixa
 *    ser medida de novo; depois de restaurar, nunca mais o valor que piorou (nem além);
 *  - a ordem vem do conteúdo da curva lida (MUL_ACT), não do relógio da multimídia.
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
        /** Alvo do dono: dentro de ±4% depois da correção é "chegou". */
        val OK_AFTER_LOG = ln(1.04)
        /** "Piorou" só se ficou mais de 4% pior E acima de 5% de erro (margem do dono; Platina 41123729). */
        val WORSE_MARGIN_LOG = ln(1.04)
        val WORSE_MIN_ERROR_LOG = ln(1.05)
        /** Mudança relativa entre o antes e o depois abaixo de 3% não vira ação (dono, 2026-10-16). */
        val WORSE_MIN_DELTA_LOG = ln(1.03)
        /**
         * "Piorou" é o único veredito que muda a curva do dono (oferta de restaurar, ganho da faixa pela metade), então
         * não sai de 8 leituras: pede [WORSE_MIN_SAMPLES] leituras estáveis depois da gravação (sessão 20:41, 2026-10-13:
         * "piorou 6" com 10 a 163 leituras logo após gravar). Só por quantidade de evidência, nunca por relógio (dono,
         * 2026-10-14). Melhorou/chegou não espera. As leituras do acumulador são independentes (3 quadros cada, sem
         * sobreposição): 20 leituras são ~17 s de GNV estável, não ~1 s (sessão 16/10: RESTAURAR_TRECHO 16 s após gravar).
         */
        const val WORSE_MIN_SAMPLES = 20
        /**
         * "Piorou" também pede leituras novas em pelo menos tantas regiões RPM×MAP distintas da faixa (índice `cells`):
         * parado num semáforo o acumulador junta dezenas de leituras do mesmo ponto, e isso não é evidência de faixa.
         * Abaixo disso a faixa fica COLETANDO (SEM_DADOS depois do prazo parcial), nunca "piorou".
         */
        const val WORSE_MIN_CELLS = 3
        /** Antes e depois com fração da curva da ECU diferente além disso: referência trocou. */
        const val REFERENCE_SHARE_TOLERANCE = 0.25
        const val MIN_SCALE = 0.4
        const val MAX_SCALE = 1.3
        const val MAX_EXPERIMENTS = 40
        val BANDS = EquivalenceLedger.BANDS
        /** Um ponto age na faixa até meia unidade de ms além das bordas (K interpolado entre pontos vizinhos). */
        const val BAND_MARGIN_MS = 0.5
        /**
         * Evidência (não relógio): leituras estáveis novas, em todas as faixas, depois da gravação. Com este tanto fecha com o
         * que já deu para julgar: faixa que o motorista não visita não segura o refino para sempre.
         */
        const val VERIFY_PARTIAL_SAMPLES = 100
        /** Com este tanto de leituras novas e nenhuma faixa julgável: INCONCLUSIVO, e o refino segue. */
        const val VERIFY_GIVE_UP_SAMPLES = 500
        /** Leituras novas da MUL_ACT ainda com a curva de antes da gravação toleradas como atrasadas. */
        const val STALE_READS_MAX = 1
        private const val SAVE_EVERY_MS = 60_000L
        const val STATUS_VERIFYING = "VERIFICANDO"

        /** Tipo da gravação, pelo motivo que a tela passa ao KFactorManager (confirmedEvents[].reason). */
        const val KIND_PROPOSTA = "PROPOSTA"
        const val KIND_RESTAURAR = "RESTAURAR"
        const val KIND_DESFAZER = "DESFAZER"
        const val KIND_MANUAL = "MANUAL"
        /** Reset da Curva K (MUL_ACT = 1,0) e backup restaurado: decisão do dono, não ensinam nem pedem restaurar. */
        const val KIND_RESET = "RESET"
        const val KIND_BACKUP = "BACKUP"
        /** Experimento salvo por uma versão antiga (sem tipo): rejulgado pela margem do dono; não ensina nem oferece. */
        const val KIND_LEGADO = "LEGADO"
        const val REASON_PREFIX = "Refino OMEGAS:"

        fun kindFromReason(reason: String?): String {
            val text = reason.orEmpty().trim()
            if (!text.startsWith(REASON_PREFIX)) {
                val lower = text.lowercase()
                return when {
                    lower.startsWith("reset curva k") || lower.startsWith("neutralizar mul_act") -> KIND_RESET
                    lower.startsWith("restaurar backup") -> KIND_BACKUP
                    else -> KIND_MANUAL
                }
            }
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
        const val MAP_K_WRITTEN = "MAPA_K_GRAVADO"
        const val PETROL_REACQUIRED = "GASOLINA_READQUIRIDA"
        private val CURVE_CHANGED_OUTSIDE = setOf(OUTSIDE_AUTOMATCH, OUTSIDE_CURVE)
        /** Mudou o gás ou a gasolina de referência: o "piorou" medido antes não vale mais para restaurar. */
        private val VOIDS_RESTORE = setOf(MAP_K_WRITTEN, PETROL_REACQUIRED, OUTSIDE_AUTOMATCH)

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

        /** Veredito de uma faixa com antes/depois válidos (margem do dono). "Piorou" exige mudança relativa ≥ 3%. */
        fun judge(ratioBefore: Double, ratioAfter: Double): String {
            val e0 = ln(ratioBefore)
            val e1 = ln(ratioAfter)
            return when {
                abs(e1) <= max(NOISE_LOG, OK_AFTER_LOG) || abs(e1) <= abs(e0) * 0.35 -> "CONFIRMADA"
                abs(e1) > abs(e0) + WORSE_MARGIN_LOG && abs(e1) > WORSE_MIN_ERROR_LOG && abs(e1 - e0) >= WORSE_MIN_DELTA_LOG -> "PIOROU"
                e0 * e1 < 0 -> "PASSOU"
                else -> "CURTA"
            }
        }

        /**
         * Antes e depois medidos contra a mesma referência de gasolina? Mesma fração de pares com a curva da ECU,
         * a mesma curva da ECU onde ela entra e a mesma época da gasolina própria onde ela entra.
         */
        fun sameReference(before: JSONObject, after: JSONObject, bandBefore: JSONObject, bandAfter: JSONObject): Boolean {
            val sb = bandBefore.optDouble("ecuShare", 0.0).takeIf { it.isFinite() } ?: 0.0
            val sa = bandAfter.optDouble("ecuShare", 0.0).takeIf { it.isFinite() } ?: 0.0
            if (abs(sb - sa) > REFERENCE_SHARE_TOLERANCE) return false
            if (max(sb, sa) > 0.0 && before.optString("ecuReference") != after.optString("ecuReference")) return false
            if (min(sb, sa) < 1.0 && before.optInt("petrolEpoch", 0) != after.optInt("petrolEpoch", 0)) return false
            return true
        }
    }

    private val lock = Any()
    private val saveLock = Any()
    private val experiments = ArrayList<JSONObject>()
    private val bandScale = DoubleArray(BANDS.size) { 1.0 }
    /** Bloqueios por ponto depois de restaurar/desfazer: {index, direction, badRaw, restoredRaw, at, kind, capped}. */
    private val blocks = ArrayList<JSONObject>()
    private var lastEvaluateAt = 0L
    private var lastSaveAt = Long.MIN_VALUE / 2
    private var dirty = false
    private var experimentSequence = 0L
    /** Último índice avaliado (condução): restaurar só vale enquanto a faixa ainda está fora da margem. */
    private var lastIndexNow: JSONObject? = null
    /** Curva que o app sabe que está na ECU (última gravação ou última leitura aceita). null = ainda não sabe. */
    private var expectedRaw: IntArray? = null
    /** Leituras da MUL_ACT: a última vista, a vista quando a última gravação aconteceu e as atrasadas depois dela. */
    private var lastEcuCapture: Long? = null
    private var preWriteCapture: Long? = null
    private val staleCaptures = HashSet<Long>()
    /** Motivo de o arquivo não ter sido lido (ou lido em parte); vai em [json] para a tela dizer em palavras. */
    private var loadError: String? = null

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
                .put("sequence", experimentSequence)
                .put("appliedAt", now)
                .put("source", source)
                .put("kind", kind)
                .put("axisRaw", JSONArray(axisRaw.toList()))
                .put("beforeRaw", JSONArray(beforeRaw.toList()))
                .put("afterRaw", JSONArray(afterRaw.toList()))
                .put("indexBefore", indexBefore)
                .put("owned", JSONArray(touched.toList()))
                .put("status", STATUS_VERIFYING)
            while (experiments.size > MAX_EXPERIMENTS) experiments.removeAt(0)
            // A ECU agora tem a curva gravada; a leitura em mãos é a de antes (atrasada), não "mudou por fora".
            expectedRaw = afterRaw.copyOf()
            preWriteCapture = lastEcuCapture
            staleCaptures.clear()
            markChanged()
        }
        save()
    }

    /**
     * Algo mudou o motor por fora (Mapa K, AutoMatch nativo, gasolina readquirida): verificações em curso perdem
     * validade. AutoMatch nativo troca a curva inteira (desfazer/restaurar e bloqueios perdem a referência);
     * Mapa K e gasolina readquirida mudam o gás/a referência (a oferta de restaurar perde a base).
     */
    fun interrupt(reason: String) {
        synchronized(lock) {
            experiments.filter { it.optString("status") == STATUS_VERIFYING }.forEach {
                it.put("status", "INTERROMPIDO").put("interruptReason", reason).put("closedAt", clock())
            }
            if (reason in CURVE_CHANGED_OUTSIDE) {
                experiments.filter { !it.has("curveChangedOutside") }.forEach { it.put("curveChangedOutside", reason) }
                blocks.clear()
                expectedRaw = null // a próxima leitura da ECU vira a base
            }
            if (reason in VOIDS_RESTORE) experiments.filter { !it.has("restoreVoid") }.forEach { it.put("restoreVoid", reason) }
            markChanged()
        }
        save()
    }

    /**
     * Curva lida da ECU (MUL_ACT). Igual à que o app sabe: nada. Igual à de antes da última gravação: leitura
     * atrasada (até [STALE_READS_MAX] leituras novas). Diferente: mudou por fora do app (ProgBase, gravação que
     * falhou no meio, reset): verificações em curso param, bloqueios dos pontos que mudaram somem e a curva lida
     * passa a valer; desfazer/restaurar seguem só nos pontos que a ECU ainda tem no valor gravado.
     * Retorna true uma vez por mudança. A ordem vem do conteúdo e da identidade da leitura, nunca do relógio.
     */
    fun noteEcuCurve(currentRaw: IntArray, mulActCapturedAtMs: Long): Boolean {
        if (currentRaw.size != 30) return false
        val outside = synchronized(lock) {
            val newCapture = mulActCapturedAtMs != lastEcuCapture
            lastEcuCapture = mulActCapturedAtMs
            val expected = expectedRaw
            if (expected == null) {
                expectedRaw = currentRaw.copyOf()
                markChanged()
                return@synchronized false
            }
            val latest = experiments.lastOrNull()
            if (currentRaw.contentEquals(expected)) {
                if (latest != null && !latest.optBoolean("ecuConfirmed")) { latest.put("ecuConfirmed", true); markChanged() }
                return@synchronized false
            }
            val before = latest?.optJSONArray("beforeRaw")?.let { a -> IntArray(a.length()) { a.optInt(it) } }
            if (latest != null && !latest.optBoolean("ecuConfirmed") && before != null && currentRaw.contentEquals(before)) {
                if (mulActCapturedAtMs == preWriteCapture) return@synchronized false
                if (newCapture) staleCaptures += mulActCapturedAtMs
                if (staleCaptures.size <= STALE_READS_MAX) return@synchronized false
            }
            val diff = expected.indices.filter { expected[it] != currentRaw[it] }.toSet()
            experiments.filter { it.optString("status") == STATUS_VERIFYING }.forEach {
                it.put("status", "INTERROMPIDO").put("interruptReason", OUTSIDE_CURVE).put("closedAt", clock())
            }
            blocks.removeAll { it.optInt("index") in diff }
            expectedRaw = currentRaw.copyOf()
            markChanged()
            true
        }
        if (outside) save()
        return outside
    }

    /** Id da última gravação registrada ("" sem nenhuma). */
    fun latestId(): String = synchronized(lock) { experiments.lastOrNull()?.optString("id") ?: "" }

    /** Curva que o app sabe que está na ECU (null = ainda não sabe). */
    fun expectedCurve(): IntArray? = synchronized(lock) { expectedRaw?.copyOf() }

    /** Eixo da última gravação registrada (para registrar uma gravação parcial sem leitura nova). */
    fun latestAxis(): IntArray? = synchronized(lock) {
        experiments.lastOrNull()?.optJSONArray("axisRaw")?.takeIf { it.length() == 30 }?.let { a -> IntArray(30) { a.optInt(it) } }
    }

    // ---------------------------------------------------------------- verificação

    /**
     * Avalia cada gravação aberta nas faixas que são dela. [indexNow] = índice atual; [indexSince] dá o índice
     * só com o GNV medido a partir de um instante (o "depois" de cada gravação). [driving] = ECU online e motor
     * funcionando no GNV (lenta incluída): só então o prazo anda. Retorna true se algum veredito mudou ou fechou.
     */
    fun evaluate(indexNow: JSONObject, driving: Boolean = true, indexSince: ((Long) -> JSONObject)? = null): Boolean {
        var visible = false
        var structural = false
        synchronized(lock) {
            val now = clock()
            lastEvaluateAt = now
            lastIndexNow = indexNow
            for (exp in experiments.filter { it.optString("status") == STATUS_VERIFYING }) {
                val afterIndex = indexSince?.invoke(exp.optLong("appliedAt")) ?: indexNow
                val newReads = (0 until (afterIndex.optJSONArray("bands")?.length() ?: 0)).sumOf { afterIndex.optJSONArray("bands")!!.optJSONObject(it)?.optInt("samples", 0) ?: 0 }
                if (newReads != exp.optInt("newReads", -1)) { exp.put("newReads", newReads); dirty = true }
                val timeboxed = newReads >= VERIFY_PARTIAL_SAMPLES
                val owned = ownedBands(exp)
                val frozen = frozenBands(exp)
                val beforeIndex = exp.optJSONObject("indexBefore") ?: JSONObject()
                val before = beforeIndex.optJSONArray("bands") ?: JSONArray()
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
                    val afterKnown = na >= MIN_BAND_SAMPLES && ra.isFinite() && ra > 0.0
                    when {
                        i in frozen && kept != null -> verdict.put("verdict", kept.optString("verdict"))
                            .put("ratioAfter", kept.opt("ratioAfter") ?: JSONObject.NULL).put("samplesAfter", kept.optInt("samplesAfter"))
                        i !in owned -> verdict.put("verdict", "NAO_ALTERADA")
                        // Sem "antes": não julga, mas segura a faixa até medir a curva nova (ou o prazo).
                        nb < MIN_BAND_SAMPLES || !rb.isFinite() || rb <= 0.0 ->
                            verdict.put("verdict", if (afterKnown || timeboxed) "SEM_ANTES" else "COLETANDO")
                        !afterKnown -> verdict.put("verdict", if (timeboxed) "SEM_DADOS" else "COLETANDO")
                        !sameReference(beforeIndex, afterIndex, b, a) ->
                            verdict.put("verdict", if (timeboxed) "SEM_DADOS" else "COLETANDO").put("referenceChanged", true)
                        else -> {
                            val judged = judge(rb, ra)
                            // Índice sem contagem de regiões (versão antiga, sintético): cada leitura conta como uma região.
                            val cells = if (a.has("cells")) a.optInt("cells", 0) else na
                            val thin = judged == "PIOROU" && (cells < WORSE_MIN_CELLS || (na < WORSE_MIN_SAMPLES && !timeboxed))
                            verdict.put("cellsAfter", cells)
                            if (thin) verdict.put("thinWorse", if (cells < WORSE_MIN_CELLS) "POUCAS_REGIOES" else "POUCAS_LEITURAS")
                            verdict.put("verdict", if (!thin) judged else if (timeboxed) "SEM_DADOS" else "COLETANDO")
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
                if ((pending == 0 && (judged > 0 || noBase)) || newReads >= VERIFY_GIVE_UP_SAMPLES) {
                    close(exp, now)
                    structural = true
                }
            }
            if (visible || structural) markChanged()
        }
        val now = clock()
        if (structural || visible) save() else if (synchronized(lock) { dirty } && now - lastSaveAt >= SAVE_EVERY_MS) save()
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
        markChanged()
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

    /** A ECU ainda tem o valor que esta gravação pôs no ponto (pela curva que o app sabe que está lá)? */
    private fun ecuStillHas(e: JSONObject, i: Int): Boolean {
        val expected = expectedRaw ?: return true
        val after = e.optJSONArray("afterRaw") ?: return false
        return i < expected.size && expected[i] == after.optInt(i)
    }

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
     * restauração, um desfazer, um reset ou um registro antigo (sem laço). Uma gravação nova em outros pontos
     * não apaga a oferta; Mapa K, gasolina readquirida, AutoMatch nativo ou o prazo apagam.
     */
    private fun restoreSource(): JSONObject? {
        for (k in experiments.indices.reversed()) {
            val e = experiments[k]
            if (e.optString("kind", KIND_MANUAL) !in TEACHES) continue
            if (e.optString("status") != "PIOROU_EM_PARTE" || e.optBoolean("undone") || e.has("curveChangedOutside") || e.has("restoreVoid")) continue
            if (offerable(e, k).length() > 0) return e
        }
        return null
    }

    /**
     * Pontos que pioraram, que a ECU ainda tem no valor gravado (nenhuma gravação depois mexeu neles, nada mudou
     * por fora), sem restaurar, e cuja faixa está fora da margem do dono agora, com evidência atual (≥ 8
     * leituras): sem dado não há oferta; se a medição nova voltou para dentro, não há o que restaurar.
     */
    private fun offerable(src: JSONObject, position: Int): JSONArray {
        val restored = src.optJSONArray("restored")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() } ?: emptySet()
        val later = experiments.drop(position + 1).flatMap { changedIndexes(it) }.toSet()
        val now = lastIndexNow?.optJSONArray("bands")
        fun stillWorse(ms: Double): Boolean = touchedBands(ms).any { b ->
            if (verdictOf(src, b) != "PIOROU") return@any false
            val band = now?.optJSONObject(b) ?: return@any false
            val ratio = band.optDouble("ratio", Double.NaN)
            band.optInt("samples", 0) >= MIN_BAND_SAMPLES && ratio.isFinite() && ratio > 0.0 && abs(ln(ratio)) > WORSE_MIN_ERROR_LOG
        }
        val out = JSONArray()
        val points = pointVerdicts(src)
        for (k in 0 until points.length()) {
            val p = points.getJSONObject(k)
            val i = p.optInt("index")
            if (p.optString("verdict") != "PIOROU" || i in restored || i in later || !ecuStillHas(src, i) || !stillWorse(p.optDouble("ms"))) continue
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
     * Desfazer: só da última gravação, se veio de proposta, até a próxima gravação, enquanto a ECU ainda tem
     * todos os pontos gravados (a curva não mudou por fora). Nunca de um desfazer/restauração/reset.
     */
    private fun undoPointsOf(exp: JSONObject?): JSONArray {
        if (exp == null || exp.optString("kind") != KIND_PROPOSTA || exp.optBoolean("undone") || exp.has("curveChangedOutside")) return JSONArray()
        val before = exp.optJSONArray("beforeRaw") ?: return JSONArray()
        val after = exp.optJSONArray("afterRaw") ?: return JSONArray()
        val changed = changedIndexes(exp)
        if (changed.any { !ecuStillHas(exp, it) }) return JSONArray()
        return JSONArray(changed.map {
            JSONObject().put("index", it).put("currentRaw", after.optInt(it)).put("targetRaw", before.optInt(it)).put("referenceTimeMs", msOf(exp, it))
        })
    }

    /** Faixas que alguma gravação aberta ainda está medindo (passado o prazo, SEM_DADOS não segura nada). */
    fun measuringBands(): Set<Int> = synchronized(lock) {
        experiments.filter { it.optString("status") == STATUS_VERIFYING }
            .flatMap { e -> pendingBands(e) }
            .toSet()
    }

    private fun pendingBands(e: JSONObject): List<Int> =
        ownedBands(e).filter { verdictOf(e, it) == "COLETANDO" || verdictOf(e, it).isEmpty() }

    // ---------------------------------------------------------------- proposta

    /**
     * Retenções do ciclo sobre a análise refinada. A regra de entrada de cada ponto é a de antes
     * (origem ≠ HELD e calculatedRaw ≠ currentRaw) e o valor proposto é o do motor, sem mudança.
     * Fica fora: ponto cujo trecho de influência cruza o trecho alterado de uma gravação ainda em medição
     * (numa faixa que ela mede); ponto restaurado/desfeito na mesma direção sem evidência nova; ponto
     * restaurado que voltaria ao valor que piorou (ou além). Análise sobre uma curva diferente da que o app
     * sabe que está na ECU (leitura atrasada) não gera proposta. A proposta leva o experimento em que se baseou.
     */
    fun proposalFrom(analysis: JSONObject, indexNow: JSONObject, indexSince: ((Long) -> JSONObject)? = null): JSONObject {
        val (basedOn, expected) = synchronized(lock) { (experiments.lastOrNull()?.optString("id") ?: "") to expectedRaw?.copyOf() }
        val out = JSONObject().put("known", false).put("count", 0).put("points", JSONArray()).put("held", JSONArray())
            .put("basedOn", basedOn).put("mode", analysis.optString("refinementMode"))
        if (!analysis.optBoolean("available", false)) return out.put("reason", "ANALISE_INDISPONIVEL")
        val points = analysis.optJSONArray("points") ?: JSONArray()
        val curve = IntArray(30) { -1 }
        for (k in 0 until points.length()) points.optJSONObject(k)?.let { p -> p.optInt("index", -1).takeIf { it in 0 until 30 }?.let { curve[it] = p.optInt("currentRaw") } }
        if (expected != null && curve.none { it < 0 } && !curve.contentEquals(expected)) return out.put("reason", "LENDO_CURVA_NOVA")
        val axisMs = DoubleArray(30) { Double.NaN }
        for (k in 0 until points.length()) points.optJSONObject(k)?.let { p -> p.optInt("index", -1).takeIf { it in 0 until 30 }?.let { axisMs[it] = p.optDouble("referenceTimeMs") } }
        // Trechos alterados por gravações ainda em medição, com as faixas que cada uma ainda mede.
        val open = synchronized(lock) {
            experiments.filter { it.optString("status") == STATUS_VERIFYING }.mapNotNull { e ->
                val pending = pendingBands(e).toSet()
                val axis = e.optJSONArray("axisRaw") ?: return@mapNotNull null
                val b = e.optJSONArray("beforeRaw") ?: return@mapNotNull null
                val a = e.optJSONArray("afterRaw") ?: return@mapNotNull null
                if (pending.isEmpty()) null
                else affectedRangesMs(IntArray(axis.length()) { axis.optInt(it) }, IntArray(b.length()) { b.optInt(it) }, IntArray(a.length()) { a.optInt(it) }) to pending
            }
        }
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
        fun measuring(j: Int, ms: Double): Boolean {
            if (open.isEmpty()) return false
            val lo = if (j > 0 && axisMs[j - 1].isFinite()) axisMs[j - 1] else 0.0
            val hi = if (j < 29 && axisMs[j + 1].isFinite()) axisMs[j + 1] else Double.POSITIVE_INFINITY
            val bands = touchedBands(ms)
            return open.any { (ranges, pending) -> bands.any { it in pending } && ranges.any { (rlo, rhi) -> lo < rhi && rlo < hi } }
        }
        val kept = JSONArray()
        val held = JSONArray()
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
                measuring(j, ms) -> "MEDINDO"
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
            .put("verifyBudgetSamples", VERIFY_PARTIAL_SAMPLES)
            .put("giveUpBudgetSamples", VERIFY_GIVE_UP_SAMPLES)
            .put("latest", last?.let { e ->
                JSONObject(e.toString()).apply { remove("axisRaw"); remove("verdictNames"); remove("indexAfter") }
                    .put("points", pointVerdicts(e))
                    .put("undoPoints", undoPointsOf(e))
                    .put("undoable", undoPointsOf(e).length() > 0)
            } ?: JSONObject.NULL)
            // Resultado que a tela mostra: o da oferta de restaurar, senão a última gravação.
            .put("result", result?.let { e ->
                JSONObject().put("id", e.optString("id")).put("kind", e.optString("kind", KIND_MANUAL))
                    .put("status", e.optString("status")).put("appliedAt", e.optLong("appliedAt"))
                    .put("closedAt", e.optLong("closedAt", 0L)).put("newReads", e.optInt("newReads", 0))
                    .put("points", pointVerdicts(e))
                    .put("bands", e.optJSONArray("bands") ?: JSONArray())
            } ?: JSONObject.NULL)
            // Progresso da medição em curso (para a frase "faltam N faixas · X de 15 min rodando").
            .put("verifying", experiments.lastOrNull { it.optString("status") == STATUS_VERIFYING }?.let { open ->
                JSONObject().put("id", open.optString("id")).put("bandsLeft", measuringBands().size)
                    .put("newReads", open.optInt("newReads", 0)).put("budgetReads", VERIFY_PARTIAL_SAMPLES)
                    .put("giveUpReads", VERIFY_GIVE_UP_SAMPLES)
            } ?: JSONObject.NULL)
            .put("restoreFrom", source?.optString("id") ?: JSONObject.NULL)
            .put("measuringBands", JSONArray(measuringBands().toList()))
            .put("blocks", JSONArray(blocks.map { JSONObject(it.toString()) }))
            .put("count", experiments.size)
            .put("loadError", loadError ?: JSONObject.NULL)
            .put("history", JSONArray(experiments.takeLast(10).map { e ->
                JSONObject().put("id", e.optString("id")).put("appliedAt", e.optLong("appliedAt"))
                    .put("status", e.optString("status")).put("source", e.optString("source"))
                    .put("kind", e.optString("kind", KIND_MANUAL))
                    .put("ratioBefore", e.optJSONObject("indexBefore")?.opt("ratio") ?: JSONObject.NULL)
                    .put("ratioAfter", e.optJSONObject("indexAfter")?.opt("ratio") ?: JSONObject.NULL)
            }))
            .put("automatic", false)
    }

    private fun markChanged() {
        dirty = true
        lastSaveAt = Long.MIN_VALUE / 2
    }

    /** Grava só quando algo mudou; o "depois" medido (indexAfter) não vai para o arquivo (é recalculado). */
    private fun save() {
        val target = file ?: return
        synchronized(saveLock) {
            val payload = synchronized(lock) {
                if (!dirty) return
                dirty = false
                lastSaveAt = clock()
                JSONObject().put("format", FORMAT)
                    .put("bandScale", JSONArray(bandScale.toList()))
                    .put("experimentSequence", experimentSequence)
                    .put("experiments", JSONArray(experiments.map { JSONObject(it.toString()).apply { remove("indexAfter"); remove("verdictNames") } }))
                    .put("blocks", JSONArray(blocks.map { JSONObject(it.toString()) }))
                    .put("expectedRaw", expectedRaw?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            }
            try {
                AtomicJsonFile.write(target, payload.toString())
            } catch (_: Exception) {
                synchronized(lock) { dirty = true }
            }
        }
    }

    /**
     * Arquivo (ou a reserva `.bak`) → memória. Formato desconhecido ou JSON inválido não zera nada nem apaga arquivo:
     * fica o motivo em [loadError]. Antes o `catch` zerava tudo em silêncio e o salvamento seguinte apagava o que sobrava.
     */
    private fun load() {
        val target = file ?: return
        val root = AtomicJsonFile.readJson(target) ?: run {
            if (target.isFile || AtomicJsonFile.backupOf(target).isFile) loadError = "Diário do refino ilegível (nem a reserva .bak serviu)"
            return
        }
        if (root.optString("format") != FORMAT) {
            loadError = "Formato desconhecido do diário do refino: ${root.optString("format")}"
            return
        }
        try {
            root.optJSONArray("bandScale")?.let { a ->
                for (i in 0 until min(a.length(), bandScale.size)) bandScale[i] = a.optDouble(i, 1.0).coerceIn(MIN_SCALE, MAX_SCALE)
            }
            root.optJSONArray("experiments")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(experiments::add) }
            root.optJSONArray("blocks")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(blocks::add) }
            experimentSequence = root.optLong("experimentSequence", experiments.size.toLong())
            experiments.filter { !it.has("kind") }.forEach(::migrateLegacy)
            expectedRaw = root.optJSONArray("expectedRaw")?.takeIf { it.length() == 30 }?.let { a -> IntArray(30) { a.optInt(it) } }
                ?: experiments.lastOrNull()?.takeIf { !it.has("curveChangedOutside") }?.optJSONArray("afterRaw")
                    ?.takeIf { it.length() == 30 }?.let { a -> IntArray(30) { a.optInt(it) } }
        } catch (error: Exception) {
            // Fica o que já foi lido; só o motivo é registrado.
            loadError = "Diário do refino lido em parte: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    /**
     * Experimento de uma versão antiga (sem "kind"): o "piorou" dele foi julgado pela regra velha (+2%).
     * Rejulga as faixas julgadas pela margem do dono a partir das razões salvas e marca como LEGADO (não ensina
     * e nunca oferece restaurar). Onde o "piorou" caiu, o ganho aprendido com ele volta a 1,0.
     */
    private fun migrateLegacy(e: JSONObject) {
        e.put("kind", KIND_LEGADO).put("migratedFrom", "SEM_TIPO")
        val bands = e.optJSONArray("bands") ?: return
        var anyWorse = false
        for (i in 0 until bands.length()) {
            val band = bands.optJSONObject(i) ?: continue
            val old = band.optString("verdict")
            if (old !in JUDGED) continue
            val rb = band.optDouble("ratioBefore", Double.NaN)
            val ra = band.optDouble("ratioAfter", Double.NaN)
            if (!rb.isFinite() || !ra.isFinite() || rb <= 0.0 || ra <= 0.0) continue
            val now = judge(rb, ra)
            if (now != old) band.put("verdict", now).put("legacyVerdict", old)
            if (old == "PIOROU" && now != "PIOROU" && i < bandScale.size) bandScale[i] = 1.0
            if (now == "PIOROU") anyWorse = true
        }
        if (e.optString("status") in setOf("PIOROU_EM_PARTE", "VERIFICADO")) e.put("status", if (anyWorse) "PIOROU_EM_PARTE" else "VERIFICADO")
        dirty = true
    }
}
