package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Monta a fonte única consumida pela interface de aprendizado.
 *
 * O arquivo persistido continua sendo a origem. Antes da projeção visual, todo
 * GNV pendente da época ativa é reconciliado contra a superfície física de
 * gasolina e o assessor é recalculado com essas comparações.
 *
 * A reconstrução pesada é revision-driven: enquanto o estado persistido não
 * muda, chamadas repetidas da WebView recebem uma cópia da mesma fotografia e
 * não refazem reconciler, projeção nem Advisor.
 */
object LearningUiSnapshotAssembler {
    @Volatile private var cachedRevision = ""
    @Volatile private var cachedPayload = ""

    @Synchronized
    fun assemble(rawSnapshot: JSONObject): JSONObject {
        val revision = revisionKey(rawSnapshot)
        if (revision == cachedRevision && cachedPayload.isNotBlank()) {
            return JSONObject(cachedPayload)
        }

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
        val result = adviceInput
            .put("cells", cells)
            .put("integrity", integrity)
            .put("comparisons", comparisons)
            .put("assistedCalibration", advice)
            .put("assisted_calibration", advice)
            .put("comparisonCount", comparisons.length())
            .put("comparison_count", comparisons.length())
            .put("uiPipeline", "PERSISTED_REVISION_CACHE")
            .put("ui_pipeline", "PERSISTED_REVISION_CACHE")
            .put("uiRevision", revision)

        cachedRevision = revision
        cachedPayload = result.toString()
        return JSONObject(cachedPayload)
    }

    private fun revisionKey(rawSnapshot: JSONObject): String {
        rawSnapshot.optString("stateDigest").takeIf { it.isNotBlank() }?.let { return "digest:$it" }
        return listOf(
            rawSnapshot.optLong("savedAt", 0L),
            rawSnapshot.optInt("epoch", 1),
            rawSnapshot.optString("mapHash", rawSnapshot.optString("map_hash", "")),
            rawSnapshot.optJSONArray("regions")?.length() ?: 0,
            rawSnapshot.optJSONArray("comparisons")?.length() ?: 0,
            rawSnapshot.optJSONArray("sessions")?.length() ?: 0,
        ).joinToString(":", prefix = "fallback:")
    }
}
