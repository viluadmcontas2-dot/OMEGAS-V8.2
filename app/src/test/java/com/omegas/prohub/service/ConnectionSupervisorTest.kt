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
}
