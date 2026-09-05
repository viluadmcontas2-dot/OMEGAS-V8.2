package com.omegas.prohub.blue

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject

/** Passive view of current K cells. It never calculates a correction target. */
object BlueEvidenceProjection {
    fun build(learningSnapshot: JSONObject, confirmedMapSnapshot: JSONObject?): JSONObject {
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
                        .put("currentK", currentK ?: JSONObject.NULL)
                        .put("targetK", JSONObject.NULL)
                        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
                        .put("automaticWrite", false),
                )
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "BLUE_CAUSAL_ENGINE")
            .put("epoch", learningSnapshot.optInt("epoch", 1).coerceAtLeast(1))
            .put("rows", petrolBins.size)
            .put("columns", rpmBins.size)
            .put("physicalAxis", "RPM_X_PETROL_INJECTION_MS")
            .put("cells", cells)
            .put("automaticWrite", false)
            .put("humanReviewRequired", true)
    }

    private fun mapValue(rows: JSONArray?, row: Int, column: Int): Int? {
        val line = rows?.optJSONArray(row) ?: return null
        if (column !in 0 until line.length()) return null
        return (line.opt(column) as? Number)?.toInt()?.takeIf { it in 0..255 }
    }
}
