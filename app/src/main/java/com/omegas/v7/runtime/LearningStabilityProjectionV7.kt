package com.omegas.v7.runtime

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.learning.ContinuousLearningMath

/**
 * Projeção compacta da estabilidade para consumidores/UI.
 * Evita varrer 144 células sem evidência em cada leitura de estado.
 */
object LearningStabilityProjectionV7 {
    fun mapWithEvidence(comparisons: List<FuelComparisonV7>): Map<String, LearningStabilitySnapshotV7> {
        val keys = linkedSetOf<Pair<Int, Int>>()
        comparisons.forEach { comparison ->
            ContinuousLearningMath.bilinearWeights(comparison.rpm, comparison.petrolOnCngMs)
                .filter { it.weight > 0.0 }
                .forEach { keys += it.row to it.column }
        }
        return keys
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .associate { (row, column) ->
                "$row:$column" to LearningStabilityV7.mapCell(comparisons, row, column)
            }
    }

    fun curveWithEvidence(comparisons: List<FuelComparisonV7>): Map<Int, LearningStabilitySnapshotV7> {
        val indexes = linkedSetOf<Int>()
        comparisons.forEach { comparison ->
            val (lower, upper, _) = KFactorProtocol.blendAxis(comparison.petrolTargetMs)
            indexes += lower
            indexes += upper
        }
        return indexes.sorted().associateWith { index ->
            LearningStabilityV7.curvePoint(comparisons, index)
        }
    }
}
