package com.omegas.prohub.session

enum class LearningReadiness {
    EMPTY,
    PETROL_ONLY,
    CNG_ONLY,
    READY_TO_COMPARE,
    SUGGESTION_AVAILABLE,
}

data class NowUiState(
    val sessionId: String,
    val revisionLabel: String,
    val petrolVisits: Int,
    val activeCngVisits: Int,
    val activeComparisons: Int,
    val readiness: LearningReadiness,
    val headline: String,
)

data class LearningUiState(
    val petrolVisits: Int,
    val activeCngVisits: Int,
    val activeComparisons: Int,
    val historicalComparisons: Int,
    val historicalCngVisits: Int,
    val preservedPetrolReference: Boolean,
    val activeRevision: CalibrationRevision,
    val explanation: String,
)

data class AdjustmentUiState(
    val suggestionCount: Int,
    val checkpointCount: Int,
    val lastWriteMessage: String,
    val actionLabel: String,
)

data class UiState(
    val now: NowUiState,
    val learning: LearningUiState,
    val adjustment: AdjustmentUiState,
)

object UiProjection {
    fun from(state: SessionState): UiState {
        val petrol = state.petrolEvidence.size
        val activeCng = state.activeCngEvidence().size
        val activeComparisons = state.activeComparisons().size
        val activePendingSuggestions = state.suggestions.count {
            it.expectedRevision == state.calibration.revision && it.lifecycle == SuggestionLifecycle.PENDING
        }
        val historicalComparisons = state.comparisons.size - activeComparisons
        val historicalCng = state.cngEvidenceByRevision
            .filterKeys { it != state.calibration.revision }
            .values.sumOf { it.size }
        val readiness = when {
            activePendingSuggestions > 0 -> LearningReadiness.SUGGESTION_AVAILABLE
            activeComparisons > 0 -> LearningReadiness.READY_TO_COMPARE
            petrol > 0 && activeCng > 0 -> LearningReadiness.READY_TO_COMPARE
            petrol > 0 -> LearningReadiness.PETROL_ONLY
            activeCng > 0 -> LearningReadiness.CNG_ONLY
            else -> LearningReadiness.EMPTY
        }
        val headline = when (readiness) {
            LearningReadiness.EMPTY -> "Comece dirigindo em gasolina ou GNV"
            LearningReadiness.PETROL_ONLY -> "Referência gasolina preservada; falta evidência GNV equivalente"
            LearningReadiness.CNG_ONLY -> "Evidência GNV preservada; falta referência gasolina equivalente"
            LearningReadiness.READY_TO_COMPARE -> if (activeComparisons > 0) {
                "$activeComparisons comparação(ões) física(s) formada(s)"
            } else {
                "Há dados dos dois combustíveis; aguardando condição equivalente"
            }
            LearningReadiness.SUGGESTION_AVAILABLE -> "$activePendingSuggestions sugestão(ões) pronta(s) para revisar"
        }
        val explanation = when {
            petrol == 0 && activeCng == 0 -> "Nenhuma visita física foi consolidada nesta sessão."
            petrol == 0 -> "O GNV foi coletado primeiro e permanece salvo. A comparação nascerá quando uma referência gasolina equivalente aparecer."
            activeCng == 0 -> "A gasolina permanece como referência entre revisões. Falta GNV da revisão ativa."
            activeComparisons == 0 -> "Existem dados dos dois combustíveis, mas ainda não na mesma vizinhança física de RPM, MAP e temperatura."
            else -> "$activeComparisons visita(s) GNV já foram comparadas à referência gasolina equivalente por RPM, MAP e temperatura."
        }
        return UiState(
            now = NowUiState(
                sessionId = state.sessionId,
                revisionLabel = "Curva ${state.calibration.revision.curveK} • Mapa ${state.calibration.revision.mapK}",
                petrolVisits = petrol,
                activeCngVisits = activeCng,
                activeComparisons = activeComparisons,
                readiness = readiness,
                headline = headline,
            ),
            learning = LearningUiState(
                petrolVisits = petrol,
                activeCngVisits = activeCng,
                activeComparisons = activeComparisons,
                historicalComparisons = historicalComparisons,
                historicalCngVisits = historicalCng,
                preservedPetrolReference = petrol > 0,
                activeRevision = state.calibration.revision,
                explanation = explanation,
            ),
            adjustment = AdjustmentUiState(
                suggestionCount = activePendingSuggestions,
                checkpointCount = state.checkpoints.size,
                lastWriteMessage = state.lastWriteMessage,
                actionLabel = "Revisar sugestão",
            ),
        )
    }
}
