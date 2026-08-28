package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Resumo read-only da evidência AutoCal nativa persistida pelo aprendizado.
 *
 * Não importa amostras, não executa assessor, não altera memória ativa e não
 * chama qualquer writer. O arquivo só é relido quando mtime/tamanho mudam.
 */
class NativeAutoCalEvidenceSummary(
    runtimeRoot: File,
) {
    companion object {
        const val FILE_NAME = "learning_v6_evidence.json"
        const val SOURCE = "ECU_NATIVE"
    }

    private val evidenceFile = File(runtimeRoot, FILE_NAME)
    private var cachedSignature = ""
    private var cached = emptySummary()

    @Synchronized
    fun snapshot(): JSONObject {
        val signature = signature()
        if (signature == cachedSignature) return JSONObject(cached.toString())
        cachedSignature = signature
        cached = buildSummary()
        return JSONObject(cached.toString())
    }

    private fun signature(): String = if (!evidenceFile.isFile) {
        "missing"
    } else {
        "${evidenceFile.lastModified()}:${evidenceFile.length()}"
    }

    private fun buildSummary(): JSONObject {
        if (!evidenceFile.isFile) return emptySummary()
        return try {
            val root = JSONObject(evidenceFile.readText(Charsets.UTF_8))
            summarize(root.optJSONArray("nativeEcuEvidence") ?: JSONArray())
                .put("available", true)
                .put("file", evidenceFile.name)
        } catch (error: Exception) {
            emptySummary()
                .put("available", false)
                .put("error", error.message ?: "Evidência AutoCal nativa indisponível")
        }
    }

    private fun summarize(items: JSONArray): JSONObject {
        val snapshotIds = linkedSetOf<String>()
        var totalCounts = 0L
        var bandsWithSamples = 0
        var historicalConditionKnown = 0
        var coverageSum = 0.0
        var coverageItems = 0
        var latestSnapshotId = ""

        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val snapshotId = item.optString("snapshotId")
            if (snapshotId.isNotBlank()) {
                snapshotIds += snapshotId
                latestSnapshotId = snapshotId
            }
            val count = item.optInt("count", 0).coerceAtLeast(0)
            totalCounts += count.toLong()
            if (count > 0) bandsWithSamples += 1
            if (item.optBoolean("historicalConditionKnown", false)) historicalConditionKnown += 1
            if (item.has("coverageQuality") && !item.isNull("coverageQuality")) {
                coverageSum += item.optDouble("coverageQuality", 0.0).coerceIn(0.0, 1.0)
                coverageItems += 1
            }
        }

        return JSONObject()
            .put("ok", true)
            .put("source", SOURCE)
            .put("available", items.length() > 0)
            .put("snapshotCount", snapshotIds.size)
            .put("bandCount", items.length())
            .put("bandsWithSamples", bandsWithSamples)
            .put("totalNativeCounts", totalCounts)
            .put("historicalConditionKnownBands", historicalConditionKnown)
            .put("averageCoverageQuality", if (coverageItems > 0) coverageSum / coverageItems else 0.0)
            .put("latestSnapshotId", latestSnapshotId.ifBlank { JSONObject.NULL })
            .put("automaticCalibration", false)
            .put("manualOnly", true)
            .put("activeLearningMutation", false)
            .put("writerAvailable", false)
    }

    private fun emptySummary(): JSONObject = JSONObject()
        .put("ok", true)
        .put("source", SOURCE)
        .put("available", false)
        .put("snapshotCount", 0)
        .put("bandCount", 0)
        .put("bandsWithSamples", 0)
        .put("totalNativeCounts", 0)
        .put("historicalConditionKnownBands", 0)
        .put("averageCoverageQuality", 0.0)
        .put("latestSnapshotId", JSONObject.NULL)
        .put("automaticCalibration", false)
        .put("manualOnly", true)
        .put("activeLearningMutation", false)
        .put("writerAvailable", false)
}
