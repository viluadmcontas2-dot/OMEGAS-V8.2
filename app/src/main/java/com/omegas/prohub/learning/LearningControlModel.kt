package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Camada didática dos controles de aprendizado.
 *
 * A interface escolhe níveis semânticos. Todos os valores técnicos e perfis são
 * calculados aqui em Kotlin e persistidos pela política nativa.
 */
object LearningControlModel {
    private val levelNames = listOf(
        "Muito rigoroso",
        "Rigoroso",
        "Equilibrado",
        "Flexível",
        "Muito flexível",
    )
    private val multipliers = doubleArrayOf(0.65, 0.82, 1.0, 1.25, 1.55)

    fun describe(policy: LearningTolerancePolicy, minimumWaterC: Int): JSONObject = JSONObject()
        .put("ok", true)
        .put("minimumWaterC", minimumWaterC)
        .put("levels", JSONArray(levelNames))
        .put("controls", JSONArray()
            .put(control("rpm", "Estabilidade da rotação", "Quanto a rotação pode variar durante uma medição.", inferRpm(policy), rpmValues(policy)))
            .put(control("map", "Estabilidade da carga", "Quanto o MAP pode variar durante uma medição.", inferLevel(policy.mapOscillationBar, LearningTolerancePolicy().mapOscillationBar), mapValues(policy)))
            .put(control("petrol", "Estabilidade do Petrol Inj.", "Quanto o tempo comandado pela ECU pode oscilar.", inferLevel(policy.petrolOscillationPercent, LearningTolerancePolicy().petrolOscillationPercent), petrolValues(policy)))
            .put(control("pressure", "Estabilidade da pressão GNV", "Quanto a pressão diferencial pode variar.", inferLevel(policy.pressureOscillationBar, LearningTolerancePolicy().pressureOscillationBar), pressureValues(policy)))
            .put(control("collection", "Ritmo da coleta", "Quanto tempo o aplicativo observa antes de formar uma evidência.", inferCollection(policy), collectionValues(policy))))
        .put("advanced", policy.toJson())
        .put("automaticCalibration", false)

    fun apply(payload: JSONObject, base: LearningTolerancePolicy): AppliedControls {
        val rpmLevel = level(payload, "rpm")
        val mapLevel = level(payload, "map")
        val petrolLevel = level(payload, "petrol")
        val pressureLevel = level(payload, "pressure")
        val collectionLevel = level(payload, "collection")
        val defaults = LearningTolerancePolicy()

        val rpmMultiplier = multipliers[rpmLevel]
        val mapMultiplier = multipliers[mapLevel]
        val petrolMultiplier = multipliers[petrolLevel]
        val pressureMultiplier = multipliers[pressureLevel]

        val frames = intArrayOf(18, 14, defaults.requiredFrames, 8, 6)[collectionLevel]
        val stride = intArrayOf(5, 4, defaults.evaluationStride, 2, 1)[collectionLevel]
        // O orçamento precisa comportar a cadência física observada (p95 perto
        // de 350 ms). Ele é um timeout auditável, não uma tesoura que remove
        // silenciosamente o início da janela.
        val maximumAttempt = longArrayOf(6_000L, 4_500L, defaults.maximumAttemptMs, 2_000L, 1_600L)[collectionLevel]

        val policy = base.copy(
            requiredFrames = frames,
            evaluationStride = stride,
            maximumAttemptMs = maximumAttempt,
            rpmCenterMinimum = defaults.rpmCenterMinimum * rpmMultiplier,
            rpmCenterPercent = defaults.rpmCenterPercent * rpmMultiplier,
            rpmOscillationMinimum = defaults.rpmOscillationMinimum * rpmMultiplier,
            rpmOscillationPercent = defaults.rpmOscillationPercent * rpmMultiplier,
            mapCenterBar = defaults.mapCenterBar * mapMultiplier,
            mapOscillationBar = defaults.mapOscillationBar * mapMultiplier,
            petrolCenterMinimumMs = defaults.petrolCenterMinimumMs * petrolMultiplier,
            petrolCenterPercent = defaults.petrolCenterPercent * petrolMultiplier,
            petrolOscillationPercent = defaults.petrolOscillationPercent * petrolMultiplier,
            strongPetrolOscillationPercent = (defaults.strongPetrolOscillationPercent * petrolMultiplier)
                .coerceAtMost(defaults.petrolOscillationPercent * petrolMultiplier),
            pressureCenterBar = defaults.pressureCenterBar * pressureMultiplier,
            pressureOscillationBar = defaults.pressureOscillationBar * pressureMultiplier,
        ).normalized()

        val water = payload.optInt("minimumWaterC", LearningTemperatureSettings.currentMinimumWaterC)
            .coerceIn(LearningTemperatureSettings.MIN_ALLOWED_C, LearningTemperatureSettings.MAX_ALLOWED_C)
        return AppliedControls(policy, water)
    }

    private fun level(payload: JSONObject, key: String): Int = payload.optInt(key, 2).coerceIn(0, 4)

    private fun inferRpm(policy: LearningTolerancePolicy): Int {
        val defaults = LearningTolerancePolicy()
        val ratios = listOf(
            policy.rpmCenterMinimum / defaults.rpmCenterMinimum,
            policy.rpmCenterPercent / defaults.rpmCenterPercent,
            policy.rpmOscillationMinimum / defaults.rpmOscillationMinimum,
            policy.rpmOscillationPercent / defaults.rpmOscillationPercent,
        )
        return nearestLevel(ratios.average())
    }

    private fun inferCollection(policy: LearningTolerancePolicy): Int {
        val options = intArrayOf(18, 14, LearningTolerancePolicy().requiredFrames, 8, 6)
        return options.indices.minByOrNull { abs(options[it] - policy.requiredFrames) } ?: 2
    }

    private fun inferLevel(value: Double, default: Double): Int = nearestLevel(value / default)

    private fun nearestLevel(ratio: Double): Int = multipliers.indices
        .minByOrNull { abs(multipliers[it] - ratio) } ?: 2

    private fun control(
        id: String,
        title: String,
        description: String,
        selected: Int,
        actualValues: JSONObject,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("selected", selected)
        .put("selectedLabel", levelNames[selected])
        .put("actualValues", actualValues)

    private fun rpmValues(policy: LearningTolerancePolicy): JSONObject = JSONObject()
        .put("centerMinimumRpm", policy.rpmCenterMinimum)
        .put("centerPercent", policy.rpmCenterPercent)
        .put("oscillationMinimumRpm", policy.rpmOscillationMinimum)
        .put("oscillationPercent", policy.rpmOscillationPercent)

    private fun mapValues(policy: LearningTolerancePolicy): JSONObject = JSONObject()
        .put("centerBar", policy.mapCenterBar)
        .put("oscillationBar", policy.mapOscillationBar)

    private fun petrolValues(policy: LearningTolerancePolicy): JSONObject = JSONObject()
        .put("centerMinimumMs", policy.petrolCenterMinimumMs)
        .put("centerPercent", policy.petrolCenterPercent)
        .put("oscillationPercent", policy.petrolOscillationPercent)

    private fun pressureValues(policy: LearningTolerancePolicy): JSONObject = JSONObject()
        .put("centerBar", policy.pressureCenterBar)
        .put("oscillationBar", policy.pressureOscillationBar)

    private fun collectionValues(policy: LearningTolerancePolicy): JSONObject = JSONObject()
        .put("minimumFrames", AdaptiveSampleWindow.minimumFrames(policy.requiredFrames))
        .put("desiredFrames", policy.requiredFrames)
        .put("requiredFrames", policy.requiredFrames)
        .put("evaluationStride", 1)
        .put("evaluationMode", "EVERY_FRAME_AFTER_MINIMUM")
        .put("maximumAttemptMs", policy.maximumAttemptMs)
}

data class AppliedControls(
    val policy: LearningTolerancePolicy,
    val minimumWaterC: Int,
)
