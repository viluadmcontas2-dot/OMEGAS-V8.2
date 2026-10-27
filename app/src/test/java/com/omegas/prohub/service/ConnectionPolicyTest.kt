package com.omegas.prohub.service

import org.junit.Assert.*
import org.junit.Test

class ConnectionPolicyTest {
    private fun run(vararg ev: ConnectionEvent, from: ConnectionState = ConnectionState()) =
        ev.fold(from) { s, e -> ConnectionPolicy.next(s, e) }

    @Test fun `caminho feliz passa por permissao, conectando, lendo e pronto`() {
        var s = ConnectionState()
        s = ConnectionPolicy.next(s, ConnectionEvent.PermissionRequested); assertEquals(ConnectionPhase.PERMISSAO, s.phase)
        s = ConnectionPolicy.next(s, ConnectionEvent.PermissionGranted); assertEquals(ConnectionPhase.CONECTANDO, s.phase)
        s = ConnectionPolicy.next(s, ConnectionEvent.Opened); assertEquals(ConnectionPhase.LENDO, s.phase)
        s = ConnectionPolicy.next(s, ConnectionEvent.Read(true)); assertEquals(ConnectionPhase.PRONTO, s.phase)
    }

    @Test fun `permissao negada nao tenta sozinha e attached limpa a negada`() {
        val negada = run(ConnectionEvent.PermissionDenied)
        assertEquals(ConnectionCause.SEM_PERMISSAO, negada.cause)
        assertEquals(negada, ConnectionPolicy.next(negada, ConnectionEvent.PermissionRequested))
        assertEquals(negada, ConnectionPolicy.next(negada, ConnectionEvent.UsbDetached))
        assertNull(ConnectionPolicy.next(negada, ConnectionEvent.UsbAttached).cause)
    }

    @Test fun `cabo ok mas central muda nunca vira pronto`() {
        var s = run(ConnectionEvent.UsbAttached, ConnectionEvent.Opened)
        repeat(ConnectionPolicy.MAX_READS_WITHOUT_FRAME - 1) { s = ConnectionPolicy.next(s, ConnectionEvent.Read(false)); assertNotEquals(ConnectionPhase.PRONTO, s.phase) }
        s = ConnectionPolicy.next(s, ConnectionEvent.Read(false))
        assertEquals(ConnectionPhase.PROBLEMA, s.phase)
        assertEquals(ConnectionCause.CENTRAL_NAO_RESPONDE, s.cause)
    }

    @Test fun `quadro valido zera a contagem`() {
        var s = run(ConnectionEvent.Opened)
        repeat(5) { s = ConnectionPolicy.next(s, ConnectionEvent.Read(false)) }
        s = ConnectionPolicy.next(s, ConnectionEvent.Read(true))
        assertEquals(0, s.readsWithoutFrame)
    }

    @Test fun `queda depois de ao vivo vira retomando e conta tentativas ate esgotar`() {
        var s = run(ConnectionEvent.Opened, ConnectionEvent.Read(true))
        repeat(ConnectionPolicy.MAX_RECONNECTS) { i ->
            s = ConnectionPolicy.next(s, ConnectionEvent.UsbDetached)
            assertEquals(ConnectionPhase.RETOMANDO, s.phase); assertEquals(i + 1, s.attempts)
        }
        s = ConnectionPolicy.next(s, ConnectionEvent.UsbDetached)
        assertEquals(ConnectionPhase.PROBLEMA, s.phase)
    }

    @Test fun `queda antes de ao vivo volta a procurar`() {
        assertEquals(ConnectionPhase.PROCURANDO, run(ConnectionEvent.Opened, ConnectionEvent.UsbDetached).phase)
    }

    @Test fun `aparelho incompativel e tentar de novo`() {
        val s = run(ConnectionEvent.IncompatibleDevice)
        assertEquals(ConnectionCause.APARELHO_INCOMPATIVEL, s.cause)
        assertEquals(ConnectionPhase.CONECTANDO, ConnectionPolicy.next(s, ConnectionEvent.UserRetry).phase)
    }
}
