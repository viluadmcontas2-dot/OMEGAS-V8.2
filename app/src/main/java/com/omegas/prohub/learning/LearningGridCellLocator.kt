package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import kotlin.math.abs

/** Lightweight K-map cell lookup for telemetry hot paths; never materializes JSON or interpolation arrays. */
internal object LearningGridCellLocator {
    data class Cell(
        val geometryKnown: Boolean,
        val row: Int,
        val column: Int,
        val key: String,
    )

    private val legacyRpmAxis = KMapPhysicalAxes.rpmBins()
    private val legacyPetrolAxis = KMapPhysicalAxes.petrolBins()

    fun locate(rpm: Double, petrolMs: Double): Cell {
        val binding = LearningCalibrationAuthority.snapshot()
        val row: Int
        val column: Int
        if (binding != null && binding.geometryKnown()) {
            row = nearestDouble(binding.petrolAxisMs, petrolMs)
            column = nearestInt(binding.rpmAxis, rpm)
        } else {
            if (LearningCalibrationAuthority.requiresKnownGeometry()) return unknown()
            row = nearest(legacyPetrolAxis, petrolMs)
            column = nearest(legacyRpmAxis, rpm)
        }
        if (row < 0 || column < 0) return unknown()
        return Cell(
            geometryKnown = true,
            row = row,
            column = column,
            key = "$row:$column",
        )
    }

    private fun nearest(values: DoubleArray, target: Double): Int {
        if (values.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.indices.forEach { index ->
            val distance = abs(values[index] - target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun nearest(values: IntArray, target: Double): Int {
        if (values.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.indices.forEach { index ->
            val distance = abs(values[index].toDouble() - target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun nearestDouble(values: List<Double>, target: Double): Int {
        if (values.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.indices.forEach { index ->
            val distance = abs(values[index] - target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun nearestInt(values: List<Int>, target: Double): Int {
        if (values.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.indices.forEach { index ->
            val distance = abs(values[index].toDouble() - target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun unknown(): Cell = Cell(
        geometryKnown = false,
        row = -1,
        column = -1,
        key = "UNKNOWN",
    )
}
