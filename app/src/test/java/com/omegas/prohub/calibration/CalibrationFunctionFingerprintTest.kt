package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalibrationFunctionFingerprintTest {
    private fun raw(
        sessionId: Long = 77L,
        timeDelta: Int = 0,
        mapDelta: Int = 0,
        curveAxisDelta: Int = 0,
        factorDelta: Int = 0,
    ): CompositeCalibrationRawRead {
        val factors = List(30) { 0x4000 + it }.toMutableList().also { it[29] += factorDelta }
        return CompositeCalibrationRawRead(
            usbSessionId = sessionId,
            autoMatchCountStart = 4,
            autoMatchCountEnd = 4,
            curveAxisRaw = List(30) { (it + 1) * 256 }.toMutableList().also { it[29] += curveAxisDelta },
            mulActStartRaw = factors.toList(),
            mulActEndRaw = factors.toList(),
            mapTimeAxisRaw = List(12) { 781 + it }.toMutableList().also { it[11] += timeDelta },
            mapRpmAxisRaw = List(12) { 1000 + it * 500 },
            mapRowsRaw = List(13) { row -> List(12) { column -> ((row * 12 + column) + if (row == 12 && column == 11) mapDelta else 0) and 0xFF } },
            generationCheck = CalibrationGenerationCheck(true, emptySet()),
        )
    }

    @Test
    fun `mesma funcao fisica gera mesmo fingerprint mesmo em outra sessao`() {
        val first = CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(sessionId = 77L)))
        val second = CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(sessionId = 99L)))
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `qualquer componente fisico muda fingerprint`() {
        val base = CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw()))
        assertNotEquals(base, CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(timeDelta = 1))))
        assertNotEquals(base, CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(mapDelta = 1))))
        assertNotEquals(base, CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(curveAxisDelta = 1))))
        assertNotEquals(base, CalibrationFunctionFingerprint.from(CompositeCalibrationSnapshot.promote(raw(factorDelta = 1))))
    }
}
