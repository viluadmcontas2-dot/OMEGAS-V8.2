package com.omegas.prohub.obd

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure, bounded STFT physical-evidence store.
 *
 * This class has no writer dependencies and no K target authority. It only keeps
 * timestamp-paired OBD/MP48 observations and builds gasoline-relative physical
 * evidence in RPM/MAP/Petrol-Inj. compatible regions.
 */
class ObdWitnessEngine(
    private val policy: ObdWitnessPolicy = ObdWitnessPolicy(),
) {
    private val observations = ArrayDeque<ObdWitnessSample>()

    fun observe(sample: ObdWitnessSample) {
        require(sample.observedAtMs >= 0L)
        require(sample.stftPct.isFinite())
        require(sample.rpm.isFinite() && sample.rpm > 0.0)
        require(sample.mapBar.isFinite() && sample.mapBar > 0.0)
        require(sample.petrolMs.isFinite() && sample.petrolMs > 0.0)
        require(sample.calibrationState.isNotBlank())
        require(sample.skewMs >= 0L)
        require(ObdFuelState.normalize(sample.fuel) != null) { "Unsupported MP48 fuel: ${sample.fuel}" }

        observations.addLast(sample)
        while (observations.size > policy.historyLimit) observations.removeFirst()
    }

    fun evaluate(
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
        calibrationState: String,
    ): ObdWitnessResult {
        require(rpm.isFinite() && rpm > 0.0)
        require(mapBar.isFinite() && mapBar > 0.0)
        require(petrolMs.isFinite() && petrolMs > 0.0)
        require(calibrationState.isNotBlank())

        val compatible = observations.filter { isOperatingMatch(it, rpm, mapBar, petrolMs) }
        val gasoline = compatible.filter { ObdFuelState.normalize(it.fuel) == ObdScientificFuel.PETROL }
        val gnv = compatible.filter {
            ObdFuelState.normalize(it.fuel) == ObdScientificFuel.CNG && it.calibrationState == calibrationState
        }

        if (gasoline.size < policy.minimumSamples || gnv.size < policy.minimumSamples) {
            return ObdWitnessResult(
                state = if (observations.isEmpty()) ObdWitnessState.UNAVAILABLE else ObdWitnessState.INSUFFICIENT,
                gasolineReferencePct = gasoline.takeIf { it.isNotEmpty() }?.let(::medianStft),
                gnvStftPct = gnv.takeIf { it.isNotEmpty() }?.let(::medianStft),
                residualPp = null,
                correctionRatio = null,
                errorLog = null,
                correctionPercent = null,
                quality = 0.0,
                gasolineSamples = gasoline.size,
                gnvSamples = gnv.size,
            )
        }

        val gasolineMedian = medianStft(gasoline)
        val gnvMedian = medianStft(gnv)
        val gasolineFactor = 1.0 + gasolineMedian / 100.0
        val gnvFactor = 1.0 + gnvMedian / 100.0
        if (gasolineFactor <= 0.0 || gnvFactor <= 0.0) {
            return ObdWitnessResult(
                state = ObdWitnessState.INSUFFICIENT,
                gasolineReferencePct = gasolineMedian,
                gnvStftPct = gnvMedian,
                residualPp = null,
                correctionRatio = null,
                errorLog = null,
                correctionPercent = null,
                quality = 0.0,
                gasolineSamples = gasoline.size,
                gnvSamples = gnv.size,
            )
        }

        val correctionRatio = gnvFactor / gasolineFactor
        return ObdWitnessResult(
            state = ObdWitnessState.SUPPORTS,
            gasolineReferencePct = gasolineMedian,
            gnvStftPct = gnvMedian,
            residualPp = gnvMedian - gasolineMedian,
            correctionRatio = correctionRatio,
            errorLog = ln(correctionRatio),
            correctionPercent = (correctionRatio - 1.0) * 100.0,
            quality = quality(gasoline + gnv),
            gasolineSamples = gasoline.size,
            gnvSamples = gnv.size,
        )
    }

    private fun isOperatingMatch(
        sample: ObdWitnessSample,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
    ): Boolean {
        val rpmWindow = max(policy.minimumRpmWindow, rpm * policy.relativeRpmWindow)
        val petrolWindow = max(policy.minimumPetrolMsWindow, petrolMs * policy.relativePetrolMsWindow)
        return abs(sample.rpm - rpm) <= rpmWindow &&
            abs(sample.mapBar - mapBar) <= policy.mapWindowBar &&
            abs(sample.petrolMs - petrolMs) <= petrolWindow
    }

    private fun quality(samples: List<ObdWitnessSample>): Double {
        if (samples.isEmpty()) return 0.0
        val values = samples.map { it.stftPct }
        val median = median(values)
        val dispersion = median(values.map { abs(it - median) })
        val dispersionQuality = (1.0 / (1.0 + dispersion / policy.dispersionScalePp)).coerceIn(0.0, 1.0)
        val skewQuality = samples.map {
            (1.0 - it.skewMs.toDouble() / policy.maxQualitySkewMs.toDouble()).coerceIn(0.0, 1.0)
        }.average()
        val supportQuality = sqrt(
            (samples.size.toDouble() / policy.fullSupportSamples.toDouble()).coerceIn(0.0, 1.0),
        )
        return (skewQuality * dispersionQuality * supportQuality).coerceIn(0.0, 1.0)
    }

    private fun medianStft(samples: List<ObdWitnessSample>): Double = median(samples.map { it.stftPct })

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}

data class ObdWitnessPolicy(
    val minimumSamples: Int = 3,
    val historyLimit: Int = 512,
    val minimumRpmWindow: Double = 120.0,
    val relativeRpmWindow: Double = 0.06,
    val mapWindowBar: Double = 0.08,
    val minimumPetrolMsWindow: Double = 0.25,
    val relativePetrolMsWindow: Double = 0.08,
    val maxQualitySkewMs: Long = 250L,
    val fullSupportSamples: Int = 10,
    val dispersionScalePp: Double = 4.0,
) {
    init {
        require(minimumSamples > 0)
        require(historyLimit >= minimumSamples * 2)
        require(minimumRpmWindow > 0.0)
        require(relativeRpmWindow > 0.0)
        require(mapWindowBar > 0.0)
        require(minimumPetrolMsWindow > 0.0)
        require(relativePetrolMsWindow > 0.0)
        require(maxQualitySkewMs > 0L)
        require(fullSupportSamples > 0)
        require(dispersionScalePp > 0.0)
    }
}

data class ObdWitnessSample(
    val observedAtMs: Long,
    val stftPct: Double,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val fuel: String,
    val calibrationState: String,
    val skewMs: Long,
)

enum class ObdWitnessState { SUPPORTS, CONFLICTS, INSUFFICIENT, UNAVAILABLE }

data class ObdWitnessResult(
    val state: ObdWitnessState,
    val gasolineReferencePct: Double?,
    val gnvStftPct: Double?,
    val residualPp: Double?,
    val correctionRatio: Double?,
    val errorLog: Double?,
    val correctionPercent: Double?,
    val quality: Double,
    val gasolineSamples: Int,
    val gnvSamples: Int,
)
