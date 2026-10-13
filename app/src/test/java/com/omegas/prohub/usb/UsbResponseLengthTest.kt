package com.omegas.prohub.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbResponseLengthTest {
    // Original-derived synthetic replay, not captured ECU traffic.
    // Each stream includes a following ACK; a wrong count steals its bytes.
    @Test
    fun `96 consome um byte fixo e checksum sem invadir proximo frame`() {
        assertFirstFrame("96 01 97 53 00 53", "96 01 97")
        assertFirstFrame("96 FF 95 53 00 53", "96 FF 95")
    }

    @Test
    fun `CA consome metadado codigo e checksum independentemente do metadado`() {
        assertFirstFrame("CA 01 08 D3 53 00 53", "CA 01 08 D3")
        assertFirstFrame("CA 00 10 DA 53 00 53", "CA 00 10 DA")
        assertFirstFrame("CA FF 08 D1 53 00 53", "CA FF 08 D1")
    }

    @Test
    fun `ACK preserva comprimento variavel incluindo payload vazio`() {
        assertFirstFrame("53 00 53 53 00 53", "53 00 53")
        assertFirstFrame("53 02 34 12 9B 53 00 53", "53 02 34 12 9B")
    }

    private fun assertFirstFrame(streamHex: String, expectedHex: String) {
        val stream = hex(streamHex)
        val status = stream[0].toInt() and 0xFF
        val metadata = stream[1].toInt() and 0xFF
        val consumed = 2 + usbResponsePayloadLength(status, metadata) + 1
        val expected = hex(expectedHex)
        assertEquals(expectedHex, expected.size, consumed)
        assertArrayEquals(expected, stream.copyOfRange(0, consumed))
        assertArrayEquals(hex("53 00 53"), stream.copyOfRange(consumed, stream.size))
    }

    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
