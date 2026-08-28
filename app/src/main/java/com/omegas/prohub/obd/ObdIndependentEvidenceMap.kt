package com.omegas.prohub.obd

import com.omegas.prohub.stats.WeightedStat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Segunda prova OBD totalmente observacional.
 *
 * Os eixos e valores deste mapa vêm somente do OBD do veículo:
 * - eixo X: RPM OBD;
 * - eixo Y: carga calculada OBD (PID 0104);
 * - conteúdo: STFT/LTFT e contexto OBD.
 *
 * O rótulo Gasolina/GNV pode vir da MP48 quando disponível ou do operador,
 * mas nenhuma amostra deste mapa entra no aprendizado/calibração e nada aqui
 * chama writer, USB MP48 ou prepara alteração de Mapa K/Curva K.
 */
class ObdIndependentEvidenceMap(
    private val minimumSamplesPerCell: () -> Long,
) {
    companion object {
        val RPM_BINS = doubleArrayOf(750.0, 1000.0, 1250.0, 1500.0, 1750.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 5000.0, 6500.0)
        val LOAD_BINS = doubleArrayOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 85.0, 100.0)
    }

    data class Cell(
        val stft: WeightedStat = WeightedStat(),
        val ltft: WeightedStat = WeightedStat(),
        val speed: WeightedStat = WeightedStat(),
        val coolant: WeightedStat = WeightedStat(),
        val mapKpa: WeightedStat = WeightedStat(),
        val mafGps: WeightedStat = WeightedStat(),
        val throttle: WeightedStat = WeightedStat(),
        var samples: Long = 0,
        var updatedAt: Long = 0L,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("stft", stft.toJson())
            .put("ltft", ltft.toJson())
            .put("speed", speed.toJson())
            .put("coolant", coolant.toJson())
            .put("mapKpa", mapKpa.toJson())
            .put("mafGps", mafGps.toJson())
            .put("throttle", throttle.toJson())
            .put("samples", samples)
            .put("updatedAt", updatedAt)

        companion object {
            fun fromJson(json: JSONObject?): Cell = Cell(
                stft = WeightedStat.fromJson(json?.optJSONObject("stft")),
                ltft = WeightedStat.fromJson(json?.optJSONObject("ltft")),
                speed = WeightedStat.fromJson(json?.optJSONObject("speed")),
                coolant = WeightedStat.fromJson(json?.optJSONObject("coolant")),
                mapKpa = WeightedStat.fromJson(json?.optJSONObject("mapKpa")),
                mafGps = WeightedStat.fromJson(json?.optJSONObject("mafGps")),
                throttle = WeightedStat.fromJson(json?.optJSONObject("throttle")),
                samples = json?.optLong("samples", 0L) ?: 0L,
                updatedAt = json?.optLong("updatedAt", 0L) ?: 0L,
            )
        }
    }

    data class Location(
        val valid: Boolean,
        val row: Int = -1,
        val column: Int = -1,
        val key: String? = null,
        val rpmBin: Double? = null,
        val loadBin: Double? = null,
        val reason: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("valid", valid)
            .put("row", row)
            .put("column", column)
            .put("key", key ?: JSONObject.NULL)
            .put("rpmBin", rpmBin ?: JSONObject.NULL)
            .put("loadBin", loadBin ?: JSONObject.NULL)
            .put("reason", reason)
    }

    data class Observation(val accepted: Boolean, val reason: String, val key: String? = null)

    private val maps = mutableMapOf(
        "GASOLINA" to linkedMapOf<String, Cell>(),
        "GNV" to linkedMapOf(),
    )

    fun locate(rpm: Double?, loadPct: Double?): Location {
        if (rpm == null || !rpm.isFinite() || rpm <= 0.0) return Location(false, reason = "RPM_OBD_INVALIDO")
        if (loadPct == null || !loadPct.isFinite() || loadPct !in 0.0..100.0) return Location(false, reason = "CARGA_OBD_INVALIDA")
        val row = nearest(rpm, RPM_BINS)
        val column = nearest(loadPct, LOAD_BINS)
        return Location(
            valid = true,
            row = row,
            column = column,
            key = "$row:$column",
            rpmBin = RPM_BINS[row],
            loadBin = LOAD_BINS[column],
            reason = "OBD_RPM_X_LOAD",
        )
    }

    fun observe(
        fuel: String?,
        rpm: Double?,
        loadPct: Double?,
        stftPct: Double?,
        ltftPct: Double?,
        speedKmh: Double?,
        coolantC: Double?,
        mapKpa: Double?,
        mafGps: Double?,
        throttlePct: Double?,
        closedLoop: Boolean,
        minimumCoolantC: Double,
        nowMs: Long,
    ): Observation {
        val location = locate(rpm, loadPct)
        if (!location.valid) return Observation(false, location.reason)
        val key = location.key!!
        val normalizedFuel = normalizeFuel(fuel) ?: return Observation(false, "SEM_ROTULO_COMBUSTIVEL", key)
        if (!closedLoop) return Observation(false, "FORA_CLOSED_LOOP", key)
        if (stftPct == null || !stftPct.isFinite()) return Observation(false, "STFT_OBD_INVALIDO", key)
        if (coolantC != null && coolantC < minimumCoolantC) return Observation(false, "MOTOR_FRIO", key)

        val cell = maps.getValue(normalizedFuel).getOrPut(key) { Cell() }
        cell.stft.update(stftPct)
        if (ltftPct != null && ltftPct.isFinite()) cell.ltft.update(ltftPct)
        if (speedKmh != null && speedKmh.isFinite()) cell.speed.update(speedKmh)
        if (coolantC != null && coolantC.isFinite()) cell.coolant.update(coolantC)
        if (mapKpa != null && mapKpa.isFinite()) cell.mapKpa.update(mapKpa)
        if (mafGps != null && mafGps.isFinite()) cell.mafGps.update(mafGps)
        if (throttlePct != null && throttlePct.isFinite()) cell.throttle.update(throttlePct)
        cell.samples += 1
        cell.updatedAt = nowMs
        return Observation(true, "OBD_ONLY_ACCEPTED", key)
    }

    fun clear() {
        maps.values.forEach { it.clear() }
    }

    fun toJson(): JSONObject {
        val gasoline = mapToJson(maps.getValue("GASOLINA"))
        val gnv = mapToJson(maps.getValue("GNV"))
        return JSONObject()
            .put("source", "OBD_ONLY")
            .put("observationalOnly", true)
            .put("affectsLearning", false)
            .put("affectsCalibration", false)
            .put("axes", JSONObject()
                .put("x", "rpm")
                .put("y", "calculatedLoadPct")
                .put("rpmBins", JSONArray(RPM_BINS.toList()))
                .put("loadBins", JSONArray(LOAD_BINS.toList())))
            .put("gasoline", gasoline)
            .put("gnv", gnv)
            .put("validation", validationJson())
            .put("minimumSamplesPerCell", minimumSamplesPerCell())
            .put("updatedAt", maps.values.flatMap { it.values }.maxOfOrNull { it.updatedAt } ?: 0L)
    }

    fun persistenceJson(): JSONObject = JSONObject()
        .put("format", "omegas-obd-independent-map-v1")
        .put("gasoline", mapToJson(maps.getValue("GASOLINA")))
        .put("gnv", mapToJson(maps.getValue("GNV")))

    fun load(json: JSONObject?) {
        if (json == null) return
        maps.getValue("GASOLINA").apply {
            clear()
            putAll(jsonToMap(json.optJSONObject("gasoline")))
        }
        maps.getValue("GNV").apply {
            clear()
            putAll(jsonToMap(json.optJSONObject("gnv")))
        }
    }

    private fun validationJson(): JSONObject {
        val output = JSONObject()
        val gasoline = maps.getValue("GASOLINA")
        val gnv = maps.getValue("GNV")
        val minimum = minimumSamplesPerCell().coerceAtLeast(1L)
        (gasoline.keys + gnv.keys).forEach { key ->
            val petrol = gasoline[key]
            val gas = gnv[key]
            val petrolSamples = petrol?.samples ?: 0L
            val gasSamples = gas?.samples ?: 0L
            val gasolineStft = petrol?.stft?.mean
            val gnvStft = gas?.stft?.mean
            val comparisonReady = petrolSamples >= minimum && gasSamples >= minimum && gasolineStft != null && gnvStft != null
            output.put(
                key,
                JSONObject()
                    .put("gasolineStft", gasolineStft ?: JSONObject.NULL)
                    .put("gnvStft", gnvStft ?: JSONObject.NULL)
                    .put("deltaStft", if (comparisonReady) gnvStft!! - gasolineStft!! else JSONObject.NULL)
                    .put("gasolineSamples", petrolSamples)
                    .put("gnvSamples", gasSamples)
                    .put("comparisonReady", comparisonReady)
                    .put("sameObdCell", true)
                    .put("independentOfMp48Axes", true)
                    .put("reason", when {
                        comparisonReady -> "MESMA_CELULA_OBD_RPM_CARGA"
                        petrolSamples < minimum -> "GASOLINA_OBD_INSUFICIENTE"
                        else -> "GNV_OBD_INSUFICIENTE"
                    }),
            )
        }
        return output
    }

    private fun mapToJson(map: Map<String, Cell>): JSONObject = JSONObject().also { output ->
        map.forEach { (key, value) -> output.put(key, value.toJson()) }
    }

    private fun jsonToMap(json: JSONObject?): MutableMap<String, Cell> = linkedMapOf<String, Cell>().also { output ->
        json?.keys()?.forEach { key -> output[key] = Cell.fromJson(json.optJSONObject(key)) }
    }

    private fun normalizeFuel(value: String?): String? {
        val upper = value?.trim()?.uppercase().orEmpty()
        return when {
            upper.contains("GNV") || upper == "CNG" || upper == "GAS" -> "GNV"
            upper.contains("GASOLINA") || upper == "PETROL" -> "GASOLINA"
            else -> null
        }
    }

    private fun nearest(value: Double, bins: DoubleArray): Int = bins.indices.minByOrNull { abs(bins[it] - value) } ?: 0
}
