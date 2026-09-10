package com.omegas.prohub.obd

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Independent GNV/STFT learning authority.
 *
 * It deliberately has no MP48, Petrol Inj., gasoline or ECU writer input.
 */
class ObdIndependentLearningEngine(
    private val policy: ObdLearningPolicy = ObdLearningPolicy(),
) {
    private val samples = ArrayDeque<ObdLearningSample>()
    private var activeEpoch = "obd-0"

    @Synchronized
    fun observe(sample: ObdLearningSample): Boolean {
        if (!sample.gnvModeDeclared || sample.epoch != activeEpoch || !valid(sample)) return false
        samples.addLast(sample)
        while (samples.size > policy.historyLimit) samples.removeFirst()
        return true
    }

    @Synchronized
    fun beginEpoch(epoch: String) {
        require(epoch.isNotBlank())
        if (epoch == activeEpoch) return
        activeEpoch = epoch
        samples.clear()
    }

    @Synchronized
    fun currentEpoch(): String = activeEpoch

    @Synchronized
    fun evaluate(rpm: Double, mapBar: Double, epoch: String = activeEpoch): ObdLearningResult {
        if (!rpm.isFinite() || !mapBar.isFinite()) return emptyResult()
        if (epoch != activeEpoch) return emptyResult(ObdLearningState.INSUFFICIENT)
        val region = samples.filter {
            it.epoch == epoch &&
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

    @Synchronized
    fun snapshotJson(): JSONObject = JSONObject()
        .put("schema", "omegas-obd-stft-learning-v1")
        .put("epoch", activeEpoch)
        .put("samples", JSONArray().also { array ->
            samples.forEach { sample ->
                array.put(JSONObject()
                    .put("observedAtMs", sample.observedAtMs)
                    .put("rpm", sample.rpm)
                    .put("mapBar", sample.mapBar)
                    .put("stftPct", sample.stftPct)
                    .put("acquisitionSpanMs", sample.acquisitionSpanMs)
                    .put("epoch", sample.epoch)
                    .put("gnvModeDeclared", sample.gnvModeDeclared))
            }
        })

    @Synchronized
    fun restoreJson(snapshot: JSONObject) {
        if (snapshot.optString("schema") != "omegas-obd-stft-learning-v1") return
        val epoch = snapshot.optString("epoch", "obd-0").takeIf(String::isNotBlank) ?: "obd-0"
        activeEpoch = epoch
        samples.clear()
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
            )
            if (sample.epoch == activeEpoch && sample.gnvModeDeclared && valid(sample)) {
                samples.addLast(sample)
                while (samples.size > policy.historyLimit) samples.removeFirst()
            }
        }
    }

    private fun valid(sample: ObdLearningSample): Boolean =
        sample.observedAtMs >= 0L &&
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
)

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
) {
    init {
        require(minimumSamples > 0)
        require(historyLimit >= minimumSamples)
        require(minimumRpmWindow > 0.0 && relativeRpmWindow > 0.0 && mapWindowBar > 0.0)
        require(minimumQuality in 0.0..1.0 && dispersionScalePct > 0.0 && deadbandPct >= 0.0)
        require(minimumCorrectionMultiplier > 0.0)
        require(maximumCorrectionMultiplier >= minimumCorrectionMultiplier)
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
