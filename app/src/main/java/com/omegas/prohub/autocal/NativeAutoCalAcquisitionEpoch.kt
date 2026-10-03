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
        reason = if (count > before) "NATIVE_AUTOMATCH" else "AUTOMATCH_COUNTER_RESTART"
        return true
    }

    /** Somente recibos de ações confirmadas, nunca intenção de clique. */
    fun manualAction(sessionId: Long, action: String): Boolean {
        if (sessionId <= 0L || sessionId != usbSessionId) return false
        when (action) {
            "RESET_PETROL" -> {
                petrolGeneration++
                petrolPending = true
                petrolReferencePending = true
                petrolSamples = 0
                previousPetrol = null
            }
            "RESET_GAS" -> {
                gasGeneration++
                gasPending = true
                gasReferencePending = true
                gasSamples = 0
                previousGas = null
                nativeCount = null // o contador pode voltar a 0: novo baseline, sem duplicar época
            }
            "RESET_ALL" -> {
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
        previousPetrol = petrol.copyOf()
        previousGas = gas.copyOf()
        petrolSamples = petrol.count { it > 0 }
        gasSamples = gas.count { it > 0 }
        petrolPending = false
        gasPending = false
        return true
    }

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
