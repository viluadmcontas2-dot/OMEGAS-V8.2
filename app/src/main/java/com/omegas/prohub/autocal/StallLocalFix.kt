package com.omegas.prohub.autocal

import com.omegas.prohub.util.Units
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
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
 *    ponto, limitado a ±[MAX_STEP] (8%) do K atual e a [K_MIN, K_MAX] (0,75..1,20), e passado pelos MESMOS portões do motor
 *    refinado: caixa com a trava da baixa ([AutoMatchRefinedEngine.proposalBox], nunca empobrece abaixo de
 *    [AutoMatchRefinedEngine.LOW_GUARD_MS]), trava de inclinação ([AutoMatchRefinedEngine.enforceCoherence] com
 *    [AutoMatchRefinedEngine.E_MAX]), histerese ([AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG]) e regressão (a proposta nunca
 *    afasta os pontos fora do alvo). Só contam APAGOU/QUASE_APAGOU vindos de condução (rpm ≥
 *    [EquivalenceLedger.DRIVING_MIN_RPM], `drivingAts` do [StallWatch]): engasgo de lenta não pede Curva K.
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
            val recent = drivingEvents(region).count { it > sinceMs }
            region.put("recentCount", recent)
            val diagnosis = diagnose(region, covering, recent, allPoints(points))
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

    /**
     * Instantes dos engasgos que contam: `drivingAts` (APAGOU/QUASE_APAGOU com rpm ≥ [EquivalenceLedger.DRIVING_MIN_RPM]).
     * JSON antigo sem a lista: `ats` só se a região inteira veio de condução; nunca a lenta.
     */
    private fun drivingEvents(region: JSONObject): List<Long> {
        val driving = region.optJSONArray("drivingAts")
        if (driving != null) return (0 until driving.length()).map { driving.optLong(it) }
        if (region.optDouble("rpmBefore", region.optDouble("rpm", 0.0)) < EquivalenceLedger.DRIVING_MIN_RPM) return emptyList()
        val ats = region.optJSONArray("ats") ?: return List(region.optInt("count")) { Long.MAX_VALUE }
        return (0 until ats.length()).map { ats.optLong(it) }
    }

    /** Os 30 pontos do cérebro por índice (eixo e K atual), ou nulo se faltar algum. */
    private fun allPoints(points: JSONArray?): List<JSONObject>? {
        if (points == null) return null
        val byIndex = (0 until points.length()).mapNotNull { points.optJSONObject(it) }.associateBy { it.optInt("index", -1) }
        val list = (0 until AutoMatchRefinedEngine.POINT_COUNT).map { byIndex[it] ?: return null }
        if (list.any { !(it.optDouble("axisMs") > 0.0) || !(it.optDouble("kCurrent") > 0.0) }) return null
        return list
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

    private fun diagnose(region: JSONObject, covering: List<JSONObject>, recent: Int, all: List<JSONObject>?): Diagnosis {
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
        // Passo local: cada ponto fora vai ao alvo do cérebro, limitado a ±8% do K atual e a 0,75..1,20, e passa pelos portões do
        // motor refinado (caixa com trava da baixa → coerência E_MAX → histerese → regressão). Os outros pontos ficam parados.
        val items = JSONArray()
        var blocked = false
        if (all != null) {
            val n = all.size
            val axis = all.map { it.optDouble("axisMs") }
            val u = axis.map { ln(it) }
            val x0 = all.map { ln(it.optDouble("kCurrent")) }
            val target = x0.toMutableList()
            val movable = BooleanArray(n)
            val wanted = HashMap<Int, Double>()
            for (p in off) {
                val j = p.optInt("index")
                if (j !in 0 until n) continue
                val delta = p.optDouble("mixture").coerceIn(-MAX_STEP, MAX_STEP)
                // Trava da baixa: o nó e o trecho interpolado até o nó anterior (empobrecer o nó acima da trava empobrece abaixo dela).
                val guarded = axis[j] < AutoMatchRefinedEngine.LOW_GUARD_MS || (j > 0 && axis[j - 1] < AutoMatchRefinedEngine.LOW_GUARD_MS)
                if (delta < 0.0 && guarded) { blocked = true; continue }
                target[j] = x0[j] + ln(1.0 + delta)
                wanted[j] = target[j]
                movable[j] = true
            }
            val engineBox = AutoMatchRefinedEngine.proposalBox(x0, List(n) { if (movable[it]) 1.0 else 0.0 }, axis)
            val box = engineBox.mapIndexed { j, b ->
                val lo = b.first
                val hi = b.second
                val pair = if (!movable[j]) x0[j] to x0[j]
                else if (direction == DIR_POOR) maxOf(lo, x0[j]) to minOf(hi, x0[j] + ln(1.0 + MAX_STEP))
                else maxOf(lo, x0[j] + ln(1.0 - MAX_STEP)) to minOf(hi, x0[j])
                if (pair.first > pair.second) pair.second to pair.second else pair
            }
            val e = AutoMatchRefinedEngine.effectiveElasticity(box, u)
            val enforced = AutoMatchRefinedEngine.enforceCoherence(target, box, u, e)
            val final = AutoMatchRefinedEngine.holdSmallSteps(target, box, x0, enforced, u, e, AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG).second
            val raws = IntArray(n) { j ->
                if (!movable[j]) (exp(x0[j]) * Q14).roundToInt()
                else (exp(final[j]) * Q14).roundToInt().coerceIn(AutoMatchRefinedEngine.MIN_RAW_PROPOSAL, AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
            }
            // Regressão: a proposta nunca afasta do alvo do cérebro os pontos que ele julgou fora.
            val errBefore = wanted.entries.sumOf { (x0[it.key] - it.value) * (x0[it.key] - it.value) }
            val errAfter = wanted.entries.sumOf { (ln(raws[it.key] / Q14) - it.value) * (ln(raws[it.key] / Q14) - it.value) }
            if (errAfter <= errBefore + AutoMatchRefinedEngine.REGRESSION_EPS) {
                for (j in 0 until n) {
                    if (!movable[j]) continue
                    val k = exp(x0[j])
                    val after = raws[j] / Q14
                    val applied = after / k - 1.0
                    val requested = exp(wanted.getValue(j) - x0[j]) - 1.0
                    // O teto 0,75..1,20 nunca pode inverter a direção nem passar de ±8%; passo de ruído (histerese) fica.
                    if (applied * requested <= 0.0 || abs(applied) > MAX_STEP + 1e-3) continue
                    if (abs(ln(after / k)) < AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG - 1e-9 || abs(applied) < MIN_DELTA) continue
                    items.put(JSONObject().put("index", j).put("axisMs", axis[j])
                        .put("kBefore", k).put("kAfter", after).put("kAfterRaw", raws[j])
                        .put("deltaPct", Math.round(applied * 1000.0) / 10.0))
                }
            }
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
