package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun `eixo de tempo deriva ms sem alterar raws`() {
        val raw = intArrayOf(781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031)
        val original = raw.copyOf()
        val ms = Mp48GeometryCodec.timeAxisMs(raw)
        val expected = doubleArrayOf(
            1.99936,
            2.50112,
            3.00032,
            3.49952,
            4.50048,
            6.00064,
            8.0,
            9.99936,
            11.99872,
            14.00064,
            16.0,
            17.99936,
        )

        assertArrayEquals(original, raw)
        expected.indices.forEach { index ->
            assertEquals(expected[index], ms[index], 0.0000001)
        }
    }

    @Test
    fun `eixo de tempo rejeita cardinalidade que nao seja 12`() {
        assertThrows(IllegalArgumentException::class.java) {
            Mp48GeometryCodec.timeAxisMs(IntArray(11))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Mp48GeometryCodec.timeAxisMs(IntArray(13))
        }
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
