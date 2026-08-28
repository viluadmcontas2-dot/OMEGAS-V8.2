package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class V7CalibrationIdentityCoordinatorTest {
    private fun raw(session: Long = 77L, mapDelta: Int = 0): CompositeCalibrationRawRead {
        val factors = List(30) { 0x4000 + it }
        return CompositeCalibrationRawRead(
            usbSessionId = session,
            autoMatchCountStart = 4,
            autoMatchCountEnd = 4,
            curveAxisRaw = List(30) { (it + 1) * 256 },
            mulActStartRaw = factors,
            mulActEndRaw = factors,
            mapTimeAxisRaw = List(12) { 781 + it },
            mapRpmAxisRaw = List(12) { 1000 + it * 500 },
            mapRowsRaw = List(13) { row -> List(12) { column -> ((row * 12 + column) + if (row == 0 && column == 0) mapDelta else 0) and 0xFF } },
            generationCheck = CalibrationGenerationCheck(true, emptySet()),
        )
    }

    private fun identity(session: Long = 77L, mapDelta: Int = 0) = CalibrationIdentity.fromComposite(
        CompositeCalibrationSnapshot.promote(raw(session, mapDelta)),
        capturedAtMs = 100L,
        mapRevision = null,
        curveRevision = null,
    )

    @Test
    fun `coordinator recebe identity como authority e revision somente como projecao`() {
        val coordinator = V7CalibrationIdentityCoordinator()
        val id = identity()
        coordinator.accept(id, CalibrationRevisionV7(7, 9))
        val projection = coordinator.projection()
        assertEquals(id.functionFingerprint, projection.functionFingerprint)
        assertEquals(7, projection.revisionProjection.curveK)
        assertEquals(9, projection.revisionProjection.mapK)
        assertTrue(coordinator.matchesMaterial(id.functionFingerprint))
    }

    @Test
    fun `mesma revision local nao mascara troca fisica`() {
        val coordinator = V7CalibrationIdentityCoordinator()
        val old = identity(mapDelta = 0)
        val changed = identity(mapDelta = 1)
        coordinator.accept(old, CalibrationRevisionV7(3, 3))
        coordinator.accept(changed, CalibrationRevisionV7(3, 3))
        assertFalse(coordinator.matchesMaterial(old.functionFingerprint))
        assertTrue(coordinator.matchesMaterial(changed.functionFingerprint))
    }

    @Test
    fun `identity nao material e bloqueada`() {
        val coordinator = V7CalibrationIdentityCoordinator()
        val unknown = CalibrationIdentity.observational(
            usbSessionId = null,
            generation = null,
            provenance = CalibrationProvenance.UNKNOWN,
            freshness = CalibrationFreshness.UNKNOWN,
            capturedAtMs = 0L,
        )
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.accept(unknown, CalibrationRevisionV7(0, 0))
        }
    }

    @Test
    fun `fingerprint esperado divergente bloqueia gate material`() {
        val coordinator = V7CalibrationIdentityCoordinator()
        val id = identity()
        coordinator.accept(id, CalibrationRevisionV7(1, 1))
        assertThrows(IllegalStateException::class.java) { coordinator.requireMaterial("x".repeat(64)) }
        assertEquals(id.functionFingerprint, coordinator.requireMaterial(id.functionFingerprint).functionFingerprint)
    }
}
