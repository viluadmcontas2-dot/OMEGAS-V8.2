package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationIdentityTest {
    private fun raw(session: Long = 77L): CompositeCalibrationRawRead {
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
            mapRowsRaw = List(13) { row -> List(12) { column -> if (row == 0 && column == 0) 160 else (row * 12 + column) and 0xFF } },
            generationCheck = CalibrationGenerationCheck(true, emptySet()),
        )
    }

    @Test
    fun `identity FULL ECU READ carrega fingerprints sessao geracao e freshness`() {
        val composite = CompositeCalibrationSnapshot.promote(raw())
        val identity = CalibrationIdentity.fromComposite(
            composite = composite,
            capturedAtMs = 1234L,
            mapRevision = 7L,
            curveRevision = 9L,
        )
        assertEquals(CalibrationProvenance.FULL_ECU_READ, identity.provenance)
        assertEquals(CalibrationCompleteness.KNOWN, identity.completeness)
        assertEquals(CalibrationFreshness.CURRENT_SESSION, identity.freshness)
        assertEquals(77L, identity.usbSessionId)
        assertEquals(4, identity.generation)
        assertEquals(1234L, identity.capturedAtMs)
        assertEquals(7L, identity.mapRevision)
        assertEquals(9L, identity.curveRevision)
        assertEquals(CalibrationFunctionFingerprint.from(composite), identity.functionFingerprint)
        assertEquals(composite.mapGeometry.fingerprint(), identity.geometryFingerprint)
        assertEquals(composite.mapHash, identity.mapHash)
        assertEquals(composite.curve.axisFingerprint(), identity.curveAxisFingerprint)
        assertEquals(composite.curve.factorsFingerprint(), identity.curveFactorsFingerprint)
    }

    @Test
    fun `revisions locais nao alteram igualdade fisica`() {
        val composite = CompositeCalibrationSnapshot.promote(raw())
        val first = CalibrationIdentity.fromComposite(composite, 100L, 1L, 1L)
        val second = CalibrationIdentity.fromComposite(composite, 200L, 99L, 88L)
        assertEquals(first.functionFingerprint, second.functionFingerprint)
        assertNotEquals(first.mapRevision, second.mapRevision)
        assertNotEquals(first.curveRevision, second.curveRevision)
    }

    @Test
    fun `036A expõe M C e F por indices físicos com provenance`() {
        val identity = CalibrationIdentity.fromComposite(CompositeCalibrationSnapshot.promote(raw()), 100L, null, null)
        val state = identity.effectiveState(mapRow = 0, mapColumn = 0, curveIndex = 0)
        assertEquals(160.0 / 128.0, state.mapEffective, 0.000001)
        assertEquals(1.0, state.curveEffective, 0.000001)
        assertEquals(1.25, state.currentEffective, 0.000001)
        assertEquals(CalibrationProvenance.FULL_ECU_READ, state.provenance)
        assertEquals(160, state.mapRaw)
        assertEquals(0x4000, state.curveRaw)
    }

    @Test
    fun `lookup invalido não inventa valor`() {
        val identity = CalibrationIdentity.fromComposite(CompositeCalibrationSnapshot.promote(raw()), 100L, null, null)
        assertThrows(IllegalArgumentException::class.java) { identity.effectiveState(12, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { identity.effectiveState(0, 12, 0) }
        assertThrows(IllegalArgumentException::class.java) { identity.effectiveState(0, 0, 30) }
    }

    @Test
    fun `serializacao preserva identidade e unknown revisions`() {
        val identity = CalibrationIdentity.fromComposite(CompositeCalibrationSnapshot.promote(raw()), 100L, null, null)
        val serialized = identity.toSerializableMap()
        assertEquals("KNOWN", serialized.getValue("completeness"))
        assertEquals("FULL_ECU_READ", serialized.getValue("provenance"))
        assertNull(serialized["mapRevision"])
        assertNull(serialized["curveRevision"])
        assertTrue((serialized.getValue("functionFingerprint") as String).length == 64)
    }
}
