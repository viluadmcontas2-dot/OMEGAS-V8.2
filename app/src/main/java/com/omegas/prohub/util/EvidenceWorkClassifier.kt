package com.omegas.prohub.util

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.telemetry.CanonicalEvidence

/**
 * Classifica somente valor operacional do trabalho downstream.
 * Não calcula confiança, probabilidade, K ideal ou ação na ECU.
 */
object EvidenceWorkClassifier {
    fun classify(
        fuel: Mp48Fuel,
        learningEligible: Boolean,
        samplePresent: Boolean,
        reasonCode: String,
        postWriteRevalidating: Boolean,
    ): EvidenceWorkClass {
        if (!learningEligible || !samplePresent) return EvidenceWorkClass.DIAGNOSTIC_ONLY
        if (postWriteRevalidating) return EvidenceWorkClass.POST_WRITE_REVALIDATION
        if (reasonCode.uppercase().startsWith("FAST_KSTAR")) return EvidenceWorkClass.FAST_KSTAR
        return when (fuel) {
            Mp48Fuel.PETROL -> EvidenceWorkClass.STATIC_REFERENCE
            Mp48Fuel.CNG -> EvidenceWorkClass.DYNAMIC_COHERENT
            else -> EvidenceWorkClass.DIAGNOSTIC_ONLY
        }
    }

    fun classify(evidence: CanonicalEvidence, postWriteRevalidating: Boolean): EvidenceWorkClass = classify(
        fuel = evidence.rawTelemetry.fuel,
        learningEligible = evidence.sampleDecision.learningEligible,
        samplePresent = evidence.sampleDecision.sample != null,
        reasonCode = evidence.sampleDecision.reasonCode,
        postWriteRevalidating = postWriteRevalidating,
    )
}
