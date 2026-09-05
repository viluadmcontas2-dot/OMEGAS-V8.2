package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Monta a projeção de evidência física consumida pela tela Aprender.
 *
 * Esta camada não toma decisão de calibração. Ela apenas reconcilia a memória
 * física, calcula a geometria de visualização e publica comparações observadas.
 * A única autoridade de equivalência e proposta de correção é o BlueCausalEngine.
 */
object LearningUiSnapshotAssembler {
    fun assemble(rawSnapshot: JSONObject): JSONObject {
        val reconciled = LearningEvidenceDimensions.enrichRegions(
            LearningSnapshotReconciler.reconcile(rawSnapshot),
        )
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
        return JSONObject(reconciled.toString())
            .put("cells", cells)
            .put("integrity", integrity)
            .put("comparisons", comparisons)
            .put("comparisonCount", comparisons.length())
            .put("comparison_count", comparisons.length())
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
            .put("uiPipeline", "PHYSICAL_EVIDENCE_ONLY")
            .put("ui_pipeline", "PHYSICAL_EVIDENCE_ONLY")
    }
}
