package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.max

/** Correlaciona uma maturidade nativa com telemetria física já capturada pela engine. */
object NativeAutoCalAnchorCorrelator {
    data class Result(
        val state: String,
        val confidence: Double,
        val rpmConfidence: Double,
        val rpm: Int?,
        val mapBar: Double?,
        val petrolMs: Double?,
        val lagMs: Long?,
        val firstSequence: Long?,
        val lastSequence: Long?,
        val matchedFrames: Int,
    )

    fun correlate(
        frames: List<NativeAnchorTelemetryWindow.Frame>,
        nativePetrolMs: Double?,
        nativeMapBar: Double?,
        observedAtElapsedMs: Long,
        policy: LearningTolerancePolicy,
    ): Result {
        if (nativePetrolMs == null || nativeMapBar == null || !nativePetrolMs.isFinite() || !nativeMapBar.isFinite()) {
            return unreliable("NO_NATIVE_CONTEXT")
        }
        val active = policy.normalized()
        val petrolTolerance = max(active.petrolCenterMinimumMs, abs(nativePetrolMs) * active.petrolCenterPercent / 100.0)
        val mapTolerance = active.mapCenterBar
        val compatible = frames.filter { frame ->
            frame.fuel == "CNG" &&
                abs(frame.petrolMs - nativePetrolMs) <= petrolTolerance &&
                abs(frame.mapBar - nativeMapBar) <= mapTolerance
        }
        if (compatible.size < 2) return unreliable("NO_RELIABLE_CORRELATION", compatible.size)

        val rpms = compatible.map { it.rpm }.sorted()
        val medianRpm = medianInt(rpms)
        val rpmSpan = (rpms.last() - rpms.first()).toDouble()
        val rpmTolerance = max(active.rpmOscillationMinimum, medianRpm * active.rpmOscillationPercent / 100.0)
        if (rpmSpan > rpmTolerance) return unreliable("NO_RELIABLE_CORRELATION", compatible.size)

        val signalScore = compatible.map { frame ->
            val petrolScore = 1.0 - (abs(frame.petrolMs - nativePetrolMs) / petrolTolerance).coerceIn(0.0, 1.0)
            val mapScore = 1.0 - (abs(frame.mapBar - nativeMapBar) / mapTolerance).coerceIn(0.0, 1.0)
            (petrolScore + mapScore) / 2.0
        }.average().coerceIn(0.0, 1.0)
        val support = (compatible.size.toDouble() / active.requiredFrames.toDouble()).coerceIn(0.0, 1.0)
        val rpmConfidence = (1.0 - (rpmSpan / rpmTolerance).coerceIn(0.0, 1.0)) * support
        val confidence = (signalScore * 0.70 + rpmConfidence * 0.30).coerceIn(0.0, 1.0)
        val medianElapsed = medianLong(compatible.map { it.elapsedMs }.sorted())

        return Result(
            state = "CORRELATED",
            confidence = confidence,
            rpmConfidence = rpmConfidence.coerceIn(0.0, 1.0),
            rpm = medianRpm,
            mapBar = compatible.map { it.mapBar }.average(),
            petrolMs = compatible.map { it.petrolMs }.average(),
            lagMs = (observedAtElapsedMs - medianElapsed).coerceAtLeast(0L),
            firstSequence = compatible.first().sequence,
            lastSequence = compatible.last().sequence,
            matchedFrames = compatible.size,
        )
    }

    private fun unreliable(state: String, matchedFrames: Int = 0) = Result(
        state = state,
        confidence = 0.0,
        rpmConfidence = 0.0,
        rpm = null,
        mapBar = null,
        petrolMs = null,
        lagMs = null,
        firstSequence = null,
        lastSequence = null,
        matchedFrames = matchedFrames,
    )

    private fun medianInt(values: List<Int>): Int {
        val middle = values.size / 2
        return if (values.size % 2 == 0) (values[middle - 1] + values[middle]) / 2 else values[middle]
    }

    private fun medianLong(values: List<Long>): Long {
        val middle = values.size / 2
        return if (values.size % 2 == 0) (values[middle - 1] + values[middle]) / 2L else values[middle]
    }
}
