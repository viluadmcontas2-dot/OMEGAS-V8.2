package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp48TelemetryScaleTest {
    private val progBaseReferencePayload = byteArrayOf(
        0x6B, 0x03, 0x90.toByte(), 0x24, 0x00, 0x00,
        0x15, 0x11, 0x53, 0x07, 0x00, 0x90.toByte(),
        0x2C, 0x7E, 0x09, 0x07, 0x45, 0xC4.toByte(),
        0x01, 0xE1.toByte(), 0x00, 0x00, 0x00, 0x00,
        0xFA.toByte(), 0x10, 0x00, 0x00, 0x48, 0x07,
        0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun `frame real reproduz todos os valores mostrados pelo ProgBase`() {
        val telemetry = Mp48Protocol.decodeTelemetry(progBaseReferencePayload, 1234L)

        assertEquals(875, telemetry.rpm)
        assertEquals(Mp48Fuel.CNG, telemetry.fuel)
        assertEquals(1875, telemetry.petrolRaw)
        assertEquals(4.80000, telemetry.petrolMs, 0.000001)
        assertEquals(4373, telemetry.gasRaw)
        assertEquals(11.19488, telemetry.gasMsDiagnostic!!, 0.000001)
        assertEquals(65, telemetry.waterC)
        assertEquals(49, telemetry.gasC)
        assertEquals(126, telemetry.levelRaw)
        assertEquals(2.25125, telemetry.gasPressureAbsBar, 0.000001)
        assertEquals(0.452, telemetry.mapBar, 0.000001)
        assertEquals(1.79925, telemetry.pressureDiffBar, 0.000001)
        assertEquals(225, telemetry.unknownRaw19)
        assertEquals(4346, telemetry.gas2Raw)
        assertEquals(11.12576, telemetry.gas2MsDiagnostic!!, 0.000001)
        assertEquals(1864, telemetry.petrol2Raw)
        assertEquals(4.77184, telemetry.petrol2MsDiagnostic!!, 0.000001)
        assertTrue(telemetry.plausible)
    }

    @Test
    fun `byte 19 nunca e subtraido do Petrol Inj ou Gas Inj`() {
        val telemetry = Mp48Protocol.decodeTelemetry(progBaseReferencePayload, 0L)

        assertEquals(4.80000, telemetry.petrolMs, 0.000001)
        assertNotEquals((1875 - 225) * 0.00256, telemetry.petrolMs, 0.000001)
        assertEquals(11.19488, telemetry.gasMsDiagnostic!!, 0.000001)
        assertNotEquals((4373 - 225 - 234) * 0.00256, telemetry.gasMsDiagnostic!!, 0.000001)
    }

    @Test
    fun `conversoes fisicas permanecem centralizadas em Kotlin`() {
        assertEquals(4.8, Mp48TelemetryScale.injectionMs(1875), 0.000001)
        assertEquals(65, Mp48TelemetryScale.waterC(44))
        assertEquals(49, Mp48TelemetryScale.gasC(69))
        assertEquals(2.25125, Mp48TelemetryScale.gasPressureAbsBar(1801), 0.000001)
        assertEquals(0.452, Mp48TelemetryScale.mapBar(452), 0.000001)
    }

    @Test
    fun `MAP live preserva signed word do ProgBase e high bit falha fechado`() {
        val negativeMap = progBaseReferencePayload.copyOf().apply {
            this[17] = 0xFF.toByte()
            this[18] = 0xFF.toByte()
        }
        val telemetry = Mp48Protocol.decodeTelemetry(negativeMap, 0L)

        assertEquals(-1, telemetry.mapRaw)
        assertEquals(-0.001, telemetry.mapBar, 0.000001)
        assertFalse(telemetry.plausible)
        assertTrue(telemetry.plausibilityReasons.contains("MAP_OUT_OF_RANGE"))
    }

    @Test
    fun `pressao residual alta nao invalida gasolina nem bloqueia gnv mais`() {
        val highResidualPressure = progBaseReferencePayload.copyOf().apply {
            this[11] = 0x80.toByte() // gasolina
            this[14] = 0x50.toByte() // 3920 * 0,00125 = 4,9 bar abs
            this[15] = 0x0F.toByte()
            this[17] = 0xC8.toByte() // MAP 0,2 bar; diferencial 4,7 bar
            this[18] = 0x00.toByte()
        }
        val petrol = Mp48Protocol.decodeTelemetry(highResidualPressure, 0L)
        assertEquals(Mp48Fuel.PETROL, petrol.fuel)
        assertTrue(petrol.plausible)

        val cngPayload = highResidualPressure.copyOf().apply { this[11] = 0x90.toByte() }
        val cng = Mp48Protocol.decodeTelemetry(cngPayload, 0L)
        assertEquals(Mp48Fuel.CNG, cng.fuel)
        assertTrue("Pressure gates removed, should be plausible", cng.plausible)
    }
    @Test
    fun `bytes alternativos do estado MP48 preservam combustivel identificado`() {
        val expected = listOf(
            0x80 to Mp48Fuel.PETROL,
            0xA0 to Mp48Fuel.PETROL,
            0x88 to Mp48Fuel.TRANSITION,
            0xA8 to Mp48Fuel.TRANSITION,
            0x90 to Mp48Fuel.CNG,
            0xB0 to Mp48Fuel.CNG,
        )
        expected.forEach { (rawByte, fuel) ->
            val payload = progBaseReferencePayload.copyOf().apply { this[11] = rawByte.toByte() }
            val frame = Mp48Protocol.decodeTelemetry(payload, 1234L)
            assertEquals("Estado bruto 0x%02X".format(rawByte), fuel, frame.fuel)
            assertEquals(rawByte, frame.fuelByte)
        }
        listOf(0xA1, 0xB1, 0x94).forEach { rawByte ->
            val payload = progBaseReferencePayload.copyOf().apply { this[11] = rawByte.toByte() }
            assertEquals(Mp48Fuel.UNKNOWN, Mp48Protocol.decodeTelemetry(payload, 1234L).fuel)
        }
    }

    @Test
    fun `cutoff fisico precede variante B0 de GNV`() {
        val payload = progBaseReferencePayload.copyOf().apply {
            this[0] = 0x78
            this[1] = 0x05
            this[6] = 0
            this[7] = 0
            this[8] = 0
            this[9] = 0
            this[11] = 0xB0.toByte()
            this[17] = 0xC8.toByte()
            this[18] = 0
        }
        assertEquals(Mp48Fuel.CUTOFF, Mp48Protocol.decodeTelemetry(payload, 1234L).fuel)
    }

}
