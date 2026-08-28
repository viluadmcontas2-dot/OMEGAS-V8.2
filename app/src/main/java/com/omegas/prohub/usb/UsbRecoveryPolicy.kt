package com.omegas.prohub.usb

enum class UsbRecoveryAction {
    RETRY_TRANSPORT,
    HARD_DISCONNECT,
}

data class UsbRecoveryDecision(
    val action: UsbRecoveryAction,
    val delayMs: Long,
)

/**
 * Política fail-closed para falhas do transporte USB.
 *
 * Uma reabertura física da porta não pode preservar a geração lógica anterior:
 * qualquer porta reaberta precisa passar novamente por `open()`, que atribui um
 * novo `connectionSessionId`. Isso permite ao serviço invalidar Store, runtime,
 * writers, AutoCal, buffers e telemetria antes de aceitar dados da nova geração.
 *
 * Por isso, falha de transporte não usa `openRecoveredPort()` como continuação
 * da mesma sessão. O caminho seguro é desconectar e deixar o auto-reconnect abrir
 * uma geração nova. `RETRY_TRANSPORT` permanece no enum apenas por compatibilidade
 * binária durante a reconstrução, mas esta política não o emite.
 */
object UsbRecoveryPolicy {
    fun decide(
        devicePresent: Boolean,
        autoReconnect: Boolean,
        manualDisconnect: Boolean,
        attempt: Int,
    ): UsbRecoveryDecision {
        @Suppress("UNUSED_VARIABLE")
        val context = Triple(devicePresent, autoReconnect, manualDisconnect)
        @Suppress("UNUSED_VARIABLE")
        val ignoredAttempt = attempt
        return UsbRecoveryDecision(UsbRecoveryAction.HARD_DISCONNECT, 0L)
    }
}
