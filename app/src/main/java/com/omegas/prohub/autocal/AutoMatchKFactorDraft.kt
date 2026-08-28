package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class AutoMatchDraftPoint(
    val index: Int,
    val petrolMs: Double,
    val currentRaw: Int,
    val suggestedRaw: Int,
    val targetRaw: Int,
    val selected: Boolean = false,
    val origin: String,
) {
    val currentFactor: Double get() = KFactorProtocol.factorFromRaw(currentRaw)
    val suggestedFactor: Double get() = KFactorProtocol.factorFromRaw(suggestedRaw)
    val targetFactor: Double get() = KFactorProtocol.factorFromRaw(targetRaw)
    val changed: Boolean get() = currentRaw != targetRaw

    fun toJson(): JSONObject = JSONObject()
        .put("index", index)
        .put("petrolMs", petrolMs)
        .put("currentRaw", currentRaw)
        .put("currentFactor", currentFactor)
        .put("suggestedRaw", suggestedRaw)
        .put("suggestedFactor", suggestedFactor)
        .put("targetRaw", targetRaw)
        .put("targetFactor", targetFactor)
        .put("selected", selected)
        .put("changed", currentRaw != targetRaw)
        .put("origin", origin)
        .put("automatic", false)
}

data class AutoMatchKFactorDraft(
    val id: String,
    val snapshotHash: String,
    val createdAtMs: Long,
    val points: List<AutoMatchDraftPoint>,
    val revision: Int = 1,
) {
    val selectedCount: Int get() = points.count { it.selected && it.changed }
    val draftHash: String get() = AutoMatchKFactorDraftPlanner.hash(this)

    fun toJson(): JSONObject = JSONObject()
        .put("ok", true)
        .put("id", id)
        .put("snapshotHash", snapshotHash)
        .put("createdAtMs", createdAtMs)
        .put("revision", revision)
        .put("draftHash", draftHash)
        .put("pointCount", points.size)
        .put("selectedCount", selectedCount)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("requiresReview", true)
        .put("requiresFreshCurveRead", true)
        .put("minimumFactor", AutoMatchKFactorDraftPlanner.MIN_FACTOR)
        .put("maximumFactor", AutoMatchKFactorDraftPlanner.MAX_FACTOR)
        .put("points", JSONArray(points.map { it.toJson() }))

    fun selectedPointsForReview(): JSONObject = JSONObject()
        .put("ok", true)
        .put("draftId", id)
        .put("draftHash", draftHash)
        .put("snapshotHash", snapshotHash)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("requiresReview", true)
        .put("requiresFreshCurveRead", true)
        .put("points", JSONArray(points.filter { it.selected && it.changed }.map { point ->
            JSONObject()
                .put("index", point.index)
                .put("currentRaw", point.currentRaw)
                .put("targetRaw", point.targetRaw)
                .put("currentFactor", point.currentFactor)
                .put("targetFactor", point.targetFactor)
        }))
}

object AutoMatchKFactorDraftPlanner {
    const val MIN_FACTOR = 0.60
    const val MAX_FACTOR = KFactorProtocol.MAX_FACTOR

    fun create(analysis: JSONObject, nowMs: Long = System.currentTimeMillis()): AutoMatchKFactorDraft {
        require(analysis.optBoolean("ok") && analysis.optBoolean("available")) {
            analysis.optString("error").ifBlank { "Análise AutoMatch indisponível" }
        }
        require(!analysis.optBoolean("nativeFirmwareExact", true)) {
            "A reconstrução inferida V6 não pode ser tratada como firmware exato"
        }
        val rawPoints = analysis.optJSONArray("points") ?: JSONArray()
        require(rawPoints.length() == KFactorProtocol.POINT_COUNT) { "Análise AutoMatch incompleta" }
        val points = List(KFactorProtocol.POINT_COUNT) { index ->
            val raw = rawPoints.getJSONObject(index)
            require(raw.optInt("index", -1) == index) { "Ponto AutoMatch fora de ordem" }
            val currentRaw = raw.optInt("currentRaw", -1)
            val suggestedRaw = raw.optInt("calculatedRaw", -1)
            require(currentRaw in 0..KFactorProtocol.MAX_RAW) { "Leia MUL_ACT atual antes de criar o rascunho" }
            require(suggestedRaw in safeRawRange()) { "Ponto $index fora do limite físico K factor" }
            AutoMatchDraftPoint(
                index = index,
                petrolMs = raw.optDouble("referenceTimeMs", Double.NaN),
                currentRaw = currentRaw,
                suggestedRaw = suggestedRaw,
                targetRaw = suggestedRaw,
                selected = false,
                origin = raw.optString("origin", "UNAVAILABLE"),
            )
        }
        return AutoMatchKFactorDraft(
            id = "AMV5-${nowMs}-${UUID.randomUUID().toString().take(8)}",
            snapshotHash = analysis.optString("snapshotHash"),
            createdAtMs = nowMs,
            points = points,
        )
    }

    fun select(draft: AutoMatchKFactorDraft, index: Int, selected: Boolean): AutoMatchKFactorDraft {
        require(index in draft.points.indices) { "Ponto inválido" }
        return draft.copy(
            points = draft.points.map { point -> if (point.index == index) point.copy(selected = selected) else point },
            revision = draft.revision + 1,
        )
    }

    fun setTargetFactor(draft: AutoMatchKFactorDraft, index: Int, factor: Double): AutoMatchKFactorDraft {
        require(index in draft.points.indices) { "Ponto inválido" }
        require(factor.isFinite() && factor in MIN_FACTOR..MAX_FACTOR) {
            "Informe um fator entre 0,60 e %.4f".format(MAX_FACTOR)
        }
        val raw = KFactorProtocol.rawFromFactor(factor)
        return draft.copy(
            points = draft.points.map { point -> if (point.index == index) point.copy(targetRaw = raw) else point },
            revision = draft.revision + 1,
        )
    }

    internal fun hash(draft: AutoMatchKFactorDraft): String {
        val canonical = buildString {
            append(draft.id).append('|').append(draft.snapshotHash).append('|').append(draft.revision)
            draft.points.forEach { point ->
                append('|').append(point.index)
                    .append(':').append(point.currentRaw)
                    .append(':').append(point.suggestedRaw)
                    .append(':').append(point.targetRaw)
                    .append(':').append(if (point.selected) 1 else 0)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun safeRawRange(): IntRange =
        KFactorProtocol.rawFromFactor(MIN_FACTOR)..KFactorProtocol.MAX_RAW
}
