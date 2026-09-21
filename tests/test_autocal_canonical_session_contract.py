from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text("utf-8")
COCKPIT = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text("utf-8")
RECORDER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text("utf-8")

# AutoCal evidence is written into the canonical recorder.
for event in (
    '"autocal_native_snapshot"',
    '"autocal_native_calibration_epoch"',
    '"autocal_manual_snapshot"',
    '"autocal_native_action"',
):
    assert event in SERVICE or event in BRIDGE, event
assert 'sessionRecorder.record("autocal_native_snapshot"' in SERVICE
assert 'service.sessionRecorder.record("autocal_manual_snapshot"' in BRIDGE
assert 'service.sessionRecorder.record("autocal_native_action"' in BRIDGE

# AutoCal session APIs are aliases to the canonical session authority, not a second recorder.
assert 'fun getSessionLedgerStatus(): String = activityRef.get()?.serviceOrNull()?.sessionRecorderStatusJson()' in BRIDGE
assert 'fun listAutoCalSessions(): String = activityRef.get()?.serviceOrNull()?.sessionRecorderListJson()' in BRIDGE
assert 'fun exportAutoCalSession(sessionId: String)' in BRIDGE
assert 'activityRef.get()?.exportSession(sessionId)' in BRIDGE
assert 'SessionRecorder(' not in BRIDGE
assert 'sessionLogsRoot' not in BRIDGE

# Canonical recorder owns session directory and ZIP export.
assert 'class SessionRecorder(' in RECORDER
assert 'File(paths.sessionLogsRoot, id)' in RECORDER
assert 'fun exportSession(' in RECORDER
assert 'ZipOutputStream' in RECORDER

# Operator messaging says evidence is saved into the same OMEGAS sessions/document mirror.
assert 'Salvando em Documentos/Omegas automaticamente enquanto a sessão acontece.' in COCKPIT
assert 'Salvo em Documentos/Omegas. Abra Sessões apenas para revisar ou exportar.' in COCKPIT

print("AUTOCAL_CANONICAL_SESSION_CONTRACT=PASS")
