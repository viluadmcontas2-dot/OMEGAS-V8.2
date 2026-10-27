package com.omegas.prohub.session

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.learning.ContinuousLearningMath

/**
 * Projeção compacta da estabilidade para consumidores/UI.
 * Evita varrer 144 células sem evidência em cada leitura de estado.
 */
object LearningStabilityProjection {
    fun mapWithEvidence(comparisons: List<CalibrationFuelComparison>): Map<String, LearningStabilitySnapshot> {
        val keys = linkedSetOf<Pair<Int, Int>>()
        comparisons.forEach { comparison ->
            ContinuousLearningMath.bilinearWeights(comparison.rpm, comparison.petrolOnCngMs)
                .filter { it.weight > 0.0 }
                .forEach { keys += it.row to it.column }
        }
        return keys
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .associate { (row, column) ->
                "$row:$column" to LearningStability.mapCell(comparisons, row, column)
            }
    }

    fun curveWithEvidence(comparisons: List<CalibrationFuelComparison>): Map<Int, LearningStabilitySnapshot> {
        val indexes = linkedSetOf<Int>()
        comparisons.forEach { comparison ->
            val (lower, upper, _) = KFactorProtocol.blendAxis(comparison.petrolTargetMs)
            indexes += lower
            indexes += upper
        }
        return indexes.sorted().associateWith { index ->
            LearningStability.curvePoint(comparisons, index)
        }
    }
}
