package com.omegas.prohub.autocal

/**
 * Medição puramente observacional do probe leve AutoCal.
 *
 * Não agenda I/O, não altera cadência, prioridade ou timeout e não escreve ECU.
 * Apenas acumula custo já observado e resolve, no tick seguinte, o gap real
 * entre o último frame de telemetria antes do probe e o primeiro frame depois.
 */
class AutoCalProbeMetrics {
    data class Snapshot(
        val cycles: Long,
        val successfulCycles: Long,
        val fallbackCycles: Long,
        val materialChanges: Long,
        val requestBytes: Long,
        val responseBytes: Long,
        val serialElapsedMs: Long,
        val wallElapsedMs: Long,
        val maxWallElapsedMs: Long,
        val lastCadenceMs: Long?,
        val lastTelemetryGapMs: Long?,
        val maxTelemetryGapMs: Long?,
        val pendingTelemetryGap: Boolean,
    ) {
        val informationYield: Double
            get() = if (cycles <= 0L) 0.0 else materialChanges.toDouble() / cycles.toDouble()

        val lastCostShare: Double?
            get() = lastCadenceMs
                ?.takeIf { it > 0L }
                ?.let { cadence -> wallElapsedMs.takeIf { cycles == 1L }?.toDouble()?.div(cadence.toDouble()) }
    }

    private var cycles = 0L
    private var successfulCycles = 0L
    private var fallbackCycles = 0L
    private var materialChanges = 0L
    private var requestBytes = 0L
    private var responseBytes = 0L
    private var serialElapsedMs = 0L
    private var wallElapsedMs = 0L
    private var maxWallElapsedMs = 0L
    private var lastCycleWallElapsedMs = 0L
    private var lastProbeStartedAtElapsedMs = 0L
    private var lastCadenceMs: Long? = null
    private var lastTelemetryGapMs: Long? = null
    private var maxTelemetryGapMs: Long? = null
    private var pendingTelemetryBeforeMs: Long? = null
    private var pendingProbeFinishedAtElapsedMs: Long? = null

    @Synchronized
    fun reset() {
        cycles = 0L
        successfulCycles = 0L
        fallbackCycles = 0L
        materialChanges = 0L
        requestBytes = 0L
        responseBytes = 0L
        serialElapsedMs = 0L
        wallElapsedMs = 0L
        maxWallElapsedMs = 0L
        lastCycleWallElapsedMs = 0L
        lastProbeStartedAtElapsedMs = 0L
        lastCadenceMs = null
        lastTelemetryGapMs = null
        maxTelemetryGapMs = null
        pendingTelemetryBeforeMs = null
        pendingProbeFinishedAtElapsedMs = null
    }

    @Synchronized
    fun resolveTelemetryGap(frameElapsedMs: List<Long>) {
        val before = pendingTelemetryBeforeMs ?: return
        val finished = pendingProbeFinishedAtElapsedMs ?: return
        val after = frameElapsedMs.firstOrNull { it >= finished } ?: return
        val gap = (after - before).coerceAtLeast(0L)
        lastTelemetryGapMs = gap
        maxTelemetryGapMs = maxOf(maxTelemetryGapMs ?: gap, gap)
        pendingTelemetryBeforeMs = null
        pendingProbeFinishedAtElapsedMs = null
    }

    @Synchronized
    fun recordCycle(
        startedAtElapsedMs: Long,
        finishedAtElapsedMs: Long,
        requestBytes: Int,
        responseBytes: Int,
        serialElapsedMs: Long,
        success: Boolean,
        fallbackUsed: Boolean,
        lastTelemetryBeforeMs: Long?,
    ) {
        val wall = (finishedAtElapsedMs - startedAtElapsedMs).coerceAtLeast(0L)
        if (lastProbeStartedAtElapsedMs > 0L && startedAtElapsedMs >= lastProbeStartedAtElapsedMs) {
            lastCadenceMs = startedAtElapsedMs - lastProbeStartedAtElapsedMs
        }
        lastProbeStartedAtElapsedMs = startedAtElapsedMs
        cycles += 1L
        if (success) successfulCycles += 1L
        if (fallbackUsed) fallbackCycles += 1L
        this.requestBytes += requestBytes.coerceAtLeast(0).toLong()
        this.responseBytes += responseBytes.coerceAtLeast(0).toLong()
        this.serialElapsedMs += serialElapsedMs.coerceAtLeast(0L)
        wallElapsedMs += wall
        lastCycleWallElapsedMs = wall
        maxWallElapsedMs = maxOf(maxWallElapsedMs, wall)
        pendingTelemetryBeforeMs = lastTelemetryBeforeMs
        pendingProbeFinishedAtElapsedMs = lastTelemetryBeforeMs?.let { finishedAtElapsedMs }
    }

    @Synchronized
    fun markMaterialChange() {
        materialChanges += 1L
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        cycles = cycles,
        successfulCycles = successfulCycles,
        fallbackCycles = fallbackCycles,
        materialChanges = materialChanges,
        requestBytes = requestBytes,
        responseBytes = responseBytes,
        serialElapsedMs = serialElapsedMs,
        wallElapsedMs = wallElapsedMs,
        maxWallElapsedMs = maxWallElapsedMs,
        lastCadenceMs = lastCadenceMs,
        lastTelemetryGapMs = lastTelemetryGapMs,
        maxTelemetryGapMs = maxTelemetryGapMs,
        pendingTelemetryGap = pendingTelemetryBeforeMs != null,
    )
}
