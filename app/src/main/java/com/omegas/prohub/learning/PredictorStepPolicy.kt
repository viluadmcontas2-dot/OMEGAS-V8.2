package com.omegas.prohub.learning

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Correção física relativa derivada diretamente das duas autoridades já
 * congeladas no candidato. Não é armazenada em duplicidade, evitando drift.
 */
val IdealTargetCandidate.deltaStar: Double?
    get() = physicalDeltaStar(kStarObserved, currentKObserved.toDouble())

/**
 * Política de passo separada do IdealTarget científico.
 *
 * Recebe somente números já publicados pelo Predictor e um beta escolhido pela
 * camada de policy/risk. Não possui writer, USB, serial, UI, Store ou Scheduler.
 */
data class StepPolicyInput(
    val currentK: Int,
    val idealTargetK: Int,
    val beta: Double,
)

enum class StepPolicyReason {
    READY,
    INVALID_K_DOMAIN,
    NON_POSITIVE_LOG_DOMAIN,
    INVALID_BETA,
}

data class StepPolicyDecision(
    val available: Boolean,
    val kNext: Int?,
    val deltaStar: Double?,
    val beta: Double,
    val reason: StepPolicyReason,
)

object PredictorStepPolicy {
    fun apply(input: StepPolicyInput): StepPolicyDecision {
        if (input.currentK !in 0..255 || input.idealTargetK !in 0..255) {
            return unavailable(input.beta, StepPolicyReason.INVALID_K_DOMAIN)
        }
        if (!input.beta.isFinite() || input.beta !in 0.0..1.0) {
            return unavailable(input.beta, StepPolicyReason.INVALID_BETA)
        }
        val deltaStar = physicalDeltaStar(input.idealTargetK.toDouble(), input.currentK.toDouble())
            ?: return unavailable(input.beta, StepPolicyReason.NON_POSITIVE_LOG_DOMAIN)

        val kNext = (input.currentK * exp(input.beta * deltaStar))
            .roundToInt()
            .coerceIn(0, 255)
        return StepPolicyDecision(
            available = true,
            kNext = kNext,
            deltaStar = deltaStar,
            beta = input.beta,
            reason = StepPolicyReason.READY,
        )
    }

    private fun unavailable(beta: Double, reason: StepPolicyReason) = StepPolicyDecision(
        available = false,
        kNext = null,
        deltaStar = null,
        beta = beta,
        reason = reason,
    )
}

private fun physicalDeltaStar(targetK: Double, currentK: Double): Double? {
    if (!targetK.isFinite() || !currentK.isFinite() || targetK <= 0.0 || currentK <= 0.0) return null
    return ln(targetK / currentK).takeIf { it.isFinite() }
}
