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
        val view = ledger.index()
            .put("denseBands", ledger.denseBandsJson())
            .put("typicalBands", ledger.typicalBandsJson())
            .put("refinement", journal.json())
            .put("restorePoints", journal.restorePoints())
            .put("autopilot", phases.json())
            .put("stalls", stalls.json())
            .put("equivalence", equivalence ?: JSONObject.NULL)
        if (equivalence != null) {
            // A Referência (congelada ou congelável) vale mesmo antes de haver índice; o resto, só com resultado.
            equivalence.opt("reference")?.let { view.put("reference", it) }
            if (equivalence.optBoolean("available", false)) {
                for (key in FLAT_KEYS) if (equivalence.has(key)) view.put(key, equivalence.get(key))
            }
        }
        return view
    }
}
