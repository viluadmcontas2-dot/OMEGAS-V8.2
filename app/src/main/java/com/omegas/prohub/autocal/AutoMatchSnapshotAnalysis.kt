package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.AutoCalScale
import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject

/** Adapta snapshot AutoCal para o motor inferido, sem acessar USB. */
object AutoMatchSnapshotAnalysis {
    private data class Requirement(val key: String, val elements: Int)

    private val requirements = listOf(
        Requirement(AutoCalProtocol.PETR_INJ_TBP.key, KFactorProtocol.POINT_COUNT),
        Requirement(AutoCalProtocol.MNFLD_PRESS_THD.key, AutoMatchV5Engine.PRESSURE_BAND_COUNT),
        Requirement(AutoCalProtocol.PETR_MNFLD_PRESS_RV.key, KFactorProtocol.POINT_COUNT),
        Requirement(AutoCalProtocol.GAS_MNFLD_PRESS_RV.key, KFactorProtocol.POINT_COUNT),
        Requirement(AutoCalProtocol.MUL_ACT.key, KFactorProtocol.POINT_COUNT),
    )

    fun analyze(snapshot: JSONObject): JSONObject {
        val fields = fieldsByKey(snapshot.optJSONArray("fields") ?: JSONArray())
        val acquisition = AutoCalAcquisition.fromSnapshot(snapshot)
        val missing = requirements.filter { requirement ->
            val field = fields[requirement.key]
            field == null || field.optString("status") != AutoCalFieldStatus.VALID.name ||
                field.optJSONArray("rawValues")?.length() != requirement.elements
        }
        val requiredTimes = requirements.mapNotNull { requirement ->
            fields[requirement.key]?.optLong("capturedAtMs", 0L)?.takeIf { it > 0L }
        }
        val requiredSpanMs = if (requiredTimes.size == requirements.size) {
            (requiredTimes.maxOrNull() ?: 0L) - (requiredTimes.minOrNull() ?: 0L)
        } else null
        val group = coherenceGroup(snapshot, "AUTOMATCH_CURVES")
        val timingCoherent = when {
            group != null -> group.optBoolean("coherent", false)
            requiredSpanMs != null -> requiredSpanMs <= AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS
            // Snapshots/replays V6 antigos não possuíam horário por campo. Eles
            // continuam analisáveis, mas não ganham uma falsa aprovação temporal.
            else -> true
        }
        if (missing.isEmpty() && !timingCoherent) {
            return unavailableForTiming(
                acquisition = acquisition,
                spanMs = group?.optLong("spanMs", requiredSpanMs ?: 0L) ?: (requiredSpanMs ?: 0L),
                limitMs = group?.optLong(
                    "limitMs",
                    AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS,
                ) ?: AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS,
            )
        }
        if (missing.isNotEmpty()) {
            return JSONObject()
                .put("ok", true)
                .put("available", false)
                .put("mode", "AUTOMATCH_INFERIDO_V2")
                .put("nativeFirmwareExact", false)
                .put("automatic", false)
                .put("manualOnly", true)
                .put("acquisition", acquisition)
                .put("missingFields", JSONArray(missing.map { it.key }))
                .put("requirements", JSONArray(missing.map {
                    JSONObject().put("key", it.key).put("elements", it.elements)
                }))
                .put("message", "Leia eixo 30, bandas 18, curvas 30 e MUL_ACT atual 30")
        }

        return try {
            val axis = intArray(fields.getValue(AutoCalProtocol.PETR_INJ_TBP.key))
            val pressureBands = intArray(fields.getValue(AutoCalProtocol.MNFLD_PRESS_THD.key))
            val petrolMap = intArray(fields.getValue(AutoCalProtocol.PETR_MNFLD_PRESS_RV.key))
            val gasMap = intArray(fields.getValue(AutoCalProtocol.GAS_MNFLD_PRESS_RV.key))
            val currentMul = intArray(fields.getValue(AutoCalProtocol.MUL_ACT.key))
            val valid = BooleanArray(KFactorProtocol.POINT_COUNT) { index ->
                axis[index] > 0 && petrolMap[index] in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() &&
                    gasMap[index] in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()
            }
            val result = AutoMatchV5Engine.calculate(
                petrol = AutoMatchCurve30(axis, petrolMap, valid),
                gas = AutoMatchCurve30(axis, gasMap, valid),
                pressureBandsRaw = pressureBands,
                previousFactorsRaw = currentMul,
            )

            val points = JSONArray()
            result.points.forEach { point ->
                val calculatedRaw = point.factorRaw
                val deltaPercent = if (point.currentRaw > 0 && calculatedRaw != null) {
                    (calculatedRaw - point.currentRaw) * 100.0 / point.currentRaw.toDouble()
                } else null
                points.put(JSONObject()
                    .put("index", point.index)
                    .put("referenceTimeRaw", point.referenceTimeRaw)
                    .put("referenceTimeMs", point.referenceTimeMs)
                    .put("petrolMapRaw", petrolMap[point.index])
                    .put("petrolMapBar", AutoCalScale.mapBar(petrolMap[point.index]))
                    .put("gasMapRaw", gasMap[point.index])
                    .put("gasMapBar", AutoCalScale.mapBar(gasMap[point.index]))
                    .put("gasEquivalentTimeRaw", point.gasEquivalentTimeRaw ?: JSONObject.NULL)
                    .put("gasEquivalentTimeMs", point.gasEquivalentTimeMs ?: JSONObject.NULL)
                    .put("currentRaw", point.currentRaw)
                    .put("currentFactor", point.currentFactor)
                    .put("stepRatio", point.stepRatio ?: JSONObject.NULL)
                    .put("stepPercent", point.stepRatio?.times(100.0) ?: JSONObject.NULL)
                    .put("calculatedRaw", calculatedRaw ?: JSONObject.NULL)
                    .put("calculatedFactor", point.factor ?: JSONObject.NULL)
                    .put("deltaPercent", deltaPercent ?: JSONObject.NULL)
                    .put("origin", point.origin.name))
            }

            JSONObject()
                .put("ok", true)
                .put("available", true)
                .put("mode", "AUTOMATCH_INFERIDO_V2")
                .put("title", "AutoMatch inferido — Reconstrução V6")
                .put("algorithm", result.algorithm)
                .put("nativeFirmwareExact", result.nativeFirmwareExact)
                .put("formulaReference", true)
                .put("automatic", false)
                .put("manualOnly", true)
                .put("complete", result.complete)
                .put("calculatedCount", result.calculatedCount)
                .put("extendedCount", result.extendedCount)
                .put("validBandCount", result.validBandCount)
                .put("supportStartMs", result.supportStartMs ?: JSONObject.NULL)
                .put("supportEndMs", result.supportEndMs ?: JSONObject.NULL)
                .put("firstCalculatedIndex", result.firstCalculatedIndex ?: JSONObject.NULL)
                .put("lastCalculatedIndex", result.lastCalculatedIndex ?: JSONObject.NULL)
                .put("formula", JSONObject()
                    .put("family", "HORIZONTAL_SAME_PRESSURE")
                    .put("smoothingBands", AutoMatchV5Engine.SMOOTHING_WINDOW)
                    .put("gain", AutoMatchV5Engine.GAIN)
                    .put("deadbandRatio", AutoMatchV5Engine.DEADBAND_RATIO)
                    .put("maximumStepRatio", AutoMatchV5Engine.MAX_STEP_RATIO)
                    .put("update", "MULTIPLICATIVE_ON_PREVIOUS_MUL")
                    .put("quantization", "Q14_TRUNCATION"))
                .put("warnings", JSONArray(result.warnings))
                .put("snapshotHash", snapshot.optString("snapshotHash"))
                .put("points", points)
                .put("acquisition", acquisition)
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("available", false)
                .put("mode", "AUTOMATCH_INFERIDO_V2")
                .put("nativeFirmwareExact", false)
                .put("automatic", false)
                .put("manualOnly", true)
                .put("error", error.message ?: "Não foi possível calcular o AutoMatch inferido V2")
        }
    }

    private fun unavailableForTiming(
        acquisition: JSONObject,
        spanMs: Long,
        limitMs: Long,
    ): JSONObject = JSONObject()
        .put("ok", true)
        .put("available", false)
        .put("mode", "AUTOMATCH_INFERIDO_V2")
        .put("nativeFirmwareExact", false)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("acquisition", acquisition)
        .put("requiredFieldSpanMs", spanMs)
        .put("maximumFieldSkewMs", limitMs)
        .put("message", "Campos essenciais do AutoMatch foram lidos em instantes incompatíveis; releia o snapshot")

    private fun coherenceGroup(snapshot: JSONObject, key: String): JSONObject? {
        val groups = snapshot.optJSONArray("coherenceGroups") ?: return null
        repeat(groups.length()) { index ->
            val group = groups.optJSONObject(index) ?: return@repeat
            if (group.optString("key") == key) return group
        }
        return null
    }

    private fun fieldsByKey(array: JSONArray): Map<String, JSONObject> = buildMap {
        repeat(array.length()) { index ->
            val field = array.optJSONObject(index) ?: return@repeat
            field.optString("key").takeIf { it.isNotBlank() }?.let { put(it, field) }
        }
    }

    private fun intArray(field: JSONObject): IntArray = intArray(
        field.optJSONArray("rawValues") ?: JSONArray(),
    )

    private fun intArray(array: JSONArray): IntArray = IntArray(array.length()) { array.optInt(it) }
}
