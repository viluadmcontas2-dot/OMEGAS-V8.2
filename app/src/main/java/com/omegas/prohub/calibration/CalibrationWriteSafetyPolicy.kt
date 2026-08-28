package com.omegas.prohub.calibration

import com.omegas.prohub.model.HubStatus

/**
 * Autoridade única e pura para decidir se uma mutação manual da ECU pode começar.
 *
 * A política não escreve, não toca USB e não conhece WebView. Bridges e ações
 * nativas consultam a mesma decisão antes de alcançar qualquer writer/comando
 * mutável. A confirmação humana continua sendo uma etapa separada e obrigatória.
 */
object CalibrationWriteSafetyPolicy {
    const val MAX_SAFE_TELEMETRY_AGE_MS = 2_500L
    const val DRIVING_PROBABLE_RPM = 1_200

    data class Decision(
        val allowed: Boolean,
        val reason: String? = null,
        val code: String = if (allowed) "SAFE_TO_REVIEW_WRITE" else "SAFETY_BLOCKED",
    )

    fun evaluate(status: HubStatus): Decision = when {
        !status.serviceRunning -> blocked("SERVICE_UNAVAILABLE", "Serviço Android indisponível")
        !status.usbConnected -> blocked("USB_DISCONNECTED", "Conecte a ECU antes de gravar")
        status.usbPermissionPending -> blocked("USB_PERMISSION_PENDING", "Permissão USB pendente")
        !status.engineRunning || !status.engineReady || status.engineStuck -> blocked(
            "ENGINE_UNSAFE",
            "Comunicação com a ECU não está segura para escrita",
        )
        status.directTelemetryAgeMs < 0L || status.directTelemetryAgeMs > MAX_SAFE_TELEMETRY_AGE_MS -> blocked(
            "TELEMETRY_STALE",
            "Telemetria não está atual; aguarde novos quadros antes de gravar",
        )
        status.rpm >= DRIVING_PROBABLE_RPM -> blocked(
            "DRIVING_PROBABLE",
            "Condução provável: escrita bloqueada enquanto o motor estiver acima de $DRIVING_PROBABLE_RPM RPM",
        )
        else -> Decision(allowed = true)
    }

    fun unsafeReason(status: HubStatus): String? = evaluate(status).reason

    private fun blocked(code: String, reason: String): Decision = Decision(
        allowed = false,
        reason = reason,
        code = code,
    )
}
