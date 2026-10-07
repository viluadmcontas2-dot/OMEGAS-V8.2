package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe de prova 1 (contrato) e 3 (replay dos bytes reais do PortmonLOGNOVO). */
class KMapEcuAxesTest {
    // PortmonLOGNOVO, quadro 35 (1,486 s): 29 37 00 60 -> 53 18 <24 bytes> ck
    private val timePayload = hex(
        "0D 03 D1 03 94 04 57 05 DE 06 28 09 35 0C 42 0F 4F 12 5D 15 6A 18 77 1B",
    )

    // PortmonLOGNOVO, quadro 36 (1,523 s): primeira leitura, ECU ainda de fabrica (1000...6500).
    private val rpmFactory = hex(
        "E8 03 DC 05 D0 07 C4 09 B8 0B AC 0D A0 0F 94 11 88 13 7C 15 70 17 64 19",
    )

    // Mesmo eixo depois de o dono reescrever (850...6500).
    private val rpmOwner = payloadOf(intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500))

    @Test
    fun `os pedidos de leitura sao exatamente os do ProgBase`() {
        assertArrayEquals(hex("29 37 00 60"), KMapEcuAxes.READ_TIME)
        assertArrayEquals(hex("29 3D 00 66"), KMapEcuAxes.READ_RPM)
        // So leitura: opcode 0x29 (ler vetor), nunca um opcode de escrita (0x14/0x35/0x37...).
        assertEquals(0x29, KMapEcuAxes.READ_TIME[0].toInt())
        assertEquals(0x29, KMapEcuAxes.READ_RPM[0].toInt())
    }

    @Test
    fun `eixo de tempo do log real bate com o contrato fixo`() {
        val ms = KMapEcuAxes.decodePetrolMs(ack(timePayload))
        assertArrayEquals(KMapPhysicalAxes.petrolBins(), ms, 0.0)
    }

    @Test
    fun `eixo de rpm de fabrica do log real e usado e difere do contrato fixo`() {
        val resolved = KMapEcuAxes.resolve(ack(rpmFactory), ack(timePayload))
        assertArrayEquals(intArrayOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500), resolved.rpmBins)
        assertEquals(KMapEcuAxes.SOURCE_ECU, resolved.source)
        assertTrue(resolved.differsFromLock)
        assertTrue(resolved.summary().contains("DIFERENTES"))
        val json = resolved.toJson()
        assertEquals(1000, json.getJSONArray("rpmBins").getInt(0))
        assertEquals("ECU", json.getString("source"))
        // O hash do contrato fixo continua no JSON; o eixo ativo e que muda.
        assertEquals(KMapPhysicalAxes.LOCK_SHA256, json.getString("lockSha256"))
    }

    @Test
    fun `eixo do dono lido da ecu e igual ao contrato fixo`() {
        val resolved = KMapEcuAxes.resolve(ack(rpmOwner), ack(timePayload))
        assertEquals(KMapEcuAxes.SOURCE_ECU, resolved.source)
        assertFalse(resolved.differsFromLock)
        assertTrue(resolved.notes.isEmpty())
    }

    @Test
    fun `recusa da ecu volta ao eixo fixo e diz por que`() {
        // CA 01 10: a ECU recusou o endereco. Erro da ECU, nao do transporte.
        val refused = KMapEcuAxes.Attempt(0xCA, byteArrayOf(0x10), "ECU retornou status 0xCA")
        val resolved = KMapEcuAxes.resolve(refused, refused)
        assertEquals(KMapEcuAxes.SOURCE_FIXED, resolved.source)
        assertArrayEquals(KMapPhysicalAxes.rpmBins(), resolved.rpmBins)
        assertArrayEquals(KMapPhysicalAxes.petrolBins(), resolved.petrolBins, 0.0)
        assertFalse(resolved.differsFromLock)
        assertEquals(2, resolved.notes.size)
        assertTrue(resolved.summary().contains("FIXOS"))
    }

    @Test
    fun `sem resposta de transporte volta ao fixo`() {
        val resolved = KMapEcuAxes.resolve(
            KMapEcuAxes.Attempt.failed("Timeout aguardando status"),
            KMapEcuAxes.Attempt.failed("Eco divergente ou incompleto"),
        )
        assertEquals(KMapEcuAxes.SOURCE_FIXED, resolved.source)
        assertTrue(resolved.notes.first().contains("Timeout"))
    }

    @Test
    fun `payload curto, nao crescente ou fora de faixa e invalido`() {
        val short = ack(rpmOwner.copyOf(22))
        val unsorted = ack(payloadOf(intArrayOf(850, 800, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)))
        val zeros = ack(ByteArray(24))
        val insane = ack(payloadOf(IntArray(12) { 20_000 + it }))
        listOf(short, unsorted, zeros, insane).forEach { bad ->
            val resolved = KMapEcuAxes.resolve(bad, bad)
            assertEquals(KMapEcuAxes.SOURCE_FIXED, resolved.source)
            assertArrayEquals(KMapPhysicalAxes.rpmBins(), resolved.rpmBins)
        }
    }

    @Test
    fun `um eixo valido e outro invalido da fonte parcial sem misturar os dois`() {
        val resolved = KMapEcuAxes.resolve(ack(rpmFactory), ack(ByteArray(24)))
        assertEquals(KMapEcuAxes.SOURCE_PARTIAL, resolved.source)
        assertEquals(1000, resolved.rpmBins[0])
        assertArrayEquals(KMapPhysicalAxes.petrolBins(), resolved.petrolBins, 0.0)
        assertEquals(1, resolved.notes.size)
    }

    @Test
    fun `resposta do log real fecha o checksum do quadro`() {
        val frame = byteArrayOf(0x53, 0x18) + timePayload
        val sum = frame.sumOf { it.toInt() and 0xFF } and 0xFF
        assertEquals(0xD1, sum) // 'D1' e o byte final da linha 29 37 00 60 do log
        assertEquals(0x60, Mp48Protocol.checksum(byteArrayOf(0x29, 0x37, 0x00)))
    }

    private fun ack(payload: ByteArray) = KMapEcuAxes.Attempt(Mp48Protocol.STATUS_ACK, payload)

    private fun payloadOf(values: IntArray): ByteArray =
        ByteArray(values.size * 2).also { out ->
            values.forEachIndexed { i, v ->
                out[i * 2] = (v and 0xFF).toByte()
                out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
        }

    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
