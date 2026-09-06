package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Projeta a evidência física persistida para a tela de aprendizado.
 * Não reconcilia combustíveis, não gera comparação e não calcula correção.
 * Se uma comparação Blue já estiver anexada ao snapshot, ela é preservada
 * passivamente; a autoridade continua sendo o BlueCausalEngine.
 */
object LearningUiSnapshotAssembler {
    fun assemble(rawSnapshot: JSONObject): JSONObject {
        val root = JSONObject(rawSnapshot.toString())
        val regions = LearningEvidenceDimensions.enrichRegions(root)
            .optJSONArray("regions") ?: JSONArray()
        val epoch = root.optInt("epoch", 1).coerceAtLeast(1)
        val cells = LearningGridProjection.project(regions, epoch)
        val comparisons = root.optJSONArray("comparisons") ?: JSONArray()
        val integrity = LearningGridProjection.integrity(
            regions = regions,
            cells = cells,
            comparisons = comparisons,
            epoch = epoch,
            mapHash = root.optString("mapHash", root.optString("map_hash", "")),
        )
        return root
            .put("regions", regions)
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
