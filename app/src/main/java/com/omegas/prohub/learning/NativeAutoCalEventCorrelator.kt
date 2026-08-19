package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.max

/** Correlaciona evento nativo gasolina/GNV com uma microjanela física tipada. */
object NativeAutoCalEventCorrelator {
    enum class SourceFuel { PETROL, CNG }

    data class Result(
        val state: String,
        val reason: String,
        val sourceFuel: SourceFuel,
        val confidence: Double,
        val rpmConfidence: Double,
        val rpm: Int?,
        val mapBar: Double?,
        val petrolMs: Double?,
        val gasMsDiagnostic: Double?,
        val correlatedFrameElapsedMs: Long?,
        val lagMs: Long?,
        val windowFromElapsedMs: Long,
        val windowToElapsedMs: Long,
        val firstSequence: Long?,
        val lastSequence: Long?,
        val matchedFrames: Int,
        val overlapKey: String?,
        val canCloseWindowEarly: Boolean,
    )

    fun correlate(
        frames: List<NativeAnchorTelemetryWindow.Frame>,
        sourceFuel: SourceFuel,
        nativePetrolMs: Double?,
        nativeMapBar: Double?,
        observedAtElapsedMs: Long,
        windowFromElapsedMs: Long,
        windowToElapsedMs: Long,
        policy: LearningTolerancePolicy,
        sessionId: Long,
    ): Result {
        if (windowToElapsedMs < windowFromElapsedMs) {
            return inconclusive(sourceFuel, "INVALID_WINDOW", windowFromElapsedMs, windowToElapsedMs)
        }
        if (nativePetrolMs == null || nativeMapBar == null || !nativePetrolMs.isFinite() || !nativeMapBar.isFinite()) {
            return inconclusive(sourceFuel, "NO_NATIVE_CONTEXT", windowFromElapsedMs, windowToElapsedMs)
        }
        val expectedFuel = when (sourceFuel) {
            SourceFuel.PETROL -> setOf("PETROL", "GASOLINA")
            SourceFuel.CNG -> setOf("GNV", "CNG")
        }
        val inWindow = frames.filter {
            it.sessionId == sessionId &&
                it.elapsedMs in windowFromElapsedMs..windowToElapsedMs &&
                it.plausible &&
                it.fuel.uppercase() in expectedFuel
        }.sortedBy { it.elapsedMs }
        if (inWindow.isEmpty()) return inconclusive(sourceFuel, "EMPTY_OR_FUEL_MISMATCH", windowFromElapsedMs, windowToElapsedMs)

        val active = policy.normalized()
        if (observedAtElapsedMs - inWindow.last().elapsedMs > active.breakingGapMs) {
            return inconclusive(sourceFuel, "STALE_WINDOW", windowFromElapsedMs, windowToElapsedMs)
        }
        if (inWindow.zipWithNext().any { (a, b) -> b.elapsedMs - a.elapsedMs > active.breakingGapMs }) {
            return inconclusive(sourceFuel, "CONTINUITY_GAP", windowFromElapsedMs, windowToElapsedMs)
        }

        val petrolTolerance = max(active.petrolCenterMinimumMs, abs(nativePetrolMs) * active.petrolCenterPercent / 100.0)
        val compatible = inWindow.filter {
            abs(it.petrolMs - nativePetrolMs) <= petrolTolerance &&
                abs(it.mapBar - nativeMapBar) <= active.mapCenterBar
        }
        if (compatible.size < 2) {
            return inconclusive(sourceFuel, "INSUFFICIENT_PHYSICAL_SUPPORT", windowFromElapsedMs, windowToElapsedMs, compatible.size)
        }

        val rpms = compatible.map { it.rpm }.sorted()
        val rpmMedian = if (rpms.size % 2 == 0) (rpms[rpms.size / 2 - 1] + rpms[rpms.size / 2]) / 2 else rpms[rpms.size / 2]
        val rpmTolerance = max(active.rpmOscillationMinimum, rpmMedian * active.rpmOscillationPercent / 100.0)
        val rpmSpan = (rpms.last() - rpms.first()).toDouble()
        if (rpmSpan >= rpmTolerance) {
            return inconclusive(sourceFuel, "RPM_AMBIGUITY", windowFromElapsedMs, windowToElapsedMs, compatible.size)
        }

        val best = compatible.minByOrNull {
            abs(it.petrolMs - nativePetrolMs) / petrolTolerance +
                abs(it.mapBar - nativeMapBar) / active.mapCenterBar +
                abs(observedAtElapsedMs - it.elapsedMs).toDouble() / active.breakingGapMs
        } ?: return inconclusive(sourceFuel, "NO_CANDIDATE", windowFromElapsedMs, windowToElapsedMs)

        val petrolFit = compatible.map { 1.0 - (abs(it.petrolMs - nativePetrolMs) / petrolTolerance).coerceIn(0.0, 1.0) }.average()
        val mapFit = compatible.map { 1.0 - (abs(it.mapBar - nativeMapBar) / active.mapCenterBar).coerceIn(0.0, 1.0) }.average()
        val rpmFit = 1.0 - (rpmSpan / rpmTolerance).coerceIn(0.0, 1.0)
        val temporalFit = compatible.map {
            1.0 - ((observedAtElapsedMs - it.elapsedMs).coerceAtLeast(0L).toDouble() / active.breakingGapMs).coerceIn(0.0, 1.0)
        }.average()
        val confidence = ((petrolFit + mapFit + rpmFit + temporalFit) / 4.0).coerceIn(0.0, 1.0)
        val overlapKey = "$sessionId:${compatible.first().sequence}-${compatible.last().sequence}:${sourceFuel.name}"

        // Early-close não usa N/confidence target arbitrário. Só fecha quando todos
        // os frames elegíveis da janela pertencem ao mesmo suporte físico já validado.
        val unambiguousObservedWindow = compatible.size == inWindow.size

        return Result(
            state = "CORRELATED",
            reason = "STABLE_NATIVE_EVENT_WINDOW",
            sourceFuel = sourceFuel,
            confidence = confidence,
            rpmConfidence = rpmFit,
            rpm = best.rpm,
            mapBar = best.mapBar,
            petrolMs = best.petrolMs,
            gasMsDiagnostic = best.gasMsDiagnostic?.takeIf(Double::isFinite),
            correlatedFrameElapsedMs = best.elapsedMs,
            lagMs = (observedAtElapsedMs - best.elapsedMs).coerceAtLeast(0L),
            windowFromElapsedMs = windowFromElapsedMs,
            windowToElapsedMs = windowToElapsedMs,
            firstSequence = compatible.first().sequence,
            lastSequence = compatible.last().sequence,
            matchedFrames = compatible.size,
            overlapKey = overlapKey,
            canCloseWindowEarly = unambiguousObservedWindow,
        )
    }

    private fun inconclusive(
        sourceFuel: SourceFuel,
        reason: String,
        from: Long,
        to: Long,
        matchedFrames: Int = 0,
    ) = Result(
        state = "INCONCLUSIVE",
        reason = reason,
        sourceFuel = sourceFuel,
        confidence = 0.0,
        rpmConfidence = 0.0,
        rpm = null,
        mapBar = null,
        petrolMs = null,
        gasMsDiagnostic = null,
        correlatedFrameElapsedMs = null,
        lagMs = null,
        windowFromElapsedMs = from,
        windowToElapsedMs = to,
        firstSequence = null,
        lastSequence = null,
        matchedFrames = matchedFrames,
        overlapKey = null,
        canCloseWindowEarly = false,
    )
}
