package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Mp48GeometryCodecTest {
    @Test
    fun `decoder preserva 12 raws U16 little endian`() {
        val expected = intArrayOf(
            0x0000,
            0x0001,
            0x00FF,
            0x0100,
            0x1234,
            0x7FFF,
            0x8000,
            0xABCD,
            0xFF00,
            0xFFFE,
            0xFFFF,
            0x4242,
        )
        val payload = ByteArray(expected.size * 2)
        expected.forEachIndexed { index, raw ->
            payload[index * 2] = (raw and 0xFF).toByte()
            payload[index * 2 + 1] = ((raw ushr 8) and 0xFF).toByte()
        }

        assertArrayEquals(expected, Mp48GeometryCodec.decodeAxisRaw(payload))
    }

    @Test
    fun `decoder rejeita payload truncado`() {
        assertThrows(IllegalArgumentException::class.java) {
            Mp48GeometryCodec.decodeAxisRaw(ByteArray(23))
        }
    }

    @Test
    fun `decoder rejeita payload com byte extra`() {
        assertThrows(IllegalArgumentException::class.java) {
            Mp48GeometryCodec.decodeAxisRaw(ByteArray(25))
        }
    }
}
