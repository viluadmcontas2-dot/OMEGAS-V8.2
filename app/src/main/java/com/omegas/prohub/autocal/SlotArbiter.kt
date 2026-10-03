package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * Árbitro de fatias da porta serial para a aquisição AutoCal (Lote D).
 *
 * Regra: no máximo UM grupo de leitura por vez e, depois dele, a porta volta ao ciclo vivo
 * até passarem pelo menos [minLiveFrames] quadros vivos (ou [minLiveMs] de polling vivo quando a
 * contagem de quadros não é informada). Assim nenhum trabalho AutoCal segura a porta por mais de
 * um grupo (<= 3 leituras, ~150 ms) e o cursor ao vivo nunca para por ~0,5 s.
 *
 * Não tem thread, timer nem I/O e não conhece comandos: só decide QUANDO um grupo pode começar.
 * SAFETY e MANUAL_WRITE continuam passando à frente pela fila priorizada do scheduler MP48
 * ([com.omegas.prohub.ecu.Mp48WorkClass]); como cada grupo é uma unidade curta, a preempção acontece
 * na fronteira do grupo.
 */
class SlotArbiter(
    private val clock: () -> Long,
    private val liveFrames: () -> Long = { -1L },
    private val minLiveFrames: Int = MIN_LIVE_FRAMES,
    private val minLiveMs: Long = MIN_LIVE_MS,
    private val maxDeferMs: Long = MAX_DEFER_MS,
) {
    private var inGroup = false
    private var lastEndAtMs = Long.MIN_VALUE
    private var framesAtEnd = -1L
    private var slotsGranted = 0L
    private var deferrals = 0L

    /** Verdadeiro se um novo grupo pode começar agora (sem reservar o slot). */
    @Synchronized
    fun canStart(): Boolean {
        if (inGroup) return false
        if (lastEndAtMs == Long.MIN_VALUE) return true
        val now = clock()
        val sinceEnd = now - lastEndAtMs
        if (sinceEnd >= maxDeferMs) return true // válvula: nunca trava a aquisição se o vivo silenciar
        val frames = liveFrames()
        return if (frames >= 0L && framesAtEnd >= 0L) {
            frames - framesAtEnd >= minLiveFrames
        } else {
            sinceEnd >= minLiveMs
        }
    }

    /** Reserva o slot se permitido; quem recebe `true` DEVE chamar [end]. */
    @Synchronized
    fun tryBegin(): Boolean {
        if (!canStart()) {
            deferrals += 1
            return false
        }
        inGroup = true
        slotsGranted += 1
        return true
    }

    /** Libera o slot; a contagem de quadros vivos para o próximo grupo parte daqui. */
    @Synchronized
    fun end() {
        inGroup = false
        lastEndAtMs = clock()
        framesAtEnd = liveFrames()
    }

    /**
     * Espera bloqueante (somente para o snapshot completo, que já ocupa a thread do tick):
     * dorme em passos curtos até o slot abrir. Devolve false se estourar [maxWaitMs].
     */
    fun awaitSlot(maxWaitMs: Long, sleep: (Long) -> Unit = { Thread.sleep(it) }): Boolean {
        val deadline = clock() + maxWaitMs
        while (true) {
            if (tryBegin()) return true
            if (clock() >= deadline) return false
            sleep(POLL_MS)
        }
    }

    @Synchronized
    fun reset() {
        inGroup = false
        lastEndAtMs = Long.MIN_VALUE
        framesAtEnd = -1L
    }

    @Synchronized
    fun json(): JSONObject = JSONObject()
        .put("minLiveFrames", minLiveFrames)
        .put("minLiveMs", minLiveMs)
        .put("slotsGranted", slotsGranted)
        .put("deferrals", deferrals)
        .put("inGroup", inGroup)

    companion object {
        const val MIN_LIVE_FRAMES = 3
        const val MIN_LIVE_MS = 150L
        const val MAX_DEFER_MS = 2_000L
        private const val POLL_MS = 10L
    }
}

/**
 * Contadores de idade/uso por tipo de leitura (instrumentação do Lote D). Só observa: não decide nada.
 * `busShare` é uma estimativa (tempo de unidade AutoCal ÷ tempo desde a primeira), pois a duração
 * de uma unidade inclui, no máximo, a espera pela fila do scheduler.
 */
class AcquisitionDuty(private val clock: () -> Long) {
    private class Kind {
        var count = 0L
        var failures = 0L
        var lastEndAtMs = 0L
        var lastDurationMs = 0L
        var maxDurationMs = 0L
        var totalMs = 0L
    }

    private val kinds = linkedMapOf<String, Kind>()
    private var firstAtMs = 0L
    private var busyMs = 0L
    private var maxGroupMs = 0L
    private var roundsCompleted = 0L
    private var lastRoundMs = 0L
    private var lastRoundStartedAtMs = 0L
    private var lastError = ""

    @Synchronized
    fun reset() {
        kinds.clear()
        firstAtMs = 0L
        busyMs = 0L
        maxGroupMs = 0L
        roundsCompleted = 0L
        lastRoundMs = 0L
        lastRoundStartedAtMs = 0L
        lastError = ""
    }

    @Synchronized
    fun record(kind: String, startedAtMs: Long, durationMs: Long, ok: Boolean, error: String = "") {
        if (firstAtMs == 0L) firstAtMs = startedAtMs.coerceAtLeast(1L)
        val entry = kinds.getOrPut(kind) { Kind() }
        entry.count += 1
        if (!ok) entry.failures += 1
        entry.lastEndAtMs = startedAtMs + durationMs
        entry.lastDurationMs = durationMs
        entry.maxDurationMs = maxOf(entry.maxDurationMs, durationMs)
        entry.totalMs += durationMs
        busyMs += durationMs
        maxGroupMs = maxOf(maxGroupMs, durationMs)
        if (!ok && error.isNotBlank()) lastError = error.take(120)
    }

    @Synchronized
    fun roundStarted(atMs: Long) {
        lastRoundStartedAtMs = atMs
    }

    @Synchronized
    fun roundCompleted(atMs: Long) {
        if (lastRoundStartedAtMs > 0L) lastRoundMs = (atMs - lastRoundStartedAtMs).coerceAtLeast(0L)
        roundsCompleted += 1
    }

    @Synchronized
    fun busShare(nowMs: Long = clock()): Double {
        if (firstAtMs == 0L) return 0.0
        val window = (nowMs - firstAtMs).coerceAtLeast(1L)
        return (busyMs.toDouble() / window).coerceIn(0.0, 1.0)
    }

    @Synchronized
    fun json(liveFrameAgeMs: Long, liveFrameCount: Long): JSONObject {
        val now = clock()
        val groups = JSONObject()
        kinds.forEach { (key, k) ->
            groups.put(
                key,
                JSONObject()
                    .put("count", k.count)
                    .put("failures", k.failures)
                    .put("ageMs", if (k.lastEndAtMs > 0L) (now - k.lastEndAtMs).coerceAtLeast(0L) else -1L)
                    .put("lastMs", k.lastDurationMs)
                    .put("maxMs", k.maxDurationMs),
            )
        }
        return JSONObject()
            .put("liveFrameAgeMs", liveFrameAgeMs)
            .put("liveFrameCount", liveFrameCount)
            .put("groups", groups)
            .put("busyMs", busyMs)
            .put("busShare", Math.round(busShare(now) * 1000.0) / 1000.0)
            .put("maxGroupMs", maxGroupMs)
            .put("roundsCompleted", roundsCompleted)
            .put("lastRoundMs", lastRoundMs)
            .put("lastError", lastError)
    }
}
