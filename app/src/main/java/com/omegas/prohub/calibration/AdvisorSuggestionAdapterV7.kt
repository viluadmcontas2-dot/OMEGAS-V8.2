package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.CurvePointChangeV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.MapCellChangeV7
import com.omegas.v7.runtime.PhysicsSuggestionMetadataV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Traduz a saída científica do AssistedCalibrationAdvisor para sugestões
 * persistentes e versionadas. O ID representa o alvo físico/revisão e não a
 * magnitude momentânea, permitindo que a mesma sugestão amadureça com a coleta.
 *
 * O adaptador nunca toca na ECU. Aplicação continua exclusivamente manual.
 * Uma magnitude numérica só pode virar alteração PENDING quando Physics declara
 * explicitamente target ideal, mecanismo causal correspondente e autoridade
 * física/empírica. Policy, UNKNOWN e candidate-lane permanecem observacionais.
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
        val observedPhysics = mutableListOf<PhysicsSuggestionMetadataV7>()
        val changedPhysics = mutableListOf<PhysicsSuggestionMetadataV7>()
        var observed = false

        repeat(source.length()) { position ->
            val item = source.optJSONObject(position) ?: return@repeat
            val index = item.optInt("index", -1)
            if (index !in 0 until CalibrationShapeV7.CURVE_K_POINTS) return@repeat
            val itemPhysics = physicsMetadata(item)
            observedPhysics += itemPhysics
            val readiness = item.optString("readiness", "")
            val confidence = finite(item, "confidence")?.coerceIn(0.0, 1.0) ?: 0.0
            if (confidence > 0.0 || readiness.isNotBlank() && readiness != "NO_EVIDENCE") observed = true
            if (confidence > 0.0) confidences += confidence
            item.optString("decisionReason").takeIf(String::isNotBlank)?.let(reasons::add)
            if (!authorizedForConcreteChange(item, CorrectionMechanism.CURVE_MUL_ACT)) return@repeat
            val deltaPercent = finite(item, "suggestedDeltaPercent") ?: return@repeat
            val before = calibration.curveK[index]
            val requested = before * (1.0 + deltaPercent / 100.0)
            val bounded = requested.coerceIn(KFactorManager.MIN_SAFE_FACTOR, KFactorManager.MAX_SAFE_FACTOR)
            val after = KFactorProtocol.factorFromRaw(KFactorProtocol.rawFromFactor(bounded))
            if (after == before) return@repeat
            changes[index] = CurvePointChangeV7(index, before, after)
            changedPhysics += itemPhysics
        }
        if (!observed && changes.isEmpty()) return null
        val ordered = changes.values.sortedBy { it.index }
        val authorizedPhysics = aggregatePhysics(changedPhysics)
        val concreteAuthorized = ordered.isNotEmpty() && authorizedPhysics.authorizes(SuggestionTargetV7.CURVE_K)
        val lifecycle = if (concreteAuthorized) SuggestionLifecycleV7.PENDING else SuggestionLifecycleV7.OBSERVING
        val persistedChanges = if (concreteAuthorized) ordered else emptyList()
        val rationale = when {
            lifecycle == SuggestionLifecycleV7.PENDING ->
                "Target físico autorizado para ${persistedChanges.size} ponto(s) da Curva K; aplicação exclusivamente manual."
            reasons.isNotEmpty() -> reasons.first()
            else -> "Tendência global preservada; Physics ainda não autorizou target numérico para aplicação."
        }
        return LocalSuggestionV7(
            id = stableId("curve", calibration, "global"),
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            expectedRevision = calibration.revision,
            target = SuggestionTargetV7.CURVE_K,
            curveChanges = persistedChanges,
            rationale = rationale,
            lifecycle = lifecycle,
            confidence = confidences.averageOrZero(),
            physics = if (concreteAuthorized) authorizedPhysics else aggregatePhysics(observedPhysics),
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
            val physics = physicsMetadata(item)
            val authorized = authorizedForConcreteChange(item, CorrectionMechanism.MAP_LOCAL) &&
                physics.authorizes(SuggestionTargetV7.MAP_K)
            val change = if (authorized) mapChange(item, calibration) else null
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
                        "$region · target físico autorizado para esta célula; aplicação exclusivamente manual."
                    lifecycle == SuggestionLifecycleV7.PENDING ->
                        "Target físico autorizado para esta célula; aplicação exclusivamente manual."
                    reason != null -> reason
                    else -> "Sugestão preservada; Physics ainda não autorizou target numérico para aplicação."
                },
                lifecycle = lifecycle,
                confidence = confidence,
                physics = physics,
            )
        }
        return output
    }

    private fun authorizedForConcreteChange(item: JSONObject, requiredMechanism: CorrectionMechanism): Boolean {
        if (!item.optBoolean("actionable", false)) return false
        if (!item.optBoolean("idealTarget", false)) return false
        if (item.optString("correctionMechanism") != requiredMechanism.name) return false
        val authority = runCatching {
            MagnitudeAuthority.valueOf(item.optString("magnitudeAuthority"))
        }.getOrNull() ?: return false
        return authority == MagnitudeAuthority.PHYSICALLY_ANCHORED ||
            authority == MagnitudeAuthority.EMPIRICALLY_BOUNDED
    }

    private fun physicsMetadata(item: JSONObject): PhysicsSuggestionMetadataV7 = PhysicsSuggestionMetadataV7(
        magnitudeAuthority = enumOrDefault(
            item.optString("magnitudeAuthority"),
            MagnitudeAuthority.UNKNOWN,
        ),
        stepAuthority = enumOrDefault(
            item.optString("stepAuthority"),
            if (item.optString("magnitudeRole") == "STEP_POLICY_BASELINE") {
                MagnitudeAuthority.POLICY_ONLY
            } else {
                MagnitudeAuthority.UNKNOWN
            },
        ),
        correctionMechanism = enumOrDefault(
            item.optString("correctionMechanism"),
            CorrectionMechanism.UNKNOWN,
        ),
        effectDirection = enumOrDefault(
            item.optString("expectedEffectDirection"),
            legacyDirection(item),
        ),
        effectAuthority = enumOrDefault(
            item.optString("expectedEffectAuthority"),
            MagnitudeAuthority.UNKNOWN,
        ),
        lowerBound = finite(item, "expectedEffectLowerBound"),
        upperBound = finite(item, "expectedEffectUpperBound"),
        assumptions = jsonStrings(item.optJSONArray("expectedEffectAssumptions")),
        falsifier = item.optString("expectedEffectFalsifier"),
        evidencePath = jsonStrings(item.optJSONArray("mechanismEvidencePath")),
        idealTarget = item.optBoolean("idealTarget", false),
    )

    private fun aggregatePhysics(items: List<PhysicsSuggestionMetadataV7>): PhysicsSuggestionMetadataV7 {
        if (items.isEmpty()) return PhysicsSuggestionMetadataV7()
        val first = items.first()
        val sameAuthority = items.all {
            it.magnitudeAuthority == first.magnitudeAuthority &&
                it.stepAuthority == first.stepAuthority &&
                it.correctionMechanism == first.correctionMechanism &&
                it.effectDirection == first.effectDirection &&
                it.effectAuthority == first.effectAuthority &&
                it.idealTarget == first.idealTarget
        }
        val evidence = items.flatMap { it.evidencePath }.distinct()
        val assumptions = items.flatMap { it.assumptions }.distinct()
        if (!sameAuthority) {
            return PhysicsSuggestionMetadataV7(
                assumptions = assumptions,
                falsifier = "mixed physics authority across aggregated suggestion",
                evidencePath = evidence,
            )
        }
        return first.copy(
            lowerBound = first.lowerBound.takeIf { bound -> items.all { it.lowerBound == bound } },
            upperBound = first.upperBound.takeIf { bound -> items.all { it.upperBound == bound } },
            assumptions = assumptions,
            falsifier = items.map { it.falsifier }.filter(String::isNotBlank).distinct().joinToString("; "),
            evidencePath = evidence,
        )
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

    private fun jsonStrings(values: JSONArray?): List<String> {
        if (values == null) return emptyList()
        return buildList {
            repeat(values.length()) { index ->
                values.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private fun legacyDirection(item: JSONObject): EffectDirection = when (item.optString("direction")) {
        "INCREASE_CNG_DELIVERY" -> EffectDirection.INCREASE
        "DECREASE_CNG_DELIVERY" -> EffectDirection.DECREASE
        else -> EffectDirection.UNKNOWN
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
}
