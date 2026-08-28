package com.omegas.prohub.telemetry

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class ConsumptionTracker(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gnv_consumption", Context.MODE_PRIVATE)

    var remainingM3: Double = prefs.getFloat("remaining_m3", 0f).toDouble()
        private set

    var refuelDetected: Boolean = false
        private set
    
    @Volatile
    private var lastEngineOffPressure: Int = prefs.getInt("last_engine_off_pressure", -1)
    
    @Volatile
    private var currentFilteredPressure: Double = -1.0

    @Synchronized
    fun update(timestampMs: Long, rawPressure: Int) {
        val normalizedPressure = if (rawPressure in 0..255) 255 - rawPressure else rawPressure

        if (normalizedPressure in 0..255) {
            currentFilteredPressure = if (currentFilteredPressure < 0) normalizedPressure.toDouble()
                                      else (normalizedPressure * 0.05) + (currentFilteredPressure * 0.95)
        }

        if (currentFilteredPressure >= 0) {
            val currentPressInt = currentFilteredPressure.toInt()
            if (lastEngineOffPressure >= 0 && currentPressInt > lastEngineOffPressure + 10) {
                refuelDetected = true
            }
            lastEngineOffPressure = currentPressInt
        }
    }

    @Synchronized
    fun registerRefuel(addedM3: Double, distanceKm: Double, cylinderCapacityM3: Float): JSONObject {
        if (!addedM3.isFinite() || addedM3 <= 0.0) {
            return JSONObject().put("ok", false).put("error", "Informe volume válido")
        }
        
        var learnedCap = prefs.getFloat("learned_capacity_m3", cylinderCapacityM3).toDouble()
        val currentPressInt = currentFilteredPressure.toInt()

        if (lastEngineOffPressure in 0 until currentPressInt) {
            val deltaP = currentPressInt - lastEngineOffPressure
            if (deltaP > 10) {
                learnedCap = addedM3 / (deltaP / 255.0)
            }
        }

        refuelDetected = false
        lastEngineOffPressure = currentPressInt
        
        val newRemaining = learnedCap * (currentFilteredPressure / 255.0)
        remainingM3 = newRemaining.coerceAtLeast(0.0)

        prefs.edit()
            .putFloat("learned_capacity_m3", learnedCap.toFloat())
            .putFloat("remaining_m3", remainingM3.toFloat())
            .putInt("last_engine_off_pressure", lastEngineOffPressure)
            .apply()
            
        return JSONObject()
            .put("ok", true)
            .put("added_m3", addedM3)
            .put("learned_capacity_m3", learnedCap)
            .put("remaining_m3", remainingM3)
    }

    fun buildTelemetryJson(cylinderCapacityM3: Float): JSONObject {
        val cap = prefs.getFloat("learned_capacity_m3", cylinderCapacityM3).toDouble()
        if (currentFilteredPressure >= 0 && cap > 0) {
            remainingM3 = (cap * (currentFilteredPressure / 255.0)).coerceAtLeast(0.0)
        }

        return JSONObject()
            .put("remaining_m3", remainingM3)
            .put("refuel_detected", refuelDetected)
            .put("k_flow_active", false)
    }
}
