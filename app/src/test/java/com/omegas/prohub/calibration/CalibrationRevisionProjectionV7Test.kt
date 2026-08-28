package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationRevisionProjectionV7Test {
    @Test
    fun `mesma revision com fingerprints diferentes nao e mesma calibracao`() {
        val revision = CalibrationRevisionV7(curveK = 2, mapK = 3)
        val a = MaterialCalibrationBindingV7("a".repeat(64), revision)
        val b = MaterialCalibrationBindingV7("b".repeat(64), revision)
        assertFalse(a.samePhysicalCalibration(b))
    }

    @Test
    fun `revision diferente com mesmo fingerprint continua mesma funcao fisica`() {
        val a = MaterialCalibrationBindingV7("f".repeat(64), CalibrationRevisionV7(1, 1))
        val b = MaterialCalibrationBindingV7("f".repeat(64), CalibrationRevisionV7(99, 88))
        assertTrue(a.samePhysicalCalibration(b))
        assertEquals(a.functionFingerprint, b.functionFingerprint)
    }

    @Test
    fun `revision permanece disponivel apenas como projecao`() {
        val binding = MaterialCalibrationBindingV7("f".repeat(64), CalibrationRevisionV7(7, 9))
        assertEquals(7, binding.revisionProjection.curveK)
        assertEquals(9, binding.revisionProjection.mapK)
    }
}
