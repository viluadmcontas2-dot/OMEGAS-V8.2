from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

retired_paths = [
    "app/src/main/java/com/omegas/prohub/obd",
    "app/src/main/assets/ui/screens/obd.js",
    "app/src/main/assets/ui/styles-obd-evidence.css",
    "app/src/test/java/com/omegas/prohub/obd",
    "tests/ui/obd-independent-map.test.cjs",
    "tests/ui/obd-runtime-controls.test.cjs",
]
for rel in retired_paths:
    assert not (ROOT / rel).exists(), f"retired OBD runtime/test asset still exists: {rel}"

production_files = [
    "app/src/main/java/com/omegas/prohub/MainActivity.kt",
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt",
    "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt",
    "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt",
    "app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt",
    "app/src/main/java/com/omegas/prohub/link/OmegasLinkManager.kt",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/assets/ui/index.html",
    "app/src/main/assets/ui/app.js",
    "app/src/main/assets/ui/core/native-api.js",
    "app/src/main/assets/ui/core/router.js",
    "app/src/main/assets/ui/core/store.js",
    "app/src/main/assets/ui/core/scheduler.js",
    "app/src/main/assets/ui/screens/dashboard.js",
]
for rel in production_files:
    path = ROOT / rel
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    assert "obd" not in text.lower(), f"production OBD reference remains: {rel}"

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in ["android.hardware.bluetooth", "android.permission.BLUETOOTH"]:
    assert token not in manifest, f"OBD-only Bluetooth surface remains: {token}"

index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert 'data-route="obd"' not in index.lower()
assert "STFT" not in index and "LTFT" not in index

print("BLUE_NO_OBD_RUNTIME_CONTRACT=PASS")
