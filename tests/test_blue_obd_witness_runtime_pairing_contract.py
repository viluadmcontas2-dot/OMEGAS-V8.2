from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"


def main() -> None:
    text = SERVICE.read_text(encoding="utf-8")

    required = [
        "ObdWitnessEngine",
        "pairObdStftWitness(sample)",
        "telemetryStore.nearestFrame(observedAtMs, 250L)",
        "ObdWitnessSample(",
        "obdWitnessEngine.observe",
        'optDouble("rpm"',
        'optDouble("map_bar"',
        'optDouble("petrol_ms"',
        'optString("fuel"',
        'optLong("skew_ms"',
        'optJSONObject("epoch")',
        'optString("mapEpochId")',
        'optString("curveEpochId")',
    ]
    missing = [token for token in required if token not in text]
    assert not missing, f"OBD witness runtime pairing seam missing: {missing}"

    start = text.index("    private fun pairObdStftWitness(sample: JSONObject)")
    end = text.index("\n    private fun ", start + 10)
    pairing = text[start:end]
    forbidden = ["coolant", "water", "load", "throttle", "maf", "speed", "iat", "ltft"]
    present = [token for token in forbidden if token.lower() in pairing.lower()]
    assert not present, f"forbidden operating variables leaked into witness pairing: {present}"

    print("BLUE_OBD_WITNESS_RUNTIME_PAIRING_CONTRACT=PASS")


if __name__ == "__main__":
    main()
