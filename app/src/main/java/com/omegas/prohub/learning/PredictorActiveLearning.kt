package com.omegas.prohub.learning

data class PredictorLearningRegion(
    val regionId: String,
    val naturallyEligible: Boolean,
    val usage: Double,
    val geometricNovelty: Double,
    val modelUncertainty: Double,
    val referenceQuality: Double,
    val calibrationFreshness: Double,
    val independence: Double,
    val expectedErrorImpact: Double,
    val acquisitionCost: Double,
) {
    init {
        require(regionId.isNotBlank())
        requireUnitInterval("usage", usage)
        requireUnitInterval("geometricNovelty", geometricNovelty)
        requireUnitInterval("modelUncertainty", modelUncertainty)
        requireUnitInterval("referenceQuality", referenceQuality)
        requireUnitInterval("calibrationFreshness", calibrationFreshness)
        requireUnitInterval("independence", independence)
        requireUnitInterval("expectedErrorImpact", expectedErrorImpact)
        require(acquisitionCost.isFinite() && acquisitionCost > 0.0) {
            "acquisitionCost must be finite and > 0"
        }
    }

    private fun requireUnitInterval(name: String, value: Double) {
        require(value.isFinite() && value in 0.0..1.0) { "$name must be finite in [0,1]" }
    }
}

data class PredictorLearningDiagnostic(
    val regionId: String,
    val diagnosticCode: String,
    val score: Double,
)

/**
 * Pure diagnostic active-learning ranker.
 *
 * It only ranks regions that are already naturally eligible. It has no route,
 * driver-instruction, scheduler, Store, serial or writer authority. Novelty and
 * independence are explicit inputs so repeated dwell/duplicate anchors do not
 * manufacture value-of-information through visit count alone.
 */
object PredictorActiveLearning {
    private const val NEXT_USEFUL_REGION = "NEXT_USEFUL_REGION"

    fun rank(regions: List<PredictorLearningRegion>): List<PredictorLearningDiagnostic> =
        regions.asSequence()
            .filter(PredictorLearningRegion::naturallyEligible)
            .mapNotNull { region ->
                val score = score(region)
                if (!score.isFinite()) null else PredictorLearningDiagnostic(
                    regionId = region.regionId,
                    diagnosticCode = NEXT_USEFUL_REGION,
                    score = score,
                )
            }
            .sortedWith(
                compareByDescending<PredictorLearningDiagnostic> { it.score }
                    .thenBy { it.regionId },
            )
            .toList()

    fun score(region: PredictorLearningRegion): Double =
        region.usage *
            region.geometricNovelty *
            region.modelUncertainty *
            region.referenceQuality *
            region.calibrationFreshness *
            region.independence *
            region.expectedErrorImpact /
            region.acquisitionCost
}
