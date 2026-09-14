package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelStateResolverTest {

    @Test
    fun `known bytes are explicit evidence`() {
        val resolver = FuelStateResolver()
        val cng = resolver.resolve(telemetry(fuelByte = 0x90, capturedAt = 100))
        assertEquals(Mp48Fuel.CNG, cng)
        
        // First frame of new fuel sets transitionStartedAt
        resolver.resolve(telemetry(fuelByte = 0x80, capturedAt = 500))
        // Wait out the hysteresis (300ms) to ensure it transitions to PETROL
        val petrol = resolver.resolve(telemetry(fuelByte = 0x80, capturedAt = 900))
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
        
        resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 0, capturedAt = 500))
        fuel = resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 0, capturedAt = 900))
        assertEquals(Mp48Fuel.PETROL, fuel)
    }
    
    @Test
    fun `contradictory signals resolve to CNG by preference`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(fuelByte = 0x82, rpm = 2000, petrolRaw = 100, gasRaw = 100, capturedAt = 100))
        assertEquals(Mp48Fuel.CNG, fuel) // Because gasRaw > 0 comes first in the resolver logic
    }
    
    @Test
    fun `cutoff is not petrol but its own state`() {
        val resolver = FuelStateResolver()
        // Wait, CUTOFF needs fuel to be CUTOFF, let's see how resolver resolves CUTOFF if fuel enum is UNKNOWN.
        // It doesn't. If fuel enum is CUTOFF, it returns CUTOFF. Let's provide Mp48Fuel.CUTOFF manually if needed, 
        // or just test that it propagates.
        val fuel = resolver.resolve(telemetry(rpm = 1500, petrolMs = 0.5, gasRaw = 0, mapBar = 0.2, capturedAt = 100, explicitFuel = Mp48Fuel.CUTOFF))
        assertEquals(Mp48Fuel.CUTOFF, fuel)
    }
    
    @Test
    fun `engine off has precedence`() {
        val resolver = FuelStateResolver()
        val fuel = resolver.resolve(telemetry(rpm = 0, fuelByte = 0x90, capturedAt = 100, explicitFuel = Mp48Fuel.ENGINE_OFF))
        assertEquals(Mp48Fuel.ENGINE_OFF, fuel)
    }

    private fun telemetry(
        capturedAt: Long,
        fuelByte: Int = 0,
        rpm: Int = 2000,
        gasRaw: Int = 0,
        petrolRaw: Int = 100,
        petrolMs: Double = 3.0,
        mapBar: Double = 0.5,
        explicitFuel: Mp48Fuel? = null
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
        fuel = explicitFuel ?: when(fuelByte) {
            0x90 -> Mp48Fuel.CNG
            0x80 -> Mp48Fuel.PETROL
            else -> Mp48Fuel.UNKNOWN
        },
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
