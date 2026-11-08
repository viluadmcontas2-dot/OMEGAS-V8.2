package com.omegas.prohub.service

import org.junit.Assert.*
import org.junit.Test

class ConnectionSupervisorTest {
    @Test fun `attached manda conectar e negada manda mostrar problema`() {
        val sup = ConnectionSupervisor()
        assertEquals(ConnectionAction.CONNECT, sup.onEvent(ConnectionEvent.UsbAttached))
        assertEquals(ConnectionAction.SHOW_PROBLEM, sup.onEvent(ConnectionEvent.PermissionDenied))
        assertEquals(ConnectionAction.REQUEST_PERMISSION, sup.onEvent(ConnectionEvent.UserRetry))
    }

    @Test fun `servico observa porta, quadro valido e queda sem executar reconexao`() {
        val sup = ConnectionSupervisor()
        assertEquals(ConnectionAction.CONNECT, sup.observeUsbConnected())
        assertEquals(ConnectionPhase.LENDO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeValidFrame())
        assertEquals(ConnectionPhase.PRONTO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeUsbDisconnected())
        assertEquals(ConnectionPhase.RETOMANDO, sup.state.phase)
        assertEquals(1, sup.state.attempts)
        assertEquals(ConnectionAction.CONNECT, sup.observeUsbConnected())
        assertEquals(ConnectionPhase.LENDO, sup.state.phase)
    }

    @Test fun `leituras sem quadro pedem reinicio do nucleo e depois problema`() {
        val sup = ConnectionSupervisor()
        sup.onEvent(ConnectionEvent.Opened)
        val actions = (1..ConnectionPolicy.MAX_READS_WITHOUT_FRAME).map { sup.onEvent(ConnectionEvent.Read(false)) }
        assertTrue(ConnectionAction.RESTART_ENGINE in actions)
        assertEquals(ConnectionAction.SHOW_PROBLEM, actions.last())
    }

    @Test fun `pendencia so aparece no json e nunca dispara gravacao`() {
        val sup = ConnectionSupervisor()
        sup.pending = PendingWriteInfo("Mapa K", "Curva K")
        val j = sup.toJson()
        assertTrue(j.contains("\"fase\":\"PROCURANDO\"")); assertTrue(j.contains("Curva K"))
        assertEquals("null", ConnectionSupervisor().pendingJson())
    }

    @Test fun `permissao pendente e negada sao observadas sem executar transporte`() {
        val sup = ConnectionSupervisor()
        assertEquals(ConnectionAction.NONE, sup.observePermissionState(pending = true, denied = false))
        assertEquals(ConnectionPhase.PERMISSAO, sup.state.phase)

        assertEquals(ConnectionAction.SHOW_PROBLEM, sup.observePermissionState(pending = false, denied = true))
        assertEquals(ConnectionPhase.PROBLEMA, sup.state.phase)
        assertEquals(ConnectionCause.SEM_PERMISSAO, sup.state.cause)

        // Um estado neutro não deve apagar a causa nem solicitar CONNECT.
        assertEquals(ConnectionAction.NONE, sup.observePermissionState(pending = false, denied = false))
        assertEquals(ConnectionCause.SEM_PERMISSAO, sup.state.cause)
    }

    private fun engineStatus(
        state: String,
        handshakeNonRetryable: Boolean = false,
        handshakeFailures: Int = 0,
    ) = org.json.JSONObject()
        .put("state", state)
        .put("handshakeNonRetryable", handshakeNonRetryable)
        .put("handshakeFailures", handshakeFailures)

    @Test fun `handshake em andamento nao afirma que a central esta pronta`() {
        val sup = ConnectionSupervisor()
        sup.observeUsbConnected()

        assertEquals(ConnectionAction.NONE, sup.observeEngineStatus(engineStatus("HANDSHAKE")))
        assertEquals(ConnectionPhase.CONECTANDO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeEngineStatus(engineStatus("ONLINE")))
        assertEquals(ConnectionPhase.LENDO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeValidFrame())
        assertEquals(ConnectionPhase.PRONTO, sup.state.phase)
    }

    @Test fun `resposta nao repetivel do handshake vira aparelho incompativel sem tentar conectar`() {
        val sup = ConnectionSupervisor()
        sup.observeUsbConnected()

        val action = sup.observeEngineStatus(
            engineStatus("RECOVERING_HARD", handshakeNonRetryable = true),
        )

        assertEquals(ConnectionAction.SHOW_PROBLEM, action)
        assertEquals(ConnectionPhase.PROBLEMA, sup.state.phase)
        assertEquals(ConnectionCause.APARELHO_INCOMPATIVEL, sup.state.cause)
    }

    @Test fun `falha persistente do handshake vira central sem resposta sem acionar transporte`() {
        val sup = ConnectionSupervisor()
        sup.observeUsbConnected()

        val action = sup.observeEngineStatus(
            engineStatus("RECOVERING_HARD", handshakeFailures = 40),
        )

        assertEquals(ConnectionAction.SHOW_PROBLEM, action)
        assertEquals(ConnectionPhase.PROBLEMA, sup.state.phase)
        assertEquals(ConnectionCause.CENTRAL_NAO_RESPONDE, sup.state.cause)
    }

    @Test fun `recuperacao depois de conexao valida continua retomando ate quadro valido`() {
        val sup = ConnectionSupervisor()
        sup.observeUsbConnected()
        sup.observeValidFrame()
        sup.observeUsbDisconnected()
        sup.observeUsbConnected()

        assertEquals(ConnectionAction.NONE, sup.observeEngineStatus(engineStatus("RECOVERING")))
        assertEquals(ConnectionPhase.RETOMANDO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeEngineStatus(engineStatus("ONLINE")))
        assertEquals(ConnectionPhase.LENDO, sup.state.phase)
        assertEquals(ConnectionAction.NONE, sup.observeValidFrame())
        assertEquals(ConnectionPhase.PRONTO, sup.state.phase)
    }

}
