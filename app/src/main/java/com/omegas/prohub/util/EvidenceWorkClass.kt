package com.omegas.prohub.util

/**
 * Classe qualitativa do ganho marginal de informação esperado.
 *
 * Isto não é probabilidade, confiança nem magnitude científica. É somente uma
 * ordenação operacional explícita para o backpressure escolher o que preservar
 * quando o trabalho downstream satura. O custo real continua sendo observado
 * por queue-age, processing time e thread CPU no consumer.
 */
enum class MarginalInformationClass {
    DIAGNOSTIC_PRESENT_STATE,
    REUSABLE_REFERENCE,
    CONTEXT_COHERENT_OBSERVATION,
    FAST_OBJECTIVE_OBSERVATION,
    CAUSAL_POST_INTERVENTION,
}

/**
 * Valor semântico do trabalho científico depois que o frame já foi adquirido.
 *
 * Isto não classifica nem controla aquisição MP48. A porta/telemetria ficam fora
 * deste Router; somente trabalho downstream pode ser coalescido/superseded.
 */
enum class EvidenceWorkClass(
    val valueRank: Int,
    val marginalInformationClass: MarginalInformationClass,
    val diagnosticOnly: Boolean = false,
) {
    DIAGNOSTIC_ONLY(
        10,
        MarginalInformationClass.DIAGNOSTIC_PRESENT_STATE,
        diagnosticOnly = true,
    ),
    STATIC_REFERENCE(40, MarginalInformationClass.REUSABLE_REFERENCE),
    DYNAMIC_COHERENT(60, MarginalInformationClass.CONTEXT_COHERENT_OBSERVATION),
    FAST_KSTAR(80, MarginalInformationClass.FAST_OBJECTIVE_OBSERVATION),
    POST_WRITE_REVALIDATION(100, MarginalInformationClass.CAUSAL_POST_INTERVENTION),
}

object EvidenceBackpressurePolicy {
    /** Compatibilidade temporária enquanto produtores antigos ainda enviam boolean. */
    fun fromLegacyImportant(important: Boolean): EvidenceWorkClass =
        if (important) EvidenceWorkClass.DYNAMIC_COHERENT else EvidenceWorkClass.DIAGNOSTIC_ONLY

    /**
     * Em saturação, trabalho novo só pode retirar um pendente de valor menor ou igual.
     * Em empate, vence o estado/evidência mais recente; o histórico bruto permanece no recorder.
     */
    fun incomingMaySupersede(
        incoming: EvidenceWorkClass,
        pending: EvidenceWorkClass,
    ): Boolean = incoming.valueRank >= pending.valueRank
}
