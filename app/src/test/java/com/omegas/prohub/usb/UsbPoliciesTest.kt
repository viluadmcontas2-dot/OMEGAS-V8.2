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
    fun `falha transitoria com Omegas ainda presente usa recuperacao rapida limitada`() {
        val first = UsbRecoveryPolicy.decide(true, true, false, 0)
        val second = UsbRecoveryPolicy.decide(true, true, false, 1)
        val third = UsbRecoveryPolicy.decide(true, true, false, 2)
        val exhausted = UsbRecoveryPolicy.decide(true, true, false, 3)

        assertEquals(UsbRecoveryAction.RETRY_TRANSPORT, first.action)
        assertEquals(250L, first.delayMs)
        assertEquals(750L, second.delayMs)
        assertEquals(1500L, third.delayMs)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, exhausted.action)
    }

    @Test
    fun `detach manual ou ausencia do Omegas nunca entram em recuperacao`() {
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(false, true, false, 0).action)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(true, false, false, 0).action)
        assertEquals(UsbRecoveryAction.HARD_DISCONNECT, UsbRecoveryPolicy.decide(true, true, true, 0).action)
    }
}
