package com.omegas.prohub.carinfo

import org.json.JSONObject

/**
 * Estado e reconexão da fonte Car Info, sem Android (testável com relógio e agenda falsos).
 *
 * Falhar em ligar à multimídia é um estado NORMAL ("indisponivel"): o bind pode ser negado em
 * runtime (SELinux, assinatura, central sem o serviço). Nova tentativa com espera dobrando
 * (10 s, 20 s, 40 s... até 10 min), nunca em laço apertado.
 *
 * @param bind pede a ligação; true = pedido aceito (a conexão chega depois por [onConnected]).
 * @param unbind solta a ligação atual (idempotente do lado de quem implementa).
 * @param schedule agenda uma ação para daqui a `delayMs`.
 */
class CarInfoSupervisor(
    private val clock: () -> Long,
    private val bind: () -> Boolean,
    private val unbind: () -> Unit,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) {
    companion object {
        const val BASE_RETRY_MS = 10_000L
        const val MAX_RETRY_MS = 600_000L

        fun retryDelayMs(failures: Int): Long {
            if (failures <= 1) return BASE_RETRY_MS
            val shift = (failures - 1).coerceAtMost(20)
            return (BASE_RETRY_MS shl shift).coerceAtMost(MAX_RETRY_MS)
        }
    }

    private var enabled = false
    private var connected = false
    private var binding = false
    private var lastError = ""
    private var failures = 0
    private var samples = 0L
    private var lastSampleAtMs = 0L
    private var firstSampleAtMs = 0L
    private var lastSample: CarInfoSample? = null
    private var nextRetryAtMs = 0L
    /** Cada start/stop muda a geração: tentativa agendada de geração antiga não vale. */
    private var generation = 0L

    @Synchronized fun isEnabled(): Boolean = enabled
    @Synchronized fun isConnected(): Boolean = connected

    @Synchronized
    fun start() {
        if (enabled) return
        enabled = true
        generation += 1
        failures = 0
        attemptLocked()
    }

    @Synchronized
    fun stop() {
        if (!enabled) return
        enabled = false
        generation += 1
        connected = false
        binding = false
        nextRetryAtMs = 0L
        unbind()
    }

    @Synchronized
    fun onConnected() {
        if (!enabled) return
        connected = true
        binding = false
        firstSampleAtMs = 0L
        failures = 0
        lastError = ""
        nextRetryAtMs = 0L
    }

    @Synchronized
    fun onFailure(reason: String) {
        if (!enabled) return
        connected = false
        binding = false
        lastError = reason.ifBlank { "Multimídia indisponível" }
        unbind()
        scheduleRetryLocked()
    }

    @Synchronized
    fun onSample(sample: CarInfoSample) {
        if (!enabled) return
        lastSample = sample
        lastSampleAtMs = clock()
        if (firstSampleAtMs == 0L) firstSampleAtMs = lastSampleAtMs
        samples += 1
    }

    @Synchronized fun lastSample(): CarInfoSample? = lastSample

    private fun attemptLocked() {
        if (!enabled || connected || binding) return
        val accepted = try { bind() } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
        if (accepted) {
            binding = true
        } else {
            if (lastError.isBlank()) lastError = "Multimídia não aceitou a ligação"
            unbind()
            scheduleRetryLocked()
        }
    }

    private fun scheduleRetryLocked() {
        failures += 1
        val delay = retryDelayMs(failures)
        nextRetryAtMs = clock() + delay
        val token = generation
        schedule(delay) {
            synchronized(this) {
                if (token != generation || !enabled || connected) return@synchronized
                binding = false
                attemptLocked()
            }
        }
    }

    @Synchronized
    fun json(): JSONObject {
        val state = when {
            !enabled -> "desligado"
            connected -> "conectado"
            binding -> "conectando"
            else -> "indisponivel"
        }
        return JSONObject()
            .put("source", "multimidia")
            .put("readOnly", true)
            .put("enabled", enabled)
            .put("connected", connected)
            .put("state", state)
            .put("lastError", lastError)
            .put("failures", failures)
            .put("samples", samples)
            .put("lastSampleAtMs", if (lastSampleAtMs > 0L) lastSampleAtMs else JSONObject.NULL)
            .put("firstSampleAtMs", if (firstSampleAtMs > 0L) firstSampleAtMs else JSONObject.NULL)
            .put("nextRetryAtMs", if (nextRetryAtMs > 0L) nextRetryAtMs else JSONObject.NULL)
            .put("sample", lastSample?.toJson() ?: JSONObject.NULL)
    }
}
