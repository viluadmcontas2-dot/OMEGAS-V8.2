from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"

assert PLANNER.is_file(), "refresh planner ausente"
service = SERVICE.read_text("utf-8")
monitor = MONITOR.read_text("utf-8")
planner = PLANNER.read_text("utf-8")

assert "ACQUISITION_INTERVAL_MS = 1_000L" in planner
assert "REFERENCE_INTERVAL_MS = 4_000L" in planner
# W1 (2026-10-18): sem relógio no serviço; o refresh nativo anda por CONTAGEM de quadros válidos
# (FIX1: a cada AUTOCAL_TICK_FRAMES, não a cada quadro) e o health por evento.
assert "FrameCountTrigger(ServiceEventPolicy.AUTOCAL_TICK_FRAMES)" in service
assert "autoCalTick()\n                        renewWakeLockIfNeeded()" in service
assert "scheduleWithFixedDelay" not in service
assert "scheduler.execute { healthTick() }" in service
assert "refreshAcquisitionGroup" in monitor
for key in [
    "AutoCalProtocol.NUM_BUF_UPD_PETR",
    "AutoCalProtocol.NUM_BUF_UPD_GAS",
    "AutoCalProtocol.ACQUIRED_ZONES_PETROL",
    "AutoCalProtocol.ACQUIRED_ZONES_GAS",
]:
    assert key in monitor, key
assert "Executors." not in monitor
assert "ScheduledExecutor" not in monitor
assert "Thread(" not in monitor
print("AUTOCAL_GROUPED_REFRESH_C1_C2=PASS")
