package com.omegas.prohub.adaptive

import com.omegas.prohub.calibration.CalibrationGenerationCheck
import com.omegas.prohub.calibration.CalibrationIdentity
import com.omegas.prohub.calibration.CompositeCalibrationRawRead
import com.omegas.prohub.calibration.CompositeCalibrationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveModelBindingTest {
    private fun raw(
        session: Long = 77L,
        generation: Int = 4,
        firstMapValue: Int = 160,
    ): CompositeCalibrationRawRead {
        val factors = List(30) { 0x4000 + it }
        return CompositeCalibrationRawRead(
            usbSessionId = session,
            autoMatchCountStart = generation,
            autoMatchCountEnd = generation,
            curveAxisRaw = List(30) { (it + 1) * 256 },
            mulActStartRaw = factors,
            mulActEndRaw = factors,
            mapTimeAxisRaw = List(12) { 781 + it },
            mapRpmAxisRaw = List(12) { 1000 + it * 500 },
            mapRowsRaw = List(13) { row ->
                List(12) { column ->
                    if (row == 0 && column == 0) firstMapValue else (row * 12 + column) and 0xFF
                }
            },
            generationCheck = CalibrationGenerationCheck(true, emptySet()),
        )
    }

    private fun identity(raw: CompositeCalibrationRawRead): CalibrationIdentity =
        CalibrationIdentity.fromComposite(
            composite = CompositeCalibrationSnapshot.promote(raw),
            capturedAtMs = 123L,
            mapRevision = null,
            curveRevision = null,
        )

    @Test
    fun `adaptive binding is current only for the physical calibration that produced it`() {
        val physical = identity(raw())
        val binding = AdaptiveModelBinding.bind(
            identity = physical,
            modelSchema = "adaptive-reference-v1",
            modelVersion = "1",
            modelHash = "model-hash-a",
            domainId = "vehicle-local",
            boundAtElapsedMs = 456L,
        )

        assertEquals(AdaptiveModelValidity.CURRENT, binding.validityAgainst(physical))
        assertTrue(binding.isCurrentFor(physical))
        assertTrue(binding.key().contains("model-hash-a"))

        val changedMap = identity(raw(firstMapValue = 161))
        assertEquals(AdaptiveModelValidity.CALIBRATION_CHANGED, binding.validityAgainst(changedMap))
        assertFalse(binding.isCurrentFor(changedMap))
    }

    @Test
    fun `adaptive binding rejects observational identity without material payload`() {
        val physical = identity(raw())
        val binding = AdaptiveModelBinding.bind(
            physical,
            "adaptive-reference-v1",
            "1",
            "model-hash-a",
            "vehicle-local",
            456L,
        )
        val observational = CalibrationIdentity.observational(
            usbSessionId = physical.usbSessionId,
            generation = physical.generation,
            provenance = physical.provenance,
            freshness = physical.freshness,
            capturedAtMs = 999L,
            functionFingerprint = physical.functionFingerprint,
            geometryFingerprint = physical.geometryFingerprint,
            mapHash = physical.mapHash,
            curveAxisFingerprint = physical.curveAxisFingerprint,
            curveFactorsFingerprint = physical.curveFactorsFingerprint,
        )
        assertEquals(
            AdaptiveModelValidity.PHYSICAL_IDENTITY_NOT_USABLE,
            binding.validityAgainst(observational),
        )
    }

    @Test
    fun `cannot bind adaptive model to identity that is not physically usable`() {
        val observational = CalibrationIdentity.observational(
            usbSessionId = 1L,
            generation = 0,
            provenance = com.omegas.prohub.calibration.CalibrationProvenance.RESTORED_HISTORY,
            freshness = com.omegas.prohub.calibration.CalibrationFreshness.STALE,
            capturedAtMs = 1L,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveModelBinding.bind(
                observational,
                "adaptive-reference-v1",
                "1",
                "model-hash-a",
                "vehicle-local",
                2L,
            )
        }
    }
}
