package com.omegas.prohub.learning

/**
 * Não confundir origem do sinal com regra de decisão.
 *
 * NATIVE_ANCHORED = valor/semântica sustentados pela aquisição/protocolo nativo.
 * OMEGAS_COMPARABILITY_POLICY = regra criada pelo app para decidir quando duas
 * condições podem ser comparadas; nunca deve ser descrita como fórmula OEM.
 */
enum class ScientificDecisionAuthority {
    NATIVE_ANCHORED,
    OMEGAS_COMPARABILITY_POLICY,
}

data class ScientificAuthorityEvidence(
    val authority: ScientificDecisionAuthority,
    val source: String,
    val evidenceLevel: String? = null,
) {
    init {
        require(source.isNotBlank())
        if (authority == ScientificDecisionAuthority.NATIVE_ANCHORED) {
            require(!evidenceLevel.isNullOrBlank()) { "Evidência nativa exige nível explícito" }
        }
    }

    fun token(): String = buildString {
        append(authority.name)
        append(':')
        append(source)
        evidenceLevel?.let {
            append(':')
            append(it)
        }
    }
}

object ScientificAuthorityRegistry {
    fun nativeAnchored(source: String, evidenceLevel: String): ScientificAuthorityEvidence =
        ScientificAuthorityEvidence(
            authority = ScientificDecisionAuthority.NATIVE_ANCHORED,
            source = source,
            evidenceLevel = evidenceLevel,
        )

    fun omegasComparabilityPolicy(source: String): ScientificAuthorityEvidence =
        ScientificAuthorityEvidence(
            authority = ScientificDecisionAuthority.OMEGAS_COMPARABILITY_POLICY,
            source = source,
        )

    val referenceSelectorComparability = omegasComparabilityPolicy("PetrolReferenceSelector")
    val gasTemperatureSpanPolicy = omegasComparabilityPolicy("LearningTolerancePolicy.comparisonMaximumGasTempSpanC")
    val pressureSpanPolicy = omegasComparabilityPolicy("LearningTolerancePolicy.comparisonMaximumPressureSpanBar")
}
