package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.CurvePointChangeV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.MapCellChangeV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Traduz a saída científica do AssistedCalibrationAdvisor para sugestões
 * persistentes e versionadas. O ID representa o alvo físico/revisão e não a
 * magnitude momentânea, permitindo que a mesma sugestão amadureça com a coleta.
 *
 * O adaptador nunca toca na ECU. Aplicação continua exclusivamente manual.
 */
class AdvisorSuggestionAdapterV7 {
    fun adapt(
        advice: JSONObject,
        calibration: CalibrationStateV7,
        nowMs: Long = System.currentTimeMillis(),
    ): List<LocalSuggestionV7> {
        require(nowMs >= 0)
        val output = mutableListOf<LocalSuggestionV7>()
        curveSuggestion(advice, calibration, nowMs)?.let(output::add)
        output += mapSuggestions(advice, calibration, nowMs)
        return output.distinctBy { it.id }
    }

    private fun curveSuggestion(
        advice: JSONObject,
        calibration: CalibrationStateV7,
        nowMs: Long,
    ): LocalSuggestionV7? {
        val source = advice.optJSONArray("kFactorSuggestions") ?: JSONArray()
        if (source.length() == 0) return null
        val changes = linkedMapOf<Int, CurvePointChangeV7>()
        val confidences = mutableListOf<Double>()
        val reasons = linkedSetOf<String>()
        val idealDeltas = mutableListOf<Double>()
        val safeSteps = mutableListOf<Double>()
        val estimatedResiduals = mutableListOf<Double>()
        var observed = false

        repeat(source.length()) { position ->
            val item = source.optJSONObject(position) ?: return@repeat
            val index = item.optInt("index", -1)
            if (index !in 0 until CalibrationShapeV7.CURVE_K_POINTS) return@repeat
            val readiness = item.optString("readiness", "")
            val confidence = finite(item, "confidence")?.coerceIn(0.0, 1.0) ?: 0.0
            if (confidence > 0.0 || readiness.isNotBlank() && readiness != "NO_EVIDENCE") observed = true
            if (confidence > 0.0) confidences += confidence
            item.optString("decisionReason").takeIf(String::isNotBlank)?.let(reasons::add)
            if (!item.optBoolean("actionable", false)) return@repeat
            val deltaPercent = finite(item, "suggestedDeltaPercent") ?: return@repeat
            finite(item, "idealDeltaPercent")?.let(idealDeltas::add)
            safeSteps += deltaPercent
            finite(item, "estimatedResidualAfterPercent")?.let(estimatedResiduals::add)
            val before = calibration.curveK[index]
            val requested = before * (1.0 + deltaPercent / 100.0)
            val bounded = requested.coerceIn(KFactorManager.MIN_SAFE_FACTOR, KFactorManager.MAX_SAFE_FACTOR)
            val after = KFactorProtocol.factorFromRaw(KFactorProtocol.rawFromFactor(bounded))
            if (after == before) return@repeat
            changes[index] = CurvePointChangeV7(index, before, after)
        }
        if (!observed && changes.isEmpty()) return null
        val ordered = changes.values.sortedBy { it.index }
        val lifecycle = if (ordered.isEmpty()) SuggestionLifecycleV7.OBSERVING else SuggestionLifecycleV7.PENDING
        val rationale = when {
            lifecycle == SuggestionLifecycleV7.PENDING -> {
                val ideal = idealDeltas.averageOrNull()?.let(::formatSignedPercent) ?: "—"
                val step = safeSteps.averageOrNull()?.let(::formatSignedPercent) ?: "—"
                val residual = estimatedResiduals.averageOrNull()?.let(::formatSignedPercent) ?: "—"
                "Curva K · alvo ideal $ideal; passo seguro $step em ${ordered.size} ponto(s); " +
                    "resíduo estimado $residual. Aplicação exclusivamente manual."
            }
            reasons.isNotEmpty() -> reasons.first()
            else -> "Tendência global preservada; a evidência atual não justifica correção."
        }
        return LocalSuggestionV7(
            id = stableId("curve", calibration, "global"),
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            expectedRevision = calibration.revision,
            target = SuggestionTargetV7.CURVE_K,
            curveChanges = ordered,
            rationale = rationale,
            lifecycle = lifecycle,
            confidence = confidences.averageOrZero(),
        )
    }

    /**
     * Cada célula física possui uma entidade de sugestão própria. A interface pode
     * selecionar quantas quiser em uma única intenção humana; o particionamento em
     * blocos permanece detalhe do writer Kotlin, não do advisor.
     */
    private fun mapSuggestions(
        advice: JSONObject,
        calibration: CalibrationStateV7,
        nowMs: Long,
    ): List<LocalSuggestionV7> {
        val regionByCell = regionLabels(advice.optJSONArray("mapCorrectionRegions") ?: JSONArray())
        val residual = advice.optJSONArray("mapResidualSuggestions") ?: JSONArray()
        val output = mutableListOf<LocalSuggestionV7>()
        repeat(residual.length()) { position ->
            val item = residual.optJSONObject(position) ?: return@repeat
            val row = item.optInt("row", -1)
            val column = item.optInt("column", -1)
            if (row !in 0 until CalibrationShapeV7.MAP_K_EDITABLE_ROWS ||
                column !in 0 until CalibrationShapeV7.MAP_K_COLUMNS
            ) return@repeat
            val key = "$row:$column"
            val confidence = finite(item, "confidence")?.coerceIn(0.0, 1.0) ?: 0.0
            val actionable = item.optBoolean("actionable", false)
            val change = if (actionable) mapChange(item, calibration) else null
            val lifecycle = if (change != null) SuggestionLifecycleV7.PENDING else SuggestionLifecycleV7.OBSERVING
            val reason = item.optString("decisionReason").takeIf(String::isNotBlank)
            val region = regionByCell[key]
            output += LocalSuggestionV7(
                id = stableId("map", calibration, key),
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
                expectedRevision = calibration.revision,
                target = SuggestionTargetV7.MAP_K,
                mapChanges = listOfNotNull(change),
                rationale = when {
                    lifecycle == SuggestionLifecycleV7.PENDING && region != null ->
                        "$region · correção local atualizada para esta célula; aplicação exclusivamente manual."
                    lifecycle == SuggestionLifecycleV7.PENDING ->
                        "Residual local atualizado para esta célula; aplicação exclusivamente manual."
                    reason != null -> reason
                    else -> "Sugestão preservada; a evidência atual ainda não justifica correção."
                },
                lifecycle = lifecycle,
                confidence = confidence,
            )
        }
        return output
    }

    private fun regionLabels(regions: JSONArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        repeat(regions.length()) { position ->
            val region = regions.optJSONObject(position) ?: return@repeat
            val id = region.optString("id", "Região ${position + 1}")
            val cells = region.optJSONArray("cells") ?: JSONArray()
            repeat(cells.length()) { cellPosition ->
                val cell = cells.optJSONObject(cellPosition) ?: return@repeat
                val row = cell.optInt("row", -1)
                val column = cell.optInt("column", -1)
                if (row in 0 until CalibrationShapeV7.MAP_K_EDITABLE_ROWS &&
                    column in 0 until CalibrationShapeV7.MAP_K_COLUMNS
                ) out["$row:$column"] = id
            }
        }
        return out
    }

    private fun mapChange(item: JSONObject, calibration: CalibrationStateV7): MapCellChangeV7? {
        val row = item.optInt("row", -1)
        val column = item.optInt("column", -1)
        val deltaPercent = finite(item, "suggestedDeltaPercent") ?: return null
        if (row !in 0 until CalibrationShapeV7.MAP_K_EDITABLE_ROWS ||
            column !in 0 until CalibrationShapeV7.MAP_K_COLUMNS
        ) return null
        val before = calibration.mapK[row][column]
        val after = (before * (1.0 + deltaPercent / 100.0)).roundToInt()
            .coerceIn(KWriteManager.MIN_SAFE_K, 0xFF)
        if (after == before) return null
        return MapCellChangeV7(row, column, before, after)
    }

    private fun finite(source: JSONObject, key: String): Double? {
        if (!source.has(key) || source.isNull(key)) return null
        return source.optDouble(key, Double.NaN).takeIf(Double::isFinite)
    }

    private fun stableId(prefix: String, calibration: CalibrationStateV7, physicalTarget: String): String {
        val canonical = listOf(
            prefix,
            calibration.revision.curveK,
            calibration.revision.mapK,
            physicalTarget,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { "%02x".format(it) }
        return "advisor-$prefix-$digest"
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average().coerceIn(0.0, 1.0)

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun formatSignedPercent(value: Double): String =
        String.format(Locale.US, "%+.1f%%", value).replace('.', ',')
}
