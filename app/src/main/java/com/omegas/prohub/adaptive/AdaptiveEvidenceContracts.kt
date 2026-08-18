package com.omegas.prohub.adaptive

import com.omegas.prohub.telemetry.RuntimeTelemetryFrame

/**
 * OMEGAS Adaptive não cria um segundo DTO de telemetria.
 *
 * CanonicalEvidence é exatamente o frame tipado já publicado pela aquisição
 * nativa. Assim Classic, Adaptive e projeções downstream podem referenciar a
 * mesma observação física/session/timestamp sem copiar, serializar ou relabelar.
 */
typealias CanonicalEvidence = RuntimeTelemetryFrame

object CanonicalEvidenceContract {
    const val SOURCE_TYPE = "RuntimeTelemetryFrame"
    const val ACQUISITION_OWNER = "ResponseDrivenEcuEngine/NativeRuntimeManager"
    const val SINGLE_PHYSICAL_ACQUISITION = true
    const val MAY_CREATE_SECOND_MP48_POLLING = false
    const val MAY_REPARSE_JSON_TO_FORM_SCIENCE = false
    const val MAY_WRITE_ECU = false
}
