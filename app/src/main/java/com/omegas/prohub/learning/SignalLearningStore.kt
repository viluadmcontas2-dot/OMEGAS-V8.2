package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun List<Double>.medianSafe(): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else sorted[middle]
}

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
    }

    private val delegate = MotorLearningMemory(stateFile, log)
    private val evidenceStateFile = File(stateFile.parentFile ?: stateFile.absoluteFile.parentFile, "learning_v6_evidence.json")
    private val evidenceStateWriter = CoalescedSnapshotWriter(
        target = evidenceStateFile,
        threadName = "omegas-learning-evidence-persist",
    )
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
    private val nativeEvidence = linkedMapOf<String, NativeEcuEvidence>()
    private val visitAccumulators = linkedMapOf<String, VisitComparisonAccumulator>()
    private val provenanceHistory = ArrayDeque<EvidenceProvenance>()
    private var frameSequence = 0L
    private var performance = LearningPerformanceMetrics()
    private val advisorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-learning-advisor").apply { isDaemon = true }
    }
    private val advisorRefreshPending = AtomicBoolean(false)
    private val advisorRefreshDirty = AtomicBoolean(false)
    @Volatile private var advisor = analyzeCurrentMemory()

    init { loadEvidenceState() }

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
        persistEvidenceState()
        evidenceStateWriter.flush(2_000L)
        return result
    }

    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject {
        performance = performance.copy(framesReceived = performance.framesReceived + 1L)
        visibleDecision = decision
        val source = decision.sample
        val sequenceBefore = frameSequence
        if (source != null) frameSequence += source.frameCount.toLong().coerceAtLeast(0L)
        val prepared = if (decision.learningEligible && source != null) {
            val fuelKey = source.fuel.wireName
            val novelty = ContinuousWindowNovelty.calculate(
                startedAtElapsedMs = source.startedAtElapsedMs,
                endedAtElapsedMs = source.endedAtElapsedMs,
                frameCount = source.frameCount,
                medianIntervalMs = source.diagnostics.medianIntervalMs,
                previouslyRepresentedThroughElapsedMs = lastRepresentedWindowEndByFuel[fuelKey],
            )
            performance = performance.copy(
                validFrames = performance.validFrames + novelty.totalFrames,
                windowsEvaluated = performance.windowsEvaluated + 1L,
                earlySamplesAccepted = performance.earlySamplesAccepted + if (source.frameCount < 10) 1L else 0L,
                newFrames = performance.newFrames + novelty.newFrames,
                reusedFrames = performance.reusedFrames + (novelty.totalFrames - novelty.newFrames),
                usefulWeight = performance.usefulWeight + novelty.fraction * source.quality,
                firstEstimateAtMs = performance.firstEstimateAtMs ?: source.endedAtElapsedMs,
            )
            lastRepresentedWindowEndByFuel[fuelKey] = novelty.representedThroughElapsedMs
            lastNovelty = novelty
            provenanceHistory.addLast(
                EvidenceProvenance(
                    firstFrameSequence = sequenceBefore + 1L,
                    lastFrameSequence = frameSequence,
                    newFrameCount = novelty.newFrames,
                    reusedFrameCount = (novelty.totalFrames - novelty.newFrames).coerceAtLeast(0),
                    noveltyRatio = novelty.fraction,
                ),
            )
            while (provenanceHistory.size > 64) provenanceHistory.removeFirst()
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
                        // Mantém o código de compatibilidade; a novidade detalhada
                        // fica disponível nas métricas e na proveniência exportada.
                        // Compatibilidade documental: OVERLAPPING_WINDOW_NOVELTY_WEIGHTED
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
        val result = delegate.ingest(telemetry, prepared)
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
                val updated = (visitAccumulators[key] ?: VisitComparisonAccumulator(key = key)).add(
                    error = comparison.optDouble("error_pct", 0.0),
                    sampleWeight = weight,
                    independent = independent,
                    nowMs = comparison.optLong("captured_at", System.currentTimeMillis()),
                )
                visitAccumulators[key] = updated
            }
        }
        if (prepared.learningEligible && prepared.sample != null) scheduleAdvisorRefresh()
        // O sidecar é uma fotografia substituível, não um log. Só solicita nova
        // fotografia quando o analisador realmente produziu uma amostra; decisões
        // intermediárias continuam ao vivo, mas não geram I/O de arquivo por quadro.
        if (source != null) persistEvidenceState()
        return decorate(result, includeAdvisor = false)
    }

    fun statusJson(): JSONObject = decorate(delegate.statusJson())

    fun export(deviceId: String): JSONObject {
        val exported = delegate.export(deviceId)
        advisor = AssistedCalibrationAdvisor.analyze(exported)
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
            .put("equivalencePolicy", "continuous-petrol-reference-surface")
            .put("assistedCalibration", advisor)
            .put("automaticCalibration", false)
            .put("realSampleTimePreserved", true)
            .put("gasConditionPreserved", true)
            .put("crossFuelGasTemperature", false)
            .put("nativeEcuEvidence", JSONArray(nativeEvidence.values.map { it.toJson() }))
            .put("visitAccumulators", JSONArray(visitAccumulators.values.map { it.toJson() }))
            .put("evidenceProvenance", JSONArray(provenanceHistory.map { it.toJson() }))
            .put("evidenceStateSchema", "omegas-learning-evidence-v6-v1")
            .put("adaptiveConfidence", adaptiveConfidenceJson())
            .put("performanceMetrics", performance.toJson())
            .put("evidencePersistence", evidenceStateWriter.metricsJson())
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
        scheduleAdvisorRefresh()
        return decorate(result)
    }

    /** Importa somente contexto nativo; não cria comparação nem dispara writer. */
    fun importNativeSnapshot(snapshot: JSONObject): JSONObject {
        val snapshotId = snapshot.optString("snapshotId", snapshot.optString("sessionId"))
        if (snapshotId.isBlank()) return JSONObject().put("ok", false).put("error", "Snapshot nativo sem identificador")
        val fields = snapshot.optJSONArray("fields") ?: JSONArray()
        var imported = 0
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("status") != "VALID") return@repeat
            val raw = field.optJSONArray("rawValues") ?: return@repeat
            val bandCount = raw.length().coerceAtMost(18)
            repeat(bandCount) { band ->
                val key = "$snapshotId:$band"
                nativeEvidence[key] = NativeEcuEvidence(
                    snapshotId = snapshotId,
                    bandIndex = band,
                    count = field.optJSONArray("counts")?.optInt(band, 0) ?: 0,
                    coverageQuality = if (bandCount == 0) 0.0 else 1.0,
                    mapRaw = raw.optInt(band),
                )
                imported++
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "ECU_NATIVE")
            .put("importedBands", imported)
            .put("automaticCalibration", false)
            .put("manualOnly", true)
            .also { persistEvidenceState() }
    }

    fun onCalibrationAdjustment(payload: JSONObject): JSONObject {
        resetConnectionCounters()
        val result = delegate.onCalibrationAdjustment(payload)
        scheduleAdvisorRefresh()
        persistEvidenceState()
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

    private fun loadEvidenceState() {
        if (!evidenceStateFile.isFile) return
        try {
            val root = JSONObject(evidenceStateFile.readText(Charsets.UTF_8))
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
            root.optJSONArray("visitAccumulators")?.let { array ->
                repeat(array.length()) { index ->
                    val item = VisitComparisonAccumulator.fromJson(array.optJSONObject(index) ?: return@repeat)
                    visitAccumulators[item.key] = item
                }
            }
            root.optJSONArray("evidenceProvenance")?.let { array ->
                repeat(array.length()) { index -> provenanceHistory.addLast(EvidenceProvenance.fromJson(array.optJSONObject(index) ?: return@repeat)) }
                while (provenanceHistory.size > 64) provenanceHistory.removeFirst()
            }
            performance = LearningPerformanceMetrics.fromJson(root.optJSONObject("performanceMetrics") ?: JSONObject())
        } catch (_: Exception) {
            evidenceStateFile.renameTo(File(evidenceStateFile.parentFile, "${evidenceStateFile.name}.invalid"))
        }
    }

    private fun persistEvidenceState() {
        try {
            val payload = JSONObject()
                .put("schema", "omegas-learning-evidence-v6-v1")
                .put("frameSequence", frameSequence)
                .put("nativeEcuEvidence", JSONArray(nativeEvidence.values.map { it.toJson() }))
                .put("visitAccumulators", JSONArray(visitAccumulators.values.map { it.toJson() }))
                .put("evidenceProvenance", JSONArray(provenanceHistory.map { it.toJson() }))
                .put("performanceMetrics", performance.toJson())
            evidenceStateWriter.submit(payload.toString())
        } catch (_: Exception) { /* aprendizado principal continua funcionando sem o sidecar */ }
    }

    private fun adaptiveConfidenceJson(): JSONObject {
        val active = visitAccumulators.values.filter { it.weight > 0.0 }
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

    private fun refreshAdvisor() {
        advisor = analyzeCurrentMemory()
    }

    private fun scheduleAdvisorRefresh() {
        advisorRefreshDirty.set(true)
        if (!advisorRefreshPending.compareAndSet(false, true)) return
        advisorExecutor.execute {
            try {
                while (advisorRefreshDirty.getAndSet(false)) refreshAdvisor()
            } finally {
                advisorRefreshPending.set(false)
                if (advisorRefreshDirty.get()) scheduleAdvisorRefresh()
            }
        }
    }

    fun close() {
        persistEvidenceState()
        evidenceStateWriter.flush(5_000L)
        delegate.close()
        advisorExecutor.shutdownNow()
        evidenceStateWriter.close()
    }

    private fun analyzeCurrentMemory(): JSONObject = try {
        AssistedCalibrationAdvisor.analyze(delegate.export("local-runtime"))
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("automatic", false)
            .put("error", error.message ?: "Análise assistida indisponível")
    }

    private fun decorate(source: JSONObject, includeAdvisor: Boolean = true): JSONObject {
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
            .put("equivalence_policy", "continuous-petrol-reference-surface")
            .put("automatic_calibration", false)
            .put("real_sample_time_preserved", true)
            .put("gas_condition_preserved", true)
            .put("cross_fuel_gas_temperature", false)
            .put("native_ecu_evidence", JSONArray(nativeEvidence.values.map { it.toJson() }))
            .put("performance_metrics", performance.toJson())
            .put("evidence_persistence", evidenceStateWriter.metricsJson())

        if (includeAdvisor) root.put("assisted_calibration", advisor)

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
