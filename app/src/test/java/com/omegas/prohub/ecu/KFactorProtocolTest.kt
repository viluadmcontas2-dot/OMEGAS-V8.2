package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KFactorProtocolTest {
    @Test
    fun `comandos de leitura reproduzem o ProgBase`() {
        assertArrayEquals(
            byteArrayOf(0x29, 0x61, 0x01, 0x8B.toByte()),
            KFactorProtocol.readFactors(),
        )
        assertArrayEquals(
            byteArrayOf(0x29, 0x4B, 0x01, 0x75),
            KFactorProtocol.readPetrolAxis(),
        )
    }

    @Test
    fun `escritas reais reproduzem checksum e little endian`() {
        assertArrayEquals(
            byteArrayOf(0x14, 0x61, 0x01, 0x09, 0xDD.toByte(), 0x34, 0x90.toByte()),
            KFactorProtocol.writeFactor(9, 0x34DD),
        )
        assertArrayEquals(
            byteArrayOf(0x14, 0x61, 0x01, 0x09, 0x6F, 0x42, 0x30),
            KFactorProtocol.writeFactor(9, 0x426F),
        )
    }

    @Test
    fun `Q14 e eixo usam as escalas observadas`() {
        assertEquals(1.0, KFactorProtocol.factorFromRaw(0x4000), 0.000001)
        assertEquals(0.8259887695, KFactorProtocol.factorFromRaw(0x34DD), 0.000001)
        assertEquals(0x426F, KFactorProtocol.rawFromFactor(1.03802490234375))
        assertEquals(5.0, KFactorProtocol.petrolMsFromAxisRaw(0x0A00), 0.000001)
    }

    @Test
    fun `conversao para Q14 trunca e nunca arredonda para o proximo raw`() {
        val justBelowNextRaw = (0x4000 + 0.999) / KFactorProtocol.Q14_ONE
        val exactNextRaw = 0x4001 / KFactorProtocol.Q14_ONE

        assertEquals(0x4000, KFactorProtocol.rawFromFactor(justBelowNextRaw))
        assertEquals(0x4001, KFactorProtocol.rawFromFactor(exactNextRaw))
    }

    @Test
    fun `faixa maxima e exatamente o u16 Q14`() {
        assertEquals(0xFFFF, KFactorProtocol.MAX_RAW)
        assertEquals(65_535.0 / 16_384.0, KFactorProtocol.MAX_FACTOR, 0.0)
        assertEquals(KFactorProtocol.MAX_RAW, KFactorProtocol.rawFromFactor(KFactorProtocol.MAX_FACTOR))
        assertEquals(KFactorProtocol.MAX_RAW, KFactorProtocol.rawFromFactor(4.0))
        assertEquals(KFactorProtocol.MAX_FACTOR, KFactorProtocol.factorFromRaw(KFactorProtocol.MAX_RAW), 0.0)
    }

    @Test
    fun `eixo real deixa de ser uniforme depois de dez milissegundos`() {
        assertEquals(30, KFactorProtocol.OBSERVED_PETROL_AXIS_MS.size)
        assertEquals(10.0, KFactorProtocol.OBSERVED_PETROL_AXIS_MS[19], 0.000001)
        assertEquals(11.0, KFactorProtocol.OBSERVED_PETROL_AXIS_MS[20], 0.000001)
        assertEquals(18.0, KFactorProtocol.OBSERVED_PETROL_AXIS_MS[27], 0.000001)
        assertEquals(20.0, KFactorProtocol.OBSERVED_PETROL_AXIS_MS[28], 0.000001)
        assertEquals(22.0, KFactorProtocol.OBSERVED_PETROL_AXIS_MS[29], 0.000001)
    }

    @Test
    fun `interpolacao usa os dois pontos reais vizinhos`() {
        val raw = IntArray(KFactorProtocol.POINT_COUNT) { 0x4000 }
        raw[28] = KFactorProtocol.rawFromFactor(1.0)
        raw[29] = KFactorProtocol.rawFromFactor(1.2)
        assertEquals(1.1, KFactorProtocol.interpolateFactor(21.0, raw), 0.0001)
    }

    @Test
    fun `decodifica trinta pontos little endian`() {
        val payload = ByteArray(KFactorProtocol.PAYLOAD_SIZE)
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            val raw = 0x4000 + index
            payload[index * 2] = (raw and 0xFF).toByte()
            payload[index * 2 + 1] = ((raw ushr 8) and 0xFF).toByte()
        }
        val decoded = KFactorProtocol.decodeRawPoints(payload)
        assertEquals(30, decoded.size)
        assertEquals(0x4000, decoded.first())
        assertEquals(0x401D, decoded.last())
    }
}
