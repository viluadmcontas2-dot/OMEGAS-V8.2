package com.omegas.prohub.service

/**
 * Classifica a mudança física do transporte USB sem depender apenas do booleano
 * `connected`. Uma reabertura completa pode continuar visível como true -> true,
 * mas ainda assim cria uma nova geração que precisa ser propagada ao núcleo.
 */
internal enum class UsbSessionTransition {
    NONE,
    CONNECTED,
    GENERATION_CHANGED,
    DISCONNECTED,
}

internal object UsbSessionTransitionPolicy {
    fun classify(
        lastConnected: Boolean,
        lastSessionId: Long,
        connected: Boolean,
        sessionId: Long,
    ): UsbSessionTransition = when {
        connected && sessionId > 0L && !lastConnected -> UsbSessionTransition.CONNECTED
        connected && sessionId > 0L && lastConnected && sessionId != lastSessionId ->
            UsbSessionTransition.GENERATION_CHANGED
        !connected && lastConnected -> UsbSessionTransition.DISCONNECTED
        else -> UsbSessionTransition.NONE
    }
}
