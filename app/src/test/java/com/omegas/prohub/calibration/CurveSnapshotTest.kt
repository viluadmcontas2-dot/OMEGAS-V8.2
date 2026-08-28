package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurveSnapshotTest {
    private fun axis(): IntArray = IntArray(30) { (it + 1) * 256 }
    private fun factors(): IntArray = IntArray(30) { 0x4000 + it }

    @Test
    fun `snapshot copia raws e deriva unidades fisicas sem permitir mutacao externa`() {
        val axis = axis()
        val factors = factors()
        val snapshot = CurveSnapshot.create(
            petrolAxisRaw = axis,
            factorsRaw = factors,
            usbSessionId = 77L,
            provenance = CurveSnapshotProvenance.FULL_ECU_READ,
            completeness = CurveSnapshotCompleteness.KNOWN,
        )
        axis[0] = 9999
        factors[0] = 0

        assertEquals(256, snapshot.petrolAxisRaw.first())
        assertEquals(0x4000, snapshot.factorsRaw.first())
        assertEquals(0.5, snapshot.petrolAxisMs.first(), 0.000001)
        assertEquals(1.0, snapshot.factors.first(), 0.000001)
        assertEquals(77L, snapshot.usbSessionId)
        assertEquals(CurveSnapshot.SCHEMA, snapshot.schema)
    }

    @Test
    fun `fingerprints sao deterministas e sensiveis a cada vetor`() {
        val base = CurveSnapshot.create(axis(), factors(), 1L, CurveSnapshotProvenance.FULL_ECU_READ, CurveSnapshotCompleteness.KNOWN)
        val changedAxis = axis().also { it[29] += 1 }
        val changedFactor = factors().also { it[29] += 1 }
        val axisChanged = CurveSnapshot.create(changedAxis, factors(), 1L, CurveSnapshotProvenance.FULL_ECU_READ, CurveSnapshotCompleteness.KNOWN)
        val factorChanged = CurveSnapshot.create(axis(), changedFactor, 1L, CurveSnapshotProvenance.FULL_ECU_READ, CurveSnapshotCompleteness.KNOWN)

        assertEquals(base.axisFingerprint(), CurveSnapshot.create(axis(), factors(), 99L, CurveSnapshotProvenance.UNKNOWN, CurveSnapshotCompleteness.UNKNOWN).axisFingerprint())
        assertEquals(base.factorsFingerprint(), CurveSnapshot.create(axis(), factors(), 99L, CurveSnapshotProvenance.UNKNOWN, CurveSnapshotCompleteness.UNKNOWN).factorsFingerprint())
        assertNotEquals(base.axisFingerprint(), axisChanged.axisFingerprint())
        assertNotEquals(base.factorsFingerprint(), factorChanged.factorsFingerprint())
    }

    @Test
    fun `cardinalidade diferente de trinta falha fechado`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurveSnapshot.create(IntArray(29), factors(), 1L, CurveSnapshotProvenance.UNKNOWN, CurveSnapshotCompleteness.UNKNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurveSnapshot.create(axis(), IntArray(31), 1L, CurveSnapshotProvenance.UNKNOWN, CurveSnapshotCompleteness.UNKNOWN)
        }
    }

    @Test
    fun `serializacao preserva raws derivados fingerprints e metadados`() {
        val snapshot = CurveSnapshot.create(axis(), factors(), 5L, CurveSnapshotProvenance.FULL_ECU_READ, CurveSnapshotCompleteness.KNOWN)
        val out = snapshot.toSerializableMap()
        assertEquals(30, (out.getValue("petrolAxisRaw") as List<*>).size)
        assertEquals(30, (out.getValue("factorsRaw") as List<*>).size)
        assertEquals(snapshot.axisFingerprint(), out.getValue("axisFingerprint"))
        assertEquals(snapshot.factorsFingerprint(), out.getValue("factorsFingerprint"))
        assertEquals("KNOWN", out.getValue("completeness"))
        assertEquals("FULL_ECU_READ", out.getValue("provenance"))
    }
}
