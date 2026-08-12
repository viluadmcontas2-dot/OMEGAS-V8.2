package com.omegas.prohub.usb

enum class UsbRecoveryAction {
    RETRY_TRANSPORT,
    HARD_DISCONNECT,
}

data class UsbRecoveryDecision(
    val action: UsbRecoveryAction,
    val delayMs: Long,
)

object UsbRecoveryPolicy {
    private val retryDelaysMs = longArrayOf(250L, 750L, 1_500L)

    fun decide(
        devicePresent: Boolean,
        autoReconnect: Boolean,
        manualDisconnect: Boolean,
        attempt: Int,
    ): UsbRecoveryDecision {
        if (!devicePresent || !autoReconnect || manualDisconnect) {
            return UsbRecoveryDecision(UsbRecoveryAction.HARD_DISCONNECT, 0L)
        }
        val delay = retryDelaysMs.getOrNull(attempt)
            ?: return UsbRecoveryDecision(UsbRecoveryAction.HARD_DISCONNECT, 0L)
        return UsbRecoveryDecision(UsbRecoveryAction.RETRY_TRANSPORT, delay)
    }
}
