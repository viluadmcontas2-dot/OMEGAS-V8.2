package com.omegas.prohub.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbProtocolReplyTest {
    @Test
    fun `ack permanece classe de sucesso`() {
        val reply = UsbProtocolReply(ok = true, status = 0x53)
        assertEquals(UsbProtocolStatusClass.ACK, reply.statusClass)
        assertFalse(reply.retryable)
        assertFalse(reply.nonRetryable)
    }

    @Test
    fun `ca 01 08 e retryable`() {
        val reply = UsbProtocolReply(
            ok = false,
            status = 0xCA,
            payload = byteArrayOf(0x08),
            rawResponse = byteArrayOf(0xCA.toByte(), 0x01, 0x08, 0xD3.toByte()),
        )
        assertEquals(UsbProtocolStatusClass.EXTENDED_RETRYABLE, reply.statusClass)
        assertTrue(reply.retryable)
        assertFalse(reply.nonRetryable)
    }

    @Test
    fun `ca 01 10 e non retryable`() {
        val reply = UsbProtocolReply(
            ok = false,
            status = 0xCA,
            payload = byteArrayOf(0x10),
            rawResponse = byteArrayOf(0xCA.toByte(), 0x01, 0x10, 0xDB.toByte()),
        )
        assertEquals(UsbProtocolStatusClass.EXTENDED_NON_RETRYABLE, reply.statusClass)
        assertFalse(reply.retryable)
        assertTrue(reply.nonRetryable)
    }

    @Test
    fun `ca desconhecido nao ganha retry por suposicao`() {
        val reply = UsbProtocolReply(ok = false, status = 0xCA, payload = byteArrayOf(0x22))
        assertEquals(UsbProtocolStatusClass.EXTENDED_UNKNOWN, reply.statusClass)
        assertFalse(reply.retryable)
        assertFalse(reply.nonRetryable)
    }
}
