package com.omegas.prohub.learning

/**
 * Vocabulário estável para UI/diagnóstico. Os códigos detalhados existentes
 * continuam preservados, mas consumidores não precisam inferir estado por campos ausentes.
 */
enum class LearningSelectionReason {
    REFERENCE_FOUND,
    NO_REGION,
    INSUFFICIENT_SUPPORT,
    ENV_MISMATCH,
    STALE,
    GEOMETRY_UNKNOWN,
    INVALID_CONDITION,
    NUMERIC_INVALID,
    UNKNOWN,
    ;

    companion object {
        fun fromReference(
            available: Boolean,
            detailReasonCode: String,
            geometryKnown: Boolean,
        ): LearningSelectionReason {
            val referenceReason = when (detailReasonCode) {
                "LOCAL_REFERENCE_AVAILABLE",
                "NEAREST_LOCAL_REFERENCE",
                "BOUNDED_EXTRAPOLATION" -> REFERENCE_FOUND

                "NO_PETROL_REGIONS",
                "NO_LOCAL_PETROL_REFERENCE" -> NO_REGION

                "REFERENCE_SPREAD_EXCEEDED" -> INSUFFICIENT_SUPPORT
                "REFERENCE_WEIGHT_INVALID" -> NUMERIC_INVALID
                "INVALID_CNG_CONDITION" -> INVALID_CONDITION
                "ENV_MISMATCH" -> ENV_MISMATCH
                "STALE" -> STALE
                "MAP_GEOMETRY_UNKNOWN", "GEOMETRY_UNKNOWN" -> GEOMETRY_UNKNOWN
                else -> if (available) REFERENCE_FOUND else UNKNOWN
            }
            // Falha de referência continua sendo o motivo primário. Geometry só
            // bloqueia a seleção física quando a referência em si já foi encontrada.
            return if (available && referenceReason == REFERENCE_FOUND && !geometryKnown) {
                GEOMETRY_UNKNOWN
            } else {
                referenceReason
            }
        }
    }
}
