package com.omegas.prohub.learning

import android.content.Context
import org.json.JSONObject

/** Parâmetros da única lógica de aprendizado V6. */
data class LearningTolerancePolicy(
    val requiredFrames: Int = 10,
    val evaluationStride: Int = 3,
    val maximumAttemptMs: Long = 3_000L,
    val warningGapMs: Long = 300L,
    val breakingGapMs: Long = 900L,
    val toleratedSerialFailures: Int = 3,
    val hardRecoveryFailures: Int = 10,
    val hardRecoverySilenceMs: Long = 1_800L,
    val rpmCenterMinimum: Double = 20.0,
    val rpmCenterPercent: Double = 1.0,
    val rpmOscillationMinimum: Double = 40.0,
    val rpmOscillationPercent: Double = 1.5,
    val mapCenterBar: Double = 0.020,
    val mapOscillationBar: Double = 0.035,
    val petrolCenterMinimumMs: Double = 0.15,
    val petrolCenterPercent: Double = 6.0,
    val petrolOscillationPercent: Double = 10.0,
    val strongPetrolOscillationPercent: Double = 8.0,
    val pressureCenterBar: Double = 0.025,
    val pressureOscillationBar: Double = 0.040,
    val cutoffMinimumRpm: Int = 1_200,
    val cutoffMaximumPetrolMs: Double = 0.70,
    val cutoffMaximumMapBar: Double = 0.35,
    val physicalExitFrames: Int = 3,
    val historicalRpmMinimum: Double = 40.0,
    val historicalRpmPercent: Double = 2.5,
    val historicalMapBar: Double = 0.030,
    val historicalTemperatureC: Double = 6.0,
    val referenceMaximumSpreadMs: Double = 0.35,
    val directionConsensusMinimum: Double = 0.75,
    val comparisonMaximumMadMs: Double = 0.25,
    val comparisonMaximumGasTempSpanC: Double = 15.0,
    val comparisonMaximumPressureSpanBar: Double = 0.08,
    val equivalenceDeadbandMs: Double = 0.12,
    val equivalenceDeadbandPercent: Double = 2.5,
    val confidenceSampleTarget: Int = 6,
    val provisionalVisits: Int = 2,
    val acceptedVisits: Int = 4,
    val confirmedVisits: Int = 6,
) {
    fun normalized(): LearningTolerancePolicy {
        val frames = requiredFrames.coerceIn(6, 30)
        val warning = warningGapMs.coerceIn(80L, 2_000L)
        val breaking = breakingGapMs.coerceIn(warning + 10L, 5_000L)
        val tolerated = toleratedSerialFailures.coerceIn(0, 12)
        val provisional = provisionalVisits.coerceIn(1, 20)
        val accepted = acceptedVisits.coerceIn(provisional, 40)
        val petrolOscillation = petrolOscillationPercent.coerceIn(2.0, 50.0)
        return copy(
            requiredFrames = frames,
            evaluationStride = evaluationStride.coerceIn(1, minOf(12, frames)),
            maximumAttemptMs = maximumAttemptMs.coerceIn(maxOf(400L, breaking), 10_000L),
            warningGapMs = warning,
            breakingGapMs = breaking,
            toleratedSerialFailures = tolerated,
            hardRecoveryFailures = hardRecoveryFailures.coerceIn(tolerated + 1, 30),
            hardRecoverySilenceMs = hardRecoverySilenceMs.coerceIn(500L, 15_000L),
            rpmCenterMinimum = rpmCenterMinimum.coerceIn(10.0, 500.0),
            rpmCenterPercent = rpmCenterPercent.coerceIn(0.5, 20.0),
            rpmOscillationMinimum = rpmOscillationMinimum.coerceIn(20.0, 1_000.0),
            rpmOscillationPercent = rpmOscillationPercent.coerceIn(1.0, 35.0),
            mapCenterBar = mapCenterBar.coerceIn(0.005, 0.25),
            mapOscillationBar = mapOscillationBar.coerceIn(0.010, 0.50),
            petrolCenterMinimumMs = petrolCenterMinimumMs.coerceIn(0.02, 1.5),
            petrolCenterPercent = petrolCenterPercent.coerceIn(1.0, 30.0),
            petrolOscillationPercent = petrolOscillation,
            strongPetrolOscillationPercent = strongPetrolOscillationPercent.coerceIn(1.0, petrolOscillation),
            pressureCenterBar = pressureCenterBar.coerceIn(0.005, 0.30),
            pressureOscillationBar = pressureOscillationBar.coerceIn(0.010, 0.60),
            cutoffMinimumRpm = cutoffMinimumRpm.coerceIn(800, 2_500),
            cutoffMaximumPetrolMs = cutoffMaximumPetrolMs.coerceIn(0.10, 1.20),
            cutoffMaximumMapBar = cutoffMaximumMapBar.coerceIn(0.15, 0.50),
            physicalExitFrames = physicalExitFrames.coerceIn(2, 12),
            historicalRpmMinimum = historicalRpmMinimum.coerceIn(20.0, 1_000.0),
            historicalRpmPercent = historicalRpmPercent.coerceIn(1.0, 35.0),
            historicalMapBar = historicalMapBar.coerceIn(0.010, 0.50),
            historicalTemperatureC = historicalTemperatureC.coerceIn(1.0, 40.0),
            referenceMaximumSpreadMs = referenceMaximumSpreadMs.coerceIn(0.05, 1.50),
            directionConsensusMinimum = directionConsensusMinimum.coerceIn(0.50, 1.0),
            comparisonMaximumMadMs = comparisonMaximumMadMs.coerceIn(0.03, 1.0),
            comparisonMaximumGasTempSpanC = comparisonMaximumGasTempSpanC.coerceIn(3.0, 50.0),
            comparisonMaximumPressureSpanBar = comparisonMaximumPressureSpanBar.coerceIn(0.03, 0.80),
            equivalenceDeadbandMs = equivalenceDeadbandMs.coerceIn(0.01, 0.50),
            equivalenceDeadbandPercent = equivalenceDeadbandPercent.coerceIn(0.2, 10.0),
            confidenceSampleTarget = confidenceSampleTarget.coerceIn(2, 100),
            provisionalVisits = provisional,
            acceptedVisits = accepted,
            confirmedVisits = confirmedVisits.coerceIn(accepted, 80),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("requiredFrames", requiredFrames)
        .put("evaluationStride", evaluationStride)
        .put("maximumAttemptMs", maximumAttemptMs)
        .put("warningGapMs", warningGapMs)
        .put("breakingGapMs", breakingGapMs)
        .put("toleratedSerialFailures", toleratedSerialFailures)
        .put("hardRecoveryFailures", hardRecoveryFailures)
        .put("hardRecoverySilenceMs", hardRecoverySilenceMs)
        .put("rpmCenterMinimum", rpmCenterMinimum)
        .put("rpmCenterPercent", rpmCenterPercent)
        .put("rpmOscillationMinimum", rpmOscillationMinimum)
        .put("rpmOscillationPercent", rpmOscillationPercent)
        .put("mapCenterBar", mapCenterBar)
        .put("mapOscillationBar", mapOscillationBar)
        .put("petrolCenterMinimumMs", petrolCenterMinimumMs)
        .put("petrolCenterPercent", petrolCenterPercent)
        .put("petrolOscillationPercent", petrolOscillationPercent)
        .put("strongPetrolOscillationPercent", strongPetrolOscillationPercent)
        .put("pressureCenterBar", pressureCenterBar)
        .put("pressureOscillationBar", pressureOscillationBar)
        .put("cutoffMinimumRpm", cutoffMinimumRpm)
        .put("cutoffMaximumPetrolMs", cutoffMaximumPetrolMs)
        .put("cutoffMaximumMapBar", cutoffMaximumMapBar)
        .put("physicalExitFrames", physicalExitFrames)
        .put("historicalRpmMinimum", historicalRpmMinimum)
        .put("historicalRpmPercent", historicalRpmPercent)
        .put("historicalMapBar", historicalMapBar)
        .put("historicalTemperatureC", historicalTemperatureC)
        .put("referenceMaximumSpreadMs", referenceMaximumSpreadMs)
        .put("directionConsensusMinimum", directionConsensusMinimum)
        .put("comparisonMaximumMadMs", comparisonMaximumMadMs)
        .put("comparisonMaximumGasTempSpanC", comparisonMaximumGasTempSpanC)
        .put("comparisonMaximumPressureSpanBar", comparisonMaximumPressureSpanBar)
        .put("equivalenceDeadbandMs", equivalenceDeadbandMs)
        .put("equivalenceDeadbandPercent", equivalenceDeadbandPercent)
        .put("confidenceSampleTarget", confidenceSampleTarget)
        .put("provisionalVisits", provisionalVisits)
        .put("acceptedVisits", acceptedVisits)
        .put("confirmedVisits", confirmedVisits)
        .put("minimumWaterC", LearningTemperatureSettings.currentMinimumWaterC)

    companion object {
        fun fromJson(raw: JSONObject, fallback: LearningTolerancePolicy = LearningTolerancePolicy()): LearningTolerancePolicy =
            LearningTolerancePolicy(
                requiredFrames = raw.optInt("requiredFrames", fallback.requiredFrames),
                evaluationStride = raw.optInt("evaluationStride", fallback.evaluationStride),
                maximumAttemptMs = raw.optLong("maximumAttemptMs", fallback.maximumAttemptMs),
                warningGapMs = raw.optLong("warningGapMs", fallback.warningGapMs),
                breakingGapMs = raw.optLong("breakingGapMs", fallback.breakingGapMs),
                toleratedSerialFailures = raw.optInt("toleratedSerialFailures", fallback.toleratedSerialFailures),
                hardRecoveryFailures = raw.optInt("hardRecoveryFailures", fallback.hardRecoveryFailures),
                hardRecoverySilenceMs = raw.optLong("hardRecoverySilenceMs", fallback.hardRecoverySilenceMs),
                rpmCenterMinimum = raw.optDouble("rpmCenterMinimum", fallback.rpmCenterMinimum),
                rpmCenterPercent = raw.optDouble("rpmCenterPercent", fallback.rpmCenterPercent),
                rpmOscillationMinimum = raw.optDouble("rpmOscillationMinimum", fallback.rpmOscillationMinimum),
                rpmOscillationPercent = raw.optDouble("rpmOscillationPercent", fallback.rpmOscillationPercent),
                mapCenterBar = raw.optDouble("mapCenterBar", fallback.mapCenterBar),
                mapOscillationBar = raw.optDouble("mapOscillationBar", fallback.mapOscillationBar),
                petrolCenterMinimumMs = raw.optDouble("petrolCenterMinimumMs", fallback.petrolCenterMinimumMs),
                petrolCenterPercent = raw.optDouble("petrolCenterPercent", fallback.petrolCenterPercent),
                petrolOscillationPercent = raw.optDouble("petrolOscillationPercent", fallback.petrolOscillationPercent),
                strongPetrolOscillationPercent = raw.optDouble("strongPetrolOscillationPercent", fallback.strongPetrolOscillationPercent),
                pressureCenterBar = raw.optDouble("pressureCenterBar", fallback.pressureCenterBar),
                pressureOscillationBar = raw.optDouble("pressureOscillationBar", fallback.pressureOscillationBar),
                cutoffMinimumRpm = raw.optInt("cutoffMinimumRpm", fallback.cutoffMinimumRpm),
                cutoffMaximumPetrolMs = raw.optDouble("cutoffMaximumPetrolMs", fallback.cutoffMaximumPetrolMs),
                cutoffMaximumMapBar = raw.optDouble("cutoffMaximumMapBar", fallback.cutoffMaximumMapBar),
                physicalExitFrames = raw.optInt("physicalExitFrames", fallback.physicalExitFrames),
                historicalRpmMinimum = raw.optDouble("historicalRpmMinimum", fallback.historicalRpmMinimum),
                historicalRpmPercent = raw.optDouble("historicalRpmPercent", fallback.historicalRpmPercent),
                historicalMapBar = raw.optDouble("historicalMapBar", fallback.historicalMapBar),
                historicalTemperatureC = raw.optDouble("historicalTemperatureC", fallback.historicalTemperatureC),
                referenceMaximumSpreadMs = raw.optDouble("referenceMaximumSpreadMs", fallback.referenceMaximumSpreadMs),
                directionConsensusMinimum = raw.optDouble("directionConsensusMinimum", fallback.directionConsensusMinimum),
                comparisonMaximumMadMs = raw.optDouble("comparisonMaximumMadMs", fallback.comparisonMaximumMadMs),
                comparisonMaximumGasTempSpanC = raw.optDouble("comparisonMaximumGasTempSpanC", fallback.comparisonMaximumGasTempSpanC),
                comparisonMaximumPressureSpanBar = raw.optDouble("comparisonMaximumPressureSpanBar", fallback.comparisonMaximumPressureSpanBar),
                equivalenceDeadbandMs = raw.optDouble("equivalenceDeadbandMs", fallback.equivalenceDeadbandMs),
                equivalenceDeadbandPercent = raw.optDouble("equivalenceDeadbandPercent", fallback.equivalenceDeadbandPercent),
                confidenceSampleTarget = raw.optInt("confidenceSampleTarget", fallback.confidenceSampleTarget),
                provisionalVisits = raw.optInt("provisionalVisits", fallback.provisionalVisits),
                acceptedVisits = raw.optInt("acceptedVisits", fallback.acceptedVisits),
                confirmedVisits = raw.optInt("confirmedVisits", fallback.confirmedVisits),
            ).normalized()
    }
}

class LearningToleranceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("omegas_learning_v5", Context.MODE_PRIVATE)
    private val temperature = LearningTemperatureSettings(context)

    init {
        current = try {
            val saved = prefs.getString(PREF_KEY, null)
            if (saved.isNullOrBlank()) {
                LearningTolerancePolicy()
            } else {
                val loaded = LearningTolerancePolicy.fromJson(JSONObject(saved))
                val migrated = migrateLegacyCollectionWindow(loaded)
                if (migrated != loaded) {
                    prefs.edit().putString(PREF_KEY, migrated.toJson().toString()).apply()
                }
                migrated
            }
        } catch (_: Exception) {
            LearningTolerancePolicy()
        }
    }

    fun update(payload: JSONObject): LearningTolerancePolicy {
        val semantic = payload.optJSONObject("semanticControls")
        val applied = if (semantic != null) {
            val result = LearningControlModel.apply(semantic, current)
            temperature.setMinimumWaterC(result.minimumWaterC)
            result.policy
        } else {
            LearningTolerancePolicy.fromJson(payload, current)
        }
        prefs.edit().putString(PREF_KEY, applied.toJson().toString()).apply()
        current = applied
        return applied
    }

    fun reset(): LearningTolerancePolicy {
        prefs.edit().remove(PREF_KEY).apply()
        current = LearningTolerancePolicy()
        return current
    }

    fun toJson(): JSONObject = JSONObject()
        .put("ok", true)
        .put("policy", current.toJson())
        .put("defaults", LearningTolerancePolicy().toJson())
        .put("controlModel", LearningControlModel.describe(current, temperature.minimumWaterC()))
        .put("safetyLocks", JSONObject()
            .put("fuelSeparation", true)
            .put("cutoffAlwaysInvalidatesWindow", true)
            .put("plannedOperationsAlwaysResetWindow", true)
            .put("ackAndReadbackRequired", true)
            .put("automaticCalibration", false))

    companion object {
        private const val PREF_KEY = "learning_tolerance_policy_v5"
        @Volatile var current: LearningTolerancePolicy = LearningTolerancePolicy()

        /** Amplia somente os pares exatos da política antiga; personalizações ficam intactas. */
        internal fun migrateLegacyCollectionWindow(policy: LearningTolerancePolicy): LearningTolerancePolicy {
            val migratedMs = when (policy.requiredFrames to policy.maximumAttemptMs) {
                18 to 2_500L -> 6_000L
                14 to 2_000L -> 4_500L
                10 to 1_500L -> 3_000L
                8 to 1_250L -> 2_000L
                6 to 1_000L -> 1_600L
                else -> policy.maximumAttemptMs
            }
            return if (migratedMs == policy.maximumAttemptMs) policy else policy.copy(maximumAttemptMs = migratedMs)
        }
    }
}
