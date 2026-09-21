from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"

planner = PLANNER.read_text("utf-8")
monitor = MONITOR.read_text("utf-8")

assert "REFERENCE_INTERVAL_MS = 4_000L" in planner
assert "refreshReferenceGroup" in monitor, "grupo de referência ~4s ainda não implementado"
assert "REFERENCE_REFRESH_FIELDS" in monitor

for key in [
    "AutoCalProtocol.PETR_INJ_TBP",
    "AutoCalProtocol.MNFLD_PRESS_THD",
    "AutoCalProtocol.MUL_ACT",
    "AutoCalProtocol.PETR_MNFLD_PRESS_RV",
    "AutoCalProtocol.GAS_MNFLD_PRESS_RV",
]:
    assert key in monitor, f"campo de referência ausente: {key}"

reference_section = monitor.split("private fun refreshReferenceGroup", 1)[1]
reference_section = reference_section.split("private fun readFullSnapshot", 1)[0]
assert "Mp48WorkClass.READ_ONLY" in reference_section
assert "expectedSessionId = expectedSessionId" in reference_section
assert "AutoCalSnapshotBuilder.build" in reference_section
assert "snapshot.partial" in reference_section
assert "refreshPlanner.markReference" in monitor
assert "referenceRefreshAtElapsedMs" in monitor
assert "snapshotHash" in monitor, "merge incremental precisa publicar nova revisão do snapshot"

# O monitor continua sem dono próprio de thread/timer/porta serial.
assert "Executors." not in monitor
assert "ScheduledExecutor" not in monitor
assert "Thread(" not in monitor

print("AUTOCAL_GROUPED_REFERENCE_REFRESH=PASS")
