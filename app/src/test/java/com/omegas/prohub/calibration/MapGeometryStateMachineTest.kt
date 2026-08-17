package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class MapGeometryStateMachineTest {
    private val timeRaw = intArrayOf(781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031)
    private val timeMs = doubleArrayOf(1.99936, 2.50112, 3.00032, 3.49952, 4.50048, 6.00064, 8.0, 9.99936, 11.99872, 14.00064, 16.0, 17.99936)
    private val rpmRaw = intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)

    @Test
    fun `fluxo completo e absent reading known`() {
        val machine = MapGeometryStateMachine()
        assertEquals(MapGeometryStatus.ABSENT, machine.state.status)

        machine.beginRead()
        assertEquals(MapGeometryStatus.READING, machine.state.status)

        val snapshot = snapshot()
        machine.complete(snapshot)
        assertEquals(MapGeometryStatus.KNOWN, machine.state.status)
        assertSame(snapshot, machine.state.snapshot)
        assertEquals(true, machine.state.timeAxisAvailable)
        assertEquals(true, machine.state.rpmAxisAvailable)
    }

    @Test
    fun `falha de um vetor termina partial sem snapshot completo`() {
        val machine = MapGeometryStateMachine()
        machine.beginRead()
        machine.fail(timeAxisAvailable = true, rpmAxisAvailable = false, reason = "GIRI_PER_K falhou")

        assertEquals(MapGeometryStatus.PARTIAL, machine.state.status)
        assertNull(machine.state.snapshot)
        assertEquals(true, machine.state.timeAxisAvailable)
        assertEquals(false, machine.state.rpmAxisAvailable)
    }

    @Test
    fun `falha dos dois vetores termina unknown`() {
        val machine = MapGeometryStateMachine()
        machine.beginRead()
        machine.fail(timeAxisAvailable = false, rpmAxisAvailable = false, reason = "ECU sem resposta")

        assertEquals(MapGeometryStatus.UNKNOWN, machine.state.status)
        assertNull(machine.state.snapshot)
    }

    @Test
    fun `known nao pode ser fabricado fora de reading`() {
        val machine = MapGeometryStateMachine()
        assertThrows(IllegalStateException::class.java) { machine.complete(snapshot()) }
    }

    private fun snapshot(): MapGeometrySnapshot = MapGeometrySnapshot.create(
        timeAxisRaw = timeRaw,
        timeAxisMs = timeMs,
        rpmAxisRaw = rpmRaw,
        usbSessionId = 77L,
        provenance = MapGeometryProvenance.FULL_ECU_READ,
        completeness = MapGeometryCompleteness.KNOWN,
    )
}
