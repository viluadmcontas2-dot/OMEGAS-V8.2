package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalProtocolTest {
    @Test
    fun `quadros conhecidos sao gerados exatamente`() {
        assertArrayEquals(hex("09 73 01 7D"), AutoCalProtocol.read(AutoCalProtocol.MODULE_VERSION))
        assertArrayEquals(hex("29 4B 01 75"), AutoCalProtocol.read(AutoCalProtocol.PETR_INJ_TBP))
        assertArrayEquals(hex("29 4C 01 76"), AutoCalProtocol.read(AutoCalProtocol.MNFLD_PRESS_THD))
        assertArrayEquals(hex("29 61 01 8B"), AutoCalProtocol.read(AutoCalProtocol.MUL_ACT))
        assertArrayEquals(hex("09 74 01 7E"), AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED))
    }

    @Test
    fun `vetor u16 usa dimensao real de dezoito ou trinta`() {
        val payload18 = ByteArray(36) { index -> index.toByte() }
        val decoded18 = AutoCalProtocol.decode(
            AutoCalProtocol.PETR_INJ_TBUF,
            Mp48Protocol.STATUS_ACK,
            payload18,
        )
        assertEquals(18, decoded18.elementCount)
        assertEquals(0x0100, decoded18.rawValues[0])

        val payload30 = ByteArray(60) { index -> index.toByte() }
        val decoded30 = AutoCalProtocol.decode(
            AutoCalProtocol.PETR_INJ_TBP,
            Mp48Protocol.STATUS_ACK,
            payload30,
        )
        assertEquals(30, decoded30.elementCount)
        assertEquals(0x3B3A, decoded30.rawValues.last())
    }

    @Test
    fun `module version quatro promove somente os quatro vetores dinamicos para trinta`() {
        val dynamic = listOf(
            AutoCalProtocol.PETR_INJ_TBP,
            AutoCalProtocol.MUL_ACT,
            AutoCalProtocol.PETR_MNFLD_PRESS_RV,
            AutoCalProtocol.GAS_MNFLD_PRESS_RV,
        )
        dynamic.forEach { field ->
            assertEquals(30, AutoCalProtocol.expectedElements(field, 4))
            assertEquals(18, AutoCalProtocol.expectedElements(field, 3))
            assertEquals(null, AutoCalProtocol.expectedElements(field, null))
        }
        assertEquals(18, AutoCalProtocol.expectedElements(AutoCalProtocol.NUM_BUF_UPD_PETR, 4))
        assertEquals(18, AutoCalProtocol.expectedElements(AutoCalProtocol.NUM_BUF_UPD_GAS, 3))
    }

    @Test
    fun `validacao de forma rejeita vetor dinamico incompatível com a versao`() {
        val eighteen = AutoCalProtocol.decode(
            AutoCalProtocol.MUL_ACT,
            Mp48Protocol.STATUS_ACK,
            ByteArray(36),
        )
        val thirty = AutoCalProtocol.decode(
            AutoCalProtocol.MUL_ACT,
            Mp48Protocol.STATUS_ACK,
            ByteArray(60),
        )
        AutoCalProtocol.requireExpectedShape(thirty, 4)
        AutoCalProtocol.requireExpectedShape(eighteen, 3)
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.requireExpectedShape(eighteen, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.requireExpectedShape(thirty, 3)
        }
    }

    @Test
    fun `map s16 preserva sinais e conversao`() {
        val decoded = AutoCalProtocol.decode(
            AutoCalProtocol.MNFLD_PRESS_BUF,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x04, 0x00, 0xFC.toByte()),
        )
        assertArrayEquals(intArrayOf(1024, -1024), decoded.rawValues)
        assertEquals(1.0, decoded.physicalValues[0], 0.0)
        assertEquals(-1.0, decoded.physicalValues[1], 0.0)
    }

    @Test
    fun `tempo e q14 usam escalas AutoCal separadas`() {
        val time = AutoCalProtocol.decode(
            AutoCalProtocol.PETR_INJ_TBP,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x02),
        )
        assertEquals(512, time.rawValues.single())
        assertEquals(1.0, time.physicalValues.single(), 0.0)

        val factor = AutoCalProtocol.decode(
            AutoCalProtocol.MUL_ACT,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x40),
        )
        assertEquals(0x4000, factor.rawValues.single())
        assertEquals(1.0, factor.physicalValues.single(), 0.0)
    }

    @Test
    fun `contadores 015B e 015C usam dezoito palavras u16 little endian`() {
        val payload = ByteArray(36).also {
            it[0] = 0x0A
            it[2] = 0x02
            it[3] = 0x01
            it[34] = 0x34
            it[35] = 0x12
        }
        listOf(AutoCalProtocol.NUM_BUF_UPD_PETR, AutoCalProtocol.NUM_BUF_UPD_GAS).forEach { field ->
            val decoded = AutoCalProtocol.decode(
                field,
                Mp48Protocol.STATUS_ACK,
                payload,
            )
            assertEquals(18, decoded.elementCount)
            assertEquals(10, decoded.rawValues[0])
            assertEquals(0x0102, decoded.rawValues[1])
            assertEquals(0x1234, decoded.rawValues.last())
            AutoCalProtocol.requireExpectedShape(decoded, 4)
        }
    }

    @Test
    fun `contador u16 rejeita payload com largura quebrada`() {
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(
                AutoCalProtocol.NUM_BUF_UPD_PETR,
                Mp48Protocol.STATUS_ACK,
                ByteArray(35),
            )
        }
    }

    @Test
    fun `status payload vazio tamanho quebrado e escalar multiplo sao rejeitados`() {
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(AutoCalProtocol.MUL_ACT, 0x54, byteArrayOf(0, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(AutoCalProtocol.MUL_ACT, Mp48Protocol.STATUS_ACK, byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(AutoCalProtocol.MUL_ACT, Mp48Protocol.STATUS_ACK, byteArrayOf(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(
                AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
                Mp48Protocol.STATUS_ACK,
                byteArrayOf(1, 0, 2, 0),
            )
        }
    }

    @Test
    fun `contrato inicial possui somente leituras conhecidas`() {
        assertEquals(AutoCalProtocol.MODULE_VERSION, AutoCalProtocol.READ_ONLY_FIELDS.first())
        assertTrue(AutoCalProtocol.READ_ONLY_FIELDS.isNotEmpty())
        AutoCalProtocol.READ_ONLY_FIELDS.forEach { field ->
            val request = AutoCalProtocol.read(field)
            assertTrue((request[0].toInt() and 0xFF) in setOf(0x09, 0x0A, 0x29))
            assertEquals(Mp48Protocol.checksum(request.copyOfRange(0, request.lastIndex)), request.last().toInt() and 0xFF)
        }
    }

    private fun hex(value: String): ByteArray = value.split(' ')
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
