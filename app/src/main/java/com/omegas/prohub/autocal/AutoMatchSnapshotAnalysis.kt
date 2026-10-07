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

    /**
     * Equivalência Refinada OMEGAS (ver [AutoMatchRefinedEngine]). Usa eixo, MUL_ACT
     * e os buffers de aquisição atuais; sem buffers coerentes cai para o modo de
     * polimento de coerência. Nunca grava na ECU.
     */
    fun analyzeRefined(
        snapshot: JSONObject,
        telemetryPairs: List<Pair<Double, Double>> = emptyList(),
        pointGainScale: DoubleArray? = null,
        telemetryEpisodes: List<Int> = emptyList(),
        holdMinStepLog: Double = 0.0,
        fineBins: List<FineBins.Bin>? = null,
        /** Época da evidência nativa do GNV (ver [NativeGasEvidenceEpoch]): buffers de antes de uma gravação de K não contam. */
        epoch: NativeGasEvidenceEpoch = NativeGasEvidenceEpoch.shared,
    ): JSONObject {
        val fields = fieldsByKey(snapshot.optJSONArray("fields") ?: JSONArray())
        fun valid(field: AutoCalProtocol.Field, elements: Int): IntArray? {
            val value = fields[field.key] ?: return null
            if (value.optString("status") != AutoCalFieldStatus.VALID.name) return null
            val raw = value.optJSONArray("rawValues") ?: return null
            return if (raw.length() == elements) intArray(raw) else null
        }
        val base = JSONObject()
            .put("mode", REFINED_MODE)
            .put("algorithm", AutoMatchRefinedEngine.ALGORITHM)
            .put("nativeFirmwareExact", false)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("snapshotHash", snapshot.optString("snapshotHash"))
        // Snapshot incoerente no tempo (grupos lidos em instantes incompatíveis) não é base de proposta.
        // `partial` é true em todo snapshot real: NÃO é critério.
        if (snapshot.has("temporalCoherent") && !snapshot.optBoolean("temporalCoherent", true)) {
            return base.put("ok", true).put("available", false)
                .put("reason", SNAPSHOT_INCOHERENT_REASON)
                .put("message", "Snapshot lido em instantes incompatíveis; releia antes de propor")
        }
        val axis = valid(AutoCalProtocol.PETR_INJ_TBP, KFactorProtocol.POINT_COUNT)
        val currentMul = valid(AutoCalProtocol.MUL_ACT, KFactorProtocol.POINT_COUNT)
        if (axis == null || currentMul == null) {
            return base.put("ok", true).put("available", false)
                .put("reason", "EIXO_OU_MUL_ACT_INDISPONIVEL")
                .put("message", "Leia o snapshot AutoCal com eixo e Curva K atual (MUL_ACT)")
        }
        val bands = AutoMatchV5Engine.PRESSURE_BAND_COUNT
        val acquisitionGroup = coherenceGroup(snapshot, "ACQUISITION_CURRENT")
        val buffersCoherent = acquisitionGroup?.optBoolean("coherent", false) ?: true
        fun band(field: AutoCalProtocol.Field) = if (buffersCoherent) valid(field, bands) else null
        // Época: o T_g de cada banda tem de ter sido adquirido sob o MUL_ACT deste snapshot (o motor usa K(T_g) = kOld).
        val rawGasCounts = band(AutoCalProtocol.NUM_BUF_UPD_GAS)
        val gasCounts = rawGasCounts?.let {
            epoch.effectiveGasCounts(
                it,
                gasCapturedAtMs = epoch.fieldTime(snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS.key),
                mulCapturedAtMs = epoch.fieldTime(snapshot, AutoCalProtocol.MUL_ACT.key),
            )
        }
        val epochFiltered = rawGasCounts != null && gasCounts != null && !rawGasCounts.contentEquals(gasCounts)
        base.put("nativeEpochFiltered", epochFiltered)
        return try {
            val result = AutoMatchRefinedEngine.refine(
                AutoMatchRefinedEngine.Input(
                    axisRaw = axis,
                    mulActRaw = currentMul,
                    petrolTimeRaw = band(AutoCalProtocol.PETR_INJ_TBUF),
                    petrolMapRaw = band(AutoCalProtocol.MNFLD_PRESS_BUF),
                    petrolCounts = band(AutoCalProtocol.NUM_BUF_UPD_PETR),
                    gasTimeRaw = band(AutoCalProtocol.PETR_INJ_TBUF_GAS),
                    gasMapRaw = band(AutoCalProtocol.MNFLD_PRESS_BUF_GAS),
                    gasCounts = gasCounts,
                    telemetryPairs = telemetryPairs,
                    pointGainScale = pointGainScale,
                    telemetryEpisodes = telemetryEpisodes,
                    holdMinStepLog = holdMinStepLog,
                    fineBins = fineBins,
                    pressureThresholdsRaw = valid(AutoCalProtocol.MNFLD_PRESS_THD, bands),
                ),
            )
            if (!result.available) {
                return base.put("ok", true).put("available", false).put("reason", result.reason)
                    .put("outOfRangePoints", result.outOfRangePoints)
            }
            refinedJson(base, result, buffersCoherent)
        } catch (error: Exception) {
            base.put("ok", false).put("available", false)
                .put("error", error.message ?: "Não foi possível calcular a equivalência refinada")
        }
    }

    /** Abaixo disso (ms de Petrol Inj.) o refino não reduz K: protege contra o motor apagar. Fonte: [AutoMatchRefinedEngine.LOW_GUARD_MS]. */
    const val LOW_GUARD_MS = AutoMatchRefinedEngine.LOW_GUARD_MS

    const val SNAPSHOT_INCOHERENT_REASON = "SNAPSHOT_INCOERENTE_NO_TEMPO"

    private fun refinedJson(base: JSONObject, result: AutoMatchRefinedEngine.Result, buffersCoherent: Boolean): JSONObject {
        val points = JSONArray()
        result.refinedRaw.forEachIndexed { index, engineRaw ->
            val currentRaw = result.currentRaw[index]
            // Trava da baixa: marcha lenta, desaceleração e embreagem (Petrol Inj. < LOW_GUARD_MS)
            // é onde o motor apaga no GNV quando a curva fica pobre. O motor já aplica a trava como limite da
            // caixa ANTES da coerência (sem degrau); isto é só a rede de segurança e não deve disparar.
            val lowGuard = result.axisMs[index] < LOW_GUARD_MS && engineRaw < currentRaw
            val refinedRaw = if (lowGuard) currentRaw else engineRaw
            points.put(JSONObject()
                .put("index", index)
                .put("referenceTimeMs", result.axisMs[index])
                .put("currentRaw", currentRaw)
                .put("currentFactor", KFactorProtocol.factorFromRaw(currentRaw))
                .put("calculatedRaw", refinedRaw)
                .put("calculatedFactor", KFactorProtocol.factorFromRaw(refinedRaw))
                .put("deltaPercent", if (currentRaw > 0) (refinedRaw - currentRaw) * 100.0 / currentRaw else JSONObject.NULL)
                .put("evidenceGain", result.gain[index])
                .put("origin", if (lowGuard) "LOW_GUARD" else result.origins[index].name))
        }
        val targets = JSONArray(result.targets.filter { it.mapBar.isFinite() }.map { target ->
            JSONObject()
                .put("mapBar", target.mapBar)
                .put("petrolMs", target.petrolMs)
                .put("gasMs", target.gasMs)
                .put("ratio", target.ratio)
                .put("weight", target.weight)
                .put("robustWeight", target.robustWeight)
                .put("targetFactor", kotlin.math.exp(target.logTarget))
        })
        val rejected = JSONArray(result.rejectedBands.map { band ->
            JSONObject().put("fuel", band.fuel).put("band", band.band).put("mapBar", band.mapBar).put("timeMs", band.timeMs)
        })
        return base
            .put("ok", true)
            .put("available", true)
            .put("refinementMode", result.mode.name)
            .put("equivalenceAvailable", result.equivalenceAvailable)
            .put("reason", result.reason ?: JSONObject.NULL)
            // Sem evidência suficiente a proposta está ausente: pontos = curva atual, changedCount 0.
            .put("proposalAvailable", result.equivalenceAvailable)
            .put("message", result.message ?: JSONObject.NULL)
            .put("invalidEvidenceBands", result.invalidEvidenceBands)
            .put("thinBandsIgnored", result.thinBandsIgnored)
            .put("telemetryOutlierBands", result.telemetryOutlierBands)
            .put("telemetryPairsUsed", result.telemetryPairsUsed)
            .put("outOfRangePoints", result.outOfRangePoints)
            .put("buffersCoherent", buffersCoherent)
            .put("matureCommonPoints", result.matureCommonPoints)
            .put("telemetryTargets", result.telemetryTargetCount)
            .put("telemetryOnly", result.telemetryOnly)
            .put("evidenceSource", when {
                !result.equivalenceAvailable -> "NENHUMA"
                result.telemetryOnly -> "CONDUCAO"
                else -> "ECU_E_CONDUCAO"
            })
            .put("minimumMatureCommonPoints", AutoMatchRefinedEngine.MIN_COMMON_MATURE)
            .put("elasticityLimit", result.elasticityLimit)
            .put("needsAnotherPass", result.needsAnotherPass)
            .put("changedCount", points.let { array -> (0 until array.length()).count { array.getJSONObject(it).optInt("calculatedRaw") != array.getJSONObject(it).optInt("currentRaw") } })
            .put("guards", JSONObject()
                .put("maximumStepPercent", 15.0)
                .put("maximumElasticity", AutoMatchRefinedEngine.E_MAX)
                .put("minimumFactor", AutoMatchRefinedEngine.MIN_FACTOR)
                .put("maximumFactor", AutoMatchRefinedEngine.MAX_FACTOR)
                .put("lowGuardMs", LOW_GUARD_MS))
            .put("evidenceErrorBefore", result.evidenceErrorBefore ?: JSONObject.NULL)
            .put("evidenceErrorAfter", result.evidenceErrorAfter ?: JSONObject.NULL)
            .put("joltRiskBefore", joltRisk(result.metricsBefore?.maxElasticity))
            .put("joltRiskAfter", joltRisk(result.metricsAfter?.maxElasticity))
            .put("metricsBefore", metricsJson(result.metricsBefore))
            .put("metricsAfter", metricsJson(result.metricsAfter))
            .put("targets", targets)
            .put("rejectedBands", rejected)
            .put("points", points)
    }

    /**
     * Linearidade da puxada pela inclinação |d ln K / d ln t|: acima de [AutoMatchRefinedEngine.E_MAX]
     * o gás entregue deixa de acompanhar linearmente o pedido da gasolina. O limite foi
     * escolhido pelo teste cego de telemetria (tools/autocal_refine/blind_telemetry_test.py).
     */
    private fun joltRisk(elasticity: Double?): String = when {
        elasticity == null -> "UNKNOWN"
        elasticity <= AutoMatchRefinedEngine.E_MAX + 0.02 -> "LOW"
        elasticity <= 1.0 -> "ATTENTION"
        else -> "HIGH"
    }

    private fun metricsJson(metrics: AutoMatchRefinedEngine.Metrics?): Any = metrics?.let {
        JSONObject()
            .put("maxNeighborStep", it.maxNeighborStep)
            .put("maxElasticity", it.maxElasticity)
            .put("roughness", it.roughness)
            .put("slopeSignChanges", it.slopeSignChanges)
    } ?: JSONObject.NULL

    const val REFINED_MODE = "EQUIVALENCIA_REFINADA_V1"

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
