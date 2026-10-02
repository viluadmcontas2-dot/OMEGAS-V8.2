package com.omegas.prohub.ecu

class FuelStateResolver(
    private val telemetryTimeoutMs: Long = 2000L,
    private val transitionHysteresisMs: Long = 300L
) {
    private var lastFuel: Mp48Fuel = Mp48Fuel.UNKNOWN
    private var lastReceivedAt: Long = -1L
    private var transitionStartedAt: Long = -1L

    fun resolve(frame: Mp48Telemetry): Mp48Fuel {
        val now = frame.capturedAtElapsedMs
        lastReceivedAt = now

        var instantaneous = frame.fuel
        if (instantaneous == Mp48Fuel.UNKNOWN) {
            instantaneous = when {
                frame.gasRaw > 0 && frame.petrolRaw > 0 -> Mp48Fuel.TRANSITION
                frame.gasRaw > 0 -> Mp48Fuel.CNG
                frame.petrolRaw > 0 -> Mp48Fuel.PETROL
                else -> Mp48Fuel.TRANSITION
            }
        }

        if (instantaneous == Mp48Fuel.ENGINE_OFF || instantaneous == Mp48Fuel.CUTOFF) {
            transitionStartedAt = -1L
            lastFuel = instantaneous
            return instantaneous
        }

        if (instantaneous == Mp48Fuel.TRANSITION) {
            return Mp48Fuel.TRANSITION
        }

        if (lastFuel != instantaneous && lastFuel != Mp48Fuel.TRANSITION && lastFuel != Mp48Fuel.ENGINE_OFF && lastFuel != Mp48Fuel.CUTOFF && lastFuel != Mp48Fuel.UNKNOWN) {
            if (transitionStartedAt < 0L) {
                transitionStartedAt = now
            }
            if (now - transitionStartedAt < transitionHysteresisMs) {
                return Mp48Fuel.TRANSITION
            }
        }
        
        transitionStartedAt = -1L
        lastFuel = instantaneous
        return instantaneous
    }
}
