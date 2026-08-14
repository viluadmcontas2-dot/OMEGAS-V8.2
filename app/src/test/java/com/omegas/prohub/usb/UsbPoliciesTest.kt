package com.omegas.prohub.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPoliciesTest {
    @Test
    fun `somente a identidade USB Omegas e aceita`() {
        assertTrue(OmegasUsbIdentity.matches(0x10C4, 0xEA60))
        assertFalse(OmegasUsbIdentity.matches(0x10C4, 0x0001))
        assertFalse(OmegasUsbIdentity.matches(0x1A86, 0x7523))
        assertFalse(OmegasUsbIdentity.matches(0x0403, 0x6001))
        assertFalse(OmegasUsbIdentity.matches(0x067B, 0x2303))
    }

    @Test
    fun `falha de transporte exige nova geracao USB`() {
        val first = UsbRecoveryPolicy.decide(true, true, false, 0)
        val later = UsbRecoveryPolicy.decide(true, true, false, 3)

        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, first.action)
        assertEquals(0L, first.delayMs)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, later.action)
        assertEquals(0L, later.delayMs)
    }

    @Test
    fun `detach manual ausencia ou auto reconnect desligado permanecem fail closed`() {
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(false, true, false, 0).action)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(true, false, false, 0).action)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(true, true, true, 0).action)
    }
}
