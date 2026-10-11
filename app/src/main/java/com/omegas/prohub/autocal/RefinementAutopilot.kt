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
 *    completa e sem automático novo há [QUIET_MS] de ECU online): é a nossa vez.
 *    Prioridade (um botão por ação, nenhum estado sem saída, sem "Entendi"):
 *    - RESTAURAR_TRECHO: pontos pioraram (fora da margem do dono) → "Restaurar N pontos que pioraram";
 *      se houver proposta para outros pontos, "Revisar e gravar" aparece junto (pontos independentes).
 *    - PROPOSTA_PRONTA: há proposta do motor (já sem os pontos em medição/bloqueados) e as faixas
 *      medidas não estão todas dentro de ±3% → "Revisar e gravar N pontos".
 *    - VERIFICANDO: gravação nova em medição, sem botão; a frase diz o progresso em palavras.
 *    - ESTAVEL: todas as faixas medidas dentro de ±3%: pode desconectar.
 *    - COLETANDO_NOSSOS: faltam leituras GNV/gasolina no mesmo RPM×MAP.
 *    "Desfazer última gravação" só enquanto a última proposta gravada ainda está sendo medida.
 *
 * Histerese sem mudar o limiar de entrada: a faixa entra "fora" acima de ±3% (como antes) e só sai abaixo
 * do ruído de medição do diário (2%); a proposta só some depois de [PROPOSAL_EXIT_EMPTY_TICKS] observações
 * seguidas vazias, e leitura desconhecida da ECU não conta como vazia.
 *
 * Nunca grava na ECU. Só observa, decide a fase e avisa (notificação/UI).
 */
class RefinementAutopilot(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-refinement-autopilot-v1"
        /** ECU online sem automático novo, com aquisição completa, por este tempo = ECU parou. */
        const val QUIET_MS = 10 * 60_000L
        /** Sem aquisição completa (faixas que o motorista nunca visita), espera mais. */
        const val QUIET_PARTIAL_MS = 25 * 60_000L
        const val PARTIAL_MIN_ZONES = 3
        /** ±3%: abaixo disso GNV e gasolina já pedem o mesmo (ruído de medição ~2%). Limiar de entrada. */
        val TOLERANCE_LOG = ln(1.03)
        /** Saída da histerese: a faixa só volta a "dentro" abaixo do ruído de medição que o diário já usa. */
        const val TOLERANCE_EXIT_LOG = RefinementJournal.NOISE_LOG
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
    private var quietMs = 0L
    private var lastTickAt = 0L
    private var phase = "SEM_ECU"
    private var alertedPhase = ""
    /** A ECU parou de fazer automático: vale até ela fazer outro (contador muda). */
    private var ecuDoneLatch: String? = null
    private var dirty = false
    private var lastSaveAt = Long.MIN_VALUE / 2
    private var last: JSONObject = JSONObject().put("phase", phase).put("action", JSONObject().put("kind", "none"))
    /** Histerese por faixa (entra acima de ±3%, sai abaixo de 2%). */
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
    ): JSONObject {
        val result = synchronized(lock) {
            val now = clock()
            val dt = if (lastTickAt == 0L) 0L else (now - lastTickAt).coerceIn(0L, MAX_TICK_MS)
            lastTickAt = now
            val enabled = monitor?.optInt("autoCalEnabled", -1)?.takeIf { monitor.has("autoCalEnabled") && !monitor.isNull("autoCalEnabled") }
            val count = monitor?.optInt("autoMatchCount", -1)?.takeIf { it >= 0 }
            val max = monitor?.optInt("maxAutomatch", -1)?.takeIf { it > 0 && !monitor.isNull("maxAutomatch") }
            if (ecuOnline && count != null) {
                if (lastCount != null && count != lastCount) { quietMs = 0L; ecuDoneLatch = null; dirty = true }
                else { quietMs += dt; if (dt > 0) dirty = true }
                if (lastCount != count) dirty = true
                lastCount = count
            }
            // Pontos da ECU com atividade (os que o gráfico desenha) e zonas que a ECU deu como adquiridas.
            val petrolValid = activeCount(acquisition, "GASOLINA")
            val gasValid = activeCount(acquisition, "GNV")
            val petrolZones = acquiredZones(acquisition, "GASOLINA")
            val gasZones = acquiredZones(acquisition, "GNV")
            val complete = petrolZones >= 4 && gasZones >= 4
            val fresh = when {
                enabled == 0 -> "AUTOCAL_DESLIGADO"
                max != null && count != null && count >= max -> "MAX_AUTOMATCH"
                complete && quietMs >= QUIET_MS -> "AQUISICAO_COMPLETA"
                petrolZones >= PARTIAL_MIN_ZONES && gasZones >= PARTIAL_MIN_ZONES && quietMs >= QUIET_PARTIAL_MS -> "SEM_AUTOMATICO_NOVO"
                else -> null
            }
            if (fresh != null && fresh != ecuDoneLatch) { ecuDoneLatch = fresh; dirty = true }
            val ecuReason = ecuDoneLatch
            val out = JSONObject()
                .put("ecuOnline", ecuOnline)
                .put("autoMatchCount", count ?: JSONObject.NULL)
                .put("maxAutomatch", max ?: JSONObject.NULL)
                .put("autoCalEnabled", enabled ?: JSONObject.NULL)
                .put("petrolValid", petrolValid)
                .put("gasValid", gasValid)
                .put("petrolZones", petrolZones)
                .put("gasZones", gasZones)
                .put("quietMinutes", quietMs / 60_000.0)
                .put("ecuDone", ecuReason != null)
                .put("ecuDoneReason", ecuReason ?: JSONObject.NULL)
                .put("ourPoints", index.optInt("samples", 0))
                .put("petrolObservations", index.optInt("petrolObservations", 0))
                .put("gasObservations", index.optInt("gasObservations", 0))
                .put("automatic", false)

            // Faixas com histerese: entra "fora" acima de ±3% (limiar de sempre) e só sai abaixo de 2%.
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
                    val error = abs(ln(r))
                    val latched = if (i < offLatch.size) {
                        if (error > TOLERANCE_LOG) offLatch[i] = true else if (error <= TOLERANCE_EXIT_LOG) offLatch[i] = false
                        offLatch[i]
                    } else error > TOLERANCE_LOG
                    if (latched) off.put(label)
                } else {
                    if (i < offLatch.size) offLatch[i] = false
                    missing.put(label)
                }
            }
            out.put("bandsMeasured", measured.size).put("bandsOff", off).put("bandsMissing", missing)
            val stable = measured.size >= MIN_STABLE_BANDS && off.length() == 0

            val latest = journal.optJSONObject("latest")
            val latestStatus = latest?.optString("status").orEmpty()
            val latestId = latest?.optString("id").orEmpty()
            val verifying = latestStatus == RefinementJournal.STATUS_VERIFYING ||
                (journal.optJSONArray("measuringBands")?.length() ?: 0) > 0
            // Gravação nova: a proposta anterior foi feita sobre a curva antiga e sai na hora.
            if (latestId != lastJournalId) { heldProposal = null; emptyStreak = 0; lastJournalId = latestId }
            if (proposal != null && proposal.optBoolean("known", false)) {
                if (proposal.optInt("count", 0) > 0) { heldProposal = JSONObject(proposal.toString()); emptyStreak = 0 }
                else if (++emptyStreak >= PROPOSAL_EXIT_EMPTY_TICKS) heldProposal = null
            }
            val proposalCount = heldProposal?.optInt("count", 0) ?: 0

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
                !stable && proposalCount > 0 -> "PROPOSTA_PRONTA"
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
            val open = journal.optJSONObject("verifying")
            val verification = if (verifying && open != null) JSONObject()
                .put("bandsLeft", open.optInt("bandsLeft", 0))
                .put("drivingMinutes", open.optLong("drivingMs", 0L) / 60_000.0)
                .put("budgetMinutes", open.optLong("budgetMs", RefinementJournal.VERIFY_PARTIAL_ONLINE_MS) / 60_000.0)
                .put("giveUpMinutes", open.optLong("giveUpMs", RefinementJournal.VERIFY_GIVE_UP_ONLINE_MS) / 60_000.0)
            else null
            out.put("phase", phase)
                .put("canDisconnect", phase == "ESTAVEL")
                .put("action", action)
                .put("secondary", secondary)
                .put("proposal", heldProposal?.let { JSONObject(it.toString()) } ?: JSONObject()
                    .put("known", proposal?.optBoolean("known", false) ?: false).put("count", 0).put("points", JSONArray())
                    .put("reason", proposal?.optString("reason") ?: "MOTOR_NAO_CONSULTADO"))
                .put("result", JSONObject().put("improved", improved).put("worse", worse).put("measuring", measuring)
                    .put("id", resultJson?.optString("id") ?: JSONObject.NULL))
                .put("headline", headline(phase, count, max, measured.size, off.length(), proposalCount, restoreCount,
                    improved, worse, measuring, verification, out))
                .put("next", nextStep(phase, missing, index, proposalCount, measuring, verifying))
            if (verification != null) out.put("verification", verification)
            if (latest != null) out.put("journalStatus", latestStatus)
            last = out
            out
        }
        save()
        return JSONObject(result.toString())
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

    fun json(): JSONObject = synchronized(lock) { JSONObject(last.toString()) }

    /** Estados de banda com dado real (Platina: ZONA_ADQUIRIDA/ATIVIDADE; formato antigo: VALIDO/COLETANDO). */
    private val activeStates = setOf("ZONA_ADQUIRIDA", "ATIVIDADE", "VALIDO", "COLETANDO")

    private fun activeCount(acquisition: JSONObject?, fuel: String): Int {
        val points = acquisition?.optJSONArray("points") ?: return 0
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

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    /**
     * "faltam 2 faixas · 6 de 15 min rodando" (parado ou em marcha lenta o tempo não anda). Passado o prazo
     * sem nenhuma faixa medida: diz em quanto tempo rodando o resultado fecha sozinho.
     */
    private fun progress(v: JSONObject): String {
        val left = v.optInt("bandsLeft", 0)
        val minutes = Math.floor(v.optDouble("drivingMinutes", 0.0)).toInt()
        val budget = Math.round(v.optDouble("budgetMinutes", 15.0)).toInt()
        val giveUp = Math.round(v.optDouble("giveUpMinutes", 40.0)).toInt()
        return when {
            left > 0 -> "faltam $left ${plural(left, "faixa", "faixas")} · $minutes de $budget min rodando"
            minutes >= budget -> "sem leituras novas nas faixas gravadas · fecha sozinho em até ${(giveUp - minutes).coerceAtLeast(1)} min rodando"
            else -> "fechando o resultado · $minutes de $budget min rodando"
        }
    }

    private fun headline(
        phase: String, count: Int?, max: Int?, measured: Int, off: Int, proposal: Int, restore: Int,
        improved: Int, worse: Int, measuring: Int, verification: JSONObject?, out: JSONObject,
    ): String = when (phase) {
        "SEM_ECU" -> "Conecte a ECU para acompanhar a calibração."
        "ECU_TRABALHANDO" -> "A ECU está no automático" +
            (if (count != null) " $count" + (if (max != null) " de $max" else "") else "") +
            ". O OMEGAS observa e junta pontos próprios (${out.optInt("ourPoints")} até agora)."
        "COLETANDO_NOSSOS" -> if (off > 0) "$off ${plural(off, "faixa", "faixas")} fora da gasolina, ainda sem proposta do motor. O OMEGAS segue medindo."
            else "A ECU terminou. Agora o OMEGAS junta pontos GNV × gasolina no mesmo RPM e MAP."
        "PROPOSTA_PRONTA" -> "Curva refinada pronta: $proposal ${plural(proposal, "ponto", "pontos")} para ajustar" +
            (if (off > 0) " ($off de $measured faixas fora da gasolina)." else ".") +
            (verification?.let { " Gravação anterior: ${progress(it)}." } ?: "")
        "VERIFICANDO" -> "Medindo a gravação: " + (verification?.let { progress(it) } ?: "aguardando leituras") +
            (if (improved + worse > 0) ". Até agora $improved ${plural(improved, "melhorou", "melhoraram")} e $worse ${plural(worse, "piorou", "pioraram")}." else ".")
        "RESTAURAR_TRECHO" -> "$restore ${plural(restore, "ponto piorou", "pontos pioraram")} com a última gravação. " +
            "Restaure só ${plural(restore, "esse ponto", "esses pontos")}; os que melhoraram ficam."
        "ESTAVEL" -> "GNV equivalente à gasolina em $measured faixas (±3%). Pode desconectar."
        else -> ""
    }

    private fun nextStep(phase: String, missing: JSONArray, index: JSONObject, proposal: Int, measuring: Int, verifying: Boolean): String = when (phase) {
        "SEM_ECU" -> "Ligue o cabo e o motor."
        "ECU_TRABALHANDO" -> "Dirija normalmente nos dois combustíveis. A gravação libera quando a ECU terminar o automático."
        "COLETANDO_NOSSOS", "VERIFICANDO" -> {
            val wanted = (0 until missing.length()).mapNotNull { missing.optJSONObject(it) }
                .joinToString(", ") { "%.1f–%.1f ms".format(it.optDouble("fromMs"), it.optDouble("toMs")) }
            when {
                index.optInt("petrolObservations") < 40 -> "Rode alguns minutos na gasolina para criar a referência."
                wanted.isNotEmpty() -> "Rode no GNV passando por cargas de injeção $wanted." +
                    (if (verifying) " Se não passar por lá, o OMEGAS fecha a medição em até 15 min rodando (parado não conta)." else "")
                verifying && measuring > 0 -> "Continue rodando no GNV: faltam poucas leituras para fechar o resultado."
                else -> "Continue rodando no GNV."
            }
        }
        "PROPOSTA_PRONTA" -> "Toque em Revisar e gravar. Nada é gravado sem você confirmar; a ECU confere por readback."
        "RESTAURAR_TRECHO" -> "Restaurar volta só esses pontos ao valor anterior, com readback. Sem restaurar, o OMEGAS segue medindo e a oferta some se a faixa voltar para dentro da margem." +
            (if (proposal > 0) " Os outros $proposal ${plural(proposal, "ponto", "pontos")} podem ser revisados e gravados agora." else "")
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
            ecuDoneLatch = if (root.isNull("ecuDoneLatch")) null else root.optString("ecuDoneLatch").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
        }
    }
}
