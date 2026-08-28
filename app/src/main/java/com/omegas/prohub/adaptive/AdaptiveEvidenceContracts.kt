package com.omegas.prohub.adaptive

/**
 * Adaptive consome o envelope canônico do backbone de telemetria.
 *
 * Este alias é somente conveniência de namespace: a autoridade real é
 * com.omegas.prohub.telemetry.CanonicalEvidence. Não existe segundo DTO, Store,
 * polling ou writer Adaptive.
 */
typealias CanonicalEvidence = com.omegas.prohub.telemetry.CanonicalEvidence

object CanonicalEvidenceContract {
    const val SOURCE_TYPE = "telemetry.CanonicalEvidence"
    const val ACQUISITION_OWNER = "ResponseDrivenEcuEngine/NativeRuntimeManager"
    const val SINGLE_PHYSICAL_ACQUISITION = true
    const val MAY_CREATE_SECOND_MP48_POLLING = false
    const val MAY_REPARSE_JSON_TO_FORM_SCIENCE = false
    const val MAY_WRITE_ECU = false
}
