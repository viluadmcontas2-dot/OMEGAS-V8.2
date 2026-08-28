package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeCalibrationSnapshotTest {
    private fun raw(
        countEnd: Int = 4,
        mulEndDelta: Int = 0,
        rows: List<List<Int>> = List(13) { row -> List(12) { column -> (row * 12 + column) and 0xFF } },
        advertisedStable: Boolean = true,
    ): CompositeCalibrationRawRead {
        val factors = List(30) { 0x4000 + it }
        val factorsEnd = factors.toMutableList().also { it[17] += mulEndDelta }
        return CompositeCalibrationRawRead(
            usbSessionId = 77L,
            autoMatchCountStart = 4,
            autoMatchCountEnd = countEnd,
            curveAxisRaw = List(30) { (it + 1) * 256 },
            mulActStartRaw = factors,
            mulActEndRaw = factorsEnd,
            mapTimeAxisRaw = List(12) { 781 + it },
            mapRpmAxisRaw = List(12) { 1000 + it * 500 },
            mapRowsRaw = rows,
            generationCheck = CalibrationGenerationCheck(advertisedStable, if (advertisedStable) emptySet() else setOf("ADVERTISED_UNSTABLE")),
        )
    }

    @Test
    fun `promove somente leitura composta coerente para snapshots KNOWN`() {
        val snapshot = CompositeCalibrationSnapshot.promote(raw())
        assertEquals(77L, snapshot.usbSessionId)
        assertEquals(13, snapshot.mapRowsRaw.size)
        assertEquals(MapGeometryCompleteness.KNOWN, snapshot.mapGeometry.completeness)
        assertEquals(MapGeometryProvenance.FULL_ECU_READ, snapshot.mapGeometry.provenance)
        assertEquals(CurveSnapshotCompleteness.KNOWN, snapshot.curve.completeness)
        assertEquals(CurveSnapshotProvenance.FULL_ECU_READ, snapshot.curve.provenance)
        assertEquals(64, snapshot.mapHash.length)
        assertTrue(snapshot.generationStable)
    }

    @Test
    fun `contador mutante rejeita mesmo se raw mentir stable true`() {
        assertThrows(IllegalArgumentException::class.java) { CompositeCalibrationSnapshot.promote(raw(countEnd = 5, advertisedStable = true)) }
    }

    @Test
    fun `MUL ACT mutante rejeita mesmo se raw mentir stable true`() {
        assertThrows(IllegalArgumentException::class.java) { CompositeCalibrationSnapshot.promote(raw(mulEndDelta = 1, advertisedStable = true)) }
    }

    @Test
    fun `mapa incompleto ou sessao invalida falham fechado`() {
        assertThrows(IllegalArgumentException::class.java) { CompositeCalibrationSnapshot.promote(raw(rows = List(12) { List(12) { 0 } })) }
        val invalidSession = raw().copy(usbSessionId = 0L)
        assertThrows(IllegalArgumentException::class.java) { CompositeCalibrationSnapshot.promote(invalidSession) }
    }
}
