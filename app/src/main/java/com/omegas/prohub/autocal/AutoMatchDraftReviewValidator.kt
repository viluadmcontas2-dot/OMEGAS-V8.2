package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject

/** Valida o handoff local antes de abrir a revisão crítica já existente. */
object AutoMatchDraftReviewValidator {
    fun validate(draft: AutoMatchKFactorDraft, freshCurve: JSONObject): JSONObject {
        require(freshCurve.optBoolean("ok")) { freshCurve.optString("error").ifBlank { "Leitura da Curva K falhou" } }
        require(freshCurve.optBoolean("complete")) { "A Curva K precisa estar completa" }
        require(freshCurve.optBoolean("sessionConfirmed")) { "A Curva K não foi confirmada nesta sessão USB" }
        val factors = freshCurve.optJSONArray("factorsRaw") ?: JSONArray()
        val axis = freshCurve.optJSONArray("axisRaw") ?: JSONArray()
        require(factors.length() == KFactorProtocol.POINT_COUNT) { "Curva K sem 30 fatores" }
        require(axis.length() == KFactorProtocol.POINT_COUNT) { "Eixo Petrol Inj. sem 30 pontos" }
        val selected = draft.points.filter { it.selected && it.changed }
        require(selected.isNotEmpty()) { "Selecione ao menos um ponto alterado" }
        require(selected.size <= KFactorProtocol.POINT_COUNT)

        val validated = JSONArray()
        selected.forEach { point ->
            val ecuRaw = factors.optInt(point.index, -1)
            require(ecuRaw == point.currentRaw) {
                "Ponto ${point.index + 1}: o valor atual mudou de ${point.currentRaw} para $ecuRaw; recrie o rascunho"
            }
            validated.put(JSONObject()
                .put("index", point.index)
                .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(axis.getInt(point.index)))
                .put("currentRaw", ecuRaw)
                .put("targetRaw", point.targetRaw)
                .put("currentFactor", KFactorProtocol.factorFromRaw(ecuRaw))
                .put("targetFactor", KFactorProtocol.factorFromRaw(point.targetRaw))
                .put("source", "AUTOMATCH_RECONSTRUCAO_V6_DRAFT")
                .put("automatic", false))
        }

        return JSONObject()
            .put("ok", true)
            .put("draftId", draft.id)
            .put("draftHash", draft.draftHash)
            .put("snapshotHash", draft.snapshotHash)
            .put("curveHash", freshCurve.optString("hash"))
            .put("sessionId", freshCurve.optLong("sessionId", -1L))
            .put("points", validated)
            .put("pointCount", validated.length())
            .put("automatic", false)
            .put("manualOnly", true)
            .put("requiresCriticalConfirmation", true)
            .put("writesStarted", false)
    }
}
