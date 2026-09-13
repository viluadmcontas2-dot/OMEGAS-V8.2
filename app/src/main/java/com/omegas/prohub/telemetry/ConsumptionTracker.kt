package com.omegas.prohub.telemetry

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class ConsumptionTracker(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gnv_consumption", Context.MODE_PRIVATE)

    var remainingM3: Double = prefs.getFloat("remaining_m3", -1f).toDouble()
        private set

    var refuelDetected: Boolean = false
        private set
    
    @Volatile
    private var baselineRawLevel: Int = prefs.getInt("baseline_raw_level", -1)
    
    @Volatile
    private var currentFilteredRaw: Double = -1.0

    @Synchronized
    fun update(timestampMs: Long, rawLevel: Int) {
        val normalizedRaw = rawLevel.coerceIn(0, 255)

        if (currentFilteredRaw < 0) {
            currentFilteredRaw = normalizedRaw.toDouble()
        } else {
            currentFilteredRaw = (normalizedRaw * 0.05) + (currentFilteredRaw * 0.95)
        }

        val currentRawInt = currentFilteredRaw.toInt()

        if (baselineRawLevel < 0) {
            baselineRawLevel = currentRawInt
            prefs.edit().putInt("baseline_raw_level", baselineRawLevel).apply()
        }

        // Se está esvaziando (raw aumenta), atualiza o baseline
        if (currentRawInt > baselineRawLevel) {
            baselineRawLevel = currentRawInt
            prefs.edit().putInt("baseline_raw_level", baselineRawLevel).apply()
            refuelDetected = false // se estava detectando, cancela, pois subiu de novo
        } else if (currentRawInt <= baselineRawLevel - 10) {
            // Se caiu mais de 10 unidades do baseline, detectamos abastecimento
            refuelDetected = true
        } else if (refuelDetected && currentRawInt > baselineRawLevel - 5) {
            // Se foi um pico isolado para baixo e voltou, desmarca abastecimento
            refuelDetected = false
        }
    }

    @Synchronized
    fun registerRefuel(addedM3: Double, distanceKm: Double, cylinderCapacityM3: Float): JSONObject {
        if (!addedM3.isFinite() || addedM3 <= 0.0) {
            return JSONObject().put("ok", false).put("error", "Informe volume válido")
        }
        
        var learnedCap = prefs.getFloat("learned_capacity_m3", -1f).toDouble()
        val currentRawInt = currentFilteredRaw.toInt()

        // baselineRawLevel era o nível antes de abastecer (mais vazio, maior valor)
        // currentRawInt é o nível agora (mais cheio, menor valor)
        if (baselineRawLevel > currentRawInt) {
            val deltaRaw = baselineRawLevel - currentRawInt
            if (deltaRaw > 10) {
                learnedCap = addedM3 / (deltaRaw / 255.0)
            }
        }
        
        if (learnedCap <= 0.0) {
            learnedCap = cylinderCapacityM3.toDouble()
        }

        refuelDetected = false
        baselineRawLevel = currentRawInt
        
        val remainingIndex = 1.0 - (currentFilteredRaw / 255.0).coerceIn(0.0, 1.0)
        val newRemaining = learnedCap * remainingIndex
        remainingM3 = newRemaining.coerceAtLeast(0.0)

        prefs.edit()
            .putFloat("learned_capacity_m3", learnedCap.toFloat())
            .putFloat("remaining_m3", remainingM3.toFloat())
            .putInt("baseline_raw_level", baselineRawLevel)
            .apply()
            
        return JSONObject()
            .put("ok", true)
            .put("added_m3", addedM3)
            .put("learned_capacity_m3", learnedCap)
            .put("remaining_m3", remainingM3)
    }

    fun buildTelemetryJson(cylinderCapacityM3: Float): JSONObject {
        val cap = prefs.getFloat("learned_capacity_m3", -1f).toDouble()
        
        val json = JSONObject()
        json.put("refuel_detected", refuelDetected)
        json.put("k_flow_active", false)

        if (currentFilteredRaw >= 0) {
            val emptyIndex = (currentFilteredRaw / 255.0).coerceIn(0.0, 1.0)
            val remainingIndex = 1.0 - emptyIndex
            
            json.put("level_raw", currentFilteredRaw.toInt())
            json.put("empty_index", emptyIndex)
            json.put("remaining_index", remainingIndex)

            if (cap > 0) {
                remainingM3 = (cap * remainingIndex).coerceAtLeast(0.0)
                json.put("remaining_m3", remainingM3)
            } else if (remainingM3 >= 0) {
                 json.put("remaining_m3", remainingM3)
            }
        }

        return json
    }
}
