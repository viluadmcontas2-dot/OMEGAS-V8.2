package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** O quadro decodificado guarda os 34 bytes crus (export v2: nada se perde, nem os bytes que o app não decodifica). */
class Mp48RawPayloadTest {
    @Test
    fun `telemetria decodificada carrega os 34 bytes crus incluindo os nao decodificados`() {
        val payload = ByteArray(34)
        payload[0] = 0x84.toByte(); payload[1] = 0x03          // rpm 900
        payload[2] = 0x32; payload[3] = 0x1F                  // bytes 2-3, que o app nao decodifica
        payload[11] = 0x90.toByte(); payload[13] = 125; payload[17] = 0xC2.toByte(); payload[18] = 0x01
        val decoded = Mp48Protocol.decodeTelemetry(payload, 123L)
        assertEquals(900, decoded.rpm)
        assertArrayEquals(payload, decoded.raw)
        assertEquals(125, decoded.levelRaw)
    }

    @Test
    fun `payload maior que 34 bytes guarda so a janela decodificada`() {
        val extra = ByteArray(37) { (it + 1).toByte() }
        val decoded = Mp48Protocol.decodeTelemetry(extra, 1L)
        assertEquals(34, decoded.raw.size)
    }
}
