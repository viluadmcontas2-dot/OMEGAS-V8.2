from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
service = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
autocal = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
observer = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalDualFuelMaturityObserver.kt").read_text(encoding="utf-8")
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

# Read-only AutoCal não limpa a porta compartilhada antes de observar. A leitura
# hoje está repartida entre o monitor e o observer dual-fuel; a invariável é o
# comportamento de cada transação, não uma contagem histórica numa classe só.
def transaction_blocks(source: str):
    return re.findall(r"serial\.transaction\((.*?)\n\s*\)", source, flags=re.S)

for source in (autocal, observer):
    blocks = transaction_blocks(source)
    assert blocks
    assert all("purgeBefore = false" in block for block in blocks)
    assert all("workClass = Mp48WorkClass.READ_ONLY" in block for block in blocks)

print("PHYSICAL_USB_AUTOCAL_HOTFIX_CONTRACT=PASS")
