package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapGeometrySnapshotTest {
    private val timeRaw = intArrayOf(781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031)
    private val timeMs = doubleArrayOf(1.99936, 2.50112, 3.00032, 3.49952, 4.50048, 6.00064, 8.0, 9.99936, 11.99872, 14.00064, 16.0, 17.99936)
    private val rpmRaw = intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)

    @Test
    fun `snapshot preserva raws derivados sessao e estado`() {
        val mutableTime = timeRaw.copyOf()
        val snapshot = MapGeometrySnapshot.create(
            timeAxisRaw = mutableTime,
            timeAxisMs = timeMs,
            rpmAxisRaw = rpmRaw,
            usbSessionId = 42L,
            provenance = MapGeometryProvenance.FULL_ECU_READ,
            completeness = MapGeometryCompleteness.KNOWN,
        )
        mutableTime[0] = 9999

        assertEquals(781, snapshot.timeAxisRaw.first())
        assertEquals(1.99936, snapshot.timeAxisMs.first(), 0.0000001)
        assertEquals(850, snapshot.rpmAxisRaw.first())
        assertEquals(42L, snapshot.usbSessionId)
        assertEquals(MapGeometryProvenance.FULL_ECU_READ, snapshot.provenance)
        assertEquals(MapGeometryCompleteness.KNOWN, snapshot.completeness)
        assertEquals(MapGeometrySnapshot.SCHEMA, snapshot.schema)
    }

    @Test
    fun `fingerprint usa schema e raws em ordem canonica`() {
        val geometryA = intArrayOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)
        val geometryB = intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)
        val a = MapGeometrySnapshot.create(timeRaw, timeMs, geometryA, 1L, MapGeometryProvenance.FULL_ECU_READ, MapGeometryCompleteness.KNOWN)
        val b = MapGeometrySnapshot.create(timeRaw, timeMs, geometryB, 1L, MapGeometryProvenance.FULL_ECU_READ, MapGeometryCompleteness.KNOWN)
        val reordered = MapGeometrySnapshot.create(timeRaw.reversedArray(), timeMs.reversedArray(), geometryA, 1L, MapGeometryProvenance.FULL_ECU_READ, MapGeometryCompleteness.KNOWN)

        assertEquals("6ebc8a8916d7a77bac7875fd04114d67ca081d8fe5b9538c6d71a75c2777da21", a.fingerprint())
        assertEquals("ae0eea55db0af2f475be8a1f639c859dc28dd8a91bc1c36a782468af00805f61", b.fingerprint())
        assertNotEquals(a.fingerprint(), b.fingerprint())
        assertNotEquals(a.fingerprint(), reordered.fingerprint())
    }

    @Test
    fun `serializacao mantem cardinalidade e metadados`() {
        val serialized = MapGeometrySnapshot.create(
            timeAxisRaw = timeRaw,
            timeAxisMs = timeMs,
            rpmAxisRaw = rpmRaw,
            usbSessionId = 7L,
            provenance = MapGeometryProvenance.FULL_ECU_READ,
            completeness = MapGeometryCompleteness.KNOWN,
        ).toSerializableMap()

        assertEquals(12, (serialized.getValue("timeAxisRaw") as List<*>).size)
        assertEquals(12, (serialized.getValue("timeAxisMs") as List<*>).size)
        assertEquals(12, (serialized.getValue("rpmAxisRaw") as List<*>).size)
        assertEquals(7L, serialized.getValue("usbSessionId"))
        assertEquals("FULL_ECU_READ", serialized.getValue("provenance"))
        assertEquals("KNOWN", serialized.getValue("completeness"))
        assertEquals(MapGeometrySnapshot.SCHEMA, serialized.getValue("schema"))
    }

    @Test
    fun `snapshot rejeita cardinalidade errada em qualquer eixo`() {
        assertThrows(IllegalArgumentException::class.java) {
            MapGeometrySnapshot.create(IntArray(11), timeMs, rpmRaw, 1L, MapGeometryProvenance.UNKNOWN, MapGeometryCompleteness.UNKNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MapGeometrySnapshot.create(timeRaw, DoubleArray(13), rpmRaw, 1L, MapGeometryProvenance.UNKNOWN, MapGeometryCompleteness.UNKNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MapGeometrySnapshot.create(timeRaw, timeMs, IntArray(11), 1L, MapGeometryProvenance.UNKNOWN, MapGeometryCompleteness.UNKNOWN)
        }
    }
}
