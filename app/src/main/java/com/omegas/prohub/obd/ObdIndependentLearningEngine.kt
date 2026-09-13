package com.omegas.prohub.obd

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Independent GNV/STFT learning authority. No MP48, gasoline or writer input. */
class ObdIndependentLearningEngine(
    private val policy: ObdLearningPolicy = ObdLearningPolicy(),
) {
    private val samples = ArrayDeque<ObdLearningSample>()
    private val seenCycleIds = linkedSetOf<String>()
    private var activeEpoch = "obd-0"
    private var epochStartedAtMs = 0L
    private var activeSessionId = "legacy"
    private var activeSessionStartedAtMs = 0L

    @Synchronized
    fun observe(sample: ObdLearningSample, nowMs: Long = sample.observedAtMs): Boolean {
        if (!sample.gnvModeDeclared || sample.epoch != activeEpoch || !valid(sample)) return false
        if (nowMs < sample.observedAtMs || nowMs - sample.observedAtMs > policy.maximumSampleAgeMs) return false
        if (sample.cycleStartedAtMs < epochStartedAtMs) return false
        when {
            sample.sessionStartedAtMs < activeSessionStartedAtMs -> return false
            sample.sessionStartedAtMs > activeSessionStartedAtMs -> {
                activeSessionStartedAtMs = sample.sessionStartedAtMs
                activeSessionId = sample.sessionId
                samples.clear()
                seenCycleIds.clear()
            }
            sample.sessionId != activeSessionId -> return false
        }
        if (!seenCycleIds.add(sample.cycleId)) return false
        samples.addLast(sample)
        while (samples.size > policy.historyLimit) {
            val removed = samples.removeFirst()
            seenCycleIds.remove(removed.cycleId)
        }
        return true
    }

    @Synchronized
    fun beginEpoch(epoch: String, startedAtMs: Long = 0L) {
        require(epoch.isNotBlank())
        require(startedAtMs >= 0L)
        if (epoch == activeEpoch && startedAtMs <= epochStartedAtMs) return
        activeEpoch = epoch
        epochStartedAtMs = startedAtMs
        samples.clear()
        seenCycleIds.clear()
    }

    @Synchronized
    fun currentEpoch(): String = activeEpoch

    @Synchronized
    fun epochStartedAtMs(): Long = epochStartedAtMs

    @Synchronized
    fun evaluate(rpm: Double, mapBar: Double, epoch: String = activeEpoch): ObdLearningResult {
        if (!rpm.isFinite() || !mapBar.isFinite()) return emptyResult()
        if (epoch != activeEpoch) return emptyResult(ObdLearningState.INSUFFICIENT)
        val region = samples.filter {
            it.epoch == epoch && it.sessionId == activeSessionId &&
                abs(it.rpm - rpm) <= max(policy.minimumRpmWindow, rpm * policy.relativeRpmWindow) &&
                abs(it.mapBar - mapBar) <= policy.mapWindowBar
        }
        if (region.isEmpty()) return emptyResult()
        val median = median(region.map(ObdLearningSample::stftPct))
        val quality = quality(region, median)
        val state = if (region.size >= policy.minimumSamples && quality >= policy.minimumQuality) {
            ObdLearningState.READY
        } else {
            ObdLearningState.INSUFFICIENT
        }
        return ObdLearningResult(
            state = state,
            stftMedianPct = median,
            correctionMultiplier = if (state == ObdLearningState.READY && abs(median) > policy.deadbandPct) {
                (1.0 + median / 100.0).coerceIn(
                    policy.minimumCorrectionMultiplier,
                    policy.maximumCorrectionMultiplier,
                )
            } else null,
            quality = quality,
            sampleCount = region.size,
            epoch = activeEpoch,
        )
    }

    fun snapshotJson(): JSONObject = snapshotState().toJson()

    @Synchronized
    fun snapshotState(): ObdLearningSnapshot = ObdLearningSnapshot(
        epoch = activeEpoch,
        epochStartedAtMs = epochStartedAtMs,
        sessionId = activeSessionId,
        sessionStartedAtMs = activeSessionStartedAtMs,
        samples = samples.toList(),
    )

    @Synchronized
    fun restoreJson(snapshot: JSONObject) {
        if (snapshot.optString("schema") != "omegas-obd-stft-learning-v1") return
        val epoch = snapshot.optString("epoch", "obd-0").takeIf(String::isNotBlank) ?: "obd-0"
        activeEpoch = epoch
        epochStartedAtMs = snapshot.optLong("epochStartedAtMs", 0L).coerceAtLeast(0L)
        activeSessionId = snapshot.optString("sessionId", "legacy").ifBlank { "legacy" }
        activeSessionStartedAtMs = snapshot.optLong("sessionStartedAtMs", 0L).coerceAtLeast(0L)
        samples.clear()
        seenCycleIds.clear()
        val array = snapshot.optJSONArray("samples") ?: return
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val sample = ObdLearningSample(
                observedAtMs = item.optLong("observedAtMs", -1L),
                rpm = item.optDouble("rpm", Double.NaN),
                mapBar = item.optDouble("mapBar", Double.NaN),
                stftPct = item.optDouble("stftPct", Double.NaN),
                acquisitionSpanMs = item.optLong("acquisitionSpanMs", -1L),
                epoch = item.optString("epoch", ""),
                gnvModeDeclared = item.optBoolean("gnvModeDeclared", false),
                sessionId = item.optString("sessionId", activeSessionId).ifBlank { activeSessionId },
                sessionStartedAtMs = item.optLong("sessionStartedAtMs", activeSessionStartedAtMs).coerceAtLeast(0L),
                cycleStartedAtMs = item.optLong(
                    "cycleStartedAtMs",
                    item.optLong("observedAtMs", -1L) - item.optLong("acquisitionSpanMs", 0L),
                ),
                cycleId = item.optString("cycleId").ifBlank {
                    ObdLearningSample.cycleIdentity(
                        item.optLong("observedAtMs", -1L),
                        item.optDouble("rpm", Double.NaN),
                        item.optDouble("mapBar", Double.NaN),
                        item.optDouble("stftPct", Double.NaN),
                    )
                },
            )
            if (sample.epoch == activeEpoch && sample.gnvModeDeclared && valid(sample) &&
                sample.sessionId == activeSessionId && sample.sessionStartedAtMs == activeSessionStartedAtMs &&
                seenCycleIds.add(sample.cycleId)
            ) {
                samples.addLast(sample)
                while (samples.size > policy.historyLimit) {
                    val removed = samples.removeFirst()
                    seenCycleIds.remove(removed.cycleId)
                }
            }
        }
    }

    private fun valid(sample: ObdLearningSample): Boolean =
        sample.observedAtMs >= 0L && sample.cycleStartedAtMs >= 0L &&
            sample.sessionId.isNotBlank() && sample.cycleId.isNotBlank() &&
            sample.sessionStartedAtMs >= 0L &&
            sample.rpm.isFinite() && sample.rpm in 400.0..8_000.0 &&
            sample.mapBar.isFinite() && sample.mapBar in 0.10..1.60 &&
            sample.stftPct.isFinite() && abs(sample.stftPct) <= 50.0 &&
            sample.acquisitionSpanMs in 0..ObdPidCycle.MAX_ACQUISITION_SPAN_MS

    private fun quality(region: List<ObdLearningSample>, center: Double): Double {
        val mad = median(region.map { abs(it.stftPct - center) })
        val dispersion = 1.0 / (1.0 + mad / policy.dispersionScalePct)
        val timing = region.map {
            (1.0 - it.acquisitionSpanMs.toDouble() / (ObdPidCycle.MAX_ACQUISITION_SPAN_MS * 2.0))
                .coerceIn(0.5, 1.0)
        }.average()
        val support = sqrt((region.size.toDouble() / policy.minimumSamples).coerceIn(0.0, 1.0))
        return (dispersion * timing * support).coerceIn(0.0, 1.0)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun emptyResult(state: ObdLearningState = ObdLearningState.UNAVAILABLE) = ObdLearningResult(
        state = state,
        stftMedianPct = null,
        correctionMultiplier = null,
        quality = 0.0,
        sampleCount = 0,
        epoch = activeEpoch,
    )
}

data class ObdLearningSample(
    val observedAtMs: Long,
    val rpm: Double,
    val mapBar: Double,
    val stftPct: Double,
    val acquisitionSpanMs: Long,
    val epoch: String,
    val gnvModeDeclared: Boolean,
    val sessionId: String = "legacy",
    val sessionStartedAtMs: Long = 0L,
    val cycleStartedAtMs: Long = (observedAtMs - acquisitionSpanMs).coerceAtLeast(0L),
    val cycleId: String = cycleIdentity(observedAtMs, rpm, mapBar, stftPct),
) {
    companion object {
        fun cycleIdentity(observedAtMs: Long, rpm: Double, mapBar: Double, stftPct: Double): String =
            "$observedAtMs|${"%.3f".format(java.util.Locale.US, rpm)}|${"%.5f".format(java.util.Locale.US, mapBar)}|${"%.4f".format(java.util.Locale.US, stftPct)}"
    }
}

data class ObdLearningSnapshot(
    val epoch: String,
    val epochStartedAtMs: Long,
    val sessionId: String,
    val sessionStartedAtMs: Long,
    val samples: List<ObdLearningSample>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", "omegas-obd-stft-learning-v1")
        .put("epoch", epoch)
        .put("epochStartedAtMs", epochStartedAtMs)
        .put("sessionId", sessionId)
        .put("sessionStartedAtMs", sessionStartedAtMs)
        .put("samples", JSONArray().also { array ->
            samples.forEach { sample ->
                array.put(JSONObject()
                    .put("observedAtMs", sample.observedAtMs)
                    .put("rpm", sample.rpm)
                    .put("mapBar", sample.mapBar)
                    .put("stftPct", sample.stftPct)
                    .put("acquisitionSpanMs", sample.acquisitionSpanMs)
                    .put("epoch", sample.epoch)
                    .put("gnvModeDeclared", sample.gnvModeDeclared)
                    .put("sessionId", sample.sessionId)
                    .put("sessionStartedAtMs", sample.sessionStartedAtMs)
                    .put("cycleStartedAtMs", sample.cycleStartedAtMs)
                    .put("cycleId", sample.cycleId))
            }
        })
}

data class ObdLearningPolicy(
    val minimumSamples: Int = 5,
    val historyLimit: Int = 2_048,
    val minimumRpmWindow: Double = 125.0,
    val relativeRpmWindow: Double = 0.05,
    val mapWindowBar: Double = 0.05,
    val minimumQuality: Double = 0.55,
    val dispersionScalePct: Double = 4.0,
    val deadbandPct: Double = 1.0,
    val minimumCorrectionMultiplier: Double = 0.80,
    val maximumCorrectionMultiplier: Double = 1.20,
    val maximumSampleAgeMs: Long = 2_000L,
) {
    init {
        require(minimumSamples > 0)
        require(historyLimit >= minimumSamples)
        require(minimumRpmWindow > 0.0 && relativeRpmWindow > 0.0 && mapWindowBar > 0.0)
        require(minimumQuality in 0.0..1.0 && dispersionScalePct > 0.0 && deadbandPct >= 0.0)
        require(minimumCorrectionMultiplier > 0.0)
        require(maximumCorrectionMultiplier >= minimumCorrectionMultiplier)
        require(maximumSampleAgeMs > 0L)
    }
}

enum class ObdLearningState { READY, INSUFFICIENT, UNAVAILABLE }

data class ObdLearningResult(
    val state: ObdLearningState,
    val stftMedianPct: Double?,
    val correctionMultiplier: Double?,
    val quality: Double,
    val sampleCount: Int,
    val epoch: String,
)
