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
        internal const val LEGACY_MIN_REFERENCE_MS = 0.05
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

    /**
     * Fotografia mínima para o Advisor. Não inclui grid derivada, sessões nem
     * resumos de UI, evitando reconstruir payloads que a análise não consome.
     */
    fun advisorSnapshot(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("format", FORMAT)
            .put("epoch", epoch)
            .put("mapHash", mapHash)
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
            "equivalence_valid",
            "equivalence_reason_code",
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

        val equivalence = FuelEquivalenceObjective.evaluate(
            referenceMs = reference.petrolTargetMs,
            petrolOnCngMs = sample.petrolMs,
            minimumReferenceMs = LEGACY_MIN_REFERENCE_MS,
            policy = LearningToleranceSettings.current,
        )
        if (!equivalence.valid) {
            return invalidEquivalenceStatus(reference, sample, equivalence)
                .put("cng_region", region.toJson())
                .put("reference_diagnostic", JSONObject(lastReferenceDiagnostic.toString()))
        }

        val candidate = compare(
            reference = reference,
            cngSample = sample,
            visitId = visit.id,
            origin = "CONTINUOUS_REFERENCE_SURFACE",
            pairQuality = sqrt(reference.quality * sample.quality).coerceIn(0.0, 1.0),
            equivalence = equivalence,
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

    private fun invalidEquivalenceStatus(
        reference: PetrolReferenceEstimate,
        cngSample: MotorSample,
        equivalence: FuelEquivalenceResult,
    ): JSONObject = JSONObject()
        .put("state", "FUEL_EQUIVALENCE_INVALID")
        .put("reason", "A referência/observação não permite calcular erro de equivalência com segurança")
        .put("reason_code", equivalence.reasonCode)
        .put("equivalence_valid", false)
        .put("equivalence_reason_code", equivalence.reasonCode)
        .put("learning", true)
        .put("registered_now", false)
        .put("reference_surface", reference.toJson())
        .put("reference_region_ids", JSONArray(reference.regionIds))
        .put("reference_denominator_ms", reference.petrolTargetMs)
        .put("petrol_target_ms", reference.petrolTargetMs)
        .put("petrol_on_cng_ms", cngSample.petrolMs)
        .put("difference_ms", JSONObject.NULL)
        .put("error_ratio", JSONObject.NULL)
        .put("error_pct", JSONObject.NULL)
        .put("direction", JSONObject.NULL)
        .put("reference_unit", "ms")
        .put("observed_unit", "ms")
        .put("difference_unit", "ms")
        .put("error_ratio_unit", "ratio")
        .put("error_percent_unit", "percent")
        .put("cng_sample_started_at_elapsed_ms", cngSample.startedAtElapsedMs)
        .put("cng_sample_ended_at_elapsed_ms", cngSample.endedAtElapsedMs)
        .put("actionable", false)
        .put("suggested_delta_k_percent", JSONObject.NULL)
        .put("suggested_delta_k", JSONObject.NULL)
        .put("automatic_write", false)
        .put("human_confirmation_required", true)

    private fun compare(
        reference: PetrolReferenceEstimate,
        cngSample: MotorSample,
        visitId: String,
        origin: String,
        pairQuality: Double,
        equivalence: FuelEquivalenceResult,
    ): FuelComparison {
        require(equivalence.valid) { "Comparação científica exige FuelEquivalenceObjective válido" }
        val difference = requireNotNull(equivalence.differenceMs)
        val errorRatio = requireNotNull(equivalence.errorRatio)
        val errorPct = requireNotNull(equivalence.errorPercent)
        val direction = equivalenceDirection(equivalence.state)
        val cngCell = LearningGridProjection.cellFor(cngSample.rpm, cngSample.petrolMs)
        val referenceCell = LearningGridProjection.cellFor(cngSample.rpm, reference.petrolTargetMs)
        val referenceRegionId = reference.regionIds.joinToString(",")
        return FuelComparison(
            id = UUID.randomUUID().toString(),
            dedupeKey = "$epoch:$origin:$visitId:$referenceRegionId",
            visitId = visitId,
            referenceRegionId = referenceRegionId,
            referenceRegionIds = reference.regionIds,
            referenceUpdatedAtMs = reference.referenceUpdatedAtMs,
            referenceContextsJson = reference.referenceContextsJson,
            requestEnvironmentJson = reference.requestEnvironmentJson,
            sessionId = sessionId,
            capturedAt = System.currentTimeMillis(),
            cngSampleStartedAtElapsedMs = cngSample.startedAtElapsedMs,
            cngSampleEndedAtElapsedMs = cngSample.endedAtElapsedMs,
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
            petrolTargetMs = reference.petrolTargetMs,
            petrolOnCngMs = cngSample.petrolMs,
            differenceMs = difference,
            errorRatio = errorRatio,
            errorPct = errorPct,
            direction = direction,
            equivalenceState = equivalence.state.name,
            equivalenceReasonCode = equivalence.reasonCode,
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
        .put("equivalence_valid", true)
        .put("equivalence_reason_code", comparison.equivalenceReasonCode)
        .put("comparison", comparison.toJson())
        .put("petrol_target_ms", comparison.petrolTargetMs)
        .put("petrol_on_cng_ms", comparison.petrolOnCngMs)
        .put("error_ratio", comparison.errorRatio)
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
            medianErrorRatio = if (center == 0.0) 0.0 else related.map { it.errorRatio }.median(),
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
        val mean = weighted.sumOf { it.first.errorRatio * it.second } / total
        val differenceCenterMs = weighted.map { it.first.differenceMs to it.second }.weightedMedian()
        val madMs = weighted.map { abs(it.first.differenceMs - differenceCenterMs) to it.second }.weightedMedian()
        val dominant = errorRatioDirection(mean)
        val consensus = weighted.filter { errorRatioDirection(it.first.errorRatio) == dominant }
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
                        petrolMs = region.petrolRobust.median(region.petrolMean),
                        confidence = region.confidence(),
                        sampleCount = region.sampleCount,
                        updatedAtMs = region.updatedAt,
                        environment = PetrolReferenceEnvironmentBridge.region(
                            waterC = region.waterMean,
                            gasTemperatureC = region.gasMean,
                            pressureDiffBar = region.pressureMean,
                        ),
                    )
                }
                .toList(),
            request = PetrolReferenceSelector.Request(
                rpm = sample.rpm,
                mapBar = sample.mapBar,
                waterC = sample.waterC,
                environment = PetrolReferenceEnvironmentBridge.request(
                    waterC = sample.waterC,
                    gasTemperatureC = sample.gasC,
                    pressureDiffBar = sample.pressureDiffBar,
                ),
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
            selectedRegionContexts = result.selectedRegionContexts,
            requestEnvironment = requireNotNull(result.requestEnvironment),
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
            var invalidComparisons = 0
            repeat(savedComparisons.length()) { index ->
                val raw = savedComparisons.optJSONObject(index) ?: return@repeat
                val comparison = FuelComparison.fromJson(raw)
                if (comparison == null) invalidComparisons += 1 else comparisons += comparison
            }
            if (invalidComparisons > 0) {
                log.add(
                    "WARN",
                    "LEARNING-EQUIVALENCE",
                    "$invalidComparisons comparação(ões) legada(s) sem denominador/erro válido foram rebaixadas e não entram na evidência ativa",
                )
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
                    // O lock protege somente a cópia imutável. JSON, digest e disco ficam fora dele.
                    val snapshot = synchronized(lock) { persistenceSnapshotLocked() }
                    val newest = buildPersistedState(snapshot)
                    writePersistedState(newest)
                }
            } finally {
                persistDrainScheduled.set(false)
                if (persistDirty.get()) schedulePersistDrain()
            }
        }
    }

    private fun persistenceSnapshotLocked(): MotorLearningPersistenceSnapshot = MotorLearningPersistenceSnapshot(
        savedAt = System.currentTimeMillis(),
        epoch = epoch,
        mapHash = mapHash,
        revalidationJson = lastCalibrationRevalidation.toString(),
        tolerancePolicyJson = LearningToleranceSettings.current.toJson().toString(),
        regions = regions.map { it.persistenceCopy() },
        comparisons = comparisons.toList(),
        sessions = sessions.map { it.persistenceCopy() },
    )

    private fun buildPersistedState(snapshot: MotorLearningPersistenceSnapshot): JSONObject {
        var selectedVisitLimit = LearningMemoryBudget.MAX_REGION_VISIT_IDS
        var selectedSessionLimit = LearningMemoryBudget.MAX_REGION_SESSION_IDS
        var selected = JSONObject()
        var selectedBytes = Long.MAX_VALUE

        for ((visitLimit, sessionLimit) in LearningMemoryBudget.provenanceLevels) {
            val root = JSONObject()
                .put("format", FORMAT)
                .put("savedAt", snapshot.savedAt)
                .put("epoch", snapshot.epoch)
                .put("mapHash", snapshot.mapHash)
                .put("revalidation", JSONObject(snapshot.revalidationJson))
                .put("tolerancePolicy", JSONObject(snapshot.tolerancePolicyJson))
                // Persistência guarda somente ciência primária; célula/grid são derivados recalculáveis.
                .put("regions", JSONArray(snapshot.regions.map { it.toPersistedJson(visitLimit, sessionLimit) }))
                .put("comparisons", JSONArray(snapshot.comparisons.map { it.toJson() }))
                .put("sessions", JSONArray(snapshot.sessions.map { it.toJson() }))
                .put("memoryBudget", JSONObject()
                    .put("policy", LearningMemoryBudget.POLICY)
                    .put("targetPersistedBytes", LearningMemoryBudget.TARGET_PERSISTED_BYTES)
                    .put("maxRegionVisitIds", visitLimit)
                    .put("maxRegionSessionIds", sessionLimit)
                    .put("regionCount", snapshot.regions.size)
                    .put("comparisonCount", snapshot.comparisons.size)
                    .put("sessionCount", snapshot.sessions.size))
            val bytes = root.toString().toByteArray(Charsets.UTF_8).size.toLong()
            selected = root
            selectedBytes = bytes
            selectedVisitLimit = visitLimit
            selectedSessionLimit = sessionLimit
            if (bytes <= LearningMemoryBudget.TARGET_PERSISTED_BYTES) break
        }

        selected.optJSONObject("memoryBudget")
            ?.put("payloadBytesBeforeDigest", selectedBytes)
            ?.put("targetExceeded", selectedBytes > LearningMemoryBudget.TARGET_PERSISTED_BYTES)
            ?.put("provenanceCompacted",
                selectedVisitLimit < LearningMemoryBudget.MAX_REGION_VISIT_IDS ||
                    selectedSessionLimit < LearningMemoryBudget.MAX_REGION_SESSION_IDS)

        if (selectedBytes > LearningMemoryBudget.TARGET_PERSISTED_BYTES) {
            log.add(
                "WARN",
                "LEARNING-BUDGET",
                "Estado científico excedeu o alvo de bytes mesmo sem proveniência completa; ciência preservada (${selectedBytes} bytes)",
            )
        }
        return selected
    }

    private fun writePersistedState(root: JSONObject) {
        try {
            root.put("stateDigest", canonicalDigest(root))
            stateFile.parentFile?.mkdirs()
            val temp = File(stateFile.parentFile, stateFile.name + ".tmp")
            val backup = File(stateFile.parentFile, stateFile.name + ".bak")
            val encoded = root.toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temp).use { output ->
                output.write(encoded)
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

private data class MotorLearningPersistenceSnapshot(
    val savedAt: Long,
    val epoch: Int,
    val mapHash: String,
    val revalidationJson: String,
    val tolerancePolicyJson: String,
    val regions: List<LearningRegion>,
    val comparisons: List<FuelComparison>,
    val sessions: List<PhysicalLearningSession>,
)

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

    fun persistenceCopy(): PhysicalLearningSession = copy(
        fuels = fuels.toCollection(linkedSetOf()),
    )

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
    val petrolRobust: BoundedRobustPetrolSummary = BoundedRobustPetrolSummary.seed(petrolMean),
    var pressureMean: Double,
    var waterMean: Double,
    var gasMean: Double,
    var qualityMean: Double,
    var weight: Double = 0.0,
    var sampleCount: Int = 0,
    val visits: MutableSet<String> = linkedSetOf(),
    val sessions: MutableSet<String> = linkedSetOf(),
    var visitCount: Int = visits.size,
    var sessionCount: Int = sessions.size,
    var updatedAt: Long = 0L,
) {
    private var lastVisitId: String? = visits.lastOrNull()
    private var lastSessionId: String? = sessions.lastOrNull()

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
        if (sample.fuel == Mp48Fuel.PETROL) petrolRobust.observe(sample.petrolMs)
        pressureMean = blend(pressureMean, sample.pressureDiffBar)
        waterMean = blend(waterMean, sample.waterC)
        gasMean = blend(gasMean, sample.gasC)
        qualityMean = blend(qualityMean, sample.quality)
        weight = total
        sampleCount += 1

        if (visitId != lastVisitId) {
            visitCount = saturatingAdd(max(visitCount, visits.size), 1)
            lastVisitId = visitId
        }
        if (sessionId != lastSessionId) {
            sessionCount = saturatingAdd(max(sessionCount, sessions.size), 1)
            lastSessionId = sessionId
        }
        visits += visitId
        sessions += sessionId
        LearningMemoryBudget.trimNewestIds(visits, LearningMemoryBudget.MAX_REGION_VISIT_IDS)
        LearningMemoryBudget.trimNewestIds(sessions, LearningMemoryBudget.MAX_REGION_SESSION_IDS)
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
        if (fuel == Mp48Fuel.PETROL) petrolRobust.merge(other.petrolRobust)
        pressureMean = blend(pressureMean, other.pressureMean)
        waterMean = blend(waterMean, other.waterMean)
        gasMean = blend(gasMean, other.gasMean)
        qualityMean = blend(qualityMean, other.qualityMean)
        weight = total
        sampleCount = saturatingAdd(sampleCount, other.sampleCount)

        val visitOverlap = visits.intersect(other.visits).size
        val sessionOverlap = sessions.intersect(other.sessions).size
        visitCount = saturatingAdd(
            max(visitCount, visits.size),
            (max(other.visitCount, other.visits.size) - visitOverlap).coerceAtLeast(0),
        )
        sessionCount = saturatingAdd(
            max(sessionCount, sessions.size),
            (max(other.sessionCount, other.sessions.size) - sessionOverlap).coerceAtLeast(0),
        )
        visits += other.visits
        sessions += other.sessions
        LearningMemoryBudget.trimNewestIds(visits, LearningMemoryBudget.MAX_REGION_VISIT_IDS)
        LearningMemoryBudget.trimNewestIds(sessions, LearningMemoryBudget.MAX_REGION_SESSION_IDS)
        lastVisitId = visits.lastOrNull()
        lastSessionId = sessions.lastOrNull()
        updatedAt = max(updatedAt, other.updatedAt)
    }

    fun namespace(source: String): LearningRegion = copy(
        id = "$source:$id",
        petrolRobust = petrolRobust.copySummary(),
        visits = visits.mapTo(linkedSetOf()) { "$source:$it" },
        sessions = sessions.mapTo(linkedSetOf()) { "$source:$it" },
    )

    fun persistenceCopy(): LearningRegion = copy(
        petrolRobust = petrolRobust.copySummary(),
        visits = visits.toCollection(linkedSetOf()),
        sessions = sessions.toCollection(linkedSetOf()),
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
        .put("visits", visitCount)
        .put("sessions", sessionCount)
        .put("confidence", confidence())
        .put("confidence_samples", (sampleCount / tolerance.confidenceSampleTarget.toDouble()).coerceIn(0.0, 1.0))
        .put("next_visit_target", tolerance.confidenceSampleTarget)
        .put("rpm", rpmMean)
        .put("map_bar", mapMean)
    }

    fun toJson(): JSONObject = toJsonWithProvenance(
        visitLimit = LearningMemoryBudget.MAX_REGION_VISIT_IDS,
        sessionLimit = LearningMemoryBudget.MAX_REGION_SESSION_IDS,
    )

    fun toPersistedJson(visitLimit: Int, sessionLimit: Int): JSONObject =
        toJsonWithProvenance(visitLimit, sessionLimit)

    fun toAdvisorJson(): JSONObject = toJsonWithProvenance(
        visitLimit = LearningMemoryBudget.MAX_REGION_VISIT_IDS,
        sessionLimit = 0,
    )

    private fun toJsonWithProvenance(visitLimit: Int, sessionLimit: Int): JSONObject =
        LearningToleranceSettings.current.let { tolerance ->
            val retainedVisits = LearningMemoryBudget.retainNewestIds(visits, visitLimit)
            val retainedSessions = LearningMemoryBudget.retainNewestIds(sessions, sessionLimit)
            JSONObject()
                .put("id", id)
                .put("fuel", fuel.wireName)
                .put("epoch", epoch)
                .put("rpm", rpmMean)
                .put("map_bar", mapMean)
                .put("petrol_ms", petrolMean)
                .put("petrol_squared_mean", petrolSquaredMean)
                .put("petrol_spread_ms", sqrt(max(0.0, petrolSquaredMean - petrolMean * petrolMean)))
                .put("petrol_robust", if (fuel == Mp48Fuel.PETROL) petrolRobust.toJson() else JSONObject.NULL)
                .put("pressure_diff_bar", pressureMean)
                .put("water_c", waterMean)
                .put("gas_c", gasMean)
                .put("quality", qualityMean)
                .put("weight", weight)
                .put("samples", sampleCount)
                .put("visits", JSONArray(retainedVisits.toList()))
                .put("sessions", JSONArray(retainedSessions.toList()))
                .put("visit_count", max(visitCount, retainedVisits.size))
                .put("session_count", max(sessionCount, retainedSessions.size))
                .put("visit_ids_retained", retainedVisits.size)
                .put("session_ids_retained", retainedSessions.size)
                .put("visit_ids_compacted", visitCount > retainedVisits.size)
                .put("session_ids_compacted", sessionCount > retainedSessions.size)
                .put("provenance_policy", LearningMemoryBudget.POLICY)
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
            petrolRobust = if (sample.fuel == Mp48Fuel.PETROL) {
                BoundedRobustPetrolSummary.seed(sample.petrolMs)
            } else {
                BoundedRobustPetrolSummary.empty()
            },
            pressureMean = sample.pressureDiffBar,
            waterMean = sample.waterC,
            gasMean = sample.gasC,
            qualityMean = sample.quality,
        )

        fun fromJson(raw: JSONObject): LearningRegion {
            val visitArray = raw.optJSONArray("visits")
            val sessionArray = raw.optJSONArray("sessions")
            val visits = retainedIds(visitArray, LearningMemoryBudget.MAX_REGION_VISIT_IDS)
            val sessions = retainedIds(sessionArray, LearningMemoryBudget.MAX_REGION_SESSION_IDS)
            val fuel = if (raw.optString("fuel") == Mp48Fuel.CNG.wireName) {
                Mp48Fuel.CNG
            } else {
                Mp48Fuel.PETROL
            }
            val petrolMean = raw.optDouble("petrol_ms", 0.0)
            val petrolSquaredMean = raw.optDouble("petrol_squared_mean", petrolMean * petrolMean)
            val visitCount = max(raw.optInt("visit_count", visitArray?.length() ?: 0), visitArray?.length() ?: 0)
            val sessionCount = max(raw.optInt("session_count", sessionArray?.length() ?: 0), sessionArray?.length() ?: 0)
            return LearningRegion(
                id = raw.optString("id", UUID.randomUUID().toString()),
                fuel = fuel,
                epoch = if (fuel == Mp48Fuel.PETROL) 0 else raw.optInt("epoch", 1).coerceAtLeast(1),
                rpmMean = raw.optDouble("rpm", 0.0),
                mapMean = raw.optDouble("map_bar", 0.0),
                petrolMean = petrolMean,
                petrolSquaredMean = petrolSquaredMean,
                petrolRobust = if (fuel == Mp48Fuel.PETROL) {
                    BoundedRobustPetrolSummary.fromJson(raw.optJSONObject("petrol_robust"), petrolMean)
                } else {
                    BoundedRobustPetrolSummary.empty()
                },
                pressureMean = raw.optDouble("pressure_diff_bar", 0.0),
                waterMean = raw.optDouble("water_c", 0.0),
                gasMean = raw.optDouble("gas_c", 0.0),
                qualityMean = raw.optDouble("quality", 0.5),
                weight = raw.optDouble("weight", 0.0),
                sampleCount = raw.optInt("samples", 0),
                visits = visits,
                sessions = sessions,
                visitCount = visitCount,
                sessionCount = sessionCount,
                updatedAt = raw.optLong("updated_at", 0L),
            )
        }

        private fun retainedIds(raw: JSONArray?, limit: Int): LinkedHashSet<String> {
            if (raw == null || limit <= 0) return linkedSetOf()
            val start = (raw.length() - limit).coerceAtLeast(0)
            val result = linkedSetOf<String>()
            for (index in start until raw.length()) {
                raw.optString(index).takeIf { it.isNotBlank() }?.let(result::add)
            }
            return result
        }

        private fun saturatingAdd(a: Int, b: Int): Int =
            (a.toLong() + b.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

private data class PetrolReferenceEstimate(
    val petrolTargetMs: Double,
    val spreadMs: Double,
    val quality: Double,
    val regionIds: List<String>,
    val stage: String,
    val extrapolated: Boolean = false,
    val selectedRegionContexts: List<PetrolReferenceSelector.SelectedRegionContext>,
    val requestEnvironment: PetrolReferenceSelector.EnvironmentalContext,
) {
    val referenceUpdatedAtMs: Long
        get() = selectedRegionContexts.maxOfOrNull { it.updatedAtMs } ?: 0L
    val referenceContextsJson: String
        get() = JSONArray(selectedRegionContexts.map { it.toJson() }).toString()
    val requestEnvironmentJson: String
        get() = requestEnvironment.toJson().toString()

    fun toJson(): JSONObject = JSONObject()
        .put("petrol_target_ms", petrolTargetMs)
        .put("reference_denominator_ms", petrolTargetMs)
        .put("reference_unit", "ms")
        .put("spread_ms", spreadMs)
        .put("quality", quality)
        .put("region_ids", JSONArray(regionIds))
        .put("reference_region_ids", JSONArray(regionIds))
        .put("region_count", regionIds.size)
        .put("reference_updated_at_wall_ms", if (referenceUpdatedAtMs > 0L) referenceUpdatedAtMs else JSONObject.NULL)
        .put("reference_contexts", JSONArray(referenceContextsJson))
        .put("request_environment", JSONObject(requestEnvironmentJson))
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
    val referenceRegionIds: List<String>,
    val referenceUpdatedAtMs: Long,
    val referenceContextsJson: String,
    val requestEnvironmentJson: String,
    val sessionId: String,
    val capturedAt: Long,
    val cngSampleStartedAtElapsedMs: Long,
    val cngSampleEndedAtElapsedMs: Long,
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
    val errorRatio: Double,
    val errorPct: Double,
    val direction: String,
    val equivalenceState: String,
    val equivalenceReasonCode: String,
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
        val equivalence = FuelEquivalenceObjective.evaluate(
            referenceMs = targetMs,
            petrolOnCngMs = observedMs,
            minimumReferenceMs = MotorLearningMemory.LEGACY_MIN_REFERENCE_MS,
            policy = LearningToleranceSettings.current,
        )
        require(equivalence.valid) { "Consolidação só aceita comparações de equivalência válidas" }
        val differenceMs = requireNotNull(equivalence.differenceMs)
        val errorRatio = requireNotNull(equivalence.errorRatio)
        val errorPct = requireNotNull(equivalence.errorPercent)
        val direction = equivalenceDirection(equivalence.state)
        val consolidatedRpm = blended(rpm, newer.rpm)
        val consolidatedMapBar = blended(mapBar, newer.mapBar)
        val cngCell = LearningGridProjection.cellFor(consolidatedRpm, observedMs)
        val referenceCell = LearningGridProjection.cellFor(consolidatedRpm, targetMs)
        val mergedReferenceIds = (referenceRegionIds + newer.referenceRegionIds).filter { it.isNotBlank() }.distinct()
        return copy(
            referenceRegionId = mergedReferenceIds.joinToString(","),
            referenceRegionIds = mergedReferenceIds,
            referenceUpdatedAtMs = max(referenceUpdatedAtMs, newer.referenceUpdatedAtMs),
            referenceContextsJson = mergeReferenceContexts(referenceContextsJson, newer.referenceContextsJson),
            requestEnvironmentJson = newer.requestEnvironmentJson,
            capturedAt = max(capturedAt, newer.capturedAt),
            cngSampleStartedAtElapsedMs = minimumPositive(cngSampleStartedAtElapsedMs, newer.cngSampleStartedAtElapsedMs),
            cngSampleEndedAtElapsedMs = max(cngSampleEndedAtElapsedMs, newer.cngSampleEndedAtElapsedMs),
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
            errorRatio = errorRatio,
            errorPct = errorPct,
            direction = direction,
            equivalenceState = equivalence.state.name,
            equivalenceReasonCode = equivalence.reasonCode,
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
        .put("reference_region_ids", JSONArray(referenceRegionIds))
        .put("reference_updated_at_wall_ms", if (referenceUpdatedAtMs > 0L) referenceUpdatedAtMs else JSONObject.NULL)
        .put("reference_contexts", JSONArray(referenceContextsJson))
        .put("request_environment", JSONObject(requestEnvironmentJson))
        .put("session_id", sessionId)
        .put("captured_at", capturedAt)
        .put("comparison_captured_at_wall_ms", capturedAt)
        .put("cng_sample_started_at_elapsed_ms", cngSampleStartedAtElapsedMs)
        .put("cng_sample_ended_at_elapsed_ms", cngSampleEndedAtElapsedMs)
        .put("timestamp_domains", JSONObject()
            .put("reference_updated_at_wall_ms", "wall_clock_ms")
            .put("comparison_captured_at_wall_ms", "wall_clock_ms")
            .put("cng_sample_started_at_elapsed_ms", "monotonic_elapsed_ms")
            .put("cng_sample_ended_at_elapsed_ms", "monotonic_elapsed_ms"))
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
        .put("equivalence_valid", true)
        .put("equivalence_state", equivalenceState)
        .put("equivalence_reason_code", equivalenceReasonCode)
        .put("petrol_target_ms", petrolTargetMs)
        .put("reference_denominator_ms", petrolTargetMs)
        .put("petrol_on_cng_ms", petrolOnCngMs)
        .put("difference_ms", differenceMs)
        .put("error_ratio", errorRatio)
        .put("error_pct", errorPct)
        .put("reference_unit", "ms")
        .put("observed_unit", "ms")
        .put("difference_unit", "ms")
        .put("error_ratio_unit", "ratio")
        .put("error_percent_unit", "percent")
        .put("direction", direction)
        .put("quality", quality)
        .put("epoch", epoch)
        .put("map_hash", mapHash)
        .put("observation_count", observationCount)

    companion object {
        fun fromJson(raw: JSONObject): FuelComparison? {
            val targetMs = raw.optDouble("petrol_target_ms", Double.NaN)
            val observedMs = raw.optDouble("petrol_on_cng_ms", Double.NaN)
            val equivalence = FuelEquivalenceObjective.evaluate(
                referenceMs = targetMs,
                petrolOnCngMs = observedMs,
                minimumReferenceMs = MotorLearningMemory.LEGACY_MIN_REFERENCE_MS,
                policy = LearningToleranceSettings.current,
            )
            if (!equivalence.valid) return null
            val legacyReferenceId = raw.optString("reference_region_id", "")
            val structuredIds = raw.optJSONArray("reference_region_ids")?.let { array ->
                buildList {
                    repeat(array.length()) { index ->
                        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty()
            val referenceIds = if (structuredIds.isNotEmpty()) structuredIds.distinct()
            else legacyReferenceId.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val referenceContexts = raw.optJSONArray("reference_contexts")?.toString() ?: "[]"
            val requestEnvironment = raw.optJSONObject("request_environment")?.toString() ?: "{}"
            val referenceUpdated = raw.optLong("reference_updated_at_wall_ms", referenceContextMaxUpdatedAt(referenceContexts))
            return FuelComparison(
                id = raw.optString("id", UUID.randomUUID().toString()),
                dedupeKey = raw.optString("dedupe_key", UUID.randomUUID().toString()),
                visitId = raw.optString("visit_id", ""),
                referenceRegionId = referenceIds.joinToString(",").ifBlank { legacyReferenceId },
                referenceRegionIds = referenceIds,
                referenceUpdatedAtMs = referenceUpdated,
                referenceContextsJson = referenceContexts,
                requestEnvironmentJson = requestEnvironment,
                sessionId = raw.optString("session_id", ""),
                capturedAt = raw.optLong("comparison_captured_at_wall_ms", raw.optLong("captured_at", 0L)),
                cngSampleStartedAtElapsedMs = raw.optLong("cng_sample_started_at_elapsed_ms", 0L),
                cngSampleEndedAtElapsedMs = raw.optLong("cng_sample_ended_at_elapsed_ms", 0L),
                origin = raw.optString("origin", "HISTORICAL"),
                rpm = raw.optDouble("rpm", 0.0),
                mapBar = raw.optDouble("map_bar", 0.0),
                waterC = raw.optDouble("water_c", 0.0),
                gasC = raw.optDouble("gas_c", 0.0),
                pressureDiffBar = raw.optDouble("pressure_diff_bar", 0.0),
                cngCellRow = raw.optInt("cng_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), observedMs).getInt("row")),
                cngCellColumn = raw.optInt("cng_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), observedMs).getInt("column")),
                referenceCellRow = raw.optInt("reference_cell_row", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), targetMs).getInt("row")),
                referenceCellColumn = raw.optInt("reference_cell_column", LearningGridProjection.cellFor(raw.optDouble("rpm", 0.0), targetMs).getInt("column")),
                petrolTargetMs = targetMs,
                petrolOnCngMs = observedMs,
                differenceMs = requireNotNull(equivalence.differenceMs),
                errorRatio = requireNotNull(equivalence.errorRatio),
                errorPct = requireNotNull(equivalence.errorPercent),
                direction = equivalenceDirection(equivalence.state),
                equivalenceState = equivalence.state.name,
                equivalenceReasonCode = equivalence.reasonCode,
                quality = raw.optDouble("quality", 0.0),
                epoch = raw.optInt("epoch", 1),
                mapHash = raw.optString("map_hash", ""),
                observationCount = raw.optInt("observation_count", 1).coerceAtLeast(1),
            )
        }
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

private fun equivalenceDirection(state: FuelEquivalenceState): String = when (state) {
    FuelEquivalenceState.WITHIN_POLICY_DEADBAND -> "EQUIVALENT"
    FuelEquivalenceState.PETROL_ON_CNG_ABOVE_REFERENCE -> "INCREASE_CNG_DELIVERY"
    FuelEquivalenceState.PETROL_ON_CNG_BELOW_REFERENCE -> "DECREASE_CNG_DELIVERY"
    FuelEquivalenceState.INVALID -> "INVALID"
}

private fun mergeReferenceContexts(firstJson: String, secondJson: String): String {
    val merged = linkedMapOf<String, JSONObject>()
    fun absorb(encoded: String) {
        val array = try { JSONArray(encoded) } catch (_: Exception) { JSONArray() }
        repeat(array.length()) { index ->
            val context = array.optJSONObject(index) ?: return@repeat
            val key = "${context.optString("region_id")}:${context.optLong("updated_at_ms", 0L)}"
            merged[key] = JSONObject(context.toString())
        }
    }
    absorb(firstJson)
    absorb(secondJson)
    return JSONArray(merged.values.toList()).toString()
}

private fun referenceContextMaxUpdatedAt(encoded: String): Long {
    val array = try { JSONArray(encoded) } catch (_: Exception) { return 0L }
    var maximum = 0L
    repeat(array.length()) { index ->
        maximum = max(maximum, array.optJSONObject(index)?.optLong("updated_at_ms", 0L) ?: 0L)
    }
    return maximum
}

private fun minimumPositive(first: Long, second: Long): Long = when {
    first <= 0L -> second
    second <= 0L -> first
    else -> minOf(first, second)
}

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
