package com.omegas.prohub.physics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

data class FastPhysicsScenario(
    val currentFactor: Double,
    val trueTargetFactor: Double,
    val truePlantGain: Double,
    val mechanism: CorrectionMechanism,
) {
    init {
        require(currentFactor > 0.0 && trueTargetFactor > 0.0 && truePlantGain > 0.0)
        require(mechanism == CorrectionMechanism.MAP_LOCAL || mechanism == CorrectionMechanism.CURVE_MUL_ACT)
    }
}

data class FastPhysicsGateReport(
    val totalScenarios: Int,
    val meanTargetRelativeError: Double,
    val signalAccuracy: Double,
    val intervalCoverage: Double,
    val gainMeanAbsoluteError: Double,
    val allocationExclusive: Boolean,
    val maxExpectedStepsToTolerance: Int,
    val unknownGainAbstains: Boolean,
    val falsePrecisionCount: Int,
    val pass: Boolean,
)

/**
 * Deterministic offline gate over synthetic/known-truth scenarios. It measures
 * estimator and policy properties only; it is not physical ECU evidence.
 */
object FastPhysicsGateEvaluator {
    private const val REFERENCE_MS = 5.0
    private const val RELATIVE_STD = 0.02
    private const val STEP_UNCERTAINTY = 0.25
    private const val TARGET_TOLERANCE_RATIO = 0.01

    fun evaluate(scenarios: List<FastPhysicsScenario>): FastPhysicsGateReport {
        require(scenarios.isNotEmpty())
        var targetErrorSum = 0.0
        var signalCorrect = 0
        var covered = 0
        var gainErrorSum = 0.0
        var allocationExclusive = true
        var maxSteps = 0
        var falsePrecision = 0

        scenarios.forEachIndexed { index, scenario ->
            val logError = scenario.truePlantGain * ln(scenario.trueTargetFactor / scenario.currentFactor)
            val petrolOnGasMs = REFERENCE_MS * exp(logError)
            val gain = PlantGain.empiricallyBounded(
                mean = scenario.truePlantGain,
                lower = scenario.truePlantGain * 0.90,
                upper = scenario.truePlantGain * 1.10,
            )
            val estimate = KStarEstimator.estimate(
                syntheticKStarInput(
                    petrolOnGasMs = petrolOnGasMs,
                    petrolReferenceMs = REFERENCE_MS,
                    currentFactor = scenario.currentFactor,
                    gain = gain,
                    suffix = index.toString(),
                ),
            )
            val estimatedTarget = estimate.targetFactor
            if (estimatedTarget == null) {
                falsePrecision++
                return@forEachIndexed
            }
            targetErrorSum += abs(estimatedTarget - scenario.trueTargetFactor) / scenario.trueTargetFactor
            val trueDirection = direction(scenario.currentFactor, scenario.trueTargetFactor)
            val estimatedDirection = direction(scenario.currentFactor, estimatedTarget)
            if (trueDirection == estimatedDirection) signalCorrect++

            val interval = PhysicsOracleValidator.gumEquivalent(
                petrolOnGasMs = petrolOnGasMs,
                petrolReferenceMs = REFERENCE_MS,
                currentFactor = scenario.currentFactor,
                gain = gain,
                relativeStd = RELATIVE_STD,
            )
            if (interval.lower95 != null && interval.upper95 != null &&
                scenario.trueTargetFactor in interval.lower95..interval.upper95
            ) covered++

            val interventionDelta = 0.05
            val beforeError = 0.10
            val afterError = beforeError - scenario.truePlantGain * interventionDelta
            val posterior = PlantGainPosterior.unknown().update(
                beforeLogError = beforeError,
                afterLogError = afterError,
                appliedLogFactorDelta = interventionDelta,
                observationVariance = 0.0025,
            ).toPlantGain()
            val learnedGain = posterior.mean
            if (learnedGain != null) {
                gainErrorSum += abs(learnedGain - scenario.truePlantGain)
            } else {
                falsePrecision++
            }

            val allocation = ExclusiveActuatorAllocator.allocate(
                mechanism = scenario.mechanism,
                idealTarget = IdealTarget(estimatedTarget, estimate.authority),
            )
            allocationExclusive = allocationExclusive && allocation.mapShare + allocation.curveShare <= 1.0 + 1e-12

            maxSteps = maxOf(maxSteps, expectedStepsToTolerance(scenario.currentFactor, estimatedTarget))
        }

        val unknown = KStarEstimator.estimate(
            syntheticKStarInput(
                petrolOnGasMs = 5.5,
                petrolReferenceMs = 5.0,
                currentFactor = 1.0,
                gain = PlantGain.unknown(),
                suffix = "unknown-gain",
            ),
        )
        val unknownGainAbstains = unknown.abstained && unknown.targetFactor == null &&
            unknown.authority == MagnitudeAuthority.UNKNOWN
        if (!unknownGainAbstains) falsePrecision++

        val total = scenarios.size
        val meanTargetError = targetErrorSum / total
        val signalAccuracy = signalCorrect.toDouble() / total
        val intervalCoverage = covered.toDouble() / total
        val gainMeanAbsoluteError = gainErrorSum / total
        val pass = meanTargetError < 0.01 &&
            signalAccuracy == 1.0 &&
            intervalCoverage >= 0.90 &&
            gainMeanAbsoluteError < 0.10 &&
            allocationExclusive &&
            maxSteps in 1..20 &&
            unknownGainAbstains &&
            falsePrecision == 0

        return FastPhysicsGateReport(
            totalScenarios = total,
            meanTargetRelativeError = meanTargetError,
            signalAccuracy = signalAccuracy,
            intervalCoverage = intervalCoverage,
            gainMeanAbsoluteError = gainMeanAbsoluteError,
            allocationExclusive = allocationExclusive,
            maxExpectedStepsToTolerance = maxSteps,
            unknownGainAbstains = unknownGainAbstains,
            falsePrecisionCount = falsePrecision,
            pass = pass,
        )
    }

    private fun syntheticKStarInput(
        petrolOnGasMs: Double,
        petrolReferenceMs: Double,
        currentFactor: Double,
        gain: PlantGain,
        suffix: String,
    ): KStarScientificInput {
        fun evidence(id: String): ResolvedScientificEvidence = ResolvedScientificEvidence(
            authorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
            role = ScientificEvidenceRole.OBSERVATION,
            evidenceIds = setOf(id),
            physicalEvidenceId = id,
            effectiveWeight = 1.0,
            provenance = setOf("SYNTHETIC_FAST_PHYSICS_GATE"),
        )

        return KStarScientificInput(
            petrolOnGas = ScientificMeasurement(
                valueMs = petrolOnGasMs,
                evidence = evidence("fast-cng-$suffix"),
            ),
            petrolReference = ScientificMeasurement(
                valueMs = petrolReferenceMs,
                evidence = evidence("fast-gas-$suffix"),
            ),
            currentFactor = currentFactor,
            gain = gain,
        )
    }

    private fun expectedStepsToTolerance(currentFactor: Double, targetFactor: Double): Int {
        val policy = LegacyAdvisorStepPolicy()
        val ideal = IdealTarget(targetFactor, MagnitudeAuthority.EMPIRICALLY_BOUNDED)
        var current = currentFactor
        repeat(20) { index ->
            if (abs(current - targetFactor) / targetFactor <= TARGET_TOLERANCE_RATIO) return maxOf(1, index)
            current = policy.selectStep(
                currentFactor = current,
                target = ideal,
                uncertainty = STEP_UNCERTAINTY,
            ).factor
        }
        return 21
    }

    private fun direction(current: Double, target: Double): EffectDirection = when {
        target > current -> EffectDirection.INCREASE
        target < current -> EffectDirection.DECREASE
        else -> EffectDirection.NEUTRAL
    }
}
