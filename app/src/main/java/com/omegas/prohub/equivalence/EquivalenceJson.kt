package com.omegas.prohub.equivalence

import org.json.JSONArray
import org.json.JSONObject

/**
 * Forma do resultado do cérebro para a ponte e a sessão. Chaves planas que a UI já lê (`index` 0..1,
 * `coverage`, `provisional`, `nextAction`, `points`, `reference`) mais o detalhe técnico (Curvas Próprias,
 * proposta). `automatic` é sempre false: o cérebro só observa.
 *
 * CONTRATO DE FORMA (Kotlin serializa → JS lê; docs/ARCHITECTURE.md "Contrato do resultado do cérebro"):
 *  - `index` é um NÚMERO ESCALAR 0..1 ou `null` (mostrar "—"); nunca um objeto, nunca 0 no lugar de desconhecido.
 *    É `null` quando os pontos julgados cobrem menos de [EquivalenceTolerances.MIN_JUDGED_USAGE] do uso.
 *  - `provisional` (boolean), `coverage` (inteiro) e `judgedUsage` (0..1) são irmãos planos de `index` no mesmo nível,
 *    não filhos dele. As mesmas chaves existem quando `available` é false.
 *  - Teste: BrainContractTest.
 */
object EquivalenceJson {
    const val FORMAT = "omegas-equivalence-result-v1"
    const val REASON_CURVE_UNREAD = "CURVA_K_NAO_LIDA"

    private fun num(value: Double?): Any = if (value == null || !value.isFinite()) JSONObject.NULL else value

    private fun referenceJson(reference: Reference?, ecuDrift: Double?, previous: Reference?, canFreeze: Boolean, nowMs: Long): JSONObject {
        val points = JSONArray()
        reference?.points?.forEach { points.put(JSONObject().put("mapBar", it.mapBar).put("petrolMs", it.petrolMs).put("maturity", it.maturity)) }
        return JSONObject()
            .put("frozen", reference != null)
            .put("canFreeze", canFreeze)
            .put("id", reference?.id ?: JSONObject.NULL)
            .put("frozenAt", reference?.frozenAt ?: JSONObject.NULL)
            // Idade da Referência (nulo = "—", nunca 0) e se a ECU já reaprendeu a gasolina bem longe dela (deriva > alarme):
            // só se registra; a decisão de congelar de novo é do dono (um toque).
            .put("ageMs", if (reference == null || reference.frozenAt <= 0L) JSONObject.NULL else (nowMs - reference.frozenAt).coerceAtLeast(0L))
            .put("stale", reference != null && ecuDrift != null && ecuDrift.isFinite() && kotlin.math.abs(ecuDrift) > EquivalenceTolerances.DIVERGENCE_ALARM)
            .put("ecuAcquisitionFingerprint", reference?.ecuAcquisitionFingerprint ?: JSONObject.NULL)
            .put("points", points)
            .put("ecuDrift", num(ecuDrift))
            .put("previousId", previous?.id ?: JSONObject.NULL)
    }

    private fun curveJson(curve: OwnCurve): JSONObject {
        val cells = JSONArray()
        for (c in curve.cells) {
            cells.put(
                JSONObject().put("mapBar", c.mapBar).put("petrolMs", num(c.petrolMs)).put("samples", c.samples)
                    .put("dispersion", num(c.dispersion)).put("source", c.source.name).put("divergence", num(c.divergence))
                    .put(
                        "relearnSuggested",
                        c.source == CellSource.OWN && c.divergence != null && kotlin.math.abs(c.divergence) > EquivalenceTolerances.DIVERGENCE_ALARM,
                    ),
            )
        }
        return JSONObject().put("fuel", curve.fuel.name).put("cells", cells)
    }

    private fun pointJson(p: EquivalencePoint): JSONObject = JSONObject()
        .put("index", p.index).put("axisMs", p.axisMs).put("kCurrent", p.kCurrent).put("kTarget", num(p.kTarget))
        .put("mixture", num(p.mixture)).put("tolerance", p.tolerance)
        .put("roughnessRatio", num(p.roughnessRatio)).put("nearStallRatio", num(p.nearStallRatio))
        .put("slope", num(p.slope)).put("usage", p.usage).put("samples", p.samples)
        .put("sources", JSONArray(p.sources.toList())).put("state", p.state.name)

    private fun proposalJson(result: EquivalenceResult): Any {
        val proposal = result.proposal ?: return JSONObject.NULL
        return JSONObject()
            .put("mode", proposal.mode.name)
            .put("reason", proposal.reason ?: JSONObject.NULL)
            .put("telemetryOnly", proposal.telemetryOnly)
            .put("currentRaw", JSONArray(proposal.currentRaw))
            .put("refinedRaw", JSONArray(proposal.refinedRaw))
            .put("origins", JSONArray(proposal.origins.map { it.name }))
    }

    fun result(
        result: EquivalenceResult?,
        reference: Reference?,
        ecuDrift: Double?,
        previous: Reference?,
        canFreeze: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): JSONObject {
        val ref = referenceJson(reference, ecuDrift, previous, canFreeze, nowMs)
        if (result == null) {
            // Mesma forma do resultado disponível: `index` nulo ("—", nunca 0), `coverage` 0 e `provisional` irmãos planos.
            return JSONObject().put("ok", true).put("format", FORMAT).put("available", false)
                .put("index", JSONObject.NULL).put("coverage", 0).put("judgedUsage", 0.0).put("provisional", reference == null)
                .put("reason", REASON_CURVE_UNREAD).put("reference", ref).put("automatic", false)
        }
        val action = result.nextAction
        val points = JSONArray()
        result.points.forEach { points.put(pointJson(it)) }
        return JSONObject()
            .put("ok", true).put("format", FORMAT).put("available", true)
            .put("index", num(result.index))
            .put("coverage", result.coverage)
            .put("judgedUsage", result.judgedUsage)
            .put("provisional", result.provisional)
            .put(
                "nextAction",
                JSONObject().put("kind", action.kind.name).put("text", action.text)
                    .put("route", action.route ?: JSONObject.NULL).put("subpage", action.subpage ?: JSONObject.NULL)
                    .put("pointIndexes", JSONArray(action.pointIndexes))
                    // APPLY carrega a curva lida e a proposta (30 raw cada): é isso que a UI grava, nada recalculado por fora.
                    .also { o ->
                        action.currentRaw?.let { o.put("currentRaw", JSONArray(it)) }
                        action.refinedRaw?.let { o.put("refinedRaw", JSONArray(it)) }
                    },
            )
            .put("points", points)
            .put("ownPetrol", curveJson(result.ownPetrol))
            .put("ownGas", curveJson(result.ownGas))
            .put("regimeCurves", JSONObject().also { regimes ->
                result.regimeCurves.forEach { (regime, curves) ->
                    regimes.put(regime.name, JSONObject()
                        .put("petrol", curveJson(curves.petrol)).put("gas", curveJson(curves.gas)))
                }
            })
            .put("regimeAssessments", JSONObject().also { regimes ->
                result.regimeAssessments.forEach { (regime, a) ->
                    regimes.put(regime.name, JSONObject().put("pairs", a.pairs)
                        .put("effectiveSamples", a.effectiveSamples).put("mixture", num(a.mixture))
                        .put("dispersion", num(a.dispersion)).put("judgeable", a.judgeable))
                }
            })
            .put("proposal", proposalJson(result))
            .put("reference", ref)
            .put("automatic", false)
    }
}
