from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

detector_path = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeOutlierDetector.kt"
assert detector_path.is_file(), "detector nativo de outlier do AutoCal ainda não existe"
detector = detector_path.read_text(encoding="utf-8")
manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")

assert "AutoMatchRefinedEngine.monotoneFit" in detector
assert "ISOLATED_NATIVE_POINT_AT_IDLE" in detector
assert "executeAutomaticPointDelete" in manager
assert '.put("automatic", prepared.automatic)' in manager
assert '.put("humanConfirmed", !prepared.automatic)' in manager
assert "runAutomaticPointRelearn" in bridge
assert "executeAutomaticPointDelete" in bridge

print("AUTOCAL_AUTO_RELEARN_CONTRACT=PASS")
