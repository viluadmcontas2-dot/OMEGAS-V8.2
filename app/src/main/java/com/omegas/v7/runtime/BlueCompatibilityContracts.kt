package com.omegas.v7.runtime

/**
 * Passive compatibility contracts retained while RED runtime callers are moved
 * onto the Blue causal engine. These types contain no prediction or correction
 * authority.
 */
data class EquivalencePolicyV7(
    val rpmMinimumWindow: Double = 120.0,
    val rpmPercentWindow: Double = 6.0,
    val mapWindowBar: Double = 0.08,
    val waterWindowC: Double = 8.0,
    val maximumNormalizedDistance: Double = 1.75,
    val maximumNeighbors: Int = 4,
    val deadbandMs: Double = 0.08,
    val deadbandPercent: Double = 2.0,
) {
    init {
        require(rpmMinimumWindow > 0.0)
        require(rpmPercentWindow > 0.0)
        require(mapWindowBar > 0.0)
        require(waterWindowC > 0.0)
        require(maximumNormalizedDistance > 0.0)
        require(maximumNeighbors > 0)
    }
}

data class FuelComparisonV7(
    val id: String,
    val revision: CalibrationRevisionV7,
    val cngVisitId: String,
    val petrolEvidenceIds: List<String>,
    val rpm: Double,
    val mapBar: Double,
    val waterC: Double,
    val petrolTargetMs: Double,
    val petrolOnCngMs: Double,
    val differenceMs: Double,
    val errorPercent: Double,
    val direction: String,
    val quality: Double,
    val createdAtMs: Long,
) {
    init {
        require(id.isNotBlank())
        require(cngVisitId.isNotBlank())
        require(petrolEvidenceIds.isNotEmpty())
        require(quality in 0.0..1.0)
    }
}
