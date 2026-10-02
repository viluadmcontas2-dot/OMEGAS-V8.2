from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
service = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
autocal = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
policy = (ROOT / "app/src/main/java/com/omegas/prohub/service/UsbSessionTransitionPolicy.kt").read_text(encoding="utf-8")

# A conexão física não pode ser representada só por true/false: uma reabertura
# true -> true com novo connectionSessionId é uma geração nova.
assert "lastUsbSessionId" in service
assert "usb.connectionSessionId" in service
assert "UsbSessionTransition.GENERATION_CHANGED" in service
assert 'runtime.endUsbSession("USB_SESSION_REPLACED")' in service
assert "telemetryStore.beginSession(sessionId)" in service
assert "runtime.beginUsbSession(sessionId)" in service
assert "kWriter.beginUsbSession(sessionId)" in service
assert "kFactor.beginUsbSession(sessionId)" in service
assert "nativeAutoCal.beginUsbSession(sessionId)" in service
assert "sessionId != lastSessionId" in policy

# AutoCal só pode entrar depois de a telemetria real estar pronta e válida.
assert "runtime.ready && telemetryStore.isValid()" in service
assert "SESSION_SETTLE_MS = 8_000L" in autocal
assert "snapshotRequested = false" in autocal
assert "WAITING_TELEMETRY_SETTLE" in autocal

# Read-only AutoCal não limpa a porta compartilhada antes de observar.
assert "purgeBefore = true" not in autocal
assert autocal.count("purgeBefore = false") >= 4

print("PHYSICAL_USB_AUTOCAL_HOTFIX_CONTRACT=PASS")
