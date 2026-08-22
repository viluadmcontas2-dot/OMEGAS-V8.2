package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private fun List<Double>.medianSafe(): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else sorted[middle]
}

private data class EvidenceStateSnapshot(
    val frameSequence: Long,
    val nativeEvidence: List<NativeEcuEvidence>,
    val nativeAnchors: List<NativeLearningAnchor>,
    val visitAccumulators: List<VisitComparisonAccumulator>,
    val provenanceHistory: List<EvidenceProvenance>,
    val performance: LearningPerformanceMetrics,
    val nativeSnapshotsEvicted: Long,
    val visitAccumulatorsEvicted: Long,
)

/**
 * Fachada única V6 sobre a memória persistida.
 *
 * A interface pode reavaliar o motor com frequência. A memória preserva todas as
 * evidências válidas: janelas novas entram com peso normal e janelas que reutilizam
 * leituras já representadas entram com peso proporcional aos quadros realmente novos,
 * sem criar outro voto físico.
 */
class SignalLearningStore(
    stateFile: File,
    log: RingLog,
) {
    companion object {
        const val FORMAT = "omegas-learning-v6-mp48-v4"
        const val LEGACY_FORMAT = "omegas-learning-v5-mp48-v3"
        const val LEGACY_FORMAT_OLD = "omegas-learning-v5-mp48-v2"
        const val DATA_REVISION = 4
        const val LEGACY_DATA_REVISION = 3
        const val EVIDENCE_STATE_SCHEMA = "omegas-learning-evidence-v6-v3"
    }

    private val delegate = MotorLearningMemory(stateFile, log)
    private val evidenceStateFile = File(stateFile.parentFile ?: stateFile.absoluteFile.parentFile, "learning_v6_evidence.json")
    private val evidenceStateWriter = CoalescedSnapshotWriter(
        target = evidenceStateFile,
        threadName = "omegas-learning-evidence-persist",
    )
    private val persistenceGate = MaterialPersistenceGate()
    private var visibleDecision: SampleDecision? = null
    private var memoryDecision: SampleDecision? = null
    private val lastRepresentedWindowEndByFuel = linkedMapOf<String, Long>()
    private var independentSamples = 0L
    private var correlatedSamplesWeighted = 0L
    private var duplicateSamplesIgnored = 0L
    private var newFramesAbsorbed = 0L
    private var eligibleFramesEvaluated = 0L
    private var lifetimeIndependentSamples = 0L
    private var lifetimeCorrelatedSamplesWeighted = 0L
    private var lifetimeDuplicateSamplesIgnored = 0L
    private var lifetimeNewFramesAbsorbed = 0L
    private var lifetimeEligibleFramesEvaluated = 0L
    private var lastNovelty = ContinuousWindowNovelty.Result(0, 1, 0.0, 0L)
    private val evidenceLock = Any()
    private val equivalenceLock = Any()
    private val equivalenceRuntime = PersistentEquivalenceRuntime(stateFile)
    private var lastEquivalenceEstimate: EquivalenceRuntime.EquivalenceEstimate? = null
    private val nativeEvidence = linkedMapOf<String, NativeEcuEvidence>()
    private val nativeAnchors = NativeLearningAnchorRegistry(LearningEvidenceBudget.MAX_NATIVE_ANCHORS)
    private val visitAccumulators = linkedMapOf<String, VisitComparisonAccumulator>()
    private val provenanceHistory = ArrayDeque<EvidenceProvenance>()
    private var frameSequence = 0L
    private var performance = LearningPerformanceMetrics()
    private var nativeSnapshotsEvicted = 0L
    private var visitAccumulatorsEvicted = 0L
    private val advisorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-learning-advisor").apply { isDaemon = true }
    }
    private val advisorRefreshPending = AtomicBoolean(false)
    private val advisorRefreshDirty = AtomicBoolean(false)
    private val advisorRevisionGate = AdvisorRevisionGate()
    private val advisorRequestedRevision = AtomicLong(0L)
    private val advisorPublishedRevision = AtomicLong(0L)
    @Volatile private var advisor = initialAdvisor()

    init {
        loadEvidenceState()
        migrateLegacyPetrolReferences()
        scheduleAdvisorRefresh(advisorRevisionGate.force())
    }

    fun startSession(): JSONObject {
        resetConnectionCounters()
        visibleDecision = null
        memoryDecision = null
        return decorate(delegate.startSession())
    }

    fun endSession(reason: String): JSONObject {
        visibleDecision = null
        memoryDecision = null
        lastRepresentedWindowEndByFuel.clear()
        val result = decorate(delegate.endSession(reason))
        persistEvidenceState(forceBoundary = true)
        evidenceStateWriter.flush(2_000L)
        equivalenceRuntime.flush(2_000L)
        return result
    }

    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject {
        visibleDecision = decision
        val source = decision.sample
        val sequenceBefore = synchronized(evidenceLock) {
            performance = performance.copy(framesReceived = performance.framesReceived + 1L)
            val before = frameSequence
            if (source != null) frameSequence += source.frameCount.toLong().coerceAtLeast(0L)
            before
        }
        val prepared = if (decision.learningEligible && source != null) {
            val fuelKey = source.fuel.wireName
            val novelty = ContinuousWindowNovelty.calculate(
                startedAtElapsedMs = source.startedAtElapsedMs,
                endedAtElapsedMs = source.endedAtElapsedMs,
                frameCount = source.frameCount,
                medianIntervalMs = source.diagnostics.medianIntervalMs,
                previouslyRepresentedThroughElapsedMs = lastRepresentedWindowEndByFuel[fuelKey],
            )
            synchronized(evidenceLock) {
                performance = performance.copy(
                    validFrames = performance.validFrames + novelty.totalFrames,
                    windowsEvaluated = performance.windowsEvaluated + 1L,
                    earlySamplesAccepted = performance.earlySamplesAccepted + if (source.frameCount < 10) 1L else 0L,
                    newFrames = performance.newFrames + novelty.newFrames,
                    reusedFrames = performance.reusedFrames + (novelty.totalFrames - novelty.newFrames),
                    usefulWeight = performance.usefulWeight + novelty.fraction * source.quality,
                    firstEstimateAtMs = performance.firstEstimateAtMs ?: source.endedAtElapsedMs,
                )
                if (novelty.newFrames > 0) {
                    provenanceHistory.addLast(
                        EvidenceProvenance(
                            firstFrameSequence = sequenceBefore + 1L,
                            lastFrameSequence = frameSequence,
                            newFrameCount = novelty.newFrames,
                            reusedFrameCount = (novelty.totalFrames - novelty.newFrames).coerceAtLeast(0),
                            noveltyRatio = novelty.fraction,
                        ),
                    )
                    while (provenanceHistory.size > LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES) {
                        provenanceHistory.removeFirst()
                    }
                }
            }
            lastRepresentedWindowEndByFuel[fuelKey] = novelty.representedThroughElapsedMs
            lastNovelty = novelty
            eligibleFramesEvaluated += novelty.totalFrames
            lifetimeEligibleFramesEvaluated += novelty.totalFrames
            newFramesAbsorbed += novelty.newFrames
            lifetimeNewFramesAbsorbed += novelty.newFrames

            when {
                novelty.duplicate -> {
                    duplicateSamplesIgnored += 1
                    lifetimeDuplicateSamplesIgnored += 1
                    decision.copy(
                        reason = "Janela já representada integralmente; nenhum quadro novo foi contabilizado.",
                        sample = null,
                        learningEligible = false,
                        reasonCode = "DUPLICATE_WINDOW_IGNORED",
                    )
                }
                !novelty.fullyNew -> {
                    correlatedSamplesWeighted += 1
                    lifetimeCorrelatedSamplesWeighted += 1
                    decision.copy(
                        reason = "Motor estável; ${novelty.newFrames}/${novelty.totalFrames} quadros novos absorvidos proporcionalmente.",
                        sample = source.copy(
                            quality = (source.quality * novelty.fraction).coerceIn(0.0, 1.0),
                        ),
                        learningEligible = true,
                        reasonCode = "OVERLAPPING_WINDOW_WEIGHTED",
                    )
                }
                else -> {
                    independentSamples += 1
                    lifetimeIndependentSamples += 1
                    decision
                }
            }
        } else {
            decision
        }
        memoryDecision = prepared

        var advisorEstimate: EquivalenceRuntime.EquivalenceEstimate? = null
        var advisorRpm = Double.NaN
        var advisorMap = Double.NaN
        if (decision.learningEligible && source != null && lastNovelty.fraction > 0.0) {
            val lane = when (source.fuel) {
                Mp48Fuel.PETROL -> FuelLane.PETROL_REFERENCE
                Mp48Fuel.CNG -> FuelLane.CNG_PETROL_OBSERVED
                else -> null
            }
            if (lane != null) {
                val stability = EquivalenceEvidenceWeight.from(source.diagnostics).stability
                synchronized(equivalenceLock) {
                    val observed = equivalenceRuntime.observe(
                        lane = lane,
                        rpm = source.rpm,
                        mapBar = source.mapBar,
                        petrolTinjMs = source.petrolMs,
                        stability = stability,
                        novelty = lastNovelty.fraction,
                        materialRevision = frameSequence,
                    )
                    lastEquivalenceEstimate = observed.estimate
                    if (observed.estimate != null) {
                        advisorEstimate = observed.estimate
                        advisorRpm = source.rpm
                        advisorMap = source.mapBar
                    }
                }
            }
        }
        if ((!decision.learningEligible || source == null) &&
            CurrentEquivalenceStatusPolicy.allowsCachedEstimate(decision)
        ) {
            // Current status follows a bounded local query while the next sample forms.
            // No history, JSON, disk or Advisor snapshot work enters the telemetry hot path.
            synchronized(equivalenceLock) {
                lastEquivalenceEstimate = equivalenceRuntime.estimate(
                    rpm = telemetry.rpm.toDouble(),
                    mapBar = telemetry.mapBar,
                )
            }
        }
        advisorEstimate?.let { requestAdvisorRefresh(it, advisorRpm, advisorMap) }

        val nativePetrolPriors = if (prepared.learningEligible && prepared.sample?.fuel?.wireName in setOf("GNV", "CNG")) {
            synchronized(evidenceLock) { nativeAnchors.snapshot().filter { it.sourceFuel == "PETROL" } }
        } else {
            emptyList()
        }
        val result = NativePetrolPriorScope.withAnchors(nativePetrolPriors) {
            delegate.ingest(telemetry, prepared)
        }
        result.optJSONObject("comparison")?.let { comparison ->
            val visitId = comparison.optString("visit_id")
            val regionId = comparison.optString("reference_region_id")
            if (visitId.isNotBlank() && regionId.isNotBlank()) {
                val key = "$visitId:$regionId"
                val noveltyFraction = if (prepared.sample == null) 0.0 else {
                    if (prepared.sample === source && lastNovelty.totalFrames > 0) lastNovelty.fraction else 1.0
                }
                val weight = (comparison.optDouble("quality", 0.0) * noveltyFraction).coerceIn(0.0, 1.0)
                val independent = noveltyFraction >= 0.999
                synchronized(evidenceLock) {
                    val updated = (visitAccumulators[key] ?: VisitComparisonAccumulator(key = key)).add(
                        error = comparison.optDouble("error_pct", 0.0),
                        sampleWeight = weight,
                        independent = independent,
                        nowMs = comparison.optLong("captured_at", System.currentTimeMillis()),
                    )
                    visitAccumulators[key] = updated
                    trimVisitAccumulatorsLocked()
                }
            }
        }
        if (prepared.learningEligible && prepared.sample != null) {
            persistenceGate.markMaterialChange()
            persistEvidenceState()
        }
        return decorate(result, includeAdvisor = false)
    }

    fun statusJson(): JSONObject = decorate(delegate.statusJson())

    fun export(deviceId: String): JSONObject {
        val exported = delegate.export(deviceId)
        val evidence = evidenceSnapshot()
        return exported
            .put("format", FORMAT)
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("learningDataRevision", DATA_REVISION)
            .put("signalDrivenV5", true)
            .put("visitPolicy", "physical-region-exit")
            .put("sampleWeightingPolicy", "continuous-frame-novelty-per-fuel")
            .put("independentSamples", independentSamples)
            .put("correlatedSamplesWeighted", correlatedSamplesWeighted)
            .put("duplicateSamplesIgnored", duplicateSamplesIgnored)
            .put("newFramesAbsorbed", newFramesAbsorbed)
            .put("eligibleFramesEvaluated", eligibleFramesEvaluated)
            .put("lifetimeIndependentSamples", lifetimeIndependentSamples)
            .put("lifetimeCorrelatedSamplesWeighted", lifetimeCorrelatedSamplesWeighted)
            .put("lifetimeDuplicateSamplesIgnored", lifetimeDuplicateSamplesIgnored)
            .put("lifetimeNewFramesAbsorbed", lifetimeNewFramesAbsorbed)
            .put("lifetimeEligibleFramesEvaluated", lifetimeEligibleFramesEvaluated)
            .put("cumulativeEvidencePreserved", true)
            .put("sessionMetadataPolicy", "timestamp-organization-only-cumulative-memory")
            .put("equivalencePolicy", "rpm-map-petrol-tinj-continuous-v1")
            .put("primaryEquivalence", primaryEquivalenceJson(camelCase = true))
            .put("assistedCalibration", advisor)
            .put("advisorRevision", advisorRequestedRevision.get())
            .put("advisorPublishedRevision", advisorPublishedRevision.get())
            .put("advisorFresh", advisorPublishedRevision.get() >= advisorRequestedRevision.get())
            .put("automaticCalibration", false)
            .put("realSampleTimePreserved", true)
            .put("gasConditionPreserved", true)
            .put("crossFuelGasTemperature", false)
            .put("nativeEcuEvidence", JSONArray(evidence.nativeEvidence.map { it.toJson() }))
            .put("nativeLearningAnchors", JSONArray(evidence.nativeAnchors.map { it.toJson() }))
            .put("visitAccumulators", JSONArray(evidence.visitAccumulators.map { it.toJson() }))
            .put("evidenceProvenance", JSONArray(evidence.provenanceHistory.map { it.toJson() }))
            .put("evidenceStateSchema", EVIDENCE_STATE_SCHEMA)
            .put("evidenceBudget", evidenceBudgetJson(evidence))
            .put("adaptiveConfidence", adaptiveConfidenceJson())
            .put("performanceMetrics", evidence.performance.toJson())
            .put("evidencePersistence", evidenceStateWriter.metricsJson().put("materialGate", persistenceGate.metricsJson()))
    }

    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject {
        val incomingFormat = payload.optString("format")
        val incomingRevision = payload.optInt("learningDataRevision", 0)
        if (incomingFormat !in setOf(FORMAT, LEGACY_FORMAT, LEGACY_FORMAT_OLD) ||
            payload.optString("telemetryScaleSchema") != Mp48Protocol.TELEMETRY_SCALE_SCHEMA ||
            incomingRevision !in setOf(DATA_REVISION, LEGACY_DATA_REVISION)
        ) {
            return JSONObject()
                .put("ok", false)
                .put("error", "Memória incompatível com a escala MP48 atual")
                .put("requiredFormat", FORMAT)
                .put("requiredTelemetryScale", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
                .put("requiredLearningDataRevision", DATA_REVISION)
        }
        val internalPayload = JSONObject(payload.toString())
            .put("format", MotorLearningMemory.FORMAT)
        val result = delegate.merge(internalPayload, localDeviceId)
        if (migrateLegacyPetrolReferences() > 0) {
            scheduleAdvisorRefresh(advisorRevisionGate.force())
        }
        return decorate(result)
    }

    /** Importa contexto nativo tipado e âncoras observacionais; nunca cria comparação nem dispara writer. */
    fun importNativeSnapshot(snapshot: JSONObject): JSONObject {
        val snapshotId = snapshot.optString("snapshotId", snapshot.optString("sessionId"))
        if (snapshotId.isBlank()) return JSONObject().put("ok", false).put("error", "Snapshot nativo sem identificador")
        val fields = snapshot.optJSONArray("fields") ?: JSONArray()
        val calibrationEpoch = delegate.statusJson().optInt("epoch", 1).coerceAtLeast(1)

        fun validRaw(fieldKey: String): JSONArray? {
            repeat(fields.length()) { index ->
                val field = fields.optJSONObject(index) ?: return@repeat
                if (field.optString("status") == "VALID" && field.optString("key") == fieldKey) {
                    return field.optJSONArray("rawValues")
                }
            }
            return null
        }

        val countRaw = validRaw("NUM_BUF_UPD_GAS")
        val petrolOnCngRaw = validRaw("PETR_INJ_TBUF_GAS")
        val mapOnCngRaw = validRaw("MNFLD_PRESS_BUF_GAS")
        val bandCount = maxOf(
            countRaw?.length() ?: 0,
            petrolOnCngRaw?.length() ?: 0,
            mapOnCngRaw?.length() ?: 0,
        ).coerceAtMost(18)
        var imported = 0
        var anchorsImported = 0

        synchronized(evidenceLock) {
            repeat(bandCount) { band ->
                val count = countRaw?.takeIf { band < it.length() }?.optInt(band, 0) ?: 0
                val petrolOnCng = petrolOnCngRaw?.takeIf { band < it.length() }?.optInt(band)
                val mapOnCng = mapOnCngRaw?.takeIf { band < it.length() }?.optInt(band)
                val presentSignals = listOf(countRaw, petrolOnCngRaw, mapOnCngRaw).count { raw -> raw != null && band < raw.length() }
                if (presentSignals == 0) return@repeat
                val key = "$snapshotId:$band"
                nativeEvidence[key] = NativeEcuEvidence(
                    snapshotId = snapshotId,
                    bandIndex = band,
                    count = count,
                    coverageQuality = presentSignals / 3.0,
                    petrolTimeRaw = petrolOnCng,
                    cngTimeRaw = null,
                    mapRaw = mapOnCng,
                )
                imported++
            }
            trimNativeEvidenceLocked()

            val maturityEvents = snapshot.optJSONArray("nativeMaturityEvents") ?: JSONArray()
            repeat(maturityEvents.length()) { index ->
                val event = maturityEvents.optJSONObject(index) ?: return@repeat
                val anchor = NativeLearningAnchor.fromMaturityEvent(event, calibrationEpoch) ?: return@repeat
                if (nativeAnchors.upsert(anchor)) anchorsImported += 1
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "ECU_NATIVE")
            .put("importedBands", imported)
            .put("importedNativeAnchors", anchorsImported)
            .put("nativeAnchorCount", synchronized(evidenceLock) { nativeAnchors.snapshot().size })
            .put("calibrationEpoch", calibrationEpoch)
            .put("automaticCalibration", false)
            .put("manualOnly", true)
            .also {
                if (imported > 0 || anchorsImported > 0) {
                    persistenceGate.markMaterialChange()
                    persistEvidenceState()
                }
            }
    }

    fun onCalibrationAdjustment(payload: JSONObject): JSONObject {
        resetConnectionCounters()
        synchronized(evidenceLock) { nativeAnchors.clear() }
        synchronized(equivalenceLock) {
            equivalenceRuntime.clearCngForCalibrationAdjustment()
            lastEquivalenceEstimate = null
        }
        val result = delegate.onCalibrationAdjustment(payload)
        scheduleAdvisorRefresh(advisorRevisionGate.force())
        persistenceGate.markMaterialChange()
        persistEvidenceState(forceBoundary = true)
        return decorate(result)
    }

    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject =
        delegate.previewKWrite(row, column, value)

    private fun resetConnectionCounters() {
        independentSamples = 0L
        correlatedSamplesWeighted = 0L
        duplicateSamplesIgnored = 0L
        newFramesAbsorbed = 0L
        eligibleFramesEvaluated = 0L
        lastRepresentedWindowEndByFuel.clear()
        lastNovelty = ContinuousWindowNovelty.Result(0, 1, 0.0, 0L)
    }

    private fun trimNativeEvidenceLocked() {
        val beforeSnapshots = nativeEvidence.values.map { it.snapshotId }.distinct().size
        val retained = LearningEvidenceBudget.retainNewestSnapshotGroups(
            nativeEvidence.values.toList(),
            snapshotId = { it.snapshotId },
        )
        if (retained.size == nativeEvidence.size) return
        nativeEvidence.clear()
        retained.forEach { item -> nativeEvidence["${item.snapshotId}:${item.bandIndex}"] = item }
        val afterSnapshots = nativeEvidence.values.map { it.snapshotId }.distinct().size
        nativeSnapshotsEvicted += (beforeSnapshots - afterSnapshots).coerceAtLeast(0)
    }

    private fun trimVisitAccumulatorsLocked() {
        if (visitAccumulators.size <= LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS) return
        val before = visitAccumulators.size
        val retained = LearningEvidenceBudget.retainNewestVisits(
            visitAccumulators.values.toList(),
            lastSeenAt = { it.lastSeenAt },
        )
        visitAccumulators.clear()
        retained.forEach { item -> visitAccumulators[item.key] = item }
        visitAccumulatorsEvicted += (before - visitAccumulators.size).coerceAtLeast(0)
    }

    private fun evidenceSnapshot(): EvidenceStateSnapshot = synchronized(evidenceLock) {
        EvidenceStateSnapshot(
            frameSequence = frameSequence,
            nativeEvidence = nativeEvidence.values.toList(),
            nativeAnchors = nativeAnchors.snapshot(),
            visitAccumulators = visitAccumulators.values.toList(),
            provenanceHistory = provenanceHistory.toList(),
            performance = performance,
            nativeSnapshotsEvicted = nativeSnapshotsEvicted,
            visitAccumulatorsEvicted = visitAccumulatorsEvicted,
        )
    }

    private fun evidenceBudgetJson(snapshot: EvidenceStateSnapshot = evidenceSnapshot()): JSONObject = JSONObject()
        .put("maxPersistedBytes", LearningEvidenceBudget.MAX_PERSISTED_BYTES)
        .put("maxNativeSnapshots", LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS)
        .put("maxNativeAnchors", LearningEvidenceBudget.MAX_NATIVE_ANCHORS)
        .put("maxVisitAccumulators", LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS)
        .put("maxProvenanceEntries", LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES)
        .put("nativeSnapshots", snapshot.nativeEvidence.map { it.snapshotId }.distinct().size)
        .put("nativeBands", snapshot.nativeEvidence.size)
        .put("nativeAnchors", snapshot.nativeAnchors.size)
        .put("visitAccumulators", snapshot.visitAccumulators.size)
        .put("provenanceEntries", snapshot.provenanceHistory.size)
        .put("nativeSnapshotsEvicted", snapshot.nativeSnapshotsEvicted)
        .put("visitAccumulatorsEvicted", snapshot.visitAccumulatorsEvicted)

    private fun buildEvidencePayload(snapshot: EvidenceStateSnapshot): String {
        var native = LearningEvidenceBudget.retainNewestSnapshotGroups(
            snapshot.nativeEvidence,
            snapshotId = { it.snapshotId },
        )
        var anchors = LearningEvidenceBudget.retainNewestEntries(
            snapshot.nativeAnchors,
            LearningEvidenceBudget.MAX_NATIVE_ANCHORS,
        )
        var visits = LearningEvidenceBudget.retainNewestVisits(
            snapshot.visitAccumulators,
            lastSeenAt = { it.lastSeenAt },
        )
        var provenance = LearningEvidenceBudget.retainNewestEntries(snapshot.provenanceHistory)
        var byteCompacted = false

        fun build(): JSONObject = JSONObject()
            .put("schema", EVIDENCE_STATE_SCHEMA)
            .put("frameSequence", snapshot.frameSequence)
            .put("nativeEcuEvidence", JSONArray(native.map { it.toJson() }))
            .put("nativeLearningAnchors", JSONArray(anchors.map { it.toJson() }))
            .put("visitAccumulators", JSONArray(visits.map { it.toJson() }))
            .put("evidenceProvenance", JSONArray(provenance.map { it.toJson() }))
            .put("performanceMetrics", snapshot.performance.toJson())
            .put(
                "evidenceBudget",
                evidenceBudgetJson(
                    snapshot.copy(
                        nativeEvidence = native,
                        nativeAnchors = anchors,
                        visitAccumulators = visits,
                        provenanceHistory = provenance,
                    ),
                ).put("byteCompacted", byteCompacted),
            )

        var root = build()
        var encoded = root.toString()
        while (encoded.toByteArray(Charsets.UTF_8).size > LearningEvidenceBudget.MAX_PERSISTED_BYTES &&
            (visits.isNotEmpty() || native.isNotEmpty() || anchors.isNotEmpty() || provenance.isNotEmpty())
        ) {
            byteCompacted = true
            when {
                visits.size > 32 -> visits = LearningEvidenceBudget.retainNewestVisits(
                    visits,
                    lastSeenAt = { it.lastSeenAt },
                    maxEntries = maxOf(32, visits.size - 16),
                )
                native.map { it.snapshotId }.distinct().size > 4 -> {
                    val snapshotCount = native.map { it.snapshotId }.distinct().size
                    native = LearningEvidenceBudget.retainNewestSnapshotGroups(
                        native,
                        snapshotId = { it.snapshotId },
                        maxSnapshots = maxOf(4, snapshotCount - 1),
                    )
                }
                anchors.size > 32 -> anchors = LearningEvidenceBudget.retainNewestEntries(
                    anchors,
                    maxOf(32, anchors.size - 16),
                )
                provenance.size > 16 -> provenance = provenance.drop(1)
                visits.isNotEmpty() -> visits = LearningEvidenceBudget.retainNewestVisits(
                    visits,
                    lastSeenAt = { it.lastSeenAt },
                    maxEntries = visits.size - 1,
                )
                native.isNotEmpty() -> {
                    val ids = native.map { it.snapshotId }.distinct()
                    native = LearningEvidenceBudget.retainNewestSnapshotGroups(
                        native,
                        snapshotId = { it.snapshotId },
                        maxSnapshots = (ids.size - 1).coerceAtLeast(0),
                    )
                }
                anchors.isNotEmpty() -> anchors = LearningEvidenceBudget.retainNewestEntries(anchors, anchors.size - 1)
                provenance.isNotEmpty() -> provenance = provenance.drop(1)
            }
            root = build()
            encoded = root.toString()
        }
        return encoded
    }

    private fun loadEvidenceState() {
        if (!evidenceStateFile.isFile) return
        try {
            val root = JSONObject(evidenceStateFile.readText(Charsets.UTF_8))
            synchronized(evidenceLock) {
                frameSequence = root.optLong("frameSequence", 0L)
                root.optJSONArray("nativeEcuEvidence")?.let { array ->
                    repeat(array.length()) { index ->
                        val raw = array.optJSONObject(index) ?: return@repeat
                        val id = raw.optString("snapshotId")
                        val band = raw.optInt("bandIndex", -1)
                        if (id.isNotBlank() && band >= 0) nativeEvidence["$id:$band"] = NativeEcuEvidence(
                            snapshotId = id,
                            bandIndex = band,
                            count = raw.optInt("count", 0),
                            coverageQuality = raw.optDouble("coverageQuality", 0.0).coerceIn(0.0, 1.0),
                            petrolTimeRaw = raw.optInt("petrolTimeRaw", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                            cngTimeRaw = raw.optInt("cngTimeRaw", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                            mapRaw = raw.optInt("mapRaw", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                            historicalConditionKnown = raw.optBoolean("historicalConditionKnown", false),
                        )
                    }
                }
                root.optJSONArray("nativeLearningAnchors")?.let { array ->
                    val loaded = mutableListOf<NativeLearningAnchor>()
                    repeat(array.length()) { index ->
                        NativeLearningAnchor.fromJson(array.optJSONObject(index) ?: return@repeat)?.let(loaded::add)
                    }
                    nativeAnchors.replaceAll(loaded)
                }
                root.optJSONArray("visitAccumulators")?.let { array ->
                    repeat(array.length()) { index ->
                        val item = VisitComparisonAccumulator.fromJson(array.optJSONObject(index) ?: return@repeat)
                        visitAccumulators[item.key] = item
                    }
                }
                root.optJSONArray("evidenceProvenance")?.let { array ->
                    repeat(array.length()) { index -> provenanceHistory.addLast(EvidenceProvenance.fromJson(array.optJSONObject(index) ?: return@repeat)) }
                    while (provenanceHistory.size > LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES) provenanceHistory.removeFirst()
                }
                root.optJSONObject("evidenceBudget")?.let { budget ->
                    nativeSnapshotsEvicted = budget.optLong("nativeSnapshotsEvicted", 0L)
                    visitAccumulatorsEvicted = budget.optLong("visitAccumulatorsEvicted", 0L)
                }
                performance = LearningPerformanceMetrics.fromJson(root.optJSONObject("performanceMetrics") ?: JSONObject())
                trimNativeEvidenceLocked()
                trimVisitAccumulatorsLocked()
            }
        } catch (_: Exception) {
            evidenceStateFile.renameTo(File(evidenceStateFile.parentFile, "${evidenceStateFile.name}.invalid"))
        }
    }

    private fun persistEvidenceState(forceBoundary: Boolean = false) {
        if (!persistenceGate.shouldRequest(forceBoundary)) return
        try {
            val snapshot = evidenceSnapshot()
            evidenceStateWriter.request { buildEvidencePayload(snapshot) }
        } catch (_: Exception) { }
    }

    private fun adaptiveConfidenceJson(): JSONObject {
        val active = synchronized(evidenceLock) { visitAccumulators.values.filter { it.weight > 0.0 } }
        if (active.isEmpty()) return JSONObject()
            .put("stage", "OBSERVED")
            .put("targetVisits", 10)
            .put("confidence", 0.0)
            .put("effectiveVisits", 0.0)
        val means = active.map { it.meanError() }
        val center = means.average()
        val spread = means.map { kotlin.math.abs(it - center) }.medianSafe()
        val positive = active.count { it.meanError() > 0.0 }
        val consensus = (maxOf(positive, active.size - positive).toDouble() / active.size.toDouble())
        val target = VisitConfidence.adaptiveTarget(
            spread = spread,
            spreadLimit = 0.25,
            consensus = consensus,
        )
        val evaluated = VisitConfidence.evaluate(
            uniqueVisits = active.size,
            effectiveVisits = active.sumOf { it.weight },
            spread = spread,
            spreadLimit = 0.25,
            consensus = consensus,
            provisionalVisits = 1,
            acceptedVisits = target.targetVisits,
            confirmedVisits = target.targetVisits,
        )
        return JSONObject()
            .put("stage", evaluated.stage)
            .put("targetVisits", target.targetVisits)
            .put("confidence", evaluated.confidence)
            .put("effectiveVisits", evaluated.effectiveVisits)
            .put("consensus", evaluated.consensus)
            .put("spread", spread)
            .put("confidenceBandLow", target.confidenceBandLow)
            .put("confidenceBandHigh", target.confidenceBandHigh)
    }

    private fun requestAdvisorRefresh(
        estimate: EquivalenceRuntime.EquivalenceEstimate,
        rpm: Double,
        mapBar: Double,
    ) {
        advisorRevisionGate.revise(advisorScientificToken(estimate, rpm, mapBar))
            ?.let(::scheduleAdvisorRefresh)
    }

    private fun advisorScientificToken(
        estimate: EquivalenceRuntime.EquivalenceEstimate,
        rpm: Double,
        mapBar: Double,
    ): String {
        val supportMilestone = AdvisorRevisionGate.observationMilestone(
            kotlin.math.min(estimate.petrolEffectiveSupport, estimate.cngEffectiveSupport)
                .toInt()
                .coerceAtLeast(1),
        )
        val rpmCell = kotlin.math.floor(rpm / 80.0).toInt()
        val mapCell = kotlin.math.floor(mapBar / 0.02).toInt()
        return listOf(
            "EQ",
            rpmCell,
            mapCell,
            AdvisorRevisionGate.quantize(estimate.referenceMs, 0.02),
            AdvisorRevisionGate.quantize(estimate.cngMs, 0.02),
            AdvisorRevisionGate.quantize(estimate.errorFraction, 0.0025),
            AdvisorRevisionGate.quantize(estimate.uncertaintyFraction, 0.0025),
            AdvisorRevisionGate.quantize(estimate.usefulMarginFraction, 0.0025),
            estimate.actionable,
            supportMilestone,
        ).joinToString(":")
    }

    private fun refreshAdvisor(revision: Long) {
        val refreshed = analyzeCurrentMemory()
        advisor = refreshed
        advisorPublishedRevision.set(revision)
    }

    private fun scheduleAdvisorRefresh(revision: Long) {
        advisorRequestedRevision.accumulateAndGet(revision) { current, incoming -> maxOf(current, incoming) }
        advisorRefreshDirty.set(true)
        if (!advisorRefreshPending.compareAndSet(false, true)) return
        advisorExecutor.execute {
            try {
                while (advisorRefreshDirty.getAndSet(false)) {
                    refreshAdvisor(advisorRequestedRevision.get())
                }
            } finally {
                advisorRefreshPending.set(false)
                if (advisorRefreshDirty.get()) scheduleAdvisorRefresh(advisorRequestedRevision.get())
            }
        }
    }

    private fun primaryEquivalenceJson(camelCase: Boolean): JSONObject = synchronized(equivalenceLock) {
        val estimate = lastEquivalenceEstimate.takeIf {
            CurrentEquivalenceStatusPolicy.allowsCachedEstimate(visibleDecision)
        }
        val root = JSONObject()
            .put("schema", "omegas-primary-equivalence-v1")
            .put(if (camelCase) "petrolWeight" else "petrol_weight", equivalenceRuntime.totalWeight(FuelLane.PETROL_REFERENCE))
            .put(if (camelCase) "cngWeight" else "cng_weight", equivalenceRuntime.totalWeight(FuelLane.CNG_PETROL_OBSERVED))
            .put(if (camelCase) "legacySeededRegions" else "legacy_seeded_regions", equivalenceRuntime.legacySeededRegions())
            .put("comparable", estimate != null)
            .put("runtimeAuthority", BoundedEquivalenceAdvisorSnapshot.AUTHORITY)
            .put("environmentGates", false)
            .put("persistenceRepresentation", EquivalenceSurfaceCodec.REPRESENTATION)
            .put("persistence", equivalenceRuntime.metricsJson())
        if (estimate != null) {
            root
                .put(if (camelCase) "referenceMs" else "reference_ms", estimate.referenceMs)
                .put(if (camelCase) "cngMs" else "cng_ms", estimate.cngMs)
                .put(if (camelCase) "deltaMs" else "delta_ms", estimate.deltaMs)
                .put(if (camelCase) "errorFraction" else "error_fraction", estimate.errorFraction)
                .put(if (camelCase) "uncertaintyFraction" else "uncertainty_fraction", estimate.uncertaintyFraction)
                .put(if (camelCase) "usefulMarginFraction" else "useful_margin_fraction", estimate.usefulMarginFraction)
                .put("actionable", estimate.actionable)
                .put(if (camelCase) "petrolEffectiveSupport" else "petrol_effective_support", estimate.petrolEffectiveSupport)
                .put(if (camelCase) "cngEffectiveSupport" else "cng_effective_support", estimate.cngEffectiveSupport)
                .put(if (camelCase) "materialRevision" else "material_revision", estimate.materialRevision)
        }
        root
    }

    fun close() {
        persistEvidenceState(forceBoundary = true)
        evidenceStateWriter.flush(5_000L)
        advisorExecutor.shutdownNow()
        equivalenceRuntime.close()
        delegate.close()
        evidenceStateWriter.close()
    }

    private fun analyzeCurrentMemory(): JSONObject = try {
        val epoch = delegate.statusJson().optInt("epoch", 1).coerceAtLeast(1)
        val boundedInput = BoundedEquivalenceAdvisorSnapshot.build(
            equivalenceRuntime.snapshotForAdvisor(),
            epoch = epoch,
        )
        AssistedCalibrationAdvisor.analyze(boundedInput)
            .put("primaryAuthority", BoundedEquivalenceAdvisorSnapshot.AUTHORITY)
            .put("inputSource", "BOUNDED_EQUIVALENCE_SURFACE")
            .put("environmentGates", false)
            .put("inputComparisonCount", boundedInput.optInt("comparisonCount", 0))
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("automatic", false)
            .put("primaryAuthority", BoundedEquivalenceAdvisorSnapshot.AUTHORITY)
            .put("inputSource", "BOUNDED_EQUIVALENCE_SURFACE")
            .put("environmentGates", false)
            .put("error", error.message ?: "Análise assistida indisponível")
    }

    private fun initialAdvisor(): JSONObject = JSONObject()
        .put("ok", true)
        .put("automatic", false)
        .put("mode", "CONTINUOUS_ADAPTIVE_MANUAL")
        .put("comparisonCount", 0)
        .put("primaryAuthority", BoundedEquivalenceAdvisorSnapshot.AUTHORITY)
        .put("inputSource", "BOUNDED_EQUIVALENCE_SURFACE")
        .put("environmentGates", false)

    private fun migrateLegacyPetrolReferences(): Int {
        if (equivalenceRuntime.totalWeight(FuelLane.PETROL_REFERENCE) > 0.0 ||
            equivalenceRuntime.legacySeededRegions() > 0
        ) return 0

        val regions = delegate.advisorSnapshot().optJSONArray("regions") ?: return 0
        val seeds = mutableListOf<PersistentEquivalenceRuntime.LegacyPetrolSeed>()
        repeat(regions.length()) { index ->
            val raw = regions.optJSONObject(index) ?: return@repeat
            val fuel = raw.optString("fuel").uppercase()
            if (fuel !in setOf("PETROL", "GASOLINA")) return@repeat
            val rpm = raw.optDouble("rpm", Double.NaN)
            val mapBar = raw.optDouble("map_bar", Double.NaN)
            val mean = raw.optDouble("petrol_ms", Double.NaN)
            if (!rpm.isFinite() || !mapBar.isFinite() || !mean.isFinite() || mean <= 0.05) return@repeat

            val persistedSquaredMean = raw.optDouble("petrol_squared_mean", Double.NaN)
            val persistedSpread = raw.optDouble("petrol_spread_ms", Double.NaN)
            val variance = when {
                persistedSpread.isFinite() && persistedSpread >= 0.0 -> persistedSpread * persistedSpread
                persistedSquaredMean.isFinite() -> kotlin.math.max(0.0, persistedSquaredMean - mean * mean)
                else -> 0.0
            }
            val quality = raw.optDouble("quality", raw.optDouble("confidence", 0.25)).coerceIn(0.0, 1.0)
            val storedWeight = raw.optDouble("weight", Double.NaN)
            val samples = raw.optInt("samples", 1).coerceAtLeast(1).toDouble()
            val support = storedWeight.takeIf { it.isFinite() && it > 0.0 } ?: samples
            seeds += PersistentEquivalenceRuntime.LegacyPetrolSeed(
                rpm = rpm,
                mapBar = mapBar,
                meanTinjMs = mean,
                varianceMs2 = variance,
                quality = quality,
                persistedSupport = support,
            )
        }
        return equivalenceRuntime.seedLegacyPetrolIfEmpty(seeds)
    }

    private fun decorate(source: JSONObject, includeAdvisor: Boolean = true): JSONObject {
        val performanceSnapshot = synchronized(evidenceLock) { performance }
        val root = JSONObject(source.toString())
            .put("format", FORMAT)
            .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("learning_data_revision", DATA_REVISION)
            .put("signal_driven_v5", true)
            .put("visit_policy", "physical-region-exit")
            .put("sample_weighting_policy", "continuous-frame-novelty-per-fuel")
            .put("independent_samples", independentSamples)
            .put("correlated_samples_weighted", correlatedSamplesWeighted)
            .put("duplicate_samples_ignored", duplicateSamplesIgnored)
            .put("new_frames_absorbed", newFramesAbsorbed)
            .put("eligible_frames_evaluated", eligibleFramesEvaluated)
            .put("lifetime_independent_samples", lifetimeIndependentSamples)
            .put("lifetime_correlated_samples_weighted", lifetimeCorrelatedSamplesWeighted)
            .put("lifetime_duplicate_samples_ignored", lifetimeDuplicateSamplesIgnored)
            .put("lifetime_new_frames_absorbed", lifetimeNewFramesAbsorbed)
            .put("lifetime_eligible_frames_evaluated", lifetimeEligibleFramesEvaluated)
            .put("last_novel_frames", lastNovelty.newFrames)
            .put("last_window_frames", lastNovelty.totalFrames)
            .put("last_novelty_fraction", lastNovelty.fraction)
            .put("cumulative_evidence_preserved", true)
            .put("session_metadata_policy", "timestamp-organization-only-cumulative-memory")
            .put("equivalence_policy", "rpm-map-petrol-tinj-continuous-v1")
            .put("primary_equivalence", primaryEquivalenceJson(camelCase = false))
            .put("automatic_calibration", false)
            .put("real_sample_time_preserved", true)
            .put("gas_condition_preserved", true)
            .put("cross_fuel_gas_temperature", false)
            .put("performance_metrics", performanceSnapshot.toJson())
            .put("evidence_persistence", evidenceStateWriter.metricsJson().put("materialGate", persistenceGate.metricsJson()))

        if (includeAdvisor) {
            val evidence = evidenceSnapshot()
            root
                .put("assisted_calibration", advisor)
                .put("advisor_revision", advisorRequestedRevision.get())
                .put("advisor_published_revision", advisorPublishedRevision.get())
                .put("advisor_fresh", advisorPublishedRevision.get() >= advisorRequestedRevision.get())
                .put("native_ecu_evidence", JSONArray(evidence.nativeEvidence.map { it.toJson() }))
                .put("native_learning_anchors", JSONArray(evidence.nativeAnchors.map { it.toJson() }))
                .put("evidence_budget", evidenceBudgetJson(evidence))
        }

        memoryDecision?.let { stored ->
            root.put("memory_sample_accepted", stored.learningEligible && stored.sample != null)
            root.put("memory_reason_code", stored.reasonCode)
        }

        visibleDecision?.let { decision ->
            root.put("signal_decision", if (includeAdvisor) decision.toJson() else decision.toTelemetryJson())
            if (includeAdvisor) {
                decision.sample?.toJson()?.let { realSample ->
                    root.put("sample", realSample)
                    root.optJSONObject("live")?.put("sample", realSample)
                }
            }
        }
        return root
    }
}
