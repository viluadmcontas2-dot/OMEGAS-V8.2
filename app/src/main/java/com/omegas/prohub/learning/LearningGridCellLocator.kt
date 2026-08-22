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

    fun locate(rpm: Double, petrolMs: Double): Cell {
        val binding = LearningCalibrationAuthority.snapshot()
        val rpmAxis: IntArray
        val petrolAxis: DoubleArray
        if (binding != null && binding.geometryKnown()) {
            rpmAxis = binding.rpmAxis.toIntArray()
            petrolAxis = binding.petrolAxisMs.toDoubleArray()
        } else {
            if (LearningCalibrationAuthority.requiresKnownGeometry()) return unknown()
            rpmAxis = KMapPhysicalAxes.rpmBins()
            petrolAxis = KMapPhysicalAxes.petrolBins()
        }
        if (rpmAxis.isEmpty() || petrolAxis.isEmpty()) return unknown()

        val row = nearest(petrolAxis, petrolMs)
        val column = nearest(rpmAxis, rpm)
        return Cell(
            geometryKnown = true,
            row = row,
            column = column,
            key = "$row:$column",
        )
    }

    private fun nearest(values: DoubleArray, target: Double): Int {
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
