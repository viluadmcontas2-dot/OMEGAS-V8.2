package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONArray
import org.json.JSONObject

/**
 * Projeção de produto da fila de sugestões NEXT.
 *
 * Não escreve ECU. Apenas traduz as entidades persistentes da sessão para uma
 * fila humana, ordenada por acionabilidade/confiança/frescor sem esconder o
 * motivo. O lifecycle interno continua sendo a autoridade persistida.
 */
object SuggestionUiProjection {
    enum class HumanLifecycle { PENDENTE, OBSERVANDO, APLICADA, SUPERADA }

    fun project(
        suggestions: List<LocalSuggestionV7>,
        currentRevision: CalibrationRevisionV7,
    ): JSONObject {
        val ordered = suggestions
            .map { suggestion -> row(suggestion, currentRevision) }
            .sortedWith(
                compareByDescending<JSONObject> { it.optBoolean("actionable", false) }
                    .thenByDescending { it.optDouble("confidence", 0.0) }
                    .thenByDescending { it.optLong("updatedAt", 0L) }
                    .thenBy { it.optString("id") },
            )
        val active = ordered.filter { item ->
            item.optString("lifecycle") in setOf(HumanLifecycle.PENDENTE.name, HumanLifecycle.OBSERVANDO.name)
        }
        return JSONObject()
            .put("schema", "omegas-next-suggestions-v1")
            .put("items", JSONArray(ordered))
            .put("activeCount", active.size)
            .put("readyCount", active.count { it.optBoolean("actionable", false) })
            .put("automaticWrite", false)
            .put("humanSelectionRequired", true)
    }

    private fun row(value: LocalSuggestionV7, currentRevision: CalibrationRevisionV7): JSONObject {
        val lifecycle = when (value.lifecycle) {
            SuggestionLifecycleV7.PENDING -> HumanLifecycle.PENDENTE
            SuggestionLifecycleV7.OBSERVING -> HumanLifecycle.OBSERVANDO
            SuggestionLifecycleV7.APPLIED -> HumanLifecycle.APLICADA
            SuggestionLifecycleV7.SUPERSEDED -> HumanLifecycle.SUPERADA
        }
        val actionable = value.actionableAt(currentRevision)
        val targetLabel = when (value.target) {
            SuggestionTargetV7.MAP_K -> "Mapa K local"
            SuggestionTargetV7.CURVE_K -> "Curva K global"
        }
        return JSONObject()
            .put("id", value.id)
            .put("createdAt", value.createdAtMs)
            .put("updatedAt", value.updatedAtMs)
            .put("target", value.target.name)
            .put("targetLabel", targetLabel)
            .put("lifecycle", lifecycle.name)
            .put("actionable", actionable)
            .put("confidence", value.confidence)
            .put("supportState", value.stabilityState)
            .put("reason", value.rationale)
            .put("whatIsMissing", missingText(value, lifecycle, actionable))
            .put("mapChanges", JSONArray(value.mapChanges.map { change ->
                JSONObject()
                    .put("row", change.row)
                    .put("column", change.column)
                    .put("before", change.before)
                    .put("after", change.after)
            }))
            .put("curveChanges", JSONArray(value.curveChanges.map { change ->
                JSONObject()
                    .put("index", change.index)
                    .put("before", change.before)
                    .put("after", change.after)
            }))
            .put("automaticWrite", false)
            .put("requiresReview", lifecycle == HumanLifecycle.PENDENTE)
    }

    private fun missingText(
        value: LocalSuggestionV7,
        lifecycle: HumanLifecycle,
        actionable: Boolean,
    ): String = when {
        lifecycle == HumanLifecycle.APLICADA -> "Nada: a aplicação já foi confirmada por estado real da ECU."
        lifecycle == HumanLifecycle.SUPERADA -> "A revisão/base mudou; esta sugestão fica apenas no histórico."
        actionable -> "Nada obrigatório: está pronta para revisão humana, não para escrita automática."
        value.stabilityState == "REVALIDATING" -> "Mais evidência independente e coerente nesta condição."
        value.stabilityState == "NO_EVIDENCE" -> "Ainda falta evidência física suficiente nesta condição."
        else -> "A evidência atual ainda não justifica uma alteração; continue a coleta normal."
    }
}
