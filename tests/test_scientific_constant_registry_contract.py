from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
LEARNING = ROOT / "app/src/main/java/com/omegas/prohub/learning"
POLICY = LEARNING / "LearningToleranceSettings.kt"
REGISTRY = LEARNING / "ScientificConstantRegistry.kt"
SELECTOR = LEARNING / "PetrolReferenceSelector.kt"
VISIT_CONFIDENCE = LEARNING / "VisitConfidence.kt"
MEMORY = LEARNING / "MotorLearningMemory.kt"
OBJECTIVE = LEARNING / "FuelEquivalenceObjective.kt"
ROBUST = LEARNING / "BoundedRobustPetrolSummary.kt"
COVERAGE = LEARNING / "UsefulCoverageMetric.kt"
EVIDENCE_BUDGET = LEARNING / "LearningEvidenceBudget.kt"
MEMORY_BUDGET = LEARNING / "LearningMemoryBudget.kt"
SCHEMA_MAP = ROOT / "docs/science/phase04-evidence-schema-map.md"


def material_decision_literals(line: str) -> set[str]:
    """Falsificador mínimo: números em uma condição/limite não podem passar invisíveis."""
    decision_tokens = (">=", "<=", "coerceIn(", "coerceAtLeast(", "coerceAtMost(", "exp(", "*", "/")
    if not any(token in line for token in decision_tokens):
        return set()
    return set(re.findall(r"(?<![A-Za-z0-9_])-?(?:\d+\.\d+|\d+e-\d+)(?![A-Za-z0-9_])", line, flags=re.I))


class ScientificConstantRegistryContract(unittest.TestCase):
    def setUp(self):
        self.registry = REGISTRY.read_text("utf-8")

    def assert_symbol(self, symbol: str):
        self.assertIn(f'"{symbol}"', self.registry, f"missing classified scientific symbol {symbol}")

    def test_every_learning_policy_field_is_registered(self):
        policy = POLICY.read_text("utf-8")
        header = policy.split(") {", 1)[0]
        fields = re.findall(r"val\s+(\w+)\s*:\s*[^=,]+\s*=", header)
        self.assertGreater(len(fields), 30)
        missing = [field for field in fields if f'policy("{field}"' not in self.registry]
        self.assertEqual([], missing)

    def test_material_selector_constants_and_raw_decision_baselines_are_registered(self):
        selector = SELECTOR.read_text("utf-8")
        names = re.findall(
            r"private const val (MAX_NEIGHBORS|DIRECT_DISTANCE_WINDOW|EXTRAPOLATION_DISTANCE_WINDOW|MAX_EXTRAPOLATION_RPM_UNITS|MAX_EXTRAPOLATION_MAP_UNITS|MAX_EXTRAPOLATION_WATER_UNITS|HARD_DIRECT_SPREAD_MULTIPLIER)\s*=",
            selector,
        )
        self.assertEqual(7, len(names))
        for name in names:
            self.assert_symbol(f"selector.{name}")

        required = {
            "selector.UNKNOWN_TEMPERATURE_C": "UNKNOWN_TEMPERATURE_C = -273.15",
            "selector.MIN_REALISTIC_WATER_C": "MIN_REALISTIC_WATER_C = -80.0",
            "selector.MIN_VALID_PETROL_MS": "it.petrolMs > 0.05",
            "selector.DIRECT_AXIS_UNIT_LIMIT": "it.rpmUnits <= 1.0",
            "selector.MIN_CONFIDENCE_WEIGHT": "confidence.coerceIn(0.05, 1.0)",
            "selector.GAUSSIAN_DISTANCE_HALF": "exp(-0.5 * item.distance * item.distance)",
            "selector.INVERSE_DISTANCE_OFFSET": "1.0 / (0.15 + item.distance)",
            "selector.MIN_REFERENCE_WEIGHT": "coerceAtLeast(1e-9)",
            "selector.MIN_SPREAD_LIMIT_MS": "referenceMaximumSpreadMs.coerceAtLeast(0.05)",
            "selector.NEAREST_DOMINANCE_FALLBACK": "nearestDominance < 0.60",
            "selector.DISTANCE_QUALITY_DECAY": "exp(-0.35 * closest.distance)",
            "selector.MIN_DISTANCE_QUALITY": "coerceIn(0.10, 1.0)",
            "selector.MIN_SPREAD_QUALITY": "coerceIn(0.08, 1.0)",
            "selector.EXTRAPOLATION_QUALITY_FACTOR": "if (extrapolated) 0.35 else 1.0",
            "selector.MIN_MAP_SCALE": "historicalMapBar.coerceAtLeast(0.001)",
            "selector.WATER_DISTANCE_WEIGHT": "0.25 * waterUnits * waterUnits",
            "selector.CONFIDENCE_STAGE_CONFIRMED_DENSITY": "confidenceSampleTarget * 0.8",
            "selector.CONFIDENCE_STAGE_ACCEPTED_DENSITY": "confidenceSampleTarget * 0.5",
            "selector.CONFIDENCE_STAGE_PROVISIONAL_DENSITY": "confidenceSampleTarget * 0.2",
            "selector.CONFIDENCE_STAGE_CONFIRMED_VARIANCE": "referenceMaximumSpreadMs * policy.referenceMaximumSpreadMs * 0.5",
        }
        for symbol, snippet in required.items():
            self.assertIn(snippet, selector, f"audited selector decision path moved: {symbol}")
            self.assert_symbol(symbol)

    def test_visit_confidence_legacy_table_is_explicit_not_a_hidden_universal_toll(self):
        source = VISIT_CONFIDENCE.read_text("utf-8")
        cases = {
            "visitConfidence.STRONG_CONSENSUS": "safeConsensus >= 0.95",
            "visitConfidence.STRONG_REPEATABILITY": "repeatability >= 0.90",
            "visitConfidence.STRONG_TARGET_VISITS": "-> 3",
            "visitConfidence.GOOD_CONSENSUS": "safeConsensus >= 0.80",
            "visitConfidence.GOOD_REPEATABILITY": "repeatability >= 0.70",
            "visitConfidence.GOOD_TARGET_VISITS": "-> 5",
            "visitConfidence.WEAK_CONSENSUS": "safeConsensus >= 0.60",
            "visitConfidence.WEAK_REPEATABILITY": "repeatability >= 0.45",
            "visitConfidence.WEAK_TARGET_VISITS": "-> 7",
            "visitConfidence.NOISY_TARGET_VISITS": "else -> 10",
            "visitConfidence.CONFIRMED_REPEATABILITY": "repeatability >= 0.60",
            "visitConfidence.CONFIRMED_CONSENSUS": "safeConsensus >= 0.75",
            "visitConfidence.ACCEPTED_REPEATABILITY": "repeatability >= 0.40",
            "visitConfidence.ACCEPTED_CONSENSUS": "safeConsensus >= 0.60",
        }
        for symbol, snippet in cases.items():
            self.assertIn(snippet, source)
            self.assert_symbol(symbol)
        self.assertIn('classification = ScientificConstantClass.LEGACY_BASELINE', self.registry)

    def test_memory_decision_literals_found_by_069_are_registered_without_changing_math(self):
        source = MEMORY.read_text("utf-8")
        objective = OBJECTIVE.read_text("utf-8")
        cases = {
            "memory.PREVIEW_SUGGESTION_GAIN": "1.0 + 0.35 * confidence",
            "memory.PREVIEW_LEGACY_MIN_K": ".coerceIn(50.0, 255.0)",
            "memory.PREVIEW_LEGACY_MAX_K": ".coerceIn(50.0, 255.0)",
            "memory.LEGACY_MIN_REFERENCE_MS": "LEGACY_MIN_REFERENCE_MS = 0.05",
            "memory.SUGGESTED_DELTA_GAIN_PERCENT": "medianErrorRatio * 35.0",
            "memory.SUGGESTED_DELTA_LIMIT_PERCENT": ".coerceIn(-5.0, 5.0)",
            "memory.REGION_SAMPLE_QUALITY_FLOOR": "max(0.10, sample.quality)",
            "memory.REGION_DURATION_BASE_WEIGHT": "(0.25 + 0.75 * durationWeight)",
            "memory.REGION_DURATION_DYNAMIC_WEIGHT": "(0.25 + 0.75 * durationWeight)",
            "memory.REGION_VARIANCE_CONFIDENCE_FLOOR": ".coerceIn(0.1, 1.0)",
            "memory.REGION_SAMPLE_CONFIDENCE_FLOOR": "samplePart.coerceAtLeast(0.05)",
            "memory.REGION_QUALITY_CONFIDENCE_FLOOR": "qualityMean.coerceIn(0.10, 1.0)",
            "memory.GEOMETRIC_MEAN_FLOOR": "value.coerceIn(0.0001, 1.0)",
        }
        for symbol, snippet in cases.items():
            self.assertIn(snippet, source, f"audited memory decision path moved: {symbol}")
            self.assert_symbol(symbol)
        self.assertIn("minimumReferenceMs = LEGACY_MIN_REFERENCE_MS", source)
        self.assertIn("referenceMs < minimumReferenceMs", objective)

    def test_resource_budgets_that_can_change_retained_evidence_are_registered(self):
        evidence_budget = EVIDENCE_BUDGET.read_text("utf-8")
        memory_budget = MEMORY_BUDGET.read_text("utf-8")
        memory = MEMORY.read_text("utf-8")
        for name in (
            "MAX_NATIVE_SNAPSHOTS",
            "MAX_NATIVE_ANCHORS",
            "MAX_VISIT_ACCUMULATORS",
            "MAX_PROVENANCE_ENTRIES",
            "MAX_PERSISTED_BYTES",
        ):
            self.assertRegex(evidence_budget, rf"const val {name}\s*=")
            self.assert_symbol(f"LearningEvidenceBudget.{name}")
        for name in ("MAX_REGION_VISIT_IDS", "MAX_REGION_SESSION_IDS", "TARGET_PERSISTED_BYTES"):
            self.assertRegex(memory_budget, rf"const val {name}\s*=")
            self.assert_symbol(f"LearningMemoryBudget.{name}")
        for name in ("MAX_COMPARISONS", "MAX_SESSIONS", "MAX_REGIONS"):
            self.assertRegex(memory, rf"private const val {name}\s*=")
            self.assert_symbol(f"MotorLearningMemory.{name}")
        self.assert_symbol("LearningMemoryBudget.PROVENANCE_LEVELS")
        self.assertIn("ScientificConstantClass.RESOURCE_BUDGET", self.registry)

    def test_robust_estimator_and_useful_coverage_gates_are_not_anonymous(self):
        robust = ROBUST.read_text("utf-8")
        coverage = COVERAGE.read_text("utf-8")
        self.assertIn("quantile(0.50)", robust)
        self.assertIn("quantile(0.25)", robust)
        self.assertIn("quantile(0.75)", robust)
        self.assert_symbol("robust.MEDIAN_QUANTILE")
        self.assert_symbol("robust.IQR_LOW_QUANTILE")
        self.assert_symbol("robust.IQR_HIGH_QUANTILE")
        self.assertIn("petrol.isFinite() && petrol > 0.05", coverage)
        self.assert_symbol("selector.MIN_VALID_PETROL_MS")

    def test_registry_has_required_metadata_no_unknown_instance_and_unique_symbols_api(self):
        for field in ("symbol", "value", "unit", "source", "consumer", "falsifier", "owner", "revision"):
            self.assertIn(f"val {field}: String", self.registry)
        self.assertIn("PROTOCOL_INVARIANT", self.registry)
        self.assertIn("PHYSICAL_INVARIANT", self.registry)
        self.assertIn("RESOURCE_BUDGET", self.registry)
        self.assertIn("OWNER_HARD_BOUND", self.registry)
        self.assertIn("CALIBRATED_POLICY", self.registry)
        self.assertIn("LEGACY_BASELINE", self.registry)
        self.assertIn("UNKNOWN", self.registry)
        self.assertNotRegex(self.registry, r"classification\s*=\s*ScientificConstantClass\.UNKNOWN")
        self.assertIn("fun duplicateSymbols(): Set<String>", self.registry)
        self.assertIn("fun unknownEntries(): List<ScientificConstant>", self.registry)

    def test_schema_map_covers_provenance_geometry_environment_and_identity(self):
        schema = SCHEMA_MAP.read_text("utf-8")
        for required in (
            "producer → field → consumer",
            "RuntimeTelemetryFrame",
            "SampleDecision",
            "EvidenceProvenance",
            "PetrolReferenceSelector.Result",
            "selectedRegionContexts",
            "LearningCalibrationBinding",
            "LearningGridProjection",
            "CalibrationIdentity",
            "gasC",
            "pressureDiffBar",
            "reference IDs",
        ):
            self.assertIn(required, schema)

    def test_detector_flags_an_unclassified_synthetic_decision_literal(self):
        synthetic = "if (confidence >= 0.37) return ACTIONABLE"
        detected = material_decision_literals(synthetic)
        self.assertEqual({"0.37"}, detected)
        self.assertNotIn("0.37", self.registry)


if __name__ == "__main__":
    unittest.main()
