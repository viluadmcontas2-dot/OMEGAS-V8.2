package com.omegas.prohub.learning

/**
 * Registro causal dos números que podem alterar decisão científica ou ação futura.
 *
 * Classificar não transforma um baseline legado em verdade física. O objetivo é
 * exatamente o oposto: todo consumidor consegue saber de onde o número veio e
 * qual owner deve revalidá-lo antes de elevá-lo de status.
 */
enum class ScientificConstantClass {
    PROTOCOL_INVARIANT,
    PHYSICAL_INVARIANT,
    RESOURCE_BUDGET,
    OWNER_HARD_BOUND,
    CALIBRATED_POLICY,
    LEGACY_BASELINE,
    UNKNOWN,
}

data class ScientificConstant(
    val symbol: String,
    val value: String,
    val unit: String,
    val source: String,
    val consumer: String,
    val falsifier: String,
    val owner: String,
    val revision: String,
    val classification: ScientificConstantClass,
) {
    init {
        require(symbol.isNotBlank())
        require(value.isNotBlank())
        require(unit.isNotBlank())
        require(source.isNotBlank())
        require(consumer.isNotBlank())
        require(falsifier.isNotBlank())
        require(owner.isNotBlank())
        require(revision.isNotBlank())
    }
}

object ScientificConstantRegistry {
    const val REVISION = "SCI-CONST-V1-2026-08-17"

    private fun policy(
        symbol: String,
        value: Any,
        unit: String,
        consumer: String,
        falsifier: String,
    ) = ScientificConstant(
        symbol = symbol,
        value = value.toString(),
        unit = unit,
        source = "LearningTolerancePolicy current/default; owner-adjustable policy, not physical invariant",
        consumer = consumer,
        falsifier = falsifier,
        owner = "F4 Learning policy",
        revision = REVISION,
        classification = ScientificConstantClass.CALIBRATED_POLICY,
    )

    private fun legacy(
        symbol: String,
        value: Any,
        unit: String,
        consumer: String,
        falsifier: String,
    ) = ScientificConstant(
        symbol = symbol,
        value = value.toString(),
        unit = unit,
        source = "Legacy baseline carried into V8.2; not promoted to physical/native truth",
        consumer = consumer,
        falsifier = falsifier,
        owner = "F4 Scientific Constant Registry",
        revision = REVISION,
        classification = ScientificConstantClass.LEGACY_BASELINE,
    )

    fun entries(): List<ScientificConstant> {
        val p = LearningTolerancePolicy()
        return listOf(
            policy("requiredFrames", p.requiredFrames, "frames", "MotorSampleAnalyzer", "vary frame budget and verify accept/reject boundary"),
            policy("evaluationStride", p.evaluationStride, "frames", "MotorSampleAnalyzer", "vary stride and verify no duplicated decision authority"),
            policy("maximumAttemptMs", p.maximumAttemptMs, "ms", "MotorSampleAnalyzer", "boundary timeout test"),
            policy("warningGapMs", p.warningGapMs, "ms", "MotorSampleAnalyzer", "gap below/above boundary"),
            policy("breakingGapMs", p.breakingGapMs, "ms", "MotorSampleAnalyzer", "gap below/above boundary"),
            policy("toleratedSerialFailures", p.toleratedSerialFailures, "failures", "ResponseDrivenEcuEngine", "serial failure boundary test"),
            policy("hardRecoveryFailures", p.hardRecoveryFailures, "failures", "ResponseDrivenEcuEngine", "recovery escalation boundary"),
            policy("hardRecoverySilenceMs", p.hardRecoverySilenceMs, "ms", "ResponseDrivenEcuEngine", "silence escalation boundary"),
            policy("rpmCenterMinimum", p.rpmCenterMinimum, "rpm", "MotorSampleAnalyzer", "RPM center mismatch boundary"),
            policy("rpmCenterPercent", p.rpmCenterPercent, "%", "MotorSampleAnalyzer", "RPM center mismatch boundary"),
            policy("rpmOscillationMinimum", p.rpmOscillationMinimum, "rpm", "MotorSampleAnalyzer", "RPM oscillation boundary"),
            policy("rpmOscillationPercent", p.rpmOscillationPercent, "%", "MotorSampleAnalyzer", "RPM oscillation boundary"),
            policy("mapCenterBar", p.mapCenterBar, "bar", "MotorSampleAnalyzer", "MAP center mismatch boundary"),
            policy("mapOscillationBar", p.mapOscillationBar, "bar", "MotorSampleAnalyzer", "MAP oscillation boundary"),
            policy("petrolCenterMinimumMs", p.petrolCenterMinimumMs, "ms", "MotorSampleAnalyzer", "Petrol Inj center mismatch boundary"),
            policy("petrolCenterPercent", p.petrolCenterPercent, "%", "MotorSampleAnalyzer", "Petrol Inj center mismatch boundary"),
            policy("petrolOscillationPercent", p.petrolOscillationPercent, "%", "MotorSampleAnalyzer", "Petrol Inj oscillation boundary"),
            policy("strongPetrolOscillationPercent", p.strongPetrolOscillationPercent, "%", "MotorSampleAnalyzer", "strong oscillation boundary"),
            policy("pressureCenterBar", p.pressureCenterBar, "bar", "MotorSampleAnalyzer", "pressure center mismatch boundary"),
            policy("pressureOscillationBar", p.pressureOscillationBar, "bar", "MotorSampleAnalyzer", "pressure oscillation boundary"),
            policy("cutoffMinimumRpm", p.cutoffMinimumRpm, "rpm", "MotorSampleAnalyzer", "cutoff classification boundary"),
            policy("cutoffMaximumPetrolMs", p.cutoffMaximumPetrolMs, "ms", "MotorSampleAnalyzer", "cutoff classification boundary"),
            policy("cutoffMaximumMapBar", p.cutoffMaximumMapBar, "bar", "MotorSampleAnalyzer", "cutoff classification boundary"),
            policy("physicalExitFrames", p.physicalExitFrames, "frames", "MotorSampleAnalyzer", "physical exit hysteresis boundary"),
            policy("historicalRpmMinimum", p.historicalRpmMinimum, "rpm", "PetrolReferenceSelector", "same request around RPM neighborhood boundary"),
            policy("historicalRpmPercent", p.historicalRpmPercent, "%", "PetrolReferenceSelector", "same request around RPM neighborhood boundary"),
            policy("historicalMapBar", p.historicalMapBar, "bar", "PetrolReferenceSelector", "same request around MAP neighborhood boundary"),
            policy("historicalTemperatureC", p.historicalTemperatureC, "degC", "PetrolReferenceSelector", "same RPM/MAP with water context variation"),
            policy("referenceMaximumSpreadMs", p.referenceMaximumSpreadMs, "ms", "PetrolReferenceSelector", "spread below/above boundary"),
            policy("directionConsensusMinimum", p.directionConsensusMinimum, "ratio", "Learning comparison/reconciler", "opposed residual directions around boundary"),
            policy("comparisonMaximumMadMs", p.comparisonMaximumMadMs, "ms", "Learning comparison/reconciler", "MAD below/above boundary"),
            policy("comparisonMaximumGasTempSpanC", p.comparisonMaximumGasTempSpanC, "degC", "OMEGAS comparability policy", "same point with gas-temperature span boundary"),
            policy("comparisonMaximumPressureSpanBar", p.comparisonMaximumPressureSpanBar, "bar", "OMEGAS comparability policy", "same point with pressure span boundary"),
            policy("equivalenceDeadbandMs", p.equivalenceDeadbandMs, "ms", "FuelEquivalenceObjective", "zero/deadband boundary"),
            policy("equivalenceDeadbandPercent", p.equivalenceDeadbandPercent, "%", "FuelEquivalenceObjective", "zero/deadband boundary"),
            policy("confidenceSampleTarget", p.confidenceSampleTarget, "effective samples", "PetrolReferenceSelector", "support around confidence stages"),
            policy("provisionalVisits", p.provisionalVisits, "visits", "Visit confidence", "visit-count boundary"),
            policy("acceptedVisits", p.acceptedVisits, "visits", "Visit confidence", "visit-count boundary"),
            policy("confirmedVisits", p.confirmedVisits, "visits", "Visit confidence", "visit-count boundary"),
            ScientificConstant(
                "minimumWaterC.default", LearningTemperatureSettings.DEFAULT_C.toString(), "degC",
                "Owner-configurable Landi ECU water threshold", "MotorSampleAnalyzer",
                "water temperature immediately below/at/above threshold", "F4 Learning temperature policy", REVISION,
                ScientificConstantClass.CALIBRATED_POLICY,
            ),
            ScientificConstant(
                "minimumWaterC.allowed", "${LearningTemperatureSettings.MIN_ALLOWED_C}..${LearningTemperatureSettings.MAX_ALLOWED_C}", "degC",
                "Owner-configurable UI safety envelope", "LearningTemperatureSettings",
                "boundary 39/40/80/81", "F4/F9 temperature control", REVISION,
                ScientificConstantClass.OWNER_HARD_BOUND,
            ),
            legacy("selector.MAX_NEIGHBORS", 4, "regions", "PetrolReferenceSelector", "5th neighbor cannot silently change target"),
            legacy("selector.DIRECT_DISTANCE_WINDOW", 0.75, "normalized distance", "PetrolReferenceSelector", "direct cutoff boundary"),
            legacy("selector.EXTRAPOLATION_DISTANCE_WINDOW", 0.50, "normalized distance", "PetrolReferenceSelector", "extrapolation cutoff boundary"),
            legacy("selector.MAX_EXTRAPOLATION_RPM_UNITS", 3.00, "normalized units", "PetrolReferenceSelector", "RPM extrapolation boundary"),
            legacy("selector.MAX_EXTRAPOLATION_MAP_UNITS", 2.50, "normalized units", "PetrolReferenceSelector", "MAP extrapolation boundary"),
            legacy("selector.MAX_EXTRAPOLATION_WATER_UNITS", 2.00, "normalized units", "PetrolReferenceSelector", "water-context extrapolation boundary"),
            legacy("selector.HARD_DIRECT_SPREAD_MULTIPLIER", 2.50, "multiplier", "PetrolReferenceSelector", "spread rejection around multiplier"),
            legacy("selector.WATER_DISTANCE_WEIGHT", 0.25, "weight", "PetrolReferenceSelector", "same RPM/MAP with different water weight"),
            legacy("selector.CONFIDENCE_STAGE_CONFIRMED_DENSITY", 0.8, "ratio", "PetrolReferenceSelector", "density around confirmed boundary"),
            legacy("selector.CONFIDENCE_STAGE_ACCEPTED_DENSITY", 0.5, "ratio", "PetrolReferenceSelector", "density around accepted boundary"),
            legacy("selector.CONFIDENCE_STAGE_PROVISIONAL_DENSITY", 0.2, "ratio", "PetrolReferenceSelector", "density around provisional boundary"),
            ScientificConstant(
                "BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES",
                BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES.toString(),
                "samples/region",
                "Bounded recent robust-tail resource budget; not physical/native truth",
                "BoundedRobustPetrolSummary",
                "10k observations retain at most budget while totalObserved continues",
                "F4 robust petrol memory",
                REVISION,
                ScientificConstantClass.RESOURCE_BUDGET,
            ),
            ScientificConstant(
                "VisitComparisonAccumulator.MAX_VISIT_WEIGHT", VisitComparisonAccumulator.MAX_VISIT_WEIGHT.toString(), "weight",
                "Bound repeated windows from one physical visit", "VisitComparisonAccumulator",
                "10k correlated windows do not exceed visit cap", "F4 novelty/visit weighting", REVISION,
                ScientificConstantClass.RESOURCE_BUDGET,
            ),
            ScientificConstant(
                "MapK.ownerTargetRange", "120..200", "raw K",
                "Owner-requested hard bound frozen at 068R", "F8 Draft/pre-write validator",
                "119/120/200/201", "F8 Draft/Writer", REVISION, ScientificConstantClass.OWNER_HARD_BOUND,
            ),
            ScientificConstant(
                "CurveK.ownerFactorRange", "0.7..2.0", "factor",
                "Owner-requested hard bound frozen at 068R", "F8 Draft/pre-write validator",
                "0.699/0.7/2.0/2.001", "F8 Draft/Writer", REVISION, ScientificConstantClass.OWNER_HARD_BOUND,
            ),
        )
    }

    fun bySymbol(): Map<String, ScientificConstant> = entries().associateBy { it.symbol }

    fun unknownEntries(): List<ScientificConstant> =
        entries().filter { it.classification == ScientificConstantClass.UNKNOWN }
}
