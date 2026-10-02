package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * Transforma falhas operacionais em orientação determinística para a UX.
 *
 * Nunca repete uma mutação automaticamente: uma resposta perdida não prova que
 * a ECU não executou a escrita. Recuperação segura significa reconectar/relêr e
 * exigir nova intenção humana antes de qualquer nova mutação.
 */
object AutoCalRecoveryPolicy {
    data class Recovery(
        val reasonCode: String,
        val retryable: Boolean,
        val nextActionCode: String,
        val nextAction: String,
        val automaticRetry: Boolean = false,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("reasonCode", reasonCode)
            .put("retryable", retryable)
            .put("nextActionCode", nextActionCode)
            .put("nextAction", nextAction)
            .put("automaticRetry", automaticRetry)
    }

    fun classify(message: String?): Recovery {
        val value = message.orEmpty().lowercase()
        return when {
            "usb desconect" in value -> Recovery(
                reasonCode = "USB_DISCONNECTED",
                retryable = true,
                nextActionCode = "RECONNECT_AND_REPREPARE",
                nextAction = "Reconecte a ECU. O OMEGAS relê o estado; depois revise e prepare a ação novamente.",
            )
            "sessão usb mudou" in value || "sessao usb mudou" in value -> Recovery(
                reasonCode = "USB_SESSION_CHANGED",
                retryable = true,
                nextActionCode = "REPREPARE_CURRENT_SESSION",
                nextAction = "A sessão física mudou. Aguarde a leitura da sessão atual e prepare a ação novamente.",
            )
            "outra operação" in value || "outra calibração" in value || "outra calibracao" in value -> Recovery(
                reasonCode = "CALIBRATION_BUSY",
                retryable = true,
                nextActionCode = "WAIT_AND_REPREPARE",
                nextAction = "Finalize a outra calibração. Depois revise a ação novamente na sessão atual.",
            )
            "readback" in value || "não persistiu" in value || "nao persistiu" in value ||
                "não voltou" in value || "nao voltou" in value -> Recovery(
                reasonCode = "READBACK_MISMATCH",
                retryable = true,
                nextActionCode = "REFRESH_BEFORE_RETRY",
                nextAction = "A resposta final não confirmou o estado esperado. Atualize a ECU antes de decidir repetir a ação.",
            )
            "não confirmou" in value || "nao confirmou" in value || "ack" in value -> Recovery(
                reasonCode = "ECU_ACK_MISSING",
                retryable = true,
                nextActionCode = "REFRESH_BEFORE_RETRY",
                nextAction = "A confirmação da ECU não chegou. Primeiro releia o estado; não repita a mutação às cegas.",
            )
            "expirou" in value -> Recovery(
                reasonCode = "PREPARATION_EXPIRED",
                retryable = true,
                nextActionCode = "REPREPARE",
                nextAction = "A revisão expirou. Confira o estado atual e prepare a ação novamente.",
            )
            else -> Recovery(
                reasonCode = "UNKNOWN_FAILURE",
                retryable = false,
                nextActionCode = "INSPECT_TECHNICAL_DETAILS",
                nextAction = "Abra os detalhes técnicos e confirme o estado da ECU antes de qualquer nova ação.",
            )
        }
    }
}
