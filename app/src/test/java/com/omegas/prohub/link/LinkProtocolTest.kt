package com.omegas.prohub.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkProtocolTest {
    @Test fun `versao atual e aceita`() {
        assertEquals("OMEGAS_LINK_V2", LinkProtocol.CURRENT)
        assertFalse(LinkProtocol.isOldPeer("OMEGAS_LINK_V2"))
    }

    @Test fun `APK antigo e reconhecido`() {
        assertTrue(LinkProtocol.isOldPeer("OMEGAS_LINK_V1"))
    }

    @Test fun `beacon alheio nao e APK antigo`() {
        assertFalse(LinkProtocol.isOldPeer(""))
        assertFalse(LinkProtocol.isOldPeer("SSDP"))
    }

    @Test fun `recusa traz frase humana e a versao atual`() {
        val r = LinkProtocol.oldPeerResponse()
        assertFalse(r.getBoolean("ok"))
        assertEquals("O outro aparelho tem uma versão antiga do OMEGAS. Atualize o app nele para voltar a sincronizar.", r.getString("error"))
        assertEquals("OMEGAS_LINK_V2", r.getString("protocol"))
    }
}
