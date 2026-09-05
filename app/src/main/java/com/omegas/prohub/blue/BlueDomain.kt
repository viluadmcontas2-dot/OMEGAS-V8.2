package com.omegas.prohub.blue

import com.omegas.prohub.calibration.CalibrationShape

enum class FuelKind { PETROL, CNG }

data class CalibrationRevision(
    val curveK: Long,
    val mapK: Long,
) {
    init {
        require(curveK >= 0)
        require(mapK >= 0)
    }
}

data class CalibrationState(
    val revision: CalibrationRevision,
    val curveK: List<Double>,
    val mapK: List<List<Int>>,
) {
    init {
        CalibrationShape.requireCurve(curveK)
        CalibrationShape.requireMap(mapK)
    }
}

data class FuelEvidence(
    val id: String,
    val fuel: FuelKind,
    val collectedAtMs: Long,
    val visitId: String,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val quality: Double,
    val cngRevision: CalibrationRevision?,
    val waterC: Double = UNKNOWN_TEMPERATURE_C,
    val gasC: Double = UNKNOWN_TEMPERATURE_C,
    val pressureDiffBar: Double = 0.0,
) {
    companion object {
        const val UNKNOWN_TEMPERATURE_C = -273.15
    }

    init {
        require(id.isNotBlank())
        require(visitId.isNotBlank())
        require(collectedAtMs >= 0)
        require(rpm.isFinite() && rpm >= 0.0)
        require(mapBar.isFinite() && mapBar >= 0.0)
        require(petrolMs.isFinite() && petrolMs >= 0.0)
        require(quality.isFinite() && quality in 0.0..1.0)
        require(waterC.isFinite())
        require(gasC.isFinite())
        require(pressureDiffBar.isFinite())
        require((fuel == FuelKind.PETROL && cngRevision == null) ||
            (fuel == FuelKind.CNG && cngRevision != null))
    }
}

data class FuelComparison(
    val id: String,
    val revision: CalibrationRevision,
    val petrolVisitId: String,
    val cngVisitId: String,
    val rpm: Double,
    val mapBar: Double,
    val petrolTargetMs: Double,
    val petrolOnCngMs: Double,
    val errorPercent: Double,
    val quality: Double,
    val createdAtMs: Long,
)

data class BlueLearningState(
    val sessionId: String,
    val calibration: CalibrationState,
    val petrolEvidence: List<FuelEvidence> = emptyList(),
    val cngEvidenceByRevision: Map<CalibrationRevision, List<FuelEvidence>> = emptyMap(),
    val comparisons: List<FuelComparison> = emptyList(),
) {
    init { require(sessionId.isNotBlank()) }

    fun activeCngEvidence(): List<FuelEvidence> = cngEvidenceByRevision[calibration.revision].orEmpty()
    fun activeComparisons(): List<FuelComparison> = comparisons.filter { it.revision == calibration.revision }
}
