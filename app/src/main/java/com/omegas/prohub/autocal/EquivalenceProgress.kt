package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Projeções da mesma evidência; previsões e consultas nunca criam medições. */
object EquivalenceProgress {
    val RPM = listOf(850.0 to 1250.0, 1250.0 to 1750.0, 1750.0 to 2250.0, 2250.0 to 2750.0, 2750.0 to 3500.0)
    private fun rpmBand(rpm: Double) = RPM.indexOfFirst { rpm >= it.first && (rpm < it.second || rpm == 3500.0 && it.second == 3500.0) }
    private fun msBand(ms: Double?) = EquivalenceLedger.BANDS.indexOfFirst { ms != null && ms >= it.first && ms < it.second }

    fun unitId(rpm: Double, map: Double, ms: Double?, ecu: Boolean): String =
        "${rpmBand(rpm)}:${msBand(ms)}:${EquivalenceLedger.cellKey(rpm, map)}:${if (ecu) "ECU" else "OWN"}"

    private fun median(values: List<Double>): Double {
        val v = values.sorted()
        return (v[(v.size - 1) / 2] + v[v.size / 2]) / 2
    }

    fun build(pairs: List<EquivalenceLedger.EvidencePair>, usage: Map<String, Long>, revision: Long,
              sequence: Long, reference: String, configuration: String, connected: Boolean): JSONObject {
        val groups = pairs.filter { !it.predicted && it.petrolRefMs > 0 && it.petrolRefMs.isFinite() &&
            it.gasPetrolMs > 0 && it.gasPetrolMs.isFinite() }
            .distinctBy { listOf(it.t, it.rpm, it.map, it.ecuRef) }
            .groupBy { unitId(it.rpm, it.map, it.petrolRefMs, it.ecuRef) }
            .mapValues { (_, rows) -> rows.sortedBy { it.t }.takeLast(30) }
        fun inDomain(key: String) = !key.startsWith("-1:") && key.split(':')[1] != "-1"
        val errors = groups.filter { (key, rows) -> inDomain(key) && rows.size >= 8 }
            .mapValues { (_, rows) -> median(rows.map { abs(100 * (it.gasPetrolMs / it.petrolRefMs - 1)) }) }
        fun metric(keys: Set<String>): JSONObject {
            val total = keys.sumOf { usage[it] ?: 0L }
            val valid = keys.filter { it in errors }
            val weight = valid.sumOf { usage[it] ?: 0L }
            val e = if (connected && weight > 0) valid.sumOf { errors.getValue(it) * (usage[it] ?: 0L) } / weight else null
            val worst = if (connected) valid.maxOfOrNull { errors.getValue(it) } else null
            val coverage = if (total > 0 && connected) 100.0 * weight / total else null
            val partial = coverage == null || coverage < 100.0
            val state = when { worst != null && worst > 5 -> "OUTSIDE"; e == null || partial -> "NO_DATA"; else -> "WITHIN" }
            return JSONObject().put("errorPct", e ?: JSONObject.NULL)
                .put("equivalencePct", e?.let { (100 - it).coerceAtLeast(0.0) } ?: JSONObject.NULL)
                .put("maxCellErrorPct", worst ?: JSONObject.NULL).put("coveragePct", coverage ?: JSONObject.NULL)
                .put("measuredUnits", valid.size).put("requiredUnits", keys.size)
                .put("validPairs", keys.sumOf { groups[it]?.size ?: 0 }).put("usageCount", total)
                .put("partial", partial).put("state", state).put("targetPct", 4).put("acceptedPct", 5)
                .put("targetReached", !partial && worst != null && worst <= 4)
                .put("reason", if (partial) "EVIDENCIA_INSUFICIENTE" else "REGIAO_MEDIDA")
                .put("stateLabel", when (state) { "OUTSIDE" -> "Fora"; "NO_DATA" -> "Sem dados suficientes"; else -> if (worst!! <= 4) "Na meta" else "Aceito" })
                .put("label", if (partial) "Somente região medida" else "Base: gasolina · condições semelhantes")
        }
        fun bands(bounds: List<Pair<Double, Double>>, rpm: Boolean) = JSONArray(bounds.mapIndexed { i, (lo, hi) ->
            metric(usage.keys.filter { it.split(':')[if (rpm) 0 else 1] == i.toString() }.toSet())
                .put("id", "${if (rpm) "rpm" else "ms"}-$i").put("from", lo).put("to", hi)
                .put("upperInclusive", rpm && i == bounds.lastIndex)
                .put("label", if (rpm) "${lo.toInt()}–${hi.toInt()} RPM" else "$lo–$hi ms")
        })
        val global = metric(usage.keys)
        return JSONObject().put("schemaVersion", 1).put("revision", revision).put("observationSequence", sequence)
            // Fatia 1 mede; não implementa nem afirma a parada em dois lotes da fatia seguinte.
            .put("state", "MEDINDO").put("reason", if (connected) "COLETANDO_EVIDENCIA" else "SEM_ECU")
            .put("headline", if (connected) "Equivalência nas faixas medidas; confirmação ainda pendente." else "Conecte a ECU para continuar a medição")
            .put("refining", false).put("monitoring", true).put("connected", connected)
            .put("action", JSONObject().put("kind", "BANDS").put("label", "Ver faixas pendentes").put("enabled", connected))
            .put("scope", JSONObject().put("id", configuration).put("profileId", configuration)
                .put("unitIds", JSONArray(usage.keys.toList())).put("label", "Faixas visitadas")
                .put("rpmMin", 850).put("rpmMax", 3500).put("complete", false)
                .put("outsideDomainReads", usage.filterKeys { it.startsWith("-1:") }.values.sum())
                .put("unclassifiedReads", usage.filterKeys { it.split(':')[1] == "-1" }.values.sum()))
            .put("reference", JSONObject().put("token", reference).put("configurationId", configuration)
                .put("source", when { pairs.isEmpty() -> "NONE"; pairs.all { it.ecuRef } -> "ECU"; pairs.none { it.ecuRef } -> "OWN"; else -> "MIXED" })
                .put("ownPairs", pairs.count { !it.ecuRef }).put("ecuPairs", pairs.count { it.ecuRef })
                .put("label", "Base: gasolina · condições semelhantes"))
            .put("metric", global).put("rpmBands", bands(RPM, true)).put("msBands", bands(EquivalenceLedger.BANDS, false))
            .put("evidencePolicy", JSONObject().put("pairsPerUnit", 8).put("independentBatches", 2)
                .put("minMsBands", 3).put("minPhysicalCells", 3).put("windowPairsPerUnit", 30).put("usageProfileId", configuration))
            .put("currentBand", JSONObject.NULL).put("verification", JSONObject.NULL).put("lastWrite", JSONObject.NULL)
            .put("stop", JSONObject().put("latched", false).put("reason", "CONFIRMACAO_PENDENTE")
                .put("entryPct", 4).put("resumeAbovePct", 5).put("confirmationBatches", 2).put("confirmedBatches", 0).put("pendingUnitIds", JSONArray(usage.keys.toList())))
    }
}
