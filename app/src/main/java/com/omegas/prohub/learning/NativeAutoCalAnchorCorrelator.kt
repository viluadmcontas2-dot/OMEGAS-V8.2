package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.max

/** Correlaciona uma maturidade nativa com telemetria física já capturada pela engine. */
object NativeAutoCalAnchorCorrelator {
    data class Result(
        val state: String,
        val reason: String,
        val confidence: Double,
        val rpmConfidence: Double,
        val rpm: Int?,
        val mapBar: Double?,
        val petrolMs: Double?,
        val gasMsDiagnostic: Double?,
        val fuel: String?,
        val correlatedFrameElapsedMs: Long?,
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
        sessionId: Long? = null,
    ): Result {
        if (nativePetrolMs == null || nativeMapBar == null || !nativePetrolMs.isFinite() || !nativeMapBar.isFinite()) {
            return unreliable("NO_NATIVE_CONTEXT")
        }
        val active = policy.normalized()
        val sameSession = if (sessionId == null) frames else frames.filter { it.sessionId == sessionId }
        if (frames.isNotEmpty() && sameSession.isEmpty()) return unreliable("SESSION_MISMATCH")

        val plausible = sameSession.filter { it.plausible }
        if (sameSession.isNotEmpty() && plausible.isEmpty()) return unreliable("IMPLAUSIBLE_TELEMETRY")

        val cng = plausible.filter { it.fuel in setOf("GNV", "CNG") }
        if (plausible.isNotEmpty() && cng.isEmpty()) return unreliable("FUEL_MISMATCH")
        if (cng.isEmpty()) return unreliable("NO_PHYSICAL_CANDIDATE")

        val orderedPhysical = cng.sortedBy { it.elapsedMs }
        if (orderedPhysical.zipWithNext().any { (a, b) -> b.elapsedMs - a.elapsedMs > active.breakingGapMs }) {
            return unreliable("CONTINUITY_GAP")
        }

        val petrolTolerance = max(
            active.petrolCenterMinimumMs,
            abs(nativePetrolMs) * active.petrolCenterPercent / 100.0,
        )
        val mapTolerance = active.mapCenterBar
        val compatible = orderedPhysical.filter { frame ->
            abs(frame.petrolMs - nativePetrolMs) <= petrolTolerance &&
                abs(frame.mapBar - nativeMapBar) <= mapTolerance
        }
        if (compatible.isEmpty()) return unreliable("OUTSIDE_NATIVE_TOLERANCE")

        val fresh = compatible.filter { frame ->
            val lag = observedAtElapsedMs - frame.elapsedMs
            lag in 0..active.breakingGapMs
        }
        if (fresh.isEmpty()) return unreliable("STALE_CORRELATION")
        if (fresh.size < 2) return unreliable("INSUFFICIENT_STABLE_SUPPORT", fresh.size)

        val rpms = fresh.map { it.rpm }.sorted()
        val medianRpm = medianInt(rpms)
        val rpmSpan = (rpms.last() - rpms.first()).toDouble()
        val rpmTolerance = max(
            active.rpmOscillationMinimum,
            medianRpm * active.rpmOscillationPercent / 100.0,
        )
        // Na borda exata a fórmula de confiança abaixo seria zero. Zero confiança
        // não pode produzir uma posição física CORRELATED/NativeLearningAnchor.
        if (rpmSpan >= rpmTolerance) return unreliable("RPM_AMBIGUITY", fresh.size)

        val petrolValues = fresh.map { it.petrolMs }
        val petrolCenter = petrolValues.average()
        val petrolSpan = petrolValues.maxOrNull()!! - petrolValues.minOrNull()!!
        val petrolOscillationLimit = max(
            active.petrolCenterMinimumMs,
            abs(petrolCenter) * active.petrolOscillationPercent / 100.0,
        )
        if (petrolSpan > petrolOscillationLimit) return unreliable("PETROL_UNSTABLE", fresh.size)

        val mapValues = fresh.map { it.mapBar }
        val mapSpan = mapValues.maxOrNull()!! - mapValues.minOrNull()!!
        if (mapSpan > active.mapOscillationBar) return unreliable("MAP_UNSTABLE", fresh.size)

        val rpmStability = 1.0 - (rpmSpan / rpmTolerance).coerceIn(0.0, 1.0)
        val petrolStability = 1.0 - (petrolSpan / petrolOscillationLimit).coerceIn(0.0, 1.0)
        val mapStability = 1.0 - (mapSpan / active.mapOscillationBar).coerceIn(0.0, 1.0)
        val localStability = ((rpmStability + petrolStability + mapStability) / 3.0).coerceIn(0.0, 1.0)

        data class Candidate(
            val frame: NativeAnchorTelemetryWindow.Frame,
            val petrolFit: Double,
            val mapFit: Double,
            val temporalFit: Double,
        ) {
            val score: Double get() = (petrolFit + mapFit + temporalFit) / 3.0
        }

        val candidates = fresh.map { frame ->
            val lag = (observedAtElapsedMs - frame.elapsedMs).coerceAtLeast(0L)
            Candidate(
                frame = frame,
                petrolFit = 1.0 - (abs(frame.petrolMs - nativePetrolMs) / petrolTolerance).coerceIn(0.0, 1.0),
                mapFit = 1.0 - (abs(frame.mapBar - nativeMapBar) / mapTolerance).coerceIn(0.0, 1.0),
                temporalFit = 1.0 - (lag.toDouble() / active.breakingGapMs.toDouble()).coerceIn(0.0, 1.0),
            )
        }
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.score }.thenBy { it.frame.elapsedMs },
        ) ?: return unreliable("NO_PHYSICAL_CANDIDATE")

        val petrolCompatibility = candidates.map { it.petrolFit }.average().coerceIn(0.0, 1.0)
        val mapCompatibility = candidates.map { it.mapFit }.average().coerceIn(0.0, 1.0)
        val temporalQuality = candidates.map { it.temporalFit }.average().coerceIn(0.0, 1.0)
        // Quantidade de frames não participa da confiança. Ela somente permitiu
        // provar acima que existe um único agrupamento físico local e estável.
        val confidence = (
            petrolCompatibility + mapCompatibility + localStability + temporalQuality
        ).div(4.0).coerceIn(0.0, 1.0)

        val support = fresh.sortedBy { it.elapsedMs }
        return Result(
            state = "CORRELATED",
            reason = "STABLE_SINGLE_PHYSICAL_CLUSTER",
            confidence = confidence,
            rpmConfidence = rpmStability.coerceIn(0.0, 1.0),
            rpm = best.frame.rpm,
            mapBar = best.frame.mapBar,
            petrolMs = best.frame.petrolMs,
            gasMsDiagnostic = best.frame.gasMsDiagnostic?.takeIf(Double::isFinite),
            fuel = "GNV",
            correlatedFrameElapsedMs = best.frame.elapsedMs,
            lagMs = (observedAtElapsedMs - best.frame.elapsedMs).coerceAtLeast(0L),
            firstSequence = support.first().sequence,
            lastSequence = support.last().sequence,
            matchedFrames = support.size,
        )
    }

    private fun unreliable(reason: String, matchedFrames: Int = 0) = Result(
        state = "NO_RELIABLE_CORRELATION",
        reason = reason,
        confidence = 0.0,
        rpmConfidence = 0.0,
        rpm = null,
        mapBar = null,
        petrolMs = null,
        gasMsDiagnostic = null,
        fuel = null,
        correlatedFrameElapsedMs = null,
        lagMs = null,
        firstSequence = null,
        lastSequence = null,
        matchedFrames = matchedFrames,
    )

    private fun medianInt(values: List<Int>): Int {
        val middle = values.size / 2
        return if (values.size % 2 == 0) (values[middle - 1] + values[middle]) / 2 else values[middle]
    }
}
