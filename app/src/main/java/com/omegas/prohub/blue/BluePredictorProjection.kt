package com.omegas.prohub.blue

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Passive UI projection for the single Blue engine.
 *
 * It never derives a K target from legacy Advisor/Predictor data. Until a Blue
 * correction proposal is bound to a cell, the target is intentionally unknown.
 */
object BluePredictorProjection {
    fun build(
        learningSnapshot: JSONObject,
        confirmedMapSnapshot: JSONObject?,
    ): JSONObject {
        val mapConfirmed = confirmedMapSnapshot?.optBoolean("complete", false) == true &&
            confirmedMapSnapshot.optBoolean("sessionConfirmed", false)
        val rows = confirmedMapSnapshot?.optJSONArray("rows")
        val petrolBins = KMapPhysicalAxes.petrolBins()
        val rpmBins = KMapPhysicalAxes.rpmBins()
        val cells = JSONArray()

        petrolBins.indices.forEach { row ->
            rpmBins.indices.forEach { column ->
                val currentK = mapValue(rows, row, column)
                cells.put(
                    JSONObject()
                        .put("key", "$row:$column")
                        .put("row", row)
                        .put("column", column)
                        .put("rpm", rpmBins[column])
                        .put("petrolMs", petrolBins[row])
                        .put("state", if (currentK != null) "MAP_KNOWN_BLUE_PENDING" else "UNKNOWN")
                        .put("currentK", currentK ?: JSONObject.NULL)
                        .put("targetK", JSONObject.NULL)
                        .put("confidence", 0.0)
                        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
                        .put("automaticWrite", false),
                )
            }
        }

        return JSONObject()
            .put("ok", true)
            .put("schema", "omegas-blue-predictor-projection-v1")
            .put("source", "BLUE_CAUSAL_ENGINE")
            .put("epoch", learningSnapshot.optInt("epoch", 1).coerceAtLeast(1))
            .put("rows", petrolBins.size)
            .put("columns", rpmBins.size)
            .put("physicalAxis", "RPM_X_PETROL_INJECTION_MS")
            .put("mapConfirmed", mapConfirmed)
            .put("cells", cells)
            .put("proposalBound", false)
            .put("legacyPredictionUsed", false)
            .put("automaticWrite", false)
            .put("humanReviewRequired", true)
    }

    private fun mapValue(rows: JSONArray?, row: Int, column: Int): Int? {
        val line = rows?.optJSONArray(row) ?: return null
        if (column !in 0 until line.length()) return null
        return (line.opt(column) as? Number)?.toInt()?.takeIf { it in 0..255 }
    }
}
