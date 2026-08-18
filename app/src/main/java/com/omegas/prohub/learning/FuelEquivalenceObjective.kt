package com.omegas.prohub.learning

import kotlin.math.abs

enum class FuelEquivalenceState {
    INVALID,
    WITHIN_POLICY_DEADBAND,
    PETROL_ON_CNG_ABOVE_REFERENCE,
    PETROL_ON_CNG_BELOW_REFERENCE,
}

data class FuelEquivalenceResult(
    val valid: Boolean,
    val state: FuelEquivalenceState,
    val reasonCode: String,
    val referenceMs: Double? = null,
    val petrolOnCngMs: Double? = null,
    val differenceMs: Double? = null,
    val errorRatio: Double? = null,
    val errorPercent: Double? = null,
    val withinMsDeadband: Boolean = false,
    val withinPercentDeadband: Boolean = false,
)

/**
 * Objetivo científico: comparar o comando de gasolina observado sob GNV com a
 * referência gasolina comparável.
 *
 * Convenção de sinal única:
 *   error = (petrolOnCng - reference) / reference
 * Positivo = Petrol Inj. no GNV acima da referência; negativo = abaixo.
 *
 * O denominador mínimo é argumento obrigatório do chamador para que nenhum
 * número escondido seja promovido a invariante física. Deadbands vêm da policy
 * registrada e só declaram equivalência quando AMBAS as tolerâncias são atendidas.
 */
object FuelEquivalenceObjective {
    fun evaluate(
        referenceMs: Double?,
        petrolOnCngMs: Double?,
        minimumReferenceMs: Double,
        policy: LearningTolerancePolicy = LearningToleranceSettings.current,
    ): FuelEquivalenceResult {
        require(minimumReferenceMs > 0.0 && minimumReferenceMs.isFinite()) {
            "minimumReferenceMs deve ser positivo e finito"
        }
        if (referenceMs == null || petrolOnCngMs == null) {
            return FuelEquivalenceResult(false, FuelEquivalenceState.INVALID, "SIGNAL_UNAVAILABLE")
        }
        if (!referenceMs.isFinite() || !petrolOnCngMs.isFinite()) {
            return FuelEquivalenceResult(false, FuelEquivalenceState.INVALID, "SIGNAL_IMPLAUSIBLE")
        }
        if (referenceMs < minimumReferenceMs) {
            return FuelEquivalenceResult(
                valid = false,
                state = FuelEquivalenceState.INVALID,
                reasonCode = "REFERENCE_DENOMINATOR_TOO_SMALL",
                referenceMs = referenceMs,
                petrolOnCngMs = petrolOnCngMs,
            )
        }
        if (petrolOnCngMs < 0.0) {
            return FuelEquivalenceResult(
                false,
                FuelEquivalenceState.INVALID,
                "PETROL_ON_CNG_NEGATIVE",
                referenceMs,
                petrolOnCngMs,
            )
        }

        val differenceMs = petrolOnCngMs - referenceMs
        val ratio = differenceMs / referenceMs
        val percent = ratio * 100.0
        val withinMs = abs(differenceMs) <= policy.equivalenceDeadbandMs
        val withinPercent = abs(percent) <= policy.equivalenceDeadbandPercent
        val state = when {
            withinMs && withinPercent -> FuelEquivalenceState.WITHIN_POLICY_DEADBAND
            differenceMs > 0.0 -> FuelEquivalenceState.PETROL_ON_CNG_ABOVE_REFERENCE
            else -> FuelEquivalenceState.PETROL_ON_CNG_BELOW_REFERENCE
        }
        return FuelEquivalenceResult(
            valid = true,
            state = state,
            reasonCode = state.name,
            referenceMs = referenceMs,
            petrolOnCngMs = petrolOnCngMs,
            differenceMs = differenceMs,
            errorRatio = ratio,
            errorPercent = percent,
            withinMsDeadband = withinMs,
            withinPercentDeadband = withinPercent,
        )
    }
}
