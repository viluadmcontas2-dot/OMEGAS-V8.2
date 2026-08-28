package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority

enum class PredictorHumanScientificState {
    DIRECT_CONFIRMED,
    DIRECT_PROVISIONAL,
    PREDICTED_INTERPOLATED,
    PREDICTED_SHRUNK,
    UNKNOWN_ABSTAIN,
}

enum class PredictorHumanRiskState {
    CALIBRATED_ACTIONABLE,
    REVIEW_ONLY,
    RISK_NOT_CALIBRATED,
    BLOCKED,
}

enum class PredictorHumanActionState {
    ACTIONABLE,
    REVIEWABLE,
    ABSTAIN,
}

enum class PredictorHumanVisualState {
    VALIDADO,
    OBSERVADO,
    PREVISTO,
    DESCONHECIDO,
}

data class PredictorHumanProjectionInput(
    val currentK: Int?,
    val targetEstimateK: Double?,
    val targetRange: PredictorTargetRange?,
    val authority: MagnitudeAuthority,
    val scientificState: PredictorHumanScientificState,
    val riskState: PredictorHumanRiskState,
    val confidence: Double?,
    val reasonCode: String,
) {
    init {
        require(currentK == null || currentK in 0..255)
        require(targetEstimateK == null || targetEstimateK.isFinite() && targetEstimateK in 0.0..255.0)
        require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0)
        require(reasonCode.isNotBlank())
    }
}

data class PredictorHumanState(
    val visualState: PredictorHumanVisualState,
    val scientificState: PredictorHumanScientificState,
    val authority: MagnitudeAuthority,
    val riskState: PredictorHumanRiskState,
    val actionState: PredictorHumanActionState,
    val stateLabel: String,
    val targetLabel: String,
    val reason: String,
    val disclosure: String?,
    val confidence: Double,
    val currentK: Int?,
    val targetEstimateK: Double?,
    val intervalLowerK: Double?,
    val intervalUpperK: Double?,
    val intervalBasis: String?,
    val requiresHumanReview: Boolean,
    val predicted: Boolean,
    val directObservation: Boolean,
)

/**
 * Human-facing state is projected upstream from typed scientific authority and
 * calibrated risk. UI must render this object and must not reconstruct it from
 * raw target/confidence numbers.
 */
object PredictorHumanStateProjector {
    fun project(input: PredictorHumanProjectionInput): PredictorHumanState {
        val authoritative = input.authority == MagnitudeAuthority.PHYSICALLY_ANCHORED ||
            input.authority == MagnitudeAuthority.EMPIRICALLY_BOUNDED
        val hasTarget = input.targetEstimateK != null
        val hasRange = input.targetRange != null
        val unknownScience = input.scientificState == PredictorHumanScientificState.UNKNOWN_ABSTAIN

        val actionState = when {
            unknownScience || !hasTarget -> PredictorHumanActionState.ABSTAIN
            input.authority == MagnitudeAuthority.UNKNOWN -> PredictorHumanActionState.ABSTAIN
            authoritative && !hasRange -> PredictorHumanActionState.ABSTAIN
            authoritative && input.riskState == PredictorHumanRiskState.CALIBRATED_ACTIONABLE ->
                PredictorHumanActionState.ACTIONABLE
            authoritative && input.riskState == PredictorHumanRiskState.REVIEW_ONLY ->
                PredictorHumanActionState.REVIEWABLE
            input.authority == MagnitudeAuthority.POLICY_ONLY &&
                input.riskState == PredictorHumanRiskState.REVIEW_ONLY ->
                PredictorHumanActionState.REVIEWABLE
            else -> PredictorHumanActionState.ABSTAIN
        }

        val visualState = when (input.scientificState) {
            PredictorHumanScientificState.DIRECT_CONFIRMED -> PredictorHumanVisualState.VALIDADO
            PredictorHumanScientificState.DIRECT_PROVISIONAL -> PredictorHumanVisualState.OBSERVADO
            PredictorHumanScientificState.PREDICTED_INTERPOLATED,
            PredictorHumanScientificState.PREDICTED_SHRUNK,
            -> PredictorHumanVisualState.PREVISTO
            PredictorHumanScientificState.UNKNOWN_ABSTAIN -> PredictorHumanVisualState.DESCONHECIDO
        }
        val predicted = input.scientificState == PredictorHumanScientificState.PREDICTED_INTERPOLATED ||
            input.scientificState == PredictorHumanScientificState.PREDICTED_SHRUNK
        val directObservation = input.scientificState == PredictorHumanScientificState.DIRECT_CONFIRMED ||
            input.scientificState == PredictorHumanScientificState.DIRECT_PROVISIONAL

        val stateLabel = when (input.authority) {
            MagnitudeAuthority.PHYSICALLY_ANCHORED -> when (input.scientificState) {
                PredictorHumanScientificState.DIRECT_CONFIRMED -> "Alvo físico confirmado"
                PredictorHumanScientificState.DIRECT_PROVISIONAL -> "Alvo físico provisório"
                PredictorHumanScientificState.PREDICTED_INTERPOLATED -> "Alvo físico previsto"
                PredictorHumanScientificState.PREDICTED_SHRUNK -> "Alvo físico previsto com redução conservadora"
                PredictorHumanScientificState.UNKNOWN_ABSTAIN -> "Sem alvo físico disponível"
            }
            MagnitudeAuthority.EMPIRICALLY_BOUNDED -> when (input.scientificState) {
                PredictorHumanScientificState.DIRECT_CONFIRMED -> "Alvo empiricamente limitado"
                PredictorHumanScientificState.DIRECT_PROVISIONAL -> "Alvo empírico provisório"
                PredictorHumanScientificState.PREDICTED_INTERPOLATED -> "Alvo empírico previsto"
                PredictorHumanScientificState.PREDICTED_SHRUNK -> "Alvo empírico previsto com redução conservadora"
                PredictorHumanScientificState.UNKNOWN_ABSTAIN -> "Sem alvo empírico disponível"
            }
            MagnitudeAuthority.POLICY_ONLY -> "Proposta conservadora"
            MagnitudeAuthority.UNKNOWN -> "Estimativa sem autoridade suficiente"
        }

        val targetLabel = when (input.authority) {
            MagnitudeAuthority.PHYSICALLY_ANCHORED,
            MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            -> if (hasRange) "ALVO COM INTERVALO" else "ALVO — INTERVALO INDISPONÍVEL"
            MagnitudeAuthority.POLICY_ONLY -> "PROPOSTA CONSERVADORA"
            MagnitudeAuthority.UNKNOWN -> "ESTIMATIVA — PRECISA DE CONFIRMAÇÃO"
        }

        val reason = when {
            !hasTarget -> "Sem estimativa numérica disponível para esta região."
            unknownScience -> "Sem suporte científico suficiente para publicar uma proposta revisável."
            input.authority == MagnitudeAuthority.UNKNOWN ->
                "Estimativa disponível, mas a magnitude precisa de confirmação de autoridade antes de revisão."
            authoritative && !hasRange ->
                "Alvo sem intervalo científico completo; revisão bloqueada até o intervalo estar disponível."
            input.riskState == PredictorHumanRiskState.RISK_NOT_CALIBRATED ->
                "Risco ainda não calibrado; a estimativa permanece diagnóstica e não revisável."
            input.riskState == PredictorHumanRiskState.BLOCKED ->
                "Actionability bloqueada pelo estado de risco científico."
            input.authority == MagnitudeAuthority.POLICY_ONLY ->
                "Proposta conservadora de política; exige confirmação humana e não representa autoridade física industrial."
            actionState == PredictorHumanActionState.ACTIONABLE ->
                "Autoridade física/empírica e risco calibrado; qualquer aplicação continua dependente de revisão humana."
            actionState == PredictorHumanActionState.REVIEWABLE ->
                "Estimativa com autoridade suficiente para revisão humana, sem escrita automática."
            else -> input.reasonCode
        }

        val disclosure = if (input.scientificState == PredictorHumanScientificState.PREDICTED_SHRUNK) {
            "Correção reduzida por distância e incerteza do suporte; o Predictor aproxima a correção de zero fora da evidência local."
        } else {
            null
        }

        return PredictorHumanState(
            visualState = visualState,
            scientificState = input.scientificState,
            authority = input.authority,
            riskState = input.riskState,
            actionState = actionState,
            stateLabel = stateLabel,
            targetLabel = targetLabel,
            reason = reason,
            disclosure = disclosure,
            confidence = input.confidence ?: 0.0,
            currentK = input.currentK,
            targetEstimateK = input.targetEstimateK,
            intervalLowerK = input.targetRange?.lowerK,
            intervalUpperK = input.targetRange?.upperK,
            intervalBasis = input.targetRange?.basis,
            requiresHumanReview = actionState != PredictorHumanActionState.ABSTAIN,
            predicted = predicted,
            directObservation = directObservation,
        )
    }
}
