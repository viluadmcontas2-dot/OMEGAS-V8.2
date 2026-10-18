package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.KMapPhysicalAxes
import com.omegas.prohub.calibration.MapKManualPlanner
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sugestão pura, chamada pelo consumidor nos eventos de AutoMatch/curva/dados.
 * Não conhece relógio, USB ou writer; recebe a sessão serial vigente (0 = desconectado) e só confia no mapa lido NELA
 * (o `sessionConfirmed` do cache só volta a false na próxima conexão). Mesmo retrato de entrada => mesma saída.
 * Recebe SOMENTE evidência da época vigente; mudar curva/mapa invalida os pares antigos.
 * O percentual medido é absoluto; a correção local é o residual que a curva não explica.
 * Alvos são informativos: preparar usa o mapa confirmado e passa pela prévia manual nativa.
 */
object MapSuggestionPolicy {
    enum class Verdict { SUGERIR, SEM_EVIDENCIA, JA_OK }

    fun decide(groups: List<EquivalenceAttribution.Group>, currentMap: JSONObject?, currentSessionId: Long): JSONObject {
        val valid = groups.filter { g ->
            g.key.column in 0 until KMapPhysicalAxes.COLUMNS && g.key.band in EquivalenceLedger.BANDS.indices &&
                g.pairs > 0 && g.petrolMs.isFinite() && g.petrolMs > 0 && g.gasMs.isFinite() && g.gasMs > 0 &&
                g.errorLog.isFinite() && (g.residualLog == null || g.residualLog.isFinite())
        }.sortedWith(compareBy({ it.key.column }, { it.key.band }))
        val plan = LocalMapPlanner.plan(valid)
        val planned = plan.getJSONArray("groups")
        val requested = LocalMapPlanner.cells((0 until planned.length()).map { planned.getJSONObject(it) })
        val rows = currentMap?.optJSONArray("rows")
        val mapValid = currentMap?.optBoolean("sessionConfirmed", false) == true &&
            currentSessionId > 0L && currentMap.optLong("sessionId", 0L) == currentSessionId &&
            currentMap.optBoolean("complete", false) && rows != null && rows.length() == KMapPhysicalAxes.WRITABLE_ROWS &&
            (0 until rows.length()).all { r ->
                val row = rows.optJSONArray(r)
                row != null && row.length() == KMapPhysicalAxes.COLUMNS && (0 until row.length()).all { c ->
                    val n = row.opt(c) as? Number
                    n != null && n.toDouble().isFinite() && n.toDouble() == n.toInt().toDouble() && n.toInt() in 0..255
                }
            }
        val cells = JSONArray()
        if (mapValid) repeat(requested.length()) { i ->
            val cell = requested.getJSONObject(i)
            val row = cell.getInt("row")
            val column = cell.getInt("column")
            // Célula fora da grade gravável nunca derruba a sugestão: é ignorada.
            val current = rows?.optJSONArray(row)?.opt(column) as? Number ?: return@repeat
            val currentValue = current.toInt()
            val target = MapKManualPlanner.target(currentValue, "percent", cell.getDouble("deltaPercent"))
            if (target != currentValue) cells.put(JSONObject(cell.toString()).put("current", currentValue).put("target", target))
        }
        val verdict: Verdict
        val reason: String
        when {
            valid.isEmpty() -> { verdict = Verdict.SEM_EVIDENCIA; reason = "SEM_PARES" }
            requested.length() > 0 && !mapValid -> { verdict = Verdict.SEM_EVIDENCIA; reason = "MAPA_NAO_CONFIRMADO" }
            cells.length() > 0 -> { verdict = Verdict.SUGERIR; reason = "RESIDUAL_LOCAL" }
            requested.length() > 0 -> { verdict = Verdict.SEM_EVIDENCIA; reason = "LIMITE_DO_MAPA" }
            valid.all { it.reason == EquivalenceAttribution.SAME_AS_CURVE } -> {
                verdict = Verdict.JA_OK; reason = "SEM_RESIDUAL_LOCAL"
            }
            else -> { verdict = Verdict.SEM_EVIDENCIA; reason = valid.first { it.reason != EquivalenceAttribution.SAME_AS_CURVE }.reason }
        }
        // Grade completa: ausência é null, nunca zero. Faixas vêm do Ledger, RPM dos eixos físicos.
        val byKey = valid.associateBy { it.key }
        val heat = JSONArray()
        EquivalenceLedger.BANDS.forEachIndexed { band, range ->
            KMapPhysicalAxes.rpmBins().forEachIndexed { column, rpm ->
                val g = byKey[EquivalenceAttribution.Key(column, band)]
                heat.put(JSONObject().put("band", band).put("column", column).put("rpm", rpm)
                    .put("fromMs", range.first).put("toMs", range.second)
                    .put("errorPercent", g?.json()?.opt("errorPercent") ?: JSONObject.NULL)
                    .put("pairs", g?.pairs ?: 0).put("reason", g?.reason ?: "SEM_PARES"))
            }
        }
        return JSONObject().put("verdict", verdict.name).put("reason", reason)
            .put("automatic", false).put("manualOnly", true).put("requiresReview", true)
            .put("cells", cells).put("groups", EquivalenceAttribution.json(valid))
            .put("equivalence", JSONObject().put("cells", heat))
    }
}
