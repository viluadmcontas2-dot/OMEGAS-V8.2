package com.omegas.prohub.autocal

import com.omegas.prohub.util.Units
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Engasgo repetido na mesma região = ajuste LOCAL da Curva K, nunca "o carro quase morreu" sem solução.
 *
 * Cada região do [StallWatch] (faixa de Petrol Inj., com MAP, RPM e quantas vezes) ganha:
 *  - `curvePoints`: os pontos da Curva K que cobrem a região (os dois nós que interpolam aquele ms, e os nós dentro dela);
 *  - `diagnosis`: a direção (POBRE = falta combustível no GNV → K sobe; RICO → K desce) lida da EVIDÊNCIA do cérebro nesses
 *    pontos (GNV × gasolina por RPM×MAP). Sem evidência julgada a direção é DESCONHECIDA: nada se inventa e a frase pede mais
 *    coleta;
 *  - `proposal` (só com ≥ [MIN_REPEATS] engasgos desde a última gravação, direção determinada e passo possível): o delta de K por
 *    ponto, limitado a ±[MAX_STEP] (8%) do K atual e a [K_MIN, K_MAX] (0,75..1,20) e respeitando a trava da baixa
 *    (nunca empobrece abaixo de [AutoMatchSnapshotAnalysis.LOW_GUARD_MS]).
 *
 * `localProposal` (no nível de `stalls`) tem a forma do `proposal` do cérebro (`currentRaw`/`refinedRaw`, 30 inteiros) para a
 * UI aplicar com o mesmo toque de sempre (foto antes, Desfazer depois, readback). Nada aqui grava: só o dono, por um toque.
 */
object StallLocalFix {
    const val MIN_REPEATS = 2
    /** Passo máximo por proposta: ±8% do K atual (menor que o passo do motor refinado, 15%). */
    const val MAX_STEP = 0.08
    const val K_MIN = AutoMatchRefinedEngine.MIN_FACTOR
    const val K_MAX = AutoMatchRefinedEngine.MAX_FACTOR
    /** Abaixo disto a mudança não é passo, é ruído (0,5%). */
    private const val MIN_DELTA = 0.005
    private const val Q14 = AutoMatchRefinedEngine.Q14

    const val DIR_POOR = "POBRE"
    const val DIR_RICH = "RICO"
    const val DIR_UNKNOWN = "DESCONHECIDA"

    private fun isJudged(state: String) = state == "POBRE" || state == "RICO" || state == "EQUIVALENTE"

    /** [sinceMs]: só engasgos depois dele contam para propor (o dono já gravou a Curva K nesse instante). */
    fun enrich(stalls: JSONObject, equivalence: JSONObject?, sinceMs: Long = 0L): JSONObject {
        val out = JSONObject(stalls.toString())
        val regions = out.optJSONArray("regions") ?: JSONArray().also { out.put("regions", it) }
        val points = equivalence?.takeIf { it.optBoolean("available", false) }?.optJSONArray("points")
        var best: JSONObject? = null
        var bestRegion = -1
        for (r in 0 until regions.length()) {
            val region = regions.optJSONObject(r) ?: continue
            val covering = coveringPoints(region, points)
            region.put("curvePoints", JSONArray(covering.map { it.optInt("index") }))
            val ats = region.optJSONArray("ats")
            val recent = if (ats == null) region.optInt("count") else (0 until ats.length()).count { ats.optLong(it) > sinceMs }
            region.put("recentCount", recent)
            val diagnosis = diagnose(region, covering, recent)
            region.put("diagnosis", diagnosis.json)
            diagnosis.proposal?.let { region.put("proposal", it) }
            if (diagnosis.proposal != null && (best == null || region.optInt("count") > best.optInt("count"))) { best = region; bestRegion = r }
        }
        if (best != null && points != null) {
            val proposal = best.getJSONObject("proposal")
            val current = IntArray(30) { i -> (0 until points.length()).map { points.getJSONObject(it) }.firstOrNull { it.optInt("index") == i }
                ?.let { (it.optDouble("kCurrent") * Q14).roundToInt() } ?: 0 }
            if (current.all { it > 0 }) {
                val refined = current.copyOf()
                val items = proposal.getJSONArray("points")
                for (i in 0 until items.length()) refined[items.getJSONObject(i).getInt("index")] = items.getJSONObject(i).getInt("kAfterRaw")
                out.put("localProposal", JSONObject()
                    .put("mode", "STALL_LOCAL")
                    .put("regionIndex", bestRegion)
                    .put("pointIndexes", proposal.getJSONArray("pointIndexes"))
                    .put("currentRaw", JSONArray(current.toList()))
                    .put("refinedRaw", JSONArray(refined.toList()))
                    .put("text", proposal.getString("text"))
                    .put("automatic", false))
            }
        }
        if (!out.has("localProposal")) out.put("localProposal", JSONObject.NULL)
        return out
    }

    private fun coveringPoints(region: JSONObject, points: JSONArray?): List<JSONObject> {
        if (points == null || points.length() == 0) return emptyList()
        val list = (0 until points.length()).mapNotNull { points.optJSONObject(it) }.sortedBy { it.optDouble("axisMs") }
        val from = region.optDouble("fromMs")
        val to = region.optDouble("toMs")
        val inside = list.filter { it.optDouble("axisMs") in from..to }
        val below = list.lastOrNull { it.optDouble("axisMs") <= from }
        val above = list.firstOrNull { it.optDouble("axisMs") >= to }
        return (inside + listOfNotNull(below, above)).distinctBy { it.optInt("index") }.sortedBy { it.optInt("index") }
    }

    private class Diagnosis(val json: JSONObject, val proposal: JSONObject?)

    private fun where(region: JSONObject): String {
        val ms = region.optDouble("ms", region.optDouble("fromMs"))
        val map = region.optDouble("mapBar", Double.NaN)
        val rpm = region.optDouble("rpm", Double.NaN)
        return buildString {
            append(Units.msUnit(ms))
            if (map.isFinite()) append(" · ").append(Units.mapUnit(map))
            if (rpm.isFinite() && rpm > 0.0) append(" · ").append(Units.rpm(rpm)).append(" rpm")
        }
    }

    private fun times(n: Int) = if (n == 1) "1 engasgo" else "$n engasgos"

    private fun diagnose(region: JSONObject, covering: List<JSONObject>, recent: Int): Diagnosis {
        val place = where(region)
        val count = region.optInt("count")
        fun result(direction: String, text: String, reason: String, proposal: JSONObject? = null) = Diagnosis(
            JSONObject().put("direction", direction).put("text", text).put("technicalReason", reason), proposal,
        )
        if (covering.isEmpty()) return result(DIR_UNKNOWN, "Região $place com ${times(count)}; ainda não li a Curva K para saber quais pontos cobrem.", "SEM_CURVA")
        val judged = covering.filter { isJudged(it.optString("state")) && !it.isNull("mixture") }
        val off = judged.filter { it.optString("state") != "EQUIVALENTE" }
        if (judged.isEmpty()) {
            return result(DIR_UNKNOWN, "Região $place com ${times(count)}; faltam dados para saber se é rico ou pobre. Siga dirigindo no GNV por aí.", "SEM_EVIDENCIA_JULGADA")
        }
        if (off.isEmpty()) {
            return result(DIR_UNKNOWN, "Região $place com ${times(count)}; a mistura do GNV já está igual à gasolina aí. O engasgo parece vir de tranco ou embreagem, não da Curva K; sigo observando.", "TODOS_EQUIVALENTES")
        }
        val poor = off.filter { it.optString("state") == "POBRE" }
        val rich = off.filter { it.optString("state") == "RICO" }
        if (poor.isNotEmpty() && rich.isNotEmpty()) {
            return result(DIR_UNKNOWN, "Região $place com ${times(count)}; os pontos da região discordam (um pobre, outro rico). Preciso de mais leitura antes de propor.", "DIRECOES_DISCORDAM")
        }
        val direction = if (poor.isNotEmpty()) DIR_POOR else DIR_RICH
        val directionText = if (direction == DIR_POOR) "pobre (falta combustível no GNV)" else "rico"
        if (recent < MIN_REPEATS) {
            return result(direction, "Região $place com ${times(count)}; a mistura está $directionText aí. Se repetir, proponho um ajuste local.", "POUCOS_ENGASGOS")
        }
        // Passo local: cada ponto fora vai ao alvo do cérebro, limitado a ±8% do K atual, a 0,75..1,20 e à trava da baixa.
        val items = JSONArray()
        var blocked = false
        for (p in off) {
            val k = p.optDouble("kCurrent")
            val delta = p.optDouble("mixture").coerceIn(-MAX_STEP, MAX_STEP)
            var after = (k * (1.0 + delta)).coerceIn(K_MIN, K_MAX)
            val axis = p.optDouble("axisMs")
            if (after < k && axis < AutoMatchSnapshotAnalysis.LOW_GUARD_MS) { blocked = true; continue } // nunca empobrece na baixa
            val appliedDelta = after / k - 1.0
            if (abs(appliedDelta) < MIN_DELTA) continue
            val raw = (after * Q14).roundToInt().coerceIn(AutoMatchRefinedEngine.MIN_RAW_PROPOSAL, AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
            after = raw / Q14
            items.put(JSONObject().put("index", p.optInt("index")).put("axisMs", p.optDouble("axisMs"))
                .put("kBefore", k).put("kAfter", after).put("kAfterRaw", raw)
                .put("deltaPct", Math.round((after / k - 1.0) * 1000.0) / 10.0))
        }
        if (items.length() == 0) {
            val why = if (blocked) "a trava da baixa não deixa empobrecer ali" else "o passo seria pequeno demais"
            return result(direction, "Região $place com ${times(count)}; a mistura está $directionText, mas $why. Sigo observando.", if (blocked) "TRAVA_DA_BAIXA" else "PASSO_PEQUENO")
        }
        val indexes = JSONArray((0 until items.length()).map { items.getJSONObject(it).getInt("index") })
        val verb = if (direction == DIR_POOR) "enriquecer" else "empobrecer"
        val biggest = (0 until items.length()).map { items.getJSONObject(it) }.maxByOrNull { abs(it.getDouble("deltaPct")) }!!
        val n = items.length()
        val text = "Região $place com ${times(count)}: GNV $directionText. Ajuste local: $verb ${if (n == 1) "o ponto" else "os $n pontos"} da Curva K " +
            "(até ${Units.gapPercent(biggest.getDouble("deltaPct"))} por ponto). Só grava com o seu toque."
        val proposal = JSONObject().put("direction", direction).put("points", items).put("pointIndexes", indexes).put("text", text)
            .put("maxStepPct", MAX_STEP * 100.0).put("kMin", K_MIN).put("kMax", K_MAX)
        return result(direction, text, "PROPOSTA_LOCAL", proposal)
    }

    /** Resumo para a próxima ação do cérebro (APPLY local), ou nulo. */
    fun nextAction(enriched: JSONObject): JSONObject? {
        val local = enriched.optJSONObject("localProposal") ?: return null
        return JSONObject().put("kind", "APPLY").put("text", local.getString("text")).put("route", "refino").put("subpage", JSONObject.NULL)
            .put("pointIndexes", local.getJSONArray("pointIndexes")).put("local", true)
            .put("currentRaw", local.getJSONArray("currentRaw")).put("refinedRaw", local.getJSONArray("refinedRaw"))
    }

}
