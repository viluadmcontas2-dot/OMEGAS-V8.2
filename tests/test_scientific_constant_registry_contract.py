from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningToleranceSettings.kt"
REGISTRY = ROOT / "app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt"
SELECTOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt"


class ScientificConstantRegistryContract(unittest.TestCase):
    def test_every_learning_policy_field_is_registered(self):
        policy = POLICY.read_text("utf-8")
        registry = REGISTRY.read_text("utf-8")
        header = policy.split(") {", 1)[0]
        fields = re.findall(r"val\s+(\w+)\s*:\s*[^=,]+\s*=", header)
        self.assertGreater(len(fields), 30)
        missing = [field for field in fields if f'policy("{field}"' not in registry]
        self.assertEqual([], missing)

    def test_material_selector_constants_have_registry_symbols(self):
        selector = SELECTOR.read_text("utf-8")
        registry = REGISTRY.read_text("utf-8")
        names = re.findall(r"private const val (MAX_NEIGHBORS|DIRECT_DISTANCE_WINDOW|EXTRAPOLATION_DISTANCE_WINDOW|MAX_EXTRAPOLATION_RPM_UNITS|MAX_EXTRAPOLATION_MAP_UNITS|MAX_EXTRAPOLATION_WATER_UNITS|HARD_DIRECT_SPREAD_MULTIPLIER)\s*=", selector)
        self.assertEqual(7, len(names))
        for name in names:
            self.assertIn(f'"selector.{name}"', registry)
        for symbol in (
            "selector.WATER_DISTANCE_WEIGHT",
            "selector.CONFIDENCE_STAGE_CONFIRMED_DENSITY",
            "selector.CONFIDENCE_STAGE_ACCEPTED_DENSITY",
            "selector.CONFIDENCE_STAGE_PROVISIONAL_DENSITY",
        ):
            self.assertIn(f'"{symbol}"', registry)

    def test_registry_has_no_unknown_entry_and_required_metadata(self):
        source = REGISTRY.read_text("utf-8")
        self.assertIn("symbol: String", source)
        self.assertIn("value: String", source)
        self.assertIn("unit: String", source)
        self.assertIn("source: String", source)
        self.assertIn("consumer: String", source)
        self.assertIn("falsifier: String", source)
        self.assertIn("owner: String", source)
        self.assertIn("revision: String", source)
        # UNKNOWN exists as a legal classification, but V1 must not instantiate one.
        instantiated = re.findall(r"ScientificConstantClass\.UNKNOWN", source)
        self.assertEqual(1, len(instantiated), "UNKNOWN may only appear in unknownEntries() filtering")


if __name__ == "__main__":
    unittest.main()
