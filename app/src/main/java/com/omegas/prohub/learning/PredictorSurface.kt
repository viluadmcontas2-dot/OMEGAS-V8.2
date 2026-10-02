package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import com.omegas.prohub.calibration.MapKManualPlanner
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Superfície pura do Predictor V8.2.
 *
 * Consome somente ciência já publicada pela Store + fotografia K já confirmada.
 * Não recalcula Advisor, não toca USB, não possui writer e nunca cria confiança.
 */
object PredictorSurface {
    const val SCHEMA = "omegas-predictor-surface-v1"

    enum class CellState {
        VALIDADO,
        OBSERVADO,
        PREVISTO,
        DESCONHECIDO,
    }

    fun build(
        learningSnapshot: JSONObject,
        confirmedMapSnapshot: JSONObject? = null,
    ): JSONObject {
        val epoch = learningSnapshot.optInt("epoch", 1).coerceAtLeast(1)
        val advisor = learningSnapshot.optJSONObject("assistedCalibration")
            ?: learningSnapshot.optJSONObject("assisted_calibration")
            ?: JSONObject()
        val residuals = advisor.optJSONArray("mapResidualSuggestions") ?: JSONArray()
        val residualByCell = linkedMapOf<String, JSONObject>()
        repeat(residuals.length()) { index ->
            val item = residuals.optJSONObject(index) ?: return@repeat
            val row = item.optInt("row", -1)
            val column = item.optInt("column", -1)
            if (row in KMapPhysicalAxes.petrolBins().indices && column in KMapPhysicalAxes.rpmBins().indices) {
                residualByCell["$row:$column"] = item
            }
        }

        val anchors = learningSnapshot.optJSONArray("nativeLearningAnchors")
            ?: learningSnapshot.optJSONArray("native_learning_anchors")
            ?: JSONArray()
        val anchorsByCell = linkedMapOf<String, MutableList<JSONObject>>()
        repeat(anchors.length()) { index ->
            val anchor = anchors.optJSONObject(index) ?: return@repeat
            if (anchor.optInt("calibrationEpoch", epoch) != epoch) return@repeat
            if (!anchor.optBoolean("nativeValidity", false)) return@repeat
            if (anchor.optString("correlationState") != "CORRELATED") return@repeat
            val rpm = anchor.optDouble("rpm", Double.NaN)
            val petrolMs = anchor.optDouble("petrolOnCngMs", Double.NaN)
            val mapBar = anchor.optDouble("mapBar", 0.60)
            if (!rpm.isFinite() || !petrolMs.isFinite() || !mapBar.isFinite()) return@repeat
            val cell = LearningGridProjection.cellFor(rpm, petrolMs, mapBar)
            anchorsByCell.getOrPut(cell.optString("key")) { mutableListOf() }.add(anchor)
        }

        val mapConfirmed = confirmedMapSnapshot?.optBoolean("complete", false) == true &&
            confirmedMapSnapshot.optBoolean("sessionConfirmed", false)
        val mapRows = confirmedMapSnapshot?.optJSONArray("rows")
        val mapHash = confirmedMapSnapshot?.optString("hash").orEmpty()

        val cells = JSONArray()
        val counts = linkedMapOf(
            CellState.VALIDADO.name to 0,
            CellState.OBSERVADO.name to 0,
            CellState.PREVISTO.name to 0,
            CellState.DESCONHECIDO.name to 0,
        )
        val petrolBins = KMapPhysicalAxes.petrolBins()
        val rpmBins = KMapPhysicalAxes.rpmBins()

        petrolBins.indices.forEach { row ->
            rpmBins.indices.forEach { column ->
                val key = "$row:$column"
                val residual = residualByCell[key]
                val cellAnchors = anchorsByCell[key].orEmpty()
                val currentK = if (mapConfirmed) mapValue(mapRows, row, column) else null
                val deltaPercent = residual?.nullableDouble("suggestedDeltaPercent")
                val targetK = if (currentK != null && deltaPercent != null) {
                    runCatching { MapKManualPlanner.target(currentK, "percent", deltaPercent) }.getOrNull()
                } else null

                val residualStage = residual?.optString("confidenceStage", "").orEmpty().uppercase()
                val actionable = residual?.optBoolean("actionable", false) == true
                val state = when {
                    residual != null && actionable && cellAnchors.isNotEmpty() && residualStage in setOf("ACCEPTED", "CONFIRMED") -> CellState.VALIDADO
                    residual != null || cellAnchors.isNotEmpty() -> CellState.OBSERVADO
                    else -> CellState.DESCONHECIDO
                }
                counts[state.name] = counts.getValue(state.name) + 1

                val provenance = JSONArray()
                if (residual != null) {
                    provenance.put(JSONObject()
                        .put("source", "OMEGAS_LEARNING_RESIDUAL")
                        .put("confidence", residual.optDouble("confidence", 0.0).coerceIn(0.0, 1.0))
                        .put("confidenceStage", residualStage.ifBlank { "OBSERVED" })
                        .put("readiness", residual.optString("readiness", "UNKNOWN"))
                        .put("regionId", residual.optString("regionId")))
                }
                cellAnchors.takeLast(8).forEach { anchor ->
                    provenance.put(JSONObject()
                        .put("source", "ECU_NATIVE_AUTOCAL")
                        .put("fingerprint", anchor.optString("fingerprint"))
                        .put("scientificRevision", anchor.optLong("scientificRevision", 0L))
                        .put("correlationConfidence", anchor.optDouble("correlationConfidence", 0.0).coerceIn(0.0, 1.0)))
                }
                if (currentK != null) {
                    provenance.put(JSONObject()
                        .put("source", "ECU_CONFIRMED_MAP_K")
                        .put("mapHash", mapHash))
                }

                cells.put(JSONObject()
                    .put("key", key)
                    .put("row", row)
                    .put("column", column)
                    .put("rpm", rpmBins[column])
                    .put("petrolMs", petrolBins[row])
                    .put("state", state.name)
                    .put("stateReason", stateReason(state, residual != null, cellAnchors.size, mapConfirmed))
                    .put("currentK", currentK ?: JSONObject.NULL)
                    .put("targetK", targetK ?: JSONObject.NULL)
                    .put("suggestedDeltaPercent", deltaPercent ?: JSONObject.NULL)
                    .put("residualErrorPercent", residual?.nullableDouble("residualErrorPercent") ?: JSONObject.NULL)
                    .put("uncertaintyPercent", residual?.nullableDouble("uncertaintyPercent") ?: JSONObject.NULL)
                    .put("confidence", residual?.optDouble("confidence", 0.0)?.coerceIn(0.0, 1.0) ?: 0.0)
                    .put("nativeAnchorCount", cellAnchors.size)
                    .put("directObservation", residual != null || cellAnchors.isNotEmpty())
                    .put("predicted", false)
                    .put("provenance", provenance)
                    .put("automaticWrite", false))
            }
        }

        val revisionToken = revisionToken(
            epoch = epoch,
            advisorRevision = learningSnapshot.optLong("advisorRevision", learningSnapshot.optLong("advisor_revision", 0L)),
            anchorRevision = maxAnchorRevision(anchors, epoch),
            mapHash = mapHash,
        )
        return JSONObject()
            .put("ok", true)
            .put("schema", SCHEMA)
            .put("epoch", epoch)
            .put("axisSchema", KMapPhysicalAxes.SCHEMA)
            .put("axisLockSha256", KMapPhysicalAxes.LOCK_SHA256)
            .put("rows", petrolBins.size)
            .put("columns", rpmBins.size)
            .put("physicalAxis", "RPM_X_PETROL_INJECTION_MS")
            .put("mapConfirmed", mapConfirmed)
            .put("mapHash", mapHash.ifBlank { JSONObject.NULL })
            .put("revisionToken", revisionToken)
            .put("states", JSONArray(CellState.entries.map { it.name }))
            .put("stateCounts", JSONObject(counts as Map<*, *>))
            .put("cells", cells)
            .put("automaticWrite", false)
            .put("writer", JSONObject.NULL)
            .put("humanReviewRequired", true)
    }

    private fun mapValue(rows: JSONArray?, row: Int, column: Int): Int? {
        val line = rows?.optJSONArray(row) ?: return null
        if (column !in 0 until line.length()) return null
        return (line.opt(column) as? Number)?.toInt()?.takeIf { it in 0..255 }
    }

    private fun stateReason(state: CellState, hasResidual: Boolean, anchorCount: Int, mapConfirmed: Boolean): String = when (state) {
        CellState.VALIDADO -> "Residual OMEGAS direto + âncora AutoCal nativa na mesma célula"
        CellState.OBSERVADO -> when {
            hasResidual && anchorCount > 0 -> "Há observação OMEGAS e suporte nativo, mas ainda sem validação suficiente"
            hasResidual -> "Há observação direta do Learning nesta célula"
            else -> "Há âncora AutoCal nativa, mas ainda não há residual local suficiente"
        }
        CellState.PREVISTO -> "Estimativa espacial derivada de suporte vizinho"
        CellState.DESCONHECIDO -> if (mapConfirmed) "Mapa K conhecido, mas sem suporte científico para prever ajuste" else "Sem suporte científico e sem mapa K confirmado"
    }

    private fun maxAnchorRevision(anchors: JSONArray, epoch: Int): Long {
        var max = 0L
        repeat(anchors.length()) { index ->
            val anchor = anchors.optJSONObject(index) ?: return@repeat
            if (anchor.optInt("calibrationEpoch", epoch) == epoch) {
                max = maxOf(max, anchor.optLong("scientificRevision", 0L))
            }
        }
        return max
    }

    private fun revisionToken(epoch: Int, advisorRevision: Long, anchorRevision: Long, mapHash: String): String {
        val raw = "$epoch|$advisorRevision|$anchorRevision|$mapHash"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
}
