package com.omegas.prohub.autocal

/**
 * Medição puramente observacional do probe leve AutoCal.
 * Não agenda I/O, não altera cadência, prioridade ou timeout e não escreve ECU.
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
        val lastWallElapsedMs: Long,
        val maxWallElapsedMs: Long,
        val observationSpanMs: Long,
        val lastCadenceMs: Long?,
        val lastTelemetryGapMs: Long?,
        val maxTelemetryGapMs: Long?,
        val pendingTelemetryGap: Boolean,
    ) {
        val informationYield: Double get() = if (cycles <= 0L) 0.0 else materialChanges.toDouble() / cycles.toDouble()
        val averageWallElapsedMs: Double? get() = if (cycles <= 0L) null else wallElapsedMs.toDouble() / cycles.toDouble()
        val lastCostShare: Double? get() = lastCadenceMs?.takeIf { it > 0L }?.let { lastWallElapsedMs.toDouble() / it.toDouble() }
    }

    private var cycles = 0L
    private var successfulCycles = 0L
    private var fallbackCycles = 0L
    private var materialChanges = 0L
    private var requestBytes = 0L
    private var responseBytes = 0L
    private var serialElapsedMs = 0L
    private var wallElapsedMs = 0L
    private var lastWallElapsedMs = 0L
    private var maxWallElapsedMs = 0L
    private var firstProbeStartedAtElapsedMs = 0L
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
        lastWallElapsedMs = 0L
        maxWallElapsedMs = 0L
        firstProbeStartedAtElapsedMs = 0L
        lastProbeStartedAtElapsedMs = 0L
        lastCadenceMs = null
        lastTelemetryGapMs = null
        maxTelemetryGapMs = null
        pendingTelemetryBeforeMs = null
        pendingProbeFinishedAtElapsedMs = null
        AutoCal122ATargetMetrics.updateProbeMetrics(snapshot())
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
        AutoCal122ATargetMetrics.updateProbeMetrics(snapshot())
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
        if (firstProbeStartedAtElapsedMs <= 0L) firstProbeStartedAtElapsedMs = startedAtElapsedMs
        if (lastProbeStartedAtElapsedMs > 0L && startedAtElapsedMs >= lastProbeStartedAtElapsedMs) lastCadenceMs = startedAtElapsedMs - lastProbeStartedAtElapsedMs
        lastProbeStartedAtElapsedMs = startedAtElapsedMs
        cycles += 1L
        if (success) successfulCycles += 1L
        if (fallbackUsed) fallbackCycles += 1L
        this.requestBytes += requestBytes.coerceAtLeast(0).toLong()
        this.responseBytes += responseBytes.coerceAtLeast(0).toLong()
        this.serialElapsedMs += serialElapsedMs.coerceAtLeast(0L)
        wallElapsedMs += wall
        lastWallElapsedMs = wall
        maxWallElapsedMs = maxOf(maxWallElapsedMs, wall)
        pendingTelemetryBeforeMs = lastTelemetryBeforeMs
        pendingProbeFinishedAtElapsedMs = lastTelemetryBeforeMs?.let { finishedAtElapsedMs }
        AutoCal122ATargetMetrics.updateProbeMetrics(snapshot())
    }

    @Synchronized fun markMaterialChange() { materialChanges += 1L }

    @Synchronized
    fun snapshot(): Snapshot {
        val observationSpan = if (firstProbeStartedAtElapsedMs > 0L && lastProbeStartedAtElapsedMs >= firstProbeStartedAtElapsedMs) lastProbeStartedAtElapsedMs - firstProbeStartedAtElapsedMs else 0L
        return Snapshot(
            cycles, successfulCycles, fallbackCycles, materialChanges, requestBytes, responseBytes,
            serialElapsedMs, wallElapsedMs, lastWallElapsedMs, maxWallElapsedMs, observationSpan,
            lastCadenceMs, lastTelemetryGapMs, maxTelemetryGapMs, pendingTelemetryBeforeMs != null,
        )
    }
}
