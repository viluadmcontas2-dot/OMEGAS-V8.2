from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
ACCESS = ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"


def main() -> None:
    text = SERVICE.read_text(encoding="utf-8")
    access = ACCESS.read_text(encoding="utf-8")
    required = [
        "ObdWitnessEngine", "pairObdStftWitness(sample)", "telemetryStore.nearestFrame(observedAtMs, 250L)",
        "ObdWitnessSample(", "obdWitnessEngine.observe", 'optDouble("rpm"', 'optDouble("map_bar"',
        'optDouble("petrol_ms"', 'optString("fuel"', 'optLong("skew_ms"', "blueCalibrationStateId()",
    ]
    missing = [token for token in required if token not in text]
    assert not missing, f"OBD witness runtime pairing seam missing: {missing}"
    assert '"map-${revision.optInt("mapK", 0)}:curve-${revision.optInt("curveK", 0)}"' in access

    start = text.index("    private fun pairObdStftWitness(sample: JSONObject)")
    end = text.index("\n    private fun ", start + 10)
    pairing = text[start:end]
    for forbidden in ["coolant", "water", "load", "throttle", "maf", "speed", "iat", "ltft", "mapsJson", "mapEpochId", "curveEpochId"]:
        assert forbidden.lower() not in pairing.lower(), f"forbidden variable/legacy state leaked into witness pairing: {forbidden}"
    print("BLUE_OBD_WITNESS_RUNTIME_PAIRING_CONTRACT=PASS")


if __name__ == "__main__":
    main()
