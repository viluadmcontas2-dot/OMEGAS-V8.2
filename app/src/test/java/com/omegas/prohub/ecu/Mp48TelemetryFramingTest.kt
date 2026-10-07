package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp48TelemetryFramingTest {
    /** Quadro real do ProgBase (o mesmo de Mp48TelemetryScaleTest). */
    private val realFrame = byteArrayOf(
        0x6B, 0x03, 0x90.toByte(), 0x24, 0x00, 0x00,
        0x15, 0x11, 0x53, 0x07, 0x00, 0x90.toByte(),
        0x2C, 0x7E, 0x09, 0x07, 0x45, 0xC4.toByte(),
        0x01, 0xE1.toByte(), 0x00, 0x00, 0x00, 0x00,
        0xFA.toByte(), 0x10, 0x00, 0x00, 0x48, 0x07,
        0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun `quadro real de 34 bytes continua aceito`() {
        assertEquals(Mp48Protocol.TELEMETRY_PAYLOAD_SIZE, realFrame.size)
        val telemetry = Mp48Protocol.decodeTelemetry(realFrame, 1L)
        assertEquals(875, telemetry.rpm)
        assertTrue(telemetry.plausible)
    }

    @Test
    fun `quadro com byte a mais na frente nao e realinhado por janela deslizante`() {
        // Antes a janela deslizante achava o quadro plausível no offset 1 e o aceitava como se fosse real.
        val misaligned = byteArrayOf(0x00) + realFrame
        assertThrows(IllegalArgumentException::class.java) {
            Mp48Protocol.decodeTelemetry(misaligned, 1L)
        }
    }

    @Test
    fun `quadro com bytes sobrando no fim tambem e rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            Mp48Protocol.decodeTelemetry(realFrame + byteArrayOf(0x55, 0x55), 1L)
        }
    }

    @Test
    fun `quadro curto continua rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            Mp48Protocol.decodeTelemetry(realFrame.copyOf(33), 1L)
        }
    }

    @Test
    fun `validacao da resposta exige o tamanho exato`() {
        assertTrue(Mp48Protocol.isTelemetryPayloadSize(34))
        assertTrue(!Mp48Protocol.isTelemetryPayloadSize(35))
        assertTrue(!Mp48Protocol.isTelemetryPayloadSize(33))
    }
}
