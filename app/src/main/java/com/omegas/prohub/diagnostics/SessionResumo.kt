package com.omegas.prohub.diagnostics

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * RESUMO.md de uma sessão, em português simples: fases do piloto do Refino, apagões (e o que veio
 * depois), gravações na ECU e veredictos do refino. Puro: sem Android, relógio e fuso injetáveis.
 *
 * É montado só com eventos já gravados na sessão (`refinement_phase`, `engine_stall`,
 * `engine_stall_after`, `k_*_batch_confirmed`, `refinement_verdict`, `autocal_native_action`).
 * Por isso uma sessão interrompida (app morto, energia cortada) é reconstruída do mesmo jeito
 * a partir dos `events_*.jsonl` (`rebuildIfOpen`) e o resumo nunca depende de memória que morreu.
 */
class SessionResumo(
    private val sessionId: String,
    private var startedAtMs: Long,
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    companion object {
        const val FILE_NAME = "RESUMO.md"
        const val CLOSED_MARK = "Estado: FECHADA"
        const val OPEN_MARK = "Estado: NÃO FECHADA"

        /** Eventos que mudam o resumo; o resto da telemetria nunca o toca. */
        val TRACKED = setOf(
            "refinement_phase", "refinement_decision", "refinement_diagnostic", "engine_stall", "engine_stall_after", "refinement_verdict",
            "k_batch_confirmed", "k_factor_batch_confirmed", "autocal_native_action",
            "session_started", "session_stopped",
        )

        /** Um quase-apagão que vira apagão dentro dessa janela é o mesmo acontecimento. */
        private const val SAME_EVENT_MS = 5_000L

        private val PHASE_WORDS = mapOf(
            "SEM_ECU" to "sem ECU conectada",
            "LENDO_ECU" to "lendo a ECU",
            "TENTATIVA_ENCERRADA" to "tentativa encerrada, aguardando dados novos",
            "ECU_TRABALHANDO" to "ECU trabalhando no automático",
            "COLETANDO_NOSSOS" to "coletando os pontos do OMEGAS",
            "PROPOSTA_PRONTA" to "curva pronta para revisar",
            "VERIFICANDO" to "verificando a última gravação",
            "RESTAURAR_TRECHO" to "restaurar um trecho piorou",
            "ESTAVEL" to "curva estável",
        )
        private val AFTER_WORDS = mapOf(
            "RELIGOU" to "o motor religou",
            "TELEMETRIA_PAROU" to "a telemetria parou (chave ou cabo)",
            "SEM_RELIGAR" to "o motor não religou",
        )
        private val VERDICT_WORDS = mapOf(
            "VERIFICADO" to "verificação concluída (resultado por faixa abaixo)",
            "PIOROU_EM_PARTE" to "piorou em parte (trecho a restaurar)",
            "SEM_BASE" to "sem medida anterior para comparar",
            "INCONCLUSIVO" to "inconclusiva (pouca condução)",
            "INTERROMPIDO" to "interrompida antes de concluir",
            "VERIFICANDO" to "ainda verificando",
        )

        /**
         * Sessão que morreu sem fechar: refaz o RESUMO.md só com o que está nos arquivos de eventos.
         * Retorna true quando reescreveu o arquivo; sessão já fechada não é tocada.
         */
        fun rebuildIfOpen(dir: File, zone: TimeZone = TimeZone.getDefault()): Boolean {
            val existing = File(dir, FILE_NAME)
            if (existing.isFile && existing.readText(Charsets.UTF_8).contains(CLOSED_MARK)) return false
            val manifest = try { JSONObject(File(dir, "manifest.json").readText(Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
            val started = manifest.optLong("startedAtMs", manifest.optLong("createdAtMs", dir.lastModified()))
            val resumo = SessionResumo(manifest.optString("sessionId", dir.name), started, zone)
            var lastAt = started
            val segments = dir.listFiles { f -> f.name.startsWith("events_") && f.name.endsWith(".jsonl") }
                ?.sortedBy { it.name }.orEmpty()
            for (segment in segments) {
                segment.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        // Filtro barato antes de interpretar: telemetria é 99% das linhas.
                        if (TRACKED.none { line.contains("\"type\":\"$it\"") }) continue
                        val item = try { JSONObject(line) } catch (_: Exception) { continue }
                        val at = item.optLong("recordedAtMs", 0L)
                        if (at > lastAt) lastAt = at
                        resumo.observe(item.optString("type"), item.optJSONObject("data") ?: JSONObject(), at)
                    }
                }
            }
            if (!resumo.stopped) resumo.abandonedAtMs = lastAt
            write(dir, resumo.markdown())
            return true
        }

        fun write(dir: File, markdown: String) {
            val target = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(markdown, Charsets.UTF_8)
            if (!tmp.renameTo(target)) { target.writeText(markdown, Charsets.UTF_8); tmp.delete() }
        }
    }

    private class Phase(val atMs: Long, val phase: String, val headline: String)
    private class Stall(
        var kind: String, var atMs: Long, val petrolMs: Double, val mapBar: Double,
        val rpmBefore: Double, val rpmMin: Double, val speedKmh: Double?,
    ) { var after: String? = null; var restartedInS: Double? = null }
    private class Write(val atMs: Long, val kind: String, val points: Int, val id: String)
    private class Verdict(
        val atMs: Long, val id: String, val status: String, val appliedAtMs: Long,
        val ratioBefore: Double?, val ratioAfter: Double?, val bands: List<Pair<String, String>>,
    )

    private val phases = ArrayList<Phase>()
    private val stalls = ArrayList<Stall>()
    private val writes = ArrayList<Write>()
    private val verdicts = ArrayList<Verdict>()
    private val actions = LinkedHashMap<String, Int>()
    private val diagnostics = ArrayList<Pair<Long, JSONObject>>()
    private val decisions = ArrayList<Pair<Long, JSONObject>>()
    private var stopped = false
    private var stoppedAtMs = 0L
    private var stopReason = ""
    private var abandonedAtMs = 0L

    private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = zone }.format(Date(ms))
    private fun day(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).apply { timeZone = zone }.format(Date(ms))
    private fun num(value: Double, digits: Int) = String.format(Locale.forLanguageTag("pt-BR"), "%.${digits}f", value)
    private fun optDouble(data: JSONObject, key: String): Double? =
        if (data.has(key) && !data.isNull(key)) data.optDouble(key).takeIf { it.isFinite() } else null

    /** Retorna true quando o resumo mudou e o arquivo precisa ser regravado. */
    fun observe(type: String, data: JSONObject, atMs: Long): Boolean {
        when (type) {
            "session_started" -> { if (atMs > 0L) startedAtMs = atMs; return false }
            "session_stopped" -> {
                stopped = true; stoppedAtMs = atMs; stopReason = data.optString("reason", "manual")
                return true
            }
            "refinement_phase" -> {
                val phase = data.optString("phase")
                if (phase.isBlank() || phases.lastOrNull()?.phase == phase) return false
                phases += Phase(atMs, phase, data.optString("headline"))
            }
            "refinement_decision", "refinement_diagnostic" -> {
                val entry = atMs to JSONObject(data.toString())
                if (type == "refinement_diagnostic") diagnostics += entry else decisions += entry
                // O JSONL mantém o histórico completo; o resumo tem orçamento de memória.
                if (diagnostics.size > 100) diagnostics.removeAt(0)
                if (decisions.size > 200) decisions.removeAt(0)
            }
            "engine_stall" -> {
                val kind = data.optString("kind")
                val at = data.optLong("at", atMs)
                val last = stalls.lastOrNull()
                val event = Stall(
                    kind, at, data.optDouble("petrolMs"), data.optDouble("mapBar"),
                    data.optDouble("rpmBefore"), data.optDouble("rpmMin"), optDouble(data, "speedKmh"),
                )
                if (last != null && last.kind == "QUASE_APAGOU" && kind == "APAGOU" && at - last.atMs <= SAME_EVENT_MS) {
                    last.kind = kind; last.atMs = at // o quase-apagão virou apagão: mesmo acontecimento
                } else stalls += event
            }
            "engine_stall_after" -> {
                val at = data.optLong("at", -1L)
                val target = stalls.lastOrNull { it.atMs == at } ?: return false
                target.after = data.optString("depois")
                target.restartedInS = optDouble(data, "religouEmS")
            }
            "k_batch_confirmed" -> writes += Write(atMs, "Mapa K", data.optJSONArray("cells")?.length() ?: 0, data.optString("adjustmentId"))
            "k_factor_batch_confirmed" -> writes += Write(atMs, "Curva K", data.optJSONArray("points")?.length() ?: 0, data.optString("adjustmentId"))
            "refinement_verdict" -> {
                val id = data.optString("id")
                if (verdicts.any { it.id == id }) return false
                val bands = ArrayList<Pair<String, String>>()
                val array = data.optJSONArray("bands")
                if (array != null) for (i in 0 until array.length()) {
                    val band = array.optJSONObject(i) ?: continue
                    val verdict = band.optString("verdict")
                    if (verdict == "NAO_ALTERADA") continue
                    bands += "${num(band.optDouble("fromMs"), 1)} a ${num(band.optDouble("toMs"), 1)} ms" to verdict
                }
                verdicts += Verdict(
                    atMs, id, data.optString("status"), data.optLong("appliedAt"),
                    optDouble(data, "ratioBefore"), optDouble(data, "ratioAfter"), bands,
                )
            }
            "autocal_native_action" -> {
                val action = data.optString("action").ifBlank { data.optString("type") }
                if (action.isBlank()) return false
                actions[action] = (actions[action] ?: 0) + 1
            }
            else -> return false
        }
        return true
    }

    fun markdown(): String {
        val out = StringBuilder()
        out.appendLine("# Resumo da sessão $sessionId")
        out.appendLine()
        if (stopped) out.appendLine("$CLOSED_MARK ($stopReason)")
        else out.appendLine("$OPEN_MARK: o app foi encerrado, a energia cortou ou a sessão ainda está gravando.")
        out.appendLine("Início: ${day(startedAtMs)}")
        val end = when { stopped -> stoppedAtMs; abandonedAtMs > 0L -> abandonedAtMs; else -> 0L }
        if (end > 0L) {
            val minutes = ((end - startedAtMs) / 60_000L).coerceAtLeast(0L)
            out.appendLine("${if (stopped) "Fim" else "Último registro"}: ${day(end)} ($minutes min)")
        }
        out.appendLine()

        out.appendLine("## Fases do piloto do Refino")
        if (phases.isEmpty()) out.appendLine("- Nenhuma mudança de fase nesta sessão.")
        phases.forEach { out.appendLine("- ${clock(it.atMs)} ${PHASE_WORDS[it.phase] ?: it.phase}${if (it.headline.isNotBlank()) ": ${it.headline}" else ""}") }
        out.appendLine()

        val real = stalls.filter { it.kind == "APAGOU" }
        val near = stalls.filter { it.kind == "QUASE_APAGOU" }
        out.appendLine("## Apagões do motor")
        out.appendLine("- Apagou: ${real.size} (religou: ${real.count { it.after == "RELIGOU" }}). Quase apagou: ${near.size}.")
        stalls.forEach { s ->
            val kind = if (s.kind == "APAGOU") "APAGOU" else "quase apagou"
            val speed = s.speedKmh?.let { ", ${num(it, 0)} km/h" } ?: ""
            val after = s.after?.let { a ->
                val seconds = s.restartedInS?.let { " em ${num(it, 1)} s" } ?: ""
                "; depois: ${AFTER_WORDS[a] ?: a}$seconds"
            } ?: if (s.kind == "APAGOU") "; depois: sem anotação (a sessão acabou antes)" else ""
            out.appendLine("- ${clock(s.atMs)} $kind a ${num(s.petrolMs, 2)} ms, MAP ${num(s.mapBar, 2)} bar, ${num(s.rpmBefore, 0)} rpm antes$speed$after")
        }
        out.appendLine()

        out.appendLine("## Gravações na ECU")
        if (writes.isEmpty()) out.appendLine("- Nenhuma gravação confirmada nesta sessão.")
        writes.forEach { out.appendLine("- ${clock(it.atMs)} ${it.kind}: ${it.points} pontos, ajuste ${it.id.ifBlank { "sem id" }} (confirmado por ACK e readback)") }
        out.appendLine()

        out.appendLine("## Veredictos do Refino")
        if (verdicts.isEmpty()) out.appendLine("- Nenhuma verificação terminou nesta sessão.")
        verdicts.forEach { v ->
            val ratio = if (v.ratioBefore != null && v.ratioAfter != null)
                " (razão GNV/gasolina ${num(v.ratioBefore, 3)} para ${num(v.ratioAfter, 3)})" else ""
            out.appendLine("- ${clock(v.atMs)} gravação das ${clock(v.appliedAtMs)}: ${VERDICT_WORDS[v.status] ?: v.status}$ratio")
            v.bands.forEach { (band, verdict) -> out.appendLine("  - faixa $band: ${verdict.lowercase().replace('_', ' ')}") }
        }
        out.appendLine()

        out.appendLine("## Decisões do Refino (motivos e números)")
        if (decisions.isEmpty()) out.appendLine("- Nenhuma decisão registrada nesta sessão.")
        decisions.forEach { (at, d) ->
            out.appendLine("- ${clock(at)} ${d.optString("reasonCode")} — ${d.optString("headline")}; números: ${d.optJSONObject("diagnostic") ?: JSONObject()}")
        }
        out.appendLine()
        out.appendLine("## O que aconteceu de estranho")
        if (diagnostics.isEmpty()) out.appendLine("- Nenhuma anomalia registrada nesta sessão.")
        diagnostics.forEach { (at, d) ->
            val domain = if (d.optString("failureDomain") == "TRANSPORT") "transporte USB/ECU" else "avaliação funcional"
            out.appendLine("- ${clock(at)} ${d.optString("headline")} [${d.optString("reasonCode")}, $domain]; números: ${d.optJSONObject("diagnostic") ?: JSONObject()}")
        }
        out.appendLine()
        out.appendLine("## Ações da ECU observadas")
        if (actions.isEmpty()) out.appendLine("- Nenhuma ação nativa registrada.")
        actions.forEach { (action, count) -> out.appendLine("- $action: $count") }
        return out.toString()
    }
}
