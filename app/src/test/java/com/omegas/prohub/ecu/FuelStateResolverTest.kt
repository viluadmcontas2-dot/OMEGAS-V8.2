package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelStateResolverTest {

    @Test
    fun `known bytes are explicit evidence`() {
        val resolver = FuelStateResolver()
        val cng = resolver.resolve(telemetry(fuelByte = 0x90, capturedAt = 100))
        assertEquals(Mp48Fuel.CNG, cng)
        
        val petrol = resolver.resolve(telemetry(fuelByte = 0x80, capturedAt = 200))
        assertEquals(Mp48Fuel.PETROL, petrol)
    }

    @Test
    fun `uncatalogued byte with gas_raw gt 0 becomes CNG`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(fuelByte = 0x82, gasRaw = 100, capturedAt = 100))
        assertEquals(Mp48Fuel.CNG, fuel)
    }

    @Test
    fun `uncatalogued byte with petrol active and no gas becomes PETROL`() {
        val resolver = FuelStateResolver()
        var fuel = resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 0, capturedAt = 100))
        assertEquals(Mp48Fuel.PETROL, fuel)
        
        // After some frames, it's definitively PETROL
        fuel = resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 0, capturedAt = 300))
        assertEquals(Mp48Fuel.PETROL, fuel)
    }
    
    @Test
    fun `contradictory signals enter TRANSITION`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 100, capturedAt = 100))
        assertEquals(Mp48Fuel.TRANSITION, fuel)
    }
    
    @Test
    fun `cutoff is not petrol`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(rpm = 1500, petrolMs = 0.5, gasRaw = 0, mapBar = 0.2, capturedAt = 100))
        assertEquals(Mp48Fuel.CUTOFF, fuel)
    }
    
    @Test
    fun `engine off has precedence`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(rpm = 0, fuelByte = 0x90, capturedAt = 100))
        assertEquals(Mp48Fuel.ENGINE_OFF, fuel)
    }

    private fun telemetry(
        capturedAt: Long,
        fuelByte: Int = 0,
        rpm: Int = 2000,
        gasRaw: Int = 0,
        petrolRaw: Int = 100,
        petrolMs: Double = 3.0,
        mapBar: Double = 0.5
    ) = Mp48Telemetry(
        capturedAtElapsedMs = capturedAt,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = gasRaw,
        gasMsDiagnostic = null,
        petrolRaw = petrolRaw,
        petrolCounts = petrolRaw,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = fuelByte,
        fuel = Mp48Fuel.UNKNOWN,
        state = "",
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = true
    )
}
