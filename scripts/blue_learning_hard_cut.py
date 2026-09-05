#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def p(rel: str) -> Path:
    return ROOT / rel


def write(rel: str, content: str) -> None:
    target = p(rel)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def delete(rel: str) -> None:
    target = p(rel)
    if target.is_file():
        target.unlink()


def replace(rel: str, old: str, new: str) -> None:
    target = p(rel)
    if not target.is_file():
        return
    text = target.read_text(encoding="utf-8")
    if old in text:
        target.write_text(text.replace(old, new), encoding="utf-8")


def regex_replace(rel: str, pattern: str, replacement: str, flags: int = 0) -> None:
    target = p(rel)
    if not target.is_file():
        return
    text = target.read_text(encoding="utf-8")
    updated, _ = re.subn(pattern, replacement, text, flags=flags)
    if updated != text:
        target.write_text(updated, encoding="utf-8")


# One passive store: accepted physical evidence in, JSON evidence out.
write(
    "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt",
    r'''package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistência passiva da evidência física usada pelo Blue.
 *
 * Esta classe não compara combustíveis, não calcula alvo K e não possui caminho
 * de escrita na ECU. Ela só registra janelas físicas aceitas pelo analisador,
 * preserva a referência de gasolina e separa a evidência GNV por época de
 * calibração. Toda decisão científica pertence ao BlueCausalEngine.
 */
class BlueEvidenceStore(
    runtimeRoot: File,
    private val log: RingLog,
) {
    companion object {
        const val FORMAT = "omegas-blue-evidence"
        const val DATA_REVISION = 1
        const val STATE_FILE = "blue_evidence_state.json"
        private const val MAX_PETROL_EVIDENCE = 8_192
        private const val MAX_CNG_EVIDENCE = 8_192
        private const val MAX_NATIVE_SNAPSHOTS = 128
    }

    private val lock = Any()
    private val stateFile = File(runtimeRoot, STATE_FILE)
    private val writer = CoalescedSnapshotWriter(
        target = stateFile,
        threadName = "omegas-blue-evidence-persist",
    )
    private val publicationGate = SciencePublicationGate()
    private val petrol = linkedMapOf<String, JSONObject>()
    private val cng = linkedMapOf<String, JSONObject>()
    private val nativeSnapshots = linkedMapOf<String, JSONObject>()
    private val nativeAnchors = NativeLearningAnchorRegistry(LearningEvidenceBudget.MAX_NATIVE_ANCHORS)

    private var epoch = 1
    private var sessionOpen = false
    private var acceptedPetrol = 0L
    private var acceptedCng = 0L
    private var lastState = "OBSERVING_ENGINE"
    private var lastReason = "Aguardando evidência física estável"
    private var lastQuality = 0.0

    init {
        runtimeRoot.mkdirs()
        loadCurrentStateOnly()
    }

    fun startSession(): JSONObject = synchronized(lock) {
        sessionOpen = true
        publicationGate.reset()
        lastState = "OBSERVING_ENGINE"
        lastReason = "Sessão física iniciada; aguardando uma janela estável"
        statusLocked()
    }

    fun endSession(reason: String): JSONObject {
        val result = synchronized(lock) {
            sessionOpen = false
            publicationGate.reset()
            lastState = "SESSION_ENDED"
            lastReason = reason
            statusLocked()
        }
        persist()
        writer.flush(2_000L)
        return result
    }

    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject {
        val sample = decision.sample
        if (!decision.learningEligible || sample == null) {
            return synchronized(lock) {
                lastState = decision.state
                lastReason = decision.reason
                lastQuality = sample?.quality ?: 0.0
                statusLocked().put("sample", decision.toTelemetryJson())
            }
        }

        val publication = publicationGate.evaluate(
            key = sample.fuel.wireName,
            startedAtElapsedMs = sample.startedAtElapsedMs,
            endedAtElapsedMs = sample.endedAtElapsedMs,
            frameCount = sample.frameCount,
            medianIntervalMs = sample.diagnostics.medianIntervalMs,
        )
        if (!publication.publish) {
            return synchronized(lock) {
                lastState = "EVIDENCE_COALESCED"
                lastReason = if (publication.novelty.duplicate) {
                    "Janela física já representada; nenhum voto duplicado foi persistido"
                } else {
                    "Janela física válida, aguardando massa nova antes de persistir outra evidência"
                }
                lastQuality = sample.quality
                statusLocked().put("sample", decision.toTelemetryJson())
            }
        }

        val now = System.currentTimeMillis()
        synchronized(lock) {
            val region = sampleRegion(sample, now)
            when (sample.fuel) {
                Mp48Fuel.PETROL -> {
                    petrol[sample.id] = region
                    acceptedPetrol += 1L
                    trimOldest(petrol, MAX_PETROL_EVIDENCE)
                    lastState = "PETROL_EVIDENCE_ACCEPTED"
                    lastReason = "Referência física de gasolina registrada"
                }
                Mp48Fuel.CNG -> {
                    cng[sample.id] = region.put("epoch", epoch)
                    acceptedCng += 1L
                    trimOldest(cng, MAX_CNG_EVIDENCE)
                    lastState = "CNG_EVIDENCE_ACCEPTED"
                    lastReason = "Evidência física GNV registrada na época $epoch"
                }
                else -> {
                    lastState = "EVIDENCE_IGNORED"
                    lastReason = "Combustível não elegível para a memória Blue"
                    return statusLocked().put("sample", decision.toTelemetryJson())
                }
            }
            lastQuality = sample.quality
        }
        persist()
        return synchronized(lock) { statusLocked().put("sample", decision.toTelemetryJson()) }
    }

    fun statusJson(): JSONObject = synchronized(lock) { statusLocked() }

    fun export(deviceId: String): JSONObject = synchronized(lock) {
        snapshotLocked()
            .put("ok", true)
            .put("deviceId", deviceId)
            .put("exportedAt", System.currentTimeMillis())
    }

    /**
     * Fusão entre aparelhos aceita somente referência de gasolina no formato
     * Blue atual. GNV nunca atravessa aparelhos/épocas porque depende da
     * calibração ativa que foi lida fisicamente nesta ECU.
     */
    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject {
        if (payload.optString("format") != FORMAT || payload.optInt("learningDataRevision", 0) != DATA_REVISION) {
            return JSONObject()
                .put("ok", false)
                .put("accepted", false)
                .put("error", "Evidência não pertence ao formato Blue atual")
        }
        val regions = payload.optJSONArray("regions") ?: JSONArray()
        var imported = 0
        synchronized(lock) {
            repeat(regions.length()) { index ->
                val source = regions.optJSONObject(index) ?: return@repeat
                if (source.optString("fuel").uppercase() !in setOf("PETROL", "GASOLINA")) return@repeat
                val id = source.optString("id")
                val petrolMs = source.optDouble("petrol_ms", 0.0)
                val quality = source.optDouble("quality", 0.0)
                if (id.isBlank() || !petrolMs.isFinite() || petrolMs <= 0.0 || !quality.isFinite() || quality <= 0.0) return@repeat
                if (!petrol.containsKey(id)) {
                    petrol[id] = JSONObject(source.toString())
                        .put("fuel", Mp48Fuel.PETROL.wireName)
                        .remove("epoch")
                    imported += 1
                }
            }
            trimOldest(petrol, MAX_PETROL_EVIDENCE)
            if (imported > 0) {
                lastState = "PETROL_EVIDENCE_MERGED"
                lastReason = "$imported referências de gasolina Blue incorporadas"
            }
        }
        if (imported > 0) persist()
        return JSONObject()
            .put("ok", true)
            .put("accepted", true)
            .put("importedPetrol", imported)
            .put("importedCng", 0)
            .put("localDeviceId", localDeviceId)
            .put("epoch", synchronized(lock) { epoch })
    }

    /** Snapshot Auto-Cal é contexto observacional; não vira comparação nem alvo. */
    fun importNativeSnapshot(snapshot: JSONObject): JSONObject {
        val snapshotId = snapshot.optString("snapshotId", snapshot.optString("sessionId"))
        if (snapshotId.isBlank()) return JSONObject().put("ok", false).put("error", "Snapshot nativo sem identificador")
        var anchorsImported = 0
        synchronized(lock) {
            nativeSnapshots[snapshotId] = JSONObject(snapshot.toString())
                .put("storedAt", System.currentTimeMillis())
            trimOldest(nativeSnapshots, MAX_NATIVE_SNAPSHOTS)
            val maturity = snapshot.optJSONArray("nativeMaturityEvents") ?: JSONArray()
            repeat(maturity.length()) { index ->
                val event = maturity.optJSONObject(index) ?: return@repeat
                val anchor = NativeLearningAnchor.fromMaturityEvent(event, epoch) ?: return@repeat
                if (nativeAnchors.upsert(anchor)) anchorsImported += 1
            }
        }
        persist()
        return JSONObject()
            .put("ok", true)
            .put("source", "ECU_NATIVE")
            .put("importedBands", 0)
            .put("importedNativeAnchors", anchorsImported)
            .put("nativeAnchorCount", synchronized(lock) { nativeAnchors.snapshot().size })
            .put("calibrationEpoch", synchronized(lock) { epoch })
            .put("activeLearningMutation", false)
    }

    /**
     * Esta chamada já representa uma alteração confirmada/readback pelo limite
     * de calibração. A gasolina continua válida; GNV e âncoras da época anterior
     * são descartados porque dependiam de outro estado K.
     */
    fun onCalibrationAdjustment(payload: JSONObject): JSONObject {
        val discarded: Int
        synchronized(lock) {
            discarded = cng.size
            cng.clear()
            nativeAnchors.clear()
            nativeSnapshots.clear()
            epoch += 1
            publicationGate.reset()
            lastState = "CALIBRATION_EPOCH_CHANGED"
            lastReason = "Nova época de calibração confirmada; gasolina preservada e GNV reiniciado"
        }
        persist()
        return JSONObject()
            .put("ok", true)
            .put("resetPerformed", true)
            .put("epoch", synchronized(lock) { epoch })
            .put("discardedCngEvidence", discarded)
            .put("petrolBaselinePreserved", true)
            .put("adjustmentId", payload.optString("adjustmentId"))
    }

    /** Eco físico da entrada manual; não calcula recomendação nem escreve na ECU. */
    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject = JSONObject()
        .put("ok", true)
        .put("row", row)
        .put("column", column)
        .put("value", value)
        .put("manualOnly", true)
        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")

    fun storageStatus(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("format", FORMAT)
            .put("stateFile", STATE_FILE)
            .put("available", stateFile.isFile)
            .put("bytes", if (stateFile.isFile) stateFile.length() else 0L)
            .put("epoch", epoch)
            .put("petrolEvidence", petrol.size)
            .put("cngEvidence", cng.size)
            .put("persistence", writer.metricsJson())
    }

    fun close() {
        persist()
        writer.close()
    }

    private fun statusLocked(): JSONObject = JSONObject()
        .put("ok", true)
        .put("state", lastState)
        .put("reason", lastReason)
        .put("learning", sessionOpen)
        .put("quality", lastQuality)
        .put("reference_confidence", lastQuality)
        .put("epoch", epoch)
        .put("petrolEvidence", petrol.size)
        .put("cngEvidence", cng.size)
        .put("acceptedPetrol", acceptedPetrol)
        .put("acceptedCng", acceptedCng)
        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")

    private fun sampleRegion(sample: MotorSample, now: Long): JSONObject = JSONObject()
        .put("id", sample.id)
        .put("fuel", sample.fuel.wireName)
        .put("updated_at", now)
        .put("rpm", sample.rpm)
        .put("map_bar", sample.mapBar)
        .put("petrol_ms", sample.petrolMs)
        .put("pressure_diff_bar", sample.pressureDiffBar)
        .put("water_c", sample.waterC)
        .put("gas_c", sample.gasC)
        .put("quality", sample.quality)
        .put("classification", sample.classification.name)
        .put("frame_count", sample.frameCount)
        .put("visits", JSONArray().put(sample.id))
        .put("diagnostics", sample.diagnostics.toJson())

    private fun snapshotLocked(): JSONObject {
        val regions = JSONArray()
        petrol.values.forEach { regions.put(JSONObject(it.toString())) }
        cng.values.forEach { regions.put(JSONObject(it.toString())) }
        return JSONObject()
            .put("format", FORMAT)
            .put("learningDataRevision", DATA_REVISION)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("epoch", epoch)
            .put("regions", regions)
            .put("comparisons", JSONArray())
            .put("nativeEcuEvidence", JSONArray(nativeSnapshots.values.map { JSONObject(it.toString()) }))
            .put("nativeLearningAnchors", JSONArray(nativeAnchors.snapshot().map { it.toJson() }))
            .put("evidenceStateSchema", FORMAT)
            .put("summary", JSONObject()
                .put("petrolEvidence", petrol.size)
                .put("cngEvidence", cng.size)
                .put("nativeAnchors", nativeAnchors.snapshot().size))
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
    }

    private fun persist() {
        writer.request { synchronized(lock) { snapshotLocked().toString() } }
    }

    private fun loadCurrentStateOnly() {
        if (!stateFile.isFile) return
        try {
            val root = JSONObject(stateFile.readText(Charsets.UTF_8))
            if (root.optString("format") != FORMAT || root.optInt("learningDataRevision", 0) != DATA_REVISION) {
                log.add("INFO", "BLUE-EVIDENCE", "Estado fora do formato Blue ignorado; nenhuma migração implícita foi executada")
                return
            }
            synchronized(lock) {
                epoch = root.optInt("epoch", 1).coerceAtLeast(1)
                val regions = root.optJSONArray("regions") ?: JSONArray()
                repeat(regions.length()) { index ->
                    val region = regions.optJSONObject(index) ?: return@repeat
                    val id = region.optString("id")
                    if (id.isBlank()) return@repeat
                    when (region.optString("fuel").uppercase()) {
                        "PETROL", "GASOLINA" -> petrol[id] = JSONObject(region.toString()).apply { remove("epoch") }
                        "CNG", "GNV", "GAS" -> if (region.optInt("epoch", epoch) == epoch) {
                            cng[id] = JSONObject(region.toString()).put("epoch", epoch)
                        }
                    }
                }
                trimOldest(petrol, MAX_PETROL_EVIDENCE)
                trimOldest(cng, MAX_CNG_EVIDENCE)
                val savedAnchors = root.optJSONArray("nativeLearningAnchors") ?: JSONArray()
                val anchors = buildList {
                    repeat(savedAnchors.length()) { index ->
                        NativeLearningAnchor.fromJson(savedAnchors.optJSONObject(index) ?: return@repeat)?.let(::add)
                    }
                }
                nativeAnchors.replaceAll(anchors.filter { it.calibrationEpoch == epoch })
            }
        } catch (error: Exception) {
            log.add("WARN", "BLUE-EVIDENCE", "Estado Blue não carregado: ${error.message}")
        }
    }

    private fun <T> trimOldest(values: LinkedHashMap<String, T>, maxEntries: Int) {
        while (values.size > maxEntries) values.remove(values.keys.first())
    }
}
''',
)

# Runtime binds directly to the passive store; there is no restore/migration facade.
runtime = "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
replace(runtime, "import com.omegas.prohub.learning.DeferredLiveOnlyLearningStore\n", "import com.omegas.prohub.learning.BlueEvidenceStore\n")
replace(runtime, "import com.omegas.prohub.learning.LiveOnlyLearningStore\n", "")
replace(runtime, "private val learning = DeferredLiveOnlyLearningStore(paths.runtimeRoot, log)", "private val learning = BlueEvidenceStore(paths.runtimeRoot, log)")
replace(runtime, '.put("learningScaleMigration", learning.migrationStatus())', '.put("learningStorage", learning.storageStatus())')
regex_replace(
    runtime,
    r'\n    fun learningStatus\(\): JSONObject \{.*?\n    fun notifyCalibrationAdjustment',
    '''\n    fun learningStatus(): JSONObject {\n        val current = safeLearningStatus()\n        publishLearningState(latestLearningSequence, current)\n        return current\n            .put("format", BlueEvidenceStore.FORMAT)\n            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)\n            .put("storage", learning.storageStatus())\n            .put("pipeline", learningPipeline.metricsJson())\n    }\n\n    fun notifyCalibrationAdjustment''',
    flags=re.S,
)

# Blue-facing service cache watches the one evidence file.
access = "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"
replace(access, "import com.omegas.prohub.learning.LearningTelemetrySchemaMigration\n", "import com.omegas.prohub.learning.BlueEvidenceStore\n")
regex_replace(
    access,
    r'return listOf\(\n\s*File\(root, LearningTelemetrySchemaMigration\.ACTIVE_STATE_FILE\),\n\s*File\(root, "learning_evidence\.json"\),\n\s*File\(root, "k_map_cache\.json"\),\n\s*\)',
    'return listOf(\n            File(root, BlueEvidenceStore.STATE_FILE),\n            File(root, "k_map_cache.json"),\n        )',
)

# UI reads current Blue evidence, never a migration/quarantine file.
hub = "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt"
replace(hub, "import com.omegas.prohub.learning.LearningTelemetrySchemaMigration\n", "import com.omegas.prohub.learning.BlueEvidenceStore\n")
replace(hub, "import com.omegas.prohub.learning.SignalLearningStore\n", "")
replace(hub, "LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE", "BlueEvidenceStore.STATE_FILE")
replace(hub, 'File(root, "learning_v6_evidence.json"),\n', "")
replace(hub, "SignalLearningStore.FORMAT", "BlueEvidenceStore.FORMAT")
replace(hub, ".put(\"learningScaleMigration\", LearningTelemetrySchemaMigration.status(service.paths.runtimeRoot))", ".put(\"learningStorage\", service.runtime.learningStatus().optJSONObject(\"storage\") ?: JSONObject())")
replace(hub, ".put(\"learningScaleMigration\", LearningTelemetrySchemaMigration.status(AppPaths(appContext).runtimeRoot))", ".put(\"learningStorage\", JSONObject().put(\"stateFile\", BlueEvidenceStore.STATE_FILE))")
replace(hub, ".put(\"scaleMigration\", LearningTelemetrySchemaMigration.status(AppPaths(appContext).runtimeRoot))", ".put(\"storage\", root.optJSONObject(\"storage\") ?: JSONObject())")
replace(hub, '.put("assistedCalibration", root.optJSONObject("assistedCalibration") ?: JSONObject())\n', '')
replace(hub, '.put("assisted_calibration", root.optJSONObject("assisted_calibration") ?: JSONObject())\n', '')
replace(hub, '.put("reconciliation", root.optJSONObject("reconciliation") ?: JSONObject())\n', '')
replace(hub, '.put("revalidation", root.optJSONObject("revalidation") ?: JSONObject())\n', '')
replace(hub, 'root.optString("uiPipeline", "PERSISTED_REGIONS_RECONCILED_ADVISOR")', 'root.optString("uiPipeline", "PHYSICAL_EVIDENCE_ONLY")')

# Archive metadata points to the current store format, not an old facade.
replace("app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt", "SignalLearningStore.FORMAT", "BlueEvidenceStore.FORMAT")

# UI assembler is a projection only; BlueCausalEngine owns cross-fuel decisions.
write(
    "app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt",
    r'''package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Projeta a evidência física persistida para a tela de aprendizado.
 * Não reconcilia combustíveis, não gera comparação e não calcula correção.
 */
object LearningUiSnapshotAssembler {
    fun assemble(rawSnapshot: JSONObject): JSONObject {
        val root = JSONObject(rawSnapshot.toString())
        val regions = LearningEvidenceDimensions.enrichRegions(root)
            .optJSONArray("regions") ?: JSONArray()
        val epoch = root.optInt("epoch", 1).coerceAtLeast(1)
        val cells = LearningGridProjection.project(regions, epoch)
        val integrity = LearningGridProjection.integrity(
            regions = regions,
            cells = cells,
            comparisons = JSONArray(),
            epoch = epoch,
            mapHash = root.optString("mapHash", root.optString("map_hash", "")),
        )
        return root
            .put("regions", regions)
            .put("cells", cells)
            .put("integrity", integrity)
            .put("comparisons", JSONArray())
            .put("comparisonCount", 0)
            .put("comparison_count", 0)
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
            .put("uiPipeline", "PHYSICAL_EVIDENCE_ONLY")
            .put("ui_pipeline", "PHYSICAL_EVIDENCE_ONLY")
    }
}
''',
)

# Tests that instantiate the retired stores test an architecture that no longer exists.
retired_test_symbols = (
    "SignalLearningStore",
    "MotorLearningMemory",
    "LiveOnlyLearningStore",
    "DeferredLiveOnlyLearningStore",
    "LearningTelemetrySchemaMigration",
    "PetrolReferenceSelector",
    "LearningSnapshotReconciler",
    "ContinuousResidualField",
    "CurveKComparison",
)
for base_rel in ("app/src/test/java", "tests"):
    base = p(base_rel)
    if not base.exists():
        continue
    for target in list(base.rglob("*")):
        if not target.is_file() or target.suffix not in {".kt", ".py", ".cjs"}:
            continue
        if target.name in {"test_blue_source_architecture_contract.py", "test_blue_learning_storage_contract.py"}:
            continue
        try:
            text = target.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if any(symbol in text for symbol in retired_test_symbols):
            target.unlink()

# Retired decision/storage classes die instead of becoming compatibility aliases.
for rel in [
    "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt",
    "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt",
    "app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt",
    "app/src/main/java/com/omegas/prohub/learning/DeferredLiveOnlyLearningStore.kt",
    "app/src/main/java/com/omegas/prohub/learning/LearningTelemetrySchemaMigration.kt",
    "app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt",
    "app/src/main/java/com/omegas/prohub/learning/LearningSnapshotReconciler.kt",
    "app/src/main/java/com/omegas/prohub/learning/ContinuousResidualField.kt",
    "app/src/main/java/com/omegas/prohub/learning/CurveKComparison.kt",
    "app/src/main/java/com/omegas/prohub/learning/InternalLearningNamespace.kt",
]:
    delete(rel)

# A hard cut is only valid if no production source still names a retired authority.
forbidden = (
    "SignalLearningStore",
    "MotorLearningMemory",
    "LiveOnlyLearningStore",
    "DeferredLiveOnlyLearningStore",
    "LearningTelemetrySchemaMigration",
    "PetrolReferenceSelector",
    "LearningSnapshotReconciler",
    "ContinuousResidualField",
    "CurveKComparison",
    "AssistedCalibrationAdvisor",
    "advisorSnapshot",
)
violations = []
source = p("app/src/main/java/com/omegas/prohub")
for target in sorted(source.rglob("*.kt")) + sorted(source.rglob("*.java")):
    text = target.read_text(encoding="utf-8")
    for symbol in forbidden:
        if symbol in text:
            violations.append(f"{symbol}: {target.relative_to(ROOT)}")
if violations:
    raise SystemExit("Retired authority references remain:\n" + "\n".join(violations))

print("BLUE_LEARNING_HARD_CUT_APPLIED")
