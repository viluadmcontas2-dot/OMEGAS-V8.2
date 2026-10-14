package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/**
 * Piloto do refino: decide em que fase a calibração está, sem ninguém olhar a tela.
 * É a única fonte de verdade da fase, da frase e dos botões: a tela só desenha [json].
 *
 * 1. ECU_TRABALHANDO — o AutoCal nativo ainda está no automático 1, 2, 3… e pedindo
 *    aquisição. O OMEGAS só observa e junta pontos próprios (RPM×MAP) em paralelo:
 *    gravar agora seria sobrescrito pelo próximo automático da ECU.
 * 2. A ECU parou (atingiu MAX_AUTOMATCH, AutoCal desligado/congelado, ou aquisição
 *    completa nos dois combustíveis; só evidência da ECU, nunca relógio): é a nossa vez.
 *    Prioridade (um botão por ação, nenhum estado sem saída, sem "Entendi"):
 *    - RESTAURAR_TRECHO: pontos pioraram (fora da margem do dono) → "Restaurar N pontos que pioraram";
 *      se houver proposta para outros pontos, "Revisar e gravar" aparece junto (pontos independentes).
 *    - PROPOSTA_PRONTA: há proposta do motor (já sem os pontos em medição/bloqueados), feita sobre a última
 *      gravação, com evidência de equivalência (modo EQUIVALENCE ou faixa medida fora) e as faixas medidas
 *      não estão todas dentro de ±4% → "Revisar e gravar N pontos".
 *    - VERIFICANDO: gravação nova em medição, sem botão; a frase diz o progresso em palavras.
 *    - ESTAVEL: nenhuma faixa medida fora da margem do dono (até 5%; quem passou de 5% só volta em 4%) e nada a
 *      propor para chegar ao alvo de ±4%: pode desconectar.
 *    - COLETANDO_NOSSOS: faltam leituras GNV/gasolina no mesmo RPM×MAP.
 *    "Desfazer última gravação" vale para a última proposta gravada até a próxima gravação.
 *    Ajuste local (dono, 2026-10-11: a curva aprende tudo e sabe onde ajustar o quê): grupo coluna do Mapa K × faixa
 *    de t que [EquivalenceAttribution] separou como local vira a ação secundária "Revisar ajuste local no Mapa K
 *    (N células)", só com a ECU fora do automático e sem proposta/verificação da curva tocando aquela faixa. Ela só
 *    abre o Mapa K com as células preenchidas; a gravação é a manual (revisão, ACK e readback).
 *
 * Margem do dono com histerese: a faixa entra "fora" só acima de 5% e sai em 4% ou menos (de 4,1 a 5% nada
 * acontece); faixa medida pela curva de gasolina da ECU (mais grossa) usa ±6% (Platina 00f6fd02). O estado
 * da histerese é persistido. A proposta só some depois de [PROPOSAL_EXIT_EMPTY_TICKS] observações seguidas
 * vazias, e leitura desconhecida da ECU não conta como vazia.
 *
 * Nunca grava na ECU. Só observa, decide a fase e avisa (notificação/UI).
 */
class RefinementAutopilot(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-refinement-autopilot-v1"
        /** Margem do dono: até 5% é aceito; a faixa só entra "fora" acima disso. */
        val TOLERANCE_LOG = ln(1.05)
        /** Alvo do dono (±4%): faixa que passou de 5% só volta a "dentro" aqui. */
        val TOLERANCE_EXIT_LOG = ln(1.04)
        /**
         * Faixa cuja gasolina de referência veio da curva da ECU (ecuShare ≥ 0,5), mais grossa que a própria:
         * entra "fora" só acima de 6% (Platina 00f6fd02) e volta a "dentro" em 5%.
         */
        val TOLERANCE_ECU_REF_LOG = ln(1.06)
        val TOLERANCE_ECU_REF_EXIT_LOG = ln(1.05)
        const val ECU_REF_SHARE = 0.5
        /** A proposta só deixa de valer depois de tantas observações seguidas vazias (~9 s). */
        const val PROPOSAL_EXIT_EMPTY_TICKS = 3
        const val MIN_BAND_SAMPLES = RefinementJournal.MIN_BAND_SAMPLES
        const val MIN_STABLE_BANDS = 3
        private const val MAX_TICK_MS = 10_000L
        /** Fases que merecem avisar o motorista uma vez. */
        val ALERT_PHASES = setOf("PROPOSTA_PRONTA", "RESTAURAR_TRECHO", "ESTAVEL")
    }

    private val lock = Any()
    private var lastCount: Int? = null
    /** Depois de readquirir ou de um AutoMatch, a aquisição velha ainda pode aparecer "completa" na leitura: só vale de novo depois de vista incompleta. */
    private var awaitingRestartEvidence = false
    /** Dono (2026-10-14): "refinar por minha conta" libera as gravações mesmo com a ECU no automático; ele decide quando gravar e manda a ECU reler o GNV. */
    private var manual = false
    private var lastTickAt = 0L
    private var phase = "SEM_ECU"
    private var alertedPhase = ""
    /** A ECU parou de fazer automático: vale até ela fazer outro (contador muda). */
    private var ecuDoneLatch: String? = null
    private var dirty = false
    private var lastSaveAt = Long.MIN_VALUE / 2
    private var last: JSONObject = JSONObject().put("phase", phase).put("action", JSONObject().put("kind", "none"))
    /** Histerese por faixa (entra acima de 5%, sai em 4%; ±6% com a referência da ECU). Persistida. */
    private val offLatch = BooleanArray(EquivalenceLedger.BANDS.size)
    /** Proposta vigente (a mesma lista que a revisão grava) e quantas observações seguidas vieram vazias. */
    private var heldProposal: JSONObject? = null
    private var emptyStreak = 0
    private var lastJournalId = ""

    init { load() }

    /**
     * Uma observação (tick do serviço). [monitor] = status do AutoCal nativo
     * (autoMatchCount, maxAutomatch, autoCalEnabled); [acquisition] = AutoCalAcquisition;
     * [index] = EquivalenceLedger.index(); [journal] = RefinementJournal.json();
     * [restoreCount] = pontos que o diário oferece restaurar; [proposal] = RefinementJournal.proposalFrom
     * (sem ela não há proposta: a tela nunca inventa "pronto").
     * Retorna a fase decidida (JSON) — também guardada para a UI.
     */
    fun observe(
        ecuOnline: Boolean,
        monitor: JSONObject?,
        acquisition: JSONObject?,
        index: JSONObject,
        journal: JSONObject,
        restoreCount: Int,
        proposal: JSONObject? = null,
        /** LocalMapPlanner.plan: grupos locais candidatos (sem ele, nenhum ajuste local). */
        local: JSONObject? = null,
    ): JSONObject {
        val result = synchronized(lock) {
            val now = clock()
            lastTickAt = now
            val enabled = monitor?.optInt("autoCalEnabled", -1)?.takeIf { monitor.has("autoCalEnabled") && !monitor.isNull("autoCalEnabled") }
            val count = monitor?.optInt("autoMatchCount", -1)?.takeIf { it >= 0 }
            val max = monitor?.optInt("maxAutomatch", -1)?.takeIf { it > 0 && !monitor.isNull("maxAutomatch") }
            if (ecuOnline && count != null) {
                if (lastCount != null && count != lastCount) { ecuDoneLatch = null; awaitingRestartEvidence = true; dirty = true }
                if (lastCount != count) dirty = true
                lastCount = count
            }
            // Pontos da ECU com atividade (os que o gráfico desenha) e zonas que a ECU deu como adquiridas.
            // Aquisição desconhecida (não lida, campo indisponível) = null, nunca "0 pontos".
            val petrolValid = activeCount(acquisition, "GASOLINA")
            val gasValid = activeCount(acquisition, "GNV")
            val petrolZones = acquiredZones(acquisition, "GASOLINA")
            val gasZones = acquiredZones(acquisition, "GNV")
            val complete = petrolZones >= 4 && gasZones >= 4
            if (awaitingRestartEvidence && !complete) { awaitingRestartEvidence = false; dirty = true }
            val fresh = when {
                manual -> "MANUAL"
                enabled == 0 -> "AUTOCAL_DESLIGADO"
                max != null && count != null && count >= max -> "MAX_AUTOMATCH"
                complete && !awaitingRestartEvidence -> "AQUISICAO_COMPLETA"
                else -> null
            }
            if (fresh != null && fresh != ecuDoneLatch) { ecuDoneLatch = fresh; dirty = true }
            val ecuReason = ecuDoneLatch
            val out = JSONObject()
                .put("ecuOnline", ecuOnline)
                .put("autoMatchCount", count ?: JSONObject.NULL)
                .put("maxAutomatch", max ?: JSONObject.NULL)
                .put("autoCalEnabled", enabled ?: JSONObject.NULL)
                .put("petrolValid", petrolValid ?: JSONObject.NULL)
                .put("gasValid", gasValid ?: JSONObject.NULL)
                .put("petrolZones", petrolZones)
                .put("gasZones", gasZones)
                .put("ecuDone", ecuReason != null)
                .put("ecuDoneReason", ecuReason ?: JSONObject.NULL)
                .put("manual", manual)
                .put("ourPoints", index.optInt("samples", 0))
                .put("petrolObservations", index.optInt("petrolObservations", 0))
                .put("gasObservations", index.optInt("gasObservations", 0))
                .put("automatic", false)

            // Faixas com histerese: entra "fora" acima de 5% e só sai em 4% (±6% com a referência da ECU).
            val bands = index.optJSONArray("bands") ?: JSONArray()
            val measured = ArrayList<JSONObject>()
            val off = JSONArray()
            val missing = JSONArray()
            var onTarget = true
            for (i in 0 until bands.length()) {
                val b = bands.optJSONObject(i) ?: continue
                val r = b.optDouble("ratio", Double.NaN)
                val ecuRef = b.optDouble("ecuShare", 0.0) >= ECU_REF_SHARE
                val enter = if (ecuRef) TOLERANCE_ECU_REF_LOG else TOLERANCE_LOG
                val exit = if (ecuRef) TOLERANCE_ECU_REF_EXIT_LOG else TOLERANCE_EXIT_LOG
                val label = JSONObject().put("fromMs", b.optDouble("fromMs")).put("toMs", b.optDouble("toMs"))
                    .put("samples", b.optInt("samples")).put("ratio", if (r.isFinite()) r else JSONObject.NULL)
                    .put("ecuReference", ecuRef)
                if (b.optInt("samples") >= MIN_BAND_SAMPLES && r.isFinite() && r > 0) {
                    measured += label
                    val error = abs(ln(r))
                    val latched = if (i < offLatch.size) {
                        val was = offLatch[i]
                        if (error > enter) offLatch[i] = true else if (error <= exit) offLatch[i] = false
                        if (offLatch[i] != was) { dirty = true; lastSaveAt = Long.MIN_VALUE / 2 }
                        offLatch[i]
                    } else error > enter
                    if (latched) off.put(label)
                    if (error > exit) onTarget = false
                } else {
                    // Sem leituras (curva nova naquele trecho, GNV recomeçado): a histerese recomeça junto.
                    if (i < offLatch.size && offLatch[i]) { offLatch[i] = false; dirty = true; lastSaveAt = Long.MIN_VALUE / 2 }
                    missing.put(label)
                }
            }
            out.put("bandsMeasured", measured.size).put("bandsOff", off).put("bandsMissing", missing)
            // Margem do dono: até 4% (alvo) é estável; entre 4% e 5% (aceito) mostra a proposta do motor se houver,
            // senão também é estável (nunca "coletando" para sempre); acima de 5% é fora até voltar a 4%.
            val stable = measured.size >= MIN_STABLE_BANDS && off.length() == 0

            val latest = journal.optJSONObject("latest")
            val latestStatus = latest?.optString("status").orEmpty()
            val latestId = latest?.optString("id").orEmpty()
            val verifying = latestStatus == RefinementJournal.STATUS_VERIFYING ||
                (journal.optJSONArray("measuringBands")?.length() ?: 0) > 0
            // Gravação nova: a proposta anterior foi feita sobre a curva antiga e sai na hora. Proposta calculada
            // antes da gravação (outro experimento de base) é descartada mesmo que chegue depois.
            if (latestId != lastJournalId) { heldProposal = null; emptyStreak = 0; lastJournalId = latestId }
            val current = proposal?.takeIf { it.optBoolean("known", false) && it.optString("basedOn", "") == latestId }
            if (current != null) {
                if (current.optInt("count", 0) > 0) { heldProposal = JSONObject(current.toString()); emptyStreak = 0 }
                else if (++emptyStreak >= PROPOSAL_EXIT_EMPTY_TICKS) heldProposal = null
            }
            // Sem evidência de equivalência (motor em POLISH: só anti-tranco) e nenhuma faixa fora: não é "pronta".
            val evidence = heldProposal?.optString("mode") == "EQUIVALENCE" || off.length() > 0
            val proposalCount = if (evidence) heldProposal?.optInt("count", 0) ?: 0 else 0

            val resultJson = journal.optJSONObject("result")
            val points = resultJson?.optJSONArray("points") ?: JSONArray()
            fun outcome(name: String) = (0 until points.length()).count { points.optJSONObject(it)?.optString("outcome") == name }
            val improved = outcome("MELHOROU")
            val worse = outcome("PIOROU")
            val measuring = outcome("MEDINDO")

            val next = when {
                !ecuOnline && lastCount == null -> "SEM_ECU"
                ecuReason == null -> "ECU_TRABALHANDO"
                restoreCount > 0 -> "RESTAURAR_TRECHO"
                (!stable || !onTarget) && proposalCount > 0 -> "PROPOSTA_PRONTA"
                verifying -> "VERIFICANDO"
                stable -> "ESTAVEL"
                else -> "COLETANDO_NOSSOS"
            }
            if (next != phase) { phase = next; dirty = true; lastSaveAt = Long.MIN_VALUE / 2 }

            val action = when (phase) {
                "ECU_TRABALHANDO" -> if (proposalCount > 0) JSONObject().put("kind", "waiting").put("enabled", false)
                    .put("label", "Aguardando a ECU terminar o automático" +
                        (if (count != null) " ($count" + (if (max != null) " de $max" else "") + ")" else ""))
                else JSONObject().put("kind", "none")
                "RESTAURAR_TRECHO" -> JSONObject().put("kind", "restore").put("enabled", ecuOnline).put("count", restoreCount)
                    .put("label", if (ecuOnline) "Restaurar $restoreCount ${plural(restoreCount, "ponto que piorou", "pontos que pioraram")}"
                    else "Conecte a ECU para restaurar")
                "PROPOSTA_PRONTA" -> JSONObject().put("kind", "review").put("enabled", ecuOnline).put("count", proposalCount)
                    .put("label", if (ecuOnline) "Revisar e gravar $proposalCount ${plural(proposalCount, "ponto", "pontos")}"
                    else "Conecte a ECU para gravar")
                "ESTAVEL" -> JSONObject().put("kind", "stable").put("enabled", false).put("label", "✓ Estável · pode desconectar")
                else -> JSONObject().put("kind", "none")
            }
            val secondary = JSONArray()
            // Pontos independentes: restaurar os que pioraram não esconde a proposta para os outros.
            if (phase == "RESTAURAR_TRECHO" && proposalCount > 0) {
                secondary.put(JSONObject().put("kind", "review").put("enabled", ecuOnline).put("count", proposalCount)
                    .put("label", "Revisar e gravar $proposalCount ${plural(proposalCount, "ponto", "pontos")}"))
            }
            if (latest?.optBoolean("undoable", false) == true && ecuReason != null) {
                secondary.put(JSONObject().put("kind", "undo").put("enabled", ecuOnline).put("label", "Desfazer última gravação").put("experimentId", latestId))
            }
            // Ajuste local no Mapa K: só com a ECU fora do automático e com a curva quieta naquela faixa (proposta da
            // curva ou verificação tocando o mesmo t esperam: primeiro a curva, depois o local).
            val localGroups = local?.optJSONArray("groups") ?: JSONArray()
            val curveBands = HashSet<Int>()
            if (proposalCount > 0) heldProposal?.optJSONArray("points")?.let { a ->
                for (k in 0 until a.length()) a.optJSONObject(k)?.optDouble("referenceTimeMs", Double.NaN)?.takeIf { it.isFinite() }
                    ?.let { curveBands += RefinementJournal.touchedBands(it) }
            }
            journal.optJSONArray("measuringBands")?.let { a -> for (k in 0 until a.length()) curveBands += a.optInt(k, -1) }
            val localKept = (0 until localGroups.length()).mapNotNull { localGroups.optJSONObject(it) }
                .filter { it.optInt("band", -1) !in curveBands }
            val localCells = if (ecuReason != null) LocalMapPlanner.cells(localKept) else JSONArray()
            val localCount = localCells.length()
            if (localCount > 0) {
                secondary.put(JSONObject().put("kind", "local").put("enabled", ecuOnline).put("count", localCount)
                    .put("label", "Revisar ajuste local no Mapa K ($localCount ${plural(localCount, "célula", "células")})")
                    .put("cells", localCells).put("groups", JSONArray(localKept)).put("automatic", false))
            }
            val open = journal.optJSONObject("verifying")
            val verification = if (verifying && open != null) JSONObject()
                .put("bandsLeft", open.optInt("bandsLeft", 0))
                .put("newReads", open.optInt("newReads", 0))
                .put("budgetReads", open.optInt("budgetReads", RefinementJournal.VERIFY_PARTIAL_SAMPLES))
                .put("giveUpReads", open.optInt("giveUpReads", RefinementJournal.VERIFY_GIVE_UP_SAMPLES))
            else null
            out.put("phase", phase)
                .put("canDisconnect", phase == "ESTAVEL" && localCount == 0)
                .put("action", if (phase == "ESTAVEL" && localCount > 0) JSONObject().put("kind", "stable").put("enabled", false).put("label", "✓ Curva estável") else action)
                .put("local", JSONObject().put("groups", localGroups.length()).put("cells", localCount)
                    .put("waiting", if (localGroups.length() == 0) JSONObject.NULL else when {
                        ecuReason == null -> "ECU_NO_AUTOMATICO"
                        localKept.size < localGroups.length() -> "CURVA_PRIMEIRO"
                        else -> JSONObject.NULL
                    }))
                .put("secondary", secondary)
                .put("proposal", heldProposal?.takeIf { proposalCount > 0 }?.let { JSONObject(it.toString()) } ?: JSONObject()
                    .put("known", proposal?.optBoolean("known", false) ?: false).put("count", 0).put("points", JSONArray())
                    .put("reason", when {
                        heldProposal != null && !evidence -> "SEM_EVIDENCIA_DE_EQUIVALENCIA"
                        proposal != null && current == null && proposal.optBoolean("known", false) -> "PROPOSTA_DE_OUTRA_GRAVACAO"
                        else -> proposal?.optString("reason") ?: "MOTOR_NAO_CONSULTADO"
                    }))
                .put("result", JSONObject().put("improved", improved).put("worse", worse).put("measuring", measuring)
                    .put("id", resultJson?.optString("id") ?: JSONObject.NULL))
                .put("headline", headline(phase, count, max, measured.size, off.length(), proposalCount, restoreCount,
                    improved, worse, measuring, verification, out, localCount))
                .put("next", nextStep(phase, missing, index, proposalCount, measuring, verifying, localCount))
            if (verification != null) out.put("verification", verification)
            if (latest != null) out.put("journalStatus", latestStatus)
            last = out
            out
        }
        save()
        return JSONObject(result.toString())
    }

    /** Fase atual quer avisar e ainda não avisou? Marca como avisada. */
    /**
     * O dono (ou o app) mandou a ECU readquirir (zerar gasolina/GNV, apagar ponto, AutoMatch manual): a aquisição recomeça,
     * então o "silêncio" medido até aqui não vale mais. Sessão 19:40 (2026-10-13): reset aos 46 min, "quieto 49,8 min" e
     * "ECU terminou" aos 52 min, e o AutoMatch da ECU veio 1 min depois por cima da proposta.
     */
    fun noteAcquisitionRestart() = synchronized(lock) {
        ecuDoneLatch = null
        awaitingRestartEvidence = true
        dirty = true
    }

    /** Liga/desliga o "refinar por minha conta". Desligar volta a valer só o que a ECU mostra. */
    fun setManual(on: Boolean) = synchronized(lock) {
        if (manual == on) return@synchronized
        manual = on
        if (!on && ecuDoneLatch == "MANUAL") ecuDoneLatch = null
        dirty = true
    }

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

    fun json(): JSONObject = synchronized(lock) { JSONObject(last.toString()) }

    /** Estados de banda com dado real (Platina: ZONA_ADQUIRIDA/ATIVIDADE; formato antigo: VALIDO/COLETANDO). */
    private val activeStates = setOf("ZONA_ADQUIRIDA", "ATIVIDADE", "VALIDO", "COLETANDO")

    private fun activeCount(acquisition: JSONObject?, fuel: String): Int? {
        val points = acquisition?.optJSONArray("points") ?: return null
        var n = 0
        var seen = 0
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            if (p.optString("fuel") != fuel) continue
            if (p.optString("state") == "SEM_DADO") return null
            seen++
            if (p.optString("state") in activeStates) n++
        }
        return if (seen == 0) null else n
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

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    /** "faltam 2 faixas · 60 de 150 leituras novas" (só evidência: parado nada anda). */
    private fun progress(v: JSONObject): String {
        val left = v.optInt("bandsLeft", 0)
        val reads = v.optInt("newReads", 0)
        val budget = v.optInt("budgetReads", RefinementJournal.VERIFY_PARTIAL_SAMPLES)
        return when {
            left > 0 -> "faltam $left ${plural(left, "trecho", "trechos")} · ${reads.coerceAtMost(budget)} de $budget leituras novas"
            reads >= budget -> "sem leituras novas nos trechos gravados · fecha sozinho com mais leituras"
            else -> "fechando o resultado · $reads de $budget leituras novas"
        }
    }

    private fun headline(
        phase: String, count: Int?, max: Int?, measured: Int, off: Int, proposal: Int, restore: Int,
        improved: Int, worse: Int, measuring: Int, verification: JSONObject?, out: JSONObject, local: Int = 0,
    ): String = when (phase) {
        "SEM_ECU" -> "Conecte a ECU para acompanhar a calibração."
        "ECU_TRABALHANDO" -> "A ECU está no automático" +
            (if (count != null) " $count" + (if (max != null) " de $max" else "") else "") +
            ". O OMEGAS observa e junta pontos próprios (${out.optInt("ourPoints")} até agora)."
        "COLETANDO_NOSSOS" -> if (off > 0) "$off ${plural(off, "trecho", "trechos")} fora da gasolina, ainda sem proposta do motor. O OMEGAS segue medindo."
            else "A ECU terminou. Agora o OMEGAS junta pontos GNV × gasolina no mesmo RPM e MAP."
        "PROPOSTA_PRONTA" -> "Curva refinada pronta: $proposal ${plural(proposal, "ponto", "pontos")} para ajustar" +
            (if (off > 0) " ($off de $measured trechos fora da gasolina)." else ".") +
            (verification?.let { " Gravação anterior: ${progress(it)}." } ?: "")
        "VERIFICANDO" -> "Medindo a gravação: " + (verification?.let { progress(it) } ?: "aguardando leituras") +
            (if (improved + worse > 0) ". Até agora $improved ${plural(improved, "melhorou", "melhoraram")} e $worse ${plural(worse, "piorou", "pioraram")}." else ".")
        "RESTAURAR_TRECHO" -> "$restore ${plural(restore, "ponto piorou", "pontos pioraram")} com a última gravação. " +
            "Restaure só ${plural(restore, "esse ponto", "esses pontos")}; os que melhoraram ficam."
        "ESTAVEL" -> if (local > 0) "Curva equivalente à gasolina em $measured trechos (dentro de 5%). Falta um ajuste local no Mapa K."
            else "GNV equivalente à gasolina em $measured trechos (dentro de 5%). Pode desconectar."
        else -> ""
    }

    private fun nextStep(phase: String, missing: JSONArray, index: JSONObject, proposal: Int, measuring: Int, verifying: Boolean, local: Int = 0): String = when (phase) {
        "SEM_ECU" -> "Ligue o cabo e o motor."
        "ECU_TRABALHANDO" -> "Dirija normalmente nos dois combustíveis. A gravação libera quando a ECU terminar o automático."
        "COLETANDO_NOSSOS", "VERIFICANDO" -> {
            val wanted = (0 until missing.length()).mapNotNull { missing.optJSONObject(it) }
                .joinToString(", ") { "%.1f–%.1f ms".format(it.optDouble("fromMs"), it.optDouble("toMs")) }
            when {
                index.optInt("petrolObservations") < 40 -> "Rode alguns minutos na gasolina para criar a referência."
                wanted.isNotEmpty() -> "Rode no GNV passando por cargas de injeção $wanted." +
                    (if (verifying) " Se não passar por lá, o OMEGAS fecha a medição depois de ${RefinementJournal.VERIFY_PARTIAL_SAMPLES} leituras novas e segue com os outros trechos." else "")
                verifying && measuring > 0 -> "Continue rodando no GNV: faltam poucas leituras para fechar o resultado."
                else -> "Continue rodando no GNV."
            }
        }
        "PROPOSTA_PRONTA" -> "Toque em Revisar e gravar. Nada é gravado sem você confirmar; a ECU confere por readback."
        "RESTAURAR_TRECHO" -> "Restaurar volta só esses pontos ao valor anterior, com readback. Sem restaurar, o OMEGAS segue medindo e a oferta some se o trecho voltar para dentro da margem." +
            (if (proposal > 0) " Os outros $proposal ${plural(proposal, "ponto", "pontos")} podem ser revisados e gravados agora." else "")
        "ESTAVEL" -> if (local > 0) "Toque em Revisar ajuste local: abre o Mapa K com as células preenchidas. Nada é gravado sem você confirmar."
            else "Nenhuma ação. O OMEGAS continua medindo e avisa se algo mudar."
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
                .put("awaiting", awaitingRestartEvidence).put("manual", manual).put("phase", phase).put("alertedPhase", alertedPhase)
                .put("ecuDoneLatch", ecuDoneLatch ?: JSONObject.NULL)
                .put("offLatch", JSONArray(offLatch.toList()))
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
            awaitingRestartEvidence = root.optBoolean("awaiting", false)
            manual = root.optBoolean("manual", false)
            phase = root.optString("phase", "SEM_ECU")
            alertedPhase = root.optString("alertedPhase", "")
            ecuDoneLatch = if (root.isNull("ecuDoneLatch")) null else root.optString("ecuDoneLatch").takeIf { it.isNotBlank() }
            root.optJSONArray("offLatch")?.let { a -> for (i in 0 until minOf(a.length(), offLatch.size)) offLatch[i] = a.optBoolean(i, false) }
        } catch (_: Exception) {
        }
    }
}
