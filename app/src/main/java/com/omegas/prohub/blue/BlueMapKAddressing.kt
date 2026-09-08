package com.omegas.prohub.blue

import com.omegas.prohub.learning.LearningGridProjection
import org.json.JSONObject

/**
 * Maps a measured Blue comparison to the Map K region that was actually active on GNV.
 * The gasoline reference determines error magnitude only; it never determines the cell address.
 */
object BlueMapKAddressing {
    fun cell(comparison: FuelComparison): JSONObject = LearningGridProjection.cellFor(
        rpm = comparison.rpm,
        petrolMs = comparison.petrolOnCngMs,
        mapBar = comparison.mapBar,
    )

    fun presentationFields(comparison: FuelComparison): JSONObject {
        val mapKCell = cell(comparison)
        return JSONObject()
            .put("mapKCell", mapKCell)
            .put("row", mapKCell.getInt("row"))
            .put("column", mapKCell.getInt("column"))
            .put("cellKey", mapKCell.getString("key"))
    }
}
