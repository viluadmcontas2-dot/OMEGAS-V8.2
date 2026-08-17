package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry

enum class RuntimeFuel {
    ENGINE_OFF,
    PETROL,
    TRANSITION,
    CNG,
    CUTOFF,
    UNKNOWN,
}

enum class RuntimeFreshness {
    CURRENT,
    STALE,
    UNKNOWN,
}

/** Quadro tipado publicado diretamente da aquisição MP48, antes de qualquer JSON/UI. */
data class RuntimeTelemetryFrame(
    val sequence: Long,
    val usbSessionId: Long,
    val capturedAtElapsedMs: Long,
    val rpm: Int,
    val petrolMs: Double,
    val gasMsDiagnostic: Double?,
    val waterC: Int,
    val gasTemperatureC: Int,
    val gasPressureAbsBar: Double,
    val mapBar: Double,
    val pressureDiffBar: Double,
    val fuel: RuntimeFuel,
    val plausible: Boolean,
    val freshness: RuntimeFreshness,
) {
    companion object {
        fun from(
            telemetry: Mp48Telemetry,
            sequence: Long,
            usbSessionId: Long,
        ): RuntimeTelemetryFrame {
            require(sequence >= 0L) { "sequence inválida" }
            require(usbSessionId > 0L) { "usbSessionId inválida" }
            return RuntimeTelemetryFrame(
                sequence = sequence,
                usbSessionId = usbSessionId,
                capturedAtElapsedMs = telemetry.capturedAtElapsedMs,
                rpm = telemetry.rpm,
                petrolMs = telemetry.petrolMs,
                gasMsDiagnostic = telemetry.gasMsDiagnostic,
                waterC = telemetry.waterC,
                gasTemperatureC = telemetry.gasC,
                gasPressureAbsBar = telemetry.gasPressureAbsBar,
                mapBar = telemetry.mapBar,
                pressureDiffBar = telemetry.pressureDiffBar,
                fuel = when (telemetry.fuel) {
                    Mp48Fuel.ENGINE_OFF -> RuntimeFuel.ENGINE_OFF
                    Mp48Fuel.PETROL -> RuntimeFuel.PETROL
                    Mp48Fuel.TRANSITION -> RuntimeFuel.TRANSITION
                    Mp48Fuel.CNG -> RuntimeFuel.CNG
                    Mp48Fuel.CUTOFF -> RuntimeFuel.CUTOFF
                    Mp48Fuel.UNKNOWN -> RuntimeFuel.UNKNOWN
                },
                plausible = telemetry.plausible,
                freshness = RuntimeFreshness.CURRENT,
            )
        }
    }
}
