package com.omegas.prohub.autocal

import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Replay local em memória separada. O chamador executa descoberta/leitura fora da UI e do USB. */
class RefinementWarmStart {
    data class Source(val id: String, val modified: Long, val open: () -> InputStream)
    @Volatile private var result = JSONObject().put("state", "PENDING").put("label", "Procurando memória local")
    fun status(): JSONObject = JSONObject(result.toString())

    fun run(ledger: EquivalenceLedger, journal: RefinementJournal, archives: () -> List<Source>,
            sessions: () -> List<Source>) {
        if (!ledger.isEmpty() || journal.latestId().isNotEmpty()) {
            result = JSONObject().put("state", "PRESERVED").put("label", "Memória local preservada")
            return
        }
        try {
            val restored = EquivalenceLedger()
            val history = RefinementJournal()
            val cycle = RefinementCycle(restored, history, RefinementAutopilot())
            var snapshot: JSONObject? = null
            var archiveAt = 0L
            var reads = 0
            var failures = 0
            val seen = HashSet<String>()
            archives().sortedByDescending { it.modified }.firstOrNull()?.let { source ->
                try {
                    val root = source.open().bufferedReader().use { JSONObject(it.readText()) }
                    val memory = root.optJSONObject("refinementMemory")
                    if (memory != null && restored.restoreMemory(memory.getJSONObject("ledger"))) {
                        memory.optJSONObject("journal")?.let(history::restoreMemory)
                        archiveAt = memory.optLong("throughMs", 0)
                    } else {
                        // Arquivo antigo não tem pares com identidade: não inventa observações a partir de médias.
                        failures++
                    }
                } catch (_: Exception) { failures++ }
            }
            fun event(line: String) {
                try {
                    val e = JSONObject(line)
                    val t = e.optLong("recordedAtMs")
                    if (t <= archiveAt) return
                    // Espelho e ZIP podem conter o mesmo segmento; a linha canônica é idêntica.
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(line.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    if (!seen.add(digest)) return
                    val data = e.optJSONObject("data") ?: return
                    when (e.optString("type")) {
                        "telemetry" -> {
                            val d = data.optJSONObject("event")?.optJSONObject("data") ?: data.optJSONObject("data") ?: data
                            val fuel = when (d.optString("fuel").uppercase()) { "PETROL", "GASOLINA" -> "GASOLINA"; "CNG", "GAS", "GNV" -> "GNV"; else -> "" }
                            restored.accept(EquivalenceLedger.Frame(t, fuel, d.optDouble("rpm"), d.optDouble("load_bar"),
                                d.optDouble("petrol_ms"), d.optDouble("gas_ms_diagnostic", 0.0),
                                if (d.has("water_c")) d.optDouble("water_c") >= 60 else null))
                            reads++
                        }
                        "autocal_native_snapshot" -> {
                            snapshot = data
                            val fields = data.optJSONArray("fields")
                            fun raw(key: String): IntArray? = (0 until (fields?.length() ?: 0)).mapNotNull { fields?.optJSONObject(it) }
                                .firstOrNull { it.optString("key") == key && it.optString("status") == "VALID" }
                                ?.optJSONArray("rawValues")?.let { a -> IntArray(a.length()) { a.optInt(it) } }
                            val axis = raw("PETR_INJ_TBP"); val curve = raw("MUL_ACT")
                            if (axis?.size == 30 && curve?.size == 30) restored.observeCurve(axis, curve)
                        }
                        "k_factor_batch_confirmed" -> cycle.recordConfirmedCurve(data)
                        "autocal_native_calibration_epoch" -> cycle.nativeCurveChanged(data, snapshot)
                        "k_batch_confirmed" -> cycle.mapKWritten()
                    }
                } catch (_: Exception) { failures++ }
            }
            sessions().sortedWith(compareBy<Source> { it.id }.thenBy { it.modified }).forEach { source ->
                try {
                    source.open().use { input ->
                        if (source.id.endsWith(".zip", true)) {
                            ZipInputStream(input).use { zip ->
                                while (true) {
                                    val entry = zip.nextEntry ?: break
                                    if (!entry.isDirectory && entry.name.endsWith(".jsonl"))
                                        zip.bufferedReader().lineSequence().forEach(::event)
                                    zip.closeEntry()
                                }
                            }
                        } else input.bufferedReader().lineSequence().forEach(::event)
                    }
                } catch (_: Exception) { failures++ }
            }
            // Não substitui telemetria que chegou enquanto os arquivos eram lidos.
            val installed = !restored.isEmpty() && ledger.restoreMemory(restored.exportMemory())
            if (installed) {
                journal.restoreMemory(history.exportMemory())
                ledger.flush()
            }
            result = JSONObject().put("state", if (failures > 0) "PARTIAL" else if (installed) "RESTORED" else "EMPTY")
                .put("label", if (failures > 0) "Memória recuperada em parte; arquivos indisponíveis ou sem pares compatíveis"
                    else if (installed) "Memória recuperada das sessões locais" else "Sem memória compatível; aprendendo ao vivo")
                .put("telemetryReads", reads).put("failures", failures)
        } catch (_: Exception) {
            result = JSONObject().put("state", "FAILED").put("label", "Não foi possível ler a memória local; aprendendo ao vivo")
        }
    }
}
