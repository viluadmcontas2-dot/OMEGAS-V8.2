package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalRefreshPlannerTest {
    @Test
    fun `full snapshot anchors the two and four second cadences`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.reset()
        planner.markFullSnapshot(10_000L)

        assertFalse(planner.due(11_999L).acquisition)
        assertFalse(planner.due(11_999L).reference)

        val atTwoSeconds = planner.due(12_000L)
        assertTrue(atTwoSeconds.acquisition)
        assertFalse(atTwoSeconds.reference)

        planner.markAcquisition(12_000L)
        assertFalse(planner.due(13_999L).acquisition)

        planner.markAcquisition(13_000L)
        val atFourSeconds = planner.due(14_000L)
        assertTrue(atFourSeconds.reference)
        assertFalse(atFourSeconds.acquisition)
    }

    @Test
    fun `failed refresh backs off exponentially instead of retrying every tick`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)
        assertTrue(planner.due(22_000L).acquisition)

        planner.markAcquisitionFailure(22_000L)
        assertFalse("1ª falha: espera 4 s", planner.due(25_999L).acquisition)
        assertTrue(planner.due(26_000L).acquisition)

        planner.markAcquisitionFailure(26_000L)
        assertFalse("2ª falha: espera 8 s", planner.due(33_999L).acquisition)
        assertTrue(planner.due(34_000L).acquisition)

        // Muitas falhas seguidas: o recuo para no teto de 30 s, nunca cresce sem limite.
        var now = 34_000L
        repeat(10) { planner.markAcquisitionFailure(now); now += 1L }
        assertFalse(planner.due(now + 29_000L).acquisition)
        assertTrue(planner.due(now + NativeAutoCalRefreshPlanner.BACKOFF_CAP_MS).acquisition)

        // Sucesso zera o recuo e volta à cadência de 2 s.
        planner.markAcquisition(100_000L)
        assertFalse(planner.due(101_999L).acquisition)
        assertTrue(planner.due(102_000L).acquisition)
    }

    @Test
    fun `reference failure backs off from four seconds and a full snapshot clears it`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(20_000L)
        planner.markReferenceFailure(24_000L)
        assertFalse(planner.due(31_999L).reference)
        assertTrue(planner.due(32_000L).reference)
        planner.markFullSnapshot(40_000L)
        assertFalse(planner.due(43_999L).reference)
        assertTrue(planner.due(44_000L).reference)
    }

    @Test
    fun `groups carry the same reads as the old single unit in at most three reads each`() {
        val groups = NativeAutoCalRefreshPlanner.Group.values()
        assertTrue(groups.all { it.fields.size in 1..NativeAutoCalRefreshPlanner.MAX_READS_PER_GROUP })
        val acquisition = groups.filter { it.family == NativeAutoCalRefreshPlanner.Family.ACQUISITION }.flatMap { it.fields }
        assertEquals(
            listOf(
                AutoCalProtocol.PETR_INJ_TBUF, AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalProtocol.NUM_BUF_UPD_PETR,
                AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV, AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV,
                AutoCalProtocol.PETR_INJ_TBUF_GAS, AutoCalProtocol.MNFLD_PRESS_BUF_GAS, AutoCalProtocol.NUM_BUF_UPD_GAS,
                AutoCalProtocol.ACQUIRED_ZONES_PETROL, AutoCalProtocol.ACQUIRED_ZONES_GAS,
            ),
            acquisition,
        )
        val reference = groups.filter { it.family == NativeAutoCalRefreshPlanner.Family.REFERENCE }.flatMap { it.fields }
        assertEquals(
            setOf(
                AutoCalProtocol.PETR_INJ_TBP, AutoCalProtocol.MNFLD_PRESS_THD, AutoCalProtocol.MUL_ACT,
                AutoCalProtocol.PETR_MNFLD_PRESS_RV, AutoCalProtocol.GAS_MNFLD_PRESS_RV,
            ),
            reference.toSet(),
        )
        assertEquals(5, reference.size)
    }

    @Test
    fun `a round walks the groups in order and only a confirmed group advances it`() {
        val planner = NativeAutoCalRefreshPlanner()
        assertNull("sem âncora de snapshot nada vence", planner.nextGroup(5_000L, acquisitionEnabled = true))
        planner.markFullSnapshot(10_000L)
        assertNull(planner.nextGroup(11_999L, acquisitionEnabled = true))

        val first = planner.nextGroup(12_000L, acquisitionEnabled = true)
        assertEquals(NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, first)
        // Enquanto não confirmado, devolve sempre o mesmo grupo (nenhum grupo é pulado).
        assertEquals(first, planner.nextGroup(12_300L, acquisitionEnabled = true))
        planner.groupDone(first!!)
        assertEquals(NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV, planner.nextGroup(12_400L, acquisitionEnabled = true))
        planner.groupDone(NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV)
        planner.groupDone(NativeAutoCalRefreshPlanner.Group.G4_GAS)
        planner.groupDone(NativeAutoCalRefreshPlanner.Group.G6_ZONES)
        assertFalse(planner.roundInProgress())
        // Rodada de 2 s medida de início a início.
        assertNull(planner.nextGroup(13_999L, acquisitionEnabled = true))
        assertEquals(NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, planner.nextGroup(14_000L, acquisitionEnabled = true))
    }

    @Test
    fun `the reference rides every second round and pause closes only the acquisition`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(10_000L)
        planner.nextGroup(14_000L, acquisitionEnabled = true) // abre rodada com aquisição + referência
        val remaining = planner.roundRemaining()
        assertEquals(
            listOf(
                NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV,
                NativeAutoCalRefreshPlanner.Group.G4_GAS, NativeAutoCalRefreshPlanner.Group.G6_ZONES,
                NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT, NativeAutoCalRefreshPlanner.Group.G7_PETROL_RV,
                NativeAutoCalRefreshPlanner.Group.G8_GAS_RV,
            ),
            remaining,
        )

        val paused = NativeAutoCalRefreshPlanner()
        paused.markFullSnapshot(10_000L)
        assertEquals(NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT, paused.nextGroup(14_000L, acquisitionEnabled = false))
        assertTrue(paused.roundRemaining().none { it.family == NativeAutoCalRefreshPlanner.Family.ACQUISITION })
    }

    @Test
    fun `a failed group drops the rest of its family and recedes - overdue groups never queue up`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(10_000L)
        planner.nextGroup(12_000L, acquisitionEnabled = true)
        planner.groupDone(NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS)
        planner.groupFailed(NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV, 12_500L)
        assertFalse("resto da aquisição descartado", planner.roundInProgress())
        assertFalse("1ª falha: aquisição só volta depois de 4 s", planner.due(16_499L).acquisition)
        assertTrue(planner.due(16_500L).acquisition)
        assertEquals(NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, planner.nextGroup(16_500L, acquisitionEnabled = true))
        // Ao fechar a rodada com sucesso o recuo zera (a referência vencida acompanhou a rodada).
        listOf(
            NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV,
            NativeAutoCalRefreshPlanner.Group.G4_GAS, NativeAutoCalRefreshPlanner.Group.G6_ZONES,
            NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT, NativeAutoCalRefreshPlanner.Group.G7_PETROL_RV,
            NativeAutoCalRefreshPlanner.Group.G8_GAS_RV,
        ).forEach { planner.groupDone(it) }
        assertFalse(planner.roundInProgress())
        assertEquals(NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS, planner.nextGroup(18_500L, acquisitionEnabled = true))
    }

    @Test
    fun `reference now makes the reference due immediately but only after a snapshot anchor`() {
        val planner = NativeAutoCalRefreshPlanner()
        planner.requestReferenceNow()
        assertFalse(planner.due(5_000L).reference)
        planner.markFullSnapshot(10_000L) // snapshot completo limpa o pedido (ele já leu tudo)
        assertFalse(planner.due(10_500L).reference)
        planner.requestReferenceNow()
        assertTrue(planner.due(10_500L).reference)
        assertEquals(NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT, planner.nextGroup(10_500L, acquisitionEnabled = true))
        assertFalse("pedido consumido ao abrir a rodada", planner.due(10_600L).reference)
    }
}
