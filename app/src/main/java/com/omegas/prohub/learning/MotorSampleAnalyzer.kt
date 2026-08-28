package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Analisa o motor pelas leituras reais.
 *
 * Não existe espera fixa depois da troca de combustível. A primeira amostra
 * completamente saudável confirma fisicamente o novo combustível e já pode ser
 * preservada como evidência; somente estados de combustível ainda não resolvidos
 * permanecem inelegíveis para aprendizado.
 */
class MotorSampleAnalyzer(
    private val policyProvider: () -> LearningTolerancePolicy = { LearningToleranceSettings.current },
) {
    private var policySource: LearningTolerancePolicy = policyProvider()
    private var activePolicy: LearningTolerancePolicy = policySource.normalized()
    private var activePolicyJson: String = activePolicy.toJson().toString()
    private val policy: LearningTolerancePolicy get() = activePolicy
    private val minimumFrames: Int get() = AdaptiveSampleWindow.minimumFrames(policy.requiredFrames)
    private val desiredFrames: Int get() = policy.requiredFrames
    private val maximumAttemptMs: Long get() = policy.maximumAttemptMs
    private val warningGapMs: Long get() = policy.warningGapMs
    private val breakingGapMs: Long get() = policy.breakingGapMs
    private val frames = ArrayDeque<Mp48Telemetry>()

    /** Último combustível confirmado por uma amostra física saudável. */
    private var stableFuel: Mp48Fuel? = null

    /** Último combustível normal indicado pela ECU. */
    private var observedNormalFuel: Mp48Fuel? = null

    /** Combustível indicado pela ECU e ainda aguardando confirmação física. */
    private var transitionTarget: Mp48Fuel? = null

    private var receivedSinceReset = 0L
    private var lastEvaluatedAtCount = 0L
    private var lastGapMs = 0L
    private var largestGapMs = 0L
    private var toleratedGapCount = 0
    private var plannedOperationPending = false
    private var continuityLossPending = false
    private var fullWindowRequired = false

    /** Uma operação conhecida nunca pode unir frames anteriores e posteriores. */
    fun markPlannedOperation() {
        resetSamples(requireFullWindow = true)
        plannedOperationPending = true
    }

    /** Falhas repetidas tornam a continuidade física da janela inconfiável. */
    fun markContinuityLost() {
        resetSamples(requireFullWindow = true)
        continuityLossPending = true
    }

    fun reset() {
        resetSamples(requireFullWindow = true)
        stableFuel = null
        observedNormalFuel = null
        transitionTarget = null
    }

    fun add(
        frame: Mp48Telemetry,
        plannedGap: Boolean = false,
        toleratedGap: Boolean = false,
    ): SampleDecision {
        refreshPolicySnapshot()
        return addInternal(frame, plannedGap, toleratedGap).withCell(frame)
    }

    private fun refreshPolicySnapshot() {
        val latest = policyProvider()
        if (latest == policySource) return
        policySource = latest
        activePolicy = latest.normalized()
        activePolicyJson = activePolicy.toJson().toString()
        resetSamples(requireFullWindow = true)
    }

    private fun addInternal(
        frame: Mp48Telemetry,
        plannedGap: Boolean,
        toleratedGap: Boolean,
    ): SampleDecision {
        if (plannedGap) markPlannedOperation()
        if (!isPrimaryEquivalencePlausible(frame)) {
            resetSamples(requireFullWindow = true)
            return SampleDecision.invalid(
                reason = "Leitura fisicamente implausível: ${frame.plausibilityReasons.joinToString().ifBlank { "motivo não informado" }}",
                plausibilityReasons = frame.plausibilityReasons,
            )
        }

        if (frame.fuel == Mp48Fuel.CUTOFF || isPhysicalCutoff(frame)) {
            resetSamples(requireFullWindow = true)
            return SampleDecision.transition(
                state = "CUTOFF",
                reason = "Cut-off / desaceleração — aprendizado pausado",
                fuelConfirmed = stableFuel?.wireName,
            )
        }

        when (frame.fuel) {
            Mp48Fuel.ENGINE_OFF -> {
                reset()
                return SampleDecision.transition(
                    state = "ENGINE_OFF",
                    reason = "Motor parado; aprendizado pausado",
                )
            }
            Mp48Fuel.TRANSITION -> {
                resetSamples(requireFullWindow = true)
                return SampleDecision.transition(
                    state = "FUEL_TRANSITION",
                    reason = "Troca de combustível indicada — observando o motor, sem aprender",
                    fuelConfirmed = stableFuel?.wireName,
                    transitionTarget = transitionTarget?.wireName,
                )
            }
            Mp48Fuel.UNKNOWN -> {
                resetSamples(requireFullWindow = true)
                return SampleDecision.transition(
                    state = "FUEL_UNKNOWN",
                    reason = "Estado de combustível ainda não confirmado",
                    fuelConfirmed = stableFuel?.wireName,
                )
            }
            Mp48Fuel.PETROL, Mp48Fuel.CNG -> Unit
            Mp48Fuel.CUTOFF -> Unit
        }

        val fuel = frame.fuel
        if (observedNormalFuel != fuel) {
            observedNormalFuel = fuel
            if (stableFuel != null && stableFuel != fuel) beginTargetTransition(fuel)
        }

        val target = transitionTarget
        if (target != null) {
            if (fuel != target) {
                if (fuel == stableFuel) {
                    transitionTarget = null
                    resetSamples(requireFullWindow = true)
                } else {
                    beginTargetTransition(fuel)
                }
            }
            if (transitionTarget != null) return verifyTransition(frame, toleratedGap)
        }

        if (stableFuel != null && stableFuel != fuel) {
            beginTargetTransition(fuel)
            return verifyTransition(frame, toleratedGap)
        }

        val collection = collect(frame, toleratedGap)
        collection.immediateDecision?.let { return it }
        if (!evaluationDue()) {
            return formingDecision(
                reason = "Formando amostra ${frames.size.coerceAtMost(desiredFrames)}/$desiredFrames leituras",
                gapMs = collection.gapMs,
                fuel = fuel,
            )
        }

        lastEvaluatedAtCount = receivedSinceReset
        val sequence = frames.toList().takeLast(desiredFrames)
        val result = evaluate(sequence)
        val earlyWindow = sequence.size < desiredFrames
        val acceptedEarly = result.sample?.let { sample ->
            AdaptiveSampleWindow.canAcceptEarly(
                sample = sample,
                desiredFrames = desiredFrames,
                toleratedGapCount = toleratedGapCount,
                fullWindowRequired = fullWindowRequired,
                strongPetrolOscillationRatio = policy.strongPetrolOscillationPercent / 100.0,
            )
        } == true

        if (earlyWindow && !acceptedEarly) {
            val reason = when {
                fullWindowRequired -> "Janela completa obrigatória após transição ou perda de continuidade: ${sequence.size}/$desiredFrames leituras"
                result.sample == null -> "Amostra mínima ainda inválida: ${result.reason}. Refinando até $desiredFrames leituras"
                else -> "Amostra mínima válida com ${(result.sample.quality * 100.0).toInt()}% de peso; refinando até $desiredFrames leituras"
            }
            return formingDecision(
                reason = reason,
                gapMs = collection.gapMs,
                fuel = fuel,
                reasonCode = if (collection.gapMs > 0L) {
                    "TOLERATED_TELEMETRY_DELAY"
                } else {
                    "EARLY_WINDOW_REFINING"
                },
            )
        }

        if (result.sample != null) {
            if (stableFuel == null) stableFuel = fuel
            fullWindowRequired = false
        }
        return result.copy(
            frameCount = result.frameCount.coerceAtMost(desiredFrames),
            gapMs = max(result.gapMs, collection.gapMs),
            fuelConfirmed = stableFuel?.wireName ?: fuel.wireName,
            learningEligible = result.sample != null,
            largestGapMs = largestGapMs,
            toleratedGapCount = toleratedGapCount,
            windowAgeMs = result.durationMs,
            windowBudgetMs = effectiveWindowBudgetMs(),
            reasonCode = when {
                result.sample == null -> result.state
                acceptedEarly -> "SAMPLE_ACCEPTED_EARLY"
                else -> "SAMPLE_ACCEPTED"
            },
        )
    }

    private fun formingDecision(
        reason: String,
        gapMs: Long,
        fuel: Mp48Fuel,
        reasonCode: String = "FORMING_SAMPLE",
    ): SampleDecision = SampleDecision.forming(
        count = frames.size,
        minimum = minimumFrames,
        desired = desiredFrames,
        timing = timing(),
        reason = reason,
        gapMs = gapMs,
        fuelConfirmed = stableFuel?.wireName ?: fuel.wireName,
        largestGapMs = largestGapMs,
        toleratedGapCount = toleratedGapCount,
        windowAgeMs = timing().durationMs,
        windowBudgetMs = effectiveWindowBudgetMs(),
        reasonCode = reasonCode,
    )

    private fun verifyTransition(frame: Mp48Telemetry, toleratedGap: Boolean): SampleDecision {
        val target = transitionTarget ?: frame.fuel
        val collection = collect(frame, toleratedGap)
        collection.immediateDecision?.let {
            return it.copy(
                state = "FUEL_VERIFYING",
                reason = "Confirmando ${target.label()}: ${it.reason}",
                learningEligible = false,
                transitionTarget = target.wireName,
                verificationPasses = 0,
                verificationRequired = 1,
                fuelConfirmed = stableFuel?.wireName,
            )
        }

        if (frames.size < desiredFrames || !evaluationDue()) {
            return SampleDecision.transition(
                state = "FUEL_VERIFYING",
                reason = "Confirmando estabilidade em ${target.label()} — nenhuma leitura será salva ainda",
                frameCount = frames.size.coerceAtMost(desiredFrames),
                gapMs = collection.gapMs,
                transitionTarget = target.wireName,
                verificationPasses = 0,
                verificationRequired = 1,
                fuelConfirmed = stableFuel?.wireName,
            )
        }

        lastEvaluatedAtCount = receivedSinceReset
        val evaluated = evaluate(frames.toList().takeLast(desiredFrames))
        val confirmationSample = evaluated.sample
        if (confirmationSample == null) {
            return evaluated.copy(
                state = "FUEL_VERIFYING",
                reason = "Confirmando ${target.label()}: ${evaluated.reason}",
                sample = null,
                learningEligible = false,
                frameCount = evaluated.frameCount.coerceAtMost(desiredFrames),
                transitionTarget = target.wireName,
                verificationPasses = 0,
                verificationRequired = 1,
                fuelConfirmed = stableFuel?.wireName,
            )
        }

        stableFuel = target
        observedNormalFuel = target
        transitionTarget = null
        resetSamples(requireFullWindow = true)
        return SampleDecision.transition(
            state = "FUEL_STABLE",
            reason = "${target.label()} confirmado por uma amostra válida; evidência preservada",
            frameCount = confirmationSample.frameCount,
            diagnostics = confirmationSample.diagnostics,
            sample = confirmationSample,
            learningEligible = true,
            fuelConfirmed = target.wireName,
            verificationPasses = 1,
            verificationRequired = 1,
            fuelJustStabilized = true,
        )
    }

    private fun beginTargetTransition(target: Mp48Fuel) {
        resetSamples(requireFullWindow = true)
        transitionTarget = target
    }

    private fun collect(frame: Mp48Telemetry, toleratedGap: Boolean): CollectionResult {
        val crossedPlannedOperation = plannedOperationPending
        val restartedAfterLoss = continuityLossPending
        plannedOperationPending = false
        continuityLossPending = false
        var warning = 0L
        frames.lastOrNull()?.let { previous ->
            val gap = frame.capturedAtElapsedMs - previous.capturedAtElapsedMs
            lastGapMs = gap
            largestGapMs = max(largestGapMs, gap)
            if (gap > breakingGapMs && !toleratedGap) {
                resetSamples(requireFullWindow = true)
                frames.addLast(frame)
                receivedSinceReset = 1L
                return CollectionResult(
                    immediateDecision = SampleDecision.transition(
                        state = "TELEMETRY_GAP",
                        reason = "Lacuna anormal de $gap ms entre leituras",
                        frameCount = 1,
                        gapMs = gap,
                        fuelConfirmed = stableFuel?.wireName,
                        largestGapMs = gap,
                        continuityLost = true,
                        reasonCode = "REAL_TELEMETRY_LOSS",
                    ),
                    gapMs = gap,
                )
            }
            if (gap > warningGapMs || toleratedGap) {
                warning = gap
                toleratedGapCount += 1
            }
        }

        frames.addLast(frame)
        receivedSinceReset += 1L
        val windowAgeMs = timing().durationMs
        val windowBudgetMs = effectiveWindowBudgetMs()
        if (frames.size < minimumFrames && windowAgeMs > windowBudgetMs) {
            val discarded = (frames.size - 1).coerceAtLeast(0)
            resetSamples(requireFullWindow = true)
            frames.addLast(frame)
            receivedSinceReset = 1L
            return CollectionResult(
                immediateDecision = SampleDecision.transition(
                    state = "WINDOW_TIMEOUT",
                    reason = "A tentativa expirou em $windowAgeMs ms antes de reunir $minimumFrames leituras mínimas; iniciando uma janela nova",
                    frameCount = 1,
                    fuelConfirmed = stableFuel?.wireName ?: frame.fuel.wireName,
                    reasonCode = "WINDOW_TIMEOUT",
                    windowAgeMs = windowAgeMs,
                    windowBudgetMs = windowBudgetMs,
                    framesEvicted = discarded,
                ),
                gapMs = warning,
            )
        }
        while (frames.size > desiredFrames) frames.removeFirst()

        if (frames.size < minimumFrames) {
            return CollectionResult(
                immediateDecision = SampleDecision.forming(
                    count = frames.size,
                    minimum = minimumFrames,
                    desired = desiredFrames,
                    timing = timing(),
                    gapMs = warning,
                    fuelConfirmed = stableFuel?.wireName ?: frame.fuel.wireName,
                    largestGapMs = largestGapMs,
                    toleratedGapCount = toleratedGapCount,
                    plannedOperation = crossedPlannedOperation,
                    continuityLost = restartedAfterLoss,
                    windowAgeMs = timing().durationMs,
                    windowBudgetMs = windowBudgetMs,
                    reasonCode = when {
                        crossedPlannedOperation -> "WINDOW_RESTARTED_AFTER_PLANNED_OPERATION"
                        restartedAfterLoss -> "WINDOW_RESTARTED_AFTER_TELEMETRY_LOSS"
                        warning > 0L -> "TOLERATED_TELEMETRY_DELAY"
                        else -> "FORMING_SAMPLE"
                    },
                ),
                gapMs = warning,
            )
        }
        return CollectionResult(null, warning)
    }

    private fun evaluate(sequence: List<Mp48Telemetry>): SampleDecision {
        val fuel = sequence.last().fuel
        if (sequence.any { it.fuel != fuel }) {
            return SampleDecision.transition(
                state = "SAMPLE_REJECTED",
                reason = "Combustível mudou dentro da amostra",
                frameCount = sequence.size,
            )
        }

        val first = sequence.take(sequence.size / 2)
        val second = sequence.drop(sequence.size / 2)
        val rpm = robustCenter(sequence.map { it.rpm.toDouble() })
        val mapBar = robustCenter(sequence.map { it.mapBar })
        val petrolMs = robustCenter(sequence.map { it.petrolMs })
        val pressure = robustCenter(sequence.map { it.pressureDiffBar })
        val waterC = robustCenter(sequence.map { it.waterC.toDouble() })
        val gasC = robustCenter(sequence.map { it.gasC.toDouble() })

        val rpmCenterShift = abs(median(second.map { it.rpm.toDouble() }) - median(first.map { it.rpm.toDouble() }))
        val mapCenterShift = abs(median(second.map { it.mapBar }) - median(first.map { it.mapBar }))
        val petrolCenterShift = abs(median(second.map { it.petrolMs }) - median(first.map { it.petrolMs }))
        val pressureCenterShift = abs(median(second.map { it.pressureDiffBar }) - median(first.map { it.pressureDiffBar }))

        val rpmOscillation = centralSpan(sequence.map { it.rpm.toDouble() })
        val mapOscillation = centralSpan(sequence.map { it.mapBar })
        val petrolOscillationMs = centralSpan(sequence.map { it.petrolMs })
        val petrolOscillationRatio = petrolOscillationMs / max(0.02, abs(petrolMs))
        val pressureOscillation = centralSpan(sequence.map { it.pressureDiffBar })

        val activePolicy = policy
        val rpmCenterLimit = max(activePolicy.rpmCenterMinimum, abs(rpm) * activePolicy.rpmCenterPercent / 100.0)
        val rpmOscillationLimit = max(
            activePolicy.rpmOscillationMinimum,
            abs(rpm) * activePolicy.rpmOscillationPercent / 100.0,
        )
        val petrolCenterLimit = max(
            activePolicy.petrolCenterMinimumMs,
            abs(petrolMs) * activePolicy.petrolCenterPercent / 100.0,
        )
        val minimumWaterC = LearningTemperatureSettings.currentMinimumWaterC
        val sampleTiming = timing(sequence)

        val diagnostics = SampleDiagnostics(
            frameCount = sequence.size,
            durationMs = sampleTiming.durationMs,
            medianIntervalMs = sampleTiming.medianIntervalMs,
            waterCenterC = waterC,
            minimumWaterC = minimumWaterC,
            rpmCenterShift = rpmCenterShift,
            rpmCenterLimit = rpmCenterLimit,
            rpmOscillation = rpmOscillation,
            rpmOscillationLimit = rpmOscillationLimit,
            mapCenterShift = mapCenterShift,
            mapCenterLimit = activePolicy.mapCenterBar,
            mapOscillation = mapOscillation,
            mapOscillationLimit = activePolicy.mapOscillationBar,
            petrolCenterShift = petrolCenterShift,
            petrolCenterLimit = petrolCenterLimit,
            petrolOscillationRatio = petrolOscillationRatio,
            petrolOscillationLimit = activePolicy.petrolOscillationPercent / 100.0,
            pressureCenterShift = pressureCenterShift,
            pressureCenterLimit = activePolicy.pressureCenterBar,
            pressureOscillation = pressureOscillation,
            pressureOscillationLimit = activePolicy.pressureOscillationBar,
            largestGapMs = sequence.zipWithNext { a, b -> b.capturedAtElapsedMs - a.capturedAtElapsedMs }
                .maxOrNull() ?: 0L,
            toleratedGapCount = toleratedGapCount,
        )

        val primaryWeight = EquivalenceEvidenceWeight.from(diagnostics)
        val classification = if (
            sequence.size >= desiredFrames &&
            primaryWeight.stability >= 0.5 &&
            petrolOscillationRatio <= activePolicy.strongPetrolOscillationPercent / 100.0
        ) {
            SampleClassification.STRONG
        } else {
            SampleClassification.USABLE
        }

        return SampleDecision.accepted(
            MotorSample(
                id = UUID.randomUUID().toString(),
                startedAtElapsedMs = sequence.first().capturedAtElapsedMs,
                endedAtElapsedMs = sequence.last().capturedAtElapsedMs,
                fuel = fuel,
                rpm = rpm,
                mapBar = mapBar,
                petrolMs = petrolMs,
                pressureDiffBar = pressure,
                waterC = waterC,
                gasC = gasC,
                quality = primaryWeight.stability,
                classification = classification,
                frameCount = sequence.size,
                diagnostics = diagnostics,
            ),
        )
    }

    private fun isPrimaryEquivalencePlausible(frame: Mp48Telemetry): Boolean =
        frame.rpm in 0..9_000 &&
            frame.mapBar in 0.0..2.5 &&
            frame.petrolMs in 0.0..40.0 &&
            (
                frame.petrolMs > 0.0 ||
                    when (frame.fuel) {
                        Mp48Fuel.ENGINE_OFF,
                        Mp48Fuel.TRANSITION,
                        Mp48Fuel.CUTOFF,
                        Mp48Fuel.UNKNOWN,
                        -> true
                        Mp48Fuel.PETROL,
                        Mp48Fuel.CNG,
                        -> isPhysicalCutoff(frame)
                    }
                )

    private fun isPhysicalCutoff(frame: Mp48Telemetry): Boolean = policy.let { active ->
        frame.rpm >= active.cutoffMinimumRpm &&
            frame.petrolMs < active.cutoffMaximumPetrolMs &&
            frame.gasRaw == 0 &&
            frame.mapBar < active.cutoffMaximumMapBar
    }

    /** Depois do mínimo, cada quadro novo provoca uma nova avaliação. */
    private fun evaluationDue(): Boolean =
        frames.size >= minimumFrames && receivedSinceReset - lastEvaluatedAtCount >= 1L

    /**
     * Mantém o orçamento configurado como mínimo e adapta para a cadência
     * observada. Leituras nunca são removidas apenas por idade: uma tentativa
     * expirada gera WINDOW_TIMEOUT e reinício explícito.
     */
    private fun effectiveWindowBudgetMs(): Long {
        if (frames.size < 2) return maximumAttemptMs
        val medianInterval = timing().medianIntervalMs.coerceAtLeast(1L)
        val cadenceBudget = (medianInterval * (desiredFrames - 1) * 3L / 2L) + warningGapMs
        return max(maximumAttemptMs, cadenceBudget).coerceAtMost(10_000L)
    }

    private fun resetSamples(requireFullWindow: Boolean = false) {
        frames.clear()
        receivedSinceReset = 0L
        lastEvaluatedAtCount = 0L
        lastGapMs = 0L
        largestGapMs = 0L
        toleratedGapCount = 0
        if (requireFullWindow) fullWindowRequired = true
    }

    private fun timing(sequence: List<Mp48Telemetry> = frames.toList()): SampleTiming {
        if (sequence.size < 2) return SampleTiming(0L, 0L)
        val intervals = sequence.zipWithNext { a, b -> b.capturedAtElapsedMs - a.capturedAtElapsedMs }
        return SampleTiming(
            durationMs = sequence.last().capturedAtElapsedMs - sequence.first().capturedAtElapsedMs,
            medianIntervalMs = median(intervals.map(Long::toDouble)).toLong(),
        )
    }

    private fun robustCenter(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val center = median(values)
        val mad = median(values.map { abs(it - center) })
        if (mad <= 1e-9) return center
        val accepted = values.filter { abs(it - center) <= mad * 3.5 }
        return if (accepted.isEmpty()) center else median(accepted)
    }

    private fun centralSpan(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        if (sorted.size < 4) return sorted.last() - sorted.first()
        return quantile(sorted, 0.90) - quantile(sorted, 0.10)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun quantile(sorted: List<Double>, fraction: Double): Double {
        val position = (sorted.size - 1) * fraction.coerceIn(0.0, 1.0)
        val low = floor(position).toInt()
        val high = ceil(position).toInt()
        if (low == high) return sorted[low]
        val weight = position - low
        return sorted[low] * (1.0 - weight) + sorted[high] * weight
    }

    private fun Mp48Fuel.label(): String = when (this) {
        Mp48Fuel.PETROL -> "gasolina"
        Mp48Fuel.CNG -> "GNV"
        else -> wireName.lowercase()
    }

    private fun SampleDecision.withCell(frame: Mp48Telemetry): SampleDecision {
        val cell = LearningGridCellLocator.locate(frame.rpm.toDouble(), frame.petrolMs)
        return copy(
            minimumFrames = this@MotorSampleAnalyzer.minimumFrames,
            desiredFrames = this@MotorSampleAnalyzer.desiredFrames,
            cellKey = cell.key,
            cellRow = cell.row,
            cellColumn = cell.column,
            tolerancePolicy = activePolicyJson,
            windowAgeMs = if (windowAgeMs > 0L) windowAgeMs else durationMs,
            windowBudgetMs = if (windowBudgetMs > 0L) windowBudgetMs else effectiveWindowBudgetMs(),
        )
    }

    private data class CollectionResult(
        val immediateDecision: SampleDecision?,
        val gapMs: Long,
    )
}

enum class SampleClassification {
    STRONG,
    USABLE,
    TRANSITION,
    INVALID,
}

data class MotorSample(
    val id: String,
    val startedAtElapsedMs: Long,
    val endedAtElapsedMs: Long,
    val fuel: Mp48Fuel,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val pressureDiffBar: Double,
    val waterC: Double,
    val gasC: Double,
    val quality: Double,
    val classification: SampleClassification,
    val frameCount: Int,
    val diagnostics: SampleDiagnostics,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("started_at_elapsed_ms", startedAtElapsedMs)
        .put("ended_at_elapsed_ms", endedAtElapsedMs)
        .put("fuel", fuel.wireName)
        .put("rpm", rpm)
        .put("map_bar", mapBar)
        .put("petrol_ms", petrolMs)
        .put("pressure_diff_bar", pressureDiffBar)
        .put("water_c", waterC)
        .put("gas_c", gasC)
        .put("quality", quality)
        .put("classification", classification.name)
        .put("frame_count", frameCount)
        .put("diagnostics", diagnostics.toJson())
}

data class SampleDiagnostics(
    val frameCount: Int,
    val durationMs: Long,
    val medianIntervalMs: Long,
    val waterCenterC: Double,
    val minimumWaterC: Int,
    val rpmCenterShift: Double,
    val rpmCenterLimit: Double,
    val rpmOscillation: Double,
    val rpmOscillationLimit: Double,
    val mapCenterShift: Double,
    val mapCenterLimit: Double,
    val mapOscillation: Double,
    val mapOscillationLimit: Double,
    val petrolCenterShift: Double,
    val petrolCenterLimit: Double,
    val petrolOscillationRatio: Double,
    val petrolOscillationLimit: Double,
    val pressureCenterShift: Double,
    val pressureCenterLimit: Double,
    val pressureOscillation: Double,
    val pressureOscillationLimit: Double,
    val largestGapMs: Long = 0L,
    val toleratedGapCount: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("frame_count", frameCount)
        .put("duration_ms", durationMs)
        .put("median_interval_ms", medianIntervalMs)
        .put("water_center_c", waterCenterC)
        .put("minimum_water_c", minimumWaterC)
        .put("rpm_center_shift", rpmCenterShift)
        .put("rpm_center_limit", rpmCenterLimit)
        .put("rpm_oscillation", rpmOscillation)
        .put("rpm_oscillation_limit", rpmOscillationLimit)
        .put("map_center_shift", mapCenterShift)
        .put("map_center_limit", mapCenterLimit)
        .put("map_oscillation", mapOscillation)
        .put("map_oscillation_limit", mapOscillationLimit)
        .put("petrol_center_shift", petrolCenterShift)
        .put("petrol_center_limit", petrolCenterLimit)
        .put("petrol_oscillation_ratio", petrolOscillationRatio)
        .put("petrol_oscillation_limit", petrolOscillationLimit)
        .put("pressure_center_shift", pressureCenterShift)
        .put("pressure_center_limit", pressureCenterLimit)
        .put("pressure_oscillation", pressureOscillation)
        .put("pressure_oscillation_limit", pressureOscillationLimit)
        .put("largest_gap_ms", largestGapMs)
        .put("tolerated_gap_count", toleratedGapCount)
}

data class SampleTiming(val durationMs: Long, val medianIntervalMs: Long)

data class SampleDecision(
    val state: String,
    val reason: String,
    val classification: SampleClassification,
    val frameCount: Int = 0,
    val minimumFrames: Int = 12,
    val desiredFrames: Int = 12,
    val durationMs: Long = 0L,
    val medianIntervalMs: Long = 0L,
    val gapMs: Long = 0L,
    val blockedRemainingMs: Long = 0L,
    val sample: MotorSample? = null,
    val diagnostics: SampleDiagnostics? = null,
    val learningEligible: Boolean = false,
    val fuelConfirmed: String? = null,
    val transitionTarget: String? = null,
    val verificationPasses: Int = 0,
    val verificationRequired: Int = 0,
    val fuelJustStabilized: Boolean = false,
    val largestGapMs: Long = 0L,
    val toleratedGapCount: Int = 0,
    val plannedOperation: Boolean = false,
    val continuityLost: Boolean = false,
    val reasonCode: String = state,
    val windowAgeMs: Long = 0L,
    val windowBudgetMs: Long = 0L,
    val framesEvicted: Int = 0,
    val plausibilityReasons: List<String> = emptyList(),
    val cellKey: String = "",
    val cellRow: Int = -1,
    val cellColumn: Int = -1,
    val tolerancePolicy: String = "{}",
) {
    /** Contrato enxuto para o evento de alta frequência; detalhes ficam no snapshot completo. */
    fun toTelemetryJson(): JSONObject = JSONObject()
        .put("state", state)
        .put("reason", reason)
        .put("classification", classification.name)
        .put("frame_count", frameCount.coerceAtMost(desiredFrames))
        .put("minimum_frames", minimumFrames)
        .put("desired_frames", desiredFrames)
        .put("duration_ms", durationMs)
        .put("median_interval_ms", medianIntervalMs)
        .put("gap_ms", gapMs)
        .put("learning_eligible", learningEligible)
        .put("fuel_confirmed", fuelConfirmed ?: JSONObject.NULL)
        .put("reason_code", reasonCode)
        .put("window_age_ms", windowAgeMs)
        .put("window_budget_ms", windowBudgetMs)
        .put("frames_evicted", framesEvicted)
        .put("plausibility_reasons", org.json.JSONArray(plausibilityReasons))
        .put("cell_key", cellKey)
        .put("cell_row", cellRow)
        .put("cell_column", cellColumn)
        .put("quality", sample?.quality ?: 0.0)

    fun toJson(): JSONObject = JSONObject()
        .put("state", state)
        .put("reason", reason)
        .put("classification", classification.name)
        .put("frame_count", frameCount.coerceAtMost(desiredFrames))
        .put("minimum_frames", minimumFrames)
        .put("desired_frames", desiredFrames)
        .put("duration_ms", durationMs)
        .put("median_interval_ms", medianIntervalMs)
        .put("gap_ms", gapMs)
        .put("blocked_remaining_ms", blockedRemainingMs)
        .put("learning_eligible", learningEligible)
        .put("fuel_confirmed", fuelConfirmed ?: JSONObject.NULL)
        .put("transition_target", transitionTarget ?: JSONObject.NULL)
        .put("verification_passes", verificationPasses)
        .put("verification_required", verificationRequired)
        .put("fuel_just_stabilized", fuelJustStabilized)
        .put("largest_gap_ms", largestGapMs)
        .put("tolerated_gap_count", toleratedGapCount)
        .put("planned_operation", plannedOperation)
        .put("continuity_lost", continuityLost)
        .put("reason_code", reasonCode)
        .put("window_age_ms", windowAgeMs)
        .put("window_budget_ms", windowBudgetMs)
        .put("frames_evicted", framesEvicted)
        .put("plausibility_reasons", org.json.JSONArray(plausibilityReasons))
        .put("cell_key", cellKey)
        .put("cell_row", cellRow)
        .put("cell_column", cellColumn)
        .put("tolerance_policy", try { JSONObject(tolerancePolicy) } catch (_: Exception) { JSONObject() })
        .put("sample", sample?.toJson() ?: JSONObject.NULL)
        .put("diagnostics", diagnostics?.toJson() ?: JSONObject.NULL)

    companion object {
        fun forming(
            count: Int,
            minimum: Int,
            desired: Int,
            timing: SampleTiming,
            reason: String = "Formando amostra ${count.coerceAtMost(desired)}/$desired leituras",
            gapMs: Long = 0L,
            fuelConfirmed: String? = null,
            largestGapMs: Long = 0L,
            toleratedGapCount: Int = 0,
            plannedOperation: Boolean = false,
            continuityLost: Boolean = false,
            reasonCode: String = "FORMING_SAMPLE",
            windowAgeMs: Long = timing.durationMs,
            windowBudgetMs: Long = 0L,
        ) = SampleDecision(
            state = "FORMING_SAMPLE",
            reason = reason,
            classification = SampleClassification.TRANSITION,
            frameCount = count.coerceAtMost(desired),
            minimumFrames = minimum,
            desiredFrames = desired,
            durationMs = timing.durationMs,
            medianIntervalMs = timing.medianIntervalMs,
            gapMs = gapMs,
            fuelConfirmed = fuelConfirmed,
            largestGapMs = largestGapMs,
            toleratedGapCount = toleratedGapCount,
            plannedOperation = plannedOperation,
            continuityLost = continuityLost,
            reasonCode = reasonCode,
            windowAgeMs = windowAgeMs,
            windowBudgetMs = windowBudgetMs,
        )

        fun transition(
            reason: String,
            state: String = "TRANSITION",
            frameCount: Int = 0,
            gapMs: Long = 0L,
            blockedRemainingMs: Long = 0L,
            sample: MotorSample? = null,
            diagnostics: SampleDiagnostics? = null,
            learningEligible: Boolean = false,
            fuelConfirmed: String? = null,
            transitionTarget: String? = null,
            verificationPasses: Int = 0,
            verificationRequired: Int = 0,
            fuelJustStabilized: Boolean = false,
            largestGapMs: Long = 0L,
            toleratedGapCount: Int = 0,
            plannedOperation: Boolean = false,
            continuityLost: Boolean = false,
            reasonCode: String = state,
            windowAgeMs: Long = 0L,
            windowBudgetMs: Long = 0L,
            framesEvicted: Int = 0,
        ) = SampleDecision(
            state = state,
            reason = reason,
            classification = SampleClassification.TRANSITION,
            frameCount = frameCount.coerceAtLeast(0),
            gapMs = gapMs,
            blockedRemainingMs = blockedRemainingMs,
            sample = sample,
            diagnostics = diagnostics,
            learningEligible = learningEligible,
            fuelConfirmed = fuelConfirmed,
            transitionTarget = transitionTarget,
            verificationPasses = verificationPasses,
            verificationRequired = verificationRequired,
            fuelJustStabilized = fuelJustStabilized,
            largestGapMs = largestGapMs,
            toleratedGapCount = toleratedGapCount,
            plannedOperation = plannedOperation,
            continuityLost = continuityLost,
            reasonCode = reasonCode,
            windowAgeMs = windowAgeMs,
            windowBudgetMs = windowBudgetMs,
            framesEvicted = framesEvicted,
        )

        fun invalid(reason: String, plausibilityReasons: List<String> = emptyList()) = SampleDecision(
            state = "INVALID",
            reason = reason,
            classification = SampleClassification.INVALID,
            reasonCode = "PLAUSIBILITY_REJECTED",
            plausibilityReasons = plausibilityReasons,
        )

        fun accepted(sample: MotorSample) = SampleDecision(
            state = "SAMPLE_ACCEPTED",
            reason = if (sample.classification == SampleClassification.STRONG) {
                "Amostra forte"
            } else {
                "Amostra utilizável"
            },
            classification = sample.classification,
            frameCount = sample.frameCount,
            durationMs = sample.diagnostics.durationMs,
            medianIntervalMs = sample.diagnostics.medianIntervalMs,
            sample = sample,
            diagnostics = sample.diagnostics,
            learningEligible = true,
            fuelConfirmed = sample.fuel.wireName,
        )
    }
}