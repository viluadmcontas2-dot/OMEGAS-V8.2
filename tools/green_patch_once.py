from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one patch target, found {count}: {old[:100]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


# Browser freshness advances even when rounded visual values do not change.
replace_once(
    "app/src/main/assets/ui/app.js",
    '''        const signature = "".concat(route, ":").concat(telemetryVisualSignature(telemetry, route));
        if (envelope.ok !== false && signature !== previousTelemetrySignature) {
          previousTelemetrySignature = signature;
          store.patch({ telemetry, presentRevision: Number(envelope.revision || 0) });
          const state2 = store.get();
          if (route === "dashboard") (_a = ensureScreen("dashboard")) == null ? void 0 : _a.render(state2);
          if (route === "learning" || route === "map") renderLightLiveContext(state2, route);
        }
''',
    '''        const signature = "".concat(route, ":").concat(telemetryVisualSignature(telemetry, route));
        if (envelope.ok !== false) {
          const ageValue = telemetry.ageMs != null ? telemetry.ageMs : telemetry.telemetryAgeMs;
          store.patch({
            presentRevision: Number(envelope.revision || 0),
            presentSequence: Number(telemetry.sequence || envelope.sequence || 0),
            presentAgeMs: Number(ageValue != null ? ageValue : envelope.ageMs != null ? envelope.ageMs : -1)
          });
          if (signature !== previousTelemetrySignature) {
            previousTelemetrySignature = signature;
            store.patch({ telemetry });
            const state2 = store.get();
            if (route === "dashboard") (_a = ensureScreen("dashboard")) == null ? void 0 : _a.render(state2);
            if (route === "learning" || route === "map") renderLightLiveContext(state2, route);
          }
        }
''',
)

# OBD screen gets only the narrow witness endpoint, never a full engine snapshot.
replace_once(
    "app/src/main/assets/ui/core/native-api.js",
    '''      obd() {
''',
    '''      obdWitness() {
        if (this.demo) return { state: "INSUFFICIENT", quality: 0, gnvSamples: 0, demo: true };
        return invoke(this.power, "getObdWitnessStatus", [], {});
      }
      obd() {
''',
)
replace_once(
    "app/src/main/assets/ui/screens/obd.js",
    '''        const snapshot = this.api.fullSnapshot() || {};
        const witness = snapshot.obd_witness || snapshot.obdWitness || {};
        if (witness && typeof witness === "object") this.lastWitness = witness;
''',
    '''        const witness = this.api.obdWitness() || {};
        if (witness && typeof witness === "object") this.lastWitness = witness;
''',
)

# Confirmed Map-K readback stays in RAM for OBD address resolution.
kw = "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt"
replace_once(
    kw,
    '''    private val cacheFile = File(paths.runtimeRoot, "k_map_cache.json")
    private val safetyFile = File(paths.runtimeRoot, "k_write_safety.json")
''',
    '''    private val cacheFile = File(paths.runtimeRoot, "k_map_cache.json")
    @Volatile private var confirmedMapSnapshotCache: JSONObject? = null
    private val safetyFile = File(paths.runtimeRoot, "k_write_safety.json")
''',
)
replace_once(
    kw,
    '''    fun isBusy(): Boolean = busy.get()
    fun statusJson(): String = synchronized(statusLock) { JSONObject(status.toString()).toString() }
    fun historyJson(): String = loadHistory().toString()
''',
    '''    fun isBusy(): Boolean = busy.get()
    fun statusJson(): String = synchronized(statusLock) { JSONObject(status.toString()).toString() }
    fun historyJson(): String = loadHistory().toString()
    fun confirmedMapSnapshot(): JSONObject? = confirmedMapSnapshotCache?.let { JSONObject(it.toString()) }
''',
)
replace_once(
    kw,
    '''    fun beginUsbSession(sessionId: Long) {
        val cache = loadCache()
''',
    '''    fun beginUsbSession(sessionId: Long) {
        confirmedMapSnapshotCache = null
        val cache = loadCache()
''',
)
replace_once(
    kw,
    '''            atomicWrite(cacheFile, cache.toString(2))
            try { onConfirmedWrite() } catch (_: Exception) {}
''',
    '''            atomicWrite(cacheFile, cache.toString(2))
            confirmedMapSnapshotCache = JSONObject(cache.toString())
            try { onConfirmedWrite() } catch (_: Exception) {}
''',
)
replace_once(
    kw,
    '''            atomicWrite(cacheFile, finalCache.toString(2))
            val payload = JSONObject()
''',
    '''            atomicWrite(cacheFile, finalCache.toString(2))
            confirmedMapSnapshotCache = JSONObject(finalCache.toString())
            val payload = JSONObject()
''',
)
replace_once(
    kw,
    '''        cache.put("allRows", allRows)
        atomicWrite(cacheFile, cache.toString(2))
''',
    '''        cache.put("allRows", allRows)
        atomicWrite(cacheFile, cache.toString(2))
        confirmedMapSnapshotCache = if (complete && cache.optBoolean("sessionConfirmed", false)) {
            JSONObject(cache.toString())
        } else null
''',
)
# Every session-invalidating cache write also invalidates the RAM resolver.
path = ROOT / kw
text = path.read_text(encoding="utf-8")
stale = '''            val stale = loadCache().put("sessionConfirmed", false)
            atomicWrite(cacheFile, stale.toString(2))'''
if text.count(stale) < 2:
    raise RuntimeError(f"{kw}: expected multiple session invalidation sites")
text = text.replace(stale, stale + "\n            confirmedMapSnapshotCache = null")
path.write_text(text, encoding="utf-8")

# OBD transport publishes immutable cycle/session context captured before acquisition.
obd = "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
replace_once(
    obd,
    '''    private var remoteLiveAt = 0L
    private var live = JSONObject()
''',
    '''    private var remoteLiveAt = 0L
    private var remoteSessionStartedAt = 0L
    private var live = JSONObject()
''',
)
replace_once(
    obd,
    '''    fun acceptRemoteLive(payload: JSONObject) {
        val now = System.currentTimeMillis()
''',
    '''    fun acceptRemoteLive(payload: JSONObject) {
        val now = System.currentTimeMillis()
        val remoteSessionStart = synchronized(stateLock) {
            if (remoteSessionStartedAt <= 0L || now - remoteLiveAt > 5_000L) remoteSessionStartedAt = now
            remoteSessionStartedAt
        }
''',
)
replace_once(
    obd,
    '''        if (cycle != null) publishLearningCycle(cycle, "remote")
''',
    '''        if (cycle != null) publishLearningCycle(cycle, "remote", remoteSessionStart, requestedAtMs)
''',
)
replace_once(
    obd,
    '''    private fun pollCycle(sock: BluetoothSocket) {
        val cycleStartedAt = System.currentTimeMillis()
''',
    '''    private fun pollCycle(sock: BluetoothSocket) {
        val cycleStartedAt = System.currentTimeMillis()
        val sessionStartedAtMs = synchronized(stateLock) { currentSessionStartedAt }
''',
)
replace_once(
    obd,
    '''        if (cycle != null) publishLearningCycle(cycle, "local")
''',
    '''        if (cycle != null) publishLearningCycle(cycle, "local", sessionStartedAtMs, cycleStartedAt)
''',
)
replace_once(
    obd,
    '''    private fun publishLearningCycle(cycle: ObdPidCycle, mode: String) {
''',
    '''    private fun publishLearningCycle(
        cycle: ObdPidCycle,
        mode: String,
        sessionStartedAtMs: Long,
        cycleStartedAtMs: Long,
    ) {
''',
)
replace_once(
    obd,
    '''                    .put("observedAtMs", cycle.observedAtMs)
                    .put("gnvModeDeclared", settings.obdGnvLearningEnabled)
                    .put("mode", mode),
''',
    '''                    .put("observedAtMs", cycle.observedAtMs)
                    .put("cycleStartedAtMs", cycleStartedAtMs)
                    .put("sessionStartedAtMs", sessionStartedAtMs)
                    .put("sessionId", "$mode:$sessionStartedAtMs")
                    .put("gnvModeDeclared", settings.obdGnvLearningEnabled)
                    .put("mode", mode),
''',
)

# Service hot path: async/coalesced persistence, stale-cycle rejection metadata,
# optional MP48 bridge only for Map-K address, and pre-snapshot overlay throttling.
svc = "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
replace_once(svc, 'import com.omegas.prohub.obd.ObdLearningSample\n', 'import com.omegas.prohub.obd.ObdLearningSample\nimport com.omegas.prohub.obd.ObdMapKSuggestion\n')
replace_once(svc, 'import com.omegas.prohub.storage.AppPaths\n', 'import com.omegas.prohub.storage.AppPaths\nimport com.omegas.prohub.storage.CoalescedSnapshotWriter\n')
replace_once(
    svc,
    '''    private lateinit var obdLearningFile: File
    @Volatile private var lastObdPersistAt = 0L
''',
    '''    private lateinit var obdLearningFile: File
    private lateinit var obdSnapshotWriter: CoalescedSnapshotWriter
    @Volatile private var lastObdPersistAt = 0L
''',
)
replace_once(
    svc,
    '''    private var lastNotificationAt = 0L
    private var lastUsbConnected = false
''',
    '''    private var lastNotificationAt = 0L
    private var lastOverlayAt = 0L
    private var lastUsbConnected = false
''',
)
replace_once(
    svc,
    '''        log = RingLog(1_500, paths.logFile)
        archives = DataArchiveManager(paths, log)
''',
    '''        log = RingLog(1_500, paths.logFile)
        obdSnapshotWriter = CoalescedSnapshotWriter(
            file = obdLearningFile,
            snapshotProvider = { obdLearningEngine.snapshotJson().toString() },
            onFailure = { error -> log.add("WARN", "OBD", "Falha ao persistir aprendizado STFT: ${error.message}") },
        )
        archives = DataArchiveManager(paths, log)
''',
)
replace_once(
    svc,
    '''        if (::obdLearningEngine.isInitialized) persistObdLearning(force = true)
        scheduler.shutdownNow()
''',
    '''        if (::obdLearningEngine.isInitialized) persistObdLearning(force = true)
        if (::obdSnapshotWriter.isInitialized) obdSnapshotWriter.close()
        scheduler.shutdownNow()
''',
)
replace_once(
    svc,
    '''        overlay.restoreIfAllowed()
        updateOverlay()
''',
    '''        overlay.restoreIfAllowed()
        updateOverlay(force = true)
''',
)
replace_once(
    svc,
    '''        val result = overlay.setEnabled(enabled)
        updateOverlay()
''',
    '''        val result = overlay.setEnabled(enabled)
        updateOverlay(force = true)
''',
)
replace_once(
    svc,
    '''    private fun updateOverlay() {
        if (!::overlay.isInitialized || (!overlay.requestedEnabled() && !overlay.visible())) return
        val hub = status()
''',
    '''    private fun updateOverlay(force: Boolean = false) {
        if (!::overlay.isInitialized || (!overlay.requestedEnabled() && !overlay.visible())) return
        val now = System.currentTimeMillis()
        if (!force && now - lastOverlayAt < 250L) return
        lastOverlayAt = now
        val hub = status()
''',
)
old_consume = '''    private fun consumeObdLearningSample(sample: JSONObject) {
        if (sample.optString("kind") != "OBD_GNV_CYCLE") return
        val learningSample = ObdLearningSample(
            observedAtMs = sample.optLong("observedAtMs", -1L),
            rpm = sample.optDouble("rpm", Double.NaN),
            mapBar = sample.optDouble("map_bar", Double.NaN),
            stftPct = sample.optDouble("stft", Double.NaN),
            acquisitionSpanMs = sample.optLong("acquisitionSpanMs", -1L),
            epoch = obdLearningEngine.currentEpoch(),
            gnvModeDeclared = sample.optBoolean("gnvModeDeclared", false),
        )
        if (!obdLearningEngine.observe(learningSample)) return
        val result = obdLearningEngine.evaluate(learningSample.rpm, learningSample.mapBar)
        persistObdLearning()
        val witness = JSONObject()
            .put("source", "OBD_INDEPENDENT_STFT_GNV")
            .put("state", result.state.name)
            .put("stftMedianPct", result.stftMedianPct ?: JSONObject.NULL)
            .put("gnvStftPct", result.stftMedianPct ?: JSONObject.NULL)
            .put("correctionMultiplier", result.correctionMultiplier ?: JSONObject.NULL)
            .put("correctionPercent", result.correctionMultiplier?.let { (it - 1.0) * 100.0 } ?: JSONObject.NULL)
            .put("quality", result.quality)
            .put("sampleCount", result.sampleCount)
            .put("gnvSamples", result.sampleCount)
            .put("rpm", learningSample.rpm)
            .put("map_bar", learningSample.mapBar)
            .put("epoch", result.epoch)
            .put("gnvModeDeclared", true)
            .put("automaticWrite", false)
            .put("mapKProposalState", if (result.correctionMultiplier != null) "ADDRESS_UNRESOLVED" else "INSUFFICIENT")
            .put("observedAtMs", learningSample.observedAtMs)
        latestObdWitness = witness
        sessionRecorder.record("obd_learning", "obd", witness, force = true)
    }
'''
new_consume = '''    private fun consumeObdLearningSample(sample: JSONObject) {
        if (sample.optString("kind") != "OBD_GNV_CYCLE") return
        val observedAtMs = sample.optLong("observedAtMs", -1L)
        val acquisitionSpanMs = sample.optLong("acquisitionSpanMs", -1L)
        val sessionStartedAtMs = sample.optLong("sessionStartedAtMs", 0L).coerceAtLeast(0L)
        val sessionId = sample.optString("sessionId").ifBlank { "${sample.optString("mode", "unknown")}:$sessionStartedAtMs" }
        val cycleStartedAtMs = sample.optLong(
            "cycleStartedAtMs",
            (observedAtMs - acquisitionSpanMs.coerceAtLeast(0L)).coerceAtLeast(0L),
        )
        val learningSample = ObdLearningSample(
            observedAtMs = observedAtMs,
            rpm = sample.optDouble("rpm", Double.NaN),
            mapBar = sample.optDouble("map_bar", Double.NaN),
            stftPct = sample.optDouble("stft", Double.NaN),
            acquisitionSpanMs = acquisitionSpanMs,
            epoch = obdLearningEngine.currentEpoch(),
            gnvModeDeclared = sample.optBoolean("gnvModeDeclared", false),
            sessionId = sessionId,
            sessionStartedAtMs = sessionStartedAtMs,
            cycleStartedAtMs = cycleStartedAtMs,
        )
        if (!obdLearningEngine.observe(learningSample, nowMs = System.currentTimeMillis())) return
        val result = obdLearningEngine.evaluate(learningSample.rpm, learningSample.mapBar)
        persistObdLearning()

        val mp48 = telemetryStore.telemetryCopy()
        val mp48Fuel = mp48.optString("fuel", mp48.optString("state", "")).uppercase()
        val mp48Rpm = mp48.optDouble("rpm", Double.NaN)
        val mp48Map = mp48.optDouble("load_bar", mp48.optDouble("map_bar", Double.NaN))
        val mp48PetrolMs = mp48.optDouble("petrol_ms", Double.NaN)
        val rpmWindow = kotlin.math.max(125.0, learningSample.rpm * 0.05)
        val bridgeValid = mp48Fuel == "GNV" && telemetryStore.ageMs() <= 1_500L &&
            mp48Rpm.isFinite() && mp48Map.isFinite() && mp48PetrolMs.isFinite() && mp48PetrolMs > 0.0 &&
            kotlin.math.abs(mp48Rpm - learningSample.rpm) <= rpmWindow &&
            kotlin.math.abs(mp48Map - learningSample.mapBar) <= 0.08
        val resolvedPetrolMs = mp48PetrolMs.takeIf { bridgeValid }
        val mapProposal = ObdMapKSuggestion.prepare(
            learning = result,
            rpm = learningSample.rpm,
            resolvedPetrolMs = resolvedPetrolMs,
            mapRows = kWriter.confirmedMapSnapshot()?.optJSONArray("rows"),
            addressConfidence = if (bridgeValid) 0.95 else 0.0,
        )
        val witness = JSONObject()
            .put("source", "OBD_INDEPENDENT_STFT_GNV")
            .put("state", result.state.name)
            .put("stftMedianPct", result.stftMedianPct ?: JSONObject.NULL)
            .put("gnvStftPct", result.stftMedianPct ?: JSONObject.NULL)
            .put("correctionMultiplier", result.correctionMultiplier ?: JSONObject.NULL)
            .put("correctionPercent", result.correctionMultiplier?.let { (it - 1.0) * 100.0 } ?: JSONObject.NULL)
            .put("quality", result.quality)
            .put("sampleCount", result.sampleCount)
            .put("gnvSamples", result.sampleCount)
            .put("rpm", learningSample.rpm)
            .put("map_bar", learningSample.mapBar)
            .put("petrol_ms", resolvedPetrolMs ?: JSONObject.NULL)
            .put("epoch", result.epoch)
            .put("sessionId", learningSample.sessionId)
            .put("sessionStartedAtMs", learningSample.sessionStartedAtMs)
            .put("cycleStartedAtMs", learningSample.cycleStartedAtMs)
            .put("calibrationState", blueCalibrationStateId())
            .put("gnvModeDeclared", true)
            .put("automaticWrite", false)
            .put("mapKProposalState", mapProposal.optString("state", "ADDRESS_UNRESOLVED"))
            .put("mapKProposal", mapProposal)
            .put("observedAtMs", learningSample.observedAtMs)
        latestObdWitness = witness
        sessionRecorder.record("obd_learning", "obd", witness, force = true)
    }
'''
replace_once(svc, old_consume, new_consume)
replace_once(
    svc,
    '''    private fun persistObdLearning(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastObdPersistAt < 5_000L) return
        try {
            val temporary = File(obdLearningFile.parentFile, obdLearningFile.name + ".tmp")
            temporary.writeText(obdLearningEngine.snapshotJson().toString(), Charsets.UTF_8)
            if (!temporary.renameTo(obdLearningFile)) {
                obdLearningFile.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
                temporary.delete()
            }
            lastObdPersistAt = now
        } catch (error: Exception) {
            log.add("WARN", "OBD", "Falha ao persistir aprendizado STFT: ${error.message}")
        }
    }
''',
    '''    private fun persistObdLearning(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastObdPersistAt < 5_000L) return
        try {
            if (force) obdSnapshotWriter.flush() else obdSnapshotWriter.request()
            lastObdPersistAt = now
        } catch (error: Exception) {
            log.add("WARN", "OBD", "Falha ao agendar persistência STFT: ${error.message}")
        }
    }
''',
)
replace_once(
    svc,
    '''        obdLearningEngine.beginEpoch("obd-${System.currentTimeMillis()}")
''',
    '''        val startedAtMs = System.currentTimeMillis()
        obdLearningEngine.beginEpoch("obd-$startedAtMs", startedAtMs = startedAtMs)
''',
)

# Causal coordinator lets the attribution gate decide continuous region match.
coord = "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt"
replace_once(coord, 'import com.omegas.prohub.blue.BlueMapKAddressing\n', 'import com.omegas.prohub.blue.BlueMapKAddressing\nimport com.omegas.prohub.blue.BlueScientificRegion\n')
replace_once(
    coord,
    '''        val after = current.activeComparisons().asSequence()
            .filter { it.scientificRegionId == intervention.scientificRegionId }
            .filter { it.createdAtMs >= intervention.confirmedAtMs }
            .maxByOrNull(FuelComparison::createdAtMs) ?: return
        val result = attribution.evaluate(intervention, before, after)
        if (result.state == BlueAttributionState.ACCEPTED) latestGainObservation = result.observation
''',
    '''        val accepted = current.activeComparisons().asSequence()
            .filter { it.createdAtMs >= intervention.confirmedAtMs }
            .sortedByDescending(FuelComparison::createdAtMs)
            .map { attribution.evaluate(intervention, before, it) }
            .firstOrNull { it.state == BlueAttributionState.ACCEPTED }
            ?: return
        latestGainObservation = accepted.observation
''',
)
replace_once(
    coord,
    '''        val observation = latestGainObservation?.takeIf {
            it.afterRevision == comparison.revision &&
                it.scientificRegionId == comparison.scientificRegionId
        }
''',
    '''        val observation = latestGainObservation?.takeIf {
            it.afterRevision == comparison.revision &&
                BlueScientificRegion.parse(it.scientificRegionId)?.physicallyMatches(comparison.rpm, comparison.mapBar) == true
        }
''',
)

# STATUS is part of the candidate before any green CI claim.
status = ROOT / "STATUS.md"
status.write_text(
    status.read_text(encoding="utf-8") + '''

## Functional recovery candidate — 2026-09-13

- Epic: #30; workstreams #31–#35; physical gate #25 remains separate/open.
- Proven RED lineage: `f77242c226626d8be131a735dacbed922af7f688` → `16fce3200a9f03f395d2ca7001a03f2ed8c408b2` → `e09769f60ebe5f593296809afa0eef310ee61eb3`.
- This successor contains the GREEN implementation for scientific region identity, causal participation, OBD lifecycle/hot path, UI freshness/backpressure, Map-K addressing and consumption evidence.
- Verification state in this file is intentionally `PENDING`: only GitHub Actions FAST + FULL on the exact final SHA may promote the software gate.
- APK remains blocked until the exact-SHA software gate is green and the owner-authorized rerun executes.
- No physical vehicle, ELM-universal, economy, ANR/soak, battery or API 26–35 claim is implied.
''',
    encoding="utf-8",
)

print("GREEN_PATCH_APPLIED")
