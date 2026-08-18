from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SELECTOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt"
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
ANALYZER = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt"
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/learning/PetrolReferenceEnvironmentBridge.kt"


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

    def test_real_selector_call_wires_region_and_current_environment(self):
        memory = MEMORY.read_text("utf-8")
        for token in (
            "environment = PetrolReferenceEnvironmentBridge.region(",
            "gasTemperatureC = region.gasMean",
            "pressureDiffBar = region.pressureMean",
            "environment = PetrolReferenceEnvironmentBridge.request(",
            "gasTemperatureC = sample.gasC",
            "pressureDiffBar = sample.pressureDiffBar",
        ):
            self.assertIn(token, memory)

    def test_gas_temperature_remains_app_context_and_pressure_map_are_native_anchored(self):
        bridge = BRIDGE.read_text("utf-8")
        selector = SELECTOR.read_text("utf-8")
        self.assertIn('gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_REGION")', bridge)
        self.assertIn('gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_CURRENT")', bridge)
        self.assertIn('"NATIVE_ANCHORED:MP48_PRESSURE_DIFF_REGION:E4"', bridge)
        self.assertIn('"NATIVE_ANCHORED:MP48_PRESSURE_DIFF_CURRENT:E4"', bridge)
        self.assertIn('mapSource = "NATIVE_ANCHORED:MP48_RUNTIME_MAP:E4"', bridge)
        self.assertIn("pressureFreshness = freshness(pressureDiffBar", bridge)
        # Native-anchored acquisition/context does not mean pressure becomes a selector gate here.
        self.assertIn('"gas_temperature_used_as_native_gate", false', selector)
        self.assertIn('"pressure_used_as_native_gate", false', selector)


if __name__ == "__main__":
    unittest.main()
