package com.omegas.prohub.migration

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object OmegasMigrationService {

    fun migrate(rawJson: String): JSONObject {
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return JSONObject().put("ok", false).put("error", "Arquivo corrompido ou JSON inválido")
        }

        val out = JSONObject()
        out.put("ok", true)
        out.put("format", "omegas-learning-v5")

        val epoch = root.optInt("epoch", 1).coerceAtLeast(1)
        out.put("epoch", epoch)
        out.put("mapHash", root.optString("mapHash", root.optString("map_hash", "")))

        val newRegions = JSONArray()
        val oldRegions = root.optJSONArray("regions") ?: JSONArray()
        
        for (i in 0 until oldRegions.length()) {
            val oldRegion = oldRegions.optJSONObject(i) ?: continue
            val newRegion = JSONObject()
            newRegion.put("id", oldRegion.optString("id", UUID.randomUUID().toString()))
            
            val fuel = oldRegion.optString("fuel", "PETROL")
            newRegion.put("fuel", fuel)
            newRegion.put("epoch", if (fuel == "PETROL") 0 else oldRegion.optInt("epoch", epoch).coerceAtLeast(1))
            
            newRegion.put("rpm", oldRegion.optDouble("rpm", oldRegion.optDouble("rpmMean", 0.0)))
            newRegion.put("map_bar", oldRegion.optDouble("map_bar", oldRegion.optDouble("mapBar", oldRegion.optDouble("mapMean", 0.0))))
            
            val petrolMs = oldRegion.optDouble("petrol_ms", oldRegion.optDouble("petrolMs", oldRegion.optDouble("petrolMean", 0.0)))
            newRegion.put("petrol_ms", petrolMs)
            
            val petrolSquared = oldRegion.optDouble("petrol_squared_mean", oldRegion.optDouble("petrolSquaredMean", petrolMs * petrolMs))
            newRegion.put("petrol_squared_mean", petrolSquared)
            
            newRegion.put("pressure_diff_bar", oldRegion.optDouble("pressure_diff_bar", oldRegion.optDouble("pressureDiffBar", oldRegion.optDouble("pressureMean", 0.0))))
            newRegion.put("water_c", oldRegion.optDouble("water_c", oldRegion.optDouble("waterC", oldRegion.optDouble("waterMean", 0.0))))
            newRegion.put("gas_c", oldRegion.optDouble("gas_c", oldRegion.optDouble("gasC", oldRegion.optDouble("gasMean", 0.0))))
            
            val quality = oldRegion.optDouble("quality", oldRegion.optDouble("qualityMean", 0.5))
            newRegion.put("quality", quality)
            
            newRegion.put("weight", oldRegion.optDouble("weight", 0.0))
            newRegion.put("samples", oldRegion.optInt("samples", oldRegion.optInt("sampleCount", 0)))
            
            newRegion.put("visits", oldRegion.optJSONArray("visits") ?: JSONArray())
            newRegion.put("sessions", oldRegion.optJSONArray("sessions") ?: JSONArray())
            newRegion.put("visit_count", oldRegion.optInt("visit_count", oldRegion.optInt("visitCount", 0)))
            newRegion.put("session_count", oldRegion.optInt("session_count", oldRegion.optInt("sessionCount", 0)))
            newRegion.put("updated_at", oldRegion.optLong("updated_at", oldRegion.optLong("updatedAt", 0L)))
            
            newRegions.put(newRegion)
        }
        
        out.put("regions", newRegions)
        
        val newSessions = JSONArray()
        val oldSessions = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until oldSessions.length()) {
            val oldSession = oldSessions.optJSONObject(i) ?: continue
            val newSession = JSONObject()
            newSession.put("id", oldSession.optString("id", UUID.randomUUID().toString()))
            newSession.put("started_at", oldSession.optLong("started_at", oldSession.optLong("startedAt", 0L)))
            newSession.put("ended_at", oldSession.optLong("ended_at", oldSession.optLong("endedAt", 0L)))
            newSession.put("updated_at", oldSession.optLong("updated_at", oldSession.optLong("updatedAt", 0L)))
            newSession.put("samples", oldSession.optInt("samples", oldSession.optInt("sampleCount", 0)))
            newSession.put("fuels", oldSession.optJSONArray("fuels") ?: JSONArray())
            newSession.put("end_reason", oldSession.optString("end_reason", oldSession.optString("endReason", "RESTORED")))
            newSessions.put(newSession)
        }
        out.put("sessions", newSessions)
        
        // Explicitly set empty arrays for computed states to ignore legacy conclusions
        out.put("comparisons", JSONArray())
        out.put("revalidation", JSONObject())
        
        return out
    }
}
