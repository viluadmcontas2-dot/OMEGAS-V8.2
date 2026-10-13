package com.omegas.prohub.autocal

/**
 * Estado de aquisição corrente, derivado exclusivamente dos contadores/recibos da ECU.
 * Não agenda consultas, não calcula AutoMatch e não escreve parâmetros.
 * Um grupo lido sob contador nativo diferente nunca quita um reset pendente.
 */
class NativeAutoCalAcquisitionEpoch {
    data class View(
        val usbSessionId: Long,
        val nativeAutoMatchCount: Int?,
        val petrolGeneration: Int,
        val gasGeneration: Int,
        val petrolPending: Boolean,
        val gasPending: Boolean,
        val referencePending: Boolean,
        val petrolReferencePending: Boolean,
        val gasReferencePending: Boolean,
        val petrolSamples: Int,
        val gasSamples: Int,
        val reason: String,
    ) {
        val comparisonAllowed: Boolean get() = usbSessionId > 0L &&
            !petrolPending && !gasPending && !referencePending &&
            petrolSamples >= MIN_COMMON_BANDS && gasSamples >= MIN_COMMON_BANDS
    }

    private var usbSessionId = 0L
    private var nativeCount: Int? = null
    private var petrolGeneration = 0
    private var gasGeneration = 0
    private var petrolPending = true
    private var gasPending = true
    private var petrolReferencePending = true
    private var gasReferencePending = true
    private val referencePending: Boolean get() = petrolReferencePending || gasReferencePending
    private var petrolSamples = 0
    private var gasSamples = 0
    private var previousPetrol: IntArray? = null
    private var previousGas: IntArray? = null
    /**
     * Último grupo ACEITO de cada combustível, que sobrevive à troca de época do AutoMatch nativo (previous* zera nela).
     * Serve de base para provar que um reset foi aplicado (fuzz 2026-10-13: reset logo depois de um AutoMatch não tinha base e o
     * combustível ficava "pendente" para sempre se o carro já tivesse voltado a adquirir antes de a leitura ver zero).
     */
    private var lastSeenPetrol: IntArray? = null
    private var lastSeenGas: IntArray? = null
    /** Reset manual/AutoMatch pediu nova aquisição: só um grupo com os contadores daquele combustível zerados a confirma. */
    private var petrolAwaitingZero = false
    private var gasAwaitingZero = false
    /** Contadores do combustível no instante do reset: a prova de que a ECU aplicou o reset é zerar OU ficar abaixo disto. */
    private var petrolBaseline: IntArray? = null
    private var gasBaseline: IntArray? = null
    private var reason = "SESSION_BOOTSTRAP"

    fun reset(sessionId: Long) {
        usbSessionId = sessionId
        nativeCount = null
        petrolGeneration = 0
        gasGeneration = 0
        petrolPending = true
        gasPending = true
        petrolReferencePending = true
        gasReferencePending = true
        petrolSamples = 0
        gasSamples = 0
        previousPetrol = null
        previousGas = null
        lastSeenPetrol = null
        lastSeenGas = null
        petrolAwaitingZero = false
        gasAwaitingZero = false
        petrolBaseline = null
        gasBaseline = null
        reason = if (sessionId > 0L) "SESSION_BOOTSTRAP" else "DISCONNECTED"
    }

    /** Observa o contador da ECU antes do grupo pesado, para invalidar já no evento. */
    fun nativeCounter(sessionId: Long, count: Int): Boolean {
        if (sessionId <= 0L || count < 0) return false
        if (sessionId != usbSessionId) reset(sessionId)
        val before = nativeCount
        nativeCount = count
        if (before == null || before == count) return false
        gasGeneration++
        gasPending = true
        gasReferencePending = true
        gasSamples = 0
        previousGas = null
        // AutoMatch nativo não zera os contadores (contrato allThreeMatchesPermitAcquisitionEvenWithoutObservedZero).
        reason = if (count > before) "NATIVE_AUTOMATCH" else "AUTOMATCH_COUNTER_RESTART"
        return true
    }

    /** Somente recibos de ações confirmadas, nunca intenção de clique. */
    fun manualAction(sessionId: Long, action: String): Boolean {
        if (sessionId <= 0L || sessionId != usbSessionId) return false
        when (action) {
            "RESET_PETROL" -> {
                petrolBaseline = previousPetrol ?: lastSeenPetrol ?: petrolBaseline
                petrolGeneration++
                petrolPending = true
                petrolReferencePending = true
                petrolSamples = 0
                previousPetrol = null
                petrolAwaitingZero = true
            }
            "RESET_GAS", "MANUAL_AUTOMATCH" -> {
                gasBaseline = previousGas ?: lastSeenGas ?: gasBaseline
                gasGeneration++
                gasPending = true
                gasReferencePending = true
                gasSamples = 0
                previousGas = null
                gasAwaitingZero = true
                nativeCount = null // o contador pode voltar a 0: novo baseline, sem duplicar época
            }
            "RESET_ALL" -> {
                petrolBaseline = previousPetrol ?: lastSeenPetrol ?: petrolBaseline
                gasBaseline = previousGas ?: lastSeenGas ?: gasBaseline
                petrolGeneration++
                gasGeneration++
                petrolPending = true
                gasPending = true
                petrolReferencePending = true
                gasReferencePending = true
                petrolSamples = 0
                gasSamples = 0
                previousPetrol = null
                previousGas = null
                petrolAwaitingZero = true
                gasAwaitingZero = true
                nativeCount = null
            }
            else -> return false // RESET_K_FACTOR, pausa, Finish etc. NÃO resetam aquisição
        }
        reason = action
        return true
    }

    /** Aceita apenas um grupo de contadores completos da sessão e época nativas atuais. */
    fun acquisitionGroup(sessionId: Long, count: Int, petrol: IntArray?, gas: IntArray?): Boolean {
        if (sessionId <= 0L || sessionId != usbSessionId || nativeCount != count ||
            petrol?.size != BAND_COUNT || gas?.size != BAND_COUNT ||
            petrol.any { it < 0 } || gas.any { it < 0 }
        ) return false

        if (!petrolPending && previousPetrol?.any { it > 0 } == true && petrol.all { it == 0 }) {
            petrolGeneration++
            petrolReferencePending = true
            reason = "PETROL_COUNTER_RESTART"
        }
        if (!gasPending && previousGas?.any { it > 0 } == true && gas.all { it == 0 }) {
            gasGeneration++
            gasReferencePending = true
            reason = "GAS_COUNTER_RESTART"
        }
        // Bug visto no carro (dono, 2026-10-12): depois de "Reaprender GNV" a ECU ainda devolve os contadores antigos
        // por alguns ciclos; aceitar esse grupo rearmava a época e a tela voltava a mostrar os 12/18 pontos apagados
        // como vivos. Um combustível reiniciado só sai de "pendente" quando a ECU devolve os contadores dele
        // zerados (único pós-estado que o corpus prova); o outro combustível segue normalmente.
        // Cada combustível decide por si (dono, 2026-10-13: GNV e gasolina podem ser adquiridos em qualquer ordem e a qualquer
        // momento): um reset ainda não aplicado num deles NUNCA segura o outro. O reset conta como aplicado quando a ECU
        // devolve os contadores zerados OU abaixo do que havia no reset (se o carro já voltou a adquirir antes da próxima
        // leitura, os contadores nem chegam a zero — esperar só o zero deixava o combustível "pendente" para sempre).
        val petrolStale = petrolAwaitingZero && !resetApplied(petrol, petrolBaseline)
        val gasStale = gasAwaitingZero && !resetApplied(gas, gasBaseline)
        if (petrolStale || gasStale) reason = if (gasStale) "GAS_RESET_NOT_APPLIED_YET" else "PETROL_RESET_NOT_APPLIED_YET"
        if (!petrolStale) {
            petrolAwaitingZero = false
            petrolBaseline = null
            previousPetrol = petrol.copyOf()
            lastSeenPetrol = petrol.copyOf()
            petrolSamples = petrol.count { it > 0 }
            petrolPending = false
        }
        if (!gasStale) {
            gasAwaitingZero = false
            gasBaseline = null
            previousGas = gas.copyOf()
            lastSeenGas = gas.copyOf()
            gasSamples = gas.count { it > 0 }
            gasPending = false
        }
        return !petrolStale && !gasStale
    }

    /**
     * "Contadores velhos" = a leitura é IDÊNTICA ao que havia antes do reset (a ECU devolvendo o quadro antigo por alguns
     * ciclos: bug do carro de 2026-10-12). Qualquer diferença (banda menor OU maior, p.ex. o carro já readquirindo) é dado vivo
     * da ECU e o app deve espelhá-lo. Zerado = reset aplicado. Sem base (nada lido na sessão) não há dado velho a esconder.
     * Fuzz 2026-10-13: exigir "menor que a base" deixava o combustível "pendente" para sempre quando o carro readquiria rápido.
     */
    private fun resetApplied(counters: IntArray, baseline: IntArray?): Boolean =
        counters.all { it == 0 } || baseline == null || !counters.contentEquals(baseline)

    /** Grupo RV30 novo, lido após a aquisição da mesma época ter suporte mínimo. */
    fun referenceGroup(sessionId: Long, count: Int): Boolean {
        if (sessionId != usbSessionId || nativeCount != count || petrolPending || gasPending ||
            petrolSamples < MIN_COMMON_BANDS || gasSamples < MIN_COMMON_BANDS) return false
        petrolReferencePending = false
        gasReferencePending = false
        return true
    }

    fun view() = View(
        usbSessionId = usbSessionId,
        nativeAutoMatchCount = nativeCount,
        petrolGeneration = petrolGeneration,
        gasGeneration = gasGeneration,
        petrolPending = petrolPending,
        gasPending = gasPending,
        referencePending = referencePending,
        petrolReferencePending = petrolReferencePending,
        gasReferencePending = gasReferencePending,
        petrolSamples = petrolSamples,
        gasSamples = gasSamples,
        reason = reason,
    )

    companion object {
        const val BAND_COUNT = 18
        const val MIN_COMMON_BANDS = 3 // guarda de apresentação, não limiar OEM de maturidade
    }
}
