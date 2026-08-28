package com.omegas.prohub.learning

/** Range carried by an IdealTargetCandidate without inventing a confidence level. */
data class PredictorTargetRange(
    val lowerK: Double,
    val upperK: Double,
    val basis: String,
) {
    init {
        require(lowerK.isFinite() && lowerK >= 0.0)
        require(upperK.isFinite() && upperK >= lowerK)
        require(basis.isNotBlank())
    }
}

/** Version identity for the scientific model and its confidence calibration. */
data class PredictorModelDescriptor(
    val modelFamily: String,
    val modelVersion: String,
    val confidenceCalibrationVersion: String,
) {
    init {
        require(modelFamily.isNotBlank())
        require(modelVersion.isNotBlank())
        require(confidenceCalibrationVersion.isNotBlank())
    }

    companion object {
        fun directKStarDefault(): PredictorModelDescriptor = PredictorModelDescriptor(
            modelFamily = "DIRECT_KSTAR_CONTRACT",
            modelVersion = "step155-v1",
            confidenceCalibrationVersion = "UNVERIFIED",
        )
    }
}

/** Empirical calibration state computed only from real post-write outcomes. */
data class PredictorPredictionErrorStats(
    val sampleCount: Int,
    val intervalHitCount: Int,
    val intervalMissCount: Int,
    val meanAbsoluteLogError: Double,
    val calibrationError: Double,
) {
    init {
        require(sampleCount >= 0)
        require(intervalHitCount >= 0)
        require(intervalMissCount >= 0)
        require(intervalHitCount + intervalMissCount == sampleCount)
        require(meanAbsoluteLogError.isFinite() && meanAbsoluteLogError >= 0.0)
        require(calibrationError.isFinite() && calibrationError >= 0.0)
    }

    val intervalCoverage: Double
        get() = if (sampleCount == 0) 0.0 else intervalHitCount.toDouble() / sampleCount.toDouble()

    companion object {
        fun empty(): PredictorPredictionErrorStats = PredictorPredictionErrorStats(
            sampleCount = 0,
            intervalHitCount = 0,
            intervalMissCount = 0,
            meanAbsoluteLogError = 0.0,
            calibrationError = 0.0,
        )
    }
}
