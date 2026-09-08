#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def text(rel): return (ROOT / rel).read_text(encoding="utf-8")

engine = text("app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt")
autocal = text("app/src/main/java/com/omegas/prohub/blue/BlueAutoCalAdapter.kt")
bridge = text("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt")
settings = text("app/src/main/java/com/omegas/prohub/settings/AppSettings.kt")
relevance = text("app/src/main/java/com/omegas/prohub/diagnostics/SessionRelevancePolicy.kt")
vault = text("app/src/main/java/com/omegas/prohub/diagnostics/SessionVault.kt")

for marker in ["cngErrorLog", "actuatorGain", "correctionMultiplier", "calibrationState"]:
    assert f"fun {marker}" in engine
for marker in ["engine.cngErrorLog", "engine.correctionMultiplier", "engine.actuatorGain"]:
    assert marker in autocal
assert 'automaticWrite = false' in autocal
assert 'getAnalysis(): String = activityRef.get()?.serviceOrNull()?.blueProposalJson()' in bridge
assert 'get() = prefs.getInt("sessionKeepCount", 30)' in settings
assert 'value.coerceIn(20, 100)' in settings
assert 'MIN_VALID_TELEMETRY_FRAMES = 20L' in relevance
assert 'MIN_VALID_DURATION_MS = 5_000L' in relevance
assert 'protectedEvidence || explicitlyProtected -> SessionRelevance.PROTECTED' in relevance
assert 'The private spool is never deleted by this class' in vault
assert '.put("spoolPreserved", true)' in vault
print("BLUE_CONVERGENCE_FINAL_CONTRACT=PASS")
