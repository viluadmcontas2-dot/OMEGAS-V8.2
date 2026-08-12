package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Política pragmática de aprendizado do OMEGAS V7.
 *
 * A gasolina é a referência física permanente. Somente GNV, equivalências,
 * comparações, sugestões e confiança derivada pertencem à calibração atual.
 * Depois de uma escrita confirmada com ACK/readback, essas evidências derivadas
 * são zeradas, mas a superfície de gasolina é preservada. Arquivos e pares GNV
 * antigos nunca são mesclados de volta à memória ativa.
 */
class LiveOnlyLearningStore(
    private val stateFile: File,
    private val log: RingLog,
) {
    companion object {
        const val FORMAT = "omegas-learning-v7-live-only-v1"
        const val DATA_REVISION = 5
        const val POLICY_VERSION = 2
        const val RESET_POLICY = "LIVE_ONLY_RESET_DERIVED_PRESERVE_PETROL"
        const val RETROACTIVE_REASON_CODE = "RETROACTIVE_LEARNING_DISABLED"
    }

    private val runtimeRoot = stateFile.parentFile ?: stateFile.absoluteFile.parentFile
    private val evidenceStateFile = File(runtimeRoot, "learning_v6_evidence.json")
    private val policyMarker = File(runtimeRoot, "learning_live_only_policy_v2.json")
    private var delegate: SignalLearningStore
    private var lastResetReceipt = JSONObject()

    init {
        runtimeRoot.mkdirs()
        delegate = SignalLearningStore(stateFile, log)
        lastResetReceipt = enforcePolicyMigration()
    }

    @Synchronized
    fun startSession(): JSONObject = decorate(delegate.startSession())

    @Synchronized
    fun endSession(reason: String): JSONObject = decorate(delegate.endSession(reason))

    @Synchronized
    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject =
        decorate(delegate.ingest(telemetry, decision))

    @Synchronized
    fun statusJson(): JSONObject = decorateStatus(delegate.statusJson())

    @Synchronized
    fun export(deviceId: String): JSONObject = decorate(delegate.export(deviceId))

    /** Aprendizado importado/remoto é deliberadamente recusado pela política live-only. */
    @Synchronized
    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject = decorate(
        JSONObject()
            .put("ok", false)
            .put("accepted", false)
            .put("reasonCode", RETROACTIVE_REASON_CODE)
            .put(
                "error",
                "Aprendizado GNV retroativo desativado. A base de gasolina local é preservada; colete GNV ao vivo na calibração atual.",
            )
            .put("incomingFormat", payload.optString("format", "UNKNOWN"))
            .put("incomingDeviceId", payload.optString("deviceId", localDeviceId)),
    )

    /** Snapshot AutoCal continua sendo apenas diagnóstico e nunca vira amostra ativa. */
    @Synchronized
    fun importNativeSnapshot(snapshot: JSONObject): JSONObject =
        decorate(delegate.importNativeSnapshot(snapshot))
            .put("activeLearningMutation", false)
            .put("retroactiveLearningAccepted", false)

    /**
     * Depois da confirmação humana, ACK e readback, preserva a referência de
     * gasolina e zera toda evidência que depende da calibração GNV anterior.
     */
    @Synchronized
    fun onCalibrationAdjustment(payload: JSONObject): JSONObject {
        if (!payload.optBoolean("humanConfirmed", false) || !payload.optBoolean("readbackValid", false)) {
            return decorate(
                JSONObject()
                    .put("ok", false)
                    .put("resetPerformed", false)
                    .put("reasonCode", "UNCONFIRMED_CALIBRATION_UPDATE")
                    .put("error", "A evidência GNV só é zerada após confirmação humana e readback válido."),
            )
        }

        val before = delegate.export("reset-audit")
        val baseline = selectPetrolBaseline(before)
        val petrolRegionsBefore = countRegions(baseline.snapshot, Mp48Fuel.PETROL.wireName)
        val discardedCngRegions = countRegions(before, Mp48Fuel.CNG.wireName)
        val discardedComparisons = before.optJSONArray("comparisons")?.length() ?: 0
        val discardedSessions = before.optJSONArray("sessions")?.length() ?: 0
        val discardedNativeEvidence = before.optJSONArray("nativeEcuEvidence")?.length() ?: 0

        val rebuild = rebuildPreservingPetrol(baseline.snapshot, "calibration_confirmed")
        val fresh = delegate.onCalibrationAdjustment(payload)

        lastResetReceipt = JSONObject()
            .put("policy", RESET_POLICY)
            .put("reasonCode", "CONFIRMED_CALIBRATION_DERIVED_RESET")
            .put(
                "reason",
                "Calibração confirmada; gasolina preservada e GNV, equivalências, comparações, sugestões e confiança zerados.",
            )
            .put("calibrationType", payload.optString("calibrationType", "UNKNOWN"))
            .put("adjustmentId", payload.optString("adjustmentId"))
            .put("newHash", payload.optString("newHash", payload.optString("hash")))
            .put("petrolRegionsBefore", petrolRegionsBefore)
            .put("preservedPetrolRegions", rebuild.preservedPetrolRegions)
            .put("petrolRecoverySource", baseline.recoveredFromQuarantine ?: JSONObject.NULL)
            .put("discardedCngRegions", discardedCngRegions)
            .put("discardedComparisons", discardedComparisons)
            .put("discardedSessions", discardedSessions)
            .put("discardedNativeEvidence", discardedNativeEvidence)
            .put("quarantinedFiles", rebuild.archives)
            .put("resetAt", System.currentTimeMillis())

        log.add(
            "WARN",
            "LEARNING-RESET",
            "Calibração confirmada: base gasolina preservada; aprendizado GNV e derivados zerados",
        )
        return decorate(fresh)
            .put("ok", true)
            .put("resetPerformed", true)
            .put("petrolBaselinePreserved", true)
            .put("resetReceipt", JSONObject(lastResetReceipt.toString()))
    }

    @Synchronized
    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject =
        delegate.previewKWrite(row, column, value)

    @Synchronized
    fun close() {
        delegate.close()
    }

    /**
     * O status ao vivo e o assessor precisam contar a mesma verdade. O assessor
     * reconcilia GNV pendente contra a gasolina persistida; o resumo herda esse
     * total para não exibir “zero equivalências” enquanto já existem propostas.
     */
    private fun decorateStatus(source: JSONObject): JSONObject {
        val root = decorate(source)
        val advice = root.optJSONObject("assisted_calibration") ?: JSONObject()
        val reconciliation = advice.optJSONObject("reconciliation") ?: JSONObject()
        val comparisonCount = advice.optInt("comparisonCount", 0).coerceAtLeast(0)
        val uniqueVisits = advice.optInt("uniqueVisitCount", comparisonCount).coerceAtLeast(0)
        val summary = root.optJSONObject("summary") ?: JSONObject().also { root.put("summary", it) }
        summary.put("comparisons", comparisonCount)
            .put("reconciled_comparisons", comparisonCount)
            .put("unique_comparison_visits", uniqueVisits)
            .put("pending_cng_visits", reconciliation.optInt("pending_cng_visits", 0))
        root.put("reconciliation", reconciliation)
            .put("comparison_count", comparisonCount)
            .put("unique_comparison_visits", uniqueVisits)
        return root
    }

    private fun decorate(source: JSONObject): JSONObject {
        val root = JSONObject(source.toString())
            .put("format", FORMAT)
            .put("learningDataRevision", DATA_REVISION)
            .put("learning_data_revision", DATA_REVISION)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("resetPolicy", RESET_POLICY)
            .put("reset_policy", RESET_POLICY)
            .put("retroactiveLearningAccepted", false)
            .put("retroactive_learning_accepted", false)
            .put("petrolBaselinePreserved", true)
            .put("petrol_baseline_preserved", true)
            .put("cumulativeEvidencePreserved", false)
            .put("cumulative_evidence_preserved", false)
            .put("sessionMetadataPolicy", "live-only-reset-derived-preserve-petrol")
            .put("session_metadata_policy", "live-only-reset-derived-preserve-petrol")
        if (lastResetReceipt.length() > 0) {
            root.put("lastReset", JSONObject(lastResetReceipt.toString()))
            root.put("last_reset", JSONObject(lastResetReceipt.toString()))
        }
        return root
    }

    /**
     * Ao ativar uma revisão da política, elimina GNV e derivados antigos uma vez,
     * mas migra a referência de gasolina para a memória ativa nova. Se a revisão
     * anterior já tiver zerado a memória ativa por engano, recupera a gasolina do
     * arquivo local mais recente isolado em learning_quarantine.
     */
    private fun enforcePolicyMigration(): JSONObject {
        val currentVersion = try {
            if (policyMarker.isFile) {
                JSONObject(policyMarker.readText(Charsets.UTF_8)).optInt("version", 0)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
        if (currentVersion == POLICY_VERSION) return JSONObject()

        val before = delegate.export("policy-migration")
        val baseline = selectPetrolBaseline(before)
        val petrolRegionsBefore = countRegions(baseline.snapshot, Mp48Fuel.PETROL.wireName)
        val discardedCngRegions = countRegions(before, Mp48Fuel.CNG.wireName)
        val discardedComparisons = before.optJSONArray("comparisons")?.length() ?: 0
        val rebuild = rebuildPreservingPetrol(baseline.snapshot, "policy_v$POLICY_VERSION")
        val mapHash = before.optString("mapHash", before.optString("map_hash"))
        delegate.onCalibrationAdjustment(
            JSONObject()
                .put("adjustmentId", "policy-migration-v$POLICY_VERSION")
                .put("newHash", mapHash),
        )

        val receipt = JSONObject()
            .put("policy", RESET_POLICY)
            .put("reasonCode", "POLICY_UPDATE_DERIVED_RESET")
            .put(
                "reason",
                "Política live-only atualizada; gasolina preservada e toda evidência GNV anterior foi zerada.",
            )
            .put("petrolRegionsBefore", petrolRegionsBefore)
            .put("preservedPetrolRegions", rebuild.preservedPetrolRegions)
            .put("petrolRecoverySource", baseline.recoveredFromQuarantine ?: JSONObject.NULL)
            .put("discardedCngRegions", discardedCngRegions)
            .put("discardedComparisons", discardedComparisons)
            .put("quarantinedFiles", rebuild.archives)
            .put("resetAt", System.currentTimeMillis())
        policyMarker.writeText(
            JSONObject(receipt.toString())
                .put("version", POLICY_VERSION)
                .put("activeState", stateFile.name)
                .toString(2),
            Charsets.UTF_8,
        )
        log.add("WARN", "LEARNING-POLICY", receipt.getString("reason"))
        return receipt
    }

    /**
     * Prefere a gasolina da memória ativa. A quarentena só é consultada para
     * reparar o reset total incorreto da revisão anterior e nunca restaura GNV,
     * comparações ou sugestões.
     */
    private fun selectPetrolBaseline(active: JSONObject): BaselineSelection {
        if (countRegions(active, Mp48Fuel.PETROL.wireName) > 0) {
            return BaselineSelection(active, null)
        }
        val quarantine = File(runtimeRoot, "learning_quarantine")
        val candidates = quarantine.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        for (candidate in candidates) {
            val raw = try {
                JSONObject(candidate.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                continue
            }
            if (countRegions(raw, Mp48Fuel.PETROL.wireName) <= 0) continue
            log.add(
                "WARN",
                "LEARNING-PETROL-RECOVERY",
                "Base gasolina recuperada de ${candidate.name}; GNV e derivados permanecem descartados",
            )
            return BaselineSelection(normalizeForInternalPetrolMerge(raw), candidate.name)
        }
        return BaselineSelection(active, null)
    }

    /** Normaliza um estado interno antigo para o contrato de merge validado. */
    private fun normalizeForInternalPetrolMerge(raw: JSONObject): JSONObject {
        val regions = raw.optJSONArray("regions")?.let { JSONArray(it.toString()) } ?: JSONArray()
        val epoch = raw.optInt("epoch", 1).coerceAtLeast(1)
        val mapHash = raw.optString("mapHash", raw.optString("map_hash"))
        return JSONObject()
            .put("format", SignalLearningStore.FORMAT)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("learningDataRevision", SignalLearningStore.DATA_REVISION)
            .put("deviceId", "local-petrol-quarantine-recovery")
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("regions", regions)
            .put("cells", LearningGridProjection.project(regions, epoch))
            .put("comparisons", JSONArray())
    }

    /**
     * Recria a memória ativa vazia e usa o importador interno já validado, que
     * por contrato importa somente regiões PETROL. O merge público continua
     * bloqueado; esta restauração serve exclusivamente para a base local.
     */
    private fun rebuildPreservingPetrol(snapshot: JSONObject, tag: String): RebuildResult {
        delegate.close()
        val archives = discardActiveLearningFiles(tag)
        delegate = SignalLearningStore(stateFile, log)
        delegate.startSession()
        val restored = delegate.merge(JSONObject(snapshot.toString()), "preserved-petrol-baseline")
        return RebuildResult(
            preservedPetrolRegions = restored.optInt("mergedRegions", 0),
            archives = archives,
        )
    }

    private fun countRegions(snapshot: JSONObject, fuel: String): Int {
        val regions = snapshot.optJSONArray("regions") ?: return 0
        var count = 0
        repeat(regions.length()) { index ->
            if (regions.optJSONObject(index)?.optString("fuel") == fuel) count += 1
        }
        return count
    }

    private fun discardActiveLearningFiles(tag: String): JSONArray {
        val quarantine = File(runtimeRoot, "learning_quarantine").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val archived = JSONArray()
        val candidates = linkedSetOf(
            stateFile,
            File(stateFile.parentFile, stateFile.name + ".bak"),
            File(stateFile.parentFile, stateFile.name + ".tmp"),
            evidenceStateFile,
            File(evidenceStateFile.parentFile, evidenceStateFile.name + ".tmp"),
            File(evidenceStateFile.parentFile, evidenceStateFile.name + ".invalid"),
        )
        candidates.filter { it.isFile }.forEachIndexed { index, source ->
            val suffix = source.extension.ifBlank { "json" }
            val target = File(
                quarantine,
                "${source.nameWithoutExtension}_${tag}_${stamp}_$index.$suffix",
            )
            val moved = try {
                if (source.renameTo(target)) {
                    true
                } else {
                    source.copyTo(target, overwrite = true)
                    source.delete()
                }
            } catch (_: Exception) {
                false
            }
            if (!moved && source.exists()) {
                try {
                    source.writeText(
                        JSONObject()
                            .put("format", "discarded-by-live-only-policy")
                            .put("discardedAt", stamp)
                            .toString(),
                        Charsets.UTF_8,
                    )
                } catch (_: Exception) {
                    source.delete()
                }
            }
            if (target.isFile) archived.put(target.name)
        }
        return archived
    }

    private data class RebuildResult(
        val preservedPetrolRegions: Int,
        val archives: JSONArray,
    )

    private data class BaselineSelection(
        val snapshot: JSONObject,
        val recoveredFromQuarantine: String?,
    )
}
