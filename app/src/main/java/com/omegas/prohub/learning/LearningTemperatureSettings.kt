package com.omegas.prohub.learning

import android.content.Context
import org.json.JSONObject

/**
 * Critério térmico do aprendizado baseado exclusivamente na temperatura informada
 * pela ECU Landi. A temperatura OBD permanece uma fonte separada e opcional.
 */
class LearningTemperatureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("omegas_learning_native", Context.MODE_PRIVATE)

    companion object {
        const val MIN_ALLOWED_C = 40
        const val MAX_ALLOWED_C = 80
        const val DEFAULT_C = 55

        @Volatile
        var currentMinimumWaterC: Int = DEFAULT_C
            private set
    }

    init {
        currentMinimumWaterC = prefs.getInt("minimumLandiWaterC", DEFAULT_C)
            .coerceIn(MIN_ALLOWED_C, MAX_ALLOWED_C)
    }

    fun minimumWaterC(): Int = currentMinimumWaterC

    fun setMinimumWaterC(value: Int): Int {
        val applied = value.coerceIn(MIN_ALLOWED_C, MAX_ALLOWED_C)
        prefs.edit().putInt("minimumLandiWaterC", applied).apply()
        currentMinimumWaterC = applied
        return applied
    }

    fun toJson(): JSONObject = JSONObject()
        .put("ok", true)
        .put("source", "LANDI_ECU")
        .put("minimumWaterC", currentMinimumWaterC)
        .put("minimumAllowedC", MIN_ALLOWED_C)
        .put("maximumAllowedC", MAX_ALLOWED_C)
        .put("obdIndependent", true)
}

