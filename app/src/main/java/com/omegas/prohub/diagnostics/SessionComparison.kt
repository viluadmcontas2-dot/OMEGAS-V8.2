package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Comparativo (`omegas-comparison-v1`): o tanque desta sessão contra o tanque anterior e contra o melhor tanque conhecido.
 * SÓ compara quando a cobertura é comparável (nível consumido suficiente nos dois, mesma fonte de velocidade em todas as
 * sessões dos dois, nível lido em todas); senão o veredito é INCONCLUSIVO com os motivos. Nunca inventa número:
 * desconhecido é null. Rendimento = PROXY (o sensor de nível é uma escada de 5 degraus); "melhorou/piorou" só passa de
 * [MIN_DELTA]; a causa provável é HIPÓTESE (mudança de ponto do mapa/curva no intervalo), nunca certeza. Puro.
 */
object SessionComparison {
    const val SCHEMA = "omegas-comparison-v1"
    /** Diferença menor que isto fica "IGUAL (dentro do ruído do sensor)". */
    const val MIN_DELTA = 0.10
    const val MAX_CAUSES = 15

    private class Cycle(val json: JSONObject) {
        val index = json.optInt("index")
        val open = json.optBoolean("open")
        val enough = json.optBoolean("enoughData")
        val sessions = json.optInt("sessions")
        val sessionIds: List<String> = json.optJSONArray("sessionIds")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
        val cov: JSONObject = json.optJSONObject("coverage") ?: JSONObject()
        val consumed: Int? = json.optIntOrNull("levelConsumedRaw")
        val km: Pair<Double, String>? = json.optJSONObject("kmPerRawUnit")?.let { o -> o.optDoubleOrNull("value")?.let { it to o.optString("source") } }
        val gnv: Double? = json.optJSONObject("gnvMinutesPer100Level")?.optDoubleOrNull("value")
        val inj: Double? = json.optJSONObject("injectorMinutesPer100Level")?.optDoubleOrNull("value")
        fun kmCovered(): Boolean = km != null && sessions > 0 && cov.optInt(if (km.second == SpeedSource.GPS) "withGPS" else "withMM") >= sessions
        fun levelCovered(): Boolean = sessions > 0 && cov.optInt("withLevel") >= sessions
        fun share(): Pair<String, Double>? {
            val moving = json.optJSONObject("movingMs"); val stopped = json.optJSONObject("stoppedMs")
            for (src in listOf(SpeedSource.MM, SpeedSource.GPS)) {
                val m = moving?.optDoubleOrNull(src); val st = stopped?.optDoubleOrNull(src)
                if (m != null && st != null && m + st > 0.0) return src to m / (m + st)
            }
            return null
        }
        fun brief() = JSONObject().put("cycleIndex", index).put("open", open).put("sessions", sessions)
            .put("sessionIds", JSONArray(sessionIds)).put("levelConsumedRaw", consumed ?: JSONObject.NULL)
            .put("enoughData", enough).put("coverage", JSONObject(cov.toString()))
            .put("kmPerLevel", km?.let { JSONObject().put("value", it.first).put("source", it.second).put("label", "PROXY") } ?: JSONObject.NULL)
            .put("gnvMinutesPer100Level", gnv ?: JSONObject.NULL)
            .put("injectorMinutesPer100Level", inj ?: JSONObject.NULL)
            .put("movingShare", share()?.let { JSONObject().put("source", it.first).put("value", Totals.nullable(it.second, 3)) } ?: JSONObject.NULL)
            .put("orderUncertain", json.optBoolean("orderUncertain"))
    }

    private fun metricFor(a: Cycle, b: Cycle): Triple<String, Double, Double>? = when {
        a.kmCovered() && b.kmCovered() && a.km!!.second == b.km!!.second && a.enough && b.enough -> Triple("kmPerLevel", a.km.first, b.km.first)
        a.gnv != null && b.gnv != null && a.levelCovered() && b.levelCovered() && a.enough && b.enough -> Triple("gnvMinutesPer100Level", a.gnv, b.gnv)
        else -> null
    }

    private fun whyNot(cur: Cycle, other: Cycle): List<String> {
        val out = ArrayList<String>()
        if (!cur.enough) out.add("Neste tanque o nível consumido ainda é menor que ${TankLedger.MIN_LEVEL_UNITS} unidades: o sensor é uma escada e não sustenta uma razão.")
        if (!other.enough) out.add("No outro tanque o nível consumido foi menor que ${TankLedger.MIN_LEVEL_UNITS} unidades.")
        if (cur.km == null || other.km == null) out.add("Sem velocidade (multimídia ou GPS) nos dois tanques: não há km por nível.")
        else if (cur.km.second != other.km.second) out.add("A velocidade veio de fontes diferentes nos dois tanques (${cur.km.second} e ${other.km.second}): não se compara.")
        else if (!cur.kmCovered() || !other.kmCovered()) out.add("A velocidade não cobre todas as sessões de um dos tanques.")
        if (!cur.levelCovered() || !other.levelCovered()) out.add("O nível do cilindro não foi lido em todas as sessões de um dos tanques.")
        if (cur.json.optBoolean("orderUncertain") || other.json.optBoolean("orderUncertain")) out.add("Relógio suspeito em alguma sessão: a ordem dos tanques pode estar errada.")
        return out
    }

    private fun verdictOf(delta: Double, higherIsBetter: Boolean): String = when {
        Math.abs(delta) < MIN_DELTA -> "IGUAL"
        (delta > 0) == higherIsBetter -> "MELHOROU"
        else -> "PIOROU"
    }

    private fun pct(d: Double) = Totals.nullable(d * 100.0, 1) ?: JSONObject.NULL

    private fun compare(label: String, cur: Cycle, other: Cycle, ledger: JSONObject, current: JSONObject, lookup: (String) -> JSONObject?): JSONObject {
        val metric = metricFor(cur, other)
        val out = JSONObject().put("against", label).put("otherTank", other.brief())
        val metrics = JSONArray()
        if (metric != null) {
            val (name, a, b) = metric
            val delta = if (b != 0.0) (a - b) / b else null
            val verdict = if (delta == null) "INCONCLUSIVO" else verdictOf(delta, higherIsBetter = true)
            metrics.put(JSONObject().put("name", name).put("current", a).put("other", b).put("deltaPct", delta?.let(::pct) ?: JSONObject.NULL)
                .put("betterWhen", "mais").put("label", "PROXY").put("verdict", verdict))
            out.put("comparable", true).put("primaryMetric", name).put("verdict", verdict)
            if (cur.open) out.put("partial", true)
        } else {
            out.put("comparable", false).put("primaryMetric", JSONObject.NULL).put("verdict", "INCONCLUSIVO")
                .put("reasons", JSONArray(whyNot(cur, other)))
        }
        if (cur.inj != null && other.inj != null && other.inj != 0.0) {
            metrics.put(JSONObject().put("name", "injectorMinutesPer100Level").put("current", cur.inj).put("other", other.inj)
                .put("deltaPct", pct((cur.inj - other.inj) / other.inj)).put("betterWhen", JSONObject.NULL).put("label", "PROXY").put("verdict", "INFORMATIVO"))
        }
        val cs = cur.share(); val os = other.share()
        if (cs != null && os != null && cs.first == os.first) {
            metrics.put(JSONObject().put("name", "movingShare").put("source", cs.first).put("current", Totals.nullable(cs.second, 3)).put("other", Totals.nullable(os.second, 3))
                .put("betterWhen", JSONObject.NULL).put("verdict", "INFORMATIVO"))
        }
        out.put("metrics", metrics)
        out.put("probableCause", probableCause(cur, other, ledger, current, lookup))
        return out
    }

    private fun summaryOf(id: String, current: JSONObject, lookup: (String) -> JSONObject?): JSONObject? =
        if (current.optJSONObject("identity")?.optString("sessionId") == id) current else lookup(id)

    private fun probableCause(cur: Cycle, other: Cycle, ledger: JSONObject, current: JSONObject, lookup: (String) -> JSONObject?): JSONObject {
        val sessions = ledger.optJSONObject("sessions") ?: JSONObject()
        fun startOf(id: String): Long? = sessions.keys().asSequence().map { sessions.getJSONObject(it) }.firstOrNull { it.optString("sessionId") == id }?.optLongOrNull("startMs")
        val lastOther = other.sessionIds.lastOrNull()
        val lastCur = cur.sessionIds.lastOrNull()
        val from = lastOther?.let(::startOf)
        val to = lastCur?.let(::startOf)
        val between = sessions.keys().asSequence().map { sessions.getJSONObject(it) }
            .filter { s -> val st = s.optLongOrNull("startMs"); st != null && (from == null || st > from) && (to == null || st <= to) }
            .sortedBy { it.optLong("startMs") }.map { it.optString("sessionId") }.toList()
        val missing = ArrayList<String>()
        val causes = JSONArray()
        var total = 0
        for (id in between) {
            val s = summaryOf(id, current, lookup)
            if (s == null) { missing.add(id); continue }
            val log = s.optJSONArray("changeLog") ?: continue
            for (i in 0 until log.length()) {
                val b = log.getJSONObject(i)
                total += 1
                if (causes.length() >= MAX_CAUSES) continue
                val examples = JSONArray()
                val ch = b.optJSONArray("changes") ?: JSONArray()
                for (k in 0 until minOf(3, ch.length())) examples.put(ch.getJSONObject(k))
                causes.put(JSONObject().put("sessionId", id).put("kind", b.optString("kind")).put("cause", b.optString("cause"))
                    .put("causeText", b.optString("causeText")).put("tankIndex", b.opt("tankIndex") ?: JSONObject.NULL)
                    .put("cellsChanged", b.optInt("cellsChanged")).put("examples", examples))
            }
        }
        fun hash(id: String?, key: String): String? = id?.let { summaryOf(it, current, lookup) }?.optJSONObject("mapCurve")?.optJSONObject(key)
            ?.optString("hashLast")?.takeIf { it.isNotBlank() }
        fun same(key: String): Any {
            val a = hash(lastOther, key); val b = hash(lastCur, key)
            return if (a == null || b == null) JSONObject.NULL else a == b
        }
        val mapSame = same("map"); val curveSame = same("curve")
        val text = when {
            total > 0 -> "Houve $total gravação(ões) de mapa/curva entre os dois tanques: a mudança de ponto é a causa PROVÁVEL (hipótese; trânsito e condução também pesam)."
            mapSame == false || curveSame == false -> "O mapa ou a curva lidos são diferentes nos dois tanques, mas nenhuma gravação ficou registrada nas sessões do intervalo."
            mapSame == true && curveSame == true -> "Mapa e curva iguais nos dois tanques: a diferença vem de outra coisa (trânsito, condução, gás, calibração do nível)."
            else -> "Não dá para saber se o mapa mudou: mapa ou curva não foram lidos nas sessões dos dois tanques (desconhecido, não igual)."
        }
        return JSONObject().put("label", "HYPOTHESIS").put("mapSame", mapSame).put("curveSame", curveSame)
            .put("changesFound", total).put("changes", causes).put("sessionsMissingSummary", JSONArray(missing)).put("text", text)
    }

    fun build(current: JSONObject, ledger: JSONObject?, lookup: (String) -> JSONObject?): JSONObject {
        val id = current.optJSONObject("identity")?.optString("sessionId") ?: ""
        val out = JSONObject().put("schema", SCHEMA).put("sessionId", id)
            .put("rule", "Só compara com cobertura comparável; senão INCONCLUSIVO. Rendimento é PROXY (mais é melhor). Diferença < ${(MIN_DELTA * 100).toInt()}% = IGUAL. Causa provável é hipótese.")
        val cyclesJson = ledger?.optJSONArray("cycles")
        val cycles = if (cyclesJson == null) emptyList() else List(cyclesJson.length()) { Cycle(cyclesJson.getJSONObject(it)) }
        val cur = cycles.lastOrNull { id in it.sessionIds }
        if (cur == null) {
            return out.put("verdict", "INCONCLUSIVO").put("current", JSONObject.NULL).put("previous", JSONObject.NULL).put("best", JSONObject.NULL)
                .put("reasons", JSONArray(listOf("Esta sessão não tem nível do cilindro lido: não há tanque para comparar (desconhecido, não zero).")))
                .put("comparisons", JSONArray())
        }
        val previous = cycles.lastOrNull { it.index < cur.index }
        val closedOthers = cycles.filter { it.index != cur.index && !it.open }
        val best = listOf("kmPerLevel", "gnvMinutesPer100Level").firstNotNullOfOrNull { name ->
            closedOthers.filter { metricFor(cur, it)?.first == name }
                .maxByOrNull { if (name == "kmPerLevel") it.km!!.first else it.gnv!! }
        }
        val comparisons = JSONArray()
        previous?.let { comparisons.put(compare("previous", cur, it, ledger!!, current, lookup)) }
        best?.let { if (it.index != previous?.index) comparisons.put(compare("best", cur, it, ledger!!, current, lookup)) }
        val bestIsCurrent = best != null && metricFor(cur, best)?.let { (_, a, b) -> a >= b } == true
        val main = comparisons.optJSONObject(0)
        return out.put("current", cur.brief()).put("previous", previous?.brief() ?: JSONObject.NULL)
            .put("best", best?.brief() ?: JSONObject.NULL).put("currentIsBest", if (best == null) JSONObject.NULL else bestIsCurrent)
            .put("verdict", main?.optString("verdict") ?: "INCONCLUSIVO")
            .put("reasons", if (main == null) JSONArray(listOf("Este é o primeiro tanque conhecido: não há com o que comparar.")) else JSONArray())
            .put("comparisons", comparisons)
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
