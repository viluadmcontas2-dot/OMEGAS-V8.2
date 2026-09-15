package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Memória única do aprendizado baseado no motor.
 *
 * O estado publicado separa três verdades:
 * - live: decisão do instante atual;
 * - session_summary: evidências criadas desde a conexão física atual;
 * - memory: referências e comparações persistidas, inclusive offline.
 *
 * Gasolina é independente da época K. Evidências GNV pertencem à época do mapa
 * que as produziu e nunca são misturadas depois de uma escrita confirmada.
 */
class MotorLearningMemory(
    private val stateFile: File,
    private val log: RingLog,
) {
    companion object {
        const val FORMAT = "omegas-learning-v5"
        private const val MAX_COMPARISONS = 600
        private const val MAX_SESSIONS = 100
        private const val MAX_REGIONS = 2000
    }

    private val lock = Any()
    private val regions = mutableListOf<LearningRegion>()
    private val comparisons = ArrayDeque<FuelComparison>()
    private val sessions = ArrayDeque<PhysicalLearningSession>()

    private var sessionId = UUID.randomUUID().toString()
    private var epoch = 1
    private var mapHash = ""
    private var adaptiveScale = AdaptivePetrolScaleState()
    private var activeVisit: ActiveVisit? = null
    private var observedOutsideFrames = 0
    private var currentSession: PhysicalLearningSession? = null
    private var lastStatus = JSONObject()
    private var lastCalibrationRevalidation = JSONObject()
    private var lastReferenceDiagnostic = JSONObject()
    private var referenceAttempts = 0L
    private var referenceAccepted = 0L
    private val referenceRejectCounts = linkedMapOf<String, Long>()

    private val sessionPetrolRegions = linkedSetOf<String>()
    private val sessionPetrolScaleByVisit = linkedMapOf<String, PetrolReferenceSelector.Region>()
    private val sessionCngRegions = linkedSetOf<String>()
    private val sessionComparisons = linkedSetOf<String>()

    private val persistExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-learning-persist").apply { isDaemon = true }
    }
    private val persistDirty = AtomicBoolean(false)
    private val persistDrainScheduled = AtomicBoolean(false)
    @Volatile private var lastPersistFuture: Future<*>? = null

    init {
        load()
        rebuildVisualStatusFromMemory()
    }

    fun startSession(): JSONObject = synchronized(lock) {
        promoteSessionScaleIfEligible()
        closeCurrentSession("NEW_PHYSICAL_USB_CONNECTION")
        sessionId = UUID.randomUUID().toString()
        activeVisit = null
        observedOutsideFrames = 0
        currentSession = PhysicalLearningSession(
            id = sessionId,
            startedAt = System.currentTimeMillis(),
        ).also { sessions.addLast(it) }
        while (sessions.size > MAX_SESSIONS) sessions.removeFirst()
        sessionPetrolRegions.clear()
        sessionPetrolScaleByVisit.clear()
        sessionCngRegions.clear()
        sessionComparisons.clear()
        lastStatus = JSONObject()
            .put("state", "OBSERVING_ENGINE")
            .put("reason", "Nova conexão física MP48")
            .put("learning", false)
            .put("session_id", sessionId)
            .put("epoch", epoch)
        persist()
        statusLocked()
    }

    fun endSession(reason: String): JSONObject = synchronized(lock) {
        promoteSessionScaleIfEligible()
        closeCurrentSession(reason)
        activeVisit = null
        observedOutsideFrames = 0
        persist()
        statusLocked()
    }

    fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision): JSONObject = synchronized(lock) {
        observePhysicalExit(telemetry)
        val sample = decision.sample
        if (sample == null || !decision.learningEligible) {
            lastStatus = JSONObject()
                .put("state", decision.state)
                .put("reason", decision.reason)
                .put("learning", false)
                .put("sample", decision.toJson())
                .put("session_id", sessionId)
                .put("epoch", epoch)
            return@synchronized compactStatusLocked()
        }

        val visit = resolveVisit(sample)
        val physicalSession = ensureCurrentSession()
        physicalSession.sampleCount += 1
        physicalSession.fuels += sample.fuel.wireName
        physicalSession.updatedAt = System.currentTimeMillis()
        val region = updateRegion(sample, visit)
        when (sample.fuel) {
            Mp48Fuel.PETROL -> {
                sessionPetrolRegions += region.id
                sessionPetrolScaleByVisit.putIfAbsent(
                    visit.id,
                    PetrolReferenceSelector.Region(
                        id = "session:${sessionId}:visit:${visit.id}",
                        rpm = sample.rpm,
                        mapBar = sample.mapBar,
                        waterC = sample.waterC,
                        petrolMs = sample.petrolMs,
                        confidence = sample.quality.coerceIn(0.05, 1.0),
                        sampleCount = 1,
                    ),
                )
                lastStatus = petrolStatus(region, sample, visit)
            }
            Mp48Fuel.CNG -> {
                sessionCngRegions += region.id
                lastStatus = cngStatus(region, sample, visit)
            }
            else -> {
                lastStatus = JSONObject()
                    .put("state", "IGNORED")
                    .put("reason", "Estado físico não participa do aprendizado")
                    .put("learning", false)
            }
        }
        lastStatus.put("sample", sample.toJson())
            .put("cell", LearningGridProjection.cellFor(sample.rpm, sample.petrolMs))
            .put("visit_rule", "Nova visita somente após sair e retornar à célula física")
            .put("visit_id", visit.id)
            .put("session_id", sessionId)
            .put("epoch", epoch)
        persist()
        compactStatusLocked()
    }

    fun statusJson(): JSONObject = synchronized(lock) { statusLocked() }

    fun awaitPersistence(timeoutSeconds: Long = 10L) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.coerceAtLeast(1L))
        while (persistDirty.get() || persistDrainScheduled.get()) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) return
            try {
                lastPersistFuture?.get(remaining, TimeUnit.NANOSECONDS)
            } catch (_: Exception) {
                return
            }
            Thread.yield()
        }
    }

    fun close() {
        awaitPersistence()
        persistExecutor.shutdown()
        try { persistExecutor.awaitTermination(2, TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    fun export(deviceId: String): JSONObject = synchronized(lock) {
        JSONObject()
            .put("ok", true)
            .put("format", FORMAT)
            .put("deviceId", deviceId)
            .put("exportedAt", System.currentTimeMillis())
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("adaptiveScale", adaptiveScale.toJson())
            .put("regions", regionsJsonLocked())
            .put("cells", cellsJsonLocked())
            .put("grid", LearningGridProjection.gridJson())
            .put("integrity", integrityJsonLocked())
            .put("comparisons", JSONArray(comparisons.map { it.toJson() }))
            .put("summary", summaryLocked())
            .put("session_summary", sessionSummaryLocked())
            .put("memory", memoryLocked())
            .put("revalidation", JSONObject(lastCalibrationRevalidation.toString()))
            .put("sessions", JSONArray(sessions.map { it.toJson() }))
            .put("tolerancePolicy", LearningToleranceSettings.current.toJson())
    }

    /**
     * Fotografia mínima para o Advisor. Não inclui grid derivada, sessões nem
     * resumos de UI, evitando reconstruir payloads que a análise não consome.
     */
    fun advisorSnapshot(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("format", FORMAT)
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("adaptiveScale", adaptiveScale.toJson())
            .put("regions", JSONArray(regions.map { it.toAdvisorJson() }))
            .put("comparisons", JSONArray(comparisons.map { it.toJson() }))
    }

    fun merge(payload: JSONObject, localDeviceId: String = ""): JSONObject = synchronized(lock) {
        val format = payload.optString("format")
        if (format != FORMAT) {
            return@synchronized JSONObject()
                .put("ok", false)
                .put("error", "Formato de aprendizado Android incompatível")
        }
        if (format == FORMAT) {
            val incomingRegions = payload.optJSONArray("regions") ?: JSONArray()
            val incomingCells = payload.optJSONArray("cells") ?: JSONArray()
            val incomingIntegrity = LearningGridProjection.integrity(
                regions = incomingRegions,
                cells = incomingCells,
                comparisons = payload.optJSONArray("comparisons") ?: JSONArray(),
                epoch = payload.optInt("epoch", 1),
                mapHash = payload.optString("mapHash"),
            )
            if (!incomingIntegrity.optBoolean("ok")) {
                return@synchronized JSONObject()
                    .put("ok", false)
                    .put("error", "Divergência entre memória e projeção do arquivo .omegas")
                    .put("integrity", incomingIntegrity)
            }
        }
        val sourceDevice = payload.optString("deviceId", "remote").ifBlank { "remote" }
        var mergedRegions = 0
        val incoming = payload.optJSONArray("regions") ?: JSONArray()
        repeat(incoming.length()) { index ->
            val raw = incoming.optJSONObject(index) ?: return@repeat
            val candidate = LearningRegion.fromJson(raw).namespace(sourceDevice)
            if (candidate.fuel != Mp48Fuel.PETROL) return@repeat
            val target = nearestRegion(
                fuel = Mp48Fuel.PETROL,
                rpm = candidate.rpmMean,
                mapBar = candidate.mapMean,
                regionEpoch = 0,
            )?.takeIf { regionEquivalent(it, candidate.rpmMean, candidate.mapMean) }
            if (target == null) regions += candidate.copy(epoch = 0) else target.merge(candidate)
            mergedRegions += 1
        }
        persist()
        JSONObject()
            .put("ok", true)
            .put("localDeviceId", localDeviceId)
            .put("mergedRegions", mergedRegions)
            .put("totalRegions", regions.size)
            .put("memory", memoryLocked())
    }

    fun onCalibrationAdjustment(payload: JSONObject): JSONObject = synchronized(lock) {
        val previousEpoch = epoch
        val nextEpoch = previousEpoch + 1
        val nextMapHash = payload.optString("newHash", payload.optString("hash", mapHash))
        val adjustedCells = adjustmentCells(payload)
        val localizedMapAdjustment = adjustedCells.isNotEmpty()
        var preservedRegions = 0
        var revalidationRegions = 0
        var preservedComparisons = 0
        var revalidationComparisons = 0

        if (localizedMapAdjustment) {
            regions.indices.forEach { index ->
                val region = regions[index]
                if (region.fuel != Mp48Fuel.CNG || region.epoch != previousEpoch) return@forEach
                if (regionTouchesAny(region, adjustedCells)) {
                    revalidationRegions += 1
                } else {
                    regions[index] = region.copy(epoch = nextEpoch)
                    preservedRegions += 1
                }
            }
            val carriedComparisons = comparisons.map { comparison ->
                if (comparison.epoch != previousEpoch) comparison
                else if (comparisonTouchesAny(comparison, adjustedCells)) {
                    revalidationComparisons += 1
                    comparison
                } else {
                    preservedComparisons += 1
                    comparison.copy(
                        epoch = nextEpoch,
                        mapHash = nextMapHash,
                        dedupeKey = "$nextEpoch:${comparison.origin}:${comparison.visitId}:${comparison.referenceRegionId}",
                    )
                }
            }
            comparisons.clear()
            comparisons.addAll(carriedComparisons)
        } else {
            revalidationRegions = regions.count { it.fuel == Mp48Fuel.CNG && it.epoch == previousEpoch }
            revalidationComparisons = comparisons.count { it.epoch == previousEpoch }
        }

        epoch = nextEpoch
        mapHash = nextMapHash
        activeVisit = null
        sessionCngRegions.clear()
        sessionComparisons.clear()
        lastCalibrationRevalidation = JSONObject()
            .put("adjustmentId", payload.optString("adjustmentId"))
            .put("scope", if (localizedMapAdjustment) "LOCALIZED_MAP_CELLS" else "GLOBAL_CALIBRATION")
            .put("previousEpoch", previousEpoch)
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("affectedCells", JSONArray(adjustedCells.toList()))
            .put("preservedRegions", preservedRegions)
            .put("revalidationRegions", revalidationRegions)
            .put("preservedComparisons", preservedComparisons)
            .put("revalidationComparisons", revalidationComparisons)
            .put("updatedAt", System.currentTimeMillis())
        lastStatus = JSONObject()
            .put("state", "NEW_CALIBRATION_EPOCH")
            .put("reason", if (localizedMapAdjustment) "Mapa K confirmado; apenas as células alteradas precisam de nova validação" else "Calibração global confirmada; evidências GNV começam em uma época nova")
            .put("learning", false)
            .put("epoch", epoch)
            .put("map_hash", mapHash)
            .put("revalidation", JSONObject(lastCalibrationRevalidation.toString()))
        persist()
        JSONObject()
            .put("ok", true)
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("revalidation", JSONObject(lastCalibrationRevalidation.toString()))
            .put("status", statusLocked())
    }

    private fun adjustmentCells(payload: JSONObject): Set<String> {
        val cells = payload.optJSONArray("cells") ?: return emptySet()
        return buildSet {
            repeat(cells.length()) { index ->
                val cell = cells.optJSONObject(index) ?: return@repeat
                val row = cell.optInt("row", -1)
                val column = cell.optInt("column", -1)
                if (row >= 0 && column >= 0) add(cellKey(row, column))
            }
        }
    }

    private fun regionTouchesAny(region: LearningRegion, cells: Set<String>): Boolean {
        val cell = LearningGridProjection.cellFor(region.rpmMean, region.petrolMean)
        return cellKey(cell.getInt("row"), cell.getInt("column")) in cells
    }

    private fun comparisonTouchesAny(comparison: FuelComparison, cells: Set<String>): Boolean =
        cells.any { key ->
            val parts = key.split(':')
            comparison.affects(parts[0].toInt(), parts[1].toInt())
        }

    private fun cellKey(row: Int, column: Int): String = "$row:$column"

    fun previewKWrite(row: Int, column: Int, value: Int): JSONObject = synchronized(lock) {
        val latest = comparisons.lastOrNull { it.epoch == epoch && it.affects(row, column) }
            ?: return@synchronized JSONObject().put("ok", false).put("error", "Ainda não existe evidência ligada a esta região contínua do mapa K")
        val evidence = comparisonEvidenceForCell(row, column)
        val confidence = evidence.confidence()
        val proposed = (value * (1.0 + 0.35 * confidence * evidence.medianErrorRatio)).coerceIn(50.0, 255.0).toInt()
        JSONObject()
            .put("ok", true)
            .put("row", row)
            .put("column", column)
            .put("requested", value)
            .put("suggested_value", proposed)
            .put("suggested_delta", proposed - value)
            .put("comparison", latest.toJson())
            .put("evidence", evidence.toJson())
            .put("automatic_write", false)
            .put("human_confirmation_required", true)
            .put("warning", "Somente sugestão: confira o valor e confirme manualmente; ACK e readback continuam obrigatórios")
    }

    private fun regionsJsonLocked(): JSONArray = JSONArray(regions.map { LearningGridProjection.enrichRegion(it.toJson()) })
    private fun cellsJsonLocked(): JSONArray = LearningGridProjection.project(regionsJsonLocked(), epoch)

    private fun integrityJsonLocked(): JSONObject {
        val regionJson = regionsJsonLocked()
        val cellJson = LearningGridProjection.project(regionJson, epoch)
        return LearningGridProjection.integrity(regions = regionJson, cells = cellJson, comparisons = JSONArray(comparisons.map { it.toJson() }), epoch = epoch, mapHash = mapHash)
    }

    private fun statusLocked(): JSONObject {
        val live = JSONObject(lastStatus.toString())
        return JSONObject(live.toString())
            .put("live", live)
            .put("summary", summaryLocked())
            .put("session_summary", sessionSummaryLocked())
            .put("memory", memoryLocked())
            .put("cells", cellsJsonLocked())
            .put("grid", LearningGridProjection.gridJson())
            .put("integrity", integrityJsonLocked())
            .put("tolerance_policy", LearningToleranceSettings.current.toJson())
            .put("revalidation", JSONObject(lastCalibrationRevalidation.toString()))
            .put("has_persisted_learning", regions.isNotEmpty() || comparisons.isNotEmpty())
    }

    private fun compactStatusLocked(): JSONObject {
        val compact = JSONObject()
            .put("state", lastStatus.optString("state", "OBSERVING_ENGINE"))
            .put("reason", lastStatus.optString("reason", "Observando o motor"))
            .put("learning", lastStatus.optBoolean("learning", false))
            .put("quality", lastStatus.optDouble("quality", 0.0))
            .put("reference_confidence", lastStatus.optDouble("reference_confidence", 0.0))
            .put("registered_now", lastStatus.optBoolean("registered_now", false))
            .put("session_id", sessionId)
            .put("epoch", epoch)
        listOf("comparison", "comparison_evidence", "direction", "error_pct", "comparison_stage", "reference_surface", "reference_diagnostic", "actionable", "suggested_delta_k_percent", "suggested_delta_k").forEach { key ->
            if (lastStatus.has(key)) compact.put(key, lastStatus.get(key))
        }
        return compact
    }

    private fun memoryLocked(): JSONObject {
        val latestPetrol = regions.filter { it.fuel == Mp48Fuel.PETROL }.maxByOrNull { it.updatedAt }
        val latestCng = regions.filter { it.fuel == Mp48Fuel.CNG && it.epoch == epoch }.maxByOrNull { it.updatedAt }
        val latestComparison = comparisons.lastOrNull { it.epoch == epoch } ?: comparisons.lastOrNull()
        val progress = when {
            latestComparison != null -> comparisonEvidence(latestComparison.rpm, latestComparison.mapBar).toJson().put("kind", "COMPARISON").put("rpm", latestComparison.rpm).put("map_bar", latestComparison.mapBar)
            latestPetrol != null -> latestPetrol.progressJson().put("kind", "PETROL_REFERENCE")
            latestCng != null -> latestCng.progressJson().put("kind", "CNG_REGION")
            else -> JSONObject().put("kind", "EMPTY").put("stage", "EMPTY")
        }
        return JSONObject()
            .put("available", regions.isNotEmpty() || comparisons.isNotEmpty())
            .put("last_reference", latestPetrol?.toJson() ?: JSONObject.NULL)
            .put("last_cng_region", latestCng?.toJson() ?: JSONObject.NULL)
            .put("last_comparison", latestComparison?.toJson() ?: JSONObject.NULL)
            .put("progress", progress)
            .put("cells", cellsJsonLocked())
            .put("grid", LearningGridProjection.gridJson())
            .put("integrity", integrityJsonLocked())
            .put("adaptive_scale", adaptiveScale.toJson())
            .put("epoch", epoch)
            .put("map_hash", mapHash)
    }

    private fun observePhysicalExit(telemetry: Mp48Telemetry) {
        val current = activeVisit ?: return
        if (telemetry.fuel == Mp48Fuel.CUTOFF || telemetry.fuel == Mp48Fuel.ENGINE_OFF) {
            activeVisit = null
            observedOutsideFrames = 0
            return
        }
        if (!telemetry.plausible || telemetry.fuel !in setOf(Mp48Fuel.PETROL, Mp48Fuel.CNG)) return
        val stillInside = current.fuel == telemetry.fuel && current.epoch == regionEpoch(telemetry.fuel) && visitEquivalent(current.rpmAnchor, current.mapAnchor, telemetry.rpm.toDouble(), telemetry.mapBar)
        if (stillInside) observedOutsideFrames = 0 else {
            observedOutsideFrames += 1
            if (observedOutsideFrames >= LearningToleranceSettings.current.physicalExitFrames) {
                activeVisit = null
                observedOutsideFrames = 0
            }
        }
    }

    private fun resolveVisit(sample: MotorSample): ActiveVisit {
        val current = activeVisit
        val same = current != null && current.fuel == sample.fuel && current.epoch == regionEpoch(sample.fuel) && visitEquivalent(current.rpmAnchor, current.mapAnchor, sample.rpm, sample.mapBar)
        if (same) {
            current!!.lastSeenAtMs = sample.endedAtElapsedMs
            current.samples += 1
            current.rpmAnchor += (sample.rpm - current.rpmAnchor) / current.samples
            current.petrolAnchor += (sample.petrolMs - current.petrolAnchor) / current.samples
            current.mapAnchor += (sample.mapBar - current.mapAnchor) / current.samples
            return current
        }
        return ActiveVisit(UUID.randomUUID().toString(), sample.fuel, regionEpoch(sample.fuel), sample.rpm, sample.petrolMs, sample.mapBar, sample.startedAtElapsedMs, sample.endedAtElapsedMs, 1).also { activeVisit = it }
    }

    private fun ensureCurrentSession(): PhysicalLearningSession {
        currentSession?.let { return it }
        return PhysicalLearningSession(id = sessionId, startedAt = System.currentTimeMillis()).also {
            currentSession = it
            sessions.addLast(it)
            while (sessions.size > MAX_SESSIONS) sessions.removeFirst()
        }
    }

    private fun promoteSessionScaleIfEligible() {
        if (sessionPetrolScaleByVisit.isEmpty()) return
        adaptiveScale = AdaptivePetrolReference.promoteScale(
            previous = adaptiveScale,
            sessionId = sessionId,
            sessionRegions = sessionPetrolScaleByVisit.values.toList(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun closeCurrentSession(reason: String) {
        currentSession?.let {
            if (it.endedAt == 0L) {
                it.endedAt = System.currentTimeMillis()
                it.updatedAt = it.endedAt
                it.endReason = reason.take(120)
            }
        }
        currentSession = null
    }

    private fun updateRegion(sample: MotorSample, visit: ActiveVisit): LearningRegion {
        val desiredEpoch = regionEpoch(sample.fuel)
        val target = nearestRegion(sample.fuel, sample.rpm, sample.mapBar, desiredEpoch)?.takeIf { regionEquivalent(it, sample.rpm, sample.mapBar) }
            ?: LearningRegion.fromSample(sample, desiredEpoch).also { regions += it; if (regions.size > MAX_REGIONS) regions.removeAt(0) }
        target.update(sample, visit.id, sessionId)
        return target
    }

    private fun petrolStatus(region: LearningRegion, sample: MotorSample, visit: ActiveVisit): JSONObject {
        val stage = region.stage()
        return JSONObject()
            .put("state", "PETROL_REFERENCE_$stage")
            .put("reason", when (stage) { "OBSERVED" -> "Referência em gasolina observada e armazenada"; "PROVISIONAL" -> "Referência provisória confirmada por variância"; "ACCEPTED" -> "Referência aceita por densidade"; else -> "Referência confirmada por densidade" })
            .put("reference", region.toJson())
            .put("reference_confidence", region.confidence())
            .put("visit_samples", visit.samples)
            .put("learning", true)
            .put("registered_now", true)
            .put("petrol_target_ms", sample.petrolMs)
    }

    private fun cngStatus(region: LearningRegion, sample: MotorSample, visit: ActiveVisit): JSONObject {
        val reference = petrolReferenceSurface(sample)
        if (reference == null) {
            val diagnostic = JSONObject(lastReferenceDiagnostic.toString())
            return JSONObject().put("state", "NO_PETROL_REFERENCE").put("reason", diagnostic.optString("message", "Falta referência confiável de gasolina nesta condição de RPM, MAP e temperatura")).put("reason_code", diagnostic.optString("reason_code", "NO_PETROL_REFERENCE")).put("reference_diagnostic", diagnostic).put("learning", true).put("registered_now", true).put("cng_region", region.toJson())
        }
        val candidate = compare(reference.petrolTargetMs, sample, visit.id, reference.regionIds.joinToString(","), "CONTINUOUS_REFERENCE_SURFACE", sqrt(reference.quality * sample.quality).coerceIn(0.0, 1.0))
        val stored = addComparisonOnce(candidate)
        val evidence = comparisonEvidence(sample.rpm, sample.mapBar)
        return comparisonStatus(stored.comparison, if (stored.created) "Petrol Inj no GNV comparado à referência local da gasolina" else "Comparação contínua consolidada; esta visita continua contabilizada uma única vez", evidence, stored.created)
            .put("reference_surface", reference.toJson()).put("reference_diagnostic", JSONObject(lastReferenceDiagnostic.toString())).put("reference_stage", reference.stage)
    }

    private fun compare(petrolTargetMs: Double, cngSample: MotorSample, visitId: String, referenceRegionId: String, origin: String, pairQuality: Double): FuelComparison {
        val difference = cngSample.petrolMs - petrolTargetMs
        val errorPct = if (petrolTargetMs <= 0.05) 0.0 else difference / petrolTargetMs * 100.0
        val direction = when { abs(difference) <= LearningToleranceSettings.current.equivalenceDeadbandMs || abs(errorPct) <= LearningToleranceSettings.current.equivalenceDeadbandPercent -> "EQUIVALENT"; difference > 0.0 -> "INCREASE_CNG_DELIVERY"; else -> "DECREASE_CNG_DELIVERY" }
        val cngCell = LearningGridProjection.cellFor(cngSample.rpm, cngSample.petrolMs)
        val referenceCell = LearningGridProjection.cellFor(cngSample.rpm, petrolTargetMs)
        return FuelComparison(UUID.randomUUID().toString(), "$epoch:$origin:$visitId:$referenceRegionId", visitId, referenceRegionId, sessionId, System.currentTimeMillis(), origin, cngSample.rpm, cngSample.mapBar, cngSample.waterC, cngSample.gasC, cngSample.pressureDiffBar, cngCell.getInt("row"), cngCell.getInt("column"), referenceCell.getInt("row"), referenceCell.getInt("column"), petrolTargetMs, cngSample.petrolMs, difference, errorPct, direction, pairQuality.coerceIn(0.0, 1.0), epoch, mapHash)
    }

    private fun comparisonStatus(comparison: FuelComparison, reason: String, evidence: ComparisonEvidence, registeredNow: Boolean): JSONObject {
        val actionable = evidence.actionable(evidence.dominantDirection)
        return JSONObject().put("state", "CONTINUOUS_FUEL_EQUIVALENCE").put("reason", reason).put("learning", true).put("registered_now", registeredNow).put("comparison", comparison.toJson()).put("petrol_target_ms", comparison.petrolTargetMs).put("petrol_on_cng_ms", comparison.petrolOnCngMs).put("error_pct", comparison.errorPct).put("direction", comparison.direction).put("quality", comparison.quality).put("comparison_evidence", evidence.toJson()).put("comparison_stage", evidence.stage).put("evidence_direction", evidence.dominantDirection).put("actionable", actionable).put("suggested_delta_k_percent", if (actionable) (evidence.medianErrorRatio * 35.0 * evidence.confidence()).coerceIn(-5.0, 5.0) else JSONObject.NULL).put("suggested_delta_k", when { !actionable -> JSONObject.NULL; evidence.dominantDirection == "INCREASE_CNG_DELIVERY" -> 1; evidence.dominantDirection == "DECREASE_CNG_DELIVERY" -> -1; else -> JSONObject.NULL }).put("automatic_write", false).put("human_confirmation_required", true)
    }

    private fun addComparisonOnce(candidate: FuelComparison): StoredComparison {
        val existing = comparisons.firstOrNull { it.dedupeKey == candidate.dedupeKey }
        if (existing != null) {
            val consolidated = existing.consolidate(candidate); comparisons.remove(existing); comparisons.addLast(consolidated); return StoredComparison(consolidated, false)
        }
        comparisons.addLast(candidate); while (comparisons.size > MAX_COMPARISONS) comparisons.removeFirst(); sessionComparisons += candidate.id; return StoredComparison(candidate, true)
    }

    private fun comparisonEvidence(rpm: Double, mapBar: Double): ComparisonEvidence {
        val related = comparisons.filter { it.epoch == epoch && visitEquivalent(it.rpm, it.mapBar, rpm, mapBar) }
        val directions = related.groupingBy { it.direction }.eachCount()
        val dominant = directions.maxByOrNull { it.value }?.key ?: "EQUIVALENT"
        val consensus = if (related.isEmpty()) 0.0 else (directions[dominant] ?: 0) / related.size.toDouble()
        val center = related.map { it.differenceMs }.median(); val mad = related.map { abs(it.differenceMs - center) }.median()
        val gasTempSpan = related.valueSpan { it.gasC }; val pressureSpan = related.valueSpan { it.pressureDiffBar }
        val effectiveSamples = ContinuousLearningMath.effectiveSampleSize(related.map { it.quality })
        val rawStage = confidenceStage(effectiveSamples, mad * mad)
        val reliable = consensus >= LearningToleranceSettings.current.directionConsensusMinimum && mad <= LearningToleranceSettings.current.comparisonMaximumMadMs
        val stage = if (rawStage in setOf("ACCEPTED", "CONFIRMED") && !reliable) "PROVISIONAL" else rawStage
        return ComparisonEvidence(stage, dominant, consensus, center, mad, gasTempSpan, pressureSpan, effectiveSamples, if (center == 0.0) 0.0 else related.map { it.errorPct / 100.0 }.median())
    }

    private fun comparisonEvidenceForCell(row: Int, column: Int): ComparisonEvidence {
        val related = comparisons.filter { it.epoch == epoch && it.affects(row, column) }
        if (related.isEmpty()) return ComparisonEvidence.empty()
        val weighted = related.mapNotNull { comparison -> val cellWeight = comparison.weightAt(row, column); if (cellWeight <= 0.0) null else comparison to (cellWeight * comparison.quality) }
        val total = weighted.sumOf { it.second }; if (total <= 0.0) return ComparisonEvidence.empty()
        val mean = weighted.sumOf { it.first.errorPct / 100.0 * it.second } / total
        val differenceCenterMs = weighted.map { it.first.differenceMs to it.second }.weightedMedian()
        val madMs = weighted.map { abs(it.first.differenceMs - differenceCenterMs) to it.second }.weightedMedian()
        val dominant = errorRatioDirection(mean)
        val consensus = weighted.filter { errorRatioDirection(it.first.errorPct / 100.0) == dominant }.sumOf { it.second } / total
        val effectiveSamples = ContinuousLearningMath.effectiveSampleSize(weighted.map { it.second })
        return ComparisonEvidence(confidenceStage(effectiveSamples, madMs * madMs), dominant, consensus, differenceCenterMs, madMs, related.valueSpan { it.gasC }, related.valueSpan { it.pressureDiffBar }, effectiveSamples, mean)
    }

    private fun nearestRegion(fuel: Mp48Fuel, rpm: Double, mapBar: Double, regionEpoch: Int): LearningRegion? = regions.asSequence().filter { it.fuel == fuel && it.epoch == regionEpoch }.minByOrNull { normalizedDistance(it.rpmMean, it.mapMean, rpm, mapBar) }
    private fun normalizedDistance(rpmA: Double, mapA: Double, rpmB: Double, mapB: Double): Double { val tolerance = LearningToleranceSettings.current; val rpmScale = max(tolerance.historicalRpmMinimum, max(abs(rpmA), abs(rpmB)) * tolerance.historicalRpmPercent / 100.0); val dr = abs(rpmA - rpmB) / rpmScale; val dm = abs(mapA - mapB) / tolerance.historicalMapBar; return sqrt(dr * dr + dm * dm) }
    private fun regionEquivalent(region: LearningRegion, rpm: Double, mapBar: Double): Boolean { val tolerance = LearningToleranceSettings.current; val rpmLimit = max(tolerance.historicalRpmMinimum, max(abs(region.rpmMean), abs(rpm)) * tolerance.historicalRpmPercent / 100.0); return abs(region.rpmMean - rpm) <= rpmLimit && abs(region.mapMean - mapBar) <= tolerance.historicalMapBar }

    private fun petrolReferenceSurface(sample: MotorSample): PetrolReferenceEstimate? {
        referenceAttempts += 1L
        val petrolRegions = regions.asSequence().filter { it.fuel == Mp48Fuel.PETROL && it.epoch == 0 }.map { region -> PetrolReferenceSelector.Region(region.id, region.rpmMean, region.mapMean, region.waterMean, region.petrolMean, region.confidence(), region.sampleCount) }.toList()
        val result = AdaptivePetrolReference.estimate(regions = petrolRegions, request = PetrolReferenceSelector.Request(sample.rpm, sample.mapBar, sample.waterC), acceptedScale = adaptiveScale.acceptedScale, policy = LearningToleranceSettings.current)
        lastReferenceDiagnostic = result.toJson()
        if (!result.available) { referenceRejectCounts[result.reasonCode] = (referenceRejectCounts[result.reasonCode] ?: 0L) + 1L; return null }
        referenceAccepted += 1L
        return PetrolReferenceEstimate(requireNotNull(result.petrolTargetMs), result.spreadMs ?: 0.0, result.quality, result.regionIds, result.stage, result.extrapolated, result.reasonCode)
    }

    private fun visitEquivalent(rpmA: Double, mapA: Double, rpmB: Double, mapB: Double): Boolean { val tolerance = LearningToleranceSettings.current; val rpmLimit = max(tolerance.historicalRpmMinimum, max(abs(rpmA), abs(rpmB)) * tolerance.historicalRpmPercent / 100.0); return abs(rpmA - rpmB) <= rpmLimit && abs(mapA - mapB) <= tolerance.historicalMapBar }
    private fun regionEpoch(fuel: Mp48Fuel): Int = if (fuel == Mp48Fuel.PETROL) 0 else epoch

    private fun summaryLocked(): JSONObject {
        val petrolRegions = regions.filter { it.fuel == Mp48Fuel.PETROL }; val cngCurrent = regions.filter { it.fuel == Mp48Fuel.CNG && it.epoch == epoch }; val cngAll = regions.filter { it.fuel == Mp48Fuel.CNG }; val comparisonsCurrent = comparisons.filter { it.epoch == epoch }
        return JSONObject().put("session_id", sessionId).put("epoch", epoch).put("map_hash", mapHash).put("petrol_regions", petrolRegions.size).put("cng_regions", cngCurrent.size).put("cng_regions_all_epochs", cngAll.size).put("comparisons", comparisonsCurrent.size).put("comparisons_all_epochs", comparisons.size).put("direct_comparisons", 0).put("confirmed_petrol_regions", petrolRegions.count { it.stage() == "CONFIRMED" }).put("reference_attempts", referenceAttempts).put("reference_accepted", referenceAccepted).put("reference_rejected", (referenceAttempts - referenceAccepted).coerceAtLeast(0L)).put("reference_rejection_reasons", JSONObject(referenceRejectCounts as Map<*, *>)).put("last_reference_diagnostic", JSONObject(lastReferenceDiagnostic.toString()))
    }

    private fun sessionSummaryLocked(): JSONObject {
        val s = currentSession ?: sessions.lastOrNull()
        return JSONObject().put("session_id", s?.id ?: sessionId).put("epoch", epoch).put("started_at", s?.startedAt ?: 0L).put("ended_at", s?.endedAt ?: 0L).put("duration_ms", s?.durationMs() ?: 0L).put("samples", s?.sampleCount ?: 0).put("fuels", JSONArray(s?.fuels?.toList() ?: emptyList<String>())).put("end_reason", s?.endReason ?: "ACTIVE").put("petrol_regions", sessionPetrolRegions.size).put("petrol_scale_visits", sessionPetrolScaleByVisit.size).put("cng_regions", sessionCngRegions.size).put("comparisons", sessionComparisons.size).put("direct_comparisons", 0)
    }

    private fun rebuildVisualStatusFromMemory() {
        if (lastStatus.length() > 0) return
        val latestComparison = comparisons.lastOrNull()
        if (latestComparison != null) { val evidence = comparisonEvidence(latestComparison.rpm, latestComparison.mapBar); lastStatus = comparisonStatus(latestComparison, "Última comparação preservada na memória", evidence, false).put("restored_from_memory", true); return }
        val latestReference = regions.filter { it.fuel == Mp48Fuel.PETROL }.maxByOrNull { it.updatedAt }
        if (latestReference != null) { lastStatus = JSONObject().put("state", "PETROL_REFERENCE_${latestReference.stage()}").put("reason", "Última referência em gasolina preservada na memória").put("reference", latestReference.toJson()).put("reference_confidence", latestReference.confidence()).put("learning", false).put("restored_from_memory", true); return }
        lastStatus = JSONObject().put("state", "OBSERVING_ENGINE").put("reason", "Memória ainda vazia").put("learning", false)
    }

    private fun load() = synchronized(lock) {
        try {
            val backup = File(stateFile.parentFile, stateFile.name + ".bak"); val primary = readValidState(stateFile); val recovered = primary == null; val root = primary ?: readValidState(backup) ?: return@synchronized
            if (recovered && backup.isFile) { backup.copyTo(stateFile, overwrite = true); log.add("WARN", "LEARNING-INTEGRITY", "Memória principal recuperada do último backup válido") }
            if (root.optString("format") != FORMAT) { log.add("INFO", "LEARNING-V5", "Memória de outro formato ignorada; iniciando uma memória V5 limpa"); return@synchronized }
            epoch = root.optInt("epoch", 1).coerceAtLeast(1); mapHash = root.optString("mapHash", ""); adaptiveScale = AdaptivePetrolScaleState.fromJson(root.optJSONObject("adaptiveScale")); lastCalibrationRevalidation = root.optJSONObject("revalidation")?.let { JSONObject(it.toString()) } ?: JSONObject()
            val savedRegions = root.optJSONArray("regions") ?: JSONArray(); repeat(savedRegions.length()) { index -> savedRegions.optJSONObject(index)?.let { regions += LearningRegion.fromJson(it) } }
            val savedComparisons = root.optJSONArray("comparisons") ?: JSONArray(); repeat(savedComparisons.length()) { index -> savedComparisons.optJSONObject(index)?.let { comparisons += FuelComparison.fromJson(it) } }
            val savedSessions = root.optJSONArray("sessions") ?: JSONArray(); repeat(savedSessions.length()) { index -> savedSessions.optJSONObject(index)?.let { sessions += PhysicalLearningSession.fromJson(it) } }
            sessions.filter { it.endedAt == 0L }.forEach { it.endedAt = max(it.updatedAt, it.startedAt); it.endReason = "PROCESS_INTERRUPTED" }
        } catch (error: Exception) { log.add("WARN", "LEARNING-NATIVE", "Memória não carregada: ${error.message}") }
    }

    private fun persist() { persistDirty.set(true); schedulePersistDrain() }
    private fun schedulePersistDrain() {
        if (!persistDrainScheduled.compareAndSet(false, true)) return
        lastPersistFuture = persistExecutor.submit { try { while (persistDirty.getAndSet(false)) { val snapshot = synchronized(lock) { persistenceSnapshotLocked() }; writePersistedState(buildPersistedState(snapshot)) } } finally { persistDrainScheduled.set(false); if (persistDirty.get()) schedulePersistDrain() } }
    }
    private fun persistenceSnapshotLocked() = MotorLearningPersistenceSnapshot(System.currentTimeMillis(), epoch, mapHash, adaptiveScale, lastCalibrationRevalidation.toString(), LearningToleranceSettings.current.toJson().toString(), regions.map { it.persistenceCopy() }, comparisons.toList(), sessions.map { it.persistenceCopy() })

    private fun buildPersistedState(snapshot: MotorLearningPersistenceSnapshot): JSONObject {
        var selectedVisitLimit = LearningMemoryBudget.MAX_REGION_VISIT_IDS; var selectedSessionLimit = LearningMemoryBudget.MAX_REGION_SESSION_IDS; var selected = JSONObject(); var selectedBytes = Long.MAX_VALUE
        for ((visitLimit, sessionLimit) in LearningMemoryBudget.provenanceLevels) {
            val root = JSONObject().put("format", FORMAT).put("savedAt", snapshot.savedAt).put("epoch", snapshot.epoch).put("mapHash", snapshot.mapHash).put("adaptiveScale", snapshot.adaptiveScale.toJson()).put("revalidation", JSONObject(snapshot.revalidationJson)).put("tolerancePolicy", JSONObject(snapshot.tolerancePolicyJson)).put("regions", JSONArray(snapshot.regions.map { it.toPersistedJson(visitLimit, sessionLimit) })).put("comparisons", JSONArray(snapshot.comparisons.map { it.toJson() })).put("sessions", JSONArray(snapshot.sessions.map { it.toJson() })).put("memoryBudget", JSONObject().put("policy", LearningMemoryBudget.POLICY).put("targetPersistedBytes", LearningMemoryBudget.TARGET_PERSISTED_BYTES).put("maxRegionVisitIds", visitLimit).put("maxRegionSessionIds", sessionLimit).put("regionCount", snapshot.regions.size).put("comparisonCount", snapshot.comparisons.size).put("sessionCount", snapshot.sessions.size))
            val bytes = root.toString().toByteArray(Charsets.UTF_8).size.toLong(); selected = root; selectedBytes = bytes; selectedVisitLimit = visitLimit; selectedSessionLimit = sessionLimit; if (bytes <= LearningMemoryBudget.TARGET_PERSISTED_BYTES) break
        }
        selected.optJSONObject("memoryBudget")?.put("payloadBytesBeforeDigest", selectedBytes)?.put("targetExceeded", selectedBytes > LearningMemoryBudget.TARGET_PERSISTED_BYTES)?.put("provenanceCompacted", selectedVisitLimit < LearningMemoryBudget.MAX_REGION_VISIT_IDS || selectedSessionLimit < LearningMemoryBudget.MAX_REGION_SESSION_IDS)
        if (selectedBytes > LearningMemoryBudget.TARGET_PERSISTED_BYTES) log.add("WARN", "LEARNING-BUDGET", "Estado científico excedeu o alvo de bytes mesmo sem proveniência completa; ciência preservada (${selectedBytes} bytes)")
        return selected
    }

    private fun writePersistedState(root: JSONObject) {
        try {
            root.put("stateDigest", canonicalDigest(root)); stateFile.parentFile?.mkdirs(); val temp = File(stateFile.parentFile, stateFile.name + ".tmp"); val backup = File(stateFile.parentFile, stateFile.name + ".bak"); val encoded = root.toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temp).use { output -> output.write(encoded); output.flush(); output.fd.sync() }
            if (stateFile.isFile) stateFile.copyTo(backup, overwrite = true)
            try { Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (_: Exception) { Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            if (readValidState(stateFile) == null && backup.isFile) backup.copyTo(stateFile, overwrite = true)
        } catch (error: Exception) { log.add("WARN", "LEARNING-NATIVE", "Erro ao salvar memória: ${error.message}") }
    }

    private fun readValidState(file: File): JSONObject? {
        if (!file.isFile) return null
        return try { val root = JSONObject(file.readText(Charsets.UTF_8)); val expected = root.optString("stateDigest"); if (expected.isNotBlank() && canonicalDigest(root) != expected) { log.add("ERROR", "LEARNING-INTEGRITY", "Divergência detectada em ${file.name}"); null } else root } catch (error: Exception) { log.add("WARN", "LEARNING-INTEGRITY", "Arquivo inválido ${file.name}: ${error.message}"); null }
    }
    private fun canonicalDigest(json: JSONObject): String { val copy = JSONObject(json.toString()).apply { remove("stateDigest") }; val sortedJson = JSONObject(); for (key in copy.keys().asSequence().sorted()) sortedJson.put(key, copy.get(key)); return sha256(sortedJson.toString()) }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private data class MotorLearningPersistenceSnapshot(val savedAt: Long, val epoch: Int, val mapHash: String, val adaptiveScale: AdaptivePetrolScaleState, val revalidationJson: String, val tolerancePolicyJson: String, val regions: List<LearningRegion>, val comparisons: List<FuelComparison>, val sessions: List<PhysicalLearningSession>)

private data class PhysicalLearningSession(val id: String, val startedAt: Long, var endedAt: Long = 0L, var updatedAt: Long = startedAt, var sampleCount: Int = 0, val fuels: MutableSet<String> = linkedSetOf(), var endReason: String = "ACTIVE") {
    fun durationMs(now: Long = System.currentTimeMillis()): Long = ((if (endedAt > 0L) endedAt else now) - startedAt).coerceAtLeast(0L)
    fun persistenceCopy() = copy(fuels = fuels.toCollection(linkedSetOf()))
    fun toJson(): JSONObject = JSONObject().put("id", id).put("started_at", startedAt).put("ended_at", endedAt).put("updated_at", updatedAt).put("duration_ms", durationMs()).put("samples", sampleCount).put("fuels", JSONArray(fuels.toList())).put("end_reason", endReason)
    companion object { fun fromJson(raw: JSONObject): PhysicalLearningSession { val fuels = linkedSetOf<String>(); raw.optJSONArray("fuels")?.let { array -> repeat(array.length()) { fuels += array.optString(it) } }; return PhysicalLearningSession(raw.optString("id", UUID.randomUUID().toString()), raw.optLong("started_at", 0L), raw.optLong("ended_at", 0L), raw.optLong("updated_at", 0L), raw.optInt("samples", 0), fuels, raw.optString("end_reason", "RESTORED")) } }
}

private data class ActiveVisit(val id: String, val fuel: Mp48Fuel, val epoch: Int, var rpmAnchor: Double, var petrolAnchor: Double, var mapAnchor: Double, val startedAtMs: Long, var lastSeenAtMs: Long, var samples: Int)

private data class LearningRegion(val id: String, val fuel: Mp48Fuel, val epoch: Int, var rpmMean: Double, var mapMean: Double, var petrolMean: Double, var petrolSquaredMean: Double, var pressureMean: Double, var waterMean: Double, var gasMean: Double, var qualityMean: Double, var weight: Double = 0.0, var sampleCount: Int = 0, val visits: MutableSet<String> = linkedSetOf(), val sessions: MutableSet<String> = linkedSetOf(), var visitCount: Int = visits.size, var sessionCount: Int = sessions.size, var updatedAt: Long = 0L) {
    private var lastVisitId: String? = visits.lastOrNull(); private var lastSessionId: String? = sessions.lastOrNull()
    fun update(sample: MotorSample, visitId: String, sessionId: String) {
        val durationWeight = ContinuousLearningMath.dwellWeight(sample.endedAtElapsedMs - sample.startedAtElapsedMs); val sampleWeight = max(0.10, sample.quality) * (0.25 + 0.75 * durationWeight); val total = weight + sampleWeight
        fun blend(current: Double, incoming: Double) = current + (incoming - current) * sampleWeight / max(sampleWeight, total)
        rpmMean = blend(rpmMean, sample.rpm); mapMean = blend(mapMean, sample.mapBar); petrolMean = blend(petrolMean, sample.petrolMs); petrolSquaredMean = blend(petrolSquaredMean, sample.petrolMs * sample.petrolMs); pressureMean = blend(pressureMean, sample.pressureDiffBar); waterMean = blend(waterMean, sample.waterC); gasMean = blend(gasMean, sample.gasC); qualityMean = blend(qualityMean, sample.quality); weight = total; sampleCount += 1
        if (visitId != lastVisitId) { visitCount = saturatingAdd(max(visitCount, visits.size), 1); lastVisitId = visitId }; if (sessionId != lastSessionId) { sessionCount = saturatingAdd(max(sessionCount, sessions.size), 1); lastSessionId = sessionId }; visits += visitId; sessions += sessionId; LearningMemoryBudget.trimNewestIds(visits, LearningMemoryBudget.MAX_REGION_VISIT_IDS); LearningMemoryBudget.trimNewestIds(sessions, LearningMemoryBudget.MAX_REGION_SESSION_IDS); updatedAt = System.currentTimeMillis()
    }
    fun merge(other: LearningRegion) {
        val incomingWeight = max(0.10, other.weight); val total = weight + incomingWeight; fun blend(current: Double, incoming: Double) = current + (incoming - current) * incomingWeight / max(incomingWeight, total)
        rpmMean = blend(rpmMean, other.rpmMean); mapMean = blend(mapMean, other.mapMean); petrolMean = blend(petrolMean, other.petrolMean); petrolSquaredMean = blend(petrolSquaredMean, other.petrolSquaredMean); pressureMean = blend(pressureMean, other.pressureMean); waterMean = blend(waterMean, other.waterMean); gasMean = blend(gasMean, other.gasMean); qualityMean = blend(qualityMean, other.qualityMean); weight = total; sampleCount = saturatingAdd(sampleCount, other.sampleCount)
        val visitOverlap = visits.intersect(other.visits).size; val sessionOverlap = sessions.intersect(other.sessions).size; visitCount = saturatingAdd(max(visitCount, visits.size), (max(other.visitCount, other.visits.size) - visitOverlap).coerceAtLeast(0)); sessionCount = saturatingAdd(max(sessionCount, sessions.size), (max(other.sessionCount, other.sessions.size) - sessionOverlap).coerceAtLeast(0)); visits += other.visits; sessions += other.sessions; LearningMemoryBudget.trimNewestIds(visits, LearningMemoryBudget.MAX_REGION_VISIT_IDS); LearningMemoryBudget.trimNewestIds(sessions, LearningMemoryBudget.MAX_REGION_SESSION_IDS); lastVisitId = visits.lastOrNull(); lastSessionId = sessions.lastOrNull(); updatedAt = max(updatedAt, other.updatedAt)
    }
    fun namespace(source: String) = copy(id = "$source:$id", visits = visits.mapTo(linkedSetOf()) { "$source:$it" }, sessions = sessions.mapTo(linkedSetOf()) { "$source:$it" })
    fun persistenceCopy() = copy(visits = visits.toCollection(linkedSetOf()), sessions = sessions.toCollection(linkedSetOf()))
    fun stage() = confidenceStage(sampleCount.toDouble(), max(0.0, petrolSquaredMean - petrolMean * petrolMean))
    fun confidence(): Double { val t = LearningToleranceSettings.current; val samplePart = (sampleCount / t.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0); val variancePart = (1.0 - max(0.0, petrolSquaredMean - petrolMean * petrolMean) / (t.referenceMaximumSpreadMs * t.referenceMaximumSpreadMs)).coerceIn(0.1, 1.0); return listOf(samplePart.coerceAtLeast(0.05), variancePart, qualityMean.coerceIn(0.10, 1.0)).geometricMean() }
    fun progressJson(): JSONObject = LearningToleranceSettings.current.let { t -> JSONObject().put("stage", stage()).put("visits", visitCount).put("sessions", sessionCount).put("confidence", confidence()).put("confidence_samples", (sampleCount / t.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0)).put("next_visit_target", t.confidenceSampleTarget).put("rpm", rpmMean).put("map_bar", mapMean) }
    fun toJson() = toJsonWithProvenance(LearningMemoryBudget.MAX_REGION_VISIT_IDS, LearningMemoryBudget.MAX_REGION_SESSION_IDS)
    fun toPersistedJson(visitLimit: Int, sessionLimit: Int) = toJsonWithProvenance(visitLimit, sessionLimit)
    fun toAdvisorJson() = toJsonWithProvenance(LearningMemoryBudget.MAX_REGION_VISIT_IDS, 0)
    private fun toJsonWithProvenance(visitLimit: Int, sessionLimit: Int): JSONObject = LearningToleranceSettings.current.let { t -> val rv = LearningMemoryBudget.retainNewestIds(visits, visitLimit); val rs = LearningMemoryBudget.retainNewestIds(sessions, sessionLimit); JSONObject().put("id", id).put("fuel", fuel.wireName).put("epoch", epoch).put("rpm", rpmMean).put("map_bar", mapMean).put("petrol_ms", petrolMean).put("petrol_squared_mean", petrolSquaredMean).put("petrol_spread_ms", sqrt(max(0.0, petrolSquaredMean - petrolMean * petrolMean))).put("pressure_diff_bar", pressureMean).put("water_c", waterMean).put("gas_c", gasMean).put("quality", qualityMean).put("weight", weight).put("samples", sampleCount).put("visits", JSONArray(rv.toList())).put("sessions", JSONArray(rs.toList())).put("visit_count", max(visitCount, rv.size)).put("session_count", max(sessionCount, rs.size)).put("visit_ids_retained", rv.size).put("session_ids_retained", rs.size).put("visit_ids_compacted", visitCount > rv.size).put("session_ids_compacted", sessionCount > rs.size).put("provenance_policy", LearningMemoryBudget.POLICY).put("stage", stage()).put("confidence", confidence()).put("confidence_samples", (sampleCount / t.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0)).put("updated_at", updatedAt) }
    companion object {
        fun fromSample(sample: MotorSample, epoch: Int) = LearningRegion(UUID.randomUUID().toString(), sample.fuel, epoch, sample.rpm, sample.mapBar, sample.petrolMs, sample.petrolMs * sample.petrolMs, sample.pressureDiffBar, sample.waterC, sample.gasC, sample.quality)
        fun fromJson(raw: JSONObject): LearningRegion { val visitArray = raw.optJSONArray("visits"); val sessionArray = raw.optJSONArray("sessions"); val visits = retainedIds(visitArray, LearningMemoryBudget.MAX_REGION_VISIT_IDS); val sessions = retainedIds(sessionArray, LearningMemoryBudget.MAX_REGION_SESSION_IDS); val fuel = if (raw.optString("fuel") == Mp48Fuel.CNG.wireName) Mp48Fuel.CNG else Mp48Fuel.PETROL; val petrolMean = raw.optDouble("petrol_ms", 0.0); return LearningRegion(raw.optString("id", UUID.randomUUID().toString()), fuel, if (fuel == Mp48Fuel.PETROL) 0 else raw.optInt("epoch", 1).coerceAtLeast(1), raw.optDouble("rpm", 0.0), raw.optDouble("map_bar", 0.0), petrolMean, raw.optDouble("petrol_squared_mean", petrolMean * petrolMean), raw.optDouble("pressure_diff_bar", 0.0), raw.optDouble("water_c", 0.0), raw.optDouble("gas_c", 0.0), raw.optDouble("quality", 0.5), raw.optDouble("weight", 0.0), raw.optInt("samples", 0), visits, sessions, max(raw.optInt("visit_count", visitArray?.length() ?: 0), visitArray?.length() ?: 0), max(raw.optInt("session_count", sessionArray?.length() ?: 0), sessionArray?.length() ?: 0), raw.optLong("updated_at", 0L)) }
        private fun retainedIds(raw: JSONArray?, limit: Int): LinkedHashSet<String> { if (raw == null || limit <= 0) return linkedSetOf(); val result = linkedSetOf<String>(); for (index in (raw.length() - limit).coerceAtLeast(0) until raw.length()) raw.optString(index).takeIf { it.isNotBlank() }?.let(result::add); return result }
        private fun saturatingAdd(a: Int, b: Int) = (a.toLong() + b.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

private data class PetrolReferenceEstimate(val petrolTargetMs: Double, val spreadMs: Double, val quality: Double, val regionIds: List<String>, val stage: String, val extrapolated: Boolean = false, val reasonCode: String = "LOCAL_REFERENCE_AVAILABLE") {
    fun toJson(): JSONObject = JSONObject().put("petrol_target_ms", petrolTargetMs).put("spread_ms", spreadMs).put("quality", quality).put("region_ids", JSONArray(regionIds)).put("region_count", regionIds.size).put("stage", stage).put("reason_code", reasonCode).put("method", if (reasonCode == "ADAPTIVE_PRIOR_RESIDUAL") "ADAPTIVE_F2_LOCAL_QUADRATIC_RESIDUAL" else "RPM_MAP_WATER_WEIGHTED_SURFACE").put("extrapolated", extrapolated).put("extrapolation_weight", if (extrapolated) 0.35 else 1.0)
}

private data class FuelComparison(val id: String, val dedupeKey: String, val visitId: String, val referenceRegionId: String, val sessionId: String, val capturedAt: Long, val origin: String, val rpm: Double, val mapBar: Double, val waterC: Double, val gasC: Double, val pressureDiffBar: Double, val cngCellRow: Int, val cngCellColumn: Int, val referenceCellRow: Int, val referenceCellColumn: Int, val petrolTargetMs: Double, val petrolOnCngMs: Double, val differenceMs: Double, val errorPct: Double, val direction: String, val quality: Double, val epoch: Int, val mapHash: String, val observationCount: Int = 1) {
    fun consolidate(newer: FuelComparison): FuelComparison { require(dedupeKey == newer.dedupeKey); val oldCount = observationCount.coerceAtLeast(1); val newCount = newer.observationCount.coerceAtLeast(1); val oldWeight = (quality * oldCount).takeIf { it > 0.0 } ?: oldCount.toDouble(); val newWeight = (newer.quality * newCount).takeIf { it > 0.0 } ?: newCount.toDouble(); val totalWeight = oldWeight + newWeight; fun blended(old: Double, new: Double) = (old * oldWeight + new * newWeight) / totalWeight; val targetMs = blended(petrolTargetMs, newer.petrolTargetMs); val observedMs = blended(petrolOnCngMs, newer.petrolOnCngMs); val diff = observedMs - targetMs; val error = if (targetMs <= 0.05) 0.0 else diff / targetMs * 100.0; val dir = when { abs(diff) <= LearningToleranceSettings.current.equivalenceDeadbandMs || abs(error) <= LearningToleranceSettings.current.equivalenceDeadbandPercent -> "EQUIVALENT"; diff > 0.0 -> "INCREASE_CNG_DELIVERY"; else -> "DECREASE_CNG_DELIVERY" }; val cr = blended(rpm, newer.rpm); val cm = blended(mapBar, newer.mapBar); val cc = LearningGridProjection.cellFor(cr, observedMs); val rc = LearningGridProjection.cellFor(cr, targetMs); return copy(capturedAt = max(capturedAt, newer.capturedAt), rpm = cr, mapBar = cm, waterC = blended(waterC, newer.waterC), gasC = blended(gasC, newer.gasC), pressureDiffBar = blended(pressureDiffBar, newer.pressureDiffBar), cngCellRow = cc.getInt("row"), cngCellColumn = cc.getInt("column"), referenceCellRow = rc.getInt("row"), referenceCellColumn = rc.getInt("column"), petrolTargetMs = targetMs, petrolOnCngMs = observedMs, differenceMs = diff, errorPct = error, direction = dir, quality = ((quality * oldCount + newer.quality * newCount) / (oldCount + newCount)).coerceIn(0.0, 1.0), observationCount = oldCount + newCount) }
    fun weightAt(row: Int, column: Int) = ContinuousLearningMath.bilinearWeights(rpm, petrolOnCngMs).firstOrNull { it.row == row && it.column == column }?.weight ?: 0.0
    fun affects(row: Int, column: Int) = weightAt(row, column) > 0.0
    fun toJson(): JSONObject = JSONObject().put("id", id).put("dedupe_key", dedupeKey).put("visit_id", visitId).put("reference_region_id", referenceRegionId).put("session_id", sessionId).put("captured_at", capturedAt).put("origin", origin).put("rpm", rpm).put("map_bar", mapBar).put("water_c", waterC).put("gas_c", gasC).put("pressure_diff_bar", pressureDiffBar).put("cng_cell_row", cngCellRow).put("cng_cell_column", cngCellColumn).put("reference_cell_row", referenceCellRow).put("reference_cell_column", referenceCellColumn).put("continuous_cell_weights", JSONArray(ContinuousLearningMath.bilinearWeights(rpm, petrolOnCngMs).map { JSONObject().put("row", it.row).put("column", it.column).put("weight", it.weight) })).put("cross_cell_equivalence", cngCellRow != referenceCellRow || cngCellColumn != referenceCellColumn).put("petrol_target_ms", petrolTargetMs).put("petrol_on_cng_ms", petrolOnCngMs).put("difference_ms", differenceMs).put("error_pct", errorPct).put("direction", direction).put("quality", quality).put("epoch", epoch).put("map_hash", mapHash).put("observation_count", observationCount)
    companion object { fun fromJson(raw: JSONObject) = FuelComparison(raw.optString("id", UUID.randomUUID().toString()), raw.optString("dedupe_key", UUID.randomUUID().toString()), raw.optString("visit_id", ""), raw.optString("reference_region_id", ""), raw.optString("session_id", ""), raw.optLong("captured_at", 0L), raw.optString("origin", "HISTORICAL"), raw.optDouble("rpm", 0.0), raw.optDouble("map_bar", 0.0), raw.optDouble("water_c", 0.0), raw.optDouble("gas_c", 0.0), raw.optDouble("pressure_diff_bar", 0.0), raw.optInt("cng_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_on_cng_ms", 0.0)).getInt("row")), raw.optInt("cng_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_on_cng_ms", 0.0)).getInt("column")), raw.optInt("reference_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_target_ms", 0.0)).getInt("row")), raw.optInt("reference_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_target_ms", 0.0)).getInt("column")), raw.optDouble("petrol_target_ms", 0.0), raw.optDouble("petrol_on_cng_ms", 0.0), raw.optDouble("difference_ms", 0.0), raw.optDouble("error_pct", 0.0), raw.optString("direction", "EQUIVALENT"), raw.optDouble("quality", 0.0), raw.optInt("epoch", 1), raw.optString("map_hash", ""), raw.optInt("observation_count", 1).coerceAtLeast(1)) }
}

private data class ComparisonEvidence(val stage: String, val dominantDirection: String, val directionConsensus: Double, val medianDifferenceMs: Double, val madMs: Double, val gasTemperatureSpanC: Double, val pressureSpanBar: Double, val effectiveSamples: Double = 0.0, val medianErrorRatio: Double = 0.0) {
    fun actionable(direction: String) = direction != "EQUIVALENT" && direction == dominantDirection && directionConsensus >= LearningToleranceSettings.current.directionConsensusMinimum && madMs <= LearningToleranceSettings.current.comparisonMaximumMadMs && effectiveSamples > 0.0
    fun confidence(): Double { val spread = (1.0 - madMs / LearningToleranceSettings.current.comparisonMaximumMadMs).coerceIn(0.0, 1.0); return (directionConsensus * spread * (1.0 - 1.0 / (1.0 + effectiveSamples))).coerceIn(0.0, 1.0) }
    fun toJson(): JSONObject = LearningToleranceSettings.current.let { t -> JSONObject().put("stage", stage).put("dominant_direction", dominantDirection).put("direction_consensus", directionConsensus).put("median_difference_ms", medianDifferenceMs).put("mad_ms", madMs).put("gas_temperature_span_c", gasTemperatureSpanC).put("pressure_span_bar", pressureSpanBar).put("effective_samples", effectiveSamples).put("median_error_ratio", medianErrorRatio).put("stable", madMs <= t.comparisonMaximumMadMs).put("confidence", when (stage) { "CONFIRMED" -> 1.0; "ACCEPTED" -> 0.75; "PROVISIONAL" -> 0.50; else -> if (effectiveSamples > 0) 0.20 else 0.0 }).put("next_visit_target", t.confidenceSampleTarget) }
    companion object { fun empty() = ComparisonEvidence("OBSERVED", "EQUIVALENT", 0.0, 0.0, 0.0, 0.0, 0.0) }
}

private data class StoredComparison(val comparison: FuelComparison, val created: Boolean)
private fun confidenceStage(density: Double, variance: Double): String = LearningToleranceSettings.current.let { t -> when { density >= t.confidenceSampleTarget * 0.8 && variance < t.referenceMaximumSpreadMs * t.referenceMaximumSpreadMs * 0.5 -> "CONFIRMED"; density >= t.confidenceSampleTarget * 0.5 && variance < t.referenceMaximumSpreadMs * t.referenceMaximumSpreadMs -> "ACCEPTED"; density >= t.confidenceSampleTarget * 0.2 -> "PROVISIONAL"; else -> "OBSERVED" } }
private fun List<Double>.geometricMean(): Double { if (isEmpty()) return 0.0; return fold(1.0) { acc, value -> acc * value.coerceIn(0.0001, 1.0) }.pow(1.0 / size).coerceIn(0.0, 1.0) }
private fun List<Double>.median(): Double { if (isEmpty()) return 0.0; val ordered = sorted(); val middle = ordered.size / 2; return if (ordered.size % 2 == 1) ordered[middle] else (ordered[middle - 1] + ordered[middle]) / 2.0 }
private fun List<Pair<Double, Double>>.weightedMedian(): Double { val usable = filter { it.first.isFinite() && it.second.isFinite() && it.second > 0.0 }.sortedBy { it.first }; if (usable.isEmpty()) return 0.0; val half = usable.sumOf { it.second } / 2.0; var cumulative = 0.0; usable.forEach { (value, weight) -> cumulative += weight; if (cumulative >= half) return value }; return usable.last().first }
private fun errorRatioDirection(errorRatio: Double): String = when { abs(errorRatio) <= LearningToleranceSettings.current.equivalenceDeadbandPercent / 100.0 -> "EQUIVALENT"; errorRatio > 0.0 -> "INCREASE_CNG_DELIVERY"; else -> "DECREASE_CNG_DELIVERY" }
private fun <T> List<T>.valueSpan(selector: (T) -> Double): Double { if (isEmpty()) return 0.0; val values = map(selector); return (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0) }
