from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
text = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
start = text.index("    private fun consumeObdLearningSample(sample: JSONObject)")
end = text.index("    private fun persistObdLearning(", start)
section = text[start:end]
for token in ["ObdLearningSample(", "obdLearningEngine.observe", "obdLearningEngine.evaluate", 'optDouble("rpm"', 'optDouble("map_bar"', 'optDouble("stft"']:
    assert token in section, token
for forbidden in ["nearestFrame", "petrol_ms", "blueCalibrationStateId", "ObdFuelState"]:
    assert forbidden not in section, forbidden
print("BLUE_OBD_WITNESS_RUNTIME_PAIRING_CONTRACT=PASS")
