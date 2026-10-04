package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * O que a aba Refino lê: índice de equivalência, pontos densos, diário, fases da equivalência e apagões.
 * Uma só montagem, usada pela bridge e pelos testes (que medem o custo com sessões reais).
 * Só leitura: nada aqui grava na ECU.
 *
 * O resultado do cérebro único (`equivalence`, F4) entra inteiro sob `equivalence` e, para a UI que já lê
 * o formato plano, também nas chaves `index` (0..1), `coverage`, `provisional`, `nextAction`, `points` e
 * `reference`. As chaves antigas (`autopilot`, `bands`, `refinement`, …) não mudam.
 *
 * Sem o cérebro (`equivalence` nulo ou sem resultado), as chaves planas `index/coverage/provisional/nextAction/points`
 * NÃO existem na raiz: a UI lê "sem índice" e mostra "—". O índice da condução antigo (razão mediana do livro)
 * fica só sob `legacyIndex`, para diagnóstico, e nunca é lido como o índice do Refino.
 */
object EquivalenceView {
    private val FLAT_KEYS = listOf("index", "coverage", "provisional", "nextAction", "points")

    fun build(
        ledger: EquivalenceLedger,
        journal: RefinementJournal,
        phases: EquivalencePhases,
        stalls: StallWatch,
        equivalence: JSONObject? = null,
    ): JSONObject {
        val legacy = ledger.index()
        val autopilot = phases.json()
        val refinement = journal.json()
        // Engasgo repetido vira ajuste LOCAL (só depois da última gravação da Curva K) e a próxima ação do cérebro quando não há outra a aplicar.
        val appliedAt = refinement.optJSONObject("latest")?.optLong("appliedAt", 0L) ?: 0L
        val stallsView = StallLocalFix.enrich(stalls.json(), equivalence, appliedAt)
        val brain = equivalence?.let { JSONObject(it.toString()) }
        if (brain != null && brain.optBoolean("available", false)) {
            val kind = brain.optJSONObject("nextAction")?.optString("kind")
            if (kind == "COLLECT" || kind == "NOTHING") StallLocalFix.nextAction(stallsView)?.let { brain.put("nextAction", it) }
        }
        val between = ledger.betweenPointsJson()
        val view = JSONObject(legacy.toString())
            .put("legacyIndex", legacy)
            .put("denseBands", ledger.denseBandsJson())
            .put("refinement", refinement)
            .put("restorePoints", journal.restorePoints())
            .put("autopilot", autopilot)
            .put("stalls", stallsView)
            .put("betweenPoints", between)
            .put("fluidity", Fluidity.fromDense(ledger.denseBandsJson()))
            .put("refinoState", RefinoState.build(autopilot, brain, between, stallsView))
            .put("equivalence", brain ?: JSONObject.NULL)
        for (key in FLAT_KEYS) view.remove(key) // nunca vaza índice antigo para a chave do cérebro
        if (brain != null) {
            // A Referência (congelada ou congelável) vale mesmo antes de haver índice; o resto, só com resultado.
            brain.opt("reference")?.let { view.put("reference", it) }
            if (brain.optBoolean("available", false)) {
                for (key in FLAT_KEYS) if (brain.has(key)) view.put(key, brain.get(key))
            }
        }
        return view
    }
}
