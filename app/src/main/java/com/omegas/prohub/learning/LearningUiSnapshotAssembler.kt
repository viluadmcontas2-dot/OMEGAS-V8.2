package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Monta a fonte única consumida pela interface de aprendizado.
 *
 * O arquivo persistido continua sendo a origem. Antes da projeção visual, todo
 * GNV pendente da época ativa é reconciliado contra a superfície física de
 * gasolina e o assessor é recalculado com essas comparações.
 */
object LearningUiSnapshotAssembler {
    fun assemble(rawSnapshot: JSONObject): JSONObject {
        val reconciled = LearningSnapshotReconciler.reconcile(rawSnapshot)
        val regions = reconciled.optJSONArray("regions") ?: JSONArray()
        val epoch = reconciled.optInt("epoch", 1).coerceAtLeast(1)
        val comparisons = reconciled.optJSONArray("comparisons") ?: JSONArray()
        val cells = LearningGridProjection.project(regions, epoch)
        val mapHash = reconciled.optString("mapHash", reconciled.optString("map_hash", ""))
        val integrity = LearningGridProjection.integrity(
            regions = regions,
            cells = cells,
            comparisons = comparisons,
            epoch = epoch,
            mapHash = mapHash,
        )
        val adviceInput = JSONObject(reconciled.toString())
            .put("cells", cells)
            .put("integrity", integrity)
        val advice = AssistedCalibrationAdvisor.analyze(adviceInput)
        return adviceInput
            .put("cells", cells)
            .put("integrity", integrity)
            .put("comparisons", comparisons)
            .put("assistedCalibration", advice)
            .put("assisted_calibration", advice)
            .put("comparisonCount", comparisons.length())
            .put("comparison_count", comparisons.length())
            .put("uiPipeline", "PERSISTED_REGIONS_RECONCILED_ADVISOR")
            .put("ui_pipeline", "PERSISTED_REGIONS_RECONCILED_ADVISOR")
    }
}
