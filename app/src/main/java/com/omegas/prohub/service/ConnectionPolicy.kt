package com.omegas.prohub.service

/** Fases que a tela Conectar mostra. Sem relógio: só eventos e contagens. */
internal enum class ConnectionPhase { PROCURANDO, PERMISSAO, CONECTANDO, LENDO, PRONTO, RETOMANDO, PROBLEMA }

/** Causa do problema, em palavras que o dono entende (a tela traduz em causa + ação). */
internal enum class ConnectionCause { SEM_PERMISSAO, CABO_SEM_CENTRAL, CENTRAL_NAO_RESPONDE, APARELHO_INCOMPATIVEL }

/** Eventos que o serviço repassa (vindos da API pública da USB e do núcleo). */
internal sealed class ConnectionEvent {
    object UsbAttached : ConnectionEvent()
    object UsbDetached : ConnectionEvent()
    object IncompatibleDevice : ConnectionEvent()
    object PermissionRequested : ConnectionEvent()
    object PermissionGranted : ConnectionEvent()
    object PermissionDenied : ConnectionEvent()
    object Opened : ConnectionEvent()
    object HandshakeOk : ConnectionEvent()
    /** Uma leitura do núcleo: [valid] = quadro válido recebido. */
    data class Read(val valid: Boolean) : ConnectionEvent()
    object UserRetry : ConnectionEvent()
}

/** Estado imutável da conexão. [everLive] = já houve leitura ao vivo nesta sessão do app (queda vira RETOMANDO). */
internal data class ConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.PROCURANDO,
    val cause: ConnectionCause? = null,
    val attempts: Int = 0,
    val readsWithoutFrame: Int = 0,
    val everLive: Boolean = false,
)

/** Máquina de estados pura. Reconexão por contagem de tentativas/leituras, nunca por tempo. */
internal object ConnectionPolicy {
    /** Leituras seguidas sem quadro válido até declarar "a central não responde". */
    const val MAX_READS_WITHOUT_FRAME = 20

    /** Tentativas de reconexão seguidas, sem leitura ao vivo entre elas, até virar problema. */
    const val MAX_RECONNECTS = 5

    fun next(s: ConnectionState, e: ConnectionEvent): ConnectionState = when (e) {
        ConnectionEvent.UsbAttached ->
            // Plugar de novo limpa a permissão negada e zera a contagem.
            s.copy(phase = ConnectionPhase.CONECTANDO, cause = null, attempts = 0, readsWithoutFrame = 0)
        ConnectionEvent.UsbDetached -> dropped(s)
        ConnectionEvent.IncompatibleDevice ->
            s.copy(phase = ConnectionPhase.PROBLEMA, cause = ConnectionCause.APARELHO_INCOMPATIVEL)
        ConnectionEvent.PermissionRequested ->
            if (s.phase == ConnectionPhase.PROBLEMA && s.cause == ConnectionCause.SEM_PERMISSAO) s
            else s.copy(phase = ConnectionPhase.PERMISSAO, cause = null)
        ConnectionEvent.PermissionGranted -> s.copy(phase = ConnectionPhase.CONECTANDO, cause = null)
        // Negada não tenta sozinha: só UserRetry ou novo ATTACHED saem daqui.
        ConnectionEvent.PermissionDenied ->
            s.copy(phase = ConnectionPhase.PROBLEMA, cause = ConnectionCause.SEM_PERMISSAO)
        ConnectionEvent.Opened -> s.copy(phase = ConnectionPhase.LENDO, cause = null, readsWithoutFrame = 0)
        ConnectionEvent.HandshakeOk -> s.copy(phase = ConnectionPhase.LENDO, readsWithoutFrame = 0)
        is ConnectionEvent.Read -> read(s, e.valid)
        ConnectionEvent.UserRetry ->
            s.copy(phase = ConnectionPhase.CONECTANDO, cause = null, attempts = 0, readsWithoutFrame = 0)
    }

    private fun dropped(s: ConnectionState): ConnectionState {
        if (s.phase == ConnectionPhase.PROBLEMA && s.cause == ConnectionCause.SEM_PERMISSAO) return s
        return if (s.everLive) {
            val n = s.attempts + 1
            if (n > MAX_RECONNECTS) s.copy(phase = ConnectionPhase.PROBLEMA, cause = ConnectionCause.CABO_SEM_CENTRAL, attempts = n)
            else s.copy(phase = ConnectionPhase.RETOMANDO, cause = null, attempts = n, readsWithoutFrame = 0)
        } else s.copy(phase = ConnectionPhase.PROCURANDO, cause = null, readsWithoutFrame = 0)
    }

    private fun read(s: ConnectionState, valid: Boolean): ConnectionState {
        if (s.phase == ConnectionPhase.PROCURANDO || s.phase == ConnectionPhase.PERMISSAO || s.phase == ConnectionPhase.PROBLEMA && s.cause != ConnectionCause.CENTRAL_NAO_RESPONDE) return s
        if (valid) return s.copy(phase = ConnectionPhase.PRONTO, cause = null, attempts = 0, readsWithoutFrame = 0, everLive = true)
        val n = s.readsWithoutFrame + 1
        return if (n >= MAX_READS_WITHOUT_FRAME) {
            s.copy(phase = ConnectionPhase.PROBLEMA, cause = ConnectionCause.CENTRAL_NAO_RESPONDE, readsWithoutFrame = n)
        } else s.copy(readsWithoutFrame = n)
    }
}
