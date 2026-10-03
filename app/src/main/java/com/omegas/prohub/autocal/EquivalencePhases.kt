package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.EquivalenceTolerances
import com.omegas.prohub.equivalence.PointState
import com.omegas.prohub.equivalence.ProofOutcome
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln

/**
 * Fases da equivalência: decide em que fase a calibração está, sem ninguém olhar a tela.
 *
 * 0. SEM_ECU / LENDO_ECU — sem cabo, ou conectou e a ECU ainda não entregou o AutoMatch e a curva.
 *    O estado vem sempre da ECU (ela guarda o AutoMatch e as curvas); o app recém-instalado lê e segue.
 *    A gasolina de referência vem das leituras do app OU da curva de gasolina que a ECU já tem.
 * 1. ECU_TRABALHANDO — o AutoCal nativo ainda está no automático 1, 2, 3… e pedindo
 *    aquisição. O OMEGAS só observa e junta pontos próprios (RPM×MAP) em paralelo:
 *    gravar agora seria sobrescrito pelo próximo automático da ECU.
 * 2. A ECU confirmou contador no MAX_AUTOMATCH ou AutoCal desligado: é a nossa vez.
 *    Aquisição completa/silêncio não confirmam finalização; timeout encerra só a tentativa do host.
 *    - COLETANDO_NOSSOS: faltam leituras GNV/gasolina no mesmo RPM×MAP.
 *    - PROPOSTA_PRONTA: alguma faixa de condução está fora de ±[TOLERANCE_LOG]; o
 *      refino (pontos da ECU + nossos pontos) tem o que corrigir. Gravação manual.
 *    - VERIFICANDO: curva nova gravada; medindo se chegou na gasolina.
 *    - RESTAURAR_TRECHO: uma faixa piorou; restaurar só aquele trecho.
 *    - ESTAVEL: todas as faixas medidas dentro de ±3%: pode desconectar.
 *
 * Prova por ponto (F4): cada ponto da Curva K ajustado entra em EM_PROVA e fecha em CONFIRMADO, CONTESTADO ou
 * INCONCLUSIVO conforme as leituras novas dele; o veredito fica até nova prova do ponto. Arquivo antigo sem
 * `proofs` abre sem provas.
 *
 * Nunca grava na ECU. Só observa, decide a fase e avisa (notificação/UI).
 */
class EquivalencePhases(
    private val file: File? = null,
    private val durationClock: (() -> Long)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val FORMAT = "omegas-refinement-autopilot-v1"
        /** Faixa cuja gasolina veio majoritariamente da curva da ECU é mais grossa: tolerância ±6%. */
        val TOLERANCE_LOG_ECU_REF = ln(1.06)
        /** Legado de diagnóstico; silêncio não é prova de conclusão nativa. */
        const val QUIET_MS = 10 * 60_000L
        /** Legado de diagnóstico; prazos operacionais vêm de PHASE_BUDGET_MS. */
        const val QUIET_PARTIAL_MS = 25 * 60_000L
        const val PARTIAL_MIN_ZONES = 3
        /** ±3%: abaixo disso GNV e gasolina já pedem o mesmo (ruído de medição ~2%). */
        val TOLERANCE_LOG = ln(1.03)
        const val MIN_BAND_SAMPLES = RefinementJournal.MIN_BAND_SAMPLES
        const val MIN_STABLE_BANDS = 3
        private const val MAX_TICK_MS = 10_000L
        /** Leituras novas no ponto para julgar a prova (= faixa do diário). */
        const val PROOF_MIN_SAMPLES = RefinementJournal.MIN_BAND_SAMPLES
        /** Condução online sem leitura suficiente: a prova fecha INCONCLUSIVO. */
        const val PROOF_TIMEBOX_ONLINE_MS = RefinementJournal.VERIFY_PARTIAL_ONLINE_MS
        /** Tetos da tentativa do host; nunca representam conclusão do AutoMatch na ECU. */
        val PHASE_BUDGET_MS = mapOf(
            "LENDO_ECU" to 30_000L,
            "ECU_TRABALHANDO" to 40 * 60_000L,
            "COLETANDO_NOSSOS" to 40 * 60_000L,
            "PROPOSTA_PRONTA" to 30 * 60_000L,
            "VERIFICANDO" to 40 * 60_000L,
            "RESTAURAR_TRECHO" to 30 * 60_000L,
        )
        /** Fases que merecem avisar o motorista uma vez. */
        val ALERT_PHASES = setOf("PROPOSTA_PRONTA", "RESTAURAR_TRECHO", "ESTAVEL")
    }

    private val lock = Any()
    private var lastCount: Int? = null
    private var quietMs = 0L
    private var lastTickAt = 0L
    private var lastDurationAt: Long? = null
    private var watchedPhase = ""
    private var phaseElapsedMs = 0L
    private var expiredEvidence: String? = null
    private var timeoutReason = ""
    private var phase = "SEM_ECU"
    private var alertedPhase = ""
    /** Conclusão nativa da observação atual; ausente/ambígua não autoriza o refino. */
    private var ecuDoneLatch: String? = null
    private var dirty = false
    private var lastSaveAt = Long.MIN_VALUE / 2
    private var last: JSONObject = JSONObject().put("phase", phase)

    /** Prova de um ponto da Curva K; [verdict] nulo = aberta (EM_PROVA). */
    private class Proof(
        val index: Int,
        var verdict: PointState?,
        var onlineMs: Long,
        val mixtureBefore: Double?,
        val roughBefore: Double?,
        val nearBefore: Double?,
        var mixtureNow: Double?,
    )

    private val proofs = LinkedHashMap<Int, Proof>()
    private var lastJudgeAt: Long? = null

    init { load() }

    /**
     * Uma observação (tick do serviço). [monitor] = status do AutoCal nativo
     * (autoMatchCount, maxAutomatch, autoCalEnabled); [acquisition] = AutoCalAcquisition;
     * [index] = EquivalenceLedger.index(); [journal] = RefinementJournal.json();
     * [restoreCount] = pontos que o diário oferece restaurar.
     * Retorna a fase decidida (JSON) — também guardada para a UI.
     */
    fun observe(ecuOnline: Boolean, monitor: JSONObject?, acquisition: JSONObject?, index: JSONObject, journal: JSONObject, restoreCount: Int): JSONObject {
        val result = synchronized(lock) {
            val now = clock()
            val durationNow = durationClock?.invoke() ?: now
            val elapsed = lastDurationAt?.let { (durationNow - it).coerceAtLeast(0L) } ?: 0L
            lastDurationAt = durationNow
            val dt = elapsed.coerceAtMost(MAX_TICK_MS)
            lastTickAt = now
            // Offline invalida autorização de fase/contador; leituras antigas só são histórico.
            if (!ecuOnline && (ecuDoneLatch != null || lastCount != null || quietMs != 0L)) {
                ecuDoneLatch = null; lastCount = null; quietMs = 0L; dirty = true
            }
            val liveMonitor = monitor.takeIf { ecuOnline }
            val liveAcquisition = acquisition.takeIf { ecuOnline }
            val enabled = liveMonitor?.optInt("autoCalEnabled", -1)?.takeIf { liveMonitor.has("autoCalEnabled") && !liveMonitor.isNull("autoCalEnabled") }
            val count = liveMonitor?.optInt("autoMatchCount", -1)?.takeIf { it >= 0 }
            val max = liveMonitor?.optInt("maxAutomatch", -1)?.takeIf { it > 0 && !liveMonitor.isNull("maxAutomatch") }
                ?: liveAcquisition?.optJSONObject("thresholds")?.takeIf { !it.isNull("maxAutomatch") }?.optInt("maxAutomatch", -1)?.takeIf { it > 0 }
            if (ecuOnline && count != null) {
                if (lastCount != null && count != lastCount) { quietMs = 0L; ecuDoneLatch = null; dirty = true }
                else { quietMs += dt; if (dt > 0) dirty = true }
                if (lastCount != count) dirty = true
                lastCount = count
            }
            // Pontos da ECU com atividade (os que o gráfico desenha) e zonas que a ECU deu como adquiridas.
            val petrolKnown = activeCount(liveAcquisition, "GASOLINA")
            val gasKnown = activeCount(liveAcquisition, "GNV")
            val petrolValid = petrolKnown ?: 0
            val gasValid = gasKnown ?: 0
            val petrolZones = acquiredZones(liveAcquisition, "GASOLINA")
            val gasZones = acquiredZones(liveAcquisition, "GNV")
            // A ECU já entregou o estado dela (contador ou vetores de aquisição)?
            val ecuRead = count != null || liveAcquisition != null
            val fresh = if (!ecuOnline) null else when {
                enabled == 0 -> "AUTOCAL_DESLIGADO"
                max != null && count != null && count >= max -> "MAX_AUTOMATCH"
                else -> null
            }
            if (fresh != ecuDoneLatch) { ecuDoneLatch = fresh; dirty = true }
            val ecuReason = ecuDoneLatch
            val out = JSONObject()
                .put("ecuOnline", ecuOnline)
                .put("autoMatchCount", count ?: JSONObject.NULL)
                .put("maxAutomatch", max ?: JSONObject.NULL)
                .put("autoCalEnabled", enabled ?: JSONObject.NULL)
                // Sem leitura da ECU o número é desconhecido (null → "—"), nunca 0.
                .put("petrolValid", petrolKnown ?: JSONObject.NULL)
                .put("gasValid", gasKnown ?: JSONObject.NULL)
                .put("petrolZones", petrolZones)
                .put("gasZones", gasZones)
                .put("quietMinutes", quietMs / 60_000.0)
                .put("ecuDone", ecuReason != null)
                .put("ecuDoneReason", ecuReason ?: JSONObject.NULL)
                .put("ecuRead", ecuRead)
                .put("petrolReference", index.optString("petrolReference", "NENHUMA"))
                .put("ecuPetrolPoints", index.optInt("ecuPetrolPoints", 0))
                .put("ourPoints", index.optInt("samples", 0))
                .put("petrolObservations", index.optInt("petrolObservations", 0))
                .put("gasObservations", index.optInt("gasObservations", 0))
                .put("automatic", false)

            val bands = index.optJSONArray("bands") ?: JSONArray()
            val measured = ArrayList<JSONObject>()
            val off = JSONArray()
            val missing = JSONArray()
            for (i in 0 until bands.length()) {
                val b = bands.optJSONObject(i) ?: continue
                val r = b.optDouble("ratio", Double.NaN)
                val label = JSONObject().put("fromMs", b.optDouble("fromMs")).put("toMs", b.optDouble("toMs"))
                    .put("samples", b.optInt("samples")).put("ratio", if (r.isFinite()) r else JSONObject.NULL)
                if (b.optInt("samples") >= MIN_BAND_SAMPLES && r.isFinite() && r > 0) {
                    measured += label
                    val tolerance = if (b.optDouble("ecuShare", 0.0) >= 0.5) TOLERANCE_LOG_ECU_REF else TOLERANCE_LOG
                    if (abs(ln(r)) > tolerance) off.put(label)
                } else missing.put(label)
            }
            out.put("bandsMeasured", measured.size).put("bandsOff", off).put("bandsMissing", missing)

            val latest = journal.optJSONObject("latest")
            val latestStatus = latest?.optString("status").orEmpty()
            val verification = if (latestStatus == "VERIFICANDO") verificationProgress(latest!!, journal) else null
            if (verification != null) out.put("verification", verification)
            val candidate = when {
                !ecuOnline -> "SEM_ECU"
                // Conectou mas a ECU ainda não entregou nada: não afirma "no automático" nem "terminou".
                ecuOnline && !ecuRead -> "LENDO_ECU"
                ecuReason == null -> "ECU_TRABALHANDO"
                latestStatus == "VERIFICANDO" -> "VERIFICANDO"
                latestStatus == "PIOROU_EM_PARTE" && restoreCount > 0 && worseStillOff(latest, off) -> "RESTAURAR_TRECHO"
                measured.size >= MIN_STABLE_BANDS && off.length() == 0 -> "ESTAVEL"
                off.length() > 0 && measured.size >= 2 -> "PROPOSTA_PRONTA"
                else -> "COLETANDO_NOSSOS"
            }
            // O prazo pertence à tentativa, não ao tick, ao calendário ou ao serviço USB.
            // Evidência nova depois de um timeout inicia outra tentativa automaticamente.
            val evidence = listOf(count, max, enabled, measured.size, off.length(),
                index.optLong("revision", -1L), latest?.optString("id"), latestStatus).joinToString("|")
            if (candidate != watchedPhase || (expiredEvidence != null && evidence != expiredEvidence)) {
                watchedPhase = candidate
                phaseElapsedMs = 0L
                expiredEvidence = null
                timeoutReason = ""
                dirty = true
            } else if (candidate in PHASE_BUDGET_MS && expiredEvidence == null) {
                phaseElapsedMs = (phaseElapsedMs + elapsed.coerceAtMost(PHASE_BUDGET_MS.getValue(candidate)))
                    .coerceAtMost(PHASE_BUDGET_MS.getValue(candidate))
                if (elapsed > 0) dirty = true
            }
            val budget = PHASE_BUDGET_MS[candidate]
            if (budget != null && phaseElapsedMs >= budget && expiredEvidence == null) {
                expiredEvidence = evidence
                timeoutReason = when (candidate) {
                    "LENDO_ECU" -> "ECU_READ_TIMEOUT"
                    "ECU_TRABALHANDO" -> "ECU_PROGRESS_TIMEOUT"
                    else -> "REFINEMENT_PHASE_TIMEOUT"
                }
                lastSaveAt = Long.MIN_VALUE / 2
            }
            val next = if (expiredEvidence != null) "TENTATIVA_ENCERRADA" else candidate
            val reason = if (expiredEvidence != null) timeoutReason else when (candidate) {
                "SEM_ECU" -> "ECU_OFFLINE"
                "LENDO_ECU" -> "ECU_STATE_PENDING"
                "ECU_TRABALHANDO" -> "ECU_AUTOMATCH_PENDING"
                "VERIFICANDO" -> "WRITE_VERIFICATION_PENDING"
                "RESTAURAR_TRECHO" -> "LAST_EXPERIMENT_WORSE"
                "ESTAVEL" -> "MEASURED_BANDS_WITHIN_TOLERANCE"
                "PROPOSTA_PRONTA" -> "MEASURED_BANDS_OFF"
                else -> "MEASUREMENT_COVERAGE_PENDING"
            }
            val domain = when {
                reason == "ECU_READ_TIMEOUT" || reason == "ECU_OFFLINE" -> "TRANSPORT"
                expiredEvidence != null -> "FUNCTIONAL"
                else -> "NONE"
            }
            if (next != phase) { phase = next; dirty = true; lastSaveAt = Long.MIN_VALUE / 2 }
            out.put("reasonCode", reason).put("failureDomain", domain)
                .put("watchdogExpired", expiredEvidence != null)
                .put("diagnostic", JSONObject()
                    .put("observedPhase", candidate).put("elapsedMs", phaseElapsedMs)
                    .put("budgetMs", budget ?: JSONObject.NULL)
                    .put("bandsMeasured", measured.size).put("bandsOff", off.length())
                    .put("samples", index.optInt("samples"))
                    .put("autoMatchCount", count ?: JSONObject.NULL)
                    .put("maxAutomatch", max ?: JSONObject.NULL)
                    .put("journalStatus", latestStatus))
            out.put("phase", phase)
                .put("canDisconnect", phase == "ESTAVEL")
                .put("headline", headline(phase, count, max, measured.size, off.length(), out, verification))
                .put("next", nextStep(phase, petrolValid, gasValid, missing, index, verification))
            if (latest != null) out.put("journalStatus", latestStatus)
            last = out
            out
        }
        save()
        return JSONObject(result.toString())
    }

    /** O trecho que piorou ainda está fora da tolerância agora? Se já voltou, não há o que restaurar. */
    private fun worseStillOff(latest: JSONObject?, off: JSONArray): Boolean {
        val verdicts = latest?.optJSONArray("bands") ?: return true
        for (i in 0 until verdicts.length()) {
            val v = verdicts.optJSONObject(i) ?: continue
            if (v.optString("verdict") != "PIOROU") continue
            for (j in 0 until off.length()) {
                val o = off.optJSONObject(j) ?: continue
                if (o.optDouble("fromMs") == v.optDouble("fromMs")) return true
            }
        }
        return false
    }

    /** Fase atual quer avisar e ainda não avisou? Marca como avisada. */
    fun takeAlert(): JSONObject? = synchronized(lock) {
        if (phase !in ALERT_PHASES || alertedPhase == phase) {
            if (phase !in ALERT_PHASES && alertedPhase.isNotEmpty() && phase != "SEM_ECU" && ecuDoneLatch != null) { alertedPhase = ""; dirty = true }
            return null
        }
        alertedPhase = phase
        dirty = true
        lastSaveAt = Long.MIN_VALUE / 2
        JSONObject(last.toString())
    }.also { if (it != null) save() }

    fun json(): JSONObject = synchronized(lock) { JSONObject(last.toString()).put("proofs", proofsJson()) }

    private fun proofsJson(): JSONArray {
        val array = JSONArray()
        for (p in proofs.values) {
            array.put(
                JSONObject().put("index", p.index).put("state", (p.verdict ?: PointState.EM_PROVA).name)
                    .put("onlineMs", p.onlineMs)
                    .put("mixtureBefore", p.mixtureBefore ?: JSONObject.NULL)
                    .put("mixtureNow", p.mixtureNow ?: JSONObject.NULL)
                    .put("roughBefore", p.roughBefore ?: JSONObject.NULL)
                    .put("nearBefore", p.nearBefore ?: JSONObject.NULL),
            )
        }
        return array
    }

    // ----------------------------------------------------------- prova por ponto (F4)

    /** Quantas provas estão abertas (o relógio delas anda a cada avaliação). */
    fun openProofCount(): Int = synchronized(lock) { proofs.values.count { it.verdict == null } }

    /** Pontos da Curva K que o dono acabou de ajustar entram em prova; substitui a prova anterior do mesmo ponto. */
    fun beginProof(pointIndexes: List<Int>, baseline: List<EquivalencePoint>) {
        synchronized(lock) {
            for (index in pointIndexes) {
                val base = baseline.firstOrNull { it.index == index }
                proofs[index] = Proof(index, null, 0L, base?.mixture, base?.roughnessRatio, base?.nearStallRatio, base?.mixture)
            }
            dirty = true
            lastSaveAt = Long.MIN_VALUE / 2
        }
        save()
    }

    /**
     * Julga as provas abertas com os pontos atuais do cérebro. [ecuOnline] decide se o relógio de condução anda
     * (teto de 10 s por chamada, como [observe]). Devolve os estados a sobrepor e os minutos que faltam.
     */
    fun judgePoints(points: List<EquivalencePoint>, ecuOnline: Boolean): ProofOutcome {
        val outcome = synchronized(lock) {
            var changed = false
            val durationNow = durationClock?.invoke() ?: clock()
            val elapsed = lastJudgeAt?.let { (durationNow - it).coerceAtLeast(0L) } ?: 0L
            lastJudgeAt = durationNow
            val dt = elapsed.coerceAtMost(MAX_TICK_MS)
            val states = HashMap<Int, PointState>()
            var remaining: Int? = null
            val iterator = proofs.values.iterator()
            while (iterator.hasNext()) {
                val proof = iterator.next()
                val closed = proof.verdict
                if (closed != null) {
                    states[proof.index] = closed
                    continue
                }
                if (ecuOnline && dt > 0L) {
                    proof.onlineMs += dt
                    dirty = true
                }
                val point = points.firstOrNull { it.index == proof.index }
                val mixture = point?.mixture
                if (mixture != null) proof.mixtureNow = mixture
                if (point == null || mixture == null || point.samples < PROOF_MIN_SAMPLES) {
                    if (proof.onlineMs >= PROOF_TIMEBOX_ONLINE_MS) {
                        proof.verdict = PointState.INCONCLUSIVO
                        states[proof.index] = PointState.INCONCLUSIVO
                        changed = true
                    } else {
                        states[proof.index] = PointState.EM_PROVA
                        val left = ceil((PROOF_TIMEBOX_ONLINE_MS - proof.onlineMs) / 60_000.0).toInt()
                        remaining = maxOf(remaining ?: 0, left)
                    }
                    continue
                }
                val worse = experienceWorse(proof.roughBefore, point.roughnessRatio) ||
                    experienceWorse(proof.nearBefore, point.nearStallRatio)
                val before = proof.mixtureBefore
                val improved = before != null && abs(mixture) < abs(before)
                when {
                    abs(mixture) <= point.tolerance && !worse -> {
                        proof.verdict = PointState.CONFIRMADO
                        states[proof.index] = PointState.CONFIRMADO
                        changed = true
                    }
                    improved && worse -> {
                        proof.verdict = PointState.CONTESTADO
                        states[proof.index] = PointState.CONTESTADO
                        changed = true
                    }
                    else -> {
                        // Fecha sem sobreposição: o ponto volta ao estado base e a próxima ação propõe de novo.
                        iterator.remove()
                        changed = true
                    }
                }
            }
            if (changed) {
                dirty = true
                lastSaveAt = Long.MIN_VALUE / 2
            }
            ProofOutcome(states, remaining)
        }
        save()
        return outcome
    }

    /** Piorou = ln(agora) − ln(antes) > WORSE_DELTA **e** ln(agora) > WORSE_ERROR; sem base ou sem leitura nova não contesta. */
    private fun experienceWorse(before: Double?, now: Double?): Boolean {
        if (before == null || now == null || before <= 0.0 || now <= 0.0) return false
        return ln(now) - ln(before) > EquivalenceTolerances.WORSE_DELTA && ln(now) > EquivalenceTolerances.WORSE_ERROR
    }

    /** Referência trocada: as provas abertas recomeçam do zero (o veredito dependia da Referência antiga). */
    @Suppress("UNUSED_PARAMETER")
    fun restartProofs(reason: String) {
        synchronized(lock) {
            proofs.values.filter { it.verdict == null }.forEach { it.onlineMs = 0L }
            dirty = true
            lastSaveAt = Long.MIN_VALUE / 2
        }
        save()
    }

    /** Mapa K gravado ou AutoMatch nativo: as provas abertas deixam de valer (não viram veredito). */
    @Suppress("UNUSED_PARAMETER")
    fun interruptProofs(reason: String) {
        synchronized(lock) {
            proofs.values.removeAll { it.verdict == null }
            dirty = true
            lastSaveAt = Long.MIN_VALUE / 2
        }
        save()
    }

    /** Estados de banda com dado real (Platina: ZONA_ADQUIRIDA/ATIVIDADE; formato antigo: VALIDO/COLETANDO). */
    private val activeStates = setOf("ZONA_ADQUIRIDA", "ATIVIDADE", "VALIDO", "COLETANDO")

    private fun activeCount(acquisition: JSONObject?, fuel: String): Int? {
        val points = acquisition?.optJSONArray("points") ?: return null
        var n = 0
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            if (p.optString("fuel") == fuel && p.optString("state") in activeStates) n++
        }
        return n
    }

    /** Zonas (0..3) que a ECU marcou como adquiridas para o combustível. */
    private fun acquiredZones(acquisition: JSONObject?, fuel: String): Int {
        val points = acquisition?.optJSONArray("points") ?: return 0
        val zones = HashSet<Int>()
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            if (p.optString("fuel") != fuel) continue
            if (p.optBoolean("zoneAcquired") || p.optString("state") == "VALIDO") zones += p.optInt("zone", -1)
        }
        zones.remove(-1)
        return zones.size
    }

    /** Quanto da verificação já passou e quais faixas ainda esperam leituras (para a tela explicar). */
    private fun verificationProgress(latest: JSONObject, journal: JSONObject): JSONObject {
        val budget = journal.optLong("verifyBudgetMs", RefinementJournal.VERIFY_PARTIAL_ONLINE_MS).coerceAtLeast(1L)
        val online = latest.optLong("onlineMs", 0L)
        val waiting = JSONArray()
        val bands = latest.optJSONArray("bands") ?: JSONArray()
        for (i in 0 until bands.length()) {
            val b = bands.optJSONObject(i) ?: continue
            if (b.optString("verdict") == "COLETANDO") {
                waiting.put(JSONObject().put("fromMs", b.optDouble("fromMs")).put("toMs", b.optDouble("toMs"))
                    .put("samples", b.optInt("samplesAfter")).put("needed", RefinementJournal.MIN_BAND_SAMPLES))
            }
        }
        return JSONObject().put("onlineMinutes", online / 60_000.0).put("budgetMinutes", budget / 60_000.0).put("waitingBands", waiting)
    }

    private fun headline(phase: String, count: Int?, max: Int?, measured: Int, off: Int, out: JSONObject, verification: JSONObject?): String = when (phase) {
        "SEM_ECU" -> "Conecte a ECU para acompanhar a calibração."
        "LENDO_ECU" -> "Lendo o estado da ECU: AutoMatch e curvas."
        "TENTATIVA_ENCERRADA" -> if (out.optString("reasonCode") == "ECU_READ_TIMEOUT")
            "A ECU não respondeu a tempo. O app continua tentando ler, sem gravar."
        else "Não houve dados suficientes para concluir esta etapa. Nada foi gravado automaticamente."
        "ECU_TRABALHANDO" -> "A ECU está no automático" +
            (if (count != null) " ${count}" + (if (max != null) " de $max" else "") else "") +
            ". O OMEGAS observa e junta pontos próprios (${out.optInt("ourPoints")} até agora)."
        "COLETANDO_NOSSOS" -> if (out.optString("petrolReference") == "ECU")
            "A ECU terminou e já tem a curva de gasolina. O OMEGAS só precisa medir o GNV rodando."
        else "A ECU terminou. Agora o OMEGAS junta pontos GNV × gasolina no mesmo RPM e MAP."
        "PROPOSTA_PRONTA" -> "Curva refinada pronta: $off de $measured faixas fora da gasolina. Revise e grave."
        "VERIFICANDO" -> "Curva nova gravada. O OMEGAS mede faixa por faixa se o GNV chegou na gasolina" +
            (verification?.let { " (%d de %d min de condução)".format(Math.floor(it.optDouble("onlineMinutes")).toInt(), Math.round(it.optDouble("budgetMinutes")).toInt()) } ?: "") + "."
        "RESTAURAR_TRECHO" -> "Um trecho piorou com a curva nova. Restaure só esse trecho."
        "ESTAVEL" -> "GNV equivalente à gasolina em $measured faixas (±3%). Pode desconectar."
        else -> ""
    }

    private fun nextStep(phase: String, petrolValid: Int, gasValid: Int, missing: JSONArray, index: JSONObject, verification: JSONObject?): String = when (phase) {
        "SEM_ECU" -> "Ligue o cabo e o motor."
        "TENTATIVA_ENCERRADA" -> "A próxima leitura válida retoma o acompanhamento automaticamente."
        "LENDO_ECU" -> "Aguarde alguns segundos. A ECU guarda o AutoMatch e as curvas e entrega tudo ao conectar."
        "ECU_TRABALHANDO" -> "Dirija normalmente nos dois combustíveis. A gravação libera quando a ECU terminar o automático."
        "VERIFICANDO" -> {
            val waiting = verification?.optJSONArray("waitingBands")
            val wanted = (0 until (waiting?.length() ?: 0)).mapNotNull { waiting?.optJSONObject(it) }
                .joinToString(", ") { "%.1f–%.1f ms".format(it.optDouble("fromMs"), it.optDouble("toMs")) }
            if (wanted.isNotEmpty()) "Rode no GNV passando por $wanted. Se não passar por lá, o OMEGAS fecha a verificação com o que mediu."
            else "Continue rodando: faltam poucas leituras para fechar o resultado."
        }
        "COLETANDO_NOSSOS" -> {
            val wanted = (0 until missing.length()).mapNotNull { missing.optJSONObject(it) }
                .joinToString(", ") { "%.1f–%.1f ms".format(it.optDouble("fromMs"), it.optDouble("toMs")) }
            val reference = index.optString("petrolReference", "NENHUMA")
            when {
                // Só pede gasolina quando nem o app nem a ECU têm referência de gasolina.
                reference == "NENHUMA" && index.optInt("petrolObservations") < 40 ->
                    "A ECU ainda não tem curva de gasolina madura. Rode alguns minutos na gasolina para criar a referência."
                wanted.isNotEmpty() -> "Rode no GNV passando por cargas de injeção $wanted."
                else -> "Continue rodando no GNV."
            }
        }
        "PROPOSTA_PRONTA" -> "Abra AutoCal → Refinar curva. Nada é gravado sem sua confirmação."
        "RESTAURAR_TRECHO" -> "Abra AutoCal → Restaurar trecho que piorou."
        "ESTAVEL" -> "Nenhuma ação. O OMEGAS continua medindo e avisa se algo mudar."
        else -> ""
    }

    private fun save() {
        val target = file ?: return
        val payload = synchronized(lock) {
            val now = clock()
            if (!dirty || now - lastSaveAt < 60_000L) return
            dirty = false
            lastSaveAt = now
            JSONObject().put("format", FORMAT).put("lastCount", lastCount ?: JSONObject.NULL)
                .put("quietMs", quietMs).put("phase", phase).put("alertedPhase", alertedPhase)
                .put("ecuDoneLatch", ecuDoneLatch ?: JSONObject.NULL)
                .put("watchedPhase", watchedPhase).put("phaseElapsedMs", phaseElapsedMs)
                .put("durationMonotonic", durationClock != null)
                .put("durationAt", lastDurationAt ?: JSONObject.NULL)
                .put("expiredEvidence", expiredEvidence ?: JSONObject.NULL).put("timeoutReason", timeoutReason)
                .put("proofs", proofsJson())
        }
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) { target.writeText(payload.toString()); tmp.delete() }
        } catch (_: Exception) {
            synchronized(lock) { dirty = true }
        }
    }

    private fun load() {
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.optString("format") != FORMAT) return
            lastCount = if (root.isNull("lastCount")) null else root.optInt("lastCount")
            quietMs = root.optLong("quietMs", 0L)
            phase = root.optString("phase", "SEM_ECU")
            alertedPhase = root.optString("alertedPhase", "")
            watchedPhase = root.optString("watchedPhase")
            // elapsedRealtime atravessa reinício do processo; após reboot, delta negativo vale zero.
            // Formato antigo/relógio incompatível não inventa tempo nem autorização.
            if (root.optBoolean("durationMonotonic") == (durationClock != null) && !root.isNull("durationAt")) {
                lastDurationAt = root.optLong("durationAt").takeIf { it >= 0L }
            }
            phaseElapsedMs = root.optLong("phaseElapsedMs", 0L).coerceIn(0L, 40 * 60_000L)
            expiredEvidence = root.optString("expiredEvidence").takeIf { !root.isNull("expiredEvidence") && it.isNotBlank() }
            timeoutReason = root.optString("timeoutReason")
            // Revalidar autorização nativa nesta conexão; o resultado anterior é só contexto.
            ecuDoneLatch = null
            lastCount = null
            quietMs = 0L
            loadProofs(root.optJSONArray("proofs"))
        } catch (_: Exception) {
        }
    }

    /** Arquivo antigo sem `proofs` (ou com lixo) abre sem provas. */
    private fun loadProofs(array: JSONArray?) {
        proofs.clear()
        if (array == null) return
        for (i in 0 until array.length()) {
            try {
                val o = array.optJSONObject(i) ?: continue
                val index = o.optInt("index", -1)
                if (index !in 0 until 30) continue
                val state = runCatching { PointState.valueOf(o.optString("state")) }.getOrNull()
                val verdict = state?.takeIf {
                    it == PointState.CONFIRMADO || it == PointState.CONTESTADO || it == PointState.INCONCLUSIVO
                }
                fun number(key: String): Double? =
                    if (o.isNull(key)) null else o.optDouble(key, Double.NaN).takeIf { it.isFinite() }
                proofs[index] = Proof(
                    index, verdict, o.optLong("onlineMs", 0L).coerceIn(0L, PROOF_TIMEBOX_ONLINE_MS),
                    number("mixtureBefore"), number("roughBefore"), number("nearBefore"), number("mixtureNow"),
                )
            } catch (_: Exception) {
            }
        }
    }
}
