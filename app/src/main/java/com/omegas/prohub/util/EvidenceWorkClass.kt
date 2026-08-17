package com.omegas.prohub.util

/**
 * Valor semântico do trabalho científico depois que o frame já foi adquirido.
 *
 * Isto não classifica nem controla aquisição MP48. A porta/telemetria ficam fora
 * deste Router; somente trabalho downstream pode ser coalescido/superseded.
 */
enum class EvidenceWorkClass(
    val valueRank: Int,
    val diagnosticOnly: Boolean = false,
) {
    DIAGNOSTIC_ONLY(10, diagnosticOnly = true),
    STATIC_REFERENCE(40),
    DYNAMIC_COHERENT(60),
    FAST_KSTAR(80),
    POST_WRITE_REVALIDATION(100),
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
