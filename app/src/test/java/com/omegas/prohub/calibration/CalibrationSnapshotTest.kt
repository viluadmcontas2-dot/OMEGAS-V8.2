package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationShapeV7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class CalibrationSnapshotTest {
    private fun map(): MutableList<MutableList<Int>> = MutableList(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
        MutableList(CalibrationShapeV7.MAP_K_COLUMNS) { row -> row + 100 }
    }

    @Test
    fun captures_raw_map_axes_curve_and_relevant_state_without_ui_conversion() {
        val snapshot = CalibrationSnapshot.capture(
            mapKRowsRaw = map(),
            rpmAxisRaw = listOf(10, 20, 30, 40),
            petrolTimeAxisRaw = listOf(5, 10, 15, 20),
            curveKRaw = List(CalibrationShapeV7.CURVE_K_POINTS) { it + 1 },
            relevantStateRaw = mapOf(0x0200 to listOf(0x01), 0x0100 to listOf(0x7F, 0x80)),
        )

        assertEquals(13, snapshot.mapKRowsRaw.size)
        assertEquals(12, snapshot.mapKRowsRaw.first().size)
        assertEquals(listOf(10, 20, 30, 40), snapshot.rpmAxisRaw)
        assertEquals(listOf(5, 10, 15, 20), snapshot.petrolTimeAxisRaw)
        assertEquals(30, snapshot.curveKRaw.size)
        assertEquals(listOf(0x0100, 0x0200), snapshot.relevantStateRaw.keys.toList())
    }

    @Test
    fun capture_is_a_snapshot_not_a_live_view_of_mutable_sources() {
        val sourceMap = map()
        val sourceRpm = mutableListOf(10, 20)
        val sourceTime = mutableListOf(3, 4)
        val sourceCurve = MutableList(CalibrationShapeV7.CURVE_K_POINTS) { 100 }
        val sourceState = mutableMapOf(0x0200 to mutableListOf(1, 2))

        val snapshot = CalibrationSnapshot.capture(
            sourceMap,
            sourceRpm,
            sourceTime,
            sourceCurve,
            sourceState,
        )
        assertNotSame(sourceMap, snapshot.mapKRowsRaw)

        sourceMap[0][0] = 255
        sourceRpm[0] = 99
        sourceTime[0] = 99
        sourceCurve[0] = 99
        sourceState.getValue(0x0200)[0] = 99

        assertEquals(100, snapshot.mapKRowsRaw[0][0])
        assertEquals(10, snapshot.rpmAxisRaw[0])
        assertEquals(3, snapshot.petrolTimeAxisRaw[0])
        assertEquals(100, snapshot.curveKRaw[0])
        assertEquals(1, snapshot.relevantStateRaw.getValue(0x0200)[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_incomplete_map_instead_of_fabricating_a_complete_snapshot() {
        CalibrationSnapshot.capture(
            mapKRowsRaw = map().dropLast(1),
            rpmAxisRaw = listOf(10),
            petrolTimeAxisRaw = listOf(5),
            curveKRaw = List(CalibrationShapeV7.CURVE_K_POINTS) { 100 },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_non_raw_values() {
        CalibrationSnapshot.capture(
            mapKRowsRaw = map(),
            rpmAxisRaw = listOf(10, 256),
            petrolTimeAxisRaw = listOf(5),
            curveKRaw = List(CalibrationShapeV7.CURVE_K_POINTS) { 100 },
        )
    }
}
