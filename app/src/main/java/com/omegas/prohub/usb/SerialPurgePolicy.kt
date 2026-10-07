package com.omegas.prohub.usb

/**
 * Política do purge antes de TODA transação serial (o ProgBase limpa TX/RX antes das 39.524 trocas
 * do PortmonLOGNOVO, ~1,8 ms cada).
 *
 * Duas camadas, sempre ANTES de escrever o pedido (nunca depois: a resposta da transação em curso só
 * existe depois da escrita, então o purge não pode apagar resposta válida; só sobra de resposta
 * atrasada de uma transação anterior):
 *  1. Fila RX em software: sempre, custo ~zero.
 *  2. Purge de hardware TX+RX do driver (transferência de controle USB): ligado por padrão, mas se
 *     falhar ou ficar lento ele se desliga sozinho para a sessão (volta só à camada 1) e registra
 *     uma vez. Assim o purge nunca come o prazo de telemetria (240 ms por fase, contado só depois
 *     da escrita) nem trava o laço.
 *
 * Classe pura (sem Android) para teste unitário. Não escreve nada na ECU.
 */
class SerialPurgePolicy(
    private val slowMs: Long = DEFAULT_SLOW_MS,
    private val slowStrikesToDisable: Int = DEFAULT_SLOW_STRIKES,
    private val staleLogEvery: Int = DEFAULT_STALE_LOG_EVERY,
) {
    @Volatile var hardwareEnabled: Boolean = true
        private set
    @Volatile var disabledReason: String = ""
        private set

    private var slowStrikes = 0
    private var staleEvents = 0L
    @Volatile var staleBytesTotal: Long = 0L
        private set
    @Volatile var purges: Long = 0L
        private set

    /** Nova conexão/porta: tenta o purge de hardware de novo. */
    @Synchronized
    fun reset() {
        hardwareEnabled = true
        disabledReason = ""
        slowStrikes = 0
    }

    /**
     * Registra um purge de hardware. Devolve uma mensagem (para o registro do sistema) somente quando
     * este purge fez a política DESLIGAR o purge de hardware; senão null.
     */
    @Synchronized
    fun recordHardware(durationMs: Long, ok: Boolean): String? {
        purges += 1
        if (!hardwareEnabled) return null
        if (!ok) return disable("driver recusou o purge de hardware")
        if (durationMs > slowMs) {
            slowStrikes += 1
            if (slowStrikes >= slowStrikesToDisable) {
                return disable("purge de hardware lento ($durationMs ms > $slowMs ms, $slowStrikes vezes seguidas)")
            }
        } else {
            slowStrikes = 0
        }
        return null
    }

    /**
     * Bytes descartados pela fila RX antes da transação = sobra de resposta atrasada.
     * Devolve true quando vale registrar (a primeira vez e depois a cada [staleLogEvery]).
     */
    @Synchronized
    fun recordStale(bytes: Int): Boolean {
        if (bytes <= 0) return false
        staleBytesTotal += bytes
        staleEvents += 1
        return staleEvents == 1L || staleEvents % staleLogEvery == 0L
    }

    private fun disable(reason: String): String {
        hardwareEnabled = false
        disabledReason = reason
        return "Purge de hardware pré-transação desligado nesta sessão: $reason; segue só a limpeza da fila RX"
    }

    companion object {
        const val DEFAULT_SLOW_MS = 40L
        const val DEFAULT_SLOW_STRIKES = 3
        const val DEFAULT_STALE_LOG_EVERY = 50
    }
}
