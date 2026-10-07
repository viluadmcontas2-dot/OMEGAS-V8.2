package com.omegas.prohub.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe de prova 1/2: a politica do purge antes de cada transacao nunca prende nem derruba o laco. */
class SerialPurgePolicyTest {
    @Test
    fun `purge de hardware comeca ligado e purge rapido nao desliga nada`() {
        val policy = SerialPurgePolicy()
        assertTrue(policy.hardwareEnabled)
        repeat(1_000) { assertNull(policy.recordHardware(2L, true)) } // ~1,8 ms mediana no original
        assertTrue(policy.hardwareEnabled)
        assertEquals(1_000L, policy.purges)
    }

    @Test
    fun `driver que recusa o purge de hardware desliga na hora e avisa uma vez`() {
        val policy = SerialPurgePolicy()
        val message = policy.recordHardware(1L, false)
        assertNotNull(message)
        assertTrue(message!!.contains("só a limpeza da fila RX"))
        assertFalse(policy.hardwareEnabled)
        assertNull(policy.recordHardware(1L, false)) // sem repetir o aviso
    }

    @Test
    fun `purge lento tres vezes seguidas desliga, uma vez lento so conta`() {
        val policy = SerialPurgePolicy(slowMs = 40, slowStrikesToDisable = 3)
        assertNull(policy.recordHardware(90L, true))
        assertNull(policy.recordHardware(90L, true))
        assertNull(policy.recordHardware(5L, true)) // um rapido zera a contagem
        assertNull(policy.recordHardware(90L, true))
        assertNull(policy.recordHardware(90L, true))
        assertTrue(policy.hardwareEnabled)
        val message = policy.recordHardware(90L, true)
        assertNotNull(message)
        assertFalse(policy.hardwareEnabled)
        assertTrue(policy.disabledReason.contains("lento"))
    }

    @Test
    fun `nova conexao tenta o purge de hardware de novo`() {
        val policy = SerialPurgePolicy()
        policy.recordHardware(1L, false)
        assertFalse(policy.hardwareEnabled)
        policy.reset()
        assertTrue(policy.hardwareEnabled)
        assertEquals("", policy.disabledReason)
    }

    @Test
    fun `sobra de resposta atrasada e contada e logada sem inundar o registro`() {
        val policy = SerialPurgePolicy(staleLogEvery = 50)
        assertFalse(policy.recordStale(0)) // nada a descartar: silencioso
        assertTrue(policy.recordStale(7)) // primeira vez: loga
        var logged = 0
        repeat(98) { if (policy.recordStale(3)) logged++ }
        assertEquals(1, logged) // o 50o evento
        assertEquals(7L + 98 * 3, policy.staleBytesTotal)
    }
}
