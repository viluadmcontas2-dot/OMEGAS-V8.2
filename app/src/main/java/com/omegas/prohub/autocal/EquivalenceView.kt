package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * O que a aba Refino lê: índice de equivalência, pontos densos, diário, fases da equivalência e apagões.
 * Uma só montagem, usada pela bridge e pelos testes (que medem o custo com sessões reais).
 * Só leitura: nada aqui grava na ECU.
 */
object EquivalenceView {
    fun build(
        ledger: EquivalenceLedger,
        journal: RefinementJournal,
        phases: EquivalencePhases,
        stalls: StallWatch,
    ): JSONObject = ledger.index()
        .put("denseBands", ledger.denseBandsJson())
        .put("typicalBands", ledger.typicalBandsJson())
        .put("refinement", journal.json())
        .put("restorePoints", journal.restorePoints())
        .put("autopilot", phases.json())
        .put("stalls", stalls.json())
}
