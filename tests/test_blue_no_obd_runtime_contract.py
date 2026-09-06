from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

retired_paths = [
    "app/src/main/java/com/omegas/prohub/obd",
    "app/src/main/assets/ui/screens/obd.js",
    "app/src/main/assets/ui/styles-obd-evidence.css",
    "app/src/main/assets/ui/styles-calibration-obd.css",
    "app/src/test/java/com/omegas/prohub/obd",
    "tests/ui/obd-independent-map.test.cjs",
    "tests/ui/obd-runtime-controls.test.cjs",
]
for rel in retired_paths:
    assert not (ROOT / rel).exists(), f"retired OBD runtime/test asset still exists: {rel}"

main = ROOT / "app/src/main"
for path in sorted(main.rglob("*")):
    if not path.is_file():
        continue
    rel = path.relative_to(ROOT).as_posix()
    assert "obd" not in rel.lower(), f"production OBD path remains: {rel}"
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    assert "obd" not in text.lower(), f"production OBD reference remains: {rel}"

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in ["android.hardware.bluetooth", "android.permission.BLUETOOTH"]:
    assert token not in manifest, f"OBD-only Bluetooth surface remains: {token}"

index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert "STFT" not in index and "LTFT" not in index

print("BLUE_NO_OBD_RUNTIME_CONTRACT=PASS")
