from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    target = ROOT / path
    assert target.is_file(), f"functional recovery contract missing: {path}"
    return target.read_text(encoding="utf-8")


region = read("app/src/main/java/com/omegas/prohub/blue/BlueScientificRegion.kt")
domain = read("app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt")
ledger = read("app/src/main/java/com/omegas/prohub/blue/BlueCausalLedger.kt")
map_k = read("app/src/main/java/com/omegas/prohub/obd/ObdMapKSuggestion.kt")
scheduler = read("app/src/main/assets/ui/core/scheduler.js")
native_api = read("app/src/main/assets/ui/core/native-api.js")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
consumption = read("app/src/main/java/com/omegas/prohub/telemetry/ConsumptionEvidenceEngine.kt")

assert "BlueScientificRegion" in region and "rpm-map-v1" in region
assert "val scientificRegionId: String = BlueScientificRegion" in domain
assert "omegas-blue-causal-ledger-v2" in ledger
assert "val row = KMapPhysicalAxes.petrolBins()" in map_k
assert "val column = KMapPhysicalAxes.rpmBins()" in map_k
assert "setInterval" not in scheduler and "setTimeout" in scheduler
assert "getObdWitnessStatus" in native_api
assert "CoalescedSnapshotWriter" in service
assert "ConsumptionEvidenceEngine" in consumption and "distanceKm / addedM3" in consumption

print("BLUE_FUNCTIONAL_RECOVERY_CONTRACT=PASS")
