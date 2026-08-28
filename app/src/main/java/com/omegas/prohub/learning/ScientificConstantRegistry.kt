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
    const val REVISION = "SCI-CONST-V2-2026-08-18"

    private fun constant(
        symbol: String,
        value: Any,
        unit: String,
        source: String,
        consumer: String,
        falsifier: String,
        owner: String,
        classification: ScientificConstantClass,
    ) = ScientificConstant(
        symbol = symbol,
        value = value.toString(),
        unit = unit,
        source = source,
        consumer = consumer,
        falsifier = falsifier,
        owner = owner,
        revision = REVISION,
        classification = classification,
    )

    private fun policy(
        symbol: String,
        value: Any,
        unit: String,
        consumer: String,
        falsifier: String,
    ) = constant(
        symbol = symbol,
        value = value,
        unit = unit,
        source = "LearningTolerancePolicy current/default; owner-adjustable policy, not physical invariant",
        consumer = consumer,
        falsifier = falsifier,
        owner = "F4 Learning policy",
        classification = ScientificConstantClass.CALIBRATED_POLICY,
    )

    private fun legacy(
        symbol: String,
        value: Any,
        unit: String,
        consumer: String,
        falsifier: String,
        source: String = "Legacy baseline carried into V8.2; not promoted to physical/native truth",
        owner: String = "F4 Scientific Constant Registry",
    ) = constant(
        symbol = symbol,
        value = value,
        unit = unit,
        source = source,
        consumer = consumer,
        falsifier = falsifier,
        owner = owner,
        classification = ScientificConstantClass.LEGACY_BASELINE,
    )

    private fun resource(
        symbol: String,
        value: Any,
        unit: String,
        consumer: String,
        falsifier: String,
        source: String = "Bounded runtime/persistence resource budget; not physical/native truth",
        owner: String = "F4/F11 resource budget",
    ) = constant(
        symbol = symbol,
        value = value,
        unit = unit,
        source = source,
        consumer = consumer,
        falsifier = falsifier,
        owner = owner,
        classification = ScientificConstantClass.RESOURCE_BUDGET,
    )

    fun entries(): List<ScientificConstant> {
        val p = LearningTolerancePolicy()
        return listOf(
            // Owner-adjustable LearningTolerancePolicy fields.
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
            policy("physicalExitFrames", p.physicalExitFrames, "frames", "MotorLearningMemory visit lifecycle", "physical exit hysteresis boundary"),
            policy("historicalRpmMinimum", p.historicalRpmMinimum, "rpm", "PetrolReferenceSelector", "same request around RPM neighborhood boundary"),
            policy("historicalRpmPercent", p.historicalRpmPercent, "%", "PetrolReferenceSelector", "same request around RPM neighborhood boundary"),
            policy("historicalMapBar", p.historicalMapBar, "bar", "PetrolReferenceSelector", "same request around MAP neighborhood boundary"),
            policy("historicalTemperatureC", p.historicalTemperatureC, "degC", "PetrolReferenceSelector", "same RPM/MAP with water context variation"),
            policy("referenceMaximumSpreadMs", p.referenceMaximumSpreadMs, "ms", "PetrolReferenceSelector", "spread below/above boundary"),
            policy("directionConsensusMinimum", p.directionConsensusMinimum, "ratio", "Learning comparison/reconciler", "opposed residual directions around boundary"),
            policy("comparisonMaximumMadMs", p.comparisonMaximumMadMs, "ms", "Learning comparison/reconciler", "MAD below/above boundary"),
            policy("comparisonMaximumGasTempSpanC", p.comparisonMaximumGasTempSpanC, "degC", "OMEGAS comparability policy", "same point with gas-temperature span boundary"),
            policy("comparisonMaximumPressureSpanBar", p.comparisonMaximumPressureSpanBar, "bar", "OMEGAS comparability policy", "same point with pressure span boundary"),
            policy("equivalenceDeadbandMs", p.equivalenceDeadbandMs, "ms", "FuelEquivalenceObjective + legacy comparison path", "zero/deadband boundary"),
            policy("equivalenceDeadbandPercent", p.equivalenceDeadbandPercent, "%", "FuelEquivalenceObjective + legacy comparison path", "zero/deadband boundary"),
            policy("confidenceSampleTarget", p.confidenceSampleTarget, "effective samples", "PetrolReferenceSelector + LearningRegion", "support around confidence stages"),
            policy("provisionalVisits", p.provisionalVisits, "visits", "VisitConfidence", "visit-count boundary"),
            policy("acceptedVisits", p.acceptedVisits, "visits", "VisitConfidence", "visit-count boundary"),
            policy("confirmedVisits", p.confirmedVisits, "visits", "VisitConfidence", "visit-count boundary"),

            constant(
                "minimumWaterC.default", LearningTemperatureSettings.DEFAULT_C, "degC",
                "Owner-configurable Landi ECU water threshold", "MotorSampleAnalyzer",
                "water temperature immediately below/at/above threshold", "F4 Learning temperature policy",
                ScientificConstantClass.CALIBRATED_POLICY,
            ),
            constant(
                "minimumWaterC.allowed", "${LearningTemperatureSettings.MIN_ALLOWED_C}..${LearningTemperatureSettings.MAX_ALLOWED_C}", "degC",
                "Owner-configurable UI safety envelope", "LearningTemperatureSettings",
                "boundary 39/40/80/81", "F4/F9 temperature control",
                ScientificConstantClass.OWNER_HARD_BOUND,
            ),

            // PetrolReferenceSelector literals discovered by 069. They remain named
            // legacy baselines until 070A–084A calibrate/replace decision policy.
            legacy("selector.MAX_NEIGHBORS", 4, "regions", "PetrolReferenceSelector", "5th neighbor cannot silently change target"),
            legacy("selector.DIRECT_DISTANCE_WINDOW", 0.75, "normalized distance", "PetrolReferenceSelector", "direct cutoff boundary"),
            legacy("selector.EXTRAPOLATION_DISTANCE_WINDOW", 0.50, "normalized distance", "PetrolReferenceSelector", "extrapolation cutoff boundary"),
            legacy("selector.MAX_EXTRAPOLATION_RPM_UNITS", 3.00, "normalized units", "PetrolReferenceSelector", "RPM extrapolation boundary"),
            legacy("selector.MAX_EXTRAPOLATION_MAP_UNITS", 2.50, "normalized units", "PetrolReferenceSelector", "MAP extrapolation boundary"),
            legacy("selector.MAX_EXTRAPOLATION_WATER_UNITS", 2.00, "normalized units", "PetrolReferenceSelector", "water-context extrapolation boundary"),
            legacy("selector.HARD_DIRECT_SPREAD_MULTIPLIER", 2.50, "multiplier", "PetrolReferenceSelector", "spread rejection around multiplier"),
            legacy("selector.UNKNOWN_TEMPERATURE_C", -273.15, "degC sentinel", "PetrolReferenceSelector", "unknown sentinel versus real water value"),
            legacy("selector.MIN_REALISTIC_WATER_C", -80.0, "degC", "PetrolReferenceSelector", "water knownness around lower plausibility boundary"),
            legacy("selector.MIN_VALID_PETROL_MS", 0.05, "ms", "PetrolReferenceSelector", "petrol reference immediately below/above validity boundary"),
            legacy("selector.DIRECT_AXIS_UNIT_LIMIT", 1.0, "normalized units", "PetrolReferenceSelector", "direct versus extrapolated candidate at normalized axis boundary"),
            legacy("selector.MIN_CONFIDENCE_WEIGHT", 0.05, "ratio", "PetrolReferenceSelector", "region confidence floor sensitivity"),
            legacy("selector.GAUSSIAN_DISTANCE_HALF", 0.5, "kernel coefficient", "PetrolReferenceSelector", "distance-kernel holdout comparison"),
            legacy("selector.INVERSE_DISTANCE_OFFSET", 0.15, "normalized distance", "PetrolReferenceSelector", "near-zero distance weighting sensitivity"),
            legacy("selector.MIN_REFERENCE_WEIGHT", 1e-9, "weight", "PetrolReferenceSelector", "weight underflow/zero boundary"),
            legacy("selector.MIN_SPREAD_LIMIT_MS", 0.05, "ms", "PetrolReferenceSelector", "spread floor sensitivity"),
            legacy("selector.NEAREST_DOMINANCE_FALLBACK", 0.60, "ratio", "PetrolReferenceSelector", "fallback/reject boundary with divergent neighbors"),
            legacy("selector.DISTANCE_QUALITY_DECAY", 0.35, "decay coefficient", "PetrolReferenceSelector", "distance-quality holdout comparison"),
            legacy("selector.MIN_DISTANCE_QUALITY", 0.10, "ratio", "PetrolReferenceSelector", "distance quality floor sensitivity"),
            legacy("selector.MIN_SPREAD_QUALITY", 0.08, "ratio", "PetrolReferenceSelector", "spread quality floor sensitivity"),
            legacy("selector.EXTRAPOLATION_QUALITY_FACTOR", 0.35, "ratio", "PetrolReferenceSelector", "extrapolated versus direct quality calibration"),
            legacy("selector.MIN_RPM_SCALE", 1.0, "rpm", "PetrolReferenceSelector", "normalized RPM denominator floor"),
            legacy("selector.MIN_MAP_SCALE", 0.001, "bar", "PetrolReferenceSelector", "normalized MAP denominator floor"),
            legacy("selector.MIN_WATER_SCALE", 1.0, "degC", "PetrolReferenceSelector", "normalized water denominator floor"),
            legacy("selector.WATER_DISTANCE_WEIGHT", 0.25, "weight", "PetrolReferenceSelector", "same RPM/MAP with different water weight"),
            legacy("selector.CONFIDENCE_STAGE_CONFIRMED_DENSITY", 0.8, "ratio", "PetrolReferenceSelector", "density around confirmed boundary"),
            legacy("selector.CONFIDENCE_STAGE_ACCEPTED_DENSITY", 0.5, "ratio", "PetrolReferenceSelector", "density around accepted boundary"),
            legacy("selector.CONFIDENCE_STAGE_PROVISIONAL_DENSITY", 0.2, "ratio", "PetrolReferenceSelector", "density around provisional boundary"),
            legacy("selector.CONFIDENCE_STAGE_CONFIRMED_VARIANCE", 0.5, "variance multiplier", "PetrolReferenceSelector", "confirmed variance boundary"),

            // VisitConfidence adaptive visit table is explicitly a legacy baseline.
            // 086A forbids treating 3/5/7/10 as universal tolls.
            legacy("visitConfidence.EPSILON", 1e-9, "ratio", "VisitConfidence", "zero spread-limit numeric stability"),
            legacy("visitConfidence.STRONG_CONSENSUS", 0.95, "ratio", "VisitConfidence.adaptiveTarget", "holdout around strong-consensus boundary"),
            legacy("visitConfidence.STRONG_REPEATABILITY", 0.90, "ratio", "VisitConfidence.adaptiveTarget", "holdout around strong-repeatability boundary"),
            legacy("visitConfidence.STRONG_TARGET_VISITS", 3, "visits", "VisitConfidence.adaptiveTarget", "contradict/repeatable fixture around 3-visit target"),
            legacy("visitConfidence.GOOD_CONSENSUS", 0.80, "ratio", "VisitConfidence.adaptiveTarget", "holdout around good-consensus boundary"),
            legacy("visitConfidence.GOOD_REPEATABILITY", 0.70, "ratio", "VisitConfidence.adaptiveTarget", "holdout around good-repeatability boundary"),
            legacy("visitConfidence.GOOD_TARGET_VISITS", 5, "visits", "VisitConfidence.adaptiveTarget", "contradict/repeatable fixture around 5-visit target"),
            legacy("visitConfidence.WEAK_CONSENSUS", 0.60, "ratio", "VisitConfidence.adaptiveTarget", "holdout around weak-consensus boundary"),
            legacy("visitConfidence.WEAK_REPEATABILITY", 0.45, "ratio", "VisitConfidence.adaptiveTarget", "holdout around weak-repeatability boundary"),
            legacy("visitConfidence.WEAK_TARGET_VISITS", 7, "visits", "VisitConfidence.adaptiveTarget", "contradict/repeatable fixture around 7-visit target"),
            legacy("visitConfidence.NOISY_TARGET_VISITS", 10, "visits", "VisitConfidence.adaptiveTarget", "noisy fixture target is not a universal production toll"),
            legacy("visitConfidence.UNCERTAINTY_CENTER_SCALE", 0.5, "ratio", "VisitConfidence.adaptiveTarget", "confidence-band calibration"),
            legacy("visitConfidence.BAND_RADIUS_BASE", 0.05, "ratio", "VisitConfidence.adaptiveTarget", "confidence-band radius calibration"),
            legacy("visitConfidence.BAND_RADIUS_UNCERTAINTY_SCALE", 0.20, "ratio", "VisitConfidence.adaptiveTarget", "confidence-band radius calibration"),
            legacy("visitConfidence.BAND_RADIUS_MAX", 0.25, "ratio", "VisitConfidence.adaptiveTarget", "confidence-band maximum calibration"),
            legacy("visitConfidence.PROGRESS_FLOOR", 0.05, "ratio", "VisitConfidence.evaluate", "small-support confidence floor sensitivity"),
            legacy("visitConfidence.GEOMETRIC_FLOOR", 0.0001, "ratio", "VisitConfidence.evaluate", "geometric-mean underflow sensitivity"),
            legacy("visitConfidence.CONFIRMED_REPEATABILITY", 0.60, "ratio", "VisitConfidence.evaluate", "confirmed stage repeatability boundary"),
            legacy("visitConfidence.CONFIRMED_CONSENSUS", 0.75, "ratio", "VisitConfidence.evaluate", "confirmed stage consensus boundary"),
            legacy("visitConfidence.ACCEPTED_REPEATABILITY", 0.40, "ratio", "VisitConfidence.evaluate", "accepted stage repeatability boundary"),
            legacy("visitConfidence.ACCEPTED_CONSENSUS", 0.60, "ratio", "VisitConfidence.evaluate", "accepted stage consensus boundary"),

            // Memory/reference-model literals that materially affect target, confidence or suggestion.
            legacy("memory.PREVIEW_SUGGESTION_GAIN", 0.35, "gain", "MotorLearningMemory.previewKWrite", "same evidence with gain perturbation"),
            legacy("memory.PREVIEW_LEGACY_MIN_K", 50.0, "raw K", "MotorLearningMemory.previewKWrite", "legacy preview lower bound versus owner 068R bound", owner = "F8 Draft/Writer"),
            legacy("memory.PREVIEW_LEGACY_MAX_K", 255.0, "raw K", "MotorLearningMemory.previewKWrite", "legacy preview upper bound versus owner 068R bound", owner = "F8 Draft/Writer"),
            legacy("memory.LEGACY_MIN_REFERENCE_MS", 0.05, "ms", "MotorLearningMemory.compare + FuelComparison.consolidate", "zero/small denominator must migrate to 070/089 objective", owner = "070/089 equivalence objective"),
            legacy("memory.SUGGESTED_DELTA_GAIN_PERCENT", 35.0, "% per error ratio", "MotorLearningMemory.comparisonStatus", "suggestion magnitude holdout"),
            legacy("memory.SUGGESTED_DELTA_LIMIT_PERCENT", 5.0, "%", "MotorLearningMemory.comparisonStatus", "suggestion clamp boundary"),
            legacy("memory.REGION_SAMPLE_QUALITY_FLOOR", 0.10, "ratio", "LearningRegion.update/merge", "low-quality sample weighting sensitivity"),
            legacy("memory.REGION_DURATION_BASE_WEIGHT", 0.25, "ratio", "LearningRegion.update", "dwell weighting decomposition"),
            legacy("memory.REGION_DURATION_DYNAMIC_WEIGHT", 0.75, "ratio", "LearningRegion.update", "dwell weighting decomposition"),
            legacy("memory.REGION_VARIANCE_CONFIDENCE_FLOOR", 0.10, "ratio", "LearningRegion.confidence", "variance confidence floor sensitivity"),
            legacy("memory.REGION_SAMPLE_CONFIDENCE_FLOOR", 0.05, "ratio", "LearningRegion.confidence", "small sample confidence floor sensitivity"),
            legacy("memory.REGION_QUALITY_CONFIDENCE_FLOOR", 0.10, "ratio", "LearningRegion.confidence", "quality confidence floor sensitivity"),
            legacy("memory.GEOMETRIC_MEAN_FLOOR", 0.0001, "ratio", "LearningRegion/ComparisonEvidence confidence", "geometric confidence underflow sensitivity"),
            legacy("memory.COMPARISON_DISPLAY_CONFIRMED", 1.0, "ratio", "ComparisonEvidence.toJson", "display confidence mapping"),
            legacy("memory.COMPARISON_DISPLAY_ACCEPTED", 0.75, "ratio", "ComparisonEvidence.toJson", "display confidence mapping"),
            legacy("memory.COMPARISON_DISPLAY_PROVISIONAL", 0.50, "ratio", "ComparisonEvidence.toJson", "display confidence mapping"),
            legacy("memory.COMPARISON_DISPLAY_OBSERVED", 0.20, "ratio", "ComparisonEvidence.toJson", "display confidence mapping"),

            // Robust estimator choices: named, but not claimed as OEM/physical invariants.
            legacy("robust.MEDIAN_QUANTILE", 0.50, "quantile", "BoundedRobustPetrolSummary", "compare median estimator against held-out petrol reference"),
            legacy("robust.IQR_LOW_QUANTILE", 0.25, "quantile", "BoundedRobustPetrolSummary", "dispersion estimator holdout"),
            legacy("robust.IQR_HIGH_QUANTILE", 0.75, "quantile", "BoundedRobustPetrolSummary", "dispersion estimator holdout"),
            legacy(
                "novelty.FULLY_NEW_FRACTION",
                ContinuousWindowNovelty.FULLY_NEW_FRACTION,
                "fraction",
                "SignalLearningStore",
                "8-frame boundary: 5 new remains correlated; 6 new is fully-new",
            ),

            // Resource budgets cannot be confused with scientific truth, but they
            // can affect retained provenance/evidence and therefore are registered.
            resource("BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES", BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES, "samples/region", "BoundedRobustPetrolSummary", "10k observations retain at most budget while totalObserved continues"),
            resource("VisitComparisonAccumulator.MAX_VISIT_WEIGHT", VisitComparisonAccumulator.MAX_VISIT_WEIGHT, "weight", "VisitComparisonAccumulator", "10k correlated windows do not exceed visit cap"),
            resource("LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS", LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS, "snapshots", "SignalLearningStore", "snapshot cardinality stabilizes under long import"),
            resource("LearningEvidenceBudget.MAX_NATIVE_ANCHORS", LearningEvidenceBudget.MAX_NATIVE_ANCHORS, "anchors", "SignalLearningStore", "native anchor cardinality stabilizes"),
            resource("LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS", LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS, "visits", "SignalLearningStore", "visit accumulator cardinality stabilizes"),
            resource("LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES", LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES, "entries", "SignalLearningStore", "provenance history remains bounded"),
            resource("LearningEvidenceBudget.MAX_PERSISTED_BYTES", LearningEvidenceBudget.MAX_PERSISTED_BYTES, "bytes", "SignalLearningStore evidence sidecar", "oversize evidence sidecar compacts or records explicit failure"),
            resource("LearningMemoryBudget.MAX_REGION_VISIT_IDS", LearningMemoryBudget.MAX_REGION_VISIT_IDS, "ids/region", "MotorLearningMemory persistence", "visit IDs compact while exact visitCount remains"),
            resource("LearningMemoryBudget.MAX_REGION_SESSION_IDS", LearningMemoryBudget.MAX_REGION_SESSION_IDS, "ids/region", "MotorLearningMemory persistence", "session IDs compact while exact sessionCount remains"),
            resource("LearningMemoryBudget.TARGET_PERSISTED_BYTES", LearningMemoryBudget.TARGET_PERSISTED_BYTES, "bytes", "MotorLearningMemory persistence", "large memory compacts provenance before exceeding target"),
            resource("LearningMemoryBudget.PROVENANCE_LEVELS", "16/8 -> 8/4 -> 4/2 -> 0/0", "visit/session ids", "MotorLearningMemory persistence", "payload crossing byte target steps down provenance only"),
            resource("MotorLearningMemory.MAX_COMPARISONS", 600, "comparisons", "MotorLearningMemory", "long trajectory retains bounded comparison deque"),
            resource("MotorLearningMemory.MAX_SESSIONS", 100, "sessions", "MotorLearningMemory", "long reconnect sequence retains bounded session deque"),
            resource("MotorLearningMemory.MAX_REGIONS", 2000, "regions", "MotorLearningMemory", "long trajectory retains bounded region list"),

            // Owner-hard bounds frozen by 068R; these are the future write envelope.
            constant(
                "MapK.ownerTargetRange", "120..200", "raw K",
                "Owner-requested hard bound frozen at 068R", "F8 Draft/pre-write validator",
                "119/120/200/201", "F8 Draft/Writer", ScientificConstantClass.OWNER_HARD_BOUND,
            ),
            constant(
                "CurveK.ownerFactorRange", "0.7..2.0", "factor",
                "Owner-requested hard bound frozen at 068R", "F8 Draft/pre-write validator",
                "0.699/0.7/2.0/2.001", "F8 Draft/Writer", ScientificConstantClass.OWNER_HARD_BOUND,
            ),
        )
    }

    fun bySymbol(): Map<String, ScientificConstant> = entries().associateBy { it.symbol }

    fun duplicateSymbols(): Set<String> = entries()
        .groupingBy { it.symbol }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    fun unknownEntries(): List<ScientificConstant> =
        entries().filter { it.classification == ScientificConstantClass.UNKNOWN }

    /**
     * Baseline legado/UNKNOWN nunca deve ser descrito como verdade física ou owner-hard-bound.
     * Owners posteriores podem consumi-lo como fixture/default explicitamente rotulado.
     */
    fun productionAuthority(symbol: String): ScientificConstantClass? = bySymbol()[symbol]?.classification
}
