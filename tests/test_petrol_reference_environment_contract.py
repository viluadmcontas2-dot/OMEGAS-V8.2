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

    def test_real_selector_call_wires_region_and_current_gas_temperature(self):
        memory = MEMORY.read_text("utf-8")
        for token in (
            "environment = PetrolReferenceEnvironmentBridge.region(",
            "gasTemperatureC = region.gasMean",
            "environment = PetrolReferenceEnvironmentBridge.request(",
            "gasTemperatureC = sample.gasC",
        ):
            self.assertIn(token, memory)

        bridge = BRIDGE.read_text("utf-8")
        self.assertIn("gasTemperatureC = gasTemperatureC.takeIf(Double::isFinite)", bridge)
        self.assertIn("gasTemperatureFreshness = freshness(gasTemperatureC", bridge)
        self.assertIn('gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_REGION")', bridge)
        self.assertIn('gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_CURRENT")', bridge)

    def test_owner_075_does_not_turn_gas_temperature_or_pressure_into_native_gate(self):
        bridge = BRIDGE.read_text("utf-8")
        selector = SELECTOR.read_text("utf-8")
        self.assertIn('pressureSource = "OWNER_076_PENDING"', bridge)
        self.assertIn("pressureFreshness = PetrolReferenceSelector.ContextFreshness.UNKNOWN", bridge)
        self.assertIn('"gas_temperature_used_as_native_gate", false', selector)
        self.assertIn('"pressure_used_as_native_gate", false', selector)


if __name__ == "__main__":
    unittest.main()
