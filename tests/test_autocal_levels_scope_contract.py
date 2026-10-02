from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
projection = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt").read_text("utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text("utf-8")
cockpit = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text("utf-8")
dashboard = (ROOT / "app/src/main/assets/ui/screens/dashboard.js").read_text("utf-8")

assert "levelsRaw" not in projection
assert "LEVELS_MAX_AGE_MS" not in projection
assert "telemetryStatus" not in projection
assert "telemetryStore.liveJson()" not in bridge
assert "autocalLiveLevel" not in cockpit
assert "LEVELS RAW" not in cockpit
assert "level_raw" not in cockpit

assert "LEVELS RAW" in dashboard
assert "dashLevelsRaw" in dashboard
assert "level_raw" in dashboard

print("AUTOCAL_LEVELS_ARCHITECTURE_SCOPE=PASS")
