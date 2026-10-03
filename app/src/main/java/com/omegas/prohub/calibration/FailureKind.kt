package com.omegas.prohub.calibration

import com.omegas.prohub.usb.UsbProtocolReply

/**
 * Regra 7: erro de transporte (cabo/USB) não é erro da ECU. A UI mostra textos diferentes.
 */
object FailureKind {
    const val TRANSPORT = "TRANSPORTE"
    const val ECU = "ECU"
    const val APP = "APP"

    /**
     * Resposta sem quadro válido (timeout, eco, checksum, USB caiu) é transporte.
     * Quadro completo com status diferente de ACK é a ECU recusando.
     */
    fun ofReply(reply: UsbProtocolReply): String = when {
        reply.ok -> ECU
        reply.error.startsWith("ECU retornou status") -> ECU
        else -> TRANSPORT
    }

    /** Heurística para mensagens que já viraram texto (exceções antigas, status persistido). */
    fun ofMessage(message: String?): String {
        val text = message.orEmpty().lowercase()
        return when {
            text.isBlank() -> APP
            text.contains("ecu retornou") || text.contains("ack inválido") || text.contains("ecu recusou") -> ECU
            text.contains("usb") || text.contains("timeout") || text.contains("eco divergente") ||
                text.contains("checksum") || text.contains("resposta incompleta") ||
                text.contains("resposta sem campo") || text.contains("serial") -> TRANSPORT
            else -> APP
        }
    }

    fun of(error: Throwable): String = (error as? CalibrationFailure)?.kind ?: ofMessage(error.message)
}

/** Falha de calibração com a origem já classificada (transporte × ECU × app). */
class CalibrationFailure(message: String, val kind: String) : IllegalStateException(message)
