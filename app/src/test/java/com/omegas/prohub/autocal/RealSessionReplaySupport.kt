package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Sessões reais do proprietário (fixtures/autocal/real, formato omegas-autocal-replay-v1,
 * extraídas das sessões do Drive por tools/autocal_refine/extract_session.py).
 * Classe 3 de prova: replay determinístico de corpus real, sem binário proprietário.
 */
object RealSessionReplaySupport {
    const val AUTOMATCH = "automatch_2026-10-01_1301"
    const val GNV_ONLY = "gnv_only_2026-09-30_0931"
    const val REFERENCE = "ref_2026-10-01_1719"

    data class Telemetry(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double, val gasMs: Double)

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
                fuel = f.optString("fuel", "DESCONHECIDO"),
                rpm = f.optDouble("rpm", 0.0).takeIf { it.isFinite() } ?: 0.0,
                map = f.optDouble("load_bar", 0.0).takeIf { it.isFinite() } ?: 0.0,
                petrolMs = f.optDouble("petrol_ms", 0.0).takeIf { it.isFinite() } ?: 0.0,
                gasMs = f.optDouble("gas_ms_diagnostic", 0.0).takeIf { it.isFinite() } ?: 0.0,
            )
        }
    }

    fun snapshot(root: JSONObject, sequence: Int): JSONObject {
        val snapshots = root.getJSONArray("snapshots")
        for (index in 0 until snapshots.length()) {
            val snap = snapshots.getJSONObject(index)
            if (snap.getInt("sequence") == sequence) return snap
        }
        error("snapshot $sequence ausente")
    }

    fun snapshots(root: JSONObject): List<JSONObject> {
        val array = root.getJSONArray("snapshots")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    fun rawValues(snapshot: JSONObject, key: String): IntArray? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            if (field.optString("key") == key && field.optString("status") == "VALID") {
                val raw: JSONArray = field.optJSONArray("rawValues") ?: return null
                return IntArray(raw.length()) { raw.getInt(it) }
            }
        }
        return null
    }

    fun recordedAtMs(snapshot: JSONObject): Long =
        java.time.Instant.parse(snapshot.getString("recordedAtUtc")).toEpochMilli()

    fun ledgerFrame(f: Telemetry) = EquivalenceLedger.Frame(f.t, f.fuel, f.rpm, f.map, f.petrolMs, f.gasMs)

    fun stallFrame(f: Telemetry) = StallWatch.Frame(f.t, f.fuel, f.rpm, f.map, f.petrolMs)

    /** Pontos de aquisição da ECU como o piloto os recebe (AutoCalAcquisition sobre o snapshot). */
    fun acquisition(snapshot: JSONObject): JSONObject = AutoCalAcquisition.fromSnapshot(snapshot)
}
