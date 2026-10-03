package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * O que a aba Refino lê: índice de equivalência, pontos densos, diário, piloto e apagões.
 * Uma só montagem, usada pela bridge e pelos testes (que medem o custo com sessões reais).
 * Só leitura: nada aqui grava na ECU.
 */
object EquivalenceView {
    fun build(
        ledger: EquivalenceLedger,
        journal: RefinementJournal,
        autopilot: RefinementAutopilot,
        stalls: StallWatch,
    ): JSONObject = ledger.index()
        .put("denseBands", ledger.denseBandsJson())
        .put("typicalBands", ledger.typicalBandsJson())
        .put("refinement", journal.json())
        .put("restorePoints", journal.restorePoints())
        .put("autopilot", autopilot.json())
        .put("stalls", stalls.json())
}
