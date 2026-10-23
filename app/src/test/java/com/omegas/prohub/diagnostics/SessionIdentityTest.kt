package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** ID de sessão ESTÁVEL (aparelho + boot + início monotônico), que não nasce da parede do relógio (que erra e salta). */
class SessionIdentityTest {
    @Test
    fun `chave nasce de aparelho, boot e inicio monotonico em segundos`() {
        assertEquals("S_abcd1234_9f8e7d6c_5000", SessionIdentity.sessionKey("abcd1234xyz", "9f8e7d6c-1111-2222-3333-444455556666", 5_000_999L))
    }

    @Test
    fun `mesma sessao reconstruida da o mesmo id, outro boot ou outro inicio da outro`() {
        val a = SessionIdentity.sessionKey("abcd1234", "9f8e7d6c-1", 70_000L)
        assertEquals(a, SessionIdentity.sessionKey("abcd1234", "9f8e7d6c-1", 70_100L))
        assertNotEquals(a, SessionIdentity.sessionKey("abcd1234", "00000000-1", 70_000L))
        assertNotEquals(a, SessionIdentity.sessionKey("abcd1234", "9f8e7d6c-1", 130_000L))
        assertNotEquals(a, SessionIdentity.sessionKey("ffff0000", "9f8e7d6c-1", 70_000L))
    }

    @Test
    fun `uuid e deterministico da chave`() {
        val k = "S_abcd1234_9f8e7d6c_70"
        assertEquals(UUID.nameUUIDFromBytes(k.toByteArray()).toString(), SessionIdentity.uuidOf(k))
        assertEquals(SessionIdentity.uuidOf(k), SessionIdentity.uuidOf(k))
    }

    @Test
    fun `boot id vem do kernel sem tracos, ou e derivado e marcado quando nao da para ler`() {
        assertEquals("9f8e7d6c", SessionIdentity.bootId({ "9f8e7d6c-aaaa-bbbb-cccc-ddddeeeeffff\n" }, wallMs = 1_790_000_000_000L, elapsedMs = 5_000L))
        val derived = SessionIdentity.bootId({ null }, wallMs = 1_790_000_000_000L, elapsedMs = 5_000L)
        assertTrue(derived.startsWith("d"))
        assertEquals(derived, SessionIdentity.bootId({ throw RuntimeException("sem permissao") }, 1_790_000_030_000L, 35_000L)) // mesmo boot, relogio avancou junto
        assertNotEquals(derived, SessionIdentity.bootId({ null }, 1_790_000_000_000L + 3_600_000L, 5_000L))
    }
}
