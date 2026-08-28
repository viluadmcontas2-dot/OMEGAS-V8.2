package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeTelemetryFrameTest {
    private fun telemetry(gasMs: Double? = 6.5) = Mp48Telemetry(
        capturedAtElapsedMs = 1234L,
        rpm = 1850,
        levelRaw = 90,
        gasRaw = if (gasMs == null) 0 else 3328,
        gasMsDiagnostic = gasMs,
        petrolRaw = 2304,
        petrolCounts = 2304,
        petrolMs = 4.5,
        dynamicCorrection = 0,
        fuelByte = 0x90,
        fuel = Mp48Fuel.CNG,
        state = "GNV_ATIVO",
        waterRaw = 120,
        waterC = 80,
        gasC = 35,
        gasPressureRaw = 16384,
        gasPressureAbsBar = 2.0,
        mapRaw = 4096,
        mapBar = 0.5,
        pressureDiffBar = 1.5,
        plausible = true,
    )

    @Test
    fun `frame tipado preserva campos físicos e provenance sem JSON`() {
        val frame = RuntimeTelemetryFrame.from(
            telemetry = telemetry(),
            sequence = 42L,
            usbSessionId = 77L,
        )
        assertEquals(42L, frame.sequence)
        assertEquals(77L, frame.usbSessionId)
        assertEquals(1234L, frame.capturedAtElapsedMs)
        assertEquals(1850, frame.rpm)
        assertEquals(4.5, frame.petrolMs, 0.000001)
        assertEquals(6.5, frame.gasMsDiagnostic!!, 0.000001)
        assertEquals(80, frame.waterC)
        assertEquals(35, frame.gasTemperatureC)
        assertEquals(2.0, frame.gasPressureAbsBar, 0.000001)
        assertEquals(0.5, frame.mapBar, 0.000001)
        assertEquals(1.5, frame.pressureDiffBar, 0.000001)
        assertEquals(RuntimeFuel.CNG, frame.fuel)
        assertEquals(RuntimeFreshness.CURRENT, frame.freshness)
        assertEquals(true, frame.plausible)
    }

    @Test
    fun `gas ms continua diagnostico nullable e nao vira referencia`() {
        val frame = RuntimeTelemetryFrame.from(telemetry(null), 1L, 2L)
        assertNull(frame.gasMsDiagnostic)
    }
}
