package com.omegas.dyno.lab

import org.json.JSONArray
import org.json.JSONObject

internal data class Stamp(val monoNs: Long, val wallMs: Long)
internal enum class Source(val key: String) { MP48("mp48"), OBD("obd") }

internal data class Observation(
    val source: Source, val stamp: Stamp, val values: Map<String, Any>,
    val fuel: Fuel = Fuel.UNKNOWN,
) {
    fun json(): JSONObject = JSONObject(values).put("source", source.key)
        .put("monoNs", stamp.monoNs).put("t", stamp.wallMs)
}

internal class TelemetryHub {
    private val links = mutableMapOf<Source, String>()
    private val errors = mutableMapOf<Source, String>()
    private var lastObd: Observation? = null
    private var mp48: Observation? = null
    private var fuelSince: Long? = null
    private var capturing = false
    private var vaultError: String? = null
    private val history = ArrayDeque<Observation>()
    private val driveFrames = ArrayDeque<DriveResponseAnalyzer.Frame>()
    private val driveAnalyzer = DriveResponseAnalyzer()
    private var driveResult = DriveResponseAnalyzer.Result(null, null, null, 0, "Dados insuficientes", null)
    private var lastDriveCalculationNs = 0L

    @Synchronized fun link(source: Source, state: String, error: String? = null) {
        links[source] = state
        if (error == null) errors.remove(source) else errors[source] = error
        if (state != ONLINE) {
            if (source == Source.MP48) {
                mp48 = null
                fuelSince = null
            } else {
                lastObd = null
                obdFields.clear()
            }
            driveFrames.clear()
            driveResult = DriveResponseAnalyzer.Result(null, null, null, 0, "Dados insuficientes", null)
        }
    }

    @Synchronized fun capture(active: Boolean) { capturing = active }
    @Synchronized fun vaultError(message: String?) { vaultError = message }
    @Synchronized fun status(): JSONObject = JSONObject()
        .put("capturing", capturing)
        .put("obd", links[Source.OBD] ?: UNKNOWN)
        .put("mp48", links[Source.MP48] ?: UNKNOWN)
        .put("error", (errors.values + listOfNotNull(vaultError)).joinToString(" · ").ifEmpty { UNKNOWN })

    private val obdFields = mutableMapOf<String, Observation>()

    @Synchronized fun accept(observation: Observation) {
        if (links[observation.source] != ONLINE) return
        if (observation.source == Source.MP48) {
            val previous = mp48
            if (previous == null || previous.fuel != observation.fuel ||
                observation.stamp.monoNs - previous.stamp.monoNs > MAX_AGE_NS
            ) fuelSince = observation.stamp.monoNs
            mp48 = observation
        } else {
            lastObd = observation
            for (pid in ObdPid.entries) {
                if (pid.field in observation.values) obdFields[pid.field] = observation
            }
        }
        if (observation.source == Source.OBD && observation.values["rpm"] is Number) {
            // The OBD reader emits one PID at a time: build a coherent frame only
            // when each joined signal has a fresh, independently timestamped value.
            val t = observation.stamp.monoNs
            fun reading(name: String): Double? {
                val value = obdFields[name] ?: return null
                if (t - value.stamp.monoNs !in 0L..MAX_AGE_NS) return null
                return (value.values[name] as? Number)?.toDouble()
            }
            val gas = mp48
            val fuelAge = gas?.let { (t - it.stamp.monoNs) / 1_000_000 } ?: -1L
            val fuelMode = if (gas != null && gas.fuel.verified && fuelAge in 0..1500 &&
                t >= (fuelSince ?: Long.MAX_VALUE)) {
                if (gas.fuel == Fuel.PETROL) "GASOLINA" else "GNV"
            } else UNKNOWN
            driveFrames.addLast(DriveResponseAnalyzer.Frame(
                tMs = t / 1_000_000,
                fuel = fuelMode,
                fuelAgeMs = fuelAge,
                rpm = (observation.values["rpm"] as Number).toDouble(),
                tpsPct = reading("tps"),
                mapKpa = reading("mapKpa") ?: gas?.values?.get("mapBar")?.let {
                    if (fuelAge in 0..1500) (it as? Number)?.toDouble()?.times(100.0) else null
                },
                ectC = reading("ect"),
                speedKmh = reading("speed"),
                stft = reading("stft"),
                ltft = reading("ltft"),
            ))
            while (driveFrames.size > 6000) driveFrames.removeFirst()
        }
        if (observation.values["rpm"] is Number) {
            history.addLast(observation)
            if (history.size > 150) history.removeFirst()
        }
    }

    @Synchronized fun snapshot(now: Long): JSONObject {
        fun age(observation: Observation?): Long? =
            observation?.stamp?.monoNs?.let { (now - it).takeIf { delta -> delta >= 0 }?.div(1_000_000) }
        fun fresh(observation: Observation?): Boolean =
            observation != null && now - observation.stamp.monoNs in 0L..MAX_AGE_NS

        val gas = mp48
        val gasFresh = fresh(gas)
        val gasJson = JSONObject().put("connected", links[Source.MP48] == ONLINE)
            .put("ageMs", age(gas) ?: UNKNOWN)
        for (key in listOf("rpm", "mapBar", "gasPressureBar", "petrolMs", "gasMs", "fuel")) {
            gasJson.put(key, gas?.values?.get(key)?.takeIf { gasFresh } ?: UNKNOWN)
        }
        val obd = lastObd
        val obdJson = JSONObject().put("connected", links[Source.OBD] == ONLINE)
            .put("ageMs", age(obd) ?: UNKNOWN)
        for (pid in ObdPid.entries) {
            val reading = obdFields[pid.field]
            obdJson.put(pid.field, reading?.values?.get(pid.field)?.takeIf { fresh(reading) } ?: UNKNOWN)
        }
        val obdRpm = obdFields["rpm"]
        val paired = gasFresh && gas?.fuel?.verified == true && fresh(obdRpm) &&
            obdRpm!!.stamp.monoNs >= (fuelSince ?: Long.MAX_VALUE)
        // These are acceleration response metrics, not the acquisition offset
        // between two independently polled sensors.
        if (driveFrames.isNotEmpty() && (lastDriveCalculationNs == 0L ||
                now - lastDriveCalculationNs >= 1_000_000_000L)) {
            driveResult = driveAnalyzer.analyze(driveFrames.toList())
            lastDriveCalculationNs = now
        }
        val comparison = if (paired) JSONObject().put("fuel", gas!!.fuel.label)
            .put("mp48Rpm", gas.values["rpm"]).put("obdRpm", obdRpm!!.values["rpm"]) else UNKNOWN
        val fusion = JSONObject()
            .put("quality", when {
                paired -> "pareado"
                gasFresh && gas?.fuel?.verified != true -> "combustível sem confirmação"
                gasFresh || fresh(obd) -> "parcial"
                else -> UNKNOWN
            })
            .put("confidence", driveResult.confidence ?: UNKNOWN)
            .put("lagMs", driveResult.lagMs ?: UNKNOWN)
            .put("roughness", driveResult.roughness ?: UNKNOWN)
            .put("comparison", if (driveResult.eventsMatched > 0) driveResult.comparison else comparison)
            .put("title", if (driveResult.eventsMatched > 0) "Resposta da aceleração" else UNKNOWN)
            .put("advice", driveResult.curveKAdvice ?: UNKNOWN)
            .put("matchedEvents", driveResult.eventsMatched)
        val series = JSONArray()
        for (reading in history) {
            series.put(JSONObject().put("t", reading.stamp.wallMs)
                .put("rpm", reading.values["rpm"]).put("source", reading.source.key))
        }
        return JSONObject().put("mp48", gasJson).put("obd", obdJson)
            .put("fusion", fusion).put("history", series).put("status", status())
    }

    companion object {
        const val ONLINE = "conectado"
        const val OFFLINE = "desconectado"
        private const val MAX_AGE_NS = 1_500_000_000L
    }
}
