package com.omegas.prohub.obd

enum class ElmStage {
    IDLE,
    PERMISSION,
    RFCOMM,
    ELM_INIT,
    PROTOCOL,
    STFT_READY,
    LIVE,
    ERROR,
}

data class ElmConnectionStatus(
    val stage: ElmStage,
    val errorCode: String = "",
    val detail: String = "",
    val startedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val retryable: Boolean = false,
)

/**
 * State machine pura da conexão ELM. O socket Android fica fora daqui; esta
 * classe apenas torna os estágios, timeouts e falhas observáveis/testáveis.
 */
class ElmConnectionState(
    private val connectTimeoutMs: Long = 12_000L,
    private val handshakeTimeoutMs: Long = 6_000L,
) {
    private var current = ElmConnectionStatus(stage = ElmStage.IDLE)

    @Synchronized
    fun snapshot(): ElmConnectionStatus = current.copy()

    @Synchronized
    fun enter(stage: ElmStage, nowMs: Long, detail: String = ""): ElmConnectionStatus {
        current = ElmConnectionStatus(
            stage = stage,
            errorCode = "",
            detail = detail,
            startedAtMs = nowMs,
            updatedAtMs = nowMs,
            retryable = false,
        )
        return current.copy()
    }

    @Synchronized
    fun fail(errorCode: String, detail: String, nowMs: Long, retryable: Boolean = true): ElmConnectionStatus {
        val started = current.startedAtMs.takeIf { it > 0L } ?: nowMs
        current = ElmConnectionStatus(
            stage = ElmStage.ERROR,
            errorCode = errorCode,
            detail = detail,
            startedAtMs = started,
            updatedAtMs = nowMs,
            retryable = retryable,
        )
        return current.copy()
    }

    @Synchronized
    fun onClock(nowMs: Long): ElmConnectionStatus {
        val elapsed = (nowMs - current.startedAtMs).coerceAtLeast(0L)
        when (current.stage) {
            ElmStage.RFCOMM -> if (current.startedAtMs > 0L && elapsed > connectTimeoutMs) {
                return fail("RFCOMM_TIMEOUT", "Conexão Bluetooth excedeu ${connectTimeoutMs} ms", nowMs)
            }
            ElmStage.ELM_INIT -> if (current.startedAtMs > 0L && elapsed > handshakeTimeoutMs) {
                return fail("ELM_INIT_TIMEOUT", "Inicialização ELM excedeu ${handshakeTimeoutMs} ms", nowMs)
            }
            ElmStage.PROTOCOL -> if (current.startedAtMs > 0L && elapsed > handshakeTimeoutMs) {
                return fail("PROTOCOL_TIMEOUT", "Negociação OBD excedeu ${handshakeTimeoutMs} ms", nowMs)
            }
            else -> Unit
        }
        if (nowMs > current.updatedAtMs) current = current.copy(updatedAtMs = nowMs)
        return current.copy()
    }
}
