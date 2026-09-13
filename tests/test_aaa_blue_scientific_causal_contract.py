from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    target = ROOT / path
    assert target.is_file(), f"missing scientific recovery seam: {path}"
    return target.read_text(encoding="utf-8")


attribution = read("app/src/main/java/com/omegas/prohub/blue/BlueCausalAttribution.kt")
k_writer = read("app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt")
map_address = read("app/src/main/java/com/omegas/prohub/blue/BlueMapKAddressing.kt")

assert "samePhysicalRegion" in attribution
assert "MAP_CELL_NOT_PARTICIPATING" in attribution
assert "CURVE_POINT_NOT_PARTICIPATING" in attribution
assert "BlueMapKAddressing.cell" in attribution
assert "KFactorProtocol.blendAxis" in attribution
assert "comparison.petrolOnCngMs" in map_address
assert "confirmedMapSnapshotCache" in k_writer
assert "fun confirmedMapSnapshot" in k_writer

print("BLUE_SCIENTIFIC_CAUSAL_CONTRACT=PASS")
