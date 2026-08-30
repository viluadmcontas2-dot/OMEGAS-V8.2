package com.omegas.v7.runtime

enum class LearningReadinessV7 {
    EMPTY,
    PETROL_ONLY,
    CNG_ONLY,
    READY_TO_COMPARE,
    SUGGESTION_AVAILABLE,
}

data class NowUiStateV7(
    val sessionId: String,
    val revisionLabel: String,
    val petrolVisits: Int,
    val activeCngVisits: Int,
    val activeComparisons: Int,
    val readiness: LearningReadinessV7,
    val headline: String,
)

data class LearningUiStateV7(
    val petrolVisits: Int,
    val activeCngVisits: Int,
    val activeComparisons: Int,
    val historicalComparisons: Int,
    val historicalCngVisits: Int,
    val preservedPetrolReference: Boolean,
    val activeRevision: CalibrationRevisionV7,
    val explanation: String,
)

data class AdjustmentUiStateV7(
    val suggestionCount: Int,
    val checkpointCount: Int,
    val lastWriteMessage: String,
    val actionLabel: String,
)

data class V7UiState(
    val now: NowUiStateV7,
    val learning: LearningUiStateV7,
    val adjustment: AdjustmentUiStateV7,
)

object V7UiProjection {
    fun from(state: V7SessionState): V7UiState {
        val petrol = state.petrolEvidence.size
        val activeCng = state.activeCngEvidence().size
        val activeComparisons = state.activeComparisons().size
        val activePendingSuggestions = state.suggestions.count {
            it.expectedRevision == state.calibration.revision && it.lifecycle == SuggestionLifecycleV7.PENDING
        }
        val historicalComparisons = state.comparisons.size - activeComparisons
        val historicalCng = state.cngEvidenceByRevision
            .filterKeys { it != state.calibration.revision }
            .values.sumOf { it.size }
        val readiness = when {
            activePendingSuggestions > 0 -> LearningReadinessV7.SUGGESTION_AVAILABLE
            activeComparisons > 0 -> LearningReadinessV7.READY_TO_COMPARE
            petrol > 0 && activeCng > 0 -> LearningReadinessV7.READY_TO_COMPARE
            petrol > 0 -> LearningReadinessV7.PETROL_ONLY
            activeCng > 0 -> LearningReadinessV7.CNG_ONLY
            else -> LearningReadinessV7.EMPTY
        }
        val headline = when (readiness) {
            LearningReadinessV7.EMPTY -> "Comece dirigindo em gasolina ou GNV"
            LearningReadinessV7.PETROL_ONLY -> "Referência gasolina preservada; falta evidência GNV equivalente"
            LearningReadinessV7.CNG_ONLY -> "Evidência GNV preservada; falta referência gasolina equivalente"
            LearningReadinessV7.READY_TO_COMPARE -> if (activeComparisons > 0) {
                "$activeComparisons comparação(ões) física(s) formada(s)"
            } else {
                "Há dados dos dois combustíveis; aguardando condição equivalente"
            }
            LearningReadinessV7.SUGGESTION_AVAILABLE -> "$activePendingSuggestions sugestão(ões) pronta(s) para revisar"
        }
        val explanation = when {
            petrol == 0 && activeCng == 0 -> "Nenhuma visita física foi consolidada nesta sessão."
            petrol == 0 -> "O GNV foi coletado primeiro e permanece salvo. A comparação nascerá quando uma referência gasolina equivalente aparecer."
            activeCng == 0 -> "A gasolina permanece como referência entre revisões. Falta GNV da revisão ativa."
            activeComparisons == 0 -> "Existem dados dos dois combustíveis, mas ainda não na mesma vizinhança física de RPM e MAP; temperatura é contexto quando disponível."
            else -> "$activeComparisons visita(s) GNV já foram comparadas à referência gasolina equivalente por RPM e MAP; temperatura é contexto quando disponível."
        }
        return V7UiState(
            now = NowUiStateV7(
                sessionId = state.sessionId,
                revisionLabel = "Curva ${state.calibration.revision.curveK} • Mapa ${state.calibration.revision.mapK}",
                petrolVisits = petrol,
                activeCngVisits = activeCng,
                activeComparisons = activeComparisons,
                readiness = readiness,
                headline = headline,
            ),
            learning = LearningUiStateV7(
                petrolVisits = petrol,
                activeCngVisits = activeCng,
                activeComparisons = activeComparisons,
                historicalComparisons = historicalComparisons,
                historicalCngVisits = historicalCng,
                preservedPetrolReference = petrol > 0,
                activeRevision = state.calibration.revision,
                explanation = explanation,
            ),
            adjustment = AdjustmentUiStateV7(
                suggestionCount = activePendingSuggestions,
                checkpointCount = state.checkpoints.size,
                lastWriteMessage = state.lastWriteMessage,
                actionLabel = "Revisar sugestão",
            ),
        )
    }
}
