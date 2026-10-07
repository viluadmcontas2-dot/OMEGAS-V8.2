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
        assertArrayEquals(hex("29 64 01 8E"), AutoCalProtocol.read(AutoCalProtocol.VECT_AUTOCAL_EE))
        assertArrayEquals(hex("0A 67 01 01 73"), AutoCalProtocol.read(AutoCalProtocol.EN_CDN_T_THD))
        assertArrayEquals(hex("09 69 01 73"), AutoCalProtocol.read(AutoCalProtocol.LIMIT_PRESSURE_MIN))
        assertArrayEquals(hex("09 6A 01 74"), AutoCalProtocol.read(AutoCalProtocol.LIMIT_PRESSURE_MAX))
        assertArrayEquals(hex("09 83 01 8D"), AutoCalProtocol.read(AutoCalProtocol.DIFF_ENG_SPD_THD))
        assertArrayEquals(hex("09 84 01 8E"), AutoCalProtocol.read(AutoCalProtocol.DELTA_ENG_SPD_THD))
        assertArrayEquals(hex("09 85 01 8F"), AutoCalProtocol.read(AutoCalProtocol.DIFF_MNFLD_PRESS_THD))
        assertArrayEquals(hex("09 86 01 90"), AutoCalProtocol.read(AutoCalProtocol.DELTA_MNFLD_PRESS_THD))
        assertArrayEquals(hex("09 87 01 91"), AutoCalProtocol.read(AutoCalProtocol.DIFF_PETR_TINJ_T_THD))
        assertArrayEquals(hex("09 88 01 92"), AutoCalProtocol.read(AutoCalProtocol.DELTA_PETR_INJ_T_THD))
        assertArrayEquals(hex("09 8B 01 95"), AutoCalProtocol.read(AutoCalProtocol.DISABLE_ACQ_BAND))
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
    fun `dimensao de vetor segue o objeto e nao module version`() {
        val reference30 = listOf(
            AutoCalProtocol.PETR_INJ_TBP,
            AutoCalProtocol.MUL_ACT,
            AutoCalProtocol.PETR_MNFLD_PRESS_RV,
            AutoCalProtocol.GAS_MNFLD_PRESS_RV,
        )
        reference30.forEach { field ->
            assertEquals(30, AutoCalProtocol.expectedElements(field, 4))
            assertEquals(30, AutoCalProtocol.expectedElements(field, 3))
            assertEquals(30, AutoCalProtocol.expectedElements(field, 100))
            assertEquals(30, AutoCalProtocol.expectedElements(field, null))
        }
        assertEquals(18, AutoCalProtocol.expectedElements(AutoCalProtocol.NUM_BUF_UPD_PETR, 4))
        assertEquals(18, AutoCalProtocol.expectedElements(AutoCalProtocol.NUM_BUF_UPD_GAS, 100))
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
        AutoCalProtocol.requireExpectedShape(thirty, 100)
        AutoCalProtocol.requireExpectedShape(thirty, null)
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.requireExpectedShape(eighteen, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.requireExpectedShape(eighteen, 100)
        }
    }

    @Test
    fun `erro de forma cita a forma fixa do campo e nao MODULE_VERSION`() {
        val eighteen = AutoCalProtocol.decode(AutoCalProtocol.MUL_ACT, Mp48Protocol.STATUS_ACK, ByteArray(36))
        val error = assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.requireExpectedShape(eighteen, 4)
        }
        val message = error.message.orEmpty()
        assertTrue(message, message.contains("MUL_ACT"))
        assertTrue(message, message.contains("18"))
        assertTrue(message, message.contains("30"))
        assertTrue("a dimensão não depende de MODULE_VERSION: $message", !message.contains("MODULE_VERSION"))
    }

    @Test
    fun `reset da curva K nunca escreve indice fora do vetor MUL_ACT`() {
        assertEquals(30, AutoCalProtocol.resetKFactorMulActFrames().size)
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.resetKFactorMulActFrames(31)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.resetKFactorMulActFrames(0)
        }
    }

    @Test
    fun `acoes manuais progbase geram frames exatos`() {
        assertArrayEquals(hex("02 24 04 01 2B"), AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_PETROL))
        assertArrayEquals(hex("02 24 04 02 2C"), AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_GAS))
        assertArrayEquals(hex("02 24 04 04 2E"), AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_ALL))
        assertEquals(listOf("RESET_PETROL", "RESET_GAS", "RESET_ALL"), AutoCalProtocol.ManualActionMode.entries.map { it.name })
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
    fun `set vector estendido segue gramatica progbase`() {
        val fullMulAct = AutoCalProtocol.writeVectorU16(0x0161, IntArray(30) { 1000 })
        assertEquals(65, fullMulAct.size)
        assertArrayEquals(hex("37 61 3E 01"), fullMulAct.copyOfRange(0, 4))
        assertEquals(
            Mp48Protocol.checksum(fullMulAct.copyOfRange(0, fullMulAct.lastIndex)),
            fullMulAct.last().toInt() and 0xFF,
        )
    }

    @Test
    fun `vetor eeprom autocal e separado do mul act live`() {
        val decoded = AutoCalProtocol.decode(
            AutoCalProtocol.VECT_AUTOCAL_EE,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0),
        )
        assertEquals(4, decoded.elementCount)
        assertArrayEquals(intArrayOf(1, 2, 3, 4), decoded.rawValues)
        AutoCalProtocol.requireExpectedShape(decoded, null)
        assertEquals(0x0164, AutoCalProtocol.VECT_AUTOCAL_EE.address)
        assertEquals(0x0161, AutoCalProtocol.MUL_ACT.address)
        assertEquals(30, AutoCalProtocol.MUL_ACT.expectedElementsHint)
    }

    @Test
    fun `dfm fecha en cdn indexado e limites de pressao signed raw`() {
        val en = AutoCalProtocol.decode(
            AutoCalProtocol.EN_CDN_T_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x34, 0x12),
        )
        assertEquals(0x1234, en.rawValues.single())

        val min = AutoCalProtocol.decode(
            AutoCalProtocol.LIMIT_PRESSURE_MIN,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0xFC.toByte()),
        )
        assertEquals(-1024, min.rawValues.single())

        val max = AutoCalProtocol.decode(
            AutoCalProtocol.LIMIT_PRESSURE_MAX,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x04),
        )
        assertEquals(1024, max.rawValues.single())
    }

    @Test
    fun `thresholds progbase decodificam escala fisica exata`() {
        val diffRpm = AutoCalProtocol.decode(
            AutoCalProtocol.DIFF_ENG_SPD_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x90.toByte(), 0x01),
        )
        assertEquals(400, diffRpm.rawValues.single())
        assertEquals(400.0, diffRpm.physicalValues.single(), 0.0)

        val deltaRpm = AutoCalProtocol.decode(
            AutoCalProtocol.DELTA_ENG_SPD_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0xC8.toByte(), 0x00),
        )
        assertEquals(200.0, deltaRpm.physicalValues.single(), 0.0)

        val diffMap = AutoCalProtocol.decode(
            AutoCalProtocol.DIFF_MNFLD_PRESS_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x02),
        )
        assertEquals(0.5, diffMap.physicalValues.single(), 0.0)

        val deltaMap = AutoCalProtocol.decode(
            AutoCalProtocol.DELTA_MNFLD_PRESS_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x33, 0x00),
        )
        assertEquals(51.0 / 1024.0, deltaMap.physicalValues.single(), 0.0)

        val diffInj = AutoCalProtocol.decode(
            AutoCalProtocol.DIFF_PETR_TINJ_T_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x08),
        )
        assertEquals(4.0, diffInj.physicalValues.single(), 0.0)

        val deltaInj = AutoCalProtocol.decode(
            AutoCalProtocol.DELTA_PETR_INJ_T_THD,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x00, 0x02),
        )
        assertEquals(1.0, deltaInj.physicalValues.single(), 0.0)

        val disabledBand = AutoCalProtocol.decode(
            AutoCalProtocol.DISABLE_ACQ_BAND,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x01),
        )
        assertEquals(1, disabledBand.rawValues.single())
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
    fun `finish autocal grava max automatch no contador nativo`() {
        assertEquals(0x0165, AutoCalProtocol.MAX_AUTOMATCH.address)
        assertEquals(2, AutoCalProtocol.MAX_AUTOMATCH.index)
        assertEquals(0x0174, AutoCalProtocol.NUM_AUTOMATCH_EXECUTED.address)
        assertArrayEquals(
            hex("12 74 01 03 8A"),
            AutoCalProtocol.finishAutoCalCommit(3, 1),
        )
        assertArrayEquals(
            hex("13 74 01 03 00 8B"),
            AutoCalProtocol.finishAutoCalCommit(3, 2),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.finishAutoCalCommit(3, 4)
        }
    }

    @Test
    fun `contador automatch aceita payload observado u8 e firmware u16`() {
        val oneByte = AutoCalProtocol.decode(
            AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x7F),
        )
        assertEquals(127, oneByte.rawValues.single())

        val twoBytes = AutoCalProtocol.decode(
            AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            Mp48Protocol.STATUS_ACK,
            byteArrayOf(0x34, 0x12),
        )
        assertEquals(0x1234, twoBytes.rawValues.single())
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