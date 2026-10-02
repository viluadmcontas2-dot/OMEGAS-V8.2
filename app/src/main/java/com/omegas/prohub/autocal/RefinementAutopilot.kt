package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/**
 * Piloto do refino: decide em que fase a calibração está, sem ninguém olhar a tela.
 *
 * 1. ECU_TRABALHANDO — o AutoCal nativo ainda está no automático 1, 2, 3… e pedindo
 *    aquisição. O OMEGAS só observa e junta pontos próprios (RPM×MAP) em paralelo:
 *    gravar agora seria sobrescrito pelo próximo automático da ECU.
 * 2. A ECU parou (atingiu MAX_AUTOMATCH, AutoCal desligado/congelado, ou aquisição
 *    completa e sem automático novo há [QUIET_MS] de ECU online): é a nossa vez.
 *    - COLETANDO_NOSSOS: faltam leituras GNV/gasolina no mesmo RPM×MAP.
 *    - PROPOSTA_PRONTA: alguma faixa de condução está fora de ±[TOLERANCE_LOG]; o
 *      refino (pontos da ECU + nossos pontos) tem o que corrigir. Gravação manual.
 *    - VERIFICANDO: curva nova gravada; medindo se chegou na gasolina.
 *    - RESTAURAR_TRECHO: uma faixa piorou; restaurar só aquele trecho.
 *    - ESTAVEL: todas as faixas medidas dentro de ±3%: pode desconectar.
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
        /** ±3%: abaixo disso GNV e gasolina já pedem o mesmo (ruído de medição ~2%). */
        val TOLERANCE_LOG = ln(1.03)
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
    private var last: JSONObject = JSONObject().put("phase", phase)

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
                    if (abs(ln(r)) > TOLERANCE_LOG) off.put(label)
                } else missing.put(label)
            }
            out.put("bandsMeasured", measured.size).put("bandsOff", off).put("bandsMissing", missing)

            val latest = journal.optJSONObject("latest")
            val latestStatus = latest?.optString("status").orEmpty()
            val next = when {
                !ecuOnline && lastCount == null -> "SEM_ECU"
                ecuReason == null -> "ECU_TRABALHANDO"
                latestStatus == "VERIFICANDO" -> "VERIFICANDO"
                latestStatus == "PIOROU_EM_PARTE" && restoreCount > 0 -> "RESTAURAR_TRECHO"
                measured.size >= MIN_STABLE_BANDS && off.length() == 0 -> "ESTAVEL"
                off.length() > 0 && measured.size >= 2 -> "PROPOSTA_PRONTA"
                else -> "COLETANDO_NOSSOS"
            }
            if (next != phase) { phase = next; dirty = true; lastSaveAt = Long.MIN_VALUE / 2 }
            out.put("phase", phase)
                .put("canDisconnect", phase == "ESTAVEL")
                .put("headline", headline(phase, count, max, measured.size, off.length(), out))
                .put("next", nextStep(phase, petrolValid, gasValid, missing, index))
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

    private fun headline(phase: String, count: Int?, max: Int?, measured: Int, off: Int, out: JSONObject): String = when (phase) {
        "SEM_ECU" -> "Conecte a ECU para acompanhar a calibração."
        "ECU_TRABALHANDO" -> "A ECU está no automático" +
            (if (count != null) " ${count}" + (if (max != null) " de $max" else "") else "") +
            ". O OMEGAS observa e junta pontos próprios (${out.optInt("ourPoints")} até agora)."
        "COLETANDO_NOSSOS" -> "A ECU terminou. Agora o OMEGAS junta pontos GNV × gasolina no mesmo RPM e MAP."
        "PROPOSTA_PRONTA" -> "Curva refinada pronta: $off de $measured faixas fora da gasolina. Revise e grave."
        "VERIFICANDO" -> "Curva nova gravada. Dirija normalmente: o OMEGAS mede se o GNV chegou na gasolina."
        "RESTAURAR_TRECHO" -> "Um trecho piorou com a curva nova. Restaure só esse trecho."
        "ESTAVEL" -> "GNV equivalente à gasolina em $measured faixas (±3%). Pode desconectar."
        else -> ""
    }

    private fun nextStep(phase: String, petrolValid: Int, gasValid: Int, missing: JSONArray, index: JSONObject): String = when (phase) {
        "SEM_ECU" -> "Ligue o cabo e o motor."
        "ECU_TRABALHANDO" -> "Dirija normalmente nos dois combustíveis. A gravação libera quando a ECU terminar o automático."
        "COLETANDO_NOSSOS", "VERIFICANDO" -> {
            val wanted = (0 until missing.length()).mapNotNull { missing.optJSONObject(it) }
                .joinToString(", ") { "%.1f–%.1f ms".format(it.optDouble("fromMs"), it.optDouble("toMs")) }
            when {
                index.optInt("petrolObservations") < 40 -> "Rode alguns minutos na gasolina para criar a referência."
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
