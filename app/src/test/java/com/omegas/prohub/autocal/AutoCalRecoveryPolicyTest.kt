package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalRecoveryPolicyTest {
    @Test fun `usb desconectado pede reconexao sem repetir escrita automaticamente`() {
        val recovery = AutoCalRecoveryPolicy.classify("USB desconectado durante a ação AutoCal")
        assertEquals("USB_DISCONNECTED", recovery.reasonCode)
        assertTrue(recovery.retryable)
        assertEquals("RECONNECT_AND_REPREPARE", recovery.nextActionCode)
        assertFalse(recovery.automaticRetry)
    }

    @Test fun `sessao mudou invalida preparacao antiga`() {
        val recovery = AutoCalRecoveryPolicy.classify("Sessão USB mudou durante a ação AutoCal")
        assertEquals("USB_SESSION_CHANGED", recovery.reasonCode)
        assertEquals("REPREPARE_CURRENT_SESSION", recovery.nextActionCode)
        assertTrue(recovery.retryable)
        assertFalse(recovery.automaticRetry)
    }

    @Test fun `readback divergente pede releitura antes de nova mutacao`() {
        val recovery = AutoCalRecoveryPolicy.classify("Readback AUTO_CAL_ENABLE divergente: esperado 1, ECU 0")
        assertEquals("READBACK_MISMATCH", recovery.reasonCode)
        assertEquals("REFRESH_BEFORE_RETRY", recovery.nextActionCode)
        assertFalse(recovery.automaticRetry)
    }

    @Test fun `ecu sem ack nao ganha retry automatico`() {
        val recovery = AutoCalRecoveryPolicy.classify("A ECU não confirmou o commit da readquisição")
        assertEquals("ECU_ACK_MISSING", recovery.reasonCode)
        assertTrue(recovery.retryable)
        assertFalse(recovery.automaticRetry)
    }

    @Test fun `erro desconhecido falha fechado`() {
        val recovery = AutoCalRecoveryPolicy.classify("erro estranho")
        assertEquals("UNKNOWN_FAILURE", recovery.reasonCode)
        assertFalse(recovery.retryable)
        assertEquals("INSPECT_TECHNICAL_DETAILS", recovery.nextActionCode)
        assertFalse(recovery.automaticRetry)
    }

    @Test fun `timeout de transporte nao vira ack da ecu ausente`() {
        val recovery = AutoCalRecoveryPolicy.classify("Timeout aguardando ACK da leitura")
        assertEquals("TRANSPORT_FAILURE", recovery.reasonCode)
        assertTrue(recovery.retryable)
        assertFalse(recovery.automaticRetry)
    }
}
