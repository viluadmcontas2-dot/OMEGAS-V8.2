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
        const val POLICY_VERSION = 3
        const val RESET_POLICY = "LIVE_ONLY_RESET_DERIVED_PRESERVE_PETROL"
        const val RETROACTIVE_REASON_CODE = "RETROACTIVE_LEARNING_DISABLED"
        const val CALIBRATION_REQUIRED_REASON_CODE = "CALIBRATION_IDENTITY_REQUIRED"
    }

    private val runtimeRoot = stateFile.parentFile ?: stateFile.absoluteFile.parentFile
    private val evidenceStateFile = File(runtimeRoot, "learning_v6_evidence.json")
    private val policyMarker = File(runtimeRoot, "learning_live_only_policy_v3.json")
    private val calibrationBindingMarker = File(runtimeRoot, "learning_calibration_binding_v1.json")
    private var delegate: SignalLearningStore
    private var lastResetReceipt = JSONObject()
    private var storedCalibrationBinding: LearningCalibrationBinding? = null
    private var activeCalibrationBinding: LearningCalibrationBinding? = null

    init {
        runtimeRoot.mkdirs()
        delegate = SignalLearningStore(stateFile, log)
        lastResetReceipt = enforcePolicyMigration()
        storedCalibrationBinding = loadCalibrationBinding()
    }

    @Synchronized
    fun startSession(): JSONObject {
        activeCalibrationBinding = null
        return decorate(delegate.startSession())
    }

    @Synchronized
    fun endSession(reason: String): JSONObject {
        activeCalibrationBinding = null
        return decorate(delegate.endSession(reason))
    }

    @Synchronized
    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject {
        syncCalibrationAuthority()
        val source = decision.sample
        if (decision.learningEligible && source?.fuel == Mp48Fuel.CNG && activeCalibrationBinding == null) {
            val blocked = decision.copy(
                state = CALIBRATION_REQUIRED_REASON_CODE,
                reason = "GNV observado, mas a calibração física ainda não foi reconciliada; telemetria continua e a amostra não entra na ciência ativa.",
                sample = null,
                learningEligible = false,
                reasonCode = CALIBRATION_REQUIRED_REASON_CODE,
            )
            return decorate(delegate.ingest(telemetry, blocked))
        }
        return decorate(delegate.ingest(telemetry, decision))
    }

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
    fun importNativeSnapshot(snapshot: JSONObject): JSONObject {
        val enabled = nativeAutoCalEnabled(snapshot)
        if (snapshot.optBoolean("frozen", false) || enabled == 0) {
            return decorate(
                JSONObject()
                    .put("ok", true)
                    .put("source", "ECU_NATIVE")
                    .put("importedBands", 0)
                    .put("frozen", true)
                    .put("reasonCode", "AUTOCAL_PAUSED_SNAPSHOT")
                    .put("reason", "AutoCal pausado: snapshot permanece diagnóstico e não vira evidência nova."),
            )
                .put("activeLearningMutation", false)
                .put("retroactiveLearningAccepted", false)
        }
        return decorate(delegate.importNativeSnapshot(snapshot))
            .put("activeLearningMutation", false)
            .put("retroactiveLearningAccepted", false)
    }

    /**
     * Depois de uma escrita manual confirmada por humano + ACK/readback, ou de
     * uma mudança nativa da ECU observada e validada por readback, preserva a referência de
     * gasolina e zera toda evidência que depende da calibração GNV anterior.
     */
    @Synchronized
    fun onCalibrationAdjustment(payload: JSONObject): JSONObject {
        val readbackValid = payload.optBoolean("readbackValid", false)
        val manualConfirmed = payload.optBoolean("humanConfirmed", false) && readbackValid
        val nativeObserved = payload.optString("source") == "ECU_NATIVE_AUTOCAL" &&
            payload.optBoolean("ecuNativeObserved", false) &&
            !payload.optBoolean("appWritePerformed", true) &&
            readbackValid
        if (!manualConfirmed && !nativeObserved) {
            return decorate(
                JSONObject()
                    .put("ok", false)
                    .put("resetPerformed", false)
                    .put("reasonCode", "UNCONFIRMED_CALIBRATION_UPDATE")
                    .put(
                        "error",
                        "A evidência GNV só é invalidada por escrita humana confirmada/readback ou mudança nativa observada/readback.",
                    ),
            )
        }

        activeCalibrationBinding = null
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
            .put("reasonCode", if (nativeObserved) "ECU_NATIVE_AUTOCAL_EPOCH" else "CONFIRMED_CALIBRATION_DERIVED_RESET")
            .put(
                "reason",
                if (nativeObserved) {
                    "AutoCal nativo alterou a base global; gasolina preservada e evidência GNV anterior foi supersedida."
                } else {
                    "Calibração confirmada; gasolina preservada e GNV, equivalências, comparações, sugestões e confiança zerados."
                },
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
            if (nativeObserved) {
                "AutoCal nativo observado: base gasolina preservada; época GNV anterior supersedida"
            } else {
                "Calibração confirmada: base gasolina preservada; aprendizado GNV e derivados zerados"
            },
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
     * Uma CalibrationIdentity fresca é a única fonte capaz de liberar ciência GNV.
     * Se a identidade material mudou, qualquer GNV anterior é descartado antes
     * de aceitar a primeira amostra da nova calibração; gasolina é preservada.
     */
    private fun syncCalibrationAuthority() {
        val incoming = LearningCalibrationAuthority.snapshot() ?: return
        if (activeCalibrationBinding?.key() == incoming.key() &&
            activeCalibrationBinding?.usbSessionId == incoming.usbSessionId
        ) return

        val previous = storedCalibrationBinding
        if (previous != null && previous.key() != incoming.key()) {
            val before = delegate.export("identity-change-audit")
            val discardedCngRegions = countRegions(before, Mp48Fuel.CNG.wireName)
            val discardedComparisons = before.optJSONArray("comparisons")?.length() ?: 0
            if (discardedCngRegions > 0 || discardedComparisons > 0) {
                val baseline = selectPetrolBaseline(before)
                val rebuild = rebuildPreservingPetrol(baseline.snapshot, "calibration_identity_changed")
                delegate.onCalibrationAdjustment(
                    JSONObject()
                        .put("adjustmentId", "calibration-identity-changed")
                        .put("newHash", incoming.mapHash),
                )
                lastResetReceipt = JSONObject()
                    .put("policy", RESET_POLICY)
                    .put("reasonCode", "CALIBRATION_IDENTITY_CHANGED")
                    .put("reason", "Identidade física da calibração mudou; gasolina preservada e evidência GNV anterior foi isolada antes da nova coleta.")
                    .put("previousCalibrationFingerprint", previous.calibrationFingerprint)
                    .put("calibrationFingerprint", incoming.calibrationFingerprint)
                    .put("discardedCngRegions", discardedCngRegions)
                    .put("discardedComparisons", discardedComparisons)
                    .put("preservedPetrolRegions", rebuild.preservedPetrolRegions)
                    .put("quarantinedFiles", rebuild.archives)
                    .put("resetAt", System.currentTimeMillis())
            }
        }
        storedCalibrationBinding = incoming
        activeCalibrationBinding = incoming
        persistCalibrationBinding(incoming)
    }

    private fun loadCalibrationBinding(): LearningCalibrationBinding? = try {
        if (!calibrationBindingMarker.isFile) null
        else LearningCalibrationBinding.fromJson(JSONObject(calibrationBindingMarker.readText(Charsets.UTF_8)))
    } catch (_: Exception) {
        null
    }

    private fun persistCalibrationBinding(binding: LearningCalibrationBinding) {
        try {
            calibrationBindingMarker.parentFile?.mkdirs()
            calibrationBindingMarker.writeText(
                binding.toJson()
                    .put("schema", "omegas-learning-calibration-binding-v1")
                    .put("saved_at", System.currentTimeMillis())
                    .toString(2),
                Charsets.UTF_8,
            )
        } catch (error: Exception) {
            log.add("WARN", "LEARNING-CALIBRATION", "Identidade da calibração não persistida: ${error.message}")
        }
    }

    private fun decorateCalibrationEvidence(root: JSONObject): JSONObject {
        val binding = storedCalibrationBinding
        root.put("calibration_identity_ready", activeCalibrationBinding != null)
            .put("calibration_binding", binding?.toJson() ?: JSONObject.NULL)
        if (binding == null) return root

        fun stamp(target: JSONObject?) {
            if (target == null) return
            binding.toJson().keys().forEach { key -> target.put(key, binding.toJson().get(key)) }
        }
        val regions = root.optJSONArray("regions")
        if (regions != null) repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            if (region.optString("fuel") == Mp48Fuel.CNG.wireName) stamp(region)
        }
        val comparisons = root.optJSONArray("comparisons")
        if (comparisons != null) repeat(comparisons.length()) { index -> stamp(comparisons.optJSONObject(index)) }
        stamp(root.optJSONObject("comparison"))
        stamp(root.optJSONObject("cng_region"))
        root.optJSONObject("sample")?.takeIf { it.optString("fuel") == Mp48Fuel.CNG.wireName }?.let(::stamp)
        root.optJSONObject("live")?.optJSONObject("sample")
            ?.takeIf { it.optString("fuel") == Mp48Fuel.CNG.wireName }
            ?.let(::stamp)
        root.optJSONObject("signal_decision")?.optJSONObject("sample")
            ?.takeIf { it.optString("fuel") == Mp48Fuel.CNG.wireName }
            ?.let(::stamp)
        return root
    }

    /**
     * O status ao vivo e o assessor precisam contar a mesma verdade. O assessor
     * reconcilia GNV pendente contra a gasolina persistida; o resumo herda esse
     * total para não exibir “zero equivalências” enquanto já existem propostas.
     */
    private fun nativeAutoCalEnabled(snapshot: JSONObject): Int? {
        if (snapshot.has("autoCalEnabled") && !snapshot.isNull("autoCalEnabled")) {
            return snapshot.optInt("autoCalEnabled")
        }
        val fields = snapshot.optJSONArray("fields") ?: return null
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") != "AUTO_CAL_ENABLE" || field.optString("status") != "VALID") return@repeat
            val raw = field.optJSONArray("rawValues") ?: return@repeat
            if (raw.length() == 1) return raw.optInt(0)
        }
        return null
    }

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
        return decorateCalibrationEvidence(root)
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
                "Política live-only atualizada; gasolina preservada e toda evidência GNV anterior sem identidade material foi zerada.",
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
            return BaselineSelection(normalizeForInternalPetrolMerge(active), null)
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

    /**
     * Normaliza a base local antes do merge interno. Isso torna a preservação
     * idempotente: resets sucessivos não podem empilhar `reset-audit:` ou outros
     * namespaces criados pelo próprio app em IDs, visitas e sessões.
     */
    private fun normalizeForInternalPetrolMerge(raw: JSONObject): JSONObject {
        val sourceRegions = raw.optJSONArray("regions") ?: JSONArray()
        val regions = JSONArray()
        repeat(sourceRegions.length()) { index ->
            val source = sourceRegions.optJSONObject(index) ?: return@repeat
            if (source.optString("fuel") != Mp48Fuel.PETROL.wireName) return@repeat
            val region = JSONObject(source.toString())
            region.put("id", InternalLearningNamespace.normalize(region.optString("id")))
            region.put("visits", normalizeInternalIds(region.optJSONArray("visits")))
            region.put("sessions", normalizeInternalIds(region.optJSONArray("sessions")))
            regions.put(region)
        }
        val epoch = raw.optInt("epoch", 1).coerceAtLeast(1)
        val mapHash = raw.optString("mapHash", raw.optString("map_hash"))
        return JSONObject()
            .put("format", SignalLearningStore.FORMAT)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("learningDataRevision", SignalLearningStore.DATA_REVISION)
            .put("deviceId", InternalLearningNamespace.PRESERVED_PETROL_SOURCE)
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("regions", regions)
            .put("cells", LearningGridProjection.project(regions, epoch))
            .put("comparisons", JSONArray())
    }

    private fun normalizeInternalIds(raw: JSONArray?): JSONArray {
        val normalized = JSONArray()
        if (raw == null) return normalized
        repeat(raw.length()) { index ->
            raw.optString(index).takeIf { it.isNotBlank() }?.let {
                normalized.put(InternalLearningNamespace.normalize(it))
            }
        }
        return normalized
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
