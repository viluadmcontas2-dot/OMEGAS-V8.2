package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converte um full snapshot AutoCal já materializado na projeção humana canônica.
 * Não realiza I/O, não agenda trabalho e não interpreta vetores de 30 pontos como aquisição.
 */
object NativeAutoCalSnapshotHumanProjector {
    fun project(snapshot: JSONObject, autoMatchRevalidating: Boolean = false): JSONObject {
        val progression = NativeAutoCalProgression.evaluate(
            petrolCounters = vector(snapshot, "NUM_BUF_UPD_PETR"),
            petrolTimes = vector(snapshot, "PETR_INJ_TBUF"),
            petrolMaps = vector(snapshot, "MNFLD_PRESS_BUF"),
            petrolZoneFlags = vector(snapshot, "ACQUIRED_ZONES_PETROL"),
            gasCounters = vector(snapshot, "NUM_BUF_UPD_GAS"),
            gasTimes = vector(snapshot, "PETR_INJ_TBUF_GAS"),
            gasMaps = vector(snapshot, "MNFLD_PRESS_BUF_GAS"),
            gasZoneFlags = vector(snapshot, "ACQUIRED_ZONES_GAS"),
            previousGasTimes = vector(snapshot, "PETR_INJ_TBUF_GAS_PREV"),
            previousGasMaps = vector(snapshot, "MNFLD_PRESS_BUF_GAS_PREV"),
            reference30 = linkedMapOf(
                "PETR_INJ_TBP" to vector(snapshot, "PETR_INJ_TBP"),
                "MUL_ACT" to vector(snapshot, "MUL_ACT"),
                "PETR_MNFLD_PRESS_RV" to vector(snapshot, "PETR_MNFLD_PRESS_RV"),
                "GAS_MNFLD_PRESS_RV" to vector(snapshot, "GAS_MNFLD_PRESS_RV"),
            ),
        )
        return NativeAutoCalHumanProjection.project(
            snapshot = progression,
            autoMatchRevalidating = autoMatchRevalidating,
            correlatedBandKeys = correlatedBandKeys(snapshot.optJSONArray("nativeMaturityEvents") ?: JSONArray()),
        ).toJson()
    }

    private fun vector(snapshot: JSONObject, key: String): IntArray? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") != key || field.optString("status") != AutoCalFieldStatus.VALID.name) return@repeat
            val values = field.optJSONArray("rawValues") ?: return null
            return IntArray(values.length()) { values.optInt(it) }
        }
        return null
    }

    private fun correlatedBandKeys(events: JSONArray): Set<String> = buildSet {
        repeat(events.length()) { index ->
            val event = events.optJSONObject(index) ?: return@repeat
            if (event.optString("correlationState") != "CORRELATED") return@repeat
            val band = event.optInt("bandIndex", -1)
            if (band !in 0 until NativeAutoCalProgression.ACQUISITION_BANDS) return@repeat
            when (event.optString("sourceFuel").uppercase()) {
                "PETROL", "GASOLINA" -> add("PETROL:$band")
                "GNV", "CNG", "GAS" -> add("GAS:$band")
            }
        }
    }
}
