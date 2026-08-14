package com.omegas.prohub.ecu

import com.omegas.prohub.usb.UsbProtocolReply

/**
 * Contrato único de acesso serial MP48 fora da engine.
 *
 * Managers nunca tocam UsbSerialManager diretamente. Eles entregam unidades
 * semânticas à engine, que mantém handshake/recovery e a oportunidade de
 * telemetria entre trabalhos secundários.
 */
enum class Mp48WorkClass(val priority: Int) {
    /** Saída/fail-closed que precisa ultrapassar trabalho normal já enfileirado. */
    SAFETY(0),
    /** Escrita iniciada e confirmada pelo operador; nunca nasce do scheduler. */
    MANUAL_WRITE(1),
    /** Leitura observacional/diagnóstica. */
    READ_ONLY(2),
}

interface Mp48SerialUnit {
    val sessionId: Long

    /** Executa uma transação completa sem permitir preempção no meio. */
    fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1_800,
        purgeBefore: Boolean = false,
    ): UsbProtocolReply
}

interface Mp48SerialScheduler {
    fun isConnected(): Boolean
    fun currentSessionId(): Long

    /** Uma transação é uma unidade; a telemetria recebe oportunidade depois dela. */
    fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1_800,
        purgeBefore: Boolean = false,
        expectedSessionId: Long = 0L,
        workClass: Mp48WorkClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter: Boolean = true,
    ): UsbProtocolReply

    /**
     * Agrupa múltiplas transações inseparáveis, por exemplo write + readback.
     * A telemetria só volta a disputar a porta após o bloco terminar.
     */
    fun <T> unit(
        reason: String,
        expectedSessionId: Long = 0L,
        workClass: Mp48WorkClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter: Boolean = true,
        waitTimeoutMs: Long = 6_000L,
        block: (Mp48SerialUnit) -> T,
    ): T
}
