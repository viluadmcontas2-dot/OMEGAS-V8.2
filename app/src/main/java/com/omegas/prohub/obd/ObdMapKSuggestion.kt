package com.omegas.prohub.obd

import com.omegas.prohub.calibration.KMapPhysicalAxes
import com.omegas.prohub.calibration.KOperatingPolicy
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fail-closed adapter from an independent OBD result to one manual Map-K cell. */
object ObdMapKSuggestion {
    private const val MIN_ADDRESS_CONFIDENCE = 0.75

    fun prepare(
        learning: ObdLearningResult,
        rpm: Double,
        resolvedPetrolMs: Double?,
        mapRows: JSONArray?,
        addressConfidence: Double,
    ): JSONObject {
        val percent = learning.correctionMultiplier?.let { (it - 1.0) * 100.0 }
        val base = JSONObject()
            .put("source", "OBD_INDEPENDENT_STFT_GNV")
            .put("correctionPercent", percent ?: JSONObject.NULL)
            .put("correctionMultiplier", learning.correctionMultiplier ?: JSONObject.NULL)
            .put("quality", learning.quality)
            .put("epoch", learning.epoch)
            .put("automatic", false)
            .put("manualOnly", true)
        if (learning.state != ObdLearningState.READY || learning.correctionMultiplier == null) {
            return base.put("available", false).put("state", "INSUFFICIENT_EVIDENCE")
        }
        if (resolvedPetrolMs == null || !resolvedPetrolMs.isFinite() ||
            addressConfidence < MIN_ADDRESS_CONFIDENCE || mapRows == null || mapRows.length() < KMapPhysicalAxes.WRITABLE_ROWS
        ) {
            return base.put("available", false).put("state", "ADDRESS_UNRESOLVED")
        }
        val row = KMapPhysicalAxes.rpmBins().indices.minByOrNull { abs(KMapPhysicalAxes.rpmBins()[it] - rpm) }
            ?: return base.put("available", false).put("state", "ADDRESS_UNRESOLVED")
        val column = KMapPhysicalAxes.petrolBins().indices.minByOrNull {
            abs(KMapPhysicalAxes.petrolBins()[it] - resolvedPetrolMs)
        } ?: return base.put("available", false).put("state", "ADDRESS_UNRESOLVED")
        val currentRow = mapRows.optJSONArray(row)
            ?: return base.put("available", false).put("state", "READBACK_REQUIRED")
        val current = currentRow.optInt(column, -1)
        if (current !in 0..255) return base.put("available", false).put("state", "READBACK_REQUIRED")
        val target = (current * learning.correctionMultiplier).roundToInt()
            .coerceIn(KOperatingPolicy.MIN_TARGET_K, KOperatingPolicy.MAX_TARGET_K)
        if (target == current) return base.put("available", false).put("state", "QUANTIZED_NO_CHANGE")
        val cell = JSONObject().put("row", row).put("column", column)
            .put("current", current).put("target", target)
        return base.put("available", true).put("state", "OBD_MAP_K_READY")
            .put("addressConfidence", addressConfidence.coerceIn(0.0, 1.0))
            .put("resolvedPetrolMs", resolvedPetrolMs)
            .put("row", row).put("column", column).put("current", current).put("target", target)
            .put("cells", JSONArray().put(cell))
    }
}
