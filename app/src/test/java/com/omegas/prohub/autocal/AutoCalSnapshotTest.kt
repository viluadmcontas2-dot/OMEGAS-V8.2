package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalSnapshotTest {
    @Test
    fun `snapshot parcial preserva campos validos e avisos`() {
        val observations = listOf(
            observation(AutoCalProtocol.PETR_INJ_TBP, byteArrayOf(0x00, 0x02), 100L),
            AutoCalReadObservation(
                field = AutoCalProtocol.MUL_ACT,
                capturedAtMs = 110L,
                error = "timeout",
            ),
        )
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = listOf(AutoCalProtocol.PETR_INJ_TBP, AutoCalProtocol.MUL_ACT),
            sessionId = "session-1",
            startedAtMs = 90L,
            finishedAtMs = 120L,
        )

        assertTrue(snapshot.partial)
        assertEquals(1, snapshot.validFieldCount)
        assertEquals(2, snapshot.fields.size)
        assertEquals(1.0, snapshot.field(AutoCalProtocol.PETR_INJ_TBP)!!.physicalValues.single(), 0.0)
        assertEquals(AutoCalFieldStatus.UNAVAILABLE, snapshot.field(AutoCalProtocol.MUL_ACT)!!.status)
        assertTrue(snapshot.warnings.any { it.contains("MUL_ACT") })
        assertTrue(snapshot.warnings.any { it.contains("MODULE_VERSION") })
        assertEquals(30L, snapshot.durationMs)
        assertFalse(snapshot.toJson().getBoolean("automatic"))
    }

    @Test
    fun `resposta de tamanho invalido marca somente aquele campo`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.MUL_ACT, byteArrayOf(0x00), 100L),
                observation(AutoCalProtocol.AUTO_CAL_ENABLE, byteArrayOf(1), 101L),
            ),
            expectedFields = listOf(AutoCalProtocol.MUL_ACT, AutoCalProtocol.AUTO_CAL_ENABLE),
        )
        assertEquals(AutoCalFieldStatus.INVALID, snapshot.field(AutoCalProtocol.MUL_ACT)!!.status)
        assertEquals(AutoCalFieldStatus.VALID, snapshot.field(AutoCalProtocol.AUTO_CAL_ENABLE)!!.status)
    }

    @Test
    fun `contador automatch aceita payload real de um byte`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED, byteArrayOf(7), 100L),
            ),
            expectedFields = listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
        )
        val field = snapshot.field(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)!!
        assertEquals(AutoCalFieldStatus.VALID, field.status)
        assertEquals(7, field.rawValues.single())
    }

    @Test
    fun `contador automatch preserva compatibilidade com dois bytes`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED, byteArrayOf(0x34, 0x12), 100L),
            ),
            expectedFields = listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
        )
        assertEquals(0x1234, snapshot.field(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)!!.rawValues.single())
    }

    @Test
    fun `campos indexados no mesmo endereco permanecem distintos`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.VECT_AUTOCAL_U8_1, byteArrayOf(3), 100L),
                observation(AutoCalProtocol.VECT_AUTOCAL_U8_2, byteArrayOf(9), 101L),
            ),
            expectedFields = listOf(AutoCalProtocol.VECT_AUTOCAL_U8_1, AutoCalProtocol.VECT_AUTOCAL_U8_2),
        )
        assertEquals(2, snapshot.fields.size)
        assertEquals(3, snapshot.field(AutoCalProtocol.VECT_AUTOCAL_U8_1)!!.rawValues.single())
        assertEquals(9, snapshot.field(AutoCalProtocol.VECT_AUTOCAL_U8_2)!!.rawValues.single())
        val jsonFields = snapshot.toJson().getJSONArray("fields")
        assertEquals(2, jsonFields.length())
        assertNotEquals(
            jsonFields.getJSONObject(0).getString("identity"),
            jsonFields.getJSONObject(1).getString("identity"),
        )
    }

    @Test
    fun `module version quatro valida trinta elementos nos vetores dinamicos`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.MODULE_VERSION, byteArrayOf(4), 90L),
                observation(AutoCalProtocol.PETR_INJ_TBP, ByteArray(60), 100L),
                observation(AutoCalProtocol.MUL_ACT, ByteArray(36), 110L),
            ),
            expectedFields = listOf(
                AutoCalProtocol.MODULE_VERSION,
                AutoCalProtocol.PETR_INJ_TBP,
                AutoCalProtocol.MUL_ACT,
            ),
        )
        assertEquals(4, snapshot.moduleVersion)
        assertEquals(4, snapshot.toJson().getInt("moduleVersion"))
        assertEquals(AutoCalFieldStatus.VALID, snapshot.field(AutoCalProtocol.PETR_INJ_TBP)!!.status)
        assertEquals(AutoCalFieldStatus.INVALID, snapshot.field(AutoCalProtocol.MUL_ACT)!!.status)
        assertTrue(snapshot.field(AutoCalProtocol.MUL_ACT)!!.error!!.contains("esperado 30"))
    }

    @Test
    fun `versao anterior valida dezoito elementos nos vetores dinamicos`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.MODULE_VERSION, byteArrayOf(3), 90L),
                observation(AutoCalProtocol.PETR_INJ_TBP, ByteArray(36), 100L),
                observation(AutoCalProtocol.MUL_ACT, ByteArray(60), 110L),
            ),
            expectedFields = listOf(
                AutoCalProtocol.MODULE_VERSION,
                AutoCalProtocol.PETR_INJ_TBP,
                AutoCalProtocol.MUL_ACT,
            ),
        )
        assertEquals(3, snapshot.moduleVersion)
        assertEquals(AutoCalFieldStatus.VALID, snapshot.field(AutoCalProtocol.PETR_INJ_TBP)!!.status)
        assertEquals(AutoCalFieldStatus.INVALID, snapshot.field(AutoCalProtocol.MUL_ACT)!!.status)
        assertTrue(snapshot.field(AutoCalProtocol.MUL_ACT)!!.error!!.contains("esperado 18"))
    }

    @Test
    fun `hash e estavel independentemente da ordem`() {
        val first = observation(AutoCalProtocol.AUTO_CAL_ENABLE, byteArrayOf(1), 100L)
        val second = observation(AutoCalProtocol.MUL_ACT, byteArrayOf(0x00, 0x40), 101L)
        val expected = listOf(AutoCalProtocol.AUTO_CAL_ENABLE, AutoCalProtocol.MUL_ACT)
        val a = AutoCalSnapshotBuilder.build(listOf(first, second), expected, sessionId = "a")
        val b = AutoCalSnapshotBuilder.build(listOf(second, first), expected.reversed(), sessionId = "b")
        assertEquals(a.snapshotHash, b.snapshotHash)
    }

    @Test
    fun `hash muda quando bytes crus mudam`() {
        val expected = listOf(AutoCalProtocol.MUL_ACT)
        val one = AutoCalSnapshotBuilder.build(
            listOf(observation(AutoCalProtocol.MUL_ACT, byteArrayOf(0x00, 0x40), 100L)),
            expected,
        )
        val changed = AutoCalSnapshotBuilder.build(
            listOf(observation(AutoCalProtocol.MUL_ACT, byteArrayOf(0x01, 0x40), 100L)),
            expected,
        )
        assertNotEquals(one.snapshotHash, changed.snapshotHash)
    }

    @Test
    fun `campo ausente fica indisponivel sem invalidar snapshot`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = emptyList(),
            expectedFields = listOf(AutoCalProtocol.PETR_INJ_TBP),
            sessionId = "missing",
            startedAtMs = 50L,
            finishedAtMs = 50L,
        )
        assertTrue(snapshot.partial)
        assertEquals(AutoCalFieldStatus.UNAVAILABLE, snapshot.field(AutoCalProtocol.PETR_INJ_TBP)!!.status)
    }

    @Test
    fun `snapshot global de 1502 ms nao invalida grupo critico coerente`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.PETR_INJ_TBP, ByteArray(60), 100L),
                observation(AutoCalProtocol.MNFLD_PRESS_THD, ByteArray(36), 200L),
                observation(AutoCalProtocol.MUL_ACT, ByteArray(60), 300L),
                observation(AutoCalProtocol.PETR_MNFLD_PRESS_RV, ByteArray(60), 400L),
                observation(AutoCalProtocol.GAS_MNFLD_PRESS_RV, ByteArray(60), 500L),
                observation(AutoCalProtocol.AUTO_CAL_ENABLE, byteArrayOf(1), 1_602L),
            ),
            expectedFields = listOf(
                AutoCalProtocol.PETR_INJ_TBP,
                AutoCalProtocol.MNFLD_PRESS_THD,
                AutoCalProtocol.MUL_ACT,
                AutoCalProtocol.PETR_MNFLD_PRESS_RV,
                AutoCalProtocol.GAS_MNFLD_PRESS_RV,
                AutoCalProtocol.AUTO_CAL_ENABLE,
            ),
        )
        assertEquals(1_502L, snapshot.validFieldSpanMs)
        assertTrue(snapshot.temporalCoherent)
        assertTrue(snapshot.coherenceGroups.single { it.key == "AUTOMATCH_CURVES" }.coherent)
    }

    @Test
    fun `grupo automatch realmente distante e rejeitado`() {
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                observation(AutoCalProtocol.PETR_INJ_TBP, ByteArray(60), 100L),
                observation(AutoCalProtocol.MNFLD_PRESS_THD, ByteArray(36), 200L),
                observation(AutoCalProtocol.MUL_ACT, ByteArray(60), 300L),
                observation(AutoCalProtocol.PETR_MNFLD_PRESS_RV, ByteArray(60), 400L),
                observation(AutoCalProtocol.GAS_MNFLD_PRESS_RV, ByteArray(60), 2_501L),
            ),
            expectedFields = listOf(
                AutoCalProtocol.PETR_INJ_TBP,
                AutoCalProtocol.MNFLD_PRESS_THD,
                AutoCalProtocol.MUL_ACT,
                AutoCalProtocol.PETR_MNFLD_PRESS_RV,
                AutoCalProtocol.GAS_MNFLD_PRESS_RV,
            ),
        )
        assertFalse(snapshot.temporalCoherent)
        assertTrue(snapshot.warnings.any { it.contains("AUTOMATCH_CURVES") })
    }

    private fun observation(
        field: AutoCalProtocol.Field,
        payload: ByteArray,
        at: Long,
    ) = AutoCalReadObservation(
        field = field,
        status = Mp48Protocol.STATUS_ACK,
        payload = payload,
        capturedAtMs = at,
    )
}
