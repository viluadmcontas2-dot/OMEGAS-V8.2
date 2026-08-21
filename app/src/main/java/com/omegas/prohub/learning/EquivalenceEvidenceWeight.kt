package com.omegas.prohub.learning

import kotlin.math.abs

/**
 * Peso científico contínuo da equivalência primária RPM + MAP -> petrol Tinj.
 *
 * Os antigos limites de estabilidade permanecem como escalas de referência,
 * não como paredes binárias de aceitação. Sinais ambientais/pressão não
 * participam desta autoridade primária.
 */
internal data class EquivalenceEvidenceWeight(
    val stability: Double,
    val limitingSignal: String,
) {
    companion object {
        fun from(diagnostics: SampleDiagnostics): EquivalenceEvidenceWeight {
            val candidates = arrayOf(
                SignalRatio("rpm_shift", diagnostics.rpmCenterShift, diagnostics.rpmCenterLimit),
                SignalRatio("rpm_osc", diagnostics.rpmOscillation, diagnostics.rpmOscillationLimit),
                SignalRatio("map_shift", diagnostics.mapCenterShift, diagnostics.mapCenterLimit),
                SignalRatio("map_osc", diagnostics.mapOscillation, diagnostics.mapOscillationLimit),
                SignalRatio("tinj_shift", diagnostics.petrolCenterShift, diagnostics.petrolCenterLimit),
                SignalRatio("tinj_osc", diagnostics.petrolOscillationRatio, diagnostics.petrolOscillationLimit),
            )
            var limitingSignal = "none"
            var limitingRatio = 0.0
            candidates.forEach { candidate ->
                val ratio = candidate.normalized()
                if (ratio > limitingRatio) {
                    limitingRatio = ratio
                    limitingSignal = candidate.name
                }
            }
            val r = limitingRatio.coerceAtLeast(0.0)
            return EquivalenceEvidenceWeight(
                stability = (1.0 / (1.0 + r * r)).coerceIn(0.0, 1.0),
                limitingSignal = limitingSignal,
            )
        }
    }

    private data class SignalRatio(
        val name: String,
        val measured: Double,
        val reference: Double,
    ) {
        fun normalized(): Double = when {
            !measured.isFinite() -> 0.0
            !reference.isFinite() || reference <= 0.0 -> 0.0
            else -> abs(measured) / abs(reference)
        }
    }
}
