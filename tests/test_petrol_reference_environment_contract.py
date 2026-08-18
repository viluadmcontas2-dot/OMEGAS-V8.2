from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SELECTOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt"
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
ANALYZER = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt"


class PetrolReferenceEnvironmentContract(unittest.TestCase):
    def test_selector_preserves_water_gas_temperature_and_pressure_context(self):
        source = SELECTOR.read_text("utf-8")
        for token in (
            "EnvironmentalContext(",
            "waterC: Double?",
            "waterFreshness: ContextFreshness",
            "gasTemperatureC: Double?",
            "gasTemperatureFreshness: ContextFreshness",
            "pressureDiffBar: Double?",
            "gasPressureAbsBar: Double?",
            "pressureFreshness: ContextFreshness",
            "mapSource: String",
            "selectedRegionContexts",
            '"OMEGAS_CONTEXT_ONLY"',
            '"gas_temperature_used_as_native_gate", false',
            '"pressure_used_as_native_gate", false',
        ):
            self.assertIn(token, source)

    def test_upstream_sample_and_region_really_preserve_observed_context(self):
        analyzer = ANALYZER.read_text("utf-8")
        memory = MEMORY.read_text("utf-8")
        for token in (
            "pressureDiffBar = pressure",
            "waterC = waterC",
            "gasC = gasC",
        ):
            self.assertIn(token, analyzer)
        for token in (
            "pressureMean = sample.pressureDiffBar",
            "waterMean = sample.waterC",
            "gasMean = sample.gasC",
            '.put("pressure_diff_bar", pressureMean)',
            '.put("water_c", waterMean)',
            '.put("gas_c", gasMean)',
        ):
            self.assertIn(token, memory)

    def test_current_selector_call_is_explicitly_detectable_until_full_context_is_wired(self):
        memory = MEMORY.read_text("utf-8")
        self.assertIn("PetrolReferenceSelector.Region(", memory)
        self.assertIn("PetrolReferenceSelector.Request(", memory)
        # This test intentionally reports the integration seam. It does not declare
        # it complete; the execution receipt must compare these fields before PASS.
        self.assertIn("waterC = region.waterMean", memory)
        self.assertIn("waterC = sample.waterC", memory)


if __name__ == "__main__":
    unittest.main()
