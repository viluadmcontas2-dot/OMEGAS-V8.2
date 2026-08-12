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
            // Somente gasolina é portátil. GNV depende do mapa K que o produziu.
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
                if (comparison.epoch != previousEpoch) {
                    comparison
                } else if (comparisonTouchesAny(comparison, adjustedCells)) {
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
            .put(
                "reason",
                if (localizedMapAdjustment) {
                    "Mapa K confirmado; apenas as células alteradas precisam de nova validação"
                } else {
                    "Calibração global confirmada; evidências GNV começam em uma época nova"
                },
            )
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
            ?: return@synchronized JSONObject()
                .put("ok", false)
                .put("error", "Ainda não existe evidência ligada a esta região contínua do mapa K")
        val evidence = comparisonEvidenceForCell(row, column)
        // Modo de edição livre: sempre permite.
        val confidence = evidence.confidence()
        val proposed = (value * (1.0 + 0.35 * confidence * evidence.medianErrorRatio))
            .coerceIn(50.0, 255.0)
            .toInt()
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

    private fun regionsJsonLocked(): JSONArray =
        JSONArray(regions.map { LearningGridProjection.enrichRegion(it.toJson()) })

    private fun cellsJsonLocked(): JSONArray =
        LearningGridProjection.project(regionsJsonLocked(), epoch)

    private fun integrityJsonLocked(): JSONObject {
        val regionJson = regionsJsonLocked()
        val cellJson = LearningGridProjection.project(regionJson, epoch)
        return LearningGridProjection.integrity(
            regions = regionJson,
            cells = cellJson,
            comparisons = JSONArray(comparisons.map { it.toJson() }),
            epoch = epoch,
            mapHash = mapHash,
        )
    }

    private fun statusLocked(): JSONObject {
        val live = JSONObject(lastStatus.toString())
        val root = JSONObject(live.toString())
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
        return root
    }

    /** Estado constante em tamanho usado no caminho de cada quadro. */
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
        // A resposta compacta alimenta a telemetria ao vivo: não pode perder a
        // comparação já consolidada no mesmo ciclo físico.
        listOf(
            "comparison",
            "comparison_evidence",
            "direction",
            "error_pct",
            "comparison_stage",
            "reference_surface",
            "reference_diagnostic",
            "actionable",
            "suggested_delta_k_percent",
            "suggested_delta_k",
        ).forEach { key ->
            if (lastStatus.has(key)) compact.put(key, lastStatus.get(key))
        }
        return compact
    }

    private fun memoryLocked(): JSONObject {
        val latestPetrol = regions
            .filter { it.fuel == Mp48Fuel.PETROL }
            .maxByOrNull { it.updatedAt }
        val latestCng = regions
            .filter { it.fuel == Mp48Fuel.CNG && it.epoch == epoch }
            .maxByOrNull { it.updatedAt }
        val latestComparison = comparisons.lastOrNull { it.epoch == epoch }
            ?: comparisons.lastOrNull()
        val progress = when {
            latestComparison != null -> comparisonEvidence(latestComparison.rpm, latestComparison.mapBar).toJson()
                .put("kind", "COMPARISON")
                .put("rpm", latestComparison.rpm)
                .put("map_bar", latestComparison.mapBar)
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
        val stillInside = current.fuel == telemetry.fuel &&
            current.epoch == regionEpoch(telemetry.fuel) &&
            visitEquivalent(
                current.rpmAnchor,
                current.mapAnchor,
                telemetry.rpm.toDouble(),
                telemetry.mapBar,
            )
        if (stillInside) {
            observedOutsideFrames = 0
        } else {
            observedOutsideFrames += 1
            if (observedOutsideFrames >= LearningToleranceSettings.current.physicalExitFrames) {
                activeVisit = null
                observedOutsideFrames = 0
            }
        }
    }

    private fun resolveVisit(sample: MotorSample): ActiveVisit {
        val current = activeVisit
        val same = current != null &&
            current.fuel == sample.fuel &&
            current.epoch == regionEpoch(sample.fuel) &&
            visitEquivalent(
                current.rpmAnchor,
                current.mapAnchor,
                sample.rpm,
                sample.mapBar,
            )
        if (same) {
            current!!.lastSeenAtMs = sample.endedAtElapsedMs
            current.samples += 1
            current.rpmAnchor += (sample.rpm - current.rpmAnchor) / current.samples
            current.petrolAnchor += (sample.petrolMs - current.petrolAnchor) / current.samples
            current.mapAnchor += (sample.mapBar - current.mapAnchor) / current.samples
            return current
        }
        return ActiveVisit(
            id = UUID.randomUUID().toString(),
            fuel = sample.fuel,
            epoch = regionEpoch(sample.fuel),
            rpmAnchor = sample.rpm,
            petrolAnchor = sample.petrolMs,
            mapAnchor = sample.mapBar,
            startedAtMs = sample.startedAtElapsedMs,
            lastSeenAtMs = sample.endedAtElapsedMs,
            samples = 1,
        ).also { activeVisit = it }
    }

    private fun ensureCurrentSession(): PhysicalLearningSession {
        currentSession?.let { return it }
        return PhysicalLearningSession(
            id = sessionId,
            startedAt = System.currentTimeMillis(),
        ).also {
            currentSession = it
            sessions.addLast(it)
            while (sessions.size > MAX_SESSIONS) sessions.removeFirst()
        }
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
        val target = nearestRegion(sample.fuel, sample.rpm, sample.mapBar, desiredEpoch)
            ?.takeIf { regionEquivalent(it, sample.rpm, sample.mapBar) }
            ?: LearningRegion.fromSample(sample, desiredEpoch).also { 
                regions += it 
                if (regions.size > MAX_REGIONS) regions.removeAt(0)
            }
        target.update(sample, visit.id, sessionId)
        return target
    }

    private fun petrolStatus(region: LearningRegion, sample: MotorSample, visit: ActiveVisit): JSONObject {
        val stage = region.stage()
        return JSONObject()
            .put("state", "PETROL_REFERENCE_$stage")
            .put(
                "reason",
                when (stage) {
                    "OBSERVED" -> "Referência em gasolina observada e armazenada"
                    "PROVISIONAL" -> "Referência provisória confirmada por variância"
                    "ACCEPTED" -> "Referência aceita por densidade"
                    else -> "Referência confirmada por densidade"
                },
            )
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
            return JSONObject()
                .put("state", "NO_PETROL_REFERENCE")
                .put(
                    "reason",
                    diagnostic.optString(
                        "message",
                        "Falta referência confiável de gasolina nesta condição de RPM, MAP e temperatura",
                    ),
                )
                .put("reason_code", diagnostic.optString("reason_code", "NO_PETROL_REFERENCE"))
                .put("reference_diagnostic", diagnostic)
                .put("learning", true)
                .put("registered_now", true)
                .put("cng_region", region.toJson())
        }

        val candidate = compare(
            petrolTargetMs = reference.petrolTargetMs,
            cngSample = sample,
            visitId = visit.id,
            referenceRegionId = reference.regionIds.joinToString(","),
            origin = "CONTINUOUS_REFERENCE_SURFACE",
            pairQuality = sqrt(reference.quality * sample.quality).coerceIn(0.0, 1.0),
        )
        val stored = addComparisonOnce(candidate)
        val evidence = comparisonEvidence(sample.rpm, sample.mapBar)
        return comparisonStatus(
            comparison = stored.comparison,
            reason = if (stored.created) {
                "Petrol Inj no GNV comparado à referência local da gasolina"
            } else {
                "Comparação contínua consolidada; esta visita continua contabilizada uma única vez"
            },
            evidence = evidence,
            registeredNow = stored.created,
        ).put("reference_surface", reference.toJson())
            .put("reference_diagnostic", JSONObject(lastReferenceDiagnostic.toString()))
            .put("reference_stage", reference.stage)
    }

    private fun compare(
        petrolTargetMs: Double,
        cngSample: MotorSample,
        visitId: String,
        referenceRegionId: String,
        origin: String,
        pairQuality: Double,
    ): FuelComparison {
        val difference = cngSample.petrolMs - petrolTargetMs
        val errorPct = if (petrolTargetMs <= 0.05) 0.0 else difference / petrolTargetMs * 100.0
        val direction = when {
            abs(difference) <= LearningToleranceSettings.current.equivalenceDeadbandMs ||
                abs(errorPct) <= LearningToleranceSettings.current.equivalenceDeadbandPercent -> "EQUIVALENT"
            difference > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }
        val cngCell = LearningGridProjection.cellFor(cngSample.rpm, cngSample.petrolMs)
        val referenceCell = LearningGridProjection.cellFor(cngSample.rpm, petrolTargetMs)
        return FuelComparison(
            id = UUID.randomUUID().toString(),
            dedupeKey = "$epoch:$origin:$visitId:$referenceRegionId",
            visitId = visitId,
            referenceRegionId = referenceRegionId,
            sessionId = sessionId,
            capturedAt = System.currentTimeMillis(),
            origin = origin,
            rpm = cngSample.rpm,
            mapBar = cngSample.mapBar,
            waterC = cngSample.waterC,
            gasC = cngSample.gasC,
            pressureDiffBar = cngSample.pressureDiffBar,
            cngCellRow = cngCell.getInt("row"),
            cngCellColumn = cngCell.getInt("column"),
            referenceCellRow = referenceCell.getInt("row"),
            referenceCellColumn = referenceCell.getInt("column"),
            petrolTargetMs = petrolTargetMs,
            petrolOnCngMs = cngSample.petrolMs,
            differenceMs = difference,
            errorPct = errorPct,
            direction = direction,
            quality = pairQuality.coerceIn(0.0, 1.0),
            epoch = epoch,
            mapHash = mapHash,
        )
    }

    private fun comparisonStatus(
        comparison: FuelComparison,
        reason: String,
        evidence: ComparisonEvidence,
        registeredNow: Boolean,
    ): JSONObject {
        // A direção que libera uma proposta precisa ser a mesma direção usada
        // para calcular a magnitude consolidada; nunca a última leitura isolada.
        val actionable = evidence.actionable(evidence.dominantDirection)
        return JSONObject()
        .put(
            "state",
            "CONTINUOUS_FUEL_EQUIVALENCE",
        )
        .put("reason", reason)
        .put("learning", true)
        .put("registered_now", registeredNow)
        .put("comparison", comparison.toJson())
        .put("petrol_target_ms", comparison.petrolTargetMs)
        .put("petrol_on_cng_ms", comparison.petrolOnCngMs)
        .put("error_pct", comparison.errorPct)
        .put("direction", comparison.direction)
        .put("quality", comparison.quality)
        .put("comparison_evidence", evidence.toJson())
        .put("comparison_stage", evidence.stage)
        .put("evidence_direction", evidence.dominantDirection)
        .put("actionable", actionable)
        .put("suggested_delta_k_percent", if (actionable) {
            (evidence.medianErrorRatio * 35.0 * evidence.confidence()).coerceIn(-5.0, 5.0)
        } else JSONObject.NULL)
        .put("suggested_delta_k", when {
            !actionable -> JSONObject.NULL
            evidence.dominantDirection == "INCREASE_CNG_DELIVERY" -> 1
            evidence.dominantDirection == "DECREASE_CNG_DELIVERY" -> -1
            else -> JSONObject.NULL
        })
        .put("automatic_write", false)
        .put("human_confirmation_required", true)
    }

    private fun addComparisonOnce(candidate: FuelComparison): StoredComparison {
        val existing = comparisons.firstOrNull { it.dedupeKey == candidate.dedupeKey }
        if (existing != null) {
            val consolidated = existing.consolidate(candidate)
            comparisons.remove(existing)
            comparisons.addLast(consolidated)
            return StoredComparison(consolidated, false)
        }
        comparisons.addLast(candidate)
        while (comparisons.size > MAX_COMPARISONS) comparisons.removeFirst()
        sessionComparisons += candidate.id
        return StoredComparison(candidate, true)
    }

    private fun comparisonEvidence(rpm: Double, mapBar: Double): ComparisonEvidence {
        val related = comparisons.filter {
            it.epoch == epoch && visitEquivalent(it.rpm, it.mapBar, rpm, mapBar)
        }
        val directions = related.groupingBy { it.direction }.eachCount()
        val dominant = directions.maxByOrNull { it.value }?.key ?: "EQUIVALENT"
        val consensus = if (related.isEmpty()) 0.0 else (directions[dominant] ?: 0) / related.size.toDouble()
        val center = related.map { it.differenceMs }.median()
        val mad = related.map { abs(it.differenceMs - center) }.median()
        val gasTempSpan = related.valueSpan { it.gasC }
        val pressureSpan = related.valueSpan { it.pressureDiffBar }
        val effectiveSamples = ContinuousLearningMath.effectiveSampleSize(related.map { it.quality })
        val rawStage = confidenceStage(effectiveSamples, mad * mad)
        val reliable = consensus >= LearningToleranceSettings.current.directionConsensusMinimum &&
            mad <= LearningToleranceSettings.current.comparisonMaximumMadMs &&
            gasTempSpan <= LearningToleranceSettings.current.comparisonMaximumGasTempSpanC &&
            pressureSpan <= LearningToleranceSettings.current.comparisonMaximumPressureSpanBar
        val stage = if (rawStage in setOf("ACCEPTED", "CONFIRMED") && !reliable) "PROVISIONAL" else rawStage
        return ComparisonEvidence(
            stage,
            dominant,
            consensus,
            center,
            mad,
            gasTempSpan,
            pressureSpan,
            effectiveSamples = effectiveSamples,
            medianErrorRatio = if (center == 0.0) 0.0 else related.map { it.errorPct / 100.0 }.median(),
        )
    }

    /** Evidência absorvida na superfície: uma comparação influencia quatro pontos K. */
    private fun comparisonEvidenceForCell(row: Int, column: Int): ComparisonEvidence {
        val related = comparisons.filter { it.epoch == epoch && it.affects(row, column) }
        if (related.isEmpty()) return ComparisonEvidence.empty()
        val weighted = related.mapNotNull { comparison ->
            val cellWeight = comparison.weightAt(row, column)
            if (cellWeight <= 0.0) null else comparison to (cellWeight * comparison.quality)
        }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return ComparisonEvidence.empty()
        val mean = weighted.sumOf { it.first.errorPct / 100.0 * it.second } / total
        val differenceCenterMs = weighted.map { it.first.differenceMs to it.second }.weightedMedian()
        val madMs = weighted.map { abs(it.first.differenceMs - differenceCenterMs) to it.second }.weightedMedian()
        val dominant = errorRatioDirection(mean)
        val consensus = weighted.filter { errorRatioDirection(it.first.errorPct / 100.0) == dominant }
            .sumOf { it.second } / total
        val effectiveSamples = ContinuousLearningMath.effectiveSampleSize(weighted.map { it.second })
        val stage = confidenceStage(effectiveSamples, madMs * madMs)
        return ComparisonEvidence(
            stage = stage,
            dominantDirection = dominant,
            directionConsensus = consensus,
            medianDifferenceMs = differenceCenterMs,
            madMs = madMs,
            gasTemperatureSpanC = related.valueSpan { it.gasC },
            pressureSpanBar = related.valueSpan { it.pressureDiffBar },
            effectiveSamples = effectiveSamples,
            medianErrorRatio = mean,
        )
    }

    private fun nearestRegion(
        fuel: Mp48Fuel,
        rpm: Double,
        mapBar: Double,
        regionEpoch: Int,
    ): LearningRegion? = regions.asSequence()
        .filter { it.fuel == fuel && it.epoch == regionEpoch }
        .minByOrNull { normalizedDistance(it.rpmMean, it.mapMean, rpm, mapBar) }

    private fun normalizedDistance(rpmA: Double, mapA: Double, rpmB: Double, mapB: Double): Double {
        val tolerance = LearningToleranceSettings.current
        val rpmScale = max(tolerance.historicalRpmMinimum, max(abs(rpmA), abs(rpmB)) * tolerance.historicalRpmPercent / 100.0)
        val dr = abs(rpmA - rpmB) / rpmScale
        val dm = abs(mapA - mapB) / tolerance.historicalMapBar
        return sqrt(dr * dr + dm * dm)
    }

    private fun regionEquivalent(region: LearningRegion, rpm: Double, mapBar: Double): Boolean {
        val tolerance = LearningToleranceSettings.current
        val rpmLimit = max(tolerance.historicalRpmMinimum, max(abs(region.rpmMean), abs(rpm)) * tolerance.historicalRpmPercent / 100.0)
        return abs(region.rpmMean - rpm) <= rpmLimit && abs(region.mapMean - mapBar) <= tolerance.historicalMapBar
    }

    private fun petrolReferenceSurface(sample: MotorSample): PetrolReferenceEstimate? {
        referenceAttempts += 1L
        val result = PetrolReferenceSelector.estimate(
            regions = regions.asSequence()
                .filter { it.fuel == Mp48Fuel.PETROL && it.epoch == 0 }
                .map { region ->
                    PetrolReferenceSelector.Region(
                        id = region.id,
                        rpm = region.rpmMean,
                        mapBar = region.mapMean,
                        waterC = region.waterMean,
                        petrolMs = region.petrolMean,
                        confidence = region.confidence(),
                        sampleCount = region.sampleCount,
                    )
                }
                .toList(),
            request = PetrolReferenceSelector.Request(
                rpm = sample.rpm,
                mapBar = sample.mapBar,
                waterC = sample.waterC,
            ),
            policy = LearningToleranceSettings.current,
        )
        lastReferenceDiagnostic = result.toJson()
        if (!result.available) {
            referenceRejectCounts[result.reasonCode] =
                (referenceRejectCounts[result.reasonCode] ?: 0L) + 1L
            return null
        }
        referenceAccepted += 1L
        return PetrolReferenceEstimate(
            petrolTargetMs = requireNotNull(result.petrolTargetMs),
            spreadMs = result.spreadMs ?: 0.0,
            quality = result.quality,
            regionIds = result.regionIds,
            stage = result.stage,
            extrapolated = result.extrapolated,
        )
    }

    private fun visitEquivalent(rpmA: Double, mapA: Double, rpmB: Double, mapB: Double): Boolean {
        val tolerance = LearningToleranceSettings.current
        val rpmLimit = max(tolerance.historicalRpmMinimum, max(abs(rpmA), abs(rpmB)) * tolerance.historicalRpmPercent / 100.0)
        return abs(rpmA - rpmB) <= rpmLimit && abs(mapA - mapB) <= tolerance.historicalMapBar
    }

    private fun regionEpoch(fuel: Mp48Fuel): Int = if (fuel == Mp48Fuel.PETROL) 0 else epoch

    private fun summaryLocked(): JSONObject {
        val petrolRegions = regions.filter { it.fuel == Mp48Fuel.PETROL }
        val cngCurrent = regions.filter { it.fuel == Mp48Fuel.CNG && it.epoch == epoch }
        val cngAll = regions.filter { it.fuel == Mp48Fuel.CNG }
        val comparisonsCurrent = comparisons.filter { it.epoch == epoch }
        return JSONObject()
            .put("session_id", sessionId)
            .put("epoch", epoch)
            .put("map_hash", mapHash)
            .put("petrol_regions", petrolRegions.size)
            .put("cng_regions", cngCurrent.size)
            .put("cng_regions_all_epochs", cngAll.size)
            .put("comparisons", comparisonsCurrent.size)
            .put("comparisons_all_epochs", comparisons.size)
            .put("direct_comparisons", 0)
            .put("confirmed_petrol_regions", petrolRegions.count { it.stage() == "CONFIRMED" })
            .put("reference_attempts", referenceAttempts)
            .put("reference_accepted", referenceAccepted)
            .put("reference_rejected", (referenceAttempts - referenceAccepted).coerceAtLeast(0L))
            .put("reference_rejection_reasons", JSONObject(referenceRejectCounts as Map<*, *>))
            .put("last_reference_diagnostic", JSONObject(lastReferenceDiagnostic.toString()))
    }

    private fun sessionSummaryLocked(): JSONObject {
        val s = currentSession ?: sessions.lastOrNull()
        return JSONObject()
            .put("session_id", s?.id ?: sessionId)
            .put("epoch", epoch)
            .put("started_at", s?.startedAt ?: 0L)
            .put("ended_at", s?.endedAt ?: 0L)
            .put("duration_ms", s?.durationMs() ?: 0L)
            .put("samples", s?.sampleCount ?: 0)
            .put("fuels", JSONArray(s?.fuels?.toList() ?: emptyList<String>()))
            .put("end_reason", s?.endReason ?: "ACTIVE")
            .put("petrol_regions", sessionPetrolRegions.size)
            .put("cng_regions", sessionCngRegions.size)
            .put("comparisons", sessionComparisons.size)
            .put("direct_comparisons", 0)
    }

    private fun rebuildVisualStatusFromMemory() {
        if (lastStatus.length() > 0) return
        val latestComparison = comparisons.lastOrNull()
        if (latestComparison != null) {
            val evidence = comparisonEvidence(latestComparison.rpm, latestComparison.mapBar)
            lastStatus = comparisonStatus(
                comparison = latestComparison,
                reason = "Última comparação preservada na memória",
                evidence = evidence,
                registeredNow = false,
            ).put("restored_from_memory", true)
            return
        }
        val latestReference = regions
            .filter { it.fuel == Mp48Fuel.PETROL }
            .maxByOrNull { it.updatedAt }
        if (latestReference != null) {
            lastStatus = JSONObject()
                .put("state", "PETROL_REFERENCE_${latestReference.stage()}")
                .put("reason", "Última referência em gasolina preservada na memória")
                .put("reference", latestReference.toJson())
                .put("reference_confidence", latestReference.confidence())
                .put("learning", false)
                .put("restored_from_memory", true)
            return
        }
        lastStatus = JSONObject()
            .put("state", "OBSERVING_ENGINE")
            .put("reason", "Memória ainda vazia")
            .put("learning", false)
    }

    private fun load() = synchronized(lock) {
        try {
            val backup = File(stateFile.parentFile, stateFile.name + ".bak")
            val primary = readValidState(stateFile)
            val recovered = primary == null
            val root = primary ?: readValidState(backup) ?: return@synchronized
            if (recovered && backup.isFile) {
                backup.copyTo(stateFile, overwrite = true)
                log.add("WARN", "LEARNING-INTEGRITY", "Memória principal recuperada do último backup válido")
            }
            val format = root.optString("format")
            if (format != FORMAT) {
                log.add("INFO", "LEARNING-V5", "Memória de outro formato ignorada; iniciando uma memória V5 limpa")
                return@synchronized
            }
            epoch = root.optInt("epoch", 1).coerceAtLeast(1)
            mapHash = root.optString("mapHash", "")
            lastCalibrationRevalidation = root.optJSONObject("revalidation")?.let { JSONObject(it.toString()) } ?: JSONObject()
            val savedRegions = root.optJSONArray("regions") ?: JSONArray()
            repeat(savedRegions.length()) { index ->
                savedRegions.optJSONObject(index)?.let { regions += LearningRegion.fromJson(it) }
            }
            val savedComparisons = root.optJSONArray("comparisons") ?: JSONArray()
            repeat(savedComparisons.length()) { index ->
                savedComparisons.optJSONObject(index)?.let { comparisons += FuelComparison.fromJson(it) }
            }
            val savedSessions = root.optJSONArray("sessions") ?: JSONArray()
            repeat(savedSessions.length()) { index ->
                savedSessions.optJSONObject(index)?.let { sessions += PhysicalLearningSession.fromJson(it) }
            }
            sessions.filter { it.endedAt == 0L }.forEach {
                it.endedAt = max(it.updatedAt, it.startedAt)
                it.endReason = "PROCESS_INTERRUPTED"
            }
        } catch (error: Exception) {
            log.add("WARN", "LEARNING-NATIVE", "Memória não carregada: ${error.message}")
        }
    }

    private fun persist() {
        // Apenas marca o estado como sujo no caminho da telemetria. A cópia
        // consistente e toda serialização acontecem no executor dedicado.
        persistDirty.set(true)
        schedulePersistDrain()
    }

    private fun schedulePersistDrain() {
        if (!persistDrainScheduled.compareAndSet(false, true)) return
        lastPersistFuture = persistExecutor.submit {
            try {
                while (persistDirty.getAndSet(false)) {
                    val newest = synchronized(lock) { persistedStateLocked() }
                    writePersistedState(newest)
                }
            } finally {
                persistDrainScheduled.set(false)
                if (persistDirty.get()) schedulePersistDrain()
            }
        }
    }

    private fun persistedStateLocked(): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("savedAt", System.currentTimeMillis())
        .put("epoch", epoch)
        .put("mapHash", mapHash)
        .put("revalidation", JSONObject(lastCalibrationRevalidation.toString()))
        .put("tolerancePolicy", LearningToleranceSettings.current.toJson())
        .put("regions", regionsJsonLocked())
        .put("comparisons", JSONArray(comparisons.map { it.toJson() }))
        .put("sessions", JSONArray(sessions.map { it.toJson() }))

    private fun writePersistedState(root: JSONObject) {
        try {
            root.put("stateDigest", canonicalDigest(root))
            stateFile.parentFile?.mkdirs()
            val temp = File(stateFile.parentFile, stateFile.name + ".tmp")
            val backup = File(stateFile.parentFile, stateFile.name + ".bak")
            FileOutputStream(temp).use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            if (stateFile.isFile) stateFile.copyTo(backup, overwrite = true)
            try {
                Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (readValidState(stateFile) == null && backup.isFile) backup.copyTo(stateFile, overwrite = true)
        } catch (error: Exception) {
            log.add("WARN", "LEARNING-NATIVE", "Erro ao salvar memória: ${error.message}")
        }
    }

    private fun readValidState(file: File): JSONObject? {
        if (!file.isFile) return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val expected = root.optString("stateDigest")
            if (expected.isNotBlank()) {
                if (canonicalDigest(root) != expected) {
                    log.add("ERROR", "LEARNING-INTEGRITY", "Divergência detectada em ${file.name}")
                    return null
                }
            }
            root
        } catch (error: Exception) {
            log.add("WARN", "LEARNING-INTEGRITY", "Arquivo inválido ${file.name}: ${error.message}")
            null
        }
    }

    private fun canonicalDigest(json: JSONObject): String {
        val copy = JSONObject(json.toString()).apply { remove("stateDigest") }
        val sortedJson = JSONObject()
        for (key in copy.keys().asSequence().sorted()) {
            sortedJson.put(key, copy.get(key))
        }
        return sha256(sortedJson.toString())
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private data class PhysicalLearningSession(
    val id: String,
    val startedAt: Long,
    var endedAt: Long = 0L,
    var updatedAt: Long = startedAt,
    var sampleCount: Int = 0,
    val fuels: MutableSet<String> = linkedSetOf(),
    var endReason: String = "ACTIVE",
) {
    fun durationMs(now: Long = System.currentTimeMillis()): Long =
        ((if (endedAt > 0L) endedAt else now) - startedAt).coerceAtLeast(0L)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("started_at", startedAt)
        .put("ended_at", endedAt)
        .put("updated_at", updatedAt)
        .put("duration_ms", durationMs())
        .put("samples", sampleCount)
        .put("fuels", JSONArray(fuels.toList()))
        .put("end_reason", endReason)

    companion object {
        fun fromJson(raw: JSONObject): PhysicalLearningSession {
            val fuels = linkedSetOf<String>()
            raw.optJSONArray("fuels")?.let { array ->
                repeat(array.length()) { fuels += array.optString(it) }
            }
            return PhysicalLearningSession(
                id = raw.optString("id", UUID.randomUUID().toString()),
                startedAt = raw.optLong("started_at", 0L),
                endedAt = raw.optLong("ended_at", 0L),
                updatedAt = raw.optLong("updated_at", 0L),
                sampleCount = raw.optInt("samples", 0),
                fuels = fuels,
                endReason = raw.optString("end_reason", "RESTORED"),
            )
        }
    }
}

private data class ActiveVisit(
    val id: String,
    val fuel: Mp48Fuel,
    val epoch: Int,
    var rpmAnchor: Double,
    var petrolAnchor: Double,
    var mapAnchor: Double,
    val startedAtMs: Long,
    var lastSeenAtMs: Long,
    var samples: Int,
)

private data class LearningRegion(
    val id: String,
    val fuel: Mp48Fuel,
    val epoch: Int,
    var rpmMean: Double,
    var mapMean: Double,
    var petrolMean: Double,
    var petrolSquaredMean: Double,
    var pressureMean: Double,
    var waterMean: Double,
    var gasMean: Double,
    var qualityMean: Double,
    var weight: Double = 0.0,
    var sampleCount: Int = 0,
    val visits: MutableSet<String> = linkedSetOf(),
    val sessions: MutableSet<String> = linkedSetOf(),
    var updatedAt: Long = 0L,
) {
    fun update(sample: MotorSample, visitId: String, sessionId: String) {
        val durationWeight = ContinuousLearningMath.dwellWeight(
            sample.endedAtElapsedMs - sample.startedAtElapsedMs,
        )
        val sampleWeight = max(0.10, sample.quality) * (0.25 + 0.75 * durationWeight)
        val total = weight + sampleWeight
        fun blend(current: Double, incoming: Double): Double =
            current + (incoming - current) * sampleWeight / max(sampleWeight, total)
        rpmMean = blend(rpmMean, sample.rpm)
        mapMean = blend(mapMean, sample.mapBar)
        petrolMean = blend(petrolMean, sample.petrolMs)
        petrolSquaredMean = blend(petrolSquaredMean, sample.petrolMs * sample.petrolMs)
        pressureMean = blend(pressureMean, sample.pressureDiffBar)
        waterMean = blend(waterMean, sample.waterC)
        gasMean = blend(gasMean, sample.gasC)
        qualityMean = blend(qualityMean, sample.quality)
        weight = total
        sampleCount += 1
        visits += visitId
        sessions += sessionId
        updatedAt = System.currentTimeMillis()
    }

    fun merge(other: LearningRegion) {
        val incomingWeight = max(0.10, other.weight)
        val total = weight + incomingWeight
        fun blend(current: Double, incoming: Double): Double =
            current + (incoming - current) * incomingWeight / max(incomingWeight, total)
        rpmMean = blend(rpmMean, other.rpmMean)
        mapMean = blend(mapMean, other.mapMean)
        petrolMean = blend(petrolMean, other.petrolMean)
        petrolSquaredMean = blend(petrolSquaredMean, other.petrolSquaredMean)
        pressureMean = blend(pressureMean, other.pressureMean)
        waterMean = blend(waterMean, other.waterMean)
        gasMean = blend(gasMean, other.gasMean)
        qualityMean = blend(qualityMean, other.qualityMean)
        weight = total
        sampleCount += other.sampleCount
        visits += other.visits
        sessions += other.sessions
        updatedAt = max(updatedAt, other.updatedAt)
    }

    fun namespace(source: String): LearningRegion = copy(
        id = "$source:$id",
        visits = visits.mapTo(linkedSetOf()) { "$source:$it" },
        sessions = sessions.mapTo(linkedSetOf()) { "$source:$it" },
    )

    fun stage(): String = confidenceStage(sampleCount.toDouble(), max(0.0, petrolSquaredMean - petrolMean * petrolMean))

    fun confidence(): Double {
        val tolerance = LearningToleranceSettings.current
        val samplePart = (sampleCount / tolerance.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0)
        val variancePart = (1.0 - max(0.0, petrolSquaredMean - petrolMean * petrolMean) / (tolerance.referenceMaximumSpreadMs * tolerance.referenceMaximumSpreadMs)).coerceIn(0.1, 1.0)
        return listOf(
            samplePart.coerceAtLeast(0.05),
            variancePart,
            qualityMean.coerceIn(0.10, 1.0),
        ).geometricMean()
    }

    fun progressJson(): JSONObject = LearningToleranceSettings.current.let { tolerance -> JSONObject()
        .put("stage", stage())
        .put("visits", visits.size)
        .put("sessions", sessions.size)
        .put("confidence", confidence())
        .put("confidence_samples", (sampleCount / tolerance.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0))
        .put("next_visit_target", tolerance.confidenceSampleTarget)
        .put("rpm", rpmMean)
        .put("map_bar", mapMean)
    }

    fun toJson(): JSONObject = LearningToleranceSettings.current.let { tolerance -> JSONObject()
        .put("id", id)
        .put("fuel", fuel.wireName)
        .put("epoch", epoch)
        .put("rpm", rpmMean)
        .put("map_bar", mapMean)
        .put("petrol_ms", petrolMean)
        .put("petrol_squared_mean", petrolSquaredMean)
        .put("petrol_spread_ms", sqrt(max(0.0, petrolSquaredMean - petrolMean * petrolMean)))
        .put("pressure_diff_bar", pressureMean)
        .put("water_c", waterMean)
        .put("gas_c", gasMean)
        .put("quality", qualityMean)
        .put("weight", weight)
        .put("samples", sampleCount)
        .put("visits", JSONArray(visits.toList()))
        .put("sessions", JSONArray(sessions.toList()))
        .put("visit_count", visits.size)
        .put("session_count", sessions.size)
        .put("stage", stage())
        .put("confidence", confidence())
        .put("confidence_samples", (sampleCount / tolerance.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0))
        .put("updated_at", updatedAt)
    }

    companion object {
        fun fromSample(sample: MotorSample, epoch: Int) = LearningRegion(
            id = UUID.randomUUID().toString(),
            fuel = sample.fuel,
            epoch = epoch,
            rpmMean = sample.rpm,
            mapMean = sample.mapBar,
            petrolMean = sample.petrolMs,
            petrolSquaredMean = sample.petrolMs * sample.petrolMs,
            pressureMean = sample.pressureDiffBar,
            waterMean = sample.waterC,
            gasMean = sample.gasC,
            qualityMean = sample.quality,
        )

        fun fromJson(raw: JSONObject): LearningRegion {
            val visits = linkedSetOf<String>()
            val sessions = linkedSetOf<String>()
            raw.optJSONArray("visits")?.let { array ->
                repeat(array.length()) { visits += array.optString(it) }
            }
            raw.optJSONArray("sessions")?.let { array ->
                repeat(array.length()) { sessions += array.optString(it) }
            }
            val fuel = if (raw.optString("fuel") == Mp48Fuel.CNG.wireName) {
                Mp48Fuel.CNG
            } else {
                Mp48Fuel.PETROL
            }
            val petrolMean = raw.optDouble("petrol_ms", 0.0)
            val petrolSquaredMean = raw.optDouble("petrol_squared_mean", petrolMean * petrolMean)
            return LearningRegion(
                id = raw.optString("id", UUID.randomUUID().toString()),
                fuel = fuel,
                epoch = if (fuel == Mp48Fuel.PETROL) 0 else raw.optInt("epoch", 1).coerceAtLeast(1),
                rpmMean = raw.optDouble("rpm", 0.0),
                mapMean = raw.optDouble("map_bar", 0.0),
                petrolMean = petrolMean,
                petrolSquaredMean = petrolSquaredMean,
                pressureMean = raw.optDouble("pressure_diff_bar", 0.0),
                waterMean = raw.optDouble("water_c", 0.0),
                gasMean = raw.optDouble("gas_c", 0.0),
                qualityMean = raw.optDouble("quality", 0.5),
                weight = raw.optDouble("weight", 0.0),
                sampleCount = raw.optInt("samples", 0),
                visits = visits,
                sessions = sessions,
                updatedAt = raw.optLong("updated_at", 0L),
            )
        }
    }
}


private data class PetrolReferenceEstimate(
    val petrolTargetMs: Double,
    val spreadMs: Double,
    val quality: Double,
    val regionIds: List<String>,
    val stage: String,
    val extrapolated: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("petrol_target_ms", petrolTargetMs)
        .put("spread_ms", spreadMs)
        .put("quality", quality)
        .put("region_ids", JSONArray(regionIds))
        .put("region_count", regionIds.size)
        .put("stage", stage)
        .put("method", "RPM_MAP_WATER_WEIGHTED_SURFACE")
        .put("extrapolated", extrapolated)
        .put("extrapolation_weight", if (extrapolated) 0.35 else 1.0)
}

private data class FuelComparison(
    val id: String,
    val dedupeKey: String,
    val visitId: String,
    val referenceRegionId: String,
    val sessionId: String,
    val capturedAt: Long,
    val origin: String,
    val rpm: Double,
    val mapBar: Double,
    val waterC: Double,
    val gasC: Double,
    val pressureDiffBar: Double,
    val cngCellRow: Int,
    val cngCellColumn: Int,
    val referenceCellRow: Int,
    val referenceCellColumn: Int,
    val petrolTargetMs: Double,
    val petrolOnCngMs: Double,
    val differenceMs: Double,
    val errorPct: Double,
    val direction: String,
    val quality: Double,
    val epoch: Int,
    val mapHash: String,
    val observationCount: Int = 1,
) {
    /**
     * Mantém uma única evidência independente por visita, mas deixa essa evidência amadurecer
     * conforme novas janelas válidas chegam. A qualidade pondera as janelas sem aumentar o
     * tamanho efetivo da amostra usado pela confiança entre visitas.
     */
    fun consolidate(newer: FuelComparison): FuelComparison {
        require(dedupeKey == newer.dedupeKey)
        val oldCount = observationCount.coerceAtLeast(1)
        val newCount = newer.observationCount.coerceAtLeast(1)
        val oldWeight = (quality * oldCount).takeIf { it > 0.0 } ?: oldCount.toDouble()
        val newWeight = (newer.quality * newCount).takeIf { it > 0.0 } ?: newCount.toDouble()
        val totalWeight = oldWeight + newWeight
        fun blended(old: Double, new: Double): Double = (old * oldWeight + new * newWeight) / totalWeight

        val targetMs = blended(petrolTargetMs, newer.petrolTargetMs)
        val observedMs = blended(petrolOnCngMs, newer.petrolOnCngMs)
        val differenceMs = observedMs - targetMs
        val errorPct = if (targetMs <= 0.05) 0.0 else differenceMs / targetMs * 100.0
        val direction = when {
            abs(differenceMs) <= LearningToleranceSettings.current.equivalenceDeadbandMs ||
                abs(errorPct) <= LearningToleranceSettings.current.equivalenceDeadbandPercent -> "EQUIVALENT"
            differenceMs > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }
        val consolidatedRpm = blended(rpm, newer.rpm)
        val consolidatedMapBar = blended(mapBar, newer.mapBar)
        val cngCell = LearningGridProjection.cellFor(consolidatedRpm, observedMs)
        val referenceCell = LearningGridProjection.cellFor(consolidatedRpm, targetMs)
        return copy(
            capturedAt = max(capturedAt, newer.capturedAt),
            rpm = consolidatedRpm,
            mapBar = consolidatedMapBar,
            waterC = blended(waterC, newer.waterC),
            gasC = blended(gasC, newer.gasC),
            pressureDiffBar = blended(pressureDiffBar, newer.pressureDiffBar),
            cngCellRow = cngCell.getInt("row"),
            cngCellColumn = cngCell.getInt("column"),
            referenceCellRow = referenceCell.getInt("row"),
            referenceCellColumn = referenceCell.getInt("column"),
            petrolTargetMs = targetMs,
            petrolOnCngMs = observedMs,
            differenceMs = differenceMs,
            errorPct = errorPct,
            direction = direction,
            quality = ((quality * oldCount + newer.quality * newCount) / (oldCount + newCount))
                .coerceIn(0.0, 1.0),
            observationCount = oldCount + newCount,
        )
    }

    fun weightAt(row: Int, column: Int): Double =
        ContinuousLearningMath.bilinearWeights(rpm, petrolOnCngMs)
            .firstOrNull { it.row == row && it.column == column }
            ?.weight ?: 0.0

    fun affects(row: Int, column: Int): Boolean = weightAt(row, column) > 0.0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("dedupe_key", dedupeKey)
        .put("visit_id", visitId)
        .put("reference_region_id", referenceRegionId)
        .put("session_id", sessionId)
        .put("captured_at", capturedAt)
        .put("origin", origin)
        .put("rpm", rpm)
        .put("map_bar", mapBar)
        .put("water_c", waterC)
        .put("gas_c", gasC)
        .put("pressure_diff_bar", pressureDiffBar)
        .put("cng_cell_row", cngCellRow)
        .put("cng_cell_column", cngCellColumn)
        .put("reference_cell_row", referenceCellRow)
        .put("reference_cell_column", referenceCellColumn)
        .put("continuous_cell_weights", JSONArray(
            ContinuousLearningMath.bilinearWeights(rpm, petrolOnCngMs).map {
                JSONObject()
                    .put("row", it.row)
                    .put("column", it.column)
                    .put("weight", it.weight)
            },
        ))
        .put("cross_cell_equivalence", cngCellRow != referenceCellRow || cngCellColumn != referenceCellColumn)
        .put("petrol_target_ms", petrolTargetMs)
        .put("petrol_on_cng_ms", petrolOnCngMs)
        .put("difference_ms", differenceMs)
        .put("error_pct", errorPct)
        .put("direction", direction)
        .put("quality", quality)
        .put("epoch", epoch)
        .put("map_hash", mapHash)
        .put("observation_count", observationCount)

    companion object {
        fun fromJson(raw: JSONObject) = FuelComparison(
            id = raw.optString("id", UUID.randomUUID().toString()),
            dedupeKey = raw.optString("dedupe_key", UUID.randomUUID().toString()),
            visitId = raw.optString("visit_id", ""),
            referenceRegionId = raw.optString("reference_region_id", ""),
            sessionId = raw.optString("session_id", ""),
            capturedAt = raw.optLong("captured_at", 0L),
            origin = raw.optString("origin", "HISTORICAL"),
            rpm = raw.optDouble("rpm", 0.0),
            mapBar = raw.optDouble("map_bar", 0.0),
            waterC = raw.optDouble("water_c", 0.0),
            gasC = raw.optDouble("gas_c", 0.0),
            pressureDiffBar = raw.optDouble("pressure_diff_bar", 0.0),
            cngCellRow = raw.optInt("cng_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_on_cng_ms", 0.0)).getInt("row")),
            cngCellColumn = raw.optInt("cng_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_on_cng_ms", 0.0)).getInt("column")),
            referenceCellRow = raw.optInt("reference_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_target_ms", 0.0)).getInt("row")),
            referenceCellColumn = raw.optInt("reference_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), raw.optDouble("petrol_target_ms", 0.0)).getInt("column")),
            petrolTargetMs = raw.optDouble("petrol_target_ms", 0.0),
            petrolOnCngMs = raw.optDouble("petrol_on_cng_ms", 0.0),
            differenceMs = raw.optDouble("difference_ms", 0.0),
            errorPct = raw.optDouble("error_pct", 0.0),
            direction = raw.optString("direction", "EQUIVALENT"),
            quality = raw.optDouble("quality", 0.0),
            epoch = raw.optInt("epoch", 1),
            mapHash = raw.optString("map_hash", ""),
            observationCount = raw.optInt("observation_count", 1).coerceAtLeast(1),
        )
    }
}

private data class ComparisonEvidence(
    val stage: String,
    val dominantDirection: String,
    val directionConsensus: Double,
    val medianDifferenceMs: Double,
    val madMs: Double,
    val gasTemperatureSpanC: Double,
    val pressureSpanBar: Double,
    val effectiveSamples: Double = 0.0,
    val medianErrorRatio: Double = 0.0,
) {
    fun actionable(direction: String): Boolean =
        direction != "EQUIVALENT" &&
            direction == dominantDirection &&
            directionConsensus >= LearningToleranceSettings.current.directionConsensusMinimum &&
            madMs <= LearningToleranceSettings.current.comparisonMaximumMadMs &&
            gasTemperatureSpanC <= LearningToleranceSettings.current.comparisonMaximumGasTempSpanC &&
            pressureSpanBar <= LearningToleranceSettings.current.comparisonMaximumPressureSpanBar &&
            effectiveSamples > 0.0

    fun confidence(): Double {
        val spread = (1.0 - madMs / LearningToleranceSettings.current.comparisonMaximumMadMs).coerceIn(0.0, 1.0)
        return (directionConsensus * spread * (1.0 - 1.0 / (1.0 + effectiveSamples))).coerceIn(0.0, 1.0)
    }

    fun toJson(): JSONObject = LearningToleranceSettings.current.let { tolerance -> JSONObject()
        .put("stage", stage)
        .put("dominant_direction", dominantDirection)
        .put("direction_consensus", directionConsensus)
        .put("median_difference_ms", medianDifferenceMs)
        .put("mad_ms", madMs)
        .put("gas_temperature_span_c", gasTemperatureSpanC)
        .put("pressure_span_bar", pressureSpanBar)
        .put("effective_samples", effectiveSamples)
        .put("median_error_ratio", medianErrorRatio)
        .put("stable", madMs <= tolerance.comparisonMaximumMadMs &&
            gasTemperatureSpanC <= tolerance.comparisonMaximumGasTempSpanC &&
            pressureSpanBar <= tolerance.comparisonMaximumPressureSpanBar)
        .put("confidence", when (stage) {
            "CONFIRMED" -> 1.0
            "ACCEPTED" -> 0.75
            "PROVISIONAL" -> 0.50
            else -> if (effectiveSamples > 0) 0.20 else 0.0
        })
        .put("next_visit_target", tolerance.confidenceSampleTarget)
    }

    companion object {
        fun empty() = ComparisonEvidence("OBSERVED", "EQUIVALENT", 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

private data class StoredComparison(
    val comparison: FuelComparison,
    val created: Boolean,
)

private fun confidenceStage(density: Double, variance: Double): String = LearningToleranceSettings.current.let { tolerance -> when {
    density >= tolerance.confidenceSampleTarget * 0.8 && variance < tolerance.referenceMaximumSpreadMs * tolerance.referenceMaximumSpreadMs * 0.5 -> "CONFIRMED"
    density >= tolerance.confidenceSampleTarget * 0.5 && variance < tolerance.referenceMaximumSpreadMs * tolerance.referenceMaximumSpreadMs -> "ACCEPTED"
    density >= tolerance.confidenceSampleTarget * 0.2 -> "PROVISIONAL"
    else -> "OBSERVED"
} }

private fun List<Double>.geometricMean(): Double {
    if (isEmpty()) return 0.0
    return fold(1.0) { acc, value -> acc * value.coerceIn(0.0001, 1.0) }
        .pow(1.0 / size)
        .coerceIn(0.0, 1.0)
}

private fun List<Double>.median(): Double {
    if (isEmpty()) return 0.0
    val ordered = sorted()
    val middle = ordered.size / 2
    return if (ordered.size % 2 == 1) ordered[middle] else (ordered[middle - 1] + ordered[middle]) / 2.0
}

/** A influência física na célula governa também o centro e a dispersão da evidência. */
private fun List<Pair<Double, Double>>.weightedMedian(): Double {
    val usable = filter { it.first.isFinite() && it.second.isFinite() && it.second > 0.0 }
        .sortedBy { it.first }
    if (usable.isEmpty()) return 0.0
    val half = usable.sumOf { it.second } / 2.0
    var cumulative = 0.0
    usable.forEach { (value, weight) ->
        cumulative += weight
        if (cumulative >= half) return value
    }
    return usable.last().first
}

private fun errorRatioDirection(errorRatio: Double): String = when {
    abs(errorRatio) <= LearningToleranceSettings.current.equivalenceDeadbandPercent / 100.0 -> "EQUIVALENT"
    errorRatio > 0.0 -> "INCREASE_CNG_DELIVERY"
    else -> "DECREASE_CNG_DELIVERY"
}

private fun <T> List<T>.valueSpan(selector: (T) -> Double): Double {
    if (isEmpty()) return 0.0
    val values = map(selector)
    return (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
}
