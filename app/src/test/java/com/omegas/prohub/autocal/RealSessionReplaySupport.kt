package com.omegas.prohub.autocal

import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Sessões reais do proprietário (fixtures/autocal/real, formato omegas-autocal-replay-v1).
 * Classe 3 de prova: replay determinístico de corpus real pelo acumulador real, sem binário proprietário.
 */
object RealSessionReplaySupport {
    const val AUTOMATCH = "automatch_2026-10-01_1301"
    const val REFERENCE = "ref_2026-10-01_1719"

    data class Telemetry(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double)

    fun fixture(name: String): JSONObject {
        val file = listOf("../fixtures/autocal/real/$name.json.gz", "fixtures/autocal/real/$name.json.gz")
            .map(::File).first { it.exists() }
        return JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() })
    }

    fun telemetry(root: JSONObject): List<Telemetry> {
        val array = root.getJSONArray("telemetry")
        return (0 until array.length()).map { i ->
            val f = array.getJSONObject(i)
            Telemetry(
                t = f.getLong("t"),
                fuel = f.optString("fuel", "DESCONHECIDO").uppercase(),
                rpm = f.optDouble("rpm", 0.0).takeIf { it.isFinite() } ?: 0.0,
                map = f.optDouble("load_bar", 0.0).takeIf { it.isFinite() } ?: 0.0,
                petrolMs = f.optDouble("petrol_ms", 0.0).takeIf { it.isFinite() } ?: 0.0,
            )
        }
    }

    fun snapshot(root: JSONObject, sequence: Int): JSONObject {
        val snapshots = root.getJSONArray("snapshots")
        for (i in 0 until snapshots.length()) snapshots.getJSONObject(i).let { if (it.getInt("sequence") == sequence) return it }
        error("snapshot $sequence ausente")
    }

    /** Instante (ms) das gravações de Curva K feitas pelo app na sessão. */
    fun appWrites(root: JSONObject): List<Long> {
        val writes = root.optJSONArray("kFactorWrites") ?: return emptyList()
        return (0 until writes.length()).map { java.time.Instant.parse(writes.getJSONObject(it).getString("recordedAtUtc")).toEpochMilli() }
    }

    fun ledgerFrame(f: Telemetry) = EquivalenceLedger.Frame(f.t, f.fuel, f.rpm, f.map, f.petrolMs)
}
